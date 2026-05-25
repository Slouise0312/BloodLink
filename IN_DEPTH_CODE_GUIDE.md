# BloodLink — In-Depth Per-Block Code Explanation

This document explains every significant block of code in BloodLink, file by file, so you understand **what it does** and **why it's there**.

---

# 1. MainActivity.kt

**Purpose:** The Android entry point. When the app starts, `MainActivity` runs first.

---

### Block: `package` and imports

```kotlin
package com.example.bloodlink
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
```

**What it does:**
- `package` declares that this file belongs to `com.example.bloodlink`
- `Bundle` is used to pass data into the activity
- `Log` is for debug output in Logcat
- `ComponentActivity` is the base Activity class for Compose
- `setContent` switches from traditional Views to Compose UI
- `MaterialTheme`, `Surface` are Compose Material 3 components
- `Firebase` and `firestore` are used for Firestore read tests

---

### Block: `class MainActivity : ComponentActivity()`

```kotlin
class MainActivity : ComponentActivity() {
```

**What it does:** Defines the main activity. Extending `ComponentActivity` lets you use Compose and lifecycle-aware APIs.

---

### Block: `onCreate`

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    testReadUser()
    setContent {
        Surface(color = MaterialTheme.colorScheme.background) {
            BloodLinkApp()
        }
    }
}
```

**What it does:**
1. `super.onCreate(savedInstanceState)` — Required activity initialization
2. `testReadUser()` — Optional Firestore connectivity test on app start
3. `setContent { ... }` — Tells Compose to render the UI inside
4. `Surface` — Wraps the app in a Material surface and applies background color
5. `BloodLinkApp()` — Root composable that contains all navigation

---

### Block: `testReadUser()`

```kotlin
private fun testReadUser() {
    val db = Firebase.firestore
    db.collection("users")
        .document("test123")
        .get()
        .addOnSuccessListener { doc ->
            if (doc.exists()) {
                val name = doc.getString("name") ?: "(no name)"
                val role = doc.getString("role") ?: "(no role)"
                Log.d("FirestoreTest", "✅ User found: name=$name role=$role")
            } else {
                Log.w("FirestoreTest", "⚠️ Document users/test123 not found")
            }
        }
        .addOnFailureListener { e ->
            Log.e("FirestoreTest", "❌ Firestore read failed: ${e.message}", e)
        }
}
```

**What it does:**
- Gets Firestore instance and tries to read `users/test123`
- `addOnSuccessListener` — If read succeeds, logs name and role if the document exists
- `addOnFailureListener` — If read fails (e.g. network), logs the error
- This is **diagnostic only** and does not affect app logic

---

# 2. App.kt

**Purpose:** Navigation and role-based routing. Decides which screen to show based on auth state and user role.

---

### Block: `sealed class Route`

```kotlin
sealed class Route(val path: String) {
    data object Auth : Route("auth")
    data object User : Route("user")
    data object Donor : Route("donor")
}
```

**What it does:**
- `Route` is a sealed class that represents navigation destinations
- `Auth` → path `"auth"` (login/signup)
- `User` → path `"user"` (user shell: screening, results, match, etc.)
- `Donor` → path `"donor"` (donor shell: requests, availability, etc.)
- Using `sealed class` ensures all routes are known at compile time

---

### Block: `ScreeningRoutes`

```kotlin
object ScreeningRoutes {
    const val HOME = "screening_home"
    const val INSTRUCTIONS = "screening_instructions/{testId}"
    const val CAPTURE = "screening_capture/{testId}"
    const val RESULT = "screening_result"
    fun instructions(testId: String) = "screening_instructions/$testId"
    fun capture(testId: String) = "screening_capture/$testId"
}
```

**What it does:**
- Holds screening flow route names
- `{testId}` is a path parameter (e.g. `conjunctiva`, `nailbed`, `palmar`)
- `instructions(testId)` and `capture(testId)` build full routes with test ID

---

### Block: `BloodLinkApp` — setup

```kotlin
@Composable
fun BloodLinkApp() {
    val navController = rememberNavController()
    val authVm: AuthViewModel = viewModel()
```

**What it does:**
- `rememberNavController()` — Creates and remembers the navigation controller
- `viewModel()` — Gets (or creates) the shared `AuthViewModel` for this scope

---

### Block: `NavHost` and `startDestination`

```kotlin
NavHost(navController, startDestination = Route.Auth.path) {
```

**What it does:**
- `NavHost` is the container for navigation
- `startDestination = "auth"` — App starts on the Auth screen

---

### Block: `composable(Route.Auth.path)` — Auth screen logic

```kotlin
composable(Route.Auth.path) {
    val currentUser by authVm.loggedInUser.collectAsState()
    LaunchedEffect(currentUser) {
        currentUser?.let { user ->
            when (user.role) {
                UserRole.USER -> navController.navigate(Route.User.path) {
                    popUpTo(Route.Auth.path) { inclusive = true }
                }
                UserRole.DONOR -> navController.navigate(Route.Donor.path) {
                    popUpTo(Route.Auth.path) { inclusive = true }
                }
            }
        }
    }
    AuthScreen(authVm = authVm)
}
```

**What it does:**
1. `collectAsState()` — Observes `loggedInUser` and recomposes when it changes
2. `LaunchedEffect(currentUser)` — Runs when `currentUser` changes (e.g. after login)
3. When user is logged in:
   - `USER` → Navigate to User shell and clear back stack
   - `DONOR` → Navigate to Donor shell and clear back stack
4. `popUpTo(Auth) { inclusive = true }` — Removes Auth from back stack so Back doesn’t return to login
5. `AuthScreen(authVm)` — Renders the login/signup UI

---

### Block: `composable(Route.User.path)` — User screen guard

```kotlin
composable(Route.User.path) {
    val user by authVm.loggedInUser.collectAsState()
    if (user == null || user?.role != UserRole.USER) {
        LaunchedEffect(Unit) {
            navController.navigate(Route.Auth.path) {
                popUpTo(Route.User.path) { inclusive = true }
            }
        }
        return@composable
    }
    UserShell(authVm = authVm, onLogout = { ... })
}
```

**What it does:**
1. Checks if user is logged in and has role `USER`
2. If not → `LaunchedEffect(Unit)` runs once and navigates to Auth, clears User from back stack
3. `return@composable` — Stops rendering; no User shell is shown
4. If valid → Renders `UserShell` with logout callback that returns to Auth

---

### Block: `composable(Route.Donor.path)` — Donor screen guard

Same pattern as User: ensures user is logged in and has role `DONOR` before showing `DonorShell`.

---

# 3. Models.kt

**Purpose:** Defines data structures used across the app. No logic, only shapes.

---

### Block: `UserRole`

```kotlin
enum class UserRole { USER, DONOR }
```

**What it does:** Two account types — regular user (screening, match) vs donor (donate blood).

---

### Block: `BloodType`

```kotlin
enum class BloodType(val label: String) {
    A_POS("A+"), A_NEG("A-"),
    B_POS("B+"), B_NEG("B-"),
    AB_POS("AB+"), AB_NEG("AB-"),
    O_POS("O+"), O_NEG("O-")
}
```

**What it does:** All standard blood types. `label` is the display string (e.g. "A+").

---

### Block: `Urgency`

```kotlin
enum class Urgency { LOW, MEDIUM, HIGH, CRITICAL }
```

**What it does:** Priority level for blood requests.

---

### Block: `AppUser`

```kotlin
data class AppUser(
    val id: String = UUID.randomUUID().toString(),
    val role: UserRole,
    val name: String,
    val email: String,
    val phone: String = "",
    val bloodType: BloodType? = null,  // Donor only
    val city: String = "",              // Donor only
    val age: Int? = null                // Donor only
)
```

**What it does:** Represents the current user. `bloodType`, `city`, `age` are used when `role == DONOR`.

---

### Block: `InventoryItem`, `BloodRequest`, `Donor`, `AppNotification`

Used by hospital / donor features:

- **InventoryItem** — Blood type and units in stock
- **BloodRequest** — Request for blood (type, units, urgency, hospital)
- **Donor** — Donor profile and availability
- **AppNotification** — Notification with read state

---

### Block: `RiskLevel`, `ScreeningTest`, `ScreeningResult`

```kotlin
enum class RiskLevel { LOW, MODERATE, HIGH }

data class ScreeningTest(val id: String, val title: String, val description: String)

data class ScreeningResult(
    val id: String,
    val testId: String,
    val testTitle: String,
    val scorePercent: Int,
    val riskLevel: RiskLevel,
    val timestamp: Long
)
```

**What it does:**
- `RiskLevel` — Anemia risk (LOW/MODERATE/HIGH)
- `ScreeningTest` — One of the three tests (conjunctiva, nailbed, palmar)
- `ScreeningResult` — A single screening result (score, risk, time)

---

### Block: `RequestUrgency`, `DonorRequestCard`, `DonorAlertItem`, `DonationLogEntry`

**What it does:** Data for donor-side UI (request cards, alerts, donation history). Used in Donor screens.

---

# 4. ViewModels.kt

**Purpose:** Business logic and reactive state. Keeps UI simple and testable.

---

### Block: `AuthViewModel` — dependencies

```kotlin
class AuthViewModel(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ViewModel() {
```

**What it does:** Accepts Firebase Auth and Firestore. Defaults use global singletons.

---

### Block: `_loggedInUser` and `loggedInUser`

```kotlin
private val _loggedInUser = MutableStateFlow<AppUser?>(null)
val loggedInUser: StateFlow<AppUser?> = _loggedInUser.asStateFlow()
```

**What it does:**
- `_loggedInUser` — Mutable state; ViewModel writes to it
- `loggedInUser` — Read-only `StateFlow`; UI observes it via `collectAsState`
- `null` = logged out, non-null = logged in with profile

---

### Block: `init` — Auth state listener

```kotlin
init {
    auth.addAuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        if (user != null) {
            loadUserProfile(user.uid)
        } else {
            _loggedInUser.value = null
        }
    }
}
```

**What it does:**
- Listens to Firebase Auth state changes (login, logout, token refresh)
- If user exists → load profile from Firestore
- If not → clear `_loggedInUser`

---

### Block: `userDoc(uid)`

```kotlin
private fun userDoc(uid: String) = firestore.collection(COLLECTION_USERS).document(uid)
```

**What it does:** Shortcut to `users/{uid}` document reference.

---

### Block: `loadUserProfile`

Reads `users/{uid}` and:

- If document exists: parses name, email, phone, role, bloodType, city, age
- If not: builds minimal `AppUser` from Firebase Auth
- Handles Firestore parse errors and still returns a basic user
- Updates `_loggedInUser` and optionally runs `onLoaded` callback

---

### Block: `login`

```kotlin
fun login(email: String, password: String, onSuccess: (AppUser) -> Unit, onError: (String) -> Unit) {
    _authError.value = null
    if (email.isBlank() || password.isBlank()) {
        onError("Email and password are required")
        return
    }
    auth.signInWithEmailAndPassword(email.trim(), password)
        .addOnSuccessListener { ... loadUserProfile(...) }
        .addOnFailureListener { ... onError(...) }
}
```

**What it does:**
1. Clears previous auth error
2. Validates input
3. Calls Firebase Auth sign-in
4. On success: loads profile, updates `_loggedInUser`, calls `onSuccess`
5. On failure: sets `_authError`, calls `onError`

---

### Block: `signUp`

- Validates name, email, password (min 6 chars)
- Creates Firebase Auth account
- Builds Firestore profile: name, email, phone, role; for donors adds bloodType, city, age
- Writes to `users/{uid}` and updates `_loggedInUser`
- Uses callbacks for success/failure

---

### Block: `updateProfile`, `logout`, `clearAuthError`

- **updateProfile** — Updates name, email, phone in Firestore and in `_loggedInUser`
- **logout** — Signs out and clears user/error
- **clearAuthError** — Clears `_authError`

---

### Block: `ScreeningViewModel` — state

```kotlin
private val _savedResults = MutableStateFlow<List<ScreeningResult>>(emptyList())
val savedResults: StateFlow<List<ScreeningResult>> = _savedResults.asStateFlow()
private val _lastResult = MutableStateFlow<ScreeningResult?>(null)
val lastResult: StateFlow<ScreeningResult?> = _lastResult.asStateFlow()
var selectedTest: ScreeningTest? = null
```

**What it does:**
- `_savedResults` — All saved screening results
- `_lastResult` — Most recent result (before saving)
- `selectedTest` — Currently chosen test for capture

---

### Block: `generateSimulatedScore`

```kotlin
fun generateSimulatedScore(): ScreeningResult? {
    val test = selectedTest ?: return null
    val score = (0..100).random()
    val riskLevel = when {
        score <= 39 -> RiskLevel.LOW
        score <= 69 -> RiskLevel.MODERATE
        else -> RiskLevel.HIGH
    }
    val result = ScreeningResult(...)
    _lastResult.value = result
    return result
}
```

**What it does:** Simulates a screening result with random score and risk level. No real analysis.

---

### Block: `saveResult`, `clearLastResult`

- **saveResult** — Appends a result to `_savedResults`
- **clearLastResult** — Clears `_lastResult` and `selectedTest`

---

### Block: `SCREENING_TESTS`

Static list of three tests: Conjunctiva, Nailbed, Palmar.

---

# 5. HospitalRepository.kt

**Purpose:** Interface for hospital-related data. Allows swapping real (Firebase) vs fake implementations.

---

### Block: interface definition

```kotlin
interface HospitalRepository {
    val inventory: StateFlow<List<InventoryItem>>
    val requests: StateFlow<List<BloodRequest>>
    val donors: StateFlow<List<Donor>>
    val notifications: StateFlow<List<AppNotification>>
    fun adjustInventory(bloodType: BloodType, delta: Int)
    fun addRequest(req: BloodRequest)
    fun toggleDonorAvailability(donorId: String)
    fun addDonor(donor: Donor)
    fun markAllRead()
    fun markNotificationRead(notificationId: String)
}
```

**What it does:** Declares reactive streams and write operations. Any implementation must provide these.

---

# 6. FirebaseRepository.kt

**Purpose:** Firestore-backed implementation of `HospitalRepository`.

---

### Block: StateFlow fields and listeners

```kotlin
private val _inventory = MutableStateFlow<List<InventoryItem>>(emptyList())
override val inventory: StateFlow<List<InventoryItem>> = _inventory.asStateFlow()
// ... similar for requests, donors, notifications
private var inventoryRegistration: ListenerRegistration? = null
// ...
```

**What it does:** Each domain has a `MutableStateFlow` and a Firestore snapshot listener. When Firestore data changes, the listener updates the flow.

---

### Block: `init` — attachListeners, seedInventoryIfNeeded

- `attachListeners()` — Registers snapshot listeners for inventory, requests, donors, notifications
- `seedInventoryIfNeeded()` — If inventory doc doesn’t exist, creates it with default values

---

### Block: `adjustInventory`, `addRequest`, `toggleDonorAvailability`, etc.

Each override:
1. Updates local state (`_inventory`, etc.)
2. Writes changes to Firestore
Listeners will propagate updates to other clients.

---

### Block: Parsing helpers

`parseInventorySnapshot`, `parseRequestsSnapshot`, `parseDonorsSnapshot`, `parseNotificationsSnapshot` convert Firestore documents into `InventoryItem`, `BloodRequest`, `Donor`, `AppNotification`.

Extension functions like `InventoryItem.toMap()` serialize models for Firestore.

---

# 7. FakeRepository.kt

**Purpose:** In-memory implementation for development and testing.

- Uses `MutableStateFlow` and `update` instead of Firestore
- Starts with hardcoded sample data
- All operations mutate flows directly; no network calls

---

# 8. Theme (Theme.kt, Color.kt, Type.kt)

**Purpose:** Material 3 theme (colors, typography).

- **Color.kt** — Defines purple/pink colors for light/dark
- **Type.kt** — Defines `Typography` (e.g. `bodyLarge`)
- **Theme.kt** — `BloodLinkTheme` selects color scheme (dynamic or static) and applies `Typography`

---

# 9. Screens.kt — Overview

**Purpose:** All Compose UI. Large file; broken into logical sections.

---

## Screens.kt — Top-level constants and sample data

```kotlin
private val BloodRed = Color(0xFFD50000)
private val sampleDonorRequests = listOf(...)
private val sampleDonorAlerts = listOf(...)
private val sampleScreeningResults = listOf(...)
private val sampleDonationLog = listOf(...)
```

**What it does:** Shared color and sample data for Donor and Results UI.

---

## AuthScreen

**State:**
- `tab` — 0 = Login, 1 = Sign Up
- `email`, `pass`, `name`, `role`, `donorBloodType`, `donorCity`, `donorAge`, `isLoading`

**Layout:**
1. Logo and app name
2. Segmented tabs (Login / Sign Up)
3. Card with form:
   - Sign Up: role chips, name, donor fields (blood type, city, age)
   - Email and password
   - Error message
   - Submit button (Login or Create Account)

**Logic:** On submit, calls `authVm.login` or `authVm.signUp` with appropriate callbacks.

---

## SegmentedTabs, SegmentButton, RoleChip

Reusable UI pieces for tab switching and role selection on Auth.

---

## UserShell

**State:** `tab` — SCREENING, RESULTS, MATCH, ALERTS, PROFILE

**Layout:**
- Top bar with "BloodLink" and "Anemia Risk Screening"
- Bottom `NavigationBar` with 5 tabs
- Content: `when (tab)` shows `ScreeningNavHost`, `ResultsScreen`, `UserMatchScreen`, `UserAlertsScreen`, or `UserProfileScreen`

---

## ScreeningNavHost

`NavHost` for screening:
- `screening_home` → `ScreeningHomeScreen`
- `screening_instructions/{testId}` → `ScreeningInstructionsScreen`
- `screening_capture/{testId}` → `ScreeningCapturePlaceholderScreen`
- `screening_result` → `ScreeningResultScreen`

---

## ScreeningHomeScreen

- Important notice card (screening only, not diagnosis)
- "Choose a Screening Test" with cards for Conjunctiva, Nailbed, Palmar
- Each card: Start Test, View Instructions
- "How It Works" and "Quick & Easy" info cards

---

## ScreeningInstructionsScreen

- `when (testId)` loads test-specific title, steps, best practices, mistakes, tips
- Red header
- Step-by-step list, Best Practices, Common Mistakes, Pro Tips, Disclaimer
- Back and "Proceed to Capture" buttons

---

## ScreeningCapturePlaceholderScreen

- Red header with test name
- Dark “camera preview” area (placeholder)
- Positioning instruction
- Cancel and Capture buttons — Capture calls `generateSimulatedScore()` and navigates to result

---

## ScreeningResultScreen

- Reads `lastResult` from ViewModel
- Shows score and risk level with color
- Disclaimer
- Retake, Save Result, Proceed to Match (enabled only when risk is HIGH)

---

## ResultsScreen

- Header "Results"
- Latest Result card with "View Details"
- Filter chips: All, Low, Moderate, High
- Screening history list — tapping opens `ResultDetailsScreen`
- Uses `sampleScreeningResults` when no saved results

---

## ResultDetailsScreen

- Back header
- Summary card (risk level, score, date)
- Interpretation
- Recommended next steps
- "Find Blood Support Now" → switches to Match tab
- Medical disclaimer

---

## UserMatchScreen

**State:** `showBloodMatchRequest`, `showFindBestMatches`

**Flow:**
- If both true → `FindBestMatchesScreen` (Back → `showFindBestMatches = false`)
- If only `showBloodMatchRequest` → `BloodMatchRequestScreen` (Back / Continue)
- Otherwise → Match landing: Emergency Blood Support card, How Blood Matching Works, Matching Factors, Privacy Notice
- "Find Blood Support Now" sets `showBloodMatchRequest = true`

---

## BloodMatchRequestScreen

**State:** `selectedBloodType`, `units`, `selectedUrgency`, `location`

**Layout:**
- Red header with Back
- Blood Type chips (8 types)
- Units Needed stepper
- Urgency Level (Immediate, Urgent, Moderate)
- Location input + Use Current Location
- How We Rank Donors (4 criteria)
- Disclaimer
- "Find Best Matches" button → calls `onContinue`

---

## FindBestMatchesScreen

- Red header with Back
- Request Urgency (Urgent 2–3 days / Moderate 1 week)
- Location section
- How We Rank Donors
- Disclaimer
- "Find Best Matches" button

---

## UserAlertsScreen, UserProfileScreen

- **UserAlertsScreen** — Placeholder for user notifications
- **UserProfileScreen** — Editable name, email, phone and Save; logout button

---

## DonorShell, DonorHomeScreen

- Donor tab shell with bottom nav
- Donor home with requests, availability, alerts, history, profile

---

## DonorRequestsScreen, DonorAvailabilityScreen, DonorAlertsScreen, DonorHistoryScreen

- Use `sampleDonorRequests`, `sampleDonorAlerts`, `sampleDonationLog` to show donor-facing UI (requests, availability, alerts, history)

---

## DonorProfileScreen

- Donor profile and settings

---

# Quick reference: Where things live

| What                | File                | Block / Component          |
|---------------------|---------------------|----------------------------|
| App entry           | MainActivity.kt     | `onCreate`, `setContent`   |
| Navigation          | App.kt              | `NavHost`, `composable`    |
| Data shapes         | Models.kt           | data classes, enums        |
| Auth logic          | ViewModels.kt       | `AuthViewModel`            |
| Screening logic     | ViewModels.kt       | `ScreeningViewModel`       |
| Hospital data API   | HospitalRepository  | interface                  |
| Firestore impl      | FirebaseRepository  | listeners, parsing         |
| In-memory impl      | FakeRepository      | `MutableStateFlow`         |
| All UI              | Screens.kt          | composables listed above   |
| Theme               | Theme.kt, Color.kt, Type.kt | `BloodLinkTheme`     |

---

*Use this guide alongside the source code. Open a file, find the block, and read the matching explanation.*
