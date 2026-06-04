package com.example.bloodlink

import android.app.Application
import android.content.Intent
import android.util.Log
import kotlin.system.exitProcess

/**
 * Global uncaught exception handler.
 *
 * When any unhandled crash occurs, instead of showing Android's
 * "BloodLink has stopped" dialog, this handler:
 *   1. Logs the full stack trace to Logcat
 *   2. Restarts the app fresh at MainActivity
 *
 * Install once in MainActivity.onCreate() BEFORE setContent {}.
 */
object CrashHandler : Thread.UncaughtExceptionHandler {

    private lateinit var app: Application
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    fun install(application: Application) {
        app = application
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        Log.e("BloodLink-Crash", "UNCAUGHT on ${thread.name}", throwable)
        try {
            val intent = Intent(app, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            app.startActivity(intent)
        } catch (e: Exception) {
            Log.e("BloodLink-Crash", "Failed to restart: ${e.message}")
        }
        // Kill the current process so Android doesn't show "app stopped"
        android.os.Process.killProcess(android.os.Process.myPid())
        exitProcess(1)
    }
}