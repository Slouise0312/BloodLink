package com.example.bloodlink

/**
 * Organization-centered pre–blood donor screening.
 * Firestore: users/{uid}, organizations/{orgId}, events/{eventId}, screenings/{screeningId}, users/{uid}/alerts/{alertId}
 */

// ---- ROLES ----
enum class UserRole { APPLICANT, STAFF, ADMIN }

// ---- USER ----
data class AppUser(
    val id: String,
    val role: UserRole,
    val name: String,
    val email: String,
    val phone: String = "",
    val orgId: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

// ---- ORGANIZATION ----
data class Organization(
    val id: String,
    val name: String,
    val address: String = "",
    val contact: String = "",
    val createdAt: Long = 0L
)

// ---- EVENTS ----
enum class EventStatus { ACTIVE, CLOSED }

data class Event(
    val id: String,
    val orgId: String,
    val title: String,
    val dateTime: Long,
    val location: String = "",
    val status: EventStatus = EventStatus.ACTIVE,
    val createdByUid: String,
    val createdAt: Long,
    val updatedAt: Long,
    val qrPayload: String? = null
)

// ---- PHYSICAL TEST RESULTS ----
enum class PhysicalTestResult { NOT_DONE, NORMAL, POSSIBLE_SIGN }

// ---- QUESTIONNAIRE / ELIGIBILITY ----
enum class QuestionnaireStatus { NOT_DONE, ELIGIBLE, TEMP_DEFERRED, NOT_ELIGIBLE }

// ---- OVERALL & FINAL OUTCOME ----
enum class OverallStatus { INCOMPLETE, ELIGIBLE, TEMP_DEFERRED, NOT_ELIGIBLE }

enum class FinalOutcome(val label: String) {
    NONE("—"),
    ARRIVED("Arrived"),
    ACCEPTED_ONSITE("Accepted onsite"),
    DEFERRED_ONSITE("Deferred onsite"),
    DONATED("Donated"),
    NO_SHOW("No show")
}

// ---- SCREENING ----
data class Screening(
    val id: String,
    val eventId: String,
    val orgId: String,
    val applicantUid: String,
    val applicantName: String? = null,
    // Physical test results
    val pallorResult: PhysicalTestResult = PhysicalTestResult.NOT_DONE,
    val pallorScore: Float? = null,
    val jaundiceResult: PhysicalTestResult = PhysicalTestResult.NOT_DONE,
    val jaundiceIndex: Float? = null,
    val cyanosisResult: PhysicalTestResult = PhysicalTestResult.NOT_DONE,
    val cyanosisScore: Float? = null,
    val skinLesionResult: PhysicalTestResult = PhysicalTestResult.NOT_DONE,
    val skinLesionScore: Float? = null,
    // Questionnaire
    val questionnaireStatus: QuestionnaireStatus = QuestionnaireStatus.NOT_DONE,
    val deferralReason: String? = null,
    val deferralUntil: Long? = null,
    // Overall
    val overallStatus: OverallStatus = OverallStatus.INCOMPLETE,
    val finalOutcome: FinalOutcome = FinalOutcome.NONE,
    val staffNotes: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

// ---- ALERTS ----
data class AppAlert(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val relatedEventId: String? = null,
    val relatedScreeningId: String? = null,
    val createdAt: Long,
    val read: Boolean = false
)

// ---- QUESTIONNAIRE RULE ENGINE OUTPUT ----
data class QuestionnaireOutcome(
    val status: QuestionnaireStatus,
    val deferralReason: String? = null,
    val deferralUntil: Long? = null
)

// ---- PHYSICAL TEST MODULE CONTRACT (for Pallor / Jaundice) ----
data class TestResult(
    val result: PhysicalTestResult,
    val scoreOrIndex: Float? = null,
    val qualityWarning: String? = null
)