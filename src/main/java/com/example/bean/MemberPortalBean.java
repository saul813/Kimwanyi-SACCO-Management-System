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
import com.example.service.StatementPdfService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.text.SimpleDateFormat;
import java.io.IOException;

@Named("memberPortalBean")
@ViewScoped
public class MemberPortalBean implements Serializable {
    private static final long serialVersionUID = 1L;

    @Inject private AuthBean authBean;
    @Inject private AccountDAO accountDAO;
    @Inject private LoanDAO loanDAO;
    @Inject private LoanValidationService loanValidationService;
    @Inject private WithdrawalService withdrawalService;
    @Inject private StatementPdfService statementPdfService;

    // Financial state records variables
    private SavingsAccount activeAccount;
    private List<Transaction> statementHistory;
    private List<Loan> personalLoans;
    private List<Repayment> personalRepayments;
    private Map<Long, BigDecimal> statementRunningBalances = new HashMap<>();

    // Interactive credit application variables
    private BigDecimal requestedLoanPrincipal;
    private String requestedLoanReason;
    private BigDecimal savingsTransactionAmount;
    private String savingsTransactionDescription;
    private BigDecimal loanRepaymentAmount;
    private String loanRepaymentReference;
    private String profilePhoneNumber;
    private String profileEmailAddress;

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
                this.personalRepayments = loanDAO.findRepaymentsByUserId(currentSessionUser.getId());
                this.profilePhoneNumber = currentSessionUser.getPhoneNumber();
                this.profileEmailAddress = currentSessionUser.getEmail();
                if (activeAccount != null) {
                    this.statementHistory = accountDAO.findStatementsByAccountId(activeAccount.getId());
                }
            }
            dataAvailable = true;
            dataStatusMessage = "Member data loaded.";
        } catch (Throwable ex) {
            this.activeAccount = null;
            this.personalLoans = Collections.emptyList();
            this.personalRepayments = Collections.emptyList();
            this.statementHistory = Collections.emptyList();
            dataAvailable = false;
            dataStatusMessage = "Member data unavailable. Check database connection and credentials.";
            ex.printStackTrace();
        }

        if (this.personalLoans == null) {
            this.personalLoans = Collections.emptyList();
        }
        if (this.personalRepayments == null) {
            this.personalRepayments = Collections.emptyList();
        }
        if (this.statementHistory == null) {
            this.statementHistory = Collections.emptyList();
        }
        rebuildStatementRunningBalances();
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

        if (requestedLoanReason == null || requestedLoanReason.trim().length() < 10) {
            context.addMessage(null, new FacesMessage(
                    FacesMessage.SEVERITY_ERROR, "Invalid Request", "Please enter a loan reason of at least 10 characters."
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
        loan.setLoanReason(requestedLoanReason.trim());
        loan.setStatus(Loan.LoanStatus.PENDING);
        loan.setAppliedAt(new Date());

        loanDAO.saveLoan(loan);
        this.personalLoans = loanDAO.findLoansByUserId(currentUser.getId());
        this.requestedLoanPrincipal = null;
        this.requestedLoanReason = null;

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
                : "[Member] " + savingsTransactionDescription.trim());

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
                : "[Member] " + savingsTransactionDescription.trim());

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
                : "[Member] " + loanRepaymentReference.trim());

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
            this.personalRepayments = loanDAO.findRepaymentsByUserId(currentSessionUser.getId());
            this.statementHistory = activeAccount != null ? accountDAO.findStatementsByAccountId(activeAccount.getId()) : Collections.emptyList();
        }
        rebuildStatementRunningBalances();
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

    public String updateProfileContactDetails() {
        FacesContext context = FacesContext.getCurrentInstance();
        User currentUser = authBean.getLoggedInUser();

        if (currentUser == null) {
            context.addMessage(null, new FacesMessage(
                    FacesMessage.SEVERITY_ERROR, "Session Expired", "Please log in again."
            ));
            return "/login?faces-redirect=true";
        }

        if (profilePhoneNumber == null || profilePhoneNumber.trim().isBlank()) {
            context.addMessage(null, new FacesMessage(
                    FacesMessage.SEVERITY_ERROR, "Update Failed", "Phone number is required."
            ));
            return null;
        }

        if (profileEmailAddress == null || profileEmailAddress.trim().isBlank() || !profileEmailAddress.contains("@")) {
            context.addMessage(null, new FacesMessage(
                    FacesMessage.SEVERITY_ERROR, "Update Failed", "Enter a valid email address."
            ));
            return null;
        }

        currentUser.setPhoneNumber(profilePhoneNumber.trim());
        currentUser.setEmail(profileEmailAddress.trim().toLowerCase());
        UserDAO userDAO = new UserDAO();
        userDAO.saveUser(currentUser);
        authBean.setLoggedInUser(currentUser);

        context.addMessage(null, new FacesMessage(
                FacesMessage.SEVERITY_INFO, "Profile Updated", "Your contact details were updated successfully."
        ));
        return null;
    }

    public String downloadSavingsStatementPdf() {
        User currentUser = authBean.getLoggedInUser();
        if (currentUser == null) {
            return null;
        }
        try {
            byte[] pdf = statementPdfService.buildSavingsStatement(
                    currentUser,
                    getActiveAccountNumber(),
                    getActiveAccountBalance(),
                    getAvailableForWithdrawal(),
                    statementHistory,
                    this::getStatementRunningBalance,
                    getLogoRealPath()
            );
            sendPdf(pdf, "kimwanyi-savings-statement.pdf");
        } catch (IOException ex) {
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
                    FacesMessage.SEVERITY_ERROR, "PDF Download Failed", "Unable to generate the savings statement PDF."
            ));
            ex.printStackTrace();
        }
        return null;
    }

    public String downloadLoanStatementPdf() {
        User currentUser = authBean.getLoggedInUser();
        if (currentUser == null) {
            return null;
        }
        try {
            byte[] pdf = statementPdfService.buildLoanStatement(
                    currentUser,
                    getActiveAccountNumber(),
                    getActiveLoanOutstandingBalance(),
                    getMaximumEligibleLoanAmount(),
                    getLoanLedgerHistory(),
                    getLogoRealPath()
            );
            sendPdf(pdf, "kimwanyi-loan-statement.pdf");
        } catch (IOException ex) {
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
                    FacesMessage.SEVERITY_ERROR, "PDF Download Failed", "Unable to generate the loan statement PDF."
            ));
            ex.printStackTrace();
        }
        return null;
    }

    private String getLogoRealPath() {
        ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();
        return externalContext.getRealPath("/resources/images/logo.png");
    }

    private void sendPdf(byte[] pdf, String filename) throws IOException {
        FacesContext context = FacesContext.getCurrentInstance();
        ExternalContext externalContext = context.getExternalContext();
        externalContext.responseReset();
        externalContext.setResponseContentType("application/pdf");
        externalContext.setResponseContentLength(pdf.length);
        externalContext.setResponseHeader("Content-Disposition", "inline; filename=\"" + filename + "\"");
        externalContext.getResponseOutputStream().write(pdf);
        context.responseComplete();
    }

    public SavingsAccount getActiveAccount() { return activeAccount; }
    public boolean isHasActiveAccount() { return activeAccount != null; }
    public String getActiveAccountNumber() { return activeAccount != null ? activeAccount.getAccountNumber() : "Not provisioned"; }
    public BigDecimal getActiveAccountBalance() { return activeAccount != null ? activeAccount.getBalance() : BigDecimal.ZERO; }
    public BigDecimal getEstimatedMonthlySavingsInterest() {
        return getActiveAccountBalance().multiply(new BigDecimal("0.05")).divide(new BigDecimal("12"), 2, RoundingMode.HALF_UP);
    }
    public BigDecimal getAvailableForWithdrawal() {
        BigDecimal available = getActiveAccountBalance().subtract(new BigDecimal("20000.00"));
        return available.compareTo(BigDecimal.ZERO) > 0 ? available : BigDecimal.ZERO;
    }
    public List<Transaction> getRecentTransactions() {
        return statementHistory.stream().limit(3).collect(Collectors.toList());
    }

    public List<MemberActivity> getRecentActivities() {
        List<MemberActivity> activities = new java.util.ArrayList<>();

        for (Transaction tx : statementHistory) {
            activities.add(new MemberActivity(
                    tx.getTransactionType() == null ? "Savings Activity" : tx.getTransactionType().toString(),
                    tx.getDescription(),
                    tx.getCreatedAt(),
                    tx.getAmount(),
                    resolveSavingsActivitySource(tx)
            ));
        }

        for (Loan loan : personalLoans) {
            activities.add(new MemberActivity(
                    "Loan Application",
                    "Loan application status: " + loan.getStatus(),
                    loan.getAppliedAt(),
                    loan.getPrincipalAmount(),
                    "Member"
            ));
        }

        for (Repayment repayment : personalRepayments) {
            String repaymentReference = cleanActivityReference(repayment.getReceiptReference());
            activities.add(new MemberActivity(
                    "Loan Repayment",
                    "Loan repayment" + (repaymentReference == null || repaymentReference.isBlank() ? "" : " - " + repaymentReference),
                    repayment.getPaidAt(),
                    repayment.getAmount(),
                    resolveLoanRepaymentSource(repayment)
            ));
        }

        return activities.stream()
                .filter(activity -> activity.getActivityDate() != null)
                .sorted((left, right) -> right.getActivityDate().compareTo(left.getActivityDate()))
                .limit(3)
                .collect(Collectors.toList());
    }

    private String resolveSavingsActivitySource(Transaction transaction) {
        String description = transaction == null || transaction.getDescription() == null ? "" : transaction.getDescription().toLowerCase();
        return description.contains("member self-service") || description.contains("[member]") ? "Member" : "Admin";
    }

    private String resolveLoanRepaymentSource(Repayment repayment) {
        String reference = repayment == null || repayment.getReceiptReference() == null ? "" : repayment.getReceiptReference();
        return "MEMBER-SELF-SERVICE".equalsIgnoreCase(reference) || reference.toLowerCase().contains("[member]") ? "Member" : "Admin";
    }

    private String cleanActivityReference(String reference) {
        if (reference == null) {
            return null;
        }
        return reference.replace("[Member]", "").trim();
    }

    public BigDecimal getStatementRunningBalance(Transaction transaction) {
        if (transaction == null || transaction.getId() == null) {
            return BigDecimal.ZERO;
        }
        return statementRunningBalances.getOrDefault(transaction.getId(), BigDecimal.ZERO);
    }
    private void rebuildStatementRunningBalances() {
        statementRunningBalances = new HashMap<>();
        BigDecimal runningBalance = getActiveAccountBalance();
        for (Transaction transaction : statementHistory) {
            statementRunningBalances.put(transaction.getId(), runningBalance);
            if (transaction.getTransactionType() == Transaction.TransactionType.WITHDRAWAL) {
                runningBalance = runningBalance.add(transaction.getAmount());
            } else {
                runningBalance = runningBalance.subtract(transaction.getAmount());
            }
        }
    }
    public List<Transaction> getStatementHistory() { return statementHistory; }
    public List<LoanLedgerRow> getLoanLedgerHistory() {
        class LoanLedgerEvent {
            private final Date date;
            private final String type;
            private final BigDecimal amount;
            private final BigDecimal balanceImpact;
            private final int sortOrder;

            private LoanLedgerEvent(Date date, String type, BigDecimal amount, BigDecimal balanceImpact, int sortOrder) {
                this.date = date;
                this.type = type;
                this.amount = amount == null ? BigDecimal.ZERO : amount;
                this.balanceImpact = balanceImpact == null ? BigDecimal.ZERO : balanceImpact;
                this.sortOrder = sortOrder;
            }
        }

        List<LoanLedgerEvent> events = new java.util.ArrayList<>();

        for (Loan loan : personalLoans) {
            events.add(new LoanLedgerEvent(
                    loan.getAppliedAt(),
                    "Loan Application - " + loan.getStatus(),
                    loan.getPrincipalAmount(),
                    loan.getTotalRepayable(),
                    0
            ));
        }

        for (Repayment repayment : personalRepayments) {
            String repaymentReference = cleanActivityReference(repayment.getReceiptReference());
            events.add(new LoanLedgerEvent(
                    repayment.getPaidAt(),
                    "Loan Repayment" + (repaymentReference == null || repaymentReference.isBlank() ? "" : " - " + repaymentReference),
                    repayment.getAmount(),
                    repayment.getAmount().negate(),
                    1
            ));
        }

        events = events.stream()
                .filter(event -> event.date != null)
                .sorted((left, right) -> {
                    int dateComparison = left.date.compareTo(right.date);
                    return dateComparison != 0 ? dateComparison : Integer.compare(left.sortOrder, right.sortOrder);
                })
                .collect(Collectors.toList());

        List<LoanLedgerRow> rows = new java.util.ArrayList<>();
        BigDecimal runningBalance = BigDecimal.ZERO;
        for (LoanLedgerEvent event : events) {
            runningBalance = runningBalance.add(event.balanceImpact);
            if (runningBalance.compareTo(BigDecimal.ZERO) < 0) {
                runningBalance = BigDecimal.ZERO;
            }
            rows.add(new LoanLedgerRow(
                    event.date,
                    event.type,
                    event.amount,
                    runningBalance
            ));
        }

        return rows.stream()
                .sorted((left, right) -> right.getActivityDate().compareTo(left.getActivityDate()))
                .collect(Collectors.toList());
    }

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
    public BigDecimal getActiveLoanPrincipalAmount() {
        Loan activeLoan = getActiveLoan();
        return activeLoan == null || activeLoan.getPrincipalAmount() == null ? BigDecimal.ZERO : activeLoan.getPrincipalAmount();
    }
    public BigDecimal getActiveLoanAmountRepaid() {
        Loan activeLoan = getActiveLoan();
        return activeLoan == null || activeLoan.getAmountRepaid() == null ? BigDecimal.ZERO : activeLoan.getAmountRepaid();
    }
    public BigDecimal getMaximumEligibleLoanAmount() {
        return getActiveAccountBalance().multiply(new BigDecimal("3")).setScale(2, RoundingMode.HALF_UP);
    }
    public BigDecimal getLoanInterestPreview() {
        return requestedLoanPrincipal == null ? BigDecimal.ZERO : requestedLoanPrincipal.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP);
    }
    public BigDecimal getLoanTotalRepaymentPreview() {
        return requestedLoanPrincipal == null ? BigDecimal.ZERO : requestedLoanPrincipal.add(getLoanInterestPreview()).setScale(2, RoundingMode.HALF_UP);
    }
    public boolean isActiveLoanOverdue() {
        Loan activeLoan = getActiveLoan();
        return activeLoan != null && activeLoan.getDueDate() != null && activeLoan.getDueDate().before(new Date());
    }
    public String getActiveLoanDueDateLabel() {
        Loan activeLoan = getActiveLoan();
        if (activeLoan == null || activeLoan.getDueDate() == null) {
            return "N/A";
        }
        return new SimpleDateFormat("dd-MMM-yyyy").format(activeLoan.getDueDate());
    }
    public boolean isDataAvailable() { return dataAvailable; }
    public String getDataStatusMessage() { return dataStatusMessage; }

    public BigDecimal getRequestedLoanPrincipal() { return requestedLoanPrincipal; }
    public void setRequestedLoanPrincipal(BigDecimal requestedLoanPrincipal) { this.requestedLoanPrincipal = requestedLoanPrincipal; }
    public String getRequestedLoanReason() { return requestedLoanReason; }
    public void setRequestedLoanReason(String requestedLoanReason) { this.requestedLoanReason = requestedLoanReason; }

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
    public String getProfilePhoneNumber() { return profilePhoneNumber; }
    public void setProfilePhoneNumber(String profilePhoneNumber) { this.profilePhoneNumber = profilePhoneNumber; }
    public String getProfileEmailAddress() { return profileEmailAddress; }
    public void setProfileEmailAddress(String profileEmailAddress) { this.profileEmailAddress = profileEmailAddress; }

    public static class MemberActivity implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String activityType;
        private final String description;
        private final Date activityDate;
        private final BigDecimal amount;
        private final String source;

        public MemberActivity(String activityType, String description, Date activityDate, BigDecimal amount, String source) {
            this.activityType = activityType;
            this.description = description == null || description.isBlank() ? activityType : description;
            this.activityDate = activityDate;
            this.amount = amount == null ? BigDecimal.ZERO : amount;
            this.source = source == null || source.isBlank() ? "Admin" : source;
        }

        public String getActivityType() { return activityType; }
        public String getDescription() { return description; }
        public Date getActivityDate() { return activityDate; }
        public BigDecimal getAmount() { return amount; }
        public String getSource() { return source; }
    }

    public static class LoanLedgerRow implements StatementPdfService.LoanStatementRow, Serializable {
        private static final long serialVersionUID = 1L;

        private final Date activityDate;
        private final String activityType;
        private final BigDecimal amount;
        private final BigDecimal runningBalance;

        public LoanLedgerRow(Date activityDate, String activityType, BigDecimal amount, BigDecimal runningBalance) {
            this.activityDate = activityDate;
            this.activityType = activityType;
            this.amount = amount == null ? BigDecimal.ZERO : amount;
            this.runningBalance = runningBalance == null ? BigDecimal.ZERO : runningBalance;
        }

        public Date getActivityDate() { return activityDate; }
        public String getActivityType() { return activityType; }
        public BigDecimal getAmount() { return amount; }
        public BigDecimal getRunningBalance() { return runningBalance; }
    }
}
