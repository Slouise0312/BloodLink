package com.example.bloodlink

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Handles FCM: token refresh (updates Firestore) and foreground notifications.
 * When the app is in background, Android shows notifications automatically.
 */
class BloodLinkMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FcmTokenHelper.updateTokenForUser(uid)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        // When app is in foreground, system does NOT auto-show notification.
        // Show a local notification so the user sees it.
        val title = message.notification?.title ?: message.data["title"] ?: "BloodLink"
        val body = message.notification?.body ?: message.data["body"] ?: ""
        if (body.isNotEmpty()) {
            NotificationHelper.showNotification(this, title, body)
        }
    }
}
