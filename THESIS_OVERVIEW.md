# Blood Donation Matching System Using Cluster-Aware KNN Algorithm

## Thesis Overview

**Title:** Blood Donation Matching System Using Cluster-Aware KNN Algorithm  
**Purpose:** A mobile application that helps recipients find the **nearest donor or blood bank hospital** with the blood type they need, using a cluster-aware K-Nearest Neighbors (KNN) algorithm for intelligent matching.

---

## System Flow (How It Works)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           BLOODLINK ECOSYSTEM                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│   HOSPITAL ADMIN                    DONORS                    RECIPIENTS    │
│   ──────────────                    ──────                    ──────────    │
│   • Manages blood inventory         • Must be authorized      • Search for  │
│   • Registers & authorizes donors     by partnered hospital     nearest     │
│   • Creates blood requests          • Updates availability      hospital/   │
│   • Views donor matches             • Shows last donation       donor       │
│   • Receives alerts                 • Shares location        • View matches │
│                                                                             │
│                              ↑ CLUSTER-AWARE KNN ↑                          │
│                    Matches based on: blood type, distance,                   │
│                    eligibility, availability, urgency                        │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## User Types & Roles

### 1. Hospital Admin
- **Who:** Staff at partnered blood banks/hospitals
- **Dashboard:** Manually update available blood inventory (units per blood type: A+, A-, B+, B-, AB+, AB-, O+, O-)
- **Donor Management:** Register new donors and authorize them (donors need hospital-verified donation logs)
- **Blood Requests:** Create requests when the hospital needs blood (blood type, units, urgency, location)
- **Matching:** Search for matching donors using blood request parameters
- **Alerts:** Receive notifications (critical requests, new matches, donations completed, low inventory)

### 2. Donors
- **Authorization:** Cannot self-register without recent donation logs at a partnered hospital; must be **authorized by the hospital**
- **Profile:** Blood type, location, last donation date, availability status
- **Visibility:** Recipients see nearest available donors; donors can post when they are available
- **Purpose:** Legitimate, verified donors only (prevents fake registrations)

### 3. Recipients
- **Purpose:** Find the nearest hospital or donor with the blood type they need
- **Flow:** Search by location and blood type → system returns matches ranked by cluster-aware KNN (proximity, availability, eligibility, urgency)

---

## Matching Algorithm Factors (Cluster-Aware KNN)

The system considers these factors when ranking matches:

| Factor | Description |
|--------|-------------|
| **Blood type compatibility** | Recipient's required type must match donor/inventory |
| **Geographic proximity** | Distance to donor/hospital (nearest first) |
| **Time since last donation** | Eligibility (e.g., 90-day gap for whole blood) |
| **Donor availability status** | Only show donors who marked themselves as available |
| **Urgency priority weighting** | Critical requests ranked higher in results |

**Cluster-Aware KNN:** Donors/hospitals are grouped into clusters (e.g., by region) to improve search efficiency and relevance. KNN finds the k nearest matches within relevant clusters.

---

## Key Flows

### Flow 1: Donor Authorization
1. Donor donates blood at a partnered hospital (offline).
2. Hospital staff registers the donor in the app (Full Name, Blood Type, Location, Phone, Age).
3. Donor is now authorized and can log in to update availability and last donation.

### Flow 2: Blood Request & Matching
1. Hospital admin creates a blood request (e.g., O- blood, 3 units, Critical, Metro Manila).
2. Admin clicks "Find Matching Donors."
3. System runs cluster-aware KNN using: blood type, distance from hospital location, donor eligibility, availability.
4. Results show ranked list of donors/blood banks.

### Flow 3: Recipient Search
1. Recipient (or hospital on their behalf) searches for blood type and location.
2. System returns nearest available donors and hospitals.
3. Recipient contacts the match to arrange donation.

---

## App Modules (From Your Screenshots)

| Screen | Purpose |
|--------|---------|
| **Login / Sign Up** | Auth; demo: email containing "hospital" → admin access |
| **Dashboard** | Blood inventory (8 types, +/- adjusters), Blood Requests list, Create Request |
| **Donors** | Total/Available stats, Registered Donors list, Register Donor form |
| **Match** | Blood Request Parameters + Find Matching Donors, Algorithm Factors info |
| **Alerts** | Notifications (Critical, New Match, Donation Completed, Inventory Low) |
| **Settings** | Logout, app configuration |

---

## Clarifications for Your Thesis

1. **Recipient vs Hospital Admin for requests:**  
   Blood requests can be created by hospital admins (on behalf of patients). Recipients may also have a separate flow to search—your app can support both.

2. **Donor login:**  
   Donors authorized by the hospital can log in to update their availability and last donation date. They do not self-register; the hospital registers them first.

3. **Cluster-aware KNN:**  
   Clustering (e.g., by geographic region or blood type) reduces search space and improves speed. KNN then finds the k nearest matches within clusters.

4. **Data model:**  
   - Donors: `id`, `fullName`, `bloodType`, `location`, `phone`, `age`, `lastDonationDaysAgo`, `isAvailable`  
   - Blood inventory: per hospital, per blood type  
   - Blood requests: `bloodType`, `unitsRequired`, `urgency`, `hospitalName`, `location`

---

*This document solidifies the thesis concept. Use it as a reference for your documentation and defense.*
