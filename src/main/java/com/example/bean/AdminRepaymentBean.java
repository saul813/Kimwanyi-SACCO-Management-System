package com.example.bean;

import com.example.dao.LoanDAO;
import com.example.model.Loan;
import com.example.model.Repayment;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Named("adminRepaymentBean")
@ViewScoped
public class AdminRepaymentBean implements Serializable {
    private static final long serialVersionUID = 1L;

    @Inject private LoanDAO loanDAO;

    private List<Loan> outstandingLoans;
    private List<RepaymentRow> repaymentRows;

    @PostConstruct
    public void init() {
        reloadOutstandingLoans();
    }

    public void reloadOutstandingLoans() {
        this.outstandingLoans = loanDAO.findApprovedOutstandingLoans();
        this.repaymentRows = outstandingLoans.stream()
                .map(loan -> new RepaymentRow(loan, generateReceiptReference()))
                .toList();
    }

    public void postRepayment(RepaymentRow row) {
        FacesContext context = FacesContext.getCurrentInstance();
        if (row == null || row.getLoan() == null || row.getLoan().getId() == null) {
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Invalid Loan", "Select a valid outstanding loan row before posting repayment."));
            return;
        }

        Long loanId = row.getLoan().getId();
        BigDecimal repaymentAmount = row.getRepaymentAmount();
        String receiptReference = row.getReceiptReference();

        if (repaymentAmount == null || repaymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Invalid Amount", "Enter a repayment amount greater than zero."));
            return;
        }

        Loan loan = loanDAO.findById(loanId);
        if (loan == null || loan.getStatus() != Loan.LoanStatus.APPROVED) {
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Invalid Loan", "Only approved outstanding loans can receive repayments."));
            return;
        }

        BigDecimal outstanding = loan.getTotalRepayable().subtract(loan.getAmountRepaid());
        if (repaymentAmount.compareTo(outstanding) > 0) {
            context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, "Overpayment Blocked", "Repayment cannot exceed the outstanding balance."));
            return;
        }

        loan.setAmountRepaid(loan.getAmountRepaid().add(repaymentAmount));
        if (loan.getAmountRepaid().compareTo(loan.getTotalRepayable()) >= 0) {
            loan.setStatus(Loan.LoanStatus.FULLY_REPAID);
        }

        Repayment repayment = new Repayment();
        repayment.setLoan(loan);
        repayment.setAmount(repaymentAmount);
        repayment.setPaidAt(new Date());
        repayment.setReceiptReference(receiptReference == null || receiptReference.isBlank() ? generateReceiptReference() : receiptReference.trim());

        loanDAO.processLoanRepayment(loan, repayment);

        context.addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, "Repayment Posted", "Loan repayment recorded successfully."));
        reloadOutstandingLoans();
    }

    public BigDecimal getOutstandingBalance(Loan loan) {
        if (loan == null || loan.getTotalRepayable() == null || loan.getAmountRepaid() == null) {
            return BigDecimal.ZERO;
        }
        return loan.getTotalRepayable().subtract(loan.getAmountRepaid());
    }

    private String generateReceiptReference() {
        return "RCP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public List<Loan> getOutstandingLoans() { return outstandingLoans; }
    public List<RepaymentRow> getRepaymentRows() { return repaymentRows; }

    public static class RepaymentRow implements Serializable {
        private static final long serialVersionUID = 1L;

        private final Loan loan;
        private BigDecimal repaymentAmount;
        private String receiptReference;

        public RepaymentRow(Loan loan, String receiptReference) {
            this.loan = loan;
            this.receiptReference = receiptReference;
        }

        public Loan getLoan() { return loan; }
        public BigDecimal getRepaymentAmount() { return repaymentAmount; }
        public void setRepaymentAmount(BigDecimal repaymentAmount) { this.repaymentAmount = repaymentAmount; }
        public String getReceiptReference() { return receiptReference; }
        public void setReceiptReference(String receiptReference) { this.receiptReference = receiptReference; }
    }
}
