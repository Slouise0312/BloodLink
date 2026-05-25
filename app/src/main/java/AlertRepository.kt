package com.example.bloodlink

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

private const val USERS = "users"
private const val ALERTS = "alerts"

object AlertRepository {

    private val db = FirebaseFirestore.getInstance()

    fun createAlert(
        uid: String,
        type: String,
        title: String,
        body: String,
        relatedEventId: String? = null,
        relatedScreeningId: String? = null,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val id = db.collection(USERS).document(uid).collection(ALERTS).document().id
        val now = System.currentTimeMillis()
        val data = hashMapOf<String, Any>(
            "type" to type,
            "title" to title,
            "body" to body,
            "createdAt" to now,
            "read" to false
        )
        relatedEventId?.let { data["relatedEventId"] = it }
        relatedScreeningId?.let { data["relatedScreeningId"] = it }
        db.collection(USERS).document(uid).collection(ALERTS).document(id).set(data)
            .addOnSuccessListener { onSuccess(id) }
            .addOnFailureListener { e -> onError(e.message ?: "Failed to create alert") }
    }

    fun listAlerts(uid: String, onResult: (List<AppAlert>) -> Unit) {
        db.collection(USERS).document(uid).collection(ALERTS)
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(100)
            .get()
            .addOnSuccessListener { snap ->
                val list = snap?.documents?.mapNotNull { doc ->
                    val m = doc.data ?: return@mapNotNull null
                    AppAlert(
                        id = doc.id,
                        type = m["type"] as? String ?: "",
                        title = m["title"] as? String ?: "",
                        body = m["body"] as? String ?: "",
                        relatedEventId = m["relatedEventId"] as? String,
                        relatedScreeningId = m["relatedScreeningId"] as? String,
                        createdAt = (m["createdAt"] as? Number)?.toLong() ?: 0L,
                        read = m["read"] as? Boolean ?: false
                    )
                } ?: emptyList()
                onResult(list)
            }
            .addOnFailureListener { onResult(emptyList()) }
    }

    fun listenAlerts(uid: String, onResult: (List<AppAlert>) -> Unit): ListenerRegistration {
        return db.collection(USERS).document(uid).collection(ALERTS)
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.mapNotNull { doc ->
                    val m = doc.data ?: return@mapNotNull null
                    AppAlert(
                        id = doc.id,
                        type = m["type"] as? String ?: "",
                        title = m["title"] as? String ?: "",
                        body = m["body"] as? String ?: "",
                        relatedEventId = m["relatedEventId"] as? String,
                        relatedScreeningId = m["relatedScreeningId"] as? String,
                        createdAt = (m["createdAt"] as? Number)?.toLong() ?: 0L,
                        read = m["read"] as? Boolean ?: false
                    )
                } ?: emptyList()
                onResult(list)
            }
    }

    fun markAlertRead(uid: String, alertId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        db.collection(USERS).document(uid).collection(ALERTS).document(alertId)
            .update("read", true)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(e.message ?: "Update failed") }
    }
}
