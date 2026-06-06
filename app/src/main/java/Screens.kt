@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.bloodlink

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

// ─── Design tokens ───────────────────────────────────────────────────────────

private val BrandRed        = Color(0xFFC0392B)
private val BrandRedLight   = Color(0xFFFCEBEB)
private val BrandRedDark    = Color(0xFF791F1F)
private val SurfaceBg       = Color(0xFFF8F8F7)
private val CardWhite       = Color.White
private val BorderColor     = Color(0x1A000000)   // ~10% black
private val TextPrimary     = Color(0xFF2C2C2A)
private val TextSecondary   = Color(0xFF5F5E5A)
private val TextHint        = Color(0xFF888780)
private val GreenBg         = Color(0xFFEAF3DE)
private val GreenText       = Color(0xFF27500A)
private val AmberBg         = Color(0xFFFAEEDA)
private val AmberText       = Color(0xFF633806)
private val GrayBg          = Color(0xFFF1EFE8)

// ─── Reusable components ─────────────────────────────────────────────────────

@Composable
private fun BrandTopBar(
    title: String,
    subtitle: String? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(BrandRed)
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 18.dp)
    ) {
        if (navigationIcon != null || actions != Unit) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                navigationIcon?.invoke() ?: Spacer(Modifier.width(1.dp))
                Row { actions() }
            }
            Spacer(Modifier.height(6.dp))
        }
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        if (subtitle != null) {
            Text(subtitle, color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) {
        Text("← Back", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
    }
}

@Composable
private fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(48.dp),
        enabled = enabled && !loading,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = BrandRed,
            contentColor = Color.White,
            disabledContainerColor = BrandRed.copy(alpha = 0.4f)
        )
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
        else Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.5.dp, BrandRed),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandRed)
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

/** Proper Google-branded sign-in button using the official Google logo vector drawable. */
@Composable
private fun GoogleSignInButton(
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFFDADADA)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White,
            contentColor = Color(0xFF3C4043),
            disabledContainerColor = Color.White.copy(alpha = 0.6f),
            disabledContentColor = Color(0xFF3C4043).copy(alpha = 0.4f)
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_google_logo),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Sign in with Google",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF3C4043)
            )
        }
    }
}

@Composable
private fun BrandCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            border = BorderStroke(0.5.dp, BorderColor)
        ) { Column(Modifier.padding(16.dp), content = content) }
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            border = BorderStroke(0.5.dp, BorderColor)
        ) { Column(Modifier.padding(16.dp), content = content) }
    }
}

@Composable
private fun StatusBadge(text: String, type: BadgeType) {
    val bg = when (type) {
        BadgeType.GREEN  -> GreenBg
        BadgeType.RED    -> BrandRedLight
        BadgeType.AMBER  -> AmberBg
        BadgeType.GRAY   -> GrayBg
    }
    val fg = when (type) {
        BadgeType.GREEN  -> GreenText
        BadgeType.RED    -> BrandRedDark
        BadgeType.AMBER  -> AmberText
        BadgeType.GRAY   -> TextSecondary
    }
    Surface(shape = RoundedCornerShape(20.dp), color = bg) {
        Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = fg)
    }
}

private enum class BadgeType { GREEN, RED, AMBER, GRAY }

@Composable
private fun DisclaimerBox(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = AmberBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            fontSize = 12.sp,
            color = AmberText,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        color = BorderColor,
        thickness = 0.5.dp,
        modifier = Modifier.padding(vertical = 0.dp)
    )
}

@Composable
private fun StatCard(value: String, label: String, valueColor: Color = TextPrimary) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = CardWhite,
        border = BorderStroke(0.5.dp, BorderColor)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = valueColor)
            Text(label, fontSize = 11.sp, color = TextHint, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun AvatarCircle(initials: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(BrandRedLight),
        contentAlignment = Alignment.Center
    ) {
        Text(initials.take(2).uppercase(), fontSize = 13.sp, fontWeight = FontWeight.Medium, color = BrandRedDark)
    }
}

@Composable
private fun ProgressStepper(currentStep: Int, totalSteps: Int) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Progress", fontSize = 12.sp, color = TextSecondary)
            Text("$currentStep of $totalSteps done", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = BrandRed)
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { currentStep.toFloat() / totalSteps.toFloat() },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = BrandRed,
            trackColor = GrayBg
        )
    }
}

private fun screeningStep(screening: Screening?): Int {
    if (screening == null) return 0
    var done = 0
    if (screening.pallorResult != PhysicalTestResult.NOT_DONE) done++
    if (screening.jaundiceResult != PhysicalTestResult.NOT_DONE) done++
    if (screening.questionnaireStatus != QuestionnaireStatus.NOT_DONE) done++
    return done
}

// ─── AUTH ────────────────────────────────────────────────────────────────────

@Composable
fun AuthScreen(authVm: AuthViewModel) {
    var isLogin by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showPrivacyConsent by remember { mutableStateOf(false) }
    var pendingGoogleSignIn by remember { mutableStateOf(false) }
    var showForgotPassword by remember { mutableStateOf(false) }

    val authError by authVm.authError.collectAsState()
    val needsEmailVerification by authVm.needsEmailVerification.collectAsState()
    val pendingVerificationEmail by authVm.pendingVerificationEmail.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(authError) { if (authError != null) isLoading = false }

    val webClientId = "343067117680-22sqbk66h5a9h1fg0eh94cdpr59bvfbf.apps.googleusercontent.com"

    val googleSignInClient = remember(webClientId) {
        webClientId?.let { id ->
            GoogleSignIn.getClient(
                context,
                GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(id).requestEmail().build()
            )
        }
    }
    val googleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            account.idToken?.let { token ->
                authVm.signInWithGoogle(token, onSuccess = { isLoading = false }, onError = { isLoading = false })
            } ?: run { isLoading = false }
        } catch (_: ApiException) { isLoading = false }
    }

    // ── Email verification screen ────────────────────────────────────────
    if (needsEmailVerification) {
        EmailVerificationScreen(
            email = pendingVerificationEmail ?: "",
            password = pass,
            authVm = authVm,
            onBackToLogin = {
                authVm.dismissEmailVerification()
                isLogin = true
                isLoading = false
            }
        )
        return
    }

    // ── Forgot password dialog ───────────────────────────────────────────
    if (showForgotPassword) {
        ForgotPasswordDialog(
            initialEmail = email,
            authVm = authVm,
            onDismiss = { showForgotPassword = false }
        )
    }

    // Show full-screen privacy consent before creating account
    if (showPrivacyConsent) {
        ConsentScreen(
            context = "signup",
            onAccept = {
                showPrivacyConsent = false
                isLoading = true
                if (pendingGoogleSignIn && googleSignInClient != null) {
                    pendingGoogleSignIn = false
                    googleSignInClient.signInIntent.let { intent ->
                        googleLauncher.launch(intent)
                    }
                } else {
                    authVm.signUp(name, email, pass, onSuccess = { isLoading = false }, onError = { isLoading = false })
                }
            },
            onDecline = {
                showPrivacyConsent = false
                pendingGoogleSignIn = false
            }
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(BrandRed)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(56.dp))

        // Logo
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text("BloodLink", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "Pre-screening for blood donation",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        // Form card
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = CardWhite,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (isLogin) "Welcome back" else "Create your account",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )

                if (!isLogin) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Full name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; authVm.clearAuthError() },
                    label = { Text("Email address") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it; authVm.clearAuthError() },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )

                // Forgot password link (only on login)
                if (isLogin) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        TextButton(
                            onClick = { showForgotPassword = true },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Forgot password?", color = BrandRed, fontSize = 12.sp)
                        }
                    }
                }

                if (authError != null) {
                    Text(authError.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }

                PrimaryButton(
                    text = if (isLogin) "Sign in" else "Create account",
                    onClick = {
                        authVm.clearAuthError()
                        if (isLogin) {
                            isLoading = true
                            authVm.login(email, pass, onSuccess = { isLoading = false }, onError = { isLoading = false })
                        } else {
                            // Show Data Privacy consent before creating account
                            showPrivacyConsent = true
                        }
                    },
                    loading = isLoading
                )

                if (googleSignInClient != null) {
                    GoogleSignInButton(
                        onClick = {
                            authVm.clearAuthError()
                            pendingGoogleSignIn = true
                            showPrivacyConsent = true
                        },
                        enabled = !isLoading
                    )
                }

                TextButton(
                    onClick = { isLogin = !isLogin; authVm.clearAuthError() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (isLogin) "Don't have an account? Sign up" else "Already have an account? Sign in",
                        color = BrandRed,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "For NGOs and blood centers in the Philippines.",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}

// ─── EMAIL VERIFICATION SCREEN ──────────────────────────────────────────────

@Composable
private fun EmailVerificationScreen(
    email: String,
    password: String,
    authVm: AuthViewModel,
    onBackToLogin: () -> Unit
) {
    var resendMessage by remember { mutableStateOf<String?>(null) }
    var isResending by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(SurfaceBg)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Email icon
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(BrandRedLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Email,
                contentDescription = null,
                tint = BrandRed,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Verify your email",
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )

        Spacer(Modifier.height(12.dp))
        Text(
            "We sent a verification link to:",
            fontSize = 14.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            email,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))
        Text(
            "Open the email and tap the link to verify your account. Then come back here and sign in.",
            fontSize = 13.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp
        )

        if (resendMessage != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                resendMessage.orEmpty(),
                fontSize = 12.sp,
                color = if (resendMessage?.contains("sent") == true) Color(0xFF1D9E75) else MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(28.dp))

        // Resend button
        OutlineButton(
            text = if (isResending) "Sending..." else "Resend verification email",
            onClick = {
                if (password.isNotBlank()) {
                    isResending = true
                    authVm.resendVerificationEmail(
                        email = email,
                        password = password,
                        onSuccess = {
                            resendMessage = "Verification email sent! Check your inbox."
                            isResending = false
                        },
                        onError = { msg ->
                            resendMessage = msg
                            isResending = false
                        }
                    )
                } else {
                    resendMessage = "Please go back and sign in again to resend."
                }
            },
            enabled = !isResending
        )

        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            text = "Back to sign in",
            onClick = onBackToLogin
        )
    }
}

// ─── FORGOT PASSWORD DIALOG ─────────────────────────────────────────────────

@Composable
private fun ForgotPasswordDialog(
    initialEmail: String,
    authVm: AuthViewModel,
    onDismiss: () -> Unit
) {
    var resetEmail by remember { mutableStateOf(initialEmail) }
    var message by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardWhite,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                if (isSuccess) "Email sent!" else "Reset your password",
                fontWeight = FontWeight.Medium,
                fontSize = 17.sp,
                color = TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isSuccess) {
                    Text(
                        "We sent a password reset link to $resetEmail. Open the email and follow the instructions to set a new password.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        lineHeight = 19.sp
                    )
                } else {
                    Text(
                        "Enter the email address you used to create your account. We'll send you a link to reset your password.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        lineHeight = 19.sp
                    )
                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = { resetEmail = it; message = null },
                        label = { Text("Email address") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )
                    if (message != null) {
                        Text(message.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            if (isSuccess) {
                TextButton(onClick = onDismiss) {
                    Text("Done", color = BrandRed, fontWeight = FontWeight.Medium)
                }
            } else {
                TextButton(
                    onClick = {
                        isSending = true
                        authVm.sendPasswordResetEmail(
                            email = resetEmail,
                            onSuccess = {
                                isSuccess = true
                                isSending = false
                            },
                            onError = { msg ->
                                message = msg
                                isSending = false
                            }
                        )
                    },
                    enabled = !isSending
                ) {
                    Text(
                        if (isSending) "Sending..." else "Send reset link",
                        color = BrandRed,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        dismissButton = {
            if (!isSuccess) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        }
    )
}

// ─── ROLE SELECTION ──────────────────────────────────────────────────────────

@Composable
fun RoleSelectionScreen(authVm: AuthViewModel, onRoleCreated: (UserRole) -> Unit) {
    var selectedRole by remember { mutableStateOf(UserRole.APPLICANT) }
    var orgId by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(SurfaceBg)
    ) {
        BrandTopBar(title = "Welcome to BloodLink", subtitle = "Choose your role to continue")

        Column(
            Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "How will you be using BloodLink today?",
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )

            listOf(
                Triple(UserRole.APPLICANT, "I am a donor", "Take the pre-screening test at a blood drive"),
                Triple(UserRole.STAFF, "I am a staff member", "View applicants and verify outcomes at events"),
                Triple(UserRole.ADMIN, "I am an admin", "Create events, manage staff, and view analytics")
            ).forEach { (role, title, desc) ->
                val isSelected = selectedRole == role
                Card(
                    onClick = { selectedRole = role; error = null },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    border = BorderStroke(if (isSelected) 2.dp else 0.5.dp, if (isSelected) BrandRed else BorderColor)
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) BrandRedLight else GrayBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (role == UserRole.APPLICANT) Icons.Outlined.Person else Icons.Outlined.Badge,
                                contentDescription = null,
                                tint = if (isSelected) BrandRed else TextHint,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                            Text(desc, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp))
                        }
                        if (isSelected) {
                            Box(
                                Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(BrandRed),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("✓", color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            if (selectedRole == UserRole.STAFF || selectedRole == UserRole.ADMIN) {
                OutlinedTextField(
                    value = orgId,
                    onValueChange = { orgId = it; error = null },
                    label = { Text("Organization ID") },
                    placeholder = { Text("e.g. org-pnrc-qc") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    "Enter the organization ID provided by your administrator.",
                    fontSize = 11.sp,
                    color = TextHint
                )
            }

            if (error != null) {
                Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }

            Spacer(Modifier.height(8.dp))
            PrimaryButton(
                text = "Continue",
                onClick = {
                    error = null
                    loading = true
                    authVm.createUserWithRole(
                        selectedRole,
                        orgId.ifBlank { null },
                        onSuccess = { loading = false; onRoleCreated(selectedRole) },
                        onError = { loading = false; error = it }
                    )
                },
                enabled = !loading && (selectedRole == UserRole.APPLICANT || orgId.isNotBlank()),
                loading = loading
            )
        }
    }
}

// ─── APPLICANT SHELL ─────────────────────────────────────────────────────────

enum class ApplicantTab { EVENT, SCREENING, QUESTIONNAIRE, RESULT, PROFILE }

@Composable
fun ApplicantShell(authVm: AuthViewModel, onLogout: () -> Unit) {
    var tab by remember { mutableStateOf(ApplicantTab.EVENT) }
    val screeningVm: ScreeningViewModel = viewModel()
    val user by authVm.loggedInUser.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = SurfaceBg,
        bottomBar = {
            NavigationBar(
                containerColor = CardWhite,
                tonalElevation = 0.dp,
                modifier = Modifier.border(BorderStroke(0.5.dp, BorderColor), shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp))
            ) {
                listOf(
                    Triple(ApplicantTab.EVENT, Icons.Outlined.QrCodeScanner, "Event"),
                    Triple(ApplicantTab.SCREENING, Icons.Outlined.CameraAlt, "Screening"),
                    Triple(ApplicantTab.QUESTIONNAIRE, Icons.Outlined.Assignment, "Questions"),
                    Triple(ApplicantTab.RESULT, Icons.Outlined.CheckCircle, "Result"),
                    Triple(ApplicantTab.PROFILE, Icons.Outlined.Person, "Profile")
                ).forEach { (t, icon, label) ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(label, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = BrandRed,
                            selectedTextColor = BrandRed,
                            indicatorColor = BrandRedLight
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                ApplicantTab.EVENT          -> ApplicantEventTab(screeningVm, user?.id ?: "", user?.name, showMessage)
                ApplicantTab.SCREENING      -> ApplicantScreeningTab(screeningVm, showMessage)
                ApplicantTab.QUESTIONNAIRE  -> ApplicantQuestionnaireTab(screeningVm, showMessage)
                ApplicantTab.RESULT         -> ApplicantResultTab(screeningVm)
                ApplicantTab.PROFILE        -> ApplicantProfileTab(authVm, onLogout, viewModel())
            }
        }
    }
}

// ─── APPLICANT: EVENT TAB ────────────────────────────────────────────────────

@Composable
private fun ApplicantEventTab(
    screeningVm: ScreeningViewModel,
    userId: String,
    userName: String?,
    onShowMessage: (String) -> Unit
) {
    val currentEvent by screeningVm.currentEvent.collectAsState()
    val currentScreening by screeningVm.currentScreening.collectAsState()
    val loading by screeningVm.loading.collectAsState()
    val error by screeningVm.error.collectAsState()
    var eventIdInput by remember { mutableStateOf("") }
    var showQrScanner by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    Column(Modifier.fillMaxSize().background(SurfaceBg)) {
        val ev = currentEvent
        if (ev != null) {
            BrandTopBar(title = ev.title, subtitle = ev.location)
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                currentScreening?.let { sc ->
                    BrandCard {
                        ProgressStepper(currentStep = screeningStep(sc), totalSteps = 3)
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatusBadge(
                                text = when (sc.overallStatus) {
                                    OverallStatus.ELIGIBLE      -> "Eligible"
                                    OverallStatus.TEMP_DEFERRED -> "Deferred"
                                    OverallStatus.NOT_ELIGIBLE  -> "Not eligible"
                                    OverallStatus.INCOMPLETE    -> "In progress"
                                },
                                type = when (sc.overallStatus) {
                                    OverallStatus.ELIGIBLE      -> BadgeType.GREEN
                                    OverallStatus.TEMP_DEFERRED -> BadgeType.AMBER
                                    OverallStatus.NOT_ELIGIBLE  -> BadgeType.RED
                                    OverallStatus.INCOMPLETE    -> BadgeType.GRAY
                                }
                            )
                            StatusBadge(ev.status.name.lowercase().replaceFirstChar { it.uppercase() }, BadgeType.GRAY)
                        }
                    }
                }

                DisclaimerBox("This app performs a pre-screening check only. It is not a medical diagnosis. Final eligibility is determined by licensed staff on-site.")

                OutlineButton(
                    text = "Leave event",
                    onClick = {
                        screeningVm.leaveEvent {
                            onShowMessage("You have left the event. Your progress was cleared.")
                        }
                    }
                )
            }
        } else {
            BrandTopBar(title = "Join an event", subtitle = "Scan the event QR code or enter the event code")
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlineButton(
                    text = "Scan event QR code",
                    onClick = { showQrScanner = true },
                    enabled = !loading
                )

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(Modifier.weight(1f), color = BorderColor)
                    Text("or enter code manually", fontSize = 11.sp, color = TextHint)
                    HorizontalDivider(Modifier.weight(1f), color = BorderColor)
                }

                OutlinedTextField(
                    value = eventIdInput,
                    onValueChange = { eventIdInput = it; screeningVm.clearError() },
                    label = { Text("Event code") },
                    placeholder = { Text("e.g. BLD-2025-001") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !loading
                )

                if (error != null) {
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }

                PrimaryButton(
                    text = "Join event",
                    onClick = {
                        screeningVm.clearError()
                        screeningVm.joinEvent(eventIdInput.trim(), userId, userName) { s ->
                            if (s != null) onShowMessage("Event joined. You can now begin screening.")
                        }
                    },
                    enabled = eventIdInput.isNotBlank(),
                    loading = loading
                )

                DisclaimerBox("This app performs a pre-screening check only. It is not a medical diagnosis. Final eligibility is determined by licensed staff on-site.")
            }
        }
    }

    if (showQrScanner) {
        QrScannerScreen(
            onScanned = { id ->
                eventIdInput = id
                showQrScanner = false
                onShowMessage("Event code scanned. Tap 'Join event' to continue.")
            },
            onCancel = { showQrScanner = false },
            hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
            onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) }
        )
    }
}

// ─── APPLICANT: SCREENING TAB ────────────────────────────────────────────────

@Composable
private fun ApplicantScreeningTab(screeningVm: ScreeningViewModel, onShowMessage: (String) -> Unit) {
    val screening by screeningVm.currentScreening.collectAsState()
    val sc = screening

    if (sc == null) {
        EmptyState(
            icon = Icons.Outlined.CameraAlt,
            title = "No active event",
            subtitle = "Join an event from the Event tab first."
        )
        return
    }

    val pallorModule  = remember { PallorModule() }
    val jaundiceModule = remember { JaundiceModule() }
    var selectedTest  by remember { mutableStateOf<PhysicalTestModule?>(null) }
    var lastResult    by remember { mutableStateOf<TestResult?>(null) }
    var saving        by remember { mutableStateOf(false) }
    val scope         = rememberCoroutineScope()
    val context = LocalContext.current

    // Runtime camera permission
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Camera photo file — stored outside remember so the launcher callback always sees the latest value
    val currentPhotoFileRef = remember { androidx.compose.runtime.mutableStateOf<java.io.File?>(null) }

    var qualityError by remember { mutableStateOf<String?>(null) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            qualityError = "Camera permission is required. Please enable it in your phone's Settings → Apps → BloodLink → Permissions."
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val test = selectedTest ?: return@rememberLauncherForActivityResult
        if (success) {
            scope.launch {
                try {
                    val file = currentPhotoFileRef.value ?: return@launch
                    val result = withContext(Dispatchers.IO) {
                        val raw = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                            ?: return@withContext Triple<String?, TestResult?, Boolean>("Photo could not be read. Please try again.", null, true)
                        val bitmap = ImagePipeline.downscaleBitmap(raw)
                        val check = ImagePipeline.checkImageQuality(bitmap, test.id)
                        if (check is ImagePipeline.QualityCheckResult.Fail) {
                            Triple<String?, TestResult?, Boolean>(check.reason, null, false)
                        } else {
                            Triple<String?, TestResult?, Boolean>(null, test.run(bitmap), false)
                        }
                    }
                    qualityError = result.first
                    lastResult = result.second
                } catch (e: Exception) {
                    android.util.Log.e("BloodLink", "Camera capture failed: ${e.message}", e)
                    qualityError = "Something went wrong processing the photo. Please try again."
                }
            }
        }
    }
    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val test = selectedTest ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            scope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        val inputStream = context.contentResolver.openInputStream(uri)
                        val raw = android.graphics.BitmapFactory.decodeStream(inputStream)
                        inputStream?.close()
                        if (raw == null) {
                            return@withContext Triple<String?, TestResult?, Boolean>("Photo could not be read. Please try a different image.", null, true)
                        }
                        val bitmap = ImagePipeline.downscaleBitmap(raw)
                        val check = ImagePipeline.checkImageQuality(bitmap, test.id)
                        if (check is ImagePipeline.QualityCheckResult.Fail) {
                            Triple<String?, TestResult?, Boolean>(check.reason, null, false)
                        } else {
                            Triple<String?, TestResult?, Boolean>(null, test.run(bitmap), false)
                        }
                    }
                    qualityError = result.first
                    lastResult = result.second
                } catch (e: Exception) {
                    android.util.Log.e("BloodLink", "Image upload failed: ${e.message}", e)
                    qualityError = "Something went wrong processing the image. Please try again."
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(SurfaceBg)) {
        BrandTopBar(title = "Physical screening", subtitle = "Step 2 of 4 — pre-screening only, not a medical diagnosis")
        Column(
            Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DisclaimerBox("Pallor and jaundice indicators are screening aids only. They do not diagnose any condition. Final assessment is performed by staff at the venue.")

            listOf(pallorModule, jaundiceModule).forEach { module ->
                val currentResult = when (module.id) {
                    "pallor"   -> sc.pallorResult
                    "jaundice" -> sc.jaundiceResult
                    else       -> PhysicalTestResult.NOT_DONE
                }
                val isSelected = selectedTest?.id == module.id
                Card(
                    onClick = { selectedTest = module; lastResult = null; qualityError = null },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    border = BorderStroke(if (isSelected) 2.dp else 0.5.dp, if (isSelected) BrandRed else BorderColor)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(module.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary, modifier = Modifier.weight(1f))
                            StatusBadge(
                                text = when (currentResult) {
                                    PhysicalTestResult.NORMAL        -> "Normal"
                                    PhysicalTestResult.POSSIBLE_SIGN -> "Possible sign"
                                    PhysicalTestResult.NOT_DONE      -> "Not done"
                                },
                                type = when (currentResult) {
                                    PhysicalTestResult.NORMAL        -> BadgeType.GREEN
                                    PhysicalTestResult.POSSIBLE_SIGN -> BadgeType.RED
                                    PhysicalTestResult.NOT_DONE      -> BadgeType.GRAY
                                }
                            )
                        }
                        Text(module.instructions, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 6.dp), lineHeight = 18.sp)
                    }
                }
            }

            // ── Under development modules (Cyanosis + Skin Lesion) ──────
            listOf(
                "Cyanosis Check (Nail Bed)" to "Checks fingernail beds for bluish discoloration that may indicate poor oxygen circulation.",
                "Skin Lesion Check" to "Checks for visible skin lesions, rashes, or abnormalities that may affect donation eligibility."
            ).forEach { (title, description) ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    border = BorderStroke(0.5.dp, BorderColor)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextHint, modifier = Modifier.weight(1f))
                            StatusBadge(text = "Coming soon", type = BadgeType.GRAY)
                        }
                        Text(description, fontSize = 12.sp, color = TextHint, modifier = Modifier.padding(top = 6.dp), lineHeight = 18.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = androidx.compose.ui.graphics.Color(0xFFF3F4F6),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = TextHint,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "This test is under development. Staff will assess this condition manually during your screening.",
                                fontSize = 11.sp,
                                color = TextHint,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            if (selectedTest != null) {
                BrandCard {
                    Text(selectedTest?.title ?: "", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))

                    // ── Positioning guide per test ──────────────────────────
                    val guideText = when (selectedTest?.id) {
                        "pallor"   -> "👁 Pull your lower eyelid down so the pink inner surface is visible. Hold your phone 20–30 cm from your eye in good lighting, then take the photo."
                        "jaundice" -> "👁 Look slightly upward so the white part of your eye is well exposed. Hold your phone 20–30 cm away in good lighting, then take the photo."
                        "cyanosis" -> "✋ Hold your hand flat with fingernails facing the camera, filling the frame. Use white or natural lighting. Remove nail polish if present."
                        else       -> "📸 Position the target area so it fills most of the camera frame under good white or natural lighting."
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = androidx.compose.ui.graphics.Color(0xFFEEF4FF),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color(0xFF1A56DB),
                            modifier = Modifier.size(15.dp).padding(top = 1.dp)
                        )
                        Text(
                            text = guideText,
                            fontSize = 12.sp,
                            color = androidx.compose.ui.graphics.Color(0xFF1A56DB),
                            lineHeight = 17.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlineButton(
                            text = "Take photo",
                            onClick = {
                                // Re-check permission at click time (user may have revoked it)
                                val permNow = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED
                                if (!permNow) {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                    return@OutlineButton
                                }
                                try {
                                    val imgDir = java.io.File(context.cacheDir, "images")
                                    imgDir.mkdirs()
                                    val photoFile = java.io.File(imgDir, "photo_${System.currentTimeMillis()}.jpg")
                                    currentPhotoFileRef.value = photoFile
                                    val uri = androidx.core.content.FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        photoFile
                                    )
                                    launcher.launch(uri)
                                } catch (e: Exception) {
                                    qualityError = "Camera error: ${e.javaClass.simpleName}: ${e.message}"
                                    android.util.Log.e("BloodLink-Camera", "FAILED: ${e.javaClass.simpleName}: ${e.message}")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        OutlineButton(
                            text = "Upload photo",
                            onClick = { uploadLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // ── Quality gate rejection banner ──────────────────────
                    qualityError?.let { errMsg ->
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = androidx.compose.ui.graphics.Color(0xFFFFF3CD),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Warning,
                                contentDescription = null,
                                tint = androidx.compose.ui.graphics.Color(0xFF856404),
                                modifier = Modifier.size(16.dp).padding(top = 1.dp)
                            )
                            Text(
                                text = errMsg,
                                fontSize = 12.sp,
                                color = androidx.compose.ui.graphics.Color(0xFF856404),
                                lineHeight = 17.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    lastResult?.let { r ->
                        Spacer(Modifier.height(10.dp))
                        SectionDivider()
                        Spacer(Modifier.height(10.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Result", fontSize = 13.sp, color = TextSecondary)
                            StatusBadge(
                                text = if (r.result == PhysicalTestResult.POSSIBLE_SIGN) "Possible sign" else "Normal",
                                type = if (r.result == PhysicalTestResult.POSSIBLE_SIGN) BadgeType.RED else BadgeType.GREEN
                            )
                        }
                        r.qualityWarning?.let { warn ->
                            Text(warn, fontSize = 11.sp, color = TextHint, modifier = Modifier.padding(top = 4.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                        PrimaryButton(
                            text = "Save result",
                            onClick = {
                                val pallorResult  = if (selectedTest?.id == "pallor")   r.result else sc.pallorResult
                                val pallorScore   = if (selectedTest?.id == "pallor")   r.scoreOrIndex else sc.pallorScore
                                val jaundiceResult = if (selectedTest?.id == "jaundice") r.result else sc.jaundiceResult
                                val jaundiceIndex  = if (selectedTest?.id == "jaundice") r.scoreOrIndex else sc.jaundiceIndex
                                saving = true
                                ScreeningRepository.updatePhysicalResults(
                                    sc.id, pallorResult, pallorScore, jaundiceResult, jaundiceIndex,
                                    onSuccess = {
                                        saving = false
                                        screeningVm.refreshScreening(sc.id)
                                        onShowMessage("Result saved.")
                                    },
                                    onError = { saving = false; onShowMessage("Failed to save: $it") }
                                )
                            },
                            loading = saving
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlineButton(
                            text = "Retake test",
                            onClick = {
                                lastResult = null
                                qualityError = null
                            }
                        )
                    }
                }
            }
        }
    }
}

// ─── APPLICANT: QUESTIONNAIRE TAB ────────────────────────────────────────────

@Composable
private fun ApplicantQuestionnaireTab(screeningVm: ScreeningViewModel, onShowMessage: (String) -> Unit) {
    val screening by screeningVm.currentScreening.collectAsState()

    if (screening == null) {
        EmptyState(
            icon = Icons.Outlined.Assignment,
            title = "No active event",
            subtitle = "Join an event from the Event tab first."
        )
        return
    }

    var answers  by remember { mutableStateOf<Map<Int, Boolean>>(emptyMap()) }
    var step     by remember { mutableStateOf(0) }
    var saving   by remember { mutableStateOf(false) }
    val questions = QuestionnaireRuleEngine.QUESTIONS

    Column(Modifier.fillMaxSize().background(SurfaceBg)) {
        BrandTopBar(
            title = "Health questionnaire",
            subtitle = "Step 3 of 4 — answer each question honestly"
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val currentQ = questions.getOrNull(step)

            if (currentQ != null) {
                // Progress
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Question ${step + 1} of ${questions.size}", fontSize = 12.sp, color = TextSecondary)
                    Text("${((step.toFloat() / questions.size) * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = BrandRed)
                }
                LinearProgressIndicator(
                    progress = { step.toFloat() / questions.size },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = BrandRed,
                    trackColor = GrayBg
                )

                BrandCard {
                    Text(currentQ, fontSize = 14.sp, color = TextPrimary, lineHeight = 22.sp)
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { answers = answers + (step to false); step++ },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GreenBg, contentColor = GreenText)
                        ) { Text("No", fontWeight = FontWeight.Medium) }
                        Button(
                            onClick = { answers = answers + (step to true); step++ },
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandRedLight, contentColor = BrandRedDark)
                        ) { Text("Yes", fontWeight = FontWeight.Medium) }
                    }
                }

                if (step > 0) {
                    TextButton(onClick = { step--; answers = answers - step }) {
                        Text("← Go back to previous question", color = TextSecondary, fontSize = 13.sp)
                    }
                }

            } else if (answers.size == questions.size) {
                // All answered
                val outcome = QuestionnaireRuleEngine.evaluate(answers)

                BrandCard {
                    Text("Questionnaire complete", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Preliminary outcome", fontSize = 13.sp, color = TextSecondary)
                        StatusBadge(
                            text = when (outcome.status) {
                                QuestionnaireStatus.ELIGIBLE      -> "Eligible"
                                QuestionnaireStatus.TEMP_DEFERRED -> "Temporarily deferred"
                                QuestionnaireStatus.NOT_ELIGIBLE  -> "Not eligible"
                                QuestionnaireStatus.NOT_DONE      -> "Incomplete"
                            },
                            type = when (outcome.status) {
                                QuestionnaireStatus.ELIGIBLE      -> BadgeType.GREEN
                                QuestionnaireStatus.TEMP_DEFERRED -> BadgeType.AMBER
                                QuestionnaireStatus.NOT_ELIGIBLE  -> BadgeType.RED
                                QuestionnaireStatus.NOT_DONE      -> BadgeType.GRAY
                            }
                        )
                    }
                    outcome.deferralReason?.let { reason ->
                        Spacer(Modifier.height(8.dp))
                        Text("Reason: $reason", fontSize = 12.sp, color = TextSecondary)
                    }
                }

                DisclaimerBox("This is a preliminary result only. Final eligibility is confirmed on-site by licensed medical staff.")

                PrimaryButton(
                    text = "Save and continue",
                    onClick = {
                        screening?.let { s ->
                            saving = true
                            ScreeningRepository.updateQuestionnaireResult(
                                s.id, outcome.status, outcome.deferralReason, outcome.deferralUntil,
                                onSuccess = {
                                    saving = false
                                    screeningVm.refreshScreening(s.id)
                                    onShowMessage("Questionnaire saved. Check your result in the Result tab.")
                                },
                                onError = { saving = false; onShowMessage("Failed to save: $it") }
                            )
                        }
                    },
                    loading = saving
                )

                TextButton(onClick = { step = 0; answers = emptyMap() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Restart questionnaire", color = TextSecondary, fontSize = 13.sp)
                }
            }
        }
    }
}

// ─── APPLICANT: RESULT TAB ───────────────────────────────────────────────────

@Composable
private fun ApplicantResultTab(screeningVm: ScreeningViewModel) {
    val screening by screeningVm.currentScreening.collectAsState()
    val sc = screening

    if (sc == null) {
        EmptyState(
            icon = Icons.Outlined.CheckCircle,
            title = "No active screening",
            subtitle = "Join an event and complete at least one step to see your result."
        )
        return
    }

    val hasAnyResult = sc.pallorResult != PhysicalTestResult.NOT_DONE ||
            sc.jaundiceResult != PhysicalTestResult.NOT_DONE ||
            sc.questionnaireStatus != QuestionnaireStatus.NOT_DONE

    if (!hasAnyResult) {
        EmptyState(
            icon = Icons.Outlined.CheckCircle,
            title = "No results yet",
            subtitle = "Complete the physical screening or health questionnaire to see your result."
        )
        return
    }

    val eligible   = sc.overallStatus == OverallStatus.ELIGIBLE
    val deferred   = sc.overallStatus == OverallStatus.TEMP_DEFERRED
    val heroColor  = when (sc.overallStatus) {
        OverallStatus.ELIGIBLE      -> Color(0xFF1A6B2E)
        OverallStatus.TEMP_DEFERRED -> Color(0xFF7A4F00)
        OverallStatus.NOT_ELIGIBLE  -> BrandRedDark
        OverallStatus.INCOMPLETE    -> TextSecondary
    }
    val heroTitle = when (sc.overallStatus) {
        OverallStatus.ELIGIBLE      -> "You are eligible"
        OverallStatus.TEMP_DEFERRED -> "Temporarily deferred"
        OverallStatus.NOT_ELIGIBLE  -> "Not eligible at this time"
        OverallStatus.INCOMPLETE    -> "Screening in progress"
    }
    val heroSubtitle = when (sc.overallStatus) {
        OverallStatus.ELIGIBLE      -> "Proceed to the donation area"
        OverallStatus.TEMP_DEFERRED -> "You may be eligible after the deferral period"
        OverallStatus.NOT_ELIGIBLE  -> "Please speak with staff for guidance"
        OverallStatus.INCOMPLETE    -> "Complete all steps to see your final result"
    }

    Column(Modifier.fillMaxSize().background(SurfaceBg)) {
        // Hero result banner
        Column(
            Modifier
                .fillMaxWidth()
                .background(heroColor)
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (eligible) Icons.Outlined.CheckCircle else Icons.Outlined.Info,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(heroTitle, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Text(heroSubtitle, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (deferred && sc.deferralReason != null) {
                Surface(
                    shape = RoundedCornerShape(topStart = 0.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 0.dp),
                    color = CardWhite,
                    border = BorderStroke(0.5.dp, BorderColor),
                    modifier = Modifier.fillMaxWidth().border(
                        start = BorderStroke(3.dp, BrandRed),
                        shape = RoundedCornerShape(topStart = 0.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 0.dp)
                    )
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Reason for deferral", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = BrandRedDark)
                        Text(sc.deferralReason, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
                        sc.deferralUntil?.let { until ->
                            Text(
                                "You may try again after ${SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(until))}",
                                fontSize = 12.sp,
                                color = TextHint,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            BrandCard {
                Text("Screening summary", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary, modifier = Modifier.padding(bottom = 10.dp))

                val pallorLabel: String
                val pallorBadge: BadgeType
                when (sc.pallorResult) {
                    PhysicalTestResult.NORMAL        -> { pallorLabel = "Normal";        pallorBadge = BadgeType.GREEN }
                    PhysicalTestResult.POSSIBLE_SIGN -> { pallorLabel = "Possible sign"; pallorBadge = BadgeType.RED   }
                    PhysicalTestResult.NOT_DONE      -> { pallorLabel = "Not done";      pallorBadge = BadgeType.GRAY  }
                }
                val jaundiceLabel: String
                val jaundiceBadge: BadgeType
                when (sc.jaundiceResult) {
                    PhysicalTestResult.NORMAL        -> { jaundiceLabel = "Normal";        jaundiceBadge = BadgeType.GREEN }
                    PhysicalTestResult.POSSIBLE_SIGN -> { jaundiceLabel = "Possible sign"; jaundiceBadge = BadgeType.RED   }
                    PhysicalTestResult.NOT_DONE      -> { jaundiceLabel = "Not done";      jaundiceBadge = BadgeType.GRAY  }
                }
                // Cyanosis + Skin Lesion are under development — always show "Not assessed"
                val cyanosisLabel = "Not assessed"
                val cyanosisBadge = BadgeType.GRAY
                val skinLesionLabel = "Not assessed"
                val skinLesionBadge = BadgeType.GRAY
                val questionnaireLabel: String
                val questionnaireBadge: BadgeType
                when (sc.questionnaireStatus) {
                    QuestionnaireStatus.ELIGIBLE      -> { questionnaireLabel = "Passed";               questionnaireBadge = BadgeType.GREEN }
                    QuestionnaireStatus.TEMP_DEFERRED -> { questionnaireLabel = "Temporarily deferred"; questionnaireBadge = BadgeType.AMBER }
                    QuestionnaireStatus.NOT_ELIGIBLE  -> { questionnaireLabel = "Not eligible";         questionnaireBadge = BadgeType.RED   }
                    QuestionnaireStatus.NOT_DONE      -> { questionnaireLabel = "Not done";             questionnaireBadge = BadgeType.GRAY  }
                }

                val summaryRows: List<Pair<String, Pair<String, BadgeType>>> = listOf(
                    Pair("Pallor check",         Pair(pallorLabel,        pallorBadge)),
                    Pair("Jaundice check",       Pair(jaundiceLabel,      jaundiceBadge)),
                    Pair("Cyanosis check",       Pair(cyanosisLabel,      cyanosisBadge)),
                    Pair("Skin lesion check",    Pair(skinLesionLabel,    skinLesionBadge)),
                    Pair("Health questionnaire", Pair(questionnaireLabel, questionnaireBadge))
                )
                summaryRows.forEachIndexed { index, (label, result) ->
                    if (index > 0) SectionDivider()
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, fontSize = 13.sp, color = TextSecondary)
                        StatusBadge(result.first, result.second)
                    }
                }
            }

            // QR code card (shown when eligible or deferred — for staff to scan)
            BrandCard {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Show this to staff at the venue", fontSize = 12.sp, color = TextSecondary)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        sc.id.take(16),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(12.dp))
                    QrHelper.encodeToBitmap(sc.id, 256)?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Screening QR code for staff",
                            modifier = Modifier.size(180.dp).clip(RoundedCornerShape(8.dp))
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Screening QR code", fontSize = 11.sp, color = TextHint)
                }
            }

            DisclaimerBox("This is a pre-screening result only. It is not a medical diagnosis. Final eligibility is confirmed on-site by licensed staff.")
        }
    }
}

// ─── APPLICANT: PROFILE TAB ──────────────────────────────────────────────────

@Composable
private fun ApplicantProfileTab(authVm: AuthViewModel, onLogout: () -> Unit, alertsVm: AlertsViewModel) {
    val user   by authVm.loggedInUser.collectAsState()
    val alerts by alertsVm.alerts.collectAsState()
    LaunchedEffect(Unit) { alertsVm.startListening() }

    var showEditProfile by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var isDeleting by remember { mutableStateOf(false) }

    // ── Edit Profile Dialog ──────────────────────────────────────────────
    if (showEditProfile && user != null) {
        var editName by remember { mutableStateOf(user?.name ?: "") }
        var editPhone by remember { mutableStateOf(user?.phone ?: "") }

        AlertDialog(
            onDismissRequest = { showEditProfile = false },
            containerColor = CardWhite,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Edit profile", fontWeight = FontWeight.Medium, fontSize = 17.sp, color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Full name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editPhone,
                        onValueChange = { editPhone = it },
                        label = { Text("Phone number") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editName.isNotBlank()) {
                        authVm.updateProfile(editName.trim(), editPhone.trim())
                        showEditProfile = false
                    }
                }) {
                    Text("Save", color = BrandRed, fontWeight = FontWeight.Medium)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfile = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // ── Delete Account Confirmation Dialog ────────────────────────────────
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) { showDeleteConfirm = false; deleteError = null } },
            containerColor = CardWhite,
            shape = RoundedCornerShape(20.dp),
            title = { Text("Delete your account?", fontWeight = FontWeight.Medium, fontSize = 17.sp, color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "This will permanently delete your account, profile, screening records, and all associated data. This action cannot be undone.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        lineHeight = 19.sp
                    )
                    Text(
                        "Under RA 10173 (Data Privacy Act of 2012), you have the right to request erasure of your personal data.",
                        fontSize = 12.sp,
                        color = TextHint,
                        lineHeight = 17.sp
                    )
                    if (deleteError != null) {
                        Text(deleteError.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        isDeleting = true
                        deleteError = null
                        authVm.deleteAccount(
                            onSuccess = {
                                isDeleting = false
                                showDeleteConfirm = false
                                onLogout()
                            },
                            onError = { msg ->
                                isDeleting = false
                                deleteError = msg
                            }
                        )
                    },
                    enabled = !isDeleting
                ) {
                    Text(
                        if (isDeleting) "Deleting..." else "Delete permanently",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            dismissButton = {
                if (!isDeleting) {
                    TextButton(onClick = { showDeleteConfirm = false; deleteError = null }) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            }
        )
    }

    // ── Help Screen Dialog ───────────────────────────────────────────────
    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            containerColor = CardWhite,
            shape = RoundedCornerShape(20.dp),
            title = { Text("How to use BloodLink", fontWeight = FontWeight.Medium, fontSize = 17.sp, color = TextPrimary) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    HelpSection(
                        title = "Joining an event",
                        body = "Go to the Event tab and enter the event code provided by the blood drive organizer, or scan the QR code displayed at the venue."
                    )
                    HelpSection(
                        title = "Pallor test (eye)",
                        body = "Pull your lower eyelid down gently to expose the pink inner surface. Hold your phone 20-30 cm from your eye in good lighting (natural or white fluorescent). The AI analyzes the conjunctiva color for signs of anemia."
                    )
                    HelpSection(
                        title = "Jaundice test (eye)",
                        body = "Look slightly upward so the white part of your eye (sclera) is well exposed. Hold your eye wide open in bright, even lighting. The AI checks for yellow discoloration which may indicate liver issues."
                    )
                    HelpSection(
                        title = "Tips for best results",
                        body = "Use natural daylight or bright white fluorescent light. Avoid yellow or warm-toned lighting. Keep your phone steady and close (20-30 cm). Make sure the target area fills most of the camera frame. Remove glasses or contact lenses if possible."
                    )
                    HelpSection(
                        title = "Understanding results",
                        body = "\"Normal\" means no concerning signs were detected. \"Possible sign\" means the AI detected something worth reviewing — this is NOT a diagnosis. A trained staff member will review all results during the screening event."
                    )
                    HelpSection(
                        title = "Privacy and data",
                        body = "Your photos are processed on-device and are never uploaded. Only the screening result (Normal or Possible sign) is stored. You can delete your account and all data at any time from this screen."
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) {
                    Text("Got it", color = BrandRed, fontWeight = FontWeight.Medium)
                }
            }
        )
    }

    Column(Modifier.fillMaxSize().background(SurfaceBg)) {
        BrandTopBar(
            title = user?.name ?: "Profile",
            subtitle = user?.email ?: ""
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            user?.let { u ->
                BrandCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AvatarCircle(u.name.take(2), modifier = Modifier.size(48.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(u.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                            Text(u.email, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp))
                            if (u.phone.isNotBlank()) {
                                Text(u.phone, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 1.dp))
                            }
                            StatusBadge(
                                text = u.role.name.lowercase().replaceFirstChar { it.uppercase() },
                                type = BadgeType.GRAY
                            )
                        }
                        IconButton(onClick = { showEditProfile = true }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit profile", tint = BrandRed, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            // ── Quick actions ────────────────────────────────────────────
            BrandCard {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    ProfileMenuItem(icon = Icons.Outlined.HelpOutline, label = "Help & FAQ", onClick = { showHelp = true })
                    SectionDivider()
                    ProfileMenuItem(icon = Icons.Outlined.Logout, label = "Log out", onClick = onLogout)
                    SectionDivider()
                    ProfileMenuItem(icon = Icons.Outlined.DeleteForever, label = "Delete account", onClick = { showDeleteConfirm = true }, isDestructive = true)
                }
            }

            // ── Notifications ────────────────────────────────────────────
            Text("Notifications", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)

            if (alerts.isEmpty()) {
                BrandCard {
                    Text("No notifications yet.", fontSize = 13.sp, color = TextHint, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            } else {
                alerts.forEach { alert ->
                    Card(
                        onClick = { alertsVm.markRead(alert.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = if (alert.read) CardWhite else BrandRedLight),
                        border = BorderStroke(0.5.dp, BorderColor)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(alert.title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary, modifier = Modifier.weight(1f))
                                if (!alert.read) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(BrandRed))
                                }
                            }
                            Text(alert.body, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun ProfileMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (isDestructive) MaterialTheme.colorScheme.error else TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            label,
            fontSize = 14.sp,
            color = if (isDestructive) MaterialTheme.colorScheme.error else TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = TextHint,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun HelpSection(title: String, body: String) {
    Column {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
        Spacer(Modifier.height(4.dp))
        Text(body, fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp)
    }
}

// ─── STAFF SHELL ─────────────────────────────────────────────────────────────

enum class StaffTab { EVENTS, APPLICANTS, VERIFY, ANALYTICS, SETTINGS }

@Composable
fun StaffShell(authVm: AuthViewModel, onLogout: () -> Unit) {
    var tab         by remember { mutableStateOf(StaffTab.EVENTS) }
    val eventVm:    EventViewModel              = viewModel()
    val staffVm:    StaffApplicantsViewModel    = viewModel()
    val user        by authVm.loggedInUser.collectAsState()
    val selectedEvent by eventVm.selectedEvent.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val showMessage: (String) -> Unit = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }

    LaunchedEffect(user?.orgId) { user?.orgId?.let { eventVm.startListening(it) } }
    LaunchedEffect(selectedEvent) {
        selectedEvent?.id?.let { staffVm.setEventId(it) } ?: staffVm.setEventId("")
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = SurfaceBg,
        bottomBar = {
            NavigationBar(
                containerColor = CardWhite,
                tonalElevation = 0.dp,
                modifier = Modifier.border(BorderStroke(0.5.dp, BorderColor), shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp))
            ) {
                listOf(
                    Triple(StaffTab.EVENTS,      Icons.Outlined.Event,       "Events"),
                    Triple(StaffTab.APPLICANTS,  Icons.Outlined.People,      "Applicants"),
                    Triple(StaffTab.VERIFY,      Icons.Outlined.QrCodeScanner,"Verify"),
                    Triple(StaffTab.ANALYTICS,   Icons.Outlined.BarChart,    "Analytics"),
                    Triple(StaffTab.SETTINGS,    Icons.Outlined.Settings,    "Settings")
                ).forEach { (t, icon, label) ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(label, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = BrandRed,
                            selectedTextColor = BrandRed,
                            indicatorColor = BrandRedLight
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                StaffTab.EVENTS      -> StaffEventsTab(authVm, eventVm, showMessage)
                StaffTab.APPLICANTS  -> StaffApplicantsTab(eventVm, staffVm, showMessage)
                StaffTab.VERIFY      -> StaffVerifyTab(staffVm, showMessage)
                StaffTab.ANALYTICS   -> StaffAnalyticsTab(eventVm, staffVm)
                StaffTab.SETTINGS    -> StaffSettingsTab(authVm, onLogout, viewModel(), showMessage)
            }
        }
    }
}

// ─── STAFF: EVENTS TAB ───────────────────────────────────────────────────────

@Composable
private fun StaffEventsTab(authVm: AuthViewModel, eventVm: EventViewModel, onShowMessage: (String) -> Unit) {
    val events        by eventVm.events.collectAsState()
    val selectedEvent by eventVm.selectedEvent.collectAsState()
    val user          by authVm.loggedInUser.collectAsState()
    val isAdmin       = user?.role == UserRole.ADMIN
    val canCreate     = (user?.role == UserRole.ADMIN || user?.role == UserRole.STAFF) && !user?.orgId.isNullOrBlank()
    val orgId         = user?.orgId

    var showCreate        by remember { mutableStateOf(false) }
    var showEdit          by remember { mutableStateOf<Event?>(null) }
    var deleteTarget      by remember { mutableStateOf<Event?>(null) }
    var showQrForEventId  by remember { mutableStateOf<String?>(null) }
    var newTitle          by remember { mutableStateOf("") }
    var newLocation       by remember { mutableStateOf("") }
    var createError       by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(SurfaceBg)) {
        val ev = selectedEvent
        BrandTopBar(
            title = "Events",
            subtitle = if (ev != null) "Selected: ${ev.title}" else "Select an event to begin",
            actions = {
                if (canCreate) {
                    IconButton(onClick = { showCreate = true; newTitle = ""; newLocation = ""; createError = null }) {
                        Icon(Icons.Outlined.Add, contentDescription = "Create event", tint = Color.White)
                    }
                }
            }
        )

        if (orgId.isNullOrBlank()) {
            EmptyState(
                icon = Icons.Outlined.Business,
                title = "Organization not set",
                subtitle = "Go to the Settings tab and enter your organization ID."
            )
            return@Column
        }

        if (events.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                EmptyState(
                    icon = Icons.Outlined.Event,
                    title = "No events yet",
                    subtitle = if (canCreate) "Tap the + button above to create your first event." else "No events found for your organization."
                )
            }
            return@Column
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 20.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(events) { e ->
                val isSelected = e.id == selectedEvent?.id
                Card(
                    onClick = {
                        eventVm.selectEvent(e)
                        onShowMessage("\"${e.title}\" selected. Applicants and Analytics will show data for this event.")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    border = BorderStroke(if (isSelected) 2.dp else 0.5.dp, if (isSelected) BrandRed else BorderColor)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(e.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary, modifier = Modifier.weight(1f))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                StatusBadge(
                                    text = e.status.name.lowercase().replaceFirstChar { it.uppercase() },
                                    type = if (e.status == EventStatus.ACTIVE) BadgeType.GREEN else BadgeType.GRAY
                                )
                                if (isSelected) StatusBadge("Selected", BadgeType.RED)
                            }
                        }
                        if (e.location.isNotBlank()) {
                            Text(e.location, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
                        }
                        Text(
                            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(e.dateTime)),
                            fontSize = 11.sp, color = TextHint, modifier = Modifier.padding(top = 2.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        SectionDivider()
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(
                                onClick = { showQrForEventId = e.qrPayload ?: e.id },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) { Text("Show QR", color = BrandRed, fontSize = 12.sp) }
                            if (isAdmin) {
                                TextButton(
                                    onClick = { showEdit = e },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) { Text("Edit", color = TextSecondary, fontSize = 12.sp) }
                                if (e.status == EventStatus.ACTIVE) {
                                    TextButton(
                                        onClick = {
                                            eventVm.updateEvent(e.id, mapOf("status" to EventStatus.CLOSED.name),
                                                onSuccess = { onShowMessage("Event closed.") },
                                                onError = { onShowMessage("Failed to close event: $it") })
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) { Text("Close", color = TextSecondary, fontSize = 12.sp) }
                                }
                                TextButton(
                                    onClick = { deleteTarget = e },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) { Text("Delete", color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                            }
                        }
                    }
                }
            }
        }
    }

    // Create event dialog
    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Create new event") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (createError != null) Text(createError.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    OutlinedTextField(value = newTitle, onValueChange = { newTitle = it }, label = { Text("Event title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = newLocation, onValueChange = { newLocation = it }, label = { Text("Location") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val oid = user?.orgId
                        if (oid.isNullOrBlank()) { createError = "You need an organization ID to create events."; return@Button }
                        createError = null
                        eventVm.createEvent(oid, newTitle, System.currentTimeMillis(), newLocation, user?.id ?: "",
                            onSuccess = { eventId -> showCreate = false; showQrForEventId = eventId; onShowMessage("Event created.") },
                            onError = { createError = it })
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } }
        )
    }

    // Edit event dialog
    showEdit?.let { e ->
        var editTitle    by remember(e.id) { mutableStateOf(e.title) }
        var editLocation by remember(e.id) { mutableStateOf(e.location) }
        var editError    by remember(e.id) { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showEdit = null },
            title = { Text("Edit event") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (editError != null) Text(editError.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    OutlinedTextField(value = editTitle, onValueChange = { editTitle = it }, label = { Text("Event title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = editLocation, onValueChange = { editLocation = it }, label = { Text("Location") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        editError = null
                        eventVm.updateEvent(e.id, mapOf("title" to editTitle, "location" to editLocation),
                            onSuccess = { showEdit = null; onShowMessage("Event updated.") },
                            onError = { editError = it })
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                ) { Text("Save changes") }
            },
            dismissButton = { TextButton(onClick = { showEdit = null }) { Text("Cancel") } }
        )
    }

    // Delete confirmation dialog
    deleteTarget?.let { e ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete event?") },
            text = { Text("\"${e.title}\" will be permanently deleted. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        eventVm.deleteEvent(e.id,
                            onSuccess = { deleteTarget = null; onShowMessage("Event deleted.") },
                            onError = { onShowMessage("Failed to delete: $it") })
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }

    // Event QR dialog
    showQrForEventId?.let { eventId ->
        AlertDialog(
            onDismissRequest = { showQrForEventId = null },
            title = { Text("Event QR code") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Share this with donors so they can join the event.", fontSize = 12.sp, color = TextSecondary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Text(eventId, fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp, color = TextPrimary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    QrHelper.encodeToBitmap(eventId, 256)?.let { bmp ->
                        Image(bitmap = bmp.asImageBitmap(), contentDescription = "Event QR code", modifier = Modifier.size(200.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showQrForEventId = null }) { Text("Done") } }
        )
    }
}

// ─── STAFF: APPLICANTS TAB ───────────────────────────────────────────────────

@Composable
private fun StaffApplicantsTab(eventVm: EventViewModel, staffVm: StaffApplicantsViewModel, onShowMessage: (String) -> Unit) {
    val selectedEvent by eventVm.selectedEvent.collectAsState()
    val events        by eventVm.events.collectAsState()
    val screenings    by staffVm.screenings.collectAsState()
    val filter        by staffVm.filter.collectAsState()
    var outcomeDialog by remember { mutableStateOf<Screening?>(null) }
    var summaryDialog by remember { mutableStateOf<Screening?>(null) }

    val filteredList = remember(screenings, filter) {
        when (filter) {
            ApplicantListFilter.ALL          -> screenings
            ApplicantListFilter.INCOMPLETE   -> screenings.filter { it.overallStatus == OverallStatus.INCOMPLETE }
            ApplicantListFilter.ELIGIBLE     -> screenings.filter { it.overallStatus == OverallStatus.ELIGIBLE }
            ApplicantListFilter.TEMP_DEFERRED -> screenings.filter { it.overallStatus == OverallStatus.TEMP_DEFERRED }
            ApplicantListFilter.NOT_ELIGIBLE  -> screenings.filter { it.overallStatus == OverallStatus.NOT_ELIGIBLE }
        }
    }

    Column(Modifier.fillMaxSize().background(SurfaceBg)) {
        BrandTopBar(
            title = "Applicants",
            subtitle = selectedEvent?.title ?: "Select an event from Events"
        )

        if (selectedEvent == null) {
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                EmptyState(
                    icon = Icons.Outlined.People,
                    title = "No event selected",
                    subtitle = "Select an event from the Events tab to view applicants."
                )
                if (events.isNotEmpty()) {
                    Text("Quick select:", fontSize = 12.sp, color = TextSecondary)
                    events.take(3).forEach { e ->
                        OutlineButton(text = e.title, onClick = { eventVm.selectEvent(e) })
                    }
                }
            }
            return@Column
        }

        Column(Modifier.fillMaxSize()) {
            // Stats bar
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(CardWhite)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val eligible  = screenings.count { it.overallStatus == OverallStatus.ELIGIBLE }
                val deferred  = screenings.count { it.overallStatus == OverallStatus.TEMP_DEFERRED }
                val incomplete = screenings.count { it.overallStatus == OverallStatus.INCOMPLETE }
                StatCard(eligible.toString(), "Eligible", GreenText)
                StatCard(deferred.toString(), "Deferred", AmberText)
                StatCard(incomplete.toString(), "Pending", TextHint)
                StatCard(screenings.size.toString(), "Total")
            }

            // Filter chips
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ApplicantListFilter.entries.forEach { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { staffVm.setFilter(f) },
                        label = {
                            Text(when (f) {
                                ApplicantListFilter.ALL           -> "All"
                                ApplicantListFilter.INCOMPLETE    -> "Incomplete"
                                ApplicantListFilter.ELIGIBLE      -> "Eligible"
                                ApplicantListFilter.TEMP_DEFERRED -> "Deferred"
                                ApplicantListFilter.NOT_ELIGIBLE  -> "Not eligible"
                            }, fontSize = 12.sp)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BrandRedLight,
                            selectedLabelColor = BrandRedDark
                        )
                    )
                }
            }

            if (filteredList.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.People,
                    title = if (screenings.isEmpty()) "No applicants yet" else "No results",
                    subtitle = if (screenings.isEmpty()) "Applicants will appear here once they join this event." else "No applicants match this filter."
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredList) { s ->
                        BrandCard(modifier = Modifier.clickable { summaryDialog = s }) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AvatarCircle(
                                    (s.applicantName?.take(2) ?: s.applicantUid.take(2)).uppercase()
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        s.applicantName?.ifBlank { null } ?: "ID: ${s.applicantUid.take(10)}…",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "Updated ${SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(s.updatedAt))}",
                                        fontSize = 11.sp,
                                        color = TextHint,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                StatusBadge(
                                    text = when (s.overallStatus) {
                                        OverallStatus.ELIGIBLE      -> "Eligible"
                                        OverallStatus.TEMP_DEFERRED -> "Deferred"
                                        OverallStatus.NOT_ELIGIBLE  -> "Not eligible"
                                        OverallStatus.INCOMPLETE    -> "Incomplete"
                                    },
                                    type = when (s.overallStatus) {
                                        OverallStatus.ELIGIBLE      -> BadgeType.GREEN
                                        OverallStatus.TEMP_DEFERRED -> BadgeType.AMBER
                                        OverallStatus.NOT_ELIGIBLE  -> BadgeType.RED
                                        OverallStatus.INCOMPLETE    -> BadgeType.GRAY
                                    }
                                )
                            }
                            if (s.finalOutcome != FinalOutcome.NONE) {
                                Spacer(Modifier.height(6.dp))
                                Text("Final outcome: ${s.finalOutcome.label}", fontSize = 11.sp, color = TextSecondary)
                            }
                            Spacer(Modifier.height(10.dp))
                            PrimaryButton(
                                text = "Set final outcome",
                                onClick = { outcomeDialog = s }
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Screening Summary Dialog ──────────────────────────────────────
    summaryDialog?.let { s ->
        AlertDialog(
            onDismissRequest = { summaryDialog = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AvatarCircle((s.applicantName?.take(2) ?: s.applicantUid.take(2)).uppercase())
                    Column {
                        Text(s.applicantName?.ifBlank { null } ?: "Applicant", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("Updated ${SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(s.updatedAt))}", fontSize = 11.sp, color = TextHint)
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Physical screening section
                    Text("Physical Screening", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextSecondary)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Pallor (anemia)", fontSize = 12.sp, color = TextPrimary)
                        StatusBadge(
                            text = when (s.pallorResult) {
                                PhysicalTestResult.NORMAL -> "Normal" + (s.pallorScore?.let { " · ${(it * 100).toInt()}%" } ?: "")
                                PhysicalTestResult.POSSIBLE_SIGN -> "Possible sign" + (s.pallorScore?.let { " · ${(it * 100).toInt()}%" } ?: "")
                                PhysicalTestResult.NOT_DONE -> "Not done"
                            },
                            type = when (s.pallorResult) {
                                PhysicalTestResult.NORMAL -> BadgeType.GREEN
                                PhysicalTestResult.POSSIBLE_SIGN -> BadgeType.RED
                                PhysicalTestResult.NOT_DONE -> BadgeType.GRAY
                            }
                        )
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Jaundice", fontSize = 12.sp, color = TextPrimary)
                        StatusBadge(
                            text = when (s.jaundiceResult) {
                                PhysicalTestResult.NORMAL -> "Normal" + (s.jaundiceIndex?.let { " · ${(it * 100).toInt()}%" } ?: "")
                                PhysicalTestResult.POSSIBLE_SIGN -> "Possible sign" + (s.jaundiceIndex?.let { " · ${(it * 100).toInt()}%" } ?: "")
                                PhysicalTestResult.NOT_DONE -> "Not done"
                            },
                            type = when (s.jaundiceResult) {
                                PhysicalTestResult.NORMAL -> BadgeType.GREEN
                                PhysicalTestResult.POSSIBLE_SIGN -> BadgeType.RED
                                PhysicalTestResult.NOT_DONE -> BadgeType.GRAY
                            }
                        )
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Cyanosis", fontSize = 12.sp, color = TextPrimary)
                        StatusBadge(text = "Not assessed", type = BadgeType.GRAY)
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Skin lesion", fontSize = 12.sp, color = TextPrimary)
                        StatusBadge(text = "Not assessed", type = BadgeType.GRAY)
                    }

                    Spacer(Modifier.height(4.dp))
                    SectionDivider()
                    Spacer(Modifier.height(4.dp))

                    // Questionnaire section
                    Text("Health Questionnaire", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Status", fontSize = 12.sp, color = TextPrimary)
                        StatusBadge(
                            text = when (s.questionnaireStatus) {
                                QuestionnaireStatus.NOT_DONE -> "Not done"
                                QuestionnaireStatus.ELIGIBLE -> "Eligible"
                                QuestionnaireStatus.TEMP_DEFERRED -> "Temp. deferred"
                                QuestionnaireStatus.NOT_ELIGIBLE -> "Not eligible"
                            },
                            type = when (s.questionnaireStatus) {
                                QuestionnaireStatus.ELIGIBLE -> BadgeType.GREEN
                                QuestionnaireStatus.TEMP_DEFERRED -> BadgeType.AMBER
                                QuestionnaireStatus.NOT_ELIGIBLE -> BadgeType.RED
                                QuestionnaireStatus.NOT_DONE -> BadgeType.GRAY
                            }
                        )
                    }

                    if (s.finalOutcome != FinalOutcome.NONE) {
                        Spacer(Modifier.height(4.dp))
                        SectionDivider()
                        Spacer(Modifier.height(4.dp))
                        Text("Staff Assessment", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Final outcome", fontSize = 12.sp, color = TextPrimary)
                            StatusBadge(
                                text = s.finalOutcome.label,
                                type = when (s.finalOutcome) {
                                    FinalOutcome.DONATED -> BadgeType.GREEN
                                    FinalOutcome.ACCEPTED_ONSITE -> BadgeType.GREEN
                                    FinalOutcome.DEFERRED_ONSITE -> BadgeType.AMBER
                                    FinalOutcome.NO_SHOW -> BadgeType.RED
                                    else -> BadgeType.GRAY
                                }
                            )
                        }
                        if (!s.staffNotes.isNullOrBlank()) {
                            Text("Notes: ${s.staffNotes}", fontSize = 11.sp, color = TextHint, modifier = Modifier.padding(top = 4.dp))
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // Overall status banner
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = when (s.overallStatus) {
                                    OverallStatus.ELIGIBLE -> androidx.compose.ui.graphics.Color(0xFFEAF3DE)
                                    OverallStatus.TEMP_DEFERRED -> androidx.compose.ui.graphics.Color(0xFFFAEEDA)
                                    OverallStatus.NOT_ELIGIBLE -> androidx.compose.ui.graphics.Color(0xFFFCEBEB)
                                    OverallStatus.INCOMPLETE -> androidx.compose.ui.graphics.Color(0xFFF3F4F6)
                                },
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = when (s.overallStatus) {
                                OverallStatus.ELIGIBLE -> "✓ Eligible for donation"
                                OverallStatus.TEMP_DEFERRED -> "⏸ Temporarily deferred"
                                OverallStatus.NOT_ELIGIBLE -> "✗ Not eligible"
                                OverallStatus.INCOMPLETE -> "⋯ Screening incomplete"
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = when (s.overallStatus) {
                                OverallStatus.ELIGIBLE -> GreenText
                                OverallStatus.TEMP_DEFERRED -> AmberText
                                OverallStatus.NOT_ELIGIBLE -> BrandRedDark
                                OverallStatus.INCOMPLETE -> TextHint
                            }
                        )
                    }

                    Spacer(Modifier.height(2.dp))
                    Text(
                        "BloodLink pre-screening summary · Not a medical diagnosis",
                        fontSize = 10.sp,
                        color = TextHint,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { summaryDialog = null; outcomeDialog = s },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                ) { Text("Set outcome") }
            },
            dismissButton = { TextButton(onClick = { summaryDialog = null }) { Text("Close") } }
        )
    }

    // Set outcome dialog
    outcomeDialog?.let { s ->
        var outcome  by remember(s.id) { mutableStateOf(s.finalOutcome) }
        var notes    by remember(s.id) { mutableStateOf(s.staffNotes ?: "") }
        var errMsg   by remember(s.id) { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { outcomeDialog = null },
            title = { Text("Set outcome — ${s.applicantName ?: s.applicantUid.take(8)}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (errMsg != null) Text(errMsg.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    Text("Final outcome:", fontSize = 12.sp, color = TextSecondary)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FinalOutcome.entries.filter { it != FinalOutcome.NONE }.forEach { o ->
                            FilterChip(
                                selected = outcome == o,
                                onClick = { outcome = o },
                                label = { Text(o.label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BrandRedLight,
                                    selectedLabelColor = BrandRedDark
                                )
                            )
                        }
                    }
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Staff notes (optional)") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        errMsg = null
                        staffVm.updateFinalOutcome(s.id, outcome, notes.ifBlank { null }, s.applicantUid,
                            onSuccess = { outcomeDialog = null; onShowMessage("Outcome saved.") },
                            onError = { errMsg = it })
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                ) { Text("Save outcome") }
            },
            dismissButton = { TextButton(onClick = { outcomeDialog = null }) { Text("Cancel") } }
        )
    }
}

// ─── STAFF: VERIFY TAB ───────────────────────────────────────────────────────

@Composable
private fun StaffVerifyTab(staffVm: StaffApplicantsViewModel, onShowMessage: (String) -> Unit) {
    var screeningId  by remember { mutableStateOf("") }
    var outcome      by remember { mutableStateOf(FinalOutcome.NONE) }
    var notes        by remember { mutableStateOf("") }
    var screening    by remember { mutableStateOf<Screening?>(null) }
    var showScanner  by remember { mutableStateOf(false) }
    var loadingLocal by remember { mutableStateOf(false) }
    var notFound     by remember { mutableStateOf(false) }
    val loading      by staffVm.loading.collectAsState()
    val error        by staffVm.error.collectAsState()
    val busy         = loading || loadingLocal
    val context      = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showScanner = true
    }
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)?.let { bmp ->
                    QrHelper.decodeFromBitmap(bmp)?.let { id -> screeningId = id }
                }
            }
        } catch (_: Exception) {}
    }

    if (showScanner) {
        QrScannerScreen(
            onScanned = { id -> screeningId = id; showScanner = false },
            onCancel   = { showScanner = false },
            hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
            onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) }
        )
        return
    }

    Column(Modifier.fillMaxSize().background(SurfaceBg)) {
        BrandTopBar(title = "Verify donor", subtitle = "Scan the donor's screening QR or enter their ID")

        Column(
            Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Scanner / upload buttons
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                            showScanner = true
                        else permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.5.dp, BrandRed),
                    enabled = !busy
                ) {
                    Icon(Icons.Outlined.QrCodeScanner, null, Modifier.size(18.dp), tint = BrandRed)
                    Spacer(Modifier.width(6.dp))
                    Text("Scan QR", color = BrandRed, fontSize = 14.sp)
                }
                OutlinedButton(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.5.dp, BrandRed),
                    enabled = !busy
                ) {
                    Icon(Icons.Outlined.Image, null, Modifier.size(18.dp), tint = BrandRed)
                    Spacer(Modifier.width(6.dp))
                    Text("Upload image", color = BrandRed, fontSize = 14.sp)
                }
            }

            OutlinedTextField(
                value = screeningId,
                onValueChange = { screeningId = it; notFound = false; staffVm.clearError() },
                label = { Text("Screening ID") },
                placeholder = { Text("Enter or scan the donor's QR") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !busy
            )

            if (notFound) Text("Screening not found. Check the ID or scan the donor's QR again.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)

            PrimaryButton(
                text = "Load screening",
                onClick = {
                    staffVm.clearError()
                    notFound = false
                    loadingLocal = true
                    ScreeningRepository.getScreening(screeningId.trim()) { s ->
                        screening = s
                        loadingLocal = false
                        if (s == null) { notFound = true; onShowMessage("Screening not found.") }
                    }
                },
                enabled = screeningId.isNotBlank(),
                loading = loadingLocal
            )

            // Screening details
            screening?.let { s ->
                BrandCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                s.applicantName?.ifBlank { null } ?: "Applicant",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary
                            )
                            Text(s.id.take(16), fontSize = 11.sp, color = TextHint, modifier = Modifier.padding(top = 2.dp))
                        }
                        StatusBadge(
                            text = when (s.overallStatus) {
                                OverallStatus.ELIGIBLE      -> "Eligible"
                                OverallStatus.TEMP_DEFERRED -> "Deferred"
                                OverallStatus.NOT_ELIGIBLE  -> "Not eligible"
                                OverallStatus.INCOMPLETE    -> "Incomplete"
                            },
                            type = when (s.overallStatus) {
                                OverallStatus.ELIGIBLE      -> BadgeType.GREEN
                                OverallStatus.TEMP_DEFERRED -> BadgeType.AMBER
                                OverallStatus.NOT_ELIGIBLE  -> BadgeType.RED
                                OverallStatus.INCOMPLETE    -> BadgeType.GRAY
                            }
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    SectionDivider()

                    val vPallorLabel: String
                    val vPallorBadge: BadgeType
                    when (s.pallorResult) {
                        PhysicalTestResult.NORMAL        -> { vPallorLabel = "Normal";        vPallorBadge = BadgeType.GREEN }
                        PhysicalTestResult.POSSIBLE_SIGN -> { vPallorLabel = "Possible sign"; vPallorBadge = BadgeType.RED   }
                        PhysicalTestResult.NOT_DONE      -> { vPallorLabel = "Not done";      vPallorBadge = BadgeType.GRAY  }
                    }
                    val vJaundiceLabel: String
                    val vJaundiceBadge: BadgeType
                    when (s.jaundiceResult) {
                        PhysicalTestResult.NORMAL        -> { vJaundiceLabel = "Normal";        vJaundiceBadge = BadgeType.GREEN }
                        PhysicalTestResult.POSSIBLE_SIGN -> { vJaundiceLabel = "Possible sign"; vJaundiceBadge = BadgeType.RED   }
                        PhysicalTestResult.NOT_DONE      -> { vJaundiceLabel = "Not done";      vJaundiceBadge = BadgeType.GRAY  }
                    }
                    val vQuestionnaireLabel: String
                    val vQuestionnaireBadge: BadgeType
                    when (s.questionnaireStatus) {
                        QuestionnaireStatus.ELIGIBLE      -> { vQuestionnaireLabel = "Passed";               vQuestionnaireBadge = BadgeType.GREEN }
                        QuestionnaireStatus.TEMP_DEFERRED -> { vQuestionnaireLabel = "Temporarily deferred"; vQuestionnaireBadge = BadgeType.AMBER }
                        QuestionnaireStatus.NOT_ELIGIBLE  -> { vQuestionnaireLabel = "Not eligible";         vQuestionnaireBadge = BadgeType.RED   }
                        QuestionnaireStatus.NOT_DONE      -> { vQuestionnaireLabel = "Not done";             vQuestionnaireBadge = BadgeType.GRAY  }
                    }
                    val verifyRows: List<Pair<String, Pair<String, BadgeType>>> = listOf(
                        Pair("Pallor check",         Pair(vPallorLabel,        vPallorBadge)),
                        Pair("Jaundice check",       Pair(vJaundiceLabel,      vJaundiceBadge)),
                        Pair("Health questionnaire", Pair(vQuestionnaireLabel, vQuestionnaireBadge))
                    )
                    verifyRows.forEach { (label, result) ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, fontSize = 13.sp, color = TextSecondary)
                            StatusBadge(result.first, result.second)
                        }
                        SectionDivider()
                    }
                }

                Text("Set final outcome:", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FinalOutcome.entries.filter { it != FinalOutcome.NONE }.forEach { o ->
                        FilterChip(
                            selected = outcome == o,
                            onClick = { outcome = o },
                            label = { Text(o.label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandRedLight,
                                selectedLabelColor = BrandRedDark
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Staff notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy
                )

                PrimaryButton(
                    text = "Save outcome",
                    onClick = {
                        staffVm.updateFinalOutcome(
                            s.id, outcome, notes.ifBlank { null }, s.applicantUid,
                            onSuccess = {
                                screening = null; screeningId = ""; notes = ""; outcome = FinalOutcome.NONE; notFound = false
                                onShowMessage("Outcome saved.")
                            },
                            onError = { onShowMessage("Failed to save outcome: $it") }
                        )
                    },
                    loading = loading
                )
            }
        }
    }
}

// ─── STAFF: ANALYTICS TAB ────────────────────────────────────────────────────

@Composable
private fun StaffAnalyticsTab(eventVm: EventViewModel, staffVm: StaffApplicantsViewModel) {
    val selectedEvent by eventVm.selectedEvent.collectAsState()
    val screenings    by staffVm.screenings.collectAsState()

    val eligible    = screenings.count { it.overallStatus == OverallStatus.ELIGIBLE }
    val deferred    = screenings.count { it.overallStatus == OverallStatus.TEMP_DEFERRED }
    val notEligible = screenings.count { it.overallStatus == OverallStatus.NOT_ELIGIBLE }
    val incomplete  = screenings.count { it.overallStatus == OverallStatus.INCOMPLETE }
    val total       = screenings.size
    val completed   = total - incomplete
    val completionRate = if (total > 0) (100 * completed / total) else 0

    val deferralReasons = remember(screenings) {
        screenings.mapNotNull { it.deferralReason }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
    }

    Column(Modifier.fillMaxSize().background(SurfaceBg)) {
        BrandTopBar(
            title = "Analytics",
            subtitle = selectedEvent?.title ?: "Select an event from Events"
        )

        if (selectedEvent == null) {
            EmptyState(
                icon = Icons.Outlined.BarChart,
                title = "No event selected",
                subtitle = "Select an event from the Events tab to view analytics."
            )
            return@Column
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Stats grid
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { StatCard(eligible.toString(), "Eligible", GreenText) }
                Box(Modifier.weight(1f)) { StatCard(deferred.toString(), "Deferred", AmberText) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { StatCard(notEligible.toString(), "Not eligible", BrandRedDark) }
                Box(Modifier.weight(1f)) { StatCard(total.toString(), "Total") }
            }

            // Completion rate
            if (total > 0) {
                BrandCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Completion rate", fontSize = 13.sp, color = TextSecondary)
                        Text("$completionRate%", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                    }
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { completionRate / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = BrandRed,
                        trackColor = GrayBg
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("$completed of $total donors completed screening.", fontSize = 11.sp, color = TextHint)
                }
            }

            // Deferral reasons
            if (deferralReasons.isNotEmpty()) {
                Text("Top deferral reasons", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                BrandCard {
                    deferralReasons.take(5).forEachIndexed { index, (reason, count) ->
                        if (index > 0) SectionDivider()
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(reason, fontSize = 13.sp, color = TextPrimary, modifier = Modifier.weight(1f).padding(end = 8.dp))
                            StatusBadge("$count", BadgeType.GRAY)
                        }
                    }
                }
            }

            if (total == 0) {
                EmptyState(
                    icon = Icons.Outlined.BarChart,
                    title = "No data yet",
                    subtitle = "Analytics will appear once donors begin screening."
                )
            }
        }
    }
}

// ─── STAFF: SETTINGS TAB ─────────────────────────────────────────────────────

@Composable
private fun StaffSettingsTab(authVm: AuthViewModel, onLogout: () -> Unit, alertsVm: AlertsViewModel, onShowMessage: (String) -> Unit) {
    val user   by authVm.loggedInUser.collectAsState()
    val alerts by alertsVm.alerts.collectAsState()
    var orgIdInput  by remember(user?.orgId) { mutableStateOf(user?.orgId ?: "") }
    var orgIdSaving by remember { mutableStateOf(false) }
    var orgIdError  by remember { mutableStateOf<String?>(null) }
    val isStaffOrAdmin = user?.role == UserRole.STAFF || user?.role == UserRole.ADMIN

    LaunchedEffect(Unit) { alertsVm.startListening() }

    Column(Modifier.fillMaxSize().background(SurfaceBg)) {
        BrandTopBar(
            title = user?.name ?: "Settings",
            subtitle = user?.email ?: ""
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            user?.let { u ->
                BrandCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AvatarCircle(u.name.take(2), modifier = Modifier.size(48.dp))
                        Column {
                            Text(u.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                            Text(u.email, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 2.dp))
                            Spacer(Modifier.height(4.dp))
                            StatusBadge(u.role.name.lowercase().replaceFirstChar { it.uppercase() }, BadgeType.GRAY)
                        }
                    }
                }
            }

            if (isStaffOrAdmin) {
                Text("Organization", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                BrandCard {
                    Text("Organization ID", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
                    OutlinedTextField(
                        value = orgIdInput,
                        onValueChange = { orgIdInput = it; orgIdError = null },
                        label = { Text("Organization ID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !orgIdSaving
                    )
                    if (orgIdError != null) {
                        Text(orgIdError.orEmpty(), color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton(
                        text = "Save organization ID",
                        onClick = {
                            orgIdError = null
                            orgIdSaving = true
                            authVm.updateOrgId(orgIdInput,
                                onSuccess = { orgIdSaving = false; onShowMessage("Organization ID saved.") },
                                onError = { orgIdSaving = false; orgIdError = it })
                        },
                        loading = orgIdSaving
                    )
                }
            }

            Text("Notifications", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            if (alerts.isEmpty()) {
                BrandCard {
                    Text("No notifications yet.", fontSize = 13.sp, color = TextHint, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            } else {
                alerts.forEach { alert ->
                    Card(
                        onClick = { alertsVm.markRead(alert.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = if (alert.read) CardWhite else BrandRedLight),
                        border = BorderStroke(0.5.dp, BorderColor)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(alert.title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary, modifier = Modifier.weight(1f))
                                if (!alert.read) Box(Modifier.size(8.dp).clip(CircleShape).background(BrandRed))
                            }
                            Text(alert.body, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 3.dp))
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            OutlineButton(text = "Log out", onClick = onLogout)
        }
    }
}

// ─── SHARED: EMPTY STATE ─────────────────────────────────────────────────────

@Composable
private fun EmptyState(icon: ImageVector, title: String, subtitle: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(GrayBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = TextHint, modifier = Modifier.size(28.dp))
            }
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary, textAlign = TextAlign.Center)
            Text(subtitle, fontSize = 13.sp, color = TextSecondary, textAlign = TextAlign.Center, lineHeight = 20.sp)
        }
    }
}

// ─── HELPER: border extension for single-side ────────────────────────────────

private fun Modifier.border(start: BorderStroke, shape: RoundedCornerShape): Modifier =
    this.border(start.width, start.brush, shape)

// ─── CONSENT SCREEN ──────────────────────────────────────────────────────────

@Composable
fun ConsentScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    context: String = "screening" // "signup" or "screening"
) {
    var checked by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            BrandTopBar(
                title = "Informed Consent & Data Privacy",
                subtitle = if (context == "signup") "Required before creating your account" else "Please read carefully before proceeding"
            )
        },
        containerColor = SurfaceBg
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Header icon + title
            BrandCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(BrandRedLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.HealthAndSafety,
                            contentDescription = null,
                            tint = BrandRed,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            "BloodLink Pre-Screening Tool",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                        Text(
                            "Pre-donation screening aid — not a diagnostic device",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }
            }

            // What this app does
            BrandCard {
                Text(
                    "What this app does",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Spacer(Modifier.height(10.dp))
                ConsentPoint(
                    icon = Icons.Outlined.CameraAlt,
                    text = "Uses your camera to check for possible visual signs of anemia (pallor) and jaundice using color analysis. Cyanosis and skin lesion modules are under development for future updates."
                )
                Spacer(Modifier.height(8.dp))
                ConsentPoint(
                    icon = Icons.Outlined.Assignment,
                    text = "Asks a series of health questions aligned with Philippine National Red Cross (PNRC) and DOH pre-donation screening guidelines."
                )
                Spacer(Modifier.height(8.dp))
                ConsentPoint(
                    icon = Icons.Outlined.Flag,
                    text = "Produces a preliminary screening result to help facilitators prioritize further assessment."
                )
            }

            // Important limitations
            BrandCard {
                Text(
                    "Important limitations",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Spacer(Modifier.height(10.dp))
                ConsentPoint(
                    icon = Icons.Outlined.Warning,
                    text = "This app is NOT a medical diagnosis tool. Results are indicators only and must be confirmed by a licensed medical screener on-site.",
                    highlight = true
                )
                Spacer(Modifier.height(8.dp))
                ConsentPoint(
                    icon = Icons.Outlined.Warning,
                    text = "Camera-based screening is affected by lighting conditions, device camera quality, and positioning. Results may not be accurate in poor conditions.",
                    highlight = true
                )
                Spacer(Modifier.height(8.dp))
                ConsentPoint(
                    icon = Icons.Outlined.CheckCircle,
                    text = "A 'No possible sign detected' result does not guarantee eligibility. Final eligibility is always determined by trained staff."
                )
            }

            // Data Privacy Act (RA 10173) compliance section
            BrandCard {
                Text(
                    "Data Privacy Notice",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "In compliance with the Data Privacy Act of 2012 (Republic Act No. 10173) and its Implementing Rules and Regulations, we inform you of the following:",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 17.sp
                )
                Spacer(Modifier.height(10.dp))
                ConsentPoint(
                    icon = Icons.Outlined.Person,
                    text = "Personal data collected: name, age, sex, and pre-screening results (numerical scores only). No biometric images are stored or transmitted."
                )
                Spacer(Modifier.height(8.dp))
                ConsentPoint(
                    icon = Icons.Outlined.Info,
                    text = "Purpose of collection: to assist authorized blood donation staff in pre-screening eligibility assessment. Data is used solely for this purpose."
                )
                Spacer(Modifier.height(8.dp))
                ConsentPoint(
                    icon = Icons.Outlined.Lock,
                    text = "Your data is stored securely in an encrypted database (Google Firebase) and is accessible only to authorized staff of the organizing institution."
                )
                Spacer(Modifier.height(8.dp))
                ConsentPoint(
                    icon = Icons.Outlined.Group,
                    text = "Aggregated, anonymized data may be used for academic research to improve screening accuracy. No personally identifiable information is included."
                )
                Spacer(Modifier.height(8.dp))
                ConsentPoint(
                    icon = Icons.Outlined.Edit,
                    text = "As a data subject under RA 10173, you have the right to access, correct, object to processing, and request erasure of your personal data at any time."
                )
                Spacer(Modifier.height(8.dp))
                ConsentPoint(
                    icon = Icons.Outlined.NoPhotography,
                    text = "Retention period: screening records are retained for the duration of the blood donation event and archived for a maximum of one (1) year in accordance with institutional data retention policy."
                )
            }

            // Disclaimer box
            DisclaimerBox(
                "BloodLink is a pre-screening aid developed for academic and assistive purposes. " +
                        "It is not a licensed medical device and does not replace the judgment of qualified " +
                        "medical professionals. All final eligibility decisions are made by licensed staff " +
                        "at the blood donation venue in accordance with PNRC and DOH guidelines."
            )

            // Checkbox agreement
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (checked) GreenBg else GrayBg,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { checked = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = BrandRed,
                            uncheckedColor = TextHint
                        )
                    )
                    Text(
                        "I have read and understood the Informed Consent and Data Privacy Notice above. " +
                                "I voluntarily consent to the collection and processing of my personal data as described, " +
                                "in accordance with Republic Act No. 10173 (Data Privacy Act of 2012). " +
                                "I understand that this pre-screening tool is not a licensed medical device and does not constitute a medical diagnosis.",
                        fontSize = 13.sp,
                        color = if (checked) GreenText else TextSecondary,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Buttons
            PrimaryButton(
                text = if (context == "signup") "I Consent — Create My Account" else "I Consent — Proceed to Screening",
                onClick = onAccept,
                enabled = checked
            )

            OutlinedButton(
                onClick = onDecline,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Text("Decline", color = TextSecondary, fontSize = 15.sp)
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ConsentPoint(
    icon: ImageVector,
    text: String,
    highlight: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (highlight) BrandRed else TextSecondary,
            modifier = Modifier.size(16.dp).padding(top = 1.dp)
        )
        Text(
            text,
            fontSize = 13.sp,
            color = if (highlight) TextPrimary else TextSecondary,
            lineHeight = 19.sp,
            fontWeight = if (highlight) FontWeight.Medium else FontWeight.Normal
        )
    }
}