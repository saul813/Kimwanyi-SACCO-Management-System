package com.example.bean;

import com.example.dao.AccountDAO;
import com.example.dao.UserDAO;
import com.example.model.SavingsAccount;
import com.example.model.Transaction;
import com.example.model.User;
import com.example.service.WithdrawalService;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Named("transactionBean")
@ViewScoped
public class TransactionBean implements Serializable {
    private static final long serialVersionUID = 1L;

    @Inject private UserDAO userDAO;
    @Inject private AccountDAO accountDAO;
    @Inject private WithdrawalService withdrawalService;

    // Form input binding parameters
    private String searchNationalId;
    private BigDecimal inputAmount;
    private String inputDescription;

    // Loaded tracking properties
    private SavingsAccount targetAccount;

    /**
     * Resolves and fetches a member's savings account profile using their unique National ID.
     */
    public void lookupMemberAccount() {
        FacesContext context = FacesContext.getCurrentInstance();
        this.targetAccount = null;

        if (searchNationalId == null || searchNationalId.trim().isEmpty()) {
            return;
        }

        User user = userDAO.findByNationalId(searchNationalId.trim().toUpperCase());
        if (user == null) {
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Not Found", "No account matches this National ID."));
            return;
        }

        this.targetAccount = accountDAO.findByUserId(user.getId());
        if (this.targetAccount == null) {
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN, "Unallocated Balance", "Member profile exists but has no active savings ledger."));
        }
    }

    /**
     * Processes cashier cash deposits.
     */
    public void processDeposit() {
        FacesContext context = FacesContext.getCurrentInstance();
        if (targetAccount == null || inputAmount == null || inputAmount.compareTo(BigDecimal.ZERO) <= 0) {
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Invalid Action", "Verify lookup account parameters and amount entries."));
            return;
        }

        try {
            // Update financial parameters
            targetAccount.setBalance(targetAccount.getBalance().add(inputAmount));

            // Generate transaction log
            Transaction tx = new Transaction();
            tx.setAccount(targetAccount);
            tx.setAmount(inputAmount);
            tx.setTransactionType(Transaction.TransactionType.DEPOSIT);
            tx.setCreatedAt(new Date());
            tx.setDescription(inputDescription != null ? inputDescription : "Counter cash deposit payment voucher.");

            accountDAO.processLedgerTransaction(targetAccount, tx);

            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, "Deposit Posted", String.format("Successfully deposited UGX %,.2f", inputAmount)));
            clearFormFields();
        } catch (Exception e) {
            e.printStackTrace();
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_FATAL, "System Error", "Database commit failure."));
        }
    }

    /**
     * Processes cashier cash withdrawals while enforcing the UGX 20,000 minimum balance floor.
     */
    public void processWithdrawal() {
        FacesContext context = FacesContext.getCurrentInstance();
        if (targetAccount == null || inputAmount == null || inputAmount.compareTo(BigDecimal.ZERO) <= 0) {
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Invalid Action", "Verify transaction entries."));
            return;
        }

        // Programmatic Business Rule Guard: Enforce UGX 20,000 balance floor check
        boolean withdrawalAllowed = withdrawalService.verifyWithdrawalEligibility(targetAccount.getMember().getId(), inputAmount);
        if (!withdrawalAllowed) {
            context.addMessage(null, new FacesMessage(
                    FacesMessage.SEVERITY_ERROR,
                    "Transaction Blocked",
                    "Operation Denied: Withdrawal request would violate the mandatory UGX 20,000 minimum balance constraint."
            ));
            return;
        }

        try {
            // Update financial parameters
            targetAccount.setBalance(targetAccount.getBalance().subtract(inputAmount));

            // Generate transaction log
            Transaction tx = new Transaction();
            tx.setAccount(targetAccount);
            tx.setAmount(inputAmount);
            tx.setTransactionType(Transaction.TransactionType.WITHDRAWAL);
            tx.setCreatedAt(new Date());
            tx.setDescription(inputDescription != null ? inputDescription : "Counter cashier teller cash disbursement.");

            accountDAO.processLedgerTransaction(targetAccount, tx);

            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, "Withdrawal Disbursed", String.format("Successfully paid out UGX %,.2f", inputAmount)));
            clearFormFields();
        } catch (Exception e) {
            e.printStackTrace();
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_FATAL, "System Error", "Database processing failure."));
        }
    }

    private void clearFormFields() {
        this.searchNationalId = null;
        this.inputAmount = null;
        this.inputDescription = null;
        this.targetAccount = null;
    }

    // Getters and Setters
    public String getSearchNationalId() { return searchNationalId; }
    public void setSearchNationalId(String searchNationalId) { this.searchNationalId = searchNationalId; }

    public BigDecimal getInputAmount() { return inputAmount; }
    public void setInputAmount(BigDecimal inputAmount) { this.inputAmount = inputAmount; }

    public String getInputDescription() { return inputDescription; }
    public void setInputDescription(String inputDescription) { this.inputDescription = inputDescription; }

    public SavingsAccount getTargetAccount() { return targetAccount; }
}
