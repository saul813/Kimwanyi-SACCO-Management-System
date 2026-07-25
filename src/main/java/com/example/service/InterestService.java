package com.example.service;

import com.example.dao.AccountDAO;
import com.example.model.SavingsAccount;
import com.example.model.Transaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.List;

@ApplicationScoped
public class InterestService {

    @Inject
    private AccountDAO accountDAO;

    // 5% Annual Interest Rate (0.05) divided by 12 months = 0.00416666667 monthly rate multiplier
    private static final BigDecimal ANNUAL_RATE = new BigDecimal("0.05");
    private static final BigDecimal MONTHS_IN_YEAR = new BigDecimal("12");
    private static final BigDecimal MONTHLY_INTEREST_MULTIPLIER = ANNUAL_RATE.divide(MONTHS_IN_YEAR, 10, RoundingMode.HALF_UP);

    /**
     * Accrues and posts monthly interest yields to a specific member's savings account.
     * @param account The active target savings account entity profile.
     */
    public void accrueMonthlyInterest(SavingsAccount account) {
        BigDecimal currentBalance = account.getBalance();

        // Compute interest amount earned for the month
        BigDecimal interestEarned = currentBalance.multiply(MONTHLY_INTEREST_MULTIPLIER).setScale(2, RoundingMode.HALF_UP);

        // Only process if interest earned is greater than zero
        if (interestEarned.compareTo(BigDecimal.ZERO) > 0) {
            // Update account balance
            account.setBalance(currentBalance.add(interestEarned));

            // Create matching unalterable history ledger entry
            Transaction interestTx = new Transaction();
            interestTx.setAccount(account);
            interestTx.setAmount(interestEarned);
            interestTx.setTransactionType(Transaction.TransactionType.INTEREST_CREDIT);
            interestTx.setCreatedAt(new Date());
            interestTx.setDescription("Automated monthly 5% per annum savings interest credit.");

            // Persist atomically to the database tables
            accountDAO.processLedgerTransaction(account, interestTx);
        }
    }

    public int accrueMonthlyInterestForAllAccounts() {
        List<SavingsAccount> accounts = accountDAO.findAllAccounts();
        int postedCount = 0;
        for (SavingsAccount account : accounts) {
            BigDecimal beforeBalance = account.getBalance();
            accrueMonthlyInterest(account);
            if (account.getBalance().compareTo(beforeBalance) > 0) {
                postedCount++;
            }
        }
        return postedCount;
    }
}
