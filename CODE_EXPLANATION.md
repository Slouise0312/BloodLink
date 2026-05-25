# BloodLink App — Code Explanation & Learning Guide

BloodLink is an **organization-centered pre–blood donor screening** app for hospitals/NGOs at blood drives. Applicants complete physical screening (pallor, jaundice sign) and an eligibility questionnaire; staff/admin manage events and verify outcomes.

---

## Best Way to Learn BloodLink

**Recommendation: Learn in this order**

1. **Flow-based first** — Follow one user journey from start to finish (e.g., login → screening → results → match request). This shows how screens connect and what the user experiences.
2. **Layer-by-layer** — After you understand the flow, study each layer: Models → ViewModels → Screens.
3. **Per-file deep dive** — When you’re ready, go file by file and read this guide alongside the code.

**Suggested learning paths**

- **Path A (By feature):** Auth → Screening → Results → Match → Donor flows  
- **Path B (By architecture):** Models.kt → ViewModels.kt → App.kt → Screens.kt → Repositories  
- **Path C (By file):** Start with `Models.kt` (data shapes), then `App.kt` (navigation), then `Screens.kt` (UI).

---

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        MainActivity                               │
│  (Entry point → BloodLinkApp)                                     │
└─────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────┐
│                         BloodLinkApp (App.kt)                     │
│  NavHost: Auth → RoleSelection? → Applicant OR Staff Shell        │
└─────────────────────────────────────────────────────────────────┘
         │                    │                      │
         ▼                    ▼                      ▼
┌──────────────┐    ┌──────────────────┐    ┌──────────────────┐
│  AuthScreen  │    │ ApplicantShell   │    │   StaffShell     │
│  (Login/     │    │  Event|Screening  │    │  Events|Applicants│
│   SignUp)    │    │  Questionnaire|  │    │  Verify|Analytics│
│              │    │  Result|Profile  │    │  Settings        │
└──────────────┘    └──────────────────┘    └──────────────────┘
         │                    │                      │
         └────────────────────┼──────────────────────┘
                              ▼
                    ┌──────────────────┐
                    │   ViewModels     │
                    │ AuthViewModel    │
                    │ Screening/Event  │
                    │ StaffApplicantsVM│
                    └──────────────────┘
                              │
                              ▼
                    ┌──────────────────┐
                    │   Repositories   │
                    │ EventRepository  │
                    │ ScreeningRepository
                    └──────────────────┘
                              │
                              ▼
                    ┌──────────────────┐
                    │   Models.kt      │
                    │ User,Event,Screening
                    └──────────────────┘
```

---

## File-by-File Explanation

### 1. MainActivity.kt

**What it does:** The Android entry point. It sets up Compose and shows the app.

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    testReadUser()  // Optional: Tests Firestore connection
    setContent {
        Surface(color = MaterialTheme.colorScheme.background) {
            BloodLinkApp()  // The root composable
        }
    }
}
```

- `onCreate` runs when the app starts.
- `setContent` switches to Jetpack Compose UI.
- `BloodLinkApp()` is the root screen that contains navigation.
- `testReadUser()` is a debug helper that reads a Firestore document and logs the result.

---

### 2. App.kt

**What it does:** Defines routes and navigation. Decides which screen to show based on auth and role.

**Routes:**
```kotlin
sealed class Route(val path: String) {
    data object Auth : Route("auth")   // Login/SignUp
    data object User : Route("user")   // User flow (screening, match, etc.)
    data object Donor : Route("donor") // Donor flow (requests, availability, etc.)
}
```

**Navigation logic:**
- Start on `Auth`.
- When a user logs in, read `user.role`:
  - `USER` → go to User shell.
  - `DONOR` → go to Donor shell.
- Each shell checks if the user is logged in; if not, redirect back to Auth.
- On logout, go to Auth and clear back stack.

---

### 3. Models.kt

**What it does:** Holds data shapes used across the app. No UI or logic, just structure.

| Block | Purpose |
|-------|---------|
| `UserRole` enum | `USER` or `DONOR` — type of account |
| `BloodType` enum | A+, A-, B+, B-, AB+, AB-, O+, O- |
| `Urgency` enum | LOW, MEDIUM, HIGH, CRITICAL |
| `AppUser` | User profile: id, role, name, email, phone, (donor: bloodType, city, age) |
| `InventoryItem` | Blood type + units in stock + last update |
| `BloodRequest` | Request: bloodType, units, urgency, hospital, date |
| `Donor` | Donor info: name, blood type, city, age, phone, last donation, availability |
| `AppNotification` | Notification: title, message, time, read status |
| `RiskLevel` | LOW, MODERATE, HIGH (screening) |
| `ScreeningTest` | id, title, description for a screening test |
| `ScreeningResult` | Result: testId, score %, riskLevel, timestamp |
| `DonorRequestCard` | UI card for donor requests (recipient, units, location, urgency) |
| `DonorAlertItem` | UI item for donor alerts |
| `DonationLogEntry` | Entry in donation history |

---

### 4. ViewModels.kt

**What it does:** Business logic and state. Keeps UI simple by handling data and side effects.

#### AuthViewModel

| Block | Purpose |
|-------|---------|
| `_loggedInUser` | Current user state (null = logged out) |
| `auth.addAuthStateListener` | Runs when auth state changes (login/logout) |
| `loadUserProfile(uid)` | Reads user document from Firestore and updates `_loggedInUser` |
| `login()` | Signs in with email/password, then loads profile |
| `signUp()` | Creates account, writes profile to Firestore, sets role and optional donor fields |
| `updateProfile()` | Updates name, email, phone in Firestore |
| `logout()` | Signs out and clears user state |

#### ScreeningViewModel

| Block | Purpose |
|-------|---------|
| `_savedResults` | All saved screening results |
| `_lastResult` | Last run result (before saving) |
| `selectTest()` | Sets which screening test is selected |
| `generateSimulatedScore()` | Produces a random score and RiskLevel for demo |
| `saveResult()` | Appends result to `_savedResults` |
| `SCREENING_TESTS` | List of Conjunctiva, Nailbed, Palmar tests |

---

### 5. Screens.kt (Overview)

**What it does:** All UI composables for the app.

| Section | Composable(s) | Purpose |
|---------|---------------|---------|
| Auth | `AuthScreen` | Login / SignUp forms |
| User Shell | `UserShell` | Bottom nav: Screening, Results, Match, Alerts, Profile |
| Screening | `ScreeningNavHost`, `ScreeningHomeScreen`, `ScreeningInstructionsScreen`, `ScreeningCapturePlaceholderScreen`, `ScreeningResultScreen` | Screening flow |
| Results | `ResultsScreen`, `ResultDetailsScreen` | History and details of screening results |
| Match | `UserMatchScreen`, `BloodMatchRequestScreen`, `FindBestMatchesScreen` | Blood match request and find donors |
| Alerts | `UserAlertsScreen` | User notifications |
| Profile | `UserProfileScreen` | Edit profile, logout |
| Donor | `DonorShell`, `DonorHomeScreen`, `DonorRequestsScreen`, `DonorAvailabilityScreen`, `DonorAlertsScreen`, `DonorHistoryScreen` | Donor flows |

**Sample data (top of file):**
- `sampleDonorRequests`, `sampleDonorAlerts` — Donor UI data
- `sampleScreeningResults`, `sampleDonationLog` — Screening / history demo data

---

### 6. HospitalRepository.kt (Interface)

**What it does:** Contract for hospital data. Defines what operations the app expects.

```kotlin
interface HospitalRepository {
    val inventory: StateFlow<List<InventoryItem>>
    val requests: StateFlow<List<BloodRequest>>
    val donors: StateFlow<List<Donor>>
    val notifications: StateFlow<List<AppNotification>>
    fun adjustInventory(bloodType: BloodType, delta: Int)
    fun addRequest(req: BloodRequest)
    // ... etc
}
```

Implementations:
- `FakeRepository` — in-memory, for testing
- `FirebaseRepository` — Firestore-backed

---

### 7. FirebaseRepository.kt

**What it does:** Reads/writes hospital data in Firestore.

| Block | Purpose |
|-------|---------|
| `hospitalRef` | Path to `hospitals/{uid}/...` |
| `attachListeners()` | Snapshot listeners for inventory, requests, donors, notifications |
| `seedInventoryIfNeeded()` | Creates default inventory if empty |
| `adjustInventory()` | Updates units for a blood type and writes to Firestore |
| `addRequest()` | Saves new blood request |
| `parseInventorySnapshot()`, etc. | Converts Firestore documents to Kotlin models |

Firestore layout:
- `hospitals/{uid}/data/inventory` — one doc for inventory
- `hospitals/{uid}/requests` — blood requests
- `hospitals/{uid}/donors` — donors
- `hospitals/{uid}/notifications` — notifications

---

### 8. FakeRepository.kt

**What it does:** In-memory implementation of `HospitalRepository` for development. Uses `MutableStateFlow` and `update` instead of Firestore.

---

### 9. Theme (Theme.kt, Color.kt, Type.kt)

**What it does:** Material 3 theme and styling.

- `BloodLinkTheme` — Wraps the app in `MaterialTheme`.
- Uses dynamic colors on Android 12+ if enabled.
- Defines `Typography` and `ColorScheme`.

---

## User Flow Summary

1. **Launch** → MainActivity → BloodLinkApp → Auth screen.
2. **Login / SignUp** → AuthViewModel → Firestore.
3. **Role-based routing** → User shell or Donor shell.
4. **User shell**
   - Screening: Home → Instructions → Capture (simulated) → Result.
   - Results: History → Details.
   - Match: Match screen → Blood Match Request (blood type, units, urgency, location, ranking, disclaimer) → Find Best Matches.
5. **Donor shell**
   - Requests, Availability, Alerts, History, Profile (with sample data).

---

## Key Compose Concepts Used

| Concept | Where Used | Purpose |
|---------|------------|---------|
| `remember` | All screens | Keep state across recompositions |
| `mutableStateOf` | Form inputs, selections | Reactive UI updates |
| `collectAsState()` | ViewModels | Observe `StateFlow` in composables |
| `LaunchedEffect` | Auth, navigation | React to state changes (e.g. navigate) |
| `LazyColumn` | Lists | Lazy, scrollable lists |
| `NavHost` / `composable` | App.kt | Navigation between screens |

---

## Tips for Learning

1. Run the app and tap through each flow while reading the corresponding composables.
2. Add `Log.d("Flow", "Screen X")` in key composables to see when they run.
3. Change a sample value (e.g. in `sampleDonorRequests`) and see where it appears.
4. Follow one path end-to-end (e.g. Login → Screening → Save result) and trace the data from Model → ViewModel → Screen.
