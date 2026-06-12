# Neon Database Setup & Migration

## Quick Reference — Dump & Restore

```powershell
# 1. Dump from local Docker (run from project root)
docker exec -t bootcamp-restaurant-postgres-1 pg_dump -U postgres --no-owner --no-acl restaurant_db > dump.sql

# 2. Restore to Neon (replace URL with your actual Neon connection string)
docker run --rm -i postgres:18 psql "postgresql://kim:password@ep-xxx.ap-southeast-1.aws.neon.tech/restaurant_db?sslmode=require" < dump.sql
```

---

## 1. Create Neon Database

1. Sign up at https://neon.tech
2. **New Project** → name: `canteen`
3. **Database name**: `restaurant_db`
4. Region: pick closest to Zeabur deployment (e.g. `aws-ap-southeast-1` for Singapore)
5. Go to **Dashboard → Connection Details** → switch to **JDBC** tab
6. Copy the connection string — looks like:
   ```
   jdbc:postgresql://ep-xxx-xxx.ap-southeast-1.aws.neon.tech/restaurant_db?sslmode=require
   ```
   Also note the **psql** connection string (needed for data import):
   ```
   postgresql://kim:password@ep-xxx-xxx.ap-southeast-1.aws.neon.tech/restaurant_db?sslmode=require
   ```

---

## 2. Set Zeabur Environment Variables

Zeabur Dashboard → your service → **Variables** → add:

| Key | Value |
|-----|-------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://ep-xxx...neon.tech/restaurant_db?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | your Neon username |
| `SPRING_DATASOURCE_PASSWORD` | your Neon password |

Redeploy → Spring Boot `ddl-auto: update` auto-creates all tables on first boot.

---

## 3. Dump Existing Data (from local Docker)

Make sure local Docker Compose is running (`docker-compose up -d`), then:

```powershell
# Find the postgres container name
docker ps

# Dump to dump.sql (replace 'bootcamp-restaurant-postgres-1' with your actual container name)
docker exec -t bootcamp-restaurant-postgres-1 pg_dump -U postgres --no-owner --no-acl restaurant_db > dump.sql
```

`dump.sql` will be created in your current directory.

---

## 4. Restore Data to Neon

### Option A — Docker (no local psql needed, recommended on Windows)

```powershell
# Use a temporary postgres container to push dump.sql into Neon
# Replace the URL with your actual Neon psql connection string
docker run --rm -i postgres:18 psql "postgresql://kim:password@ep-xxx.ap-southeast-1.aws.neon.tech/restaurant_db?sslmode=require" < dump.sql
```

### Option B — local psql (if already installed)

```powershell
psql "postgresql://kim:password@ep-xxx.ap-southeast-1.aws.neon.tech/restaurant_db?sslmode=require" < dump.sql
```

Check if psql is installed:
```powershell
psql --version
```

---

## 5. Verify

Connect to Neon and check tables exist:

```powershell
docker run --rm -it postgres:18 psql "postgresql://kim:password@ep-xxx.ap-southeast-1.aws.neon.tech/restaurant_db?sslmode=require" -c "\dt"
```

You should see all tables: `users`, `dishes`, `drinks`, `menus`, `orders`, `order_items`, `wallets`, `wallet_transactions`, etc.

---

## Memory Freed on Zeabur

| Before | After |
|--------|-------|
| postgres 400m + app 700m + nginx 50m | app 700m + nginx 50m |
| ~1.15 GB | ~750 MB |
| ~850 MB headroom | ~1.25 GB headroom |

---

## Local Dev (unchanged)

`docker-compose.override.yml` adds postgres back automatically when running locally:

```powershell
docker-compose up -d   # still works, postgres starts locally as before
```
