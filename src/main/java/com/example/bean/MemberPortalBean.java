package com.example.bean;

import com.example.dao.AccountDAO;
import com.example.dao.LoanDAO;
import com.example.dao.UserDAO;
import com.example.model.Loan;
import com.example.model.Repayment;
import com.example.model.SavingsAccount;
import com.example.model.Transaction;
import com.example.model.User;
import com.example.service.WithdrawalService;
import com.example.service.LoanValidationService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Named("memberPortalBean")
@ViewScoped
public class MemberPortalBean implements Serializable {
    private static final long serialVersionUID = 1L;

    @Inject private AuthBean authBean;
    @Inject private AccountDAO accountDAO;
    @Inject private LoanDAO loanDAO;
    @Inject private LoanValidationService loanValidationService;
    @Inject private WithdrawalService withdrawalService;

    // Financial state records variables
    private SavingsAccount activeAccount;
    private List<Transaction> statementHistory;
    private List<Loan> personalLoans;

    // Interactive credit application variables
    private BigDecimal requestedLoanPrincipal;
    private BigDecimal savingsTransactionAmount;
    private String savingsTransactionDescription;
    private BigDecimal loanRepaymentAmount;
    private String loanRepaymentReference;

    // Secure profile credentials parameters
    private String currentPasswordInput;
    private String newPasswordInput;
    private boolean dataAvailable = true;
    private String dataStatusMessage = "Member data loaded.";

    @PostConstruct
    public void init() {
        try {
            User currentSessionUser = authBean.getLoggedInUser();
            if (currentSessionUser != null) {
                this.activeAccount = accountDAO.findByUserId(currentSessionUser.getId());
                this.personalLoans = loanDAO.findLoansByUserId(currentSessionUser.getId());
                if (activeAccount != null) {
                    this.statementHistory = accountDAO.findStatementsByAccountId(activeAccount.getId());
                }
            }
            dataAvailable = true;
            dataStatusMessage = "Member data loaded.";
        } catch (Throwable ex) {
            this.activeAccount = null;
            this.personalLoans = Collections.emptyList();
            this.statementHistory = Collections.emptyList();
            dataAvailable = false;
            dataStatusMessage = "Member data unavailable. Check database connection and credentials.";
            ex.printStackTrace();
        }

        if (this.personalLoans == null) {
            this.personalLoans = Collections.emptyList();
        }
        if (this.statementHistory == null) {
            this.statementHistory = Collections.emptyList();
        }
    }

    /**
     * Processes self-service loan application form submissions.
     * Enforces hardcoded 3x savings borrowing ceiling parameters.
     */
    public String applyForLoan() {
        FacesContext context = FacesContext.getCurrentInstance();
        User currentUser = authBean.getLoggedInUser();

        if (currentUser == null) {
            context.addMessage(null, new FacesMessage(
                    FacesMessage.SEVERITY_ERROR, "Session Expired", "Please log in again."
            ));
            return "/login?faces-redirect=true";
        }

        if (requestedLoanPrincipal == null || requestedLoanPrincipal.compareTo(BigDecimal.ZERO) <= 0) {
            context.addMessage(null, new FacesMessage(
                    FacesMessage.SEVERITY_ERROR, "Invalid Request", "Please enter a loan amount greater than zero."
            ));
            return null;
        }

        LoanValidationService.ValidationResult validationResult =
                loanValidationService.validateLoanEligibility(currentUser.getId(), requestedLoanPrincipal);

        if (!validationResult.isValid()) {
            context.addMessage(null, new FacesMessage(
                    FacesMessage.SEVERITY_WARN, "Loan Application Rejected", validationResult.getMessage()
            ));
            return null;
        }

        BigDecimal interestAmount = requestedLoanPrincipal
                .multiply(new BigDecimal("0.10"))
                .setScale(2, RoundingMode.HALF_UP);

        Loan loan = new Loan();
        loan.setMember(currentUser);
        loan.setPrincipalAmount(requestedLoanPrincipal.setScale(2, RoundingMode.HALF_UP));
        loan.setInterestAmount(interestAmount);
        loan.setTotalRepayable(loan.getPrincipalAmount().add(interestAmount));
        loan.setAmountRepaid(BigDecimal.ZERO);
        loan.setStatus(Loan.LoanStatus.PENDING);
        loan.setAppliedAt(new Date());

        loanDAO.saveLoan(loan);
        this.personalLoans = loanDAO.findLoansByUserId(currentUser.getId());
        this.requestedLoanPrincipal = null;

        context.addMessage(null, new FacesMessage(
                FacesMessage.SEVERITY_INFO, "Application Submitted",
                "Your loan application has been submitted for administrative review."
        ));
        return null;
    }

    public String processMemberDeposit() {
        FacesContext context = FacesContext.getCurrentInstance();
        if (!requireActiveSavingsAccount(context)) {
            return null;
        }
        if (savingsTransactionAmount == null || savingsTransactionAmount.compareTo(BigDecimal.ZERO) <= 0) {
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Invalid Amount", "Enter a deposit amount greater than zero."));
            return null;
        }

        activeAccount.setBalance(activeAccount.getBalance().add(savingsTransactionAmount));

        Transaction tx = new Transaction();
        tx.setAccount(activeAccount);
        tx.setAmount(savingsTransactionAmount.setScale(2, RoundingMode.HALF_UP));
        tx.setTransactionType(Transaction.TransactionType.DEPOSIT);
        tx.setCreatedAt(new Date());
        tx.setDescription(savingsTransactionDescription == null || savingsTransactionDescription.isBlank()
                ? "Member self-service savings deposit."
                : savingsTransactionDescription.trim());

        accountDAO.processLedgerTransaction(activeAccount, tx);
        reloadMemberData();
        clearSavingsTransactionForm();
        context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, "Deposit Posted", "Your savings deposit has been recorded."));
        return null;
    }

    public String processMemberWithdrawal() {
        FacesContext context = FacesContext.getCurrentInstance();
        if (!requireActiveSavingsAccount(context)) {
            return null;
        }
        if (savingsTransactionAmount == null || savingsTransactionAmount.compareTo(BigDecimal.ZERO) <= 0) {
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Invalid Amount", "Enter a withdrawal amount greater than zero."));
            return null;
        }

        boolean allowed = withdrawalService.verifyWithdrawalEligibility(activeAccount.getMember().getId(), savingsTransactionAmount);
        if (!allowed) {
            context.addMessage(null, new FacesMessage(
                    FacesMessage.SEVERITY_ERROR,
                    "Withdrawal Blocked",
                    "Withdrawal would violate the UGX 20,000 minimum balance rule or exceed available savings."
            ));
            return null;
        }

        activeAccount.setBalance(activeAccount.getBalance().subtract(savingsTransactionAmount));

        Transaction tx = new Transaction();
        tx.setAccount(activeAccount);
        tx.setAmount(savingsTransactionAmount.setScale(2, RoundingMode.HALF_UP));
        tx.setTransactionType(Transaction.TransactionType.WITHDRAWAL);
        tx.setCreatedAt(new Date());
        tx.setDescription(savingsTransactionDescription == null || savingsTransactionDescription.isBlank()
                ? "Member self-service savings withdrawal."
                : savingsTransactionDescription.trim());

        accountDAO.processLedgerTransaction(activeAccount, tx);
        reloadMemberData();
        clearSavingsTransactionForm();
        context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, "Withdrawal Posted", "Your savings withdrawal has been recorded."));
        return null;
    }

    public String processLoanRepayment() {
        FacesContext context = FacesContext.getCurrentInstance();
        Loan activeLoan = getActiveLoan();
        if (activeLoan == null) {
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, "No Active Loan", "You do not have an approved outstanding loan to repay."));
            return null;
        }
        if (loanRepaymentAmount == null || loanRepaymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Invalid Amount", "Enter a repayment amount greater than zero."));
            return null;
        }

        BigDecimal outstanding = getOutstandingBalance(activeLoan);
        if (loanRepaymentAmount.compareTo(outstanding) > 0) {
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Overpayment Blocked", "Repayment cannot exceed the outstanding loan balance."));
            return null;
        }

        activeLoan.setAmountRepaid(activeLoan.getAmountRepaid().add(loanRepaymentAmount.setScale(2, RoundingMode.HALF_UP)));
        if (activeLoan.getAmountRepaid().compareTo(activeLoan.getTotalRepayable()) >= 0) {
            activeLoan.setStatus(Loan.LoanStatus.FULLY_REPAID);
        }

        Repayment repayment = new Repayment();
        repayment.setLoan(activeLoan);
        repayment.setAmount(loanRepaymentAmount.setScale(2, RoundingMode.HALF_UP));
        repayment.setPaidAt(new Date());
        repayment.setReceiptReference(loanRepaymentReference == null || loanRepaymentReference.isBlank()
                ? "MEMBER-SELF-SERVICE"
                : loanRepaymentReference.trim());

        loanDAO.processLoanRepayment(activeLoan, repayment);
        reloadMemberData();
        loanRepaymentAmount = null;
        loanRepaymentReference = null;
        context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, "Repayment Posted", "Your loan repayment has been recorded."));
        return null;
    }

    private boolean requireActiveSavingsAccount(FacesContext context) {
        if (activeAccount == null) {
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, "No Savings Account", "Your savings account has not been provisioned yet."));
            return false;
        }
        return true;
    }

    private void reloadMemberData() {
        User currentSessionUser = authBean.getLoggedInUser();
        if (currentSessionUser != null) {
            this.activeAccount = accountDAO.findByUserId(currentSessionUser.getId());
            this.personalLoans = loanDAO.findLoansByUserId(currentSessionUser.getId());
            this.statementHistory = activeAccount != null ? accountDAO.findStatementsByAccountId(activeAccount.getId()) : Collections.emptyList();
        }
    }

    private void clearSavingsTransactionForm() {
        savingsTransactionAmount = null;
        savingsTransactionDescription = null;
    }

    public String updateProfilePassword() {
        FacesContext context = FacesContext.getCurrentInstance();
        User currentUser = authBean.getLoggedInUser();

        if (currentUser == null) {
            context.addMessage(null, new FacesMessage(
                    FacesMessage.SEVERITY_ERROR, "Session Expired", "Please log in again."
            ));
            return "/login?faces-redirect=true";
        }

        if (currentPasswordInput == null || !currentPasswordInput.equals(currentUser.getPassword())) {
            context.addMessage(null, new FacesMessage(
                    FacesMessage.SEVERITY_ERROR, "Password Update Failed", "The current password is incorrect."
            ));
            return null;
        }

        if (newPasswordInput == null || newPasswordInput.trim().length() < 4) {
            context.addMessage(null, new FacesMessage(
                    FacesMessage.SEVERITY_ERROR, "Password Update Failed", "The new password must be at least 4 characters."
            ));
            return null;
        }

        currentUser.setPassword(newPasswordInput.trim());
        UserDAO userDAO = new UserDAO();
        userDAO.saveUser(currentUser);
        authBean.setLoggedInUser(currentUser);
        currentPasswordInput = null;
        newPasswordInput = null;

        context.addMessage(null, new FacesMessage(
                FacesMessage.SEVERITY_INFO, "Password Updated", "Your password was changed successfully."
        ));
        return null;
    }

    public SavingsAccount getActiveAccount() { return activeAccount; }
    public boolean isHasActiveAccount() { return activeAccount != null; }
    public String getActiveAccountNumber() { return activeAccount != null ? activeAccount.getAccountNumber() : "Not provisioned"; }
    public BigDecimal getActiveAccountBalance() { return activeAccount != null ? activeAccount.getBalance() : BigDecimal.ZERO; }
    public BigDecimal getEstimatedMonthlySavingsInterest() {
        return getActiveAccountBalance().multiply(new BigDecimal("0.05")).divide(new BigDecimal("12"), 2, RoundingMode.HALF_UP);
    }
    public List<Transaction> getStatementHistory() { return statementHistory; }
    public List<Loan> getPersonalLoans() { return personalLoans; }
    public Loan getActiveLoan() {
        return personalLoans.stream()
                .filter(loan -> loan.getStatus() == Loan.LoanStatus.APPROVED && loan.getAmountRepaid().compareTo(loan.getTotalRepayable()) < 0)
                .findFirst()
                .orElse(null);
    }
    public BigDecimal getOutstandingBalance(Loan loan) {
        return loan == null ? BigDecimal.ZERO : loan.getTotalRepayable().subtract(loan.getAmountRepaid());
    }
    public BigDecimal getActiveLoanOutstandingBalance() { return getOutstandingBalance(getActiveLoan()); }
    public boolean isDataAvailable() { return dataAvailable; }
    public String getDataStatusMessage() { return dataStatusMessage; }

    public BigDecimal getRequestedLoanPrincipal() { return requestedLoanPrincipal; }
    public void setRequestedLoanPrincipal(BigDecimal requestedLoanPrincipal) { this.requestedLoanPrincipal = requestedLoanPrincipal; }

    public BigDecimal getSavingsTransactionAmount() { return savingsTransactionAmount; }
    public void setSavingsTransactionAmount(BigDecimal savingsTransactionAmount) { this.savingsTransactionAmount = savingsTransactionAmount; }
    public String getSavingsTransactionDescription() { return savingsTransactionDescription; }
    public void setSavingsTransactionDescription(String savingsTransactionDescription) { this.savingsTransactionDescription = savingsTransactionDescription; }
    public BigDecimal getLoanRepaymentAmount() { return loanRepaymentAmount; }
    public void setLoanRepaymentAmount(BigDecimal loanRepaymentAmount) { this.loanRepaymentAmount = loanRepaymentAmount; }
    public String getLoanRepaymentReference() { return loanRepaymentReference; }
    public void setLoanRepaymentReference(String loanRepaymentReference) { this.loanRepaymentReference = loanRepaymentReference; }

    public String getCurrentPasswordInput() { return currentPasswordInput; }
    public void setCurrentPasswordInput(String currentPasswordInput) { this.currentPasswordInput = currentPasswordInput; }

    public String getNewPasswordInput() { return newPasswordInput; }
    public void setNewPasswordInput(String newPasswordInput) { this.newPasswordInput = newPasswordInput; }
    }
