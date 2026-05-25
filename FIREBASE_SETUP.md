# BloodLink — Firebase setup

## 1. Firebase project

1. Go to [Firebase Console](https://console.firebase.google.com/) and create or select a project.
2. Enable **Authentication** (Email/Password; optionally Google Sign-In).
3. Enable **Cloud Firestore** (start in test mode for dev; then deploy rules).
4. Enable **Cloud Messaging** (FCM) for push notifications.
5. (Optional) Enable **Crashlytics** in the project.

## 2. Android app in Firebase

1. In Project settings, add an Android app with package name: `com.company.bloodlink` (or the one in `app/build.gradle.kts`).
2. Download **google-services.json** and place it in `app/` (replace existing if any).
3. For **Google Sign-In** (if used): add your debug/release SHA-1 in the Android app settings.

## 3. Firestore

1. Deploy rules:
   ```bash
   firebase deploy --only firestore:rules
   ```
   (Ensure `firestore.rules` is configured in `firebase.json`.)

2. Create an index if needed:
   - When the app first runs a query on `users/{uid}/alerts` with `orderBy("createdAt", "desc")`, Firestore may prompt you to create an index, or create it manually in Firestore Console → Indexes: collection `users`, subcollection `alerts`, field `createdAt` descending.

## 4. Cloud Functions

1. Install Firebase CLI: `npm install -g firebase-tools`
2. Log in: `firebase login`
3. In project root, if not already initialized:
   ```bash
   firebase init functions
   ```
   Select existing project, use existing `functions` folder if you have the code there, Node 18.

4. Install dependencies and deploy:
   ```bash
   cd functions
   npm install
   cd ..
   firebase deploy --only functions
   ```

5. Functions deployed:
   - **onScreeningComplete**: on screening update when `overallStatus` becomes complete → creates in-app alerts for staff/admin of the org and sends FCM to their tokens.
   - **onFinalOutcomeUpdate**: on screening update when `finalOutcome` is set → sends FCM to the applicant.

## 5. FCM (optional for push)

- The app saves the FCM token to `users/{uid}.fcmToken` on login / after role creation.
- For push to work: device must have Google Play Services; user must grant notifications.
- Cloud Functions use `fcmToken` to send messages when screening completes or final outcome is updated.

## 6. Crashlytics (optional)

- The app already includes the Crashlytics dependency and plugin.
- In Firebase Console, enable Crashlytics for the project if not already.
- Build and run the app once so the first crash report can register the app.

## 7. Security rules summary

- **users**: user can read/write own document.
- **users/{uid}/alerts**: user can read/write own alerts.
- **events**: read for auth users whose `orgId` matches event’s `orgId`; create/update only for ADMIN.
- **screenings**: create only with `applicantUid == auth.uid`; read/update if applicant or staff/admin of same org.
- **organizations**: read only if user’s `orgId` matches.

Ensure STAFF and ADMIN accounts have `orgId` set (e.g. during role selection or in Firestore) so they can see events and screenings.
