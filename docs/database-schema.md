# Kimwanyi SACCO Database Schema

```mermaid
erDiagram
    USERS ||--o| SAVINGS_ACCOUNTS : owns
    USERS ||--o{ LOANS : applies_for
    SAVINGS_ACCOUNTS ||--o{ TRANSACTIONS : records
    LOANS ||--o{ REPAYMENTS : receives

    USERS {
        bigint id PK
        string full_name
        string email UK
        string national_id UK
        string phone_number
        string password
        string role
        string status
    }

    SAVINGS_ACCOUNTS {
        bigint id PK
        bigint user_id FK
        string account_number UK
        decimal balance
    }

    TRANSACTIONS {
        bigint id PK
        bigint account_id FK
        decimal amount
        string transaction_type
        datetime created_at
        string description
    }

    LOANS {
        bigint id PK
        bigint user_id FK
        decimal principal_amount
        decimal interest_amount
        decimal total_repayable
        decimal amount_repaid
        string status
        datetime applied_at
        datetime actioned_at
        date due_date
    }

    REPAYMENTS {
        bigint id PK
        bigint loan_id FK
        decimal amount
        datetime paid_at
        string receipt_reference
    }
```

Relationship summary:

- One member user can own one savings account.
- One member user can have many loan records over time.
- One savings account can have many deposit, withdrawal, and interest transactions.
- One loan can have many repayment records.
