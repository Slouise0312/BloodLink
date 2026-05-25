package com.example.bloodlink

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

private const val SCREENINGS = "screenings"

object ScreeningRepository {

    private val db = FirebaseFirestore.getInstance()

    fun getOrCreateScreening(
        eventId: String,
        orgId: String,
        applicantUid: String,
        applicantName: String? = null,
        onResult: (Screening?) -> Unit
    ) {
        db.collection(SCREENINGS)
            .whereEqualTo("eventId", eventId)
            .whereEqualTo("applicantUid", applicantUid)
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                val doc = snap?.documents?.firstOrNull()
                if (doc != null) {
                    onResult(parseScreening(doc.id, doc.data ?: emptyMap()))
                    return@addOnSuccessListener
                }
                val id = db.collection(SCREENINGS).document().id
                val now = System.currentTimeMillis()
                val data = hashMapOf<String, Any>(
                    "eventId" to eventId,
                    "orgId" to orgId,
                    "applicantUid" to applicantUid,
                    "pallorResult" to PhysicalTestResult.NOT_DONE.name,
                    "jaundiceResult" to PhysicalTestResult.NOT_DONE.name,
                    "cyanosisResult" to PhysicalTestResult.NOT_DONE.name,
                    "skinLesionResult" to PhysicalTestResult.NOT_DONE.name,
                    "questionnaireStatus" to QuestionnaireStatus.NOT_DONE.name,
                    "overallStatus" to OverallStatus.INCOMPLETE.name,
                    "finalOutcome" to FinalOutcome.NONE.name,
                    "createdAt" to now,
                    "updatedAt" to now
                )
                applicantName?.let { data["applicantName"] = it }
                db.collection(SCREENINGS).document(id).set(data)
                    .addOnSuccessListener {
                        onResult(Screening(
                            id = id,
                            eventId = eventId,
                            orgId = orgId,
                            applicantUid = applicantUid,
                            applicantName = applicantName,
                            createdAt = now,
                            updatedAt = now
                        ))
                    }
                    .addOnFailureListener { onResult(null) }
            }
            .addOnFailureListener { onResult(null) }
    }

    fun listenScreening(screeningId: String, onResult: (Screening?) -> Unit): ListenerRegistration {
        return db.collection(SCREENINGS).document(screeningId)
            .addSnapshotListener { snap, _ ->
                if (snap != null && snap.exists())
                    onResult(parseScreening(snap.id, snap.data ?: emptyMap()))
                else
                    onResult(null)
            }
    }

    fun updatePhysicalResults(
        screeningId: String,
        pallorResult: PhysicalTestResult,
        pallorScore: Float?,
        jaundiceResult: PhysicalTestResult,
        jaundiceIndex: Float?,
        cyanosisResult: PhysicalTestResult = PhysicalTestResult.NOT_DONE,
        cyanosisScore: Float? = null,
        skinLesionResult: PhysicalTestResult = PhysicalTestResult.NOT_DONE,
        skinLesionScore: Float? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val now = System.currentTimeMillis()
        val updates = mutableMapOf<String, Any>(
            "pallorResult" to pallorResult.name,
            "jaundiceResult" to jaundiceResult.name,
            "cyanosisResult" to cyanosisResult.name,
            "skinLesionResult" to skinLesionResult.name,
            "updatedAt" to now
        )
        pallorScore?.let { updates["pallorScore"] = it }
        jaundiceIndex?.let { updates["jaundiceIndex"] = it }
        cyanosisScore?.let { updates["cyanosisScore"] = it }
        skinLesionScore?.let { updates["skinLesionScore"] = it }
        db.collection(SCREENINGS).document(screeningId).update(updates)
            .addOnSuccessListener { updateOverallStatus(screeningId, onSuccess, onError) }
            .addOnFailureListener { e -> onError(e.message ?: "Update failed") }
    }

    fun updateQuestionnaireResult(
        screeningId: String,
        questionnaireStatus: QuestionnaireStatus,
        deferralReason: String?,
        deferralUntil: Long?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val now = System.currentTimeMillis()
        val updates = mutableMapOf<String, Any>(
            "questionnaireStatus" to questionnaireStatus.name,
            "updatedAt" to now
        )
        deferralReason?.let { updates["deferralReason"] = it }
        deferralUntil?.let { updates["deferralUntil"] = it }
        db.collection(SCREENINGS).document(screeningId).update(updates)
            .addOnSuccessListener { updateOverallStatus(screeningId, onSuccess, onError) }
            .addOnFailureListener { e -> onError(e.message ?: "Update failed") }
    }

    private fun updateOverallStatus(screeningId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        db.collection(SCREENINGS).document(screeningId).get()
            .addOnSuccessListener { doc ->
                if (doc == null || !doc.exists()) { onSuccess(); return@addOnSuccessListener }
                val q = (doc.getString("questionnaireStatus") ?: QuestionnaireStatus.NOT_DONE.name).let { QuestionnaireStatus.entries.find { e -> e.name == it } ?: QuestionnaireStatus.NOT_DONE }
                val pallor = (doc.getString("pallorResult") ?: PhysicalTestResult.NOT_DONE.name).let { PhysicalTestResult.entries.find { e -> e.name == it } ?: PhysicalTestResult.NOT_DONE }
                val jaundice = (doc.getString("jaundiceResult") ?: PhysicalTestResult.NOT_DONE.name).let { PhysicalTestResult.entries.find { e -> e.name == it } ?: PhysicalTestResult.NOT_DONE }
                val cyanosis = (doc.getString("cyanosisResult") ?: PhysicalTestResult.NOT_DONE.name).let { PhysicalTestResult.entries.find { e -> e.name == it } ?: PhysicalTestResult.NOT_DONE }
                val skinLesion = (doc.getString("skinLesionResult") ?: PhysicalTestResult.NOT_DONE.name).let { PhysicalTestResult.entries.find { e -> e.name == it } ?: PhysicalTestResult.NOT_DONE }
                val anyPossibleSign = listOf(pallor, jaundice, cyanosis, skinLesion).any { it == PhysicalTestResult.POSSIBLE_SIGN }
                val allPhysicalDone = listOf(pallor, jaundice, cyanosis, skinLesion).none { it == PhysicalTestResult.NOT_DONE }
                val questionnaireDone = q != QuestionnaireStatus.NOT_DONE

                // Logic priority:
                //  1. Hard fail in questionnaire → NOT_ELIGIBLE immediately (saves donor time)
                //  2. Temp defer in questionnaire → TEMP_DEFERRED immediately
                //  3. Any physical possible-sign → TEMP_DEFERRED
                //  4. Everything done + everything clean → ELIGIBLE
                //  5. Otherwise → INCOMPLETE (still has tests to take)
                val overall = when {
                    q == QuestionnaireStatus.NOT_ELIGIBLE -> OverallStatus.NOT_ELIGIBLE
                    q == QuestionnaireStatus.TEMP_DEFERRED -> OverallStatus.TEMP_DEFERRED
                    anyPossibleSign -> OverallStatus.TEMP_DEFERRED
                    questionnaireDone && allPhysicalDone && !anyPossibleSign -> OverallStatus.ELIGIBLE
                    else -> OverallStatus.INCOMPLETE
                }
                db.collection(SCREENINGS).document(screeningId).update("overallStatus", overall.name, "updatedAt", System.currentTimeMillis())
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { e -> onError(e.message ?: "Update failed") }
            }
            .addOnFailureListener { onSuccess() }
    }

    fun updateFinalOutcome(screeningId: String, finalOutcome: FinalOutcome, staffNotes: String?, staffUid: String?, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val updates = mutableMapOf<String, Any>("finalOutcome" to finalOutcome.name, "updatedAt" to System.currentTimeMillis())
        staffNotes?.let { updates["staffNotes"] = it }
        staffUid?.let { updates["updatedByStaffUid"] = it }
        db.collection(SCREENINGS).document(screeningId).update(updates)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(e.message ?: "Update failed") }
    }

    fun listenScreeningsByEvent(eventId: String, onResult: (List<Screening>) -> Unit): ListenerRegistration {
        return db.collection(SCREENINGS).whereEqualTo("eventId", eventId)
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.mapNotNull { doc -> parseScreening(doc.id, doc.data ?: emptyMap()) } ?: emptyList()
                onResult(list)
            }
    }

    /**
     * Delete a screening document from Firestore by ID.
     */
    fun deleteScreening(screeningId: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        android.util.Log.d("BloodLink", "Deleting screening: $screeningId")
        db.collection(SCREENINGS).document(screeningId).delete()
            .addOnSuccessListener {
                android.util.Log.d("BloodLink", "Screening deleted successfully: $screeningId")
                onSuccess()
            }
            .addOnFailureListener { e ->
                android.util.Log.e("BloodLink", "Delete failed: ${e.message}")
                onError(e.message ?: "Delete failed")
            }
    }

    /**
     * Find and delete any screening matching an eventId + applicantUid combination.
     * Use this instead of deleteScreening when you might not have the screening ID cached.
     */
    fun deleteScreeningByEventAndUser(eventId: String, applicantUid: String, onComplete: () -> Unit = {}) {
        android.util.Log.d("BloodLink", "Looking for screening to delete: event=$eventId user=$applicantUid")
        db.collection(SCREENINGS)
            .whereEqualTo("eventId", eventId)
            .whereEqualTo("applicantUid", applicantUid)
            .get()
            .addOnSuccessListener { snap ->
                val docs = snap?.documents ?: emptyList()
                android.util.Log.d("BloodLink", "Found ${docs.size} screening(s) to delete")
                if (docs.isEmpty()) {
                    onComplete()
                    return@addOnSuccessListener
                }
                var remaining = docs.size
                docs.forEach { doc ->
                    doc.reference.delete()
                        .addOnCompleteListener {
                            remaining--
                            if (remaining <= 0) {
                                android.util.Log.d("BloodLink", "All screenings deleted")
                                onComplete()
                            }
                        }
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("BloodLink", "Query failed: ${e.message}")
                onComplete()
            }
    }

    fun getScreening(screeningId: String, onResult: (Screening?) -> Unit) {
        db.collection(SCREENINGS).document(screeningId).get()
            .addOnSuccessListener { doc ->
                if (doc != null && doc.exists())
                    onResult(parseScreening(doc.id, doc.data ?: emptyMap()))
                else
                    onResult(null)
            }
            .addOnFailureListener { onResult(null) }
    }

    private fun parseScreening(id: String, m: Map<String, Any>): Screening {
        fun str(key: String) = m[key] as? String ?: ""
        fun long(key: String) = (m[key] as? Number)?.toLong() ?: 0L
        fun float(key: String) = (m[key] as? Number)?.toFloat()
        return Screening(
            id = id,
            eventId = str("eventId"),
            orgId = str("orgId"),
            applicantUid = str("applicantUid"),
            applicantName = m["applicantName"] as? String,
            pallorResult = (m["pallorResult"] as? String)?.let { PhysicalTestResult.entries.find { e -> e.name == it } } ?: PhysicalTestResult.NOT_DONE,
            pallorScore = float("pallorScore"),
            jaundiceResult = (m["jaundiceResult"] as? String)?.let { PhysicalTestResult.entries.find { e -> e.name == it } } ?: PhysicalTestResult.NOT_DONE,
            jaundiceIndex = float("jaundiceIndex"),
            cyanosisResult = (m["cyanosisResult"] as? String)?.let { PhysicalTestResult.entries.find { e -> e.name == it } } ?: PhysicalTestResult.NOT_DONE,
            cyanosisScore = float("cyanosisScore"),
            skinLesionResult = (m["skinLesionResult"] as? String)?.let { PhysicalTestResult.entries.find { e -> e.name == it } } ?: PhysicalTestResult.NOT_DONE,
            skinLesionScore = float("skinLesionScore"),
            questionnaireStatus = (m["questionnaireStatus"] as? String)?.let { QuestionnaireStatus.entries.find { e -> e.name == it } } ?: QuestionnaireStatus.NOT_DONE,
            deferralReason = m["deferralReason"] as? String,
            deferralUntil = when (val v = m["deferralUntil"]) {
                is com.google.firebase.Timestamp -> v.toDate().time
                is Number -> v.toLong()
                else -> null
            },
            overallStatus = (m["overallStatus"] as? String)?.let { OverallStatus.entries.find { e -> e.name == it } } ?: OverallStatus.INCOMPLETE,
            finalOutcome = (m["finalOutcome"] as? String)?.let { FinalOutcome.entries.find { e -> e.name == it } } ?: FinalOutcome.NONE,
            staffNotes = m["staffNotes"] as? String,
            createdAt = long("createdAt"),
            updatedAt = long("updatedAt")
        )
    }
}