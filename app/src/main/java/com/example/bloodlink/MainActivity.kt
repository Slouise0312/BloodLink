package com.example.bloodlink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── 1. Install global crash handler FIRST ──────────────────────────
        // Catches any unhandled exception and restarts the app cleanly
        // instead of showing Android's "BloodLink has stopped" dialog.
        CrashHandler.install(application)

        // ── 2. Firestore offline persistence ───────────────────────────────
        val db = FirebaseFirestore.getInstance()
        db.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()

        // ── 3. Pre-load TFLite models on a background thread ───────────────
        // Models are ~5MB each. Loading on Main blocks the UI for 1-3 seconds
        // on mid-range phones, causing a frozen splash screen or ANR.
        Executors.newSingleThreadExecutor().execute {
            try {
                ImagePipeline.initInterpreters(applicationContext)
                android.util.Log.d("BloodLink", "TFLite models pre-loaded on background thread")
            } catch (e: Exception) {
                android.util.Log.e("BloodLink", "TFLite pre-load failed (non-fatal): ${e.message}")
            }
        }

        // ── 4. Compose UI ──────────────────────────────────────────────────
        setContent {
            Surface(color = MaterialTheme.colorScheme.background) {
                BloodLinkApp()
            }
        }
    }
}