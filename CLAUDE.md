# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a campus restaurant POS system (校園餐廳 POS 系統) — a Kitchen Ordering & Wallet System for a school cafeteria. The repository currently contains planning and proposal documents under `PosSystem/`. No application code exists yet.

## Planned Tech Stack

| Layer | Technology |
|---|---|
| Frontend | React + Vite + TailwindCSS (PWA) |
| Backend | Node.js (NestJS) or Python (FastAPI) |
| Database | PostgreSQL 16 |
| Cache / Scheduling | Redis + node-cron |
| Deployment | Docker + GitHub Actions + cloud VPS |
| Payment (Phase 3–4) | Internal wallet + Linepay / Stripe |

## System Architecture — 5 Subsystems

**1. User & Role Management** — `USERS` is the single user master table. `Students` and `Teachers` extend it via FK. `Admin_Users` and `Kitchen_Users` are separate tables (not FK-linked to USERS).

**2. Menu & Combo Management** — `Dishes` and `Drinks` are item libraries. `Menus` is the daily combo table (controls stock, booking window, sold-out flag). `MENU_ITEMS` is the pivot table linking a Menu to its Dishes/Drinks with `Group_Tag`, `Markup_Price`, and `Is_Default`.

**3. Cart & Pre-checkout** — `Carts` (one active cart per user) + `Cart_Items` (stores chosen non-default `Menu_Item_ID` selections as JSON in `Chosen_Menu_Items`). Used to calculate totals and evaluate deposit trigger before checkout.

**4. Wallet & Order System** — `Orders` → `Order_Items` for the transaction record. `Wallets` stores the running balance. `Wallet_Transactions` is the immutable ledger (TYPE: `Recharge` / `Deduct` / `Refund`). All wallet writes must use a DB transaction with an idempotency key.

**5. Fulfillment & Pickup** — `Meal_Pickups` links to each `Order_Item`. Pickup verification uses phone-last-4 + order-last-3 digits (`Method`). `Actual_Time NULL` means the customer has not picked up. `Admin_Notified` flag triggers overdue handling.

## Key Business Rules

- **Deposit trigger**: `Orders.Total_Qty >= 4` → `Deposit_Amt = 50` (HKD). Otherwise 0.
- **Order status flow**: `Pending_Pay → Deposit_Paid → Fully_Paid → Complete`
- **Booking window**: `Menus.Booking_Open` (e.g. 18:00 prior evening) → `Menus.Booking_Close` (e.g. 11:00 same day). Controlled by scheduled jobs.
- **Stock deduction**: `Available_Stock = Initial_Stock − (sum of Order_Items quantities)`. When it hits 0, set `Is_Sold_Out = 1`. Use Redis to handle concurrent deduction at peak lunch time.
- **Phase 1–2 payments are manual**: Admin collects cash/cheque offline and manually updates `Order_Status` and `Wallet_Transactions` in the admin panel. Automated payment (Phase 3–4) is not yet implemented.
- **`Special_Note`** on `Order_Items` must be capped at 200 characters and filtered at the application layer.

## Key Source Files

- `PosSystem/kitchen.txt` — canonical data model (entity list, fields, ERD notation, 5-subsystem summary)
- `PosSystem/校園餐廳POS系統_提案書.html` — full proposal including business flows, risk table, and phased roadmap
- `PosSystem/附件A_資料庫改進建議.html` — 8 database improvement recommendations (consult before writing DDL)
- `PosSystem/附件B_人力成本明細.html` — cost breakdown (internal reference)

## Development Phases

- **Phase 1–2** (Weeks 1–3): Auth, wallets (manual), menus, orders, deposit logic, pickup verification, admin panel
- **Phase 3–4** (Weeks 4–6): Online top-up, third-party payment (Linepay/Stripe), push notifications, load testing, go-live
