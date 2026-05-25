package com.example.bloodlink

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

private const val USERS = "users"

object FcmTokenHelper {

    private val db = FirebaseFirestore.getInstance()

    fun updateTokenForUser(uid: String, onComplete: (() -> Unit)? = null) {
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (task.isSuccessful && task.result != null) {
                    db.collection(USERS).document(uid)
                        .set(mapOf("fcmToken" to task.result!!, "fcmTokenUpdatedAt" to System.currentTimeMillis()), com.google.firebase.firestore.SetOptions.merge())
                        .addOnCompleteListener { onComplete?.invoke() }
                } else {
                    onComplete?.invoke()
                }
            }
    }
}
