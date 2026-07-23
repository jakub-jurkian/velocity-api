# VeloCity Fleet API

*The backend for VeloCity — an e-bike rental platform.*

## Overview

Renting an e-bike sounds like simple CRUD — until two customers try to book the **same bike for overlapping dates at the same moment**. A naive "check if it's free, then save" loses that race: both requests see the bike as available, both succeed, and now one bike is double-booked. Making that impossible — cleanly and at scale — is the problem this project is built around.

VeloCity Fleet API is the backend service behind the VeloCity rental app (React frontend). It handles the full journey: browsing available bikes, registering an account, reserving a specific bike for a date range, pricing the rental, and moving it through its lifecycle (pending → confirmed → completed/cancelled).

**Who uses it**
- **Clients** browse available bikes, book them for chosen dates, and manage their own reservations.
- **Admins** manage the physical fleet (active, maintenance, retired) and oversee users.