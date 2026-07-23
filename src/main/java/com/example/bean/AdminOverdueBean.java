package com.example.bean;

import com.example.dao.HibernateUtil;
import com.example.model.Loan;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Named;
import org.hibernate.Session;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

@Named("adminOverdueBean")
@ViewScoped
public class AdminOverdueBean implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<Loan> arrearsPortfolio;

    @PostConstruct
    public void init() {
        fetchOutstandingRiskPortfolios();
    }

    /**
     * Queries Hibernate model maps to aggregate open un-repaid credit items.
     */
    public void fetchOutstandingRiskPortfolios() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            // Pulls active approved loans where the amount repaid is still less than total repayable
            this.arrearsPortfolio = session.createQuery(
                            "FROM Loan WHERE status = :status AND amountRepaid < totalRepayable ORDER BY actionedAt ASC",
                            Loan.class)
                    .setParameter("status", Loan.LoanStatus.APPROVED)
                    .list();
        } catch (Exception e) {
            e.printStackTrace();
            this.arrearsPortfolio = Collections.emptyList();
        }
    }

    public List<Loan> getArrearsPortfolio() { return arrearsPortfolio != null ? arrearsPortfolio : Collections.emptyList(); }
}
