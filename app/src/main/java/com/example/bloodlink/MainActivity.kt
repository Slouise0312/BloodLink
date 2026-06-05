package com.example.bloodlink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── 1. Install global crash handler FIRST ──────────────────────────
        CrashHandler.install(application)

        // ── 2. Firebase App Check — verifies requests come from YOUR app ───
        // Blocks scripts, bots, and anyone using your Firebase project ID
        // from outside the real BloodLink APK.
        val appCheck = FirebaseAppCheck.getInstance()
        appCheck.installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance()
        )

        // ── 3. Firestore offline persistence ───────────────────────────────
        val db = FirebaseFirestore.getInstance()
        db.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()

        // ── 4. Pre-load TFLite models on a background thread ───────────────
        Executors.newSingleThreadExecutor().execute {
            try {
                ImagePipeline.initInterpreters(applicationContext)
                android.util.Log.d("BloodLink", "TFLite models pre-loaded on background thread")
            } catch (e: Exception) {
                android.util.Log.e("BloodLink", "TFLite pre-load failed (non-fatal): ${e.message}")
            }
        }

        // ── 5. Compose UI ──────────────────────────────────────────────────
        setContent {
            Surface(color = MaterialTheme.colorScheme.background) {
                BloodLinkApp()
            }
        }
    }
}