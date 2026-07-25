package com.example.dao;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

public class HibernateUtil {
    private static SessionFactory sessionFactory;

    private static SessionFactory buildSessionFactory() {
        try {
            // Loads configuration settings from hibernate.cfg.xml automatically
            return new Configuration().configure().buildSessionFactory();
        } catch (Throwable ex) {
            System.err.println("Database Initial SessionFactory creation failed: " + ex);
            throw new IllegalStateException("Unable to initialize Hibernate SessionFactory", ex);
        }
    }

    public static synchronized SessionFactory getSessionFactory() {
        if (sessionFactory == null || sessionFactory.isClosed()) {
            sessionFactory = buildSessionFactory();
        }
        return sessionFactory;
    }

    public static void shutdown() {
        if (sessionFactory != null && !sessionFactory.isClosed()) {
            getSessionFactory().close();
        }
    }
}
