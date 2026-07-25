package com.example.bean;

import com.example.dao.LoanDAO;
import com.example.model.Loan;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@Named("adminLoanBean")
@ViewScoped
public class AdminLoanBean implements Serializable {
    private static final long serialVersionUID = 1L;

    @Inject private LoanDAO loanDAO;

    private List<Loan> pendingLoans;

    @PostConstruct
    public void init() {
        reloadApplicationsPipeline();
    }

    public void reloadApplicationsPipeline() {
        this.pendingLoans = loanDAO.findPendingLoans();
    }

    /**
     * Executes manager underwriting decisions for open loan files.
     */
    public void processUnderwritingDecision(Long loanId, String decisionState) {
        FacesContext context = FacesContext.getCurrentInstance();
        try {
            Loan loan = loanDAO.findById(loanId);
            if (loan != null) {
                if ("APPROVED".equals(decisionState)) {
                    loan.setStatus(Loan.LoanStatus.APPROVED);

                    // Business Rule Underwriting Enforcer: Hardcode flat 10% interest calculations
                    BigDecimal principal = loan.getPrincipalAmount();
                    BigDecimal interest = principal.multiply(new BigDecimal("0.10"));

                    loan.setInterestAmount(interest);
                    loan.setTotalRepayable(principal.add(interest));
                    loan.setDueDate(Date.from(LocalDate.now()
                            .plusDays(30)
                            .atStartOfDay(ZoneId.systemDefault())
                            .toInstant()));
                } else {
                    loan.setStatus(Loan.LoanStatus.REJECTED);
                }

                loan.setActionedAt(new Date());
                loanDAO.saveLoan(loan);

                context.addMessage(null, new FacesMessage(
                        FacesMessage.SEVERITY_INFO, "Underwriting Complete", "Loan application status updated to " + decisionState
                ));
                reloadApplicationsPipeline(); // Flush changes out to viewport grid
            }
        } catch (Exception e) {
            e.printStackTrace();
            context.addMessage(null, new FacesMessage(
                    FacesMessage.SEVERITY_ERROR, "Operation Blocked", "System failure during ledger write."
            ));
        }
    }

    public List<Loan> getPendingLoans() { return pendingLoans; }
}
