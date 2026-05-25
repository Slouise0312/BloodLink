package com.example.bloodlink

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

private const val EVENTS = "events"

object EventRepository {

    private val db = FirebaseFirestore.getInstance()

    fun createEvent(
        orgId: String,
        title: String,
        dateTime: Long,
        location: String,
        createdByUid: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val id = db.collection(EVENTS).document().id
        val payload = hashMapOf<String, Any>(
            "orgId" to orgId,
            "title" to title,
            "dateTime" to dateTime,
            "location" to location,
            "status" to EventStatus.ACTIVE.name,
            "createdByUid" to createdByUid,
            "createdAt" to System.currentTimeMillis(),
            "updatedAt" to System.currentTimeMillis(),
            "qrPayload" to id
        )
        db.collection(EVENTS).document(id).set(payload)
            .addOnSuccessListener { onSuccess(id) }
            .addOnFailureListener { e -> onError(e.message ?: "Failed to create event") }
    }

    fun listenEventsByOrg(orgId: String, onResult: (List<Event>) -> Unit): ListenerRegistration {
        return db.collection(EVENTS)
            .whereEqualTo("orgId", orgId)
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    Event(
                        id = doc.id,
                        orgId = doc.getString("orgId") ?: "",
                        title = doc.getString("title") ?: "",
                        dateTime = (doc.getLong("dateTime") ?: 0L),
                        location = doc.getString("location") ?: "",
                        status = (doc.getString("status") ?: EventStatus.ACTIVE.name).let { EventStatus.entries.find { e -> e.name == it } ?: EventStatus.ACTIVE },
                        createdByUid = doc.getString("createdByUid") ?: "",
                        createdAt = doc.getLong("createdAt") ?: 0L,
                        updatedAt = doc.getLong("updatedAt") ?: 0L,
                        qrPayload = doc.getString("qrPayload")
                    )
                } ?: emptyList()
                onResult(list)
            }
    }

    fun getEvent(eventId: String, onResult: (Event?) -> Unit) {
        db.collection(EVENTS).document(eventId).get()
            .addOnSuccessListener { doc ->
                if (doc != null && doc.exists()) {
                    val d = doc.data ?: return@addOnSuccessListener
                    onResult(Event(
                        id = doc.id,
                        orgId = doc.getString("orgId") ?: "",
                        title = doc.getString("title") ?: "",
                        dateTime = doc.getLong("dateTime") ?: 0L,
                        location = doc.getString("location") ?: "",
                        status = (doc.getString("status") ?: EventStatus.ACTIVE.name).let { EventStatus.entries.find { e -> e.name == it } ?: EventStatus.ACTIVE },
                        createdByUid = doc.getString("createdByUid") ?: "",
                        createdAt = doc.getLong("createdAt") ?: 0L,
                        updatedAt = doc.getLong("updatedAt") ?: 0L,
                        qrPayload = doc.getString("qrPayload")
                    ))
                } else onResult(null)
            }
            .addOnFailureListener { onResult(null) }
    }

    fun updateEvent(eventId: String, updates: Map<String, Any>, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val withTimestamp = updates.toMutableMap()
        withTimestamp["updatedAt"] = System.currentTimeMillis()
        db.collection(EVENTS).document(eventId).update(withTimestamp)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(e.message ?: "Update failed") }
    }

    fun updateEventStatus(eventId: String, status: EventStatus, onSuccess: () -> Unit, onError: (String) -> Unit) {
        updateEvent(eventId, mapOf("status" to status.name), onSuccess, onError)
    }

    fun deleteEvent(eventId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        db.collection(EVENTS).document(eventId).delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(e.message ?: "Failed to delete event") }
    }

    fun listEvents(orgId: String, onResult: (List<Event>) -> Unit) {
        db.collection(EVENTS).whereEqualTo("orgId", orgId).get()
            .addOnSuccessListener { snap ->
                val list = snap?.documents?.mapNotNull { doc ->
                    Event(
                        id = doc.id,
                        orgId = doc.getString("orgId") ?: "",
                        title = doc.getString("title") ?: "",
                        dateTime = doc.getLong("dateTime") ?: 0L,
                        location = doc.getString("location") ?: "",
                        status = (doc.getString("status") ?: EventStatus.ACTIVE.name).let { EventStatus.entries.find { e -> e.name == it } ?: EventStatus.ACTIVE },
                        createdByUid = doc.getString("createdByUid") ?: "",
                        createdAt = doc.getLong("createdAt") ?: 0L,
                        updatedAt = doc.getLong("updatedAt") ?: 0L,
                        qrPayload = doc.getString("qrPayload")
                    )
                } ?: emptyList()
                onResult(list)
            }
            .addOnFailureListener { onResult(emptyList()) }
    }
}
