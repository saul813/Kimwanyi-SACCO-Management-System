package com.example.service;

import com.example.dao.AccountDAO;
import com.example.model.SavingsAccount;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;

@ApplicationScoped
public class WithdrawalService {

    @Inject
    private AccountDAO accountDAO;

    // Hardcoded minimum operational balance floor constraint for Kimwanyi SACCO
    private static final BigDecimal MINIMUM_BALANCE_FLOOR = new BigDecimal("20000.00");

    /**
     * Verifies if a requested ledger withdrawal is legally backed.
     * @param userId The unique identifier of the target member profile.
     * @param withdrawalAmount The value of immediate cash to withdraw.
     * @return true if the resulting ledger balance remains equal to or above UGX 20,000; false otherwise.
     */
    public boolean verifyWithdrawalEligibility(Long userId, BigDecimal withdrawalAmount) {
        SavingsAccount account = accountDAO.findByUserId(userId);
        if (account == null) {
            return false;
        }

        BigDecimal currentBalance = account.getBalance();

        // Calculate the theoretical final position if the transaction executes
        BigDecimal projectBalancePosition = currentBalance.subtract(withdrawalAmount);

        // Enforce balance floor rules programmatically (blocks negative positions and overdrafts)
        return projectBalancePosition.compareTo(MINIMUM_BALANCE_FLOOR) >= 0;
    }
}
