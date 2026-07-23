package com.example.service;

import com.example.dao.AccountDAO;
import com.example.dao.LoanDAO;
import com.example.model.Loan;
import com.example.model.SavingsAccount;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;

@ApplicationScoped
public class LoanValidationService {

    @Inject
    private LoanDAO loanDAO;

    @Inject
    private AccountDAO accountDAO;

    /**
     * Evaluates whether a member is eligible to secure a new credit line.
     * @param userId The unique primary identifier of the member.
     * @param requestedAmount The principal loan amount requested.
     * @return ValidationResult object containing status and failure descriptions if applicable.
     */
    public ValidationResult validateLoanEligibility(Long userId, BigDecimal requestedAmount) {
        // Rule 1: Exclusivity Check (Only 1 active loan permitted at any given time)
        Loan activeLoan = loanDAO.findActiveLoanByUserId(userId);
        if (activeLoan != null) {
            return new ValidationResult(false, "Application Rejected: You currently hold an outstanding active loan.");
        }

        // Retrieve savings balance for limit evaluation
        SavingsAccount account = accountDAO.findByUserId(userId);
        BigDecimal currentSavings = (account != null) ? account.getBalance() : BigDecimal.ZERO;

        // Rule 2: Borrowing Ceiling Check (Maximum credit limit is 3x current savings balance)
        BigDecimal maximumAllowedLimit = currentSavings.multiply(new BigDecimal("3"));
        if (requestedAmount.compareTo(maximumAllowedLimit) > 0) {
            return new ValidationResult(false, String.format(
                    "Application Rejected: Requested amount exceeds your credit ceiling of UGX %,.2f (3x your savings balance).",
                    maximumAllowedLimit
            ));
        }

        return new ValidationResult(true, "Application passed initial automated underwriting checks.");
    }

    // Inner helper class to handle multi-valued validation results cleanly
    public static class ValidationResult {
        private final boolean valid;
        private final String message;

        public ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public boolean isValid() { return valid; }
        public String getMessage() { return message; }
    }
}
