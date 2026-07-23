package com.example.bean;

import com.example.dao.HibernateUtil;
import com.example.model.Loan;
import com.example.model.User;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import org.hibernate.Session;
import java.io.Serializable;
import java.math.BigDecimal;

@Named("adminDashboardBean")
@ViewScoped
public class AdminDashboardBean implements Serializable {
    private static final long serialVersionUID = 1L;

    private long totalMembersCount;
    private long pendingLoansCount;
    private BigDecimal totalSACCOSavings = BigDecimal.ZERO;
    private BigDecimal activeIssuedCredit = BigDecimal.ZERO;

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

            activeIssuedCredit = session.createQuery(
                    "SELECT COALESCE(SUM(l.totalRepayable - l.amountRepaid), 0) FROM Loan l WHERE l.status = :status",
                    BigDecimal.class)
                    .setParameter("status", Loan.LoanStatus.APPROVED)
                    .uniqueResult();
        }
    }

    public long getTotalMembersCount() { return totalMembersCount; }
    public long getPendingLoansCount() { return pendingLoansCount; }
    public BigDecimal getTotalSACCOSavings() { return totalSACCOSavings; }
    public BigDecimal getActiveIssuedCredit() { return activeIssuedCredit; }
}
