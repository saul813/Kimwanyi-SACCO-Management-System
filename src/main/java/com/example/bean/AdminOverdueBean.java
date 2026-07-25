package com.example.bean;

import com.example.dao.LoanDAO;
import com.example.model.Loan;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Named("adminOverdueBean")
@ViewScoped
public class AdminOverdueBean implements Serializable {
    private static final long serialVersionUID = 1L;

    @Inject private LoanDAO loanDAO;

    private List<Loan> arrearsPortfolio;

    @PostConstruct
    public void init() {
        fetchOutstandingRiskPortfolios();
    }

    /**
     * Queries Hibernate model maps to aggregate open un-repaid credit items.
     */
    public void fetchOutstandingRiskPortfolios() {
        try {
            this.arrearsPortfolio = loanDAO.findOverdueLoans(new Date());
        } catch (Exception e) {
            e.printStackTrace();
            this.arrearsPortfolio = Collections.emptyList();
        }
    }

    public List<Loan> getArrearsPortfolio() { return arrearsPortfolio != null ? arrearsPortfolio : Collections.emptyList(); }
}
