# BloodLink – Firebase Setup & Test Checklist

## Firebase setup checklist

### 1. Project and `google-services.json`
- Create a Firebase project at [Firebase Console](https://console.firebase.google.com/) (or use an existing one).
- Add an Android app with package name **`com.company.bloodlink`** (matches `applicationId` in `app/build.gradle.kts`).
- Download **`google-services.json`** and place it in **`app/`** (same folder as `app/build.gradle.kts`).
- Sync Gradle; the plugin will generate `default_web_client_id` for Google Sign-In.

### 2. Firebase Console – enable services
- **Authentication** → Sign-in method: enable **Email/Password** and **Google**.
- **Firestore Database** → Create database (start in test mode if needed; then deploy rules from the repo).
- **Cloud Messaging**: FCM is available by default for the app.

### 3. Google Sign-In (SHA-1 / SHA-256)
- In Android Studio: **Gradle** → **app** → **Tasks** → **android** → **signingReport** (or run `./gradlew signingReport`).
- Copy **SHA-1** and **SHA-256**.
- In Firebase Console → Project settings → Your apps → Android app → **Add fingerprint** → paste SHA-1 and SHA-256.
- In **Authentication** → **Sign-in method** → **Google** → ensure **Web SDK configuration** has a Web client ID (needed for Android ID token). If you added the Android app and fingerprints, the Web client ID is usually already set.

### 4. Firestore security rules
- Deploy the rules from this repo:
  ```bash
  firebase deploy --only firestore:rules
  ```
- Or paste the contents of **`firestore.rules`** into Firebase Console → Firestore → Rules and publish.

### 5. Cloud Functions (notifications)
- Install Firebase CLI and log in: `firebase login`.
- In project root: `firebase use <your-project-id>`.
- Install and deploy:
  ```bash
  cd functions
  npm install
  npm run deploy
  ```
  Or: `firebase deploy --only functions`.
- Functions deployed:
  - **onScreeningComplete**: when a screening’s `overallStatus` becomes complete → in-app alerts + FCM to staff/admin of that org.
  - **onFinalOutcomeUpdate**: when `finalOutcome` is set → in-app alert for applicant + FCM to applicant.

### 6. FCM token and user doc
- The app writes **FCM token** to **`users/{uid}`** (merge) via `FcmTokenHelper` after login/role selection.
- Ensure **`users/{uid}`** has fields: `role`, `orgId` (for STAFF/ADMIN), and optionally `fcmToken` (updated by the app).

### 7. Firestore offline persistence
- The app **enables Firestore persistence** in **MainActivity** (before any Firestore access), so data is cached locally and the app works offline with previously loaded data; changes sync when back online.

---

## Deploy checklist (thesis-ready)

Before treating the app as ready to deploy and use for the thesis: (1) `google-services.json` in `app/`, (2) Auth (Email/Password + Google) and SHA-1/SHA-256 configured, (3) Firestore created and **rules deployed** (`firebase deploy --only firestore:rules`), (4) **Cloud Functions deployed** (`cd functions && npm install && npm run deploy`), (5) Test applicant flow (join event, screening, questionnaire, result, alerts) and staff flow (events, applicants, verify, settings orgId, analytics).

---

## How to test Applicant flow

1. **Auth**
   - Install and run the app.
   - Sign up with email/password or tap **Sign in with Google** (if configured).
   - On first login, choose role **Applicant** and continue.

2. **Join event (Step 1 of 4)**
   - Open **Event** tab.
   - Either **Scan event QR** (camera) or **Enter Event ID** (e.g. an event id from Staff/Admin) and tap **Join event**.
   - Confirm success message and that the current event card appears.

3. **Physical screening (Step 2 of 4)**
   - Open **Screening** tab.
   - Tap **Pallor** or **Jaundice** → **Take photo** → allow camera → take picture.
   - Check result (NORMAL / POSSIBLE_SIGN); tap **Save result**.
   - Confirm “Result saved.” and that the step shows as done.

4. **Questionnaire (Step 3 of 4)**
   - Open **Questions** tab.
   - Answer all 10 questions (Yes/No).
   - After the last question, see outcome (ELIGIBLE / TEMP_DEFERRED / NOT_ELIGIBLE) and **Save & continue**.
   - For TEMP_DEFERRED, confirm deferral reason and “Eligible again after” date (7 days later).

5. **Result (Step 4 of 4)**
   - Open **Result** tab.
   - If no step is done: “Result unlocks after you complete at least one step.”
   - After at least one of Physical or Questionnaire is done: result card, overall status, deferral (if any), and **Screening ID QR**.
   - Confirm disclaimer: “Pre-screening only… Not a medical diagnosis.”

6. **Alerts**
   - Open **Profile** tab → Alerts.
   - After staff sets your **finalOutcome** (in Staff app), an alert should appear (and optionally a push notification).

---

## How to test Staff flow

1. **Auth and role**
   - Sign in as a different user (or create one).
   - Choose **Staff** (or **Admin**), enter **Organization ID** (e.g. `org1`) and continue.

2. **Events**
   - **Events** tab: list of events for your org (real-time).
   - **Admin/Staff**: tap **Create event** → Title, Location → Create. Confirm “Event created.” and that **Event QR** dialog appears (event ID as QR).
   - Tap an event to **select** it; confirm Snackbar “Event … selected.”
   - **Admin**: **Show event QR** on a card → dialog with QR; **Edit** / **Close event** / **Delete** (with confirmation).

3. **Applicants**
   - Select an event from Events tab, then open **Applicants** tab.
   - Confirm list of screenings for that event; use filter chips (All, Incomplete, Eligible, etc.).
   - Tap **Set outcome** on a screening → choose outcome (e.g. ACCEPTED_ONSITE), add notes → Save.
   - Confirm “Outcome saved.” and (if Cloud Function deployed) applicant gets in-app alert + FCM.

4. **Verify**
   - Open **Verify** tab.
   - Enter **Screening ID** (or **Scan QR** / **Upload image** of applicant’s Result QR).
   - **Load screening** → confirm applicant details and set **finalOutcome** + notes → **Save outcome**.

5. **Analytics**
   - With an event selected, open **Analytics** tab.
   - Confirm counts (Eligible, Temp deferred, Not eligible, Incomplete) and top deferral reasons.

6. **Settings**
   - **Alerts**: list and tap to mark read.
   - **Staff/Admin**: **Organization ID** field and **Save organization ID** to set or change orgId (so you can see and create events).
   - **Log out**.

---

## Deploy commands (quick reference)

```bash
# Firestore rules only
firebase deploy --only firestore:rules

# Cloud Functions only
cd functions && npm install && npm run deploy

# Both
firebase deploy --only firestore:rules,functions
```

---

## Optional: Event announcements (P1)

- To notify all applicants of an event when admin “posts” an announcement:
  - Add a field or subcollection to **events** (e.g. `announcements` or `lastAnnouncementAt`).
  - Add a Cloud Function trigger that sends FCM to all users who have a screening in that event (query **screenings** by `eventId`, then **users** for each `applicantUid` and send to their `fcmToken`).
  - Create in-app alerts for each applicant in **users/{uid}/alerts**.

This is not implemented in the current P0 MVP; the above is a reference for P1.
