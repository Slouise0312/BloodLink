# BloodLink — What You Need to Do Outside the IDE

**Everything that can be done in code is already done.** This document lists only the steps you must perform outside Cursor/Android Studio (Firebase Console, terminal commands, etc.) to make the app **100% functional**.

**Note:** The app does **not** use OpenCV (image processing is pure Kotlin/ML Kit). No OpenCV setup is required.

---

## Already Done in Code (No Action Needed)

| Item | Status |
|------|--------|
| Firebase Auth, Firestore, FCM dependencies | ✅ Done |
| google-services plugin | ✅ Done |
| FCM token helper (saves token to Firestore) | ✅ Done |
| FirebaseMessagingService (token refresh + foreground notifications) | ✅ Done |
| Firestore security rules (in `firestore.rules`) | ✅ Done (you deploy them) |
| Cloud Functions code (in `functions/`) | ✅ Done (you deploy them) |
| CAMERA, READ_MEDIA_IMAGES, POST_NOTIFICATIONS | ✅ Done |

---

## 1. Firebase Console (console.firebase.google.com)

### 1.1 Create or select project
1. Go to https://console.firebase.google.com/
2. Sign in with Google
3. Click **Create a project** (or select existing)
4. Enter project name (e.g. `BloodLink`)
5. Disable Google Analytics if you don't need it
6. Click **Create project**

### 1.2 Enable Authentication
1. Left sidebar → **Build** → **Authentication**
2. Click **Get started**
3. **Sign-in method** tab → **Email/Password** → Enable → **Save**

### 1.3 Enable Firestore
1. Left sidebar → **Build** → **Firestore Database**
2. **Create database** → **Start in test mode** → Choose location → **Enable**

### 1.4 Enable Cloud Messaging
- FCM is enabled automatically with Firestore. No extra steps.

---

## 2. Add Android App & google-services.json

### 2.1 Add Android app
1. Gear icon → **Project settings**
2. **Your apps** → **Add app** → Android icon
3. Package name: `com.company.bloodlink` (must match `applicationId` in `app/build.gradle.kts`)
4. App nickname: e.g. `BloodLink Android`
5. **Register app**

### 2.2 Download and place google-services.json
1. Click **Download google-services.json**
2. Save the file
3. Copy it to: `C:\Users\Admin\AndroidStudioProjects\BloodLink\app\`
4. Replace existing file if prompted
5. Click **Next** → **Next** → **Continue to console**

### 2.3 Add SHA-1 (only if using Google Sign-In)
1. **Project settings** → Your Android app → **Add fingerprint**
2. Get SHA-1: in project folder run `.\gradlew.bat signingReport`
3. Copy the SHA-1 under `Variant: debug`
4. Paste and **Save**

---

## 3. Firebase CLI & Deploy

### 3.1 Install Firebase CLI
1. Install Node.js from https://nodejs.org/ (LTS)
2. Open terminal: `npm install -g firebase-tools`

### 3.2 Log in
- Run: `firebase login` → sign in with Google in the browser

### 3.3 Connect project (if not done)
- Run: `firebase use --add` → select your project

### 3.4 Deploy Firestore rules
```bash
cd C:\Users\Admin\AndroidStudioProjects\BloodLink
firebase deploy --only firestore:rules
```

### 3.5 Deploy Cloud Functions
```bash
cd C:\Users\Admin\AndroidStudioProjects\BloodLink\functions
npm install
cd ..
firebase deploy --only functions
```

---

## 4. Firestore Index (when prompted)

- When the app first queries alerts, Firestore may show an error with a link to create an index.
- Click that link, or: **Firestore** → **Indexes** → **Create index**
- Collection: `users`, subcollection: `alerts`, field: `createdAt` descending

---

## 5. Test Data (optional)

### Create organization
- **Firestore** → **Start collection** → ID: `organizations`
- Document ID: `org1`
- Fields: `name` (string), `address` (string), `contact` (string), `createdAt` (number)

### Set role & orgId for staff/admin
- After signing up, go to **Firestore** → **users** → your user document
- Add/edit: `role` = `ADMIN` or `STAFF`, `orgId` = `org1`

---

## Quick Checklist

| # | Task | Where |
|---|------|-------|
| 1 | Create Firebase project | Firebase Console |
| 2 | Enable Auth (Email/Password) | Firebase Console |
| 3 | Enable Firestore | Firebase Console |
| 4 | Add Android app | Firebase Console |
| 5 | Download google-services.json → put in `app/` | File system |
| 6 | `firebase deploy --only firestore:rules` | Terminal |
| 7 | `cd functions && npm install && cd .. && firebase deploy --only functions` | Terminal |
| 8 | Create Firestore index (when prompted) | Firebase Console |
| 9 | Set `role` and `orgId` on staff/admin users | Firestore |

---

## Summary

**In code:** FCM service, Firebase dependencies, and all app logic are already configured.

**You do:** Firebase Console setup, add Android app, download `google-services.json`, deploy rules and functions, create index when needed, set user roles in Firestore. No OpenCV or Crashlytics setup required.
