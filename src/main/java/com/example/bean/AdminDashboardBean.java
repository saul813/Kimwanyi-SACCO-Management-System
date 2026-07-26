package com.example.bean;

import com.example.dao.HibernateUtil;
import com.example.dao.LoanDAO;
import com.example.model.Loan;
import com.example.model.User;
import com.example.service.InterestService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.hibernate.Session;
import java.io.Serializable;
import java.math.BigDecimal;

@Named("adminDashboardBean")
@ViewScoped
public class AdminDashboardBean implements Serializable {
    private static final long serialVersionUID = 1L;

    @Inject private InterestService interestService;
    @Inject private LoanDAO loanDAO;

    private long totalMembersCount;
    private long pendingLoansCount;
    private BigDecimal totalSACCOSavings = BigDecimal.ZERO;
    private BigDecimal activeIssuedCredit = BigDecimal.ZERO;
    private boolean databaseAvailable = true;
    private String databaseStatusMessage = "Database connection active.";

    @PostConstruct
    public void init() {
        loadDashboardMetrics();
    }

    public void loadDashboardMetrics() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            totalMembersCount = session.createQuery(
                    "SELECT COUNT(u) FROM User u WHERE u.role = :role", Long.class)
                    .setParameter("role", User.Role.MEMBER)
                    .uniqueResult();

            pendingLoansCount = session.createQuery(
                    "SELECT COUNT(l) FROM Loan l WHERE l.status = :status", Long.class)
                    .setParameter("status", Loan.LoanStatus.PENDING)
                    .uniqueResult();

            totalSACCOSavings = session.createQuery(
                    "SELECT COALESCE(SUM(a.balance), 0) FROM SavingsAccount a", BigDecimal.class)
                    .uniqueResult();

            activeIssuedCredit = loanDAO.findApprovedOutstandingLoans()
                    .stream()
                    .map(this::calculateOutstandingBalance)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            databaseAvailable = true;
            databaseStatusMessage = "Database connection active.";
        } catch (Throwable ex) {
            totalMembersCount = 0;
            pendingLoansCount = 0;
            totalSACCOSavings = BigDecimal.ZERO;
            activeIssuedCredit = BigDecimal.ZERO;
            databaseAvailable = false;
            databaseStatusMessage = "Database unavailable. Start MariaDB/MySQL and confirm hibernate.cfg.xml credentials.";
            ex.printStackTrace();
        }
    }

    public void applyMonthlySavingsInterest() {
        try {
            int postedCount = interestService.accrueMonthlyInterestForAllAccounts();
            loadDashboardMetrics();
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
                    FacesMessage.SEVERITY_INFO,
                    "Monthly Interest Applied",
                    "Interest credits posted to " + postedCount + " savings account(s)."
            ));
        } catch (Exception e) {
            e.printStackTrace();
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(
                    FacesMessage.SEVERITY_ERROR,
                    "Interest Posting Failed",
                    "Unable to apply monthly savings interest."
            ));
        }
    }

    public long getTotalMembersCount() { return totalMembersCount; }
    public long getPendingLoansCount() { return pendingLoansCount; }
    public BigDecimal getTotalSACCOSavings() { return totalSACCOSavings; }
    public BigDecimal getActiveIssuedCredit() { return activeIssuedCredit; }
    public boolean isDatabaseAvailable() { return databaseAvailable; }
    public String getDatabaseStatusMessage() { return databaseStatusMessage; }

    private BigDecimal calculateOutstandingBalance(Loan loan) {
        if (loan == null || loan.getTotalRepayable() == null || loan.getAmountRepaid() == null) {
            return BigDecimal.ZERO;
        }
        return loan.getTotalRepayable().subtract(loan.getAmountRepaid());
    }
}
