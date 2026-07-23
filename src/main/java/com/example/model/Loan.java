package com.example.model;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Entity
@Table(name = "loans")
public class Loan implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Connects the loan file back to the borrower profile
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User member;

    @Column(name = "principal_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal principalAmount;

    @Column(name = "interest_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal interestAmount; // Hardcoded at 10% during application processing

    @Column(name = "total_repayable", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalRepayable;

    @Column(name = "amount_repaid", nullable = false, precision = 15, scale = 2)
    private BigDecimal amountRepaid = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LoanStatus status = LoanStatus.PENDING; // PENDING, APPROVED, REJECTED, FULLY_REPAID

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "applied_at", nullable = false, updatable = false)
    private Date appliedAt = new Date();

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "actioned_at")
    private Date actionedAt; // Tracks when a manager approved or rejected the file

    public enum LoanStatus {
        PENDING, APPROVED, REJECTED, FULLY_REPAID
    }

    public Loan() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getMember() { return member; }
    public void setMember(User member) { this.member = member; }

    public BigDecimal getPrincipalAmount() { return principalAmount; }
    public void setPrincipalAmount(BigDecimal principalAmount) { this.principalAmount = principalAmount; }

    public BigDecimal getInterestAmount() { return interestAmount; }
    public void setInterestAmount(BigDecimal interestAmount) { this.interestAmount = interestAmount; }

    public BigDecimal getTotalRepayable() { return totalRepayable; }
    public void setTotalRepayable(BigDecimal totalRepayable) { this.totalRepayable = totalRepayable; }

    public BigDecimal getAmountRepaid() { return amountRepaid; }
    public void setAmountRepaid(BigDecimal amountRepaid) { this.amountRepaid = amountRepaid; }

    public LoanStatus getStatus() { return status; }
    public void setStatus(LoanStatus status) { this.status = status; }

    public Date getAppliedAt() { return appliedAt; }
    public void setAppliedAt(Date appliedAt) { this.appliedAt = appliedAt; }

    public Date getActionedAt() { return actionedAt; }
    public void setActionedAt(Date actionedAt) { this.actionedAt = actionedAt; }
}
