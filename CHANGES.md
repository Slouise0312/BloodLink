# BloodLink — Full implementation change summary

End-to-end functional app: real Firestore, image pipeline (CLAHE + features), alerts, FCM, Cloud Functions, loading/error handling.

---

## Files added

| File | Purpose |
|------|---------|
| `app/src/main/java/AlertRepository.kt` | createAlert, listAlerts, listenAlerts, markAlertRead (Firestore `users/{uid}/alerts`) |
| `app/src/main/java/FcmTokenHelper.kt` | Updates `users/{uid}.fcmToken` via FCM and Firestore merge |
| `app/src/main/java/ImagePipeline.kt` | CLAHE on L channel, extractFeatures (RGB/HSV/redness), pallorInference, jaundiceIndexFromRoi |
| `functions/package.json` | Cloud Functions dependencies (firebase-admin, firebase-functions) |
| `functions/index.js` | onScreeningComplete (notify staff/admin), onFinalOutcomeUpdate (FCM to applicant) |
| `firebase.json` | Firestore rules + functions source for deploy |
| `FIRESTORE_SCHEMA.md` | Firestore collections and fields |
| `FUNCTIONAL_TEST_CHECKLIST.md` | Applicant + Staff test steps, emulator note |
| `FIREBASE_SETUP.md` | google-services.json, SHA-1, Firestore, Functions deploy, FCM, Crashlytics |
| `CHANGES.md` | This file |

---

## Files changed

| File | Changes |
|------|---------|
| `app/build.gradle.kts` | firebase-messaging-ktx, firebase-crashlytics-ktx, Crashlytics plugin |
| `build.gradle.kts` | Crashlytics plugin (apply false) |
| `app/src/main/java/EventRepository.kt` | updateEvent(map), listEvents(orgId), updateEventStatus unchanged |
| `app/src/main/java/ScreeningRepository.kt` | updateFinalOutcome(..., staffUid) and write updatedByStaffUid |
| `app/src/main/java/PhysicalTestEngine.kt` | Pallor: CLAHE → extractFeatures → pallorInference; Jaundice: jaundiceIndexFromRoi (no CLAHE on color) |
| `app/src/main/java/ViewModels.kt` | FcmTokenHelper after load/create user; ScreeningViewModel loading + joinEvent(eventId, applicantUid) no orgId; StaffApplicantsViewModel loading/error, updateFinalOutcome(applicantUid) + createAlert for applicant; AlertsViewModel (listen, markRead) |
| `app/src/main/java/Screens.kt` | Event tab: loading, joinEvent without orgId, error message; Verify: loading/error, applicantUid passed, FilterChips; Profile/Settings: AlertsViewModel, alerts list, markRead; Staff Events: orgId null message, create error handling |
| `firestore.rules` | (unchanged from previous; already had users, alerts, events, screenings) |

---

## Repository methods (real Firestore)

- **EventRepository**: createEvent, getEvent, listenEventsByOrg, updateEvent, updateEventStatus, listEvents
- **ScreeningRepository**: getOrCreateScreening, listenScreening, updatePhysicalResults, updateQuestionnaireResult, updateFinalOutcome (with staffUid), listenScreeningsByEvent, getScreening
- **AlertRepository**: createAlert, listAlerts, listenAlerts, markAlertRead
- **FcmTokenHelper**: updateTokenForUser(uid)

---

## Image pipeline

- **CLAHE**: Pure Kotlin tile-based CLAHE on luminance (L); applied to ROI before pallor features.
- **Features**: RGB mean, HSV S/V mean, redness ratio (ImagePipeline.RoiFeatures).
- **Pallor**: CLAHE(ROI) → extractFeatures → pallorInference(features) → score; threshold 0.5 → NORMAL / POSSIBLE_SIGN.
- **Jaundice**: ROI → jaundiceIndexFromRoi (HSV yellow range) → threshold → NORMAL / POSSIBLE_SIGN.
- **ROI**: ML Kit Face Detection left-eye region; fallback center crop.

---

## Notifications

- **In-app**: Alerts in `users/{uid}/alerts`; shown in Profile (Applicant) and Settings (Staff); mark read.
- **FCM**: Token saved to `users/{uid}.fcmToken` on login/role creation.
- **Cloud Functions**: onScreeningComplete → alerts + FCM to staff/admin; onFinalOutcomeUpdate → FCM to applicant.

---

## Security rules (summary)

- users: own doc read/write.
- users/{uid}/alerts: own alerts read/write.
- events: read if user.orgId == event.orgId; create/update only ADMIN.
- screenings: create if applicantUid == auth.uid; read/update if applicant or (staff/admin and orgId match).

---

## How to run

1. Add `google-services.json` to `app/`.
2. Build: `./gradlew assembleDebug` (or Android Studio).
3. Deploy Firestore rules: `firebase deploy --only firestore:rules`.
4. Deploy Functions: `cd functions && npm install && cd .. && firebase deploy --only functions`.
5. Use FUNCTIONAL_TEST_CHECKLIST.md for end-to-end tests.
