package com.example.bean;

import com.example.dao.AccountDAO;
import com.example.dao.LoanDAO;
import com.example.dao.UserDAO;
import com.example.model.Loan;
import com.example.model.SavingsAccount;
import com.example.model.Transaction;
import com.example.model.User;
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

    // Financial state records variables
    private SavingsAccount activeAccount;
    private List<Transaction> statementHistory;
    private List<Loan> personalLoans;

    // Interactive credit application variables
    private BigDecimal requestedLoanPrincipal;

    // Secure profile credentials parameters
    private String currentPasswordInput;
    private String newPasswordInput;

    @PostConstruct
    public void init() {
        User currentSessionUser = authBean.getLoggedInUser();
        if (currentSessionUser != null) {
            this.activeAccount = accountDAO.findByUserId(currentSessionUser.getId());
            this.personalLoans = loanDAO.findLoansByUserId(currentSessionUser.getId());
            if (activeAccount != null) {
                this.statementHistory = accountDAO.findStatementsByAccountId(activeAccount.getId());
            }
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
    public List<Transaction> getStatementHistory() { return statementHistory; }
    public List<Loan> getPersonalLoans() { return personalLoans; }

    public BigDecimal getRequestedLoanPrincipal() { return requestedLoanPrincipal; }
    public void setRequestedLoanPrincipal(BigDecimal requestedLoanPrincipal) { this.requestedLoanPrincipal = requestedLoanPrincipal; }

    public String getCurrentPasswordInput() { return currentPasswordInput; }
    public void setCurrentPasswordInput(String currentPasswordInput) { this.currentPasswordInput = currentPasswordInput; }

    public String getNewPasswordInput() { return newPasswordInput; }
    public void setNewPasswordInput(String newPasswordInput) { this.newPasswordInput = newPasswordInput; }
    }
