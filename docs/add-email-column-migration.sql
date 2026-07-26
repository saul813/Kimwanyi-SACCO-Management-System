-- Kimwanyi SACCO migration: add email login column to existing users table.
-- Run this once against the kimwanyi_sacco_db database.

USE kimwanyi_sacco_db;

-- 1. Add email as nullable first so existing users do not break the migration.
ALTER TABLE users
    ADD COLUMN email VARCHAR(120) NULL AFTER national_id;

-- 2. Give every existing user a temporary unique email.
-- You can edit these emails later from the database if needed.
UPDATE users
SET email = CONCAT('user', id, '@kimwanyi-sacco.local')
WHERE email IS NULL OR email = '';

-- 3. Enforce required + unique email after all rows have values.
ALTER TABLE users
    MODIFY COLUMN email VARCHAR(120) NOT NULL,
    ADD CONSTRAINT uk_users_email UNIQUE (email);

-- 4. Optional: set a practical admin email for the default admin account if it exists.
UPDATE users
SET email = 'admin@kimwanyi-sacco.local'
WHERE national_id = 'ADMIN001';
