# VeloCity — Demo Walkthrough

A scripted tour of the API. Every step below has an expected result, so you can either follow
along while someone presents it or work through it alone and check the answers yourself.

The interesting parts of this system are the rules it refuses to break: a bike cannot be
double-booked, a rental cannot be cancelled once it has started, a retired bike cannot come back,
and an admin cannot quietly take a bike off the road while a customer still has it. The fixtures
below exist to put each of those rules in front of you.

## Start it

Copy `.env.example` to `.env` and set `JWT_SECRET` (`openssl rand -hex 32`). Compose refuses to
start without it rather than falling back to something guessable.

```bash
docker compose up --build
```

Frontend at `http://localhost:5173`, Swagger UI at `http://localhost:8080/api/swagger-ui.html`.

The data comes from [`001-prod-seed.xml`](src/main/resources/db/changelog/prod/001-prod-seed.xml),
under the Liquibase context `prod` — the only context this deployment runs. That file is candid
about the tradeoff: a portfolio box exists to be browsed by someone who has never met me, so the
seed belongs in it.

**The demo re-anchors itself to today on every application start.** Accounts, fleet statuses and
reservations are `runAlways` changesets, so restarting the container is enough to pull "rented
right now" back to right now and undo whatever the previous visitor changed. That also means
**every reservation is deleted and rebuilt on each boot**, including any a visitor created — which
is the point, and the reason this must never be pointed at a database holding real bookings.

A restart is therefore the reset:

```bash
docker compose restart api
```

`docker compose down -v` is only needed if you also want to drop visitor-registered accounts.

> **If the credentials below are rejected, the database also contains the dev seed.**
> `application-dev.yml` points at `localhost:5432` and Compose publishes Postgres on the same
> port, so running the app locally in the `dev` profile writes into the very database the Compose
> stack uses, and the dev seed owns three of these emails with different passwords.
> `docker compose down -v` clears it.

## Accounts

| Email | Password | Role | Why it exists |
|---|---|---|---|
| `admin@velocity.com` | `Admin123!` | ADMIN | the account you present with |
| `ops@velocity.com` | `Admin123!` | ADMIN | a second admin, so admin #1 has someone to act on |
| `client@velocity.com` | `Client123!` | CLIENT | the main demo client — every client-side step runs here |
| `alice@velocity.com` | `Client123!` | CLIENT | a second client, in a different city |
| `blocked@velocity.com` | `Client123!` | CLIENT | blocked; still has a bike out |
| `newbie@velocity.com` | `Client123!` | CLIENT | no rentals at all — the empty state |

## What is in the database

44 bikes across Warsaw, Wrocław, Gdańsk and Poznań, 30 of them `ACTIVE`. Five months of finished
rentals plus a live window: 5 rentals under way, several upcoming, one unconfirmed.

Seven Warsaw bikes are scripted, and you can spot them by their IDs — they are the only ones
starting `00000000-`:

| Bike | Ends in | State | What it is for |
|---|---|---|---|
| DEMO-1 | `…0001` | ACTIVE, rented **right now** | taking a bike off the road while a rider has it |
| DEMO-2 | `…0002` | ACTIVE, rented **in 2 days** | the same conflict, before the rental starts |
| DEMO-3 | `…0003` | ACTIVE, free | a clean booking target |
| DEMO-4 | `…0004` | MAINTENANCE | returning a bike to service |
| DEMO-5 | `…0005` | LOST | a found bike cannot go straight back to ACTIVE |
| DEMO-6 | `…0006` | RETIRED | retirement is terminal |
| DEMO-7 | `…0007` | ACTIVE, two finished rentals | history does not block a status change |

---

# The walkthrough

## Part 1 — The client

### 1. A blocked account cannot log in

Log in as `blocked@velocity.com` / `Client123!`.

**Expected:** rejected. `CustomUserDetails.isAccountNonLocked()` is false for a `BLOCKED` user, so
Spring Security refuses the authentication even though the password is correct.

Note that this account still has three reservations, one of them under way. Blocking stops the
login; it does not void bookings that already exist.

### 2. Log in as the client

`client@velocity.com` / `Client123!`, then open **My Rentals**.

**Expected:** a mix of finished and live rentals. One is under way, one starts today, two are
upcoming, one is still `PENDING`. One of the cancelled ones reads
*"Cancelled by Admin: Bike transitioned to MAINTENANCE"* — that is the only way
`cancellation_reason` ever gets filled, and step 8 shows it happening live.

### 3. Cancel an upcoming rental — works

Cancel the one starting in **9 days**.

**Expected:** succeeds, status becomes `CANCELLED`, and no reason is recorded (a customer
cancelling their own booking does not owe anyone an explanation).

### 4. Cancel the rental that starts **today** — refused

**Expected:** refused with a late-cancellation error. `Reservation.transitionTo` rejects any
cancel where the current date is not strictly before `start_date`. The rental has begun; it is
too late to walk away from it.

### 5. Book a bike

Go to **Rent a Bike**, pick a date range 3–21 days out, and book.

**Expected:** availability lists one bookable bike per model for Warsaw. The new reservation is
created as `PENDING`, and the price follows the tiering in `RentalCostCalculator`: 25 PLN/day up
to 7 days, 20 PLN/day for 8–14, 15 PLN/day beyond that. A 10-day rental is 200 PLN, not 250.

Try booking a range shorter than 3 days or longer than 21 — rejected before it reaches the service.

### 6. Confirm the pending rental — or let it expire

The seeded `PENDING` reservation is created fresh on every application start.

**Expected, if you confirm it within 30 minutes of the last boot:** it becomes `CONFIRMED`.

**Expected, if you leave it:** it is `CANCELLED` on its own.
`ReservationLifecycleScheduler.cancelStalePendingReservations` runs every 5 minutes and cancels
anything still `PENDING` after 30 minutes, so an abandoned basket cannot hold a bike hostage.

Both outcomes are worth showing. If the instance has been up longer than half an hour, this one is
already cancelled — restart the API, or book a fresh one in step 5 and watch it expire instead.

## Part 2 — The admin

Log out, log in as `admin@velocity.com` / `Admin123!`.

### 7. The dashboard

Open **Admin → Panel**.

**Expected:** the revenue trend covers roughly six months and is not flat. Occupancy is around
**16.67%** — 5 rentals under way against 30 active bikes, which is exactly
`countActiveRentals / countByStatus(ACTIVE)`. Fleet popularity is deliberately uneven: Sprint
Courier S1 well ahead, Cargo King XL trailing.

Cancelled rentals are excluded from both revenue and popularity. If you cancelled one in step 3,
the numbers here already reflect it.

### 8. Take a bike off the road while someone is riding it

**Admin → Bikes**, find DEMO-1 (`…0001`), set it to `MAINTENANCE`.

**Expected:** refused, `409`. The response lists the conflicting rental with the customer's email
and dates. The system will not silently strand a rider.

Retry the same change with `?force=true`:

**Expected:** the bike moves to `MAINTENANCE` and the rental is cancelled with the reason
*"Cancelled by Admin: Bike transitioned to MAINTENANCE"*.

Worth pointing out: this path deliberately uses `cancelByOperator`, not the normal transition. The
customer-facing late-cancel rule from step 4 would have refused this exact cancellation, since the
rental is already under way — and a bike that needs to come off the road is precisely the case
that rule must not block.

Log back in as the client and look at **My Rentals**: the rental is gone, with the reason attached.

### 9. The same conflict, before the rental starts

Try the same thing on DEMO-2 (`…0002`), whose rental starts in 2 days.

**Expected:** the same `409`. The conflict check covers anything `PENDING` or `CONFIRMED` that has
not ended yet, not only what is out on the road today.

### 10. History does not block a status change

Retire DEMO-7 (`…0007`).

**Expected:** succeeds with no conflict prompt, even though this bike has two completed rentals.
`findActiveConflictsForBike` filters on `end_date > today`, so finished business is correctly
ignored. This is the edge case most implementations get wrong in the other direction.

### 11. A found bike cannot go straight back into service

DEMO-5 (`…0005`) is `LOST`. Set it to `ACTIVE`.

**Expected:** refused — *"Found bikes must go to MAINTENANCE first."* A bike that turns up after
going missing gets inspected before it carries another customer.

Now set it to `MAINTENANCE`: **accepted**. From there it can return to `ACTIVE`.

### 12. Retirement is final

DEMO-6 (`…0006`) is `RETIRED`. Try any transition.

**Expected:** refused. Retired is a terminal state — the bike is gone, and the records that point
at it stay honest.

### 13. Two admins editing the same bike

Take the `version` from any bike, change its status once, then send a second request with that
same now-stale `version`.

**Expected:** `409` — *"The bike was modified by another user. Please refresh."* Optimistic
locking, surfaced to the caller rather than silently overwriting.

### 14. User management

**Admin → Users**:

- Block `ops@velocity.com` — **succeeds**; they are an admin, and admins are not immune.
- Promote `newbie@velocity.com` to `ADMIN` — **succeeds**.
- Block **your own** account — **refused**.
- Demote **your own** role — **refused**. You cannot lock yourself out of the panel you are standing in.

---

## Running it on a server

Two things change when this stops being a laptop and becomes a URL someone else opens.

**Secrets come from the host, not the repo.** `docker-compose.yaml` reads `JWT_SECRET` and
`CORS_ALLOWED_ORIGINS` from the environment and refuses to start if the signing key is missing.
Generate a key that exists nowhere else — in particular, not the committed dev fallback in
`application-dev.yml`, which anyone can read — and point CORS at the real frontend origin:

```bash
printf 'JWT_SECRET=%s\nPOSTGRES_PASSWORD=%s\nCORS_ALLOWED_ORIGINS=https://your-app.vercel.app\n' "$(openssl rand -hex 32)" "$(openssl rand -hex 16)" > .env
```

**Restart it nightly so the demo never goes stale.** The `runAlways` changesets re-anchor every
date on boot, but only on boot — a box left running for three weeks drifts just as far as a
frozen seed would. One cron entry keeps it honest:

```bash
(crontab -l 2>/dev/null; echo "0 4 * * * cd /opt/velocity && /usr/bin/docker compose restart api") | crontab -
```

Every morning at 04:00 the tour is back to its scripted state, whatever the previous day's
visitors did to it.

## The one thing this does not seed

Nothing here demonstrates the concurrency guarantee, because a seed cannot: two requests have to
race for it. Fire two simultaneous bookings for the same bike and the same dates and exactly one
survives — not because the service checked first, but because the database refuses the second one
outright, through the `no_overlapping_active_reservations` exclusion constraint in
[`005-add-reservation-exclusion-constraint.xml`](src/main/resources/db/changelog/005-add-reservation-exclusion-constraint.xml).
The test suite covers this.
