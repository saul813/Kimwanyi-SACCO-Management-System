package com.example.dao;

import com.example.model.Loan;
import com.example.model.Repayment;
import jakarta.enterprise.context.ApplicationScoped;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import java.util.Date;
import java.util.List;

@ApplicationScoped
public class LoanDAO {

    // Saves a new loan application or modifications to an existing loan
    public void saveLoan(Loan loan) {
        Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            session.merge(loan);
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null) transaction.rollback();
            e.printStackTrace();
        }
    }

    // Finds a specific loan file by its unique Database primary key ID
    public Loan findById(Long id) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.get(Loan.class, id);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Looks up the single active loan for a specific user to enforce exclusivity rules
    public Loan findActiveLoanByUserId(Long userId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<Loan> query = session.createQuery(
                    "FROM Loan WHERE member.id = :uid AND status = :status", Loan.class);
            query.setParameter("uid", userId);
            query.setParameter("status", Loan.LoanStatus.APPROVED);
            return query.uniqueResult();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Pulls all pending loan applications for the Administrative Review grid panel
    public List<Loan> findPendingLoans() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("FROM Loan WHERE status = :status ORDER BY appliedAt ASC", Loan.class)
                    .setParameter("status", Loan.LoanStatus.PENDING)
                    .list();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Pulls historical loan records associated with a specific member account
    public List<Loan> findLoansByUserId(Long userId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<Loan> query = session.createQuery("FROM Loan WHERE member.id = :uid ORDER BY appliedAt DESC", Loan.class);
            query.setParameter("uid", userId);
            return query.list();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<Loan> findApprovedOutstandingLoans() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                            "FROM Loan WHERE status = :status AND amountRepaid < totalRepayable ORDER BY actionedAt ASC",
                            Loan.class)
                    .setParameter("status", Loan.LoanStatus.APPROVED)
                    .list();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    public List<Loan> findOverdueLoans(Date today) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                            "FROM Loan WHERE status = :status AND amountRepaid < totalRepayable AND dueDate < :today ORDER BY dueDate ASC",
                            Loan.class)
                    .setParameter("status", Loan.LoanStatus.APPROVED)
                    .setParameter("today", today)
                    .list();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    // Atomically updates a loan record balance and commits a repayment ledger receipt item
    public void processLoanRepayment(Loan loan, Repayment repayment) {
        Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();

            session.merge(loan);
            session.persist(repayment);

            transaction.commit();
        } catch (Exception e) {
            if (transaction != null) transaction.rollback();
            e.printStackTrace();
        }
    }
}
