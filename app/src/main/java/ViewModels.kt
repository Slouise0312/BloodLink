package com.example.bloodlink

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val COLLECTION_USERS = "users"

class AuthViewModel(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ViewModel() {

    private val _loggedInUser = MutableStateFlow<AppUser?>(null)
    val loggedInUser: StateFlow<AppUser?> = _loggedInUser.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _needsRoleSelection = MutableStateFlow(false)
    val needsRoleSelection: StateFlow<Boolean> = _needsRoleSelection.asStateFlow()

    /** True after sign-up while the user hasn't verified their email yet. */
    private val _needsEmailVerification = MutableStateFlow(false)
    val needsEmailVerification: StateFlow<Boolean> = _needsEmailVerification.asStateFlow()

    /** Holds the email address for display on the verification screen. */
    private val _pendingVerificationEmail = MutableStateFlow<String?>(null)
    val pendingVerificationEmail: StateFlow<String?> = _pendingVerificationEmail.asStateFlow()

    /**
     * When true, the AuthStateListener does nothing.
     * This prevents a race condition during sign-up:
     *   createUserWithEmailAndPassword → Firebase auto-signs-in → listener fires →
     *   navigates to RoleSelection BEFORE signUp() can send the verification email.
     * Set to true before sign-up, cleared after verification email is sent + sign-out.
     */
    private var suppressAuthListener = false

    init {
        auth.addAuthStateListener { firebaseAuth ->
            if (suppressAuthListener) return@addAuthStateListener
            val user = firebaseAuth.currentUser
            if (user != null) {
                loadUserProfile(user.uid)
            } else {
                _loggedInUser.value = null
                _needsRoleSelection.value = false
            }
        }
    }

    private fun userDoc(uid: String) = firestore.collection(COLLECTION_USERS).document(uid)

    fun loadUserProfile(uid: String, onLoaded: ((AppUser?) -> Unit)? = null) {
        userDoc(uid).get()
            .addOnSuccessListener { doc ->
                if (doc != null && doc.exists()) {
                    val name = doc.getString("name") ?: ""
                    val email = doc.getString("email") ?: auth.currentUser?.email ?: ""
                    val phone = doc.getString("phone") ?: ""
                    val roleStr = doc.getString("role")?.takeIf { it.isNotBlank() }
                    val role = roleStr?.let { UserRole.entries.find { r -> r.name == it } } ?: UserRole.APPLICANT
                    val orgId = doc.getString("orgId")?.takeIf { it.isNotBlank() }
                    val createdAt = (doc.getLong("createdAt") ?: 0L)
                    val updatedAt = (doc.getLong("updatedAt") ?: 0L)
                    val appUser = AppUser(id = uid, role = role, name = name, email = email, phone = phone, orgId = orgId, createdAt = createdAt, updatedAt = updatedAt)
                    _loggedInUser.value = appUser
                    // Show role selection if: no valid role, or STAFF/ADMIN without orgId (needed for events)
                    val needsRole = roleStr == null || (role == UserRole.STAFF || role == UserRole.ADMIN) && orgId == null
                    _needsRoleSelection.value = needsRole
                    if (!needsRole) FcmTokenHelper.updateTokenForUser(uid)
                    onLoaded?.invoke(appUser)
                } else {
                    _loggedInUser.value = null
                    _needsRoleSelection.value = true
                    onLoaded?.invoke(null)
                }
            }
            .addOnFailureListener {
                _authError.value = "Could not load profile. Check your connection."
                _loggedInUser.value = null
                _needsRoleSelection.value = true
                onLoaded?.invoke(null)
            }
    }

    fun createUserWithRole(role: UserRole, orgId: String? = null, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        val email = auth.currentUser?.email ?: ""
        val name = auth.currentUser?.displayName ?: email.substringBefore("@")
        val now = System.currentTimeMillis()
        val data = hashMapOf<String, Any>(
            "name" to name,
            "email" to email,
            "phone" to "",
            "role" to role.name,
            "createdAt" to now,
            "updatedAt" to now
        )
        if (orgId != null && orgId.isNotBlank()) data["orgId"] = orgId
        userDoc(uid).set(data, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                _loggedInUser.value = AppUser(id = uid, role = role, name = name, email = email, orgId = orgId, createdAt = now, updatedAt = now)
                _needsRoleSelection.value = false
                FcmTokenHelper.updateTokenForUser(uid)
                onSuccess()
            }
            .addOnFailureListener { e ->
                onError(e.message ?: "Failed to save role")
            }
    }

    fun login(email: String, password: String, onSuccess: (AppUser?) -> Unit, onError: (String) -> Unit) {
        _authError.value = null
        if (email.isBlank() || password.isBlank()) {
            onError("Email and password are required")
            return
        }
        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener {
                val firebaseUser = auth.currentUser
                val uid = firebaseUser?.uid ?: run { onError("Sign in failed"); return@addOnSuccessListener }

                // Block unverified email users — force them to verify first
                if (firebaseUser.isEmailVerified != true) {
                    _pendingVerificationEmail.value = firebaseUser.email
                    _needsEmailVerification.value = true
                    auth.signOut()  // sign out so they can't bypass
                    onError("Please verify your email before signing in. Check your inbox.")
                    return@addOnSuccessListener
                }

                loadUserProfile(uid) { profile -> onSuccess(profile) }
            }
            .addOnFailureListener { e ->
                _authError.value = e.message
                onError(e.message ?: "Login failed")
            }
    }

    fun signUp(name: String, email: String, password: String, onSuccess: (AppUser?) -> Unit, onError: (String) -> Unit) {
        _authError.value = null
        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            onError("Name, email and password are required")
            return
        }
        if (password.length < 6) {
            onError("Password must be at least 6 characters")
            return
        }

        // Suppress the auth state listener so it doesn't hijack the flow
        // before we can send the verification email
        suppressAuthListener = true

        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener {
                val firebaseUser = auth.currentUser ?: run {
                    suppressAuthListener = false
                    onError("Sign up failed")
                    return@addOnSuccessListener
                }

                // Update display name
                val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                    .setDisplayName(name.trim())
                    .build()
                firebaseUser.updateProfile(profileUpdates)

                // Send verification email
                firebaseUser.sendEmailVerification()
                    .addOnSuccessListener {
                        android.util.Log.d("BloodLink-Auth", "Verification email sent to ${firebaseUser.email}")
                        _pendingVerificationEmail.value = firebaseUser.email
                        _needsEmailVerification.value = true
                        // Sign out — they can only proceed after verifying
                        auth.signOut()
                        suppressAuthListener = false
                        onSuccess(null)
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("BloodLink-Auth", "Failed to send verification: ${e.message}")
                        // Still show verification screen — account was created
                        _pendingVerificationEmail.value = firebaseUser.email
                        _needsEmailVerification.value = true
                        auth.signOut()
                        suppressAuthListener = false
                        onSuccess(null)
                    }
            }
            .addOnFailureListener { e ->
                suppressAuthListener = false
                _authError.value = e.message
                onError(e.message ?: "Sign up failed")
            }
    }

    /** Send a password reset email. Firebase handles the email + reset link. */
    fun sendPasswordResetEmail(email: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        _authError.value = null
        if (email.isBlank()) {
            onError("Please enter your email address")
            return
        }
        auth.sendPasswordResetEmail(email.trim())
            .addOnSuccessListener {
                android.util.Log.d("BloodLink-Auth", "Password reset email sent to $email")
                onSuccess()
            }
            .addOnFailureListener { e ->
                _authError.value = e.message
                onError(e.message ?: "Failed to send reset email")
            }
    }

    /** Re-send the verification email. Requires the user to sign in briefly. */
    fun resendVerificationEmail(email: String, password: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        // Suppress listener — we're signing in temporarily just to resend the email
        suppressAuthListener = true

        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener {
                val user = auth.currentUser
                if (user != null && user.isEmailVerified != true) {
                    user.sendEmailVerification()
                        .addOnSuccessListener {
                            auth.signOut()
                            suppressAuthListener = false
                            onSuccess()
                        }
                        .addOnFailureListener { e ->
                            auth.signOut()
                            suppressAuthListener = false
                            onError(e.message ?: "Failed to resend")
                        }
                } else {
                    auth.signOut()
                    suppressAuthListener = false
                    onError("Email already verified — please sign in.")
                }
            }
            .addOnFailureListener { e ->
                suppressAuthListener = false
                onError(e.message ?: "Could not resend verification email")
            }
    }

    /** Dismiss the email verification screen and go back to login. */
    fun dismissEmailVerification() {
        _needsEmailVerification.value = false
        _pendingVerificationEmail.value = null
        _authError.value = null
    }

    /** Sign in with Google ID token (from GoogleSignIn). Calls onSuccess(profile) or onError.
     *
     * Role assignment rules (Option A):
     *  - If a Firestore doc already exists for this UID (pre-assigned by admin) → use that role.
     *  - If NO doc exists (brand-new Google user) → automatically create as APPLICANT.
     *  - Staff / Admin / EventStaff roles can ONLY be assigned by an admin directly in Firestore.
     *    Google sign-in will NEVER prompt the user to pick a role.
     */
    fun signInWithGoogle(idToken: String, onSuccess: (AppUser?) -> Unit, onError: (String) -> Unit) {
        _authError.value = null
        if (idToken.isBlank()) {
            onError("Google sign-in failed: no token")
            return
        }
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnSuccessListener {
                val uid = auth.currentUser?.uid ?: run { onError("Sign in failed"); return@addOnSuccessListener }
                val email = auth.currentUser?.email ?: ""
                val name = auth.currentUser?.displayName ?: email.substringBefore("@")
                // Check if a Firestore doc already exists for this user
                userDoc(uid).get()
                    .addOnSuccessListener { doc ->
                        if (doc != null && doc.exists()) {
                            // Doc exists — load whatever role admin pre-assigned (or previous session)
                            val roleStr = doc.getString("role")?.takeIf { it.isNotBlank() }
                            val role = roleStr?.let { UserRole.entries.find { r -> r.name == it } } ?: UserRole.APPLICANT
                            val orgId = doc.getString("orgId")?.takeIf { it.isNotBlank() }
                            val phone = doc.getString("phone") ?: ""
                            val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                            val updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                            val appUser = AppUser(id = uid, role = role, name = name, email = email, phone = phone, orgId = orgId, createdAt = createdAt, updatedAt = updatedAt)
                            _loggedInUser.value = appUser
                            _needsRoleSelection.value = false
                            FcmTokenHelper.updateTokenForUser(uid)
                            onSuccess(appUser)
                        } else {
                            // No doc — brand new Google user → auto-create as APPLICANT (no role selection)
                            val now = System.currentTimeMillis()
                            val data = hashMapOf<String, Any>(
                                "name" to name,
                                "email" to email,
                                "phone" to "",
                                "role" to UserRole.APPLICANT.name,
                                "createdAt" to now,
                                "updatedAt" to now
                            )
                            userDoc(uid).set(data, com.google.firebase.firestore.SetOptions.merge())
                                .addOnSuccessListener {
                                    val appUser = AppUser(id = uid, role = UserRole.APPLICANT, name = name, email = email, orgId = null, createdAt = now, updatedAt = now)
                                    _loggedInUser.value = appUser
                                    _needsRoleSelection.value = false
                                    FcmTokenHelper.updateTokenForUser(uid)
                                    onSuccess(appUser)
                                }
                                .addOnFailureListener { e ->
                                    _authError.value = e.message
                                    onError(e.message ?: "Failed to create profile")
                                }
                        }
                    }
                    .addOnFailureListener { e ->
                        _authError.value = e.message
                        onError(e.message ?: "Google sign-in failed")
                    }
            }
            .addOnFailureListener { e ->
                _authError.value = e.message
                onError(e.message ?: "Google sign-in failed")
            }
    }

    fun updateProfile(name: String, phone: String) {
        val uid = auth.currentUser?.uid ?: return
        val now = System.currentTimeMillis()
        userDoc(uid).update(
            mapOf(
                "name" to name,
                "phone" to phone,
                "updatedAt" to now
            )
        )
            .addOnSuccessListener {
                _loggedInUser.value = _loggedInUser.value?.copy(name = name, phone = phone, updatedAt = now)
            }
    }

    /** Update orgId for STAFF/ADMIN so they can see and create events. */
    fun updateOrgId(orgId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        val now = System.currentTimeMillis()
        userDoc(uid).update(mapOf("orgId" to orgId.trim(), "updatedAt" to now))
            .addOnSuccessListener {
                _loggedInUser.value = _loggedInUser.value?.copy(orgId = orgId.trim().ifBlank { null }, updatedAt = now)
                onSuccess()
            }
            .addOnFailureListener { e -> onError(e.message ?: "Failed to save") }
    }

    fun logout() {
        auth.signOut()
        _loggedInUser.value = null
        _authError.value = null
        _needsRoleSelection.value = false
    }

    fun clearAuthError() {
        _authError.value = null
    }
}

// ---- Applicant: current event + screening ----
class ScreeningViewModel(application: Application) : AndroidViewModel(application) {

    // SharedPreferences for persisting event session across app kills
    private val prefs = application.getSharedPreferences("bloodlink_session", Context.MODE_PRIVATE)
    private val KEY_EVENT_ID = "persisted_event_id"

    // Declare all state BEFORE init so they are initialized when init runs
    private val _currentEvent = MutableStateFlow<Event?>(null)
    val currentEvent: StateFlow<Event?> = _currentEvent.asStateFlow()

    private val _currentScreening = MutableStateFlow<Screening?>(null)
    val currentScreening: StateFlow<Screening?> = _currentScreening.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        PhysicalTestEngine.init(application.applicationContext)
        // Restore session on cold start — if user was in an event when app died, rejoin silently
        val savedEventId = prefs.getString(KEY_EVENT_ID, null)
        if (!savedEventId.isNullOrBlank()) {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid != null) {
                _loading.value = true
                EventRepository.getEvent(savedEventId) { event ->
                    if (event != null) {
                        _currentEvent.value = event
                        ScreeningRepository.getOrCreateScreening(savedEventId, event.orgId, uid, null) { screening ->
                            _currentScreening.value = screening
                            _loading.value = false
                        }
                    } else {
                        prefs.edit().remove(KEY_EVENT_ID).apply()
                        _loading.value = false
                    }
                }
            }
        }
    }

    fun setCurrentEvent(event: Event?) {
        _currentEvent.value = event
        if (event == null) _currentScreening.value = null
    }

    fun joinEvent(eventId: String, applicantUid: String, applicantName: String? = null, onJoined: (Screening?) -> Unit) {
        if (eventId.isBlank()) {
            _error.value = "Enter event ID or scan QR"
            onJoined(null)
            return
        }
        _error.value = null
        _loading.value = true
        EventRepository.getEvent(eventId.trim()) { event ->
            if (event != null) {
                _currentEvent.value = event
                // Persist the event ID so it survives app kill / phone restart
                prefs.edit().putString(KEY_EVENT_ID, eventId.trim()).apply()
                ScreeningRepository.getOrCreateScreening(eventId.trim(), event.orgId, applicantUid, applicantName) { screening ->
                    _currentScreening.value = screening
                    _loading.value = false
                    onJoined(screening)
                }
            } else {
                _error.value = "Event not found. Check the code or scan the event QR again."
                _loading.value = false
                onJoined(null)
            }
        }
    }

    fun refreshScreening(screeningId: String) {
        ScreeningRepository.getScreening(screeningId) { _currentScreening.value = it }
    }

    /**
     * Leave the current event. Deletes the screening document from Firestore
     * and clears the persisted session so the next join starts fresh.
     */
    fun leaveEvent(onComplete: () -> Unit = {}) {
        val eventId = _currentEvent.value?.id
        val applicantUid = FirebaseAuth.getInstance().currentUser?.uid
        android.util.Log.d("BloodLink", "leaveEvent called: eventId=$eventId applicantUid=$applicantUid")

        // Clear persisted session on explicit leave
        prefs.edit().remove(KEY_EVENT_ID).apply()

        if (!eventId.isNullOrBlank() && !applicantUid.isNullOrBlank()) {
            ScreeningRepository.deleteScreeningByEventAndUser(eventId, applicantUid) {
                _currentScreening.value = null
                _currentEvent.value = null
                onComplete()
            }
        } else {
            _currentScreening.value = null
            _currentEvent.value = null
            onComplete()
        }
    }

    fun clearError() { _error.value = null }

    override fun onCleared() {
        PhysicalTestEngine.release()
        super.onCleared()
    }
}

// ---- Staff/Admin: events + applicants ----
class EventViewModel : ViewModel() {
    private val _events = MutableStateFlow<List<Event>>(emptyList())
    val events: StateFlow<List<Event>> = _events.asStateFlow()

    private val _selectedEvent = MutableStateFlow<Event?>(null)
    val selectedEvent: StateFlow<Event?> = _selectedEvent.asStateFlow()

    private var registration: com.google.firebase.firestore.ListenerRegistration? = null

    fun startListening(orgId: String) {
        registration?.remove()
        registration = EventRepository.listenEventsByOrg(orgId) { list ->
            _events.value = list
            if (_selectedEvent.value == null && list.isNotEmpty()) {
                val today = System.currentTimeMillis()
                val activeOrFirst = list.firstOrNull { it.status == EventStatus.ACTIVE } ?: list.firstOrNull()
                activeOrFirst?.let { _selectedEvent.value = it }
            }
        }
    }

    fun selectEvent(event: Event?) {
        _selectedEvent.value = event
    }

    fun createEvent(orgId: String, title: String, dateTime: Long, location: String, createdByUid: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        EventRepository.createEvent(orgId, title, dateTime, location, createdByUid, onSuccess, onError)
    }

    fun updateEvent(eventId: String, updates: Map<String, Any>, onSuccess: () -> Unit, onError: (String) -> Unit) {
        EventRepository.updateEvent(eventId, updates, onSuccess, onError)
    }

    fun deleteEvent(eventId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        EventRepository.deleteEvent(eventId, onSuccess = {
            if (_selectedEvent.value?.id == eventId) _selectedEvent.value = null
            onSuccess()
        }, onError = onError)
    }

    override fun onCleared() {
        registration?.remove()
        super.onCleared()
    }
}

enum class ApplicantListFilter { ALL, INCOMPLETE, ELIGIBLE, TEMP_DEFERRED, NOT_ELIGIBLE }

class StaffApplicantsViewModel(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {
    private val _screenings = MutableStateFlow<List<Screening>>(emptyList())
    val screenings: StateFlow<List<Screening>> = _screenings.asStateFlow()

    private val _filter = MutableStateFlow(ApplicantListFilter.ALL)
    val filter: StateFlow<ApplicantListFilter> = _filter.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var registration: com.google.firebase.firestore.ListenerRegistration? = null

    fun setFilter(f: ApplicantListFilter) { _filter.value = f }

    fun setEventId(eventId: String) {
        registration?.remove()
        if (eventId.isBlank()) {
            _screenings.value = emptyList()
            return
        }
        registration = ScreeningRepository.listenScreeningsByEvent(eventId) { _screenings.value = it }
    }

    fun updateFinalOutcome(screeningId: String, outcome: FinalOutcome, notes: String?, applicantUid: String?, onSuccess: () -> Unit, onError: (String) -> Unit) {
        _error.value = null
        _loading.value = true
        val staffUid = auth.currentUser?.uid
        ScreeningRepository.updateFinalOutcome(screeningId, outcome, notes, staffUid, onSuccess = {
            _loading.value = false
            if (!applicantUid.isNullOrBlank()) {
                AlertRepository.createAlert(
                    uid = applicantUid,
                    type = "FINAL_OUTCOME",
                    title = "Screening updated",
                    body = "Staff set your outcome to: ${outcome.label}.",
                    relatedScreeningId = screeningId,
                    onSuccess = { onSuccess() },
                    onError = { onSuccess() }
                )
            } else {
                onSuccess()
            }
        }, onError = {
            _loading.value = false
            _error.value = it
            onError(it)
        })
    }

    fun clearError() { _error.value = null }

    override fun onCleared() {
        registration?.remove()
        super.onCleared()
    }
}

class AlertsViewModel(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {
    private val _alerts = MutableStateFlow<List<AppAlert>>(emptyList())
    val alerts: StateFlow<List<AppAlert>> = _alerts.asStateFlow()

    private var registration: com.google.firebase.firestore.ListenerRegistration? = null

    fun startListening() {
        val uid = auth.currentUser?.uid ?: return
        registration?.remove()
        registration = AlertRepository.listenAlerts(uid) { _alerts.value = it }
    }

    fun markRead(alertId: String) {
        val uid = auth.currentUser?.uid ?: return
        AlertRepository.markAlertRead(uid, alertId, onSuccess = { }, onError = { })
    }

    override fun onCleared() {
        registration?.remove()
        super.onCleared()
    }
}