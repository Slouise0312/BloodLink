package com.example.bloodlink

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

private const val USERS = "users"

object FcmTokenHelper {

    private val db = FirebaseFirestore.getInstance()

    /**
     * Update the FCM push notification token for a user.
     * Uses update() instead of set(merge) so it NEVER accidentally creates a doc —
     * if the doc doesn't exist yet, the update silently fails (which is fine,
     * because the token will be written on the next successful profile load).
     */
    fun updateTokenForUser(uid: String, onComplete: (() -> Unit)? = null) {
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (task.isSuccessful && task.result != null) {
                    db.collection(USERS).document(uid)
                        .update(
                            mapOf(
                                "fcmToken" to task.result!!,
                                "fcmTokenUpdatedAt" to System.currentTimeMillis()
                            )
                        )
                        .addOnSuccessListener { onComplete?.invoke() }
                        .addOnFailureListener {
                            // Doc doesn't exist yet — that's fine, token will be set on next load
                            android.util.Log.d("BloodLink-FCM", "Token update skipped (doc may not exist yet)")
                            onComplete?.invoke()
                        }
                } else {
                    onComplete?.invoke()
                }
            }
    }
}