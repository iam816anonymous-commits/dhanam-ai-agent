# Phase 2 — Finance parity and local data integrity

Phase 2 is delivered as verified vertical slices. The first slice adds local expense persistence and CRUD; it does not claim all Phase 2 parity is complete.

## Phase 2A — Local Room persistence and expense CRUD

### Research evidence
- Official Room release notes identify Room 2.8.5 as stable in September 2026: https://developer.android.com/jetpack/androidx/releases/room
- Official migration guidance requires preserving data and testing migrations: https://developer.android.com/training/data-storage/room/migrating-db-versions
- Expense-Tracker MIT repository inspected at commit 5c3e4d19d2b7e093d22e6a420b40799b2f8985b0: TransactionEntity.kt, TransactionDao.kt, AppDatabase.kt, TransactionRepositoryImpl.kt and LICENSE. Its entity/DAO use Double for money, so Dhanam does not copy its monetary representation. Repository/Flow boundaries are useful comparison points. No code copied.
- Expenso Apache-2.0 repository inspected at commit 0b495c9758c45c5807f7467a3b43ebf705fe5890: AppDatabase.kt, Daos.kt, Repository.kt and LICENSE. Its data model uses Double and its database includes a destructive migration fallback; both patterns are rejected for Dhanam. No code copied.
- Exact source paths and initial review: ../research/2026-10-29-initial-source-review.md

### Implemented in this slice
- Room database with schema export and no destructive-migration fallback.
- Expense entity stores money in integer paise (Long) and date as LocalDate epoch day.
- DAO and repository boundaries with observable Flow records.
- Expense create/read/update/delete UI, with explicit delete confirmation.
- Money parser rejects malformed, negative, zero, >2-decimal and overflow values.
- Input validation checks title, category, ISO date, amount and note length.
- Unit tests cover money parsing/formatting and core validation.
- No internet permission, remote API, model, or seeded demo finance data.

### Acceptance and remaining verification
- CI must pass unit tests, lint, debug assembly and APK artifact verification before this slice is build-verified.
- Device checks remain required for install, persistence across app restart, CRUD, and API 27 behavior.
- Budgets, Khata/repayments, import/export, PDF, reminders, insights, legacy-data migration and agent tools remain unimplemented.
- The new database starts at version 1. Future schema changes must include exported schemas and tested explicit migrations; do not add destructive migration fallback.
