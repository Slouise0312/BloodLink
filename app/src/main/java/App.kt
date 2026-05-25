package com.example.bloodlink

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

sealed class Route(val path: String) {
    data object Auth : Route("auth")
    data object RoleSelection : Route("role_selection")
    data object Consent : Route("consent")
    data object Applicant : Route("applicant")
    data object Staff : Route("staff")
}

@Composable
fun BloodLinkApp() {
    val navController = rememberNavController()
    val authVm: AuthViewModel = viewModel()
    val currentUser by authVm.loggedInUser.collectAsState()
    val needsRoleSelection by authVm.needsRoleSelection.collectAsState()
    val context = LocalContext.current
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(currentUser) {
        if (currentUser != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    NavHost(navController, startDestination = Route.Consent.path) {

        composable(Route.Auth.path) {
            LaunchedEffect(currentUser, needsRoleSelection) {
                when {
                    currentUser != null && !needsRoleSelection -> {
                        when (currentUser!!.role) {
                            UserRole.APPLICANT -> navController.navigate(Route.Applicant.path) {
                                popUpTo(Route.Auth.path) { inclusive = true }
                            }
                            UserRole.STAFF, UserRole.ADMIN -> navController.navigate(Route.Staff.path) {
                                popUpTo(Route.Auth.path) { inclusive = true }
                            }
                        }
                    }
                    needsRoleSelection -> navController.navigate(Route.RoleSelection.path) {
                        popUpTo(Route.Auth.path) { inclusive = true }
                    }
                }
            }
            AuthScreen(authVm = authVm)
        }

        composable(Route.RoleSelection.path) {
            RoleSelectionScreen(
                authVm = authVm,
                onRoleCreated = { role ->
                    when (role) {
                        UserRole.APPLICANT -> navController.navigate(Route.Applicant.path) {
                            popUpTo(Route.RoleSelection.path) { inclusive = true }
                        }
                        UserRole.STAFF, UserRole.ADMIN -> navController.navigate(Route.Staff.path) {
                            popUpTo(Route.RoleSelection.path) { inclusive = true }
                        }
                        else -> { }
                    }
                }
            )
        }

        composable(Route.Consent.path) {
            ConsentScreen(
                onAccept = {
                    // After accepting consent, proceed to authentication
                    navController.navigate(Route.Auth.path) {
                        popUpTo(Route.Consent.path) { inclusive = true }
                    }
                },
                onDecline = {
                    // User declined — stay on consent screen (they can't proceed without consent)
                    // Or you could finish the activity here
                }
            )
        }

        composable(Route.Applicant.path) {
            val user by authVm.loggedInUser.collectAsState()
            if (user == null || user?.role != UserRole.APPLICANT) {
                LaunchedEffect(Unit) {
                    navController.navigate(Route.Auth.path) { popUpTo(Route.Applicant.path) { inclusive = true } }
                }
                return@composable
            }
            ApplicantShell(
                authVm = authVm,
                onLogout = {
                    authVm.logout()
                    navController.navigate(Route.Auth.path) { popUpTo(Route.Applicant.path) { inclusive = true } }
                }
            )
        }

        composable(Route.Staff.path) {
            val user by authVm.loggedInUser.collectAsState()
            if (user == null || (user?.role != UserRole.STAFF && user?.role != UserRole.ADMIN)) {
                LaunchedEffect(Unit) {
                    navController.navigate(Route.Auth.path) { popUpTo(Route.Staff.path) { inclusive = true } }
                }
                return@composable
            }
            StaffShell(
                authVm = authVm,
                onLogout = {
                    authVm.logout()
                    navController.navigate(Route.Auth.path) { popUpTo(Route.Staff.path) { inclusive = true } }
                }
            )
        }
    }
}