package com.example.dao;

import com.example.model.SavingsAccount;
import com.example.model.Transaction;
import jakarta.enterprise.context.ApplicationScoped;
import org.hibernate.Session;
import org.hibernate.query.Query;
import java.util.List;

@ApplicationScoped
public class AccountDAO {

    // Saves a new account profile during onboarding
    public void saveAccount(SavingsAccount account) {
        org.hibernate.Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            session.merge(account);
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null) transaction.rollback();
            e.printStackTrace();
        }
    }

    // Finds an account profile using the unique internal Member User reference ID
    public SavingsAccount findByUserId(Long userId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<SavingsAccount> query = session.createQuery(
                    "SELECT a FROM SavingsAccount a JOIN FETCH a.member WHERE a.member.id = :uid",
                    SavingsAccount.class
            );
            query.setParameter("uid", userId);
            return query.uniqueResult();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Executes safe balance modifications along with ledger entries
    public void processLedgerTransaction(SavingsAccount account, Transaction ledgerEntry) {
        org.hibernate.Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();

            // Updates account balances and creates the transaction item
            SavingsAccount managedAccount = session.merge(account);
            ledgerEntry.setAccount(managedAccount);
            session.persist(ledgerEntry);

            transaction.commit();
        } catch (Exception e) {
            if (transaction != null) transaction.rollback();
            e.printStackTrace();
        }
    }

    // Pulls account transaction history statements
    public List<Transaction> findStatementsByAccountId(Long accountId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Query<Transaction> query = session.createQuery("FROM Transaction WHERE account.id = :aid ORDER BY createdAt DESC", Transaction.class);
            query.setParameter("aid", accountId);
            return query.list();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<SavingsAccount> findAllAccounts() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "SELECT a FROM SavingsAccount a JOIN FETCH a.member ORDER BY a.accountNumber ASC",
                    SavingsAccount.class
            ).list();
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }
}
