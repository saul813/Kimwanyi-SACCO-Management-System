Kimwanyi SACCO Management System
================================

Kimwanyi SACCO Management System is a Java/Jakarta Faces web application for managing SACCO members, savings transactions, loan applications, repayments, and basic administrative reporting.

Core features
-------------

- Member registration, login, profile viewing, and password update.
- Admin approval/deactivation of member accounts.
- Automatic savings account creation when a member is activated.
- Cash deposits and withdrawals with a UGX 20,000 minimum balance rule.
- Member balance and transaction statement history.
- Loan application with one-active-loan validation.
- Loan approval/rejection with flat 10% loan interest.
- Loan repayment posting and automatic `FULLY_REPAID` status update.
- Overdue loan listing using approved loans whose due date has passed.
- Monthly savings interest posting at 5% per annum divided by 12.
- Admin dashboard with member, loan, savings, and active credit totals.

Technology stack
----------------

- Java 17
- Maven
- Jakarta Faces 4 / JSF
- CDI / Weld
- Hibernate ORM
- MariaDB or MySQL
- Apache Tomcat 10.1+

Database setup
--------------

Create a MariaDB/MySQL database:

```sql
CREATE DATABASE kimwanyi_sacco_db;
```

Update database credentials in:

```text
src/main/resources/hibernate.cfg.xml
```

Default local configuration:

```text
jdbc:mariadb://localhost:3306/kimwanyi_sacco_db
username: root
password: root
```

Hibernate is configured with `hibernate.hbm2ddl.auto=update`, so tables are created/updated automatically when the application starts.

Build
-----

```bash
mvn clean package
```

The generated WAR file is:

```text
target/Kimwanyi_SACCO.war
```

Run
---

1. Start MariaDB/MySQL.
2. Ensure `kimwanyi_sacco_db` exists.
3. Build the WAR with Maven.
4. Deploy `target/Kimwanyi_SACCO.war` to Tomcat 10.1+.
5. Open:

```text
http://localhost:8080/Kimwanyi_SACCO/
```

Initial admin account
---------------------

Create an admin user directly in the database for first login:

```sql
INSERT INTO users (full_name, email, national_id, phone_number, password, role, status)
VALUES ('System Admin', 'admin@kimwanyi-sacco.local', 'ADMIN001', '0700000000', 'admin123', 'ADMIN', 'ACTIVE');
```

Use email `admin@kimwanyi-sacco.local` and password `admin123`, then change the password after login.

Main roles
----------

- `MEMBER`: applies for loans, views savings balance, statements, loan status, and profile.
- `ADMIN`: approves/deactivates members, approves/rejects loans, posts repayments, applies monthly interest, and views dashboards.
- `CASHIER` and `MANAGER` are supported role values and are routed to the admin workspace.

Database schema
---------------


