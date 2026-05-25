# BloodLink Thesis – End Goal and Tools

This document summarizes the thesis end goal and all tools used so the app is fully functional from Android Studio to Firebase and ML.

---

## 1. Thesis end goal

**BloodLink** is a **pre–blood donor screening** Android app for NGOs/hospitals at blood drives. The deliverable is a **single, fully functional app** with real backend and ML-based screening aids.

**Applicant flow:** Sign in (email/password + Google) → choose role → join event (QR or event code) → physical screening (pallor + jaundice via camera) → eligibility questionnaire → result and screening QR → alerts when staff set outcome.

**Staff/Admin flow:** Sign in → events (create/show event QR) → applicants per event → verify by scanning applicant QR or ID → set final outcome → notifications to applicant; analytics (counts, deferral reasons).

**Backend:** Firestore for all data; FCM and Cloud Functions for push and server-side triggers. **No image storage**; only extracted features and results in Firestore. **Safety wording:** “possible sign” only, not diagnosis.

---

## 2. Tools (full list)

| Layer | Tool | Purpose | In project |
|-------|------|---------|------------|
| **Build & run** | Android Studio, Gradle | IDE, build, run | Yes |
| **UI** | Kotlin, Jetpack Compose, Navigation Compose | Screens, routes | Yes (App.kt, Screens.kt) |
| **Auth** | Firebase Auth | Email/password + Google Sign-In | Yes (ViewModels.kt, Screens.kt) |
| **Database** | Cloud Firestore | users, events, screenings, alerts | Yes (EventRepository, ScreeningRepository, AlertRepository) |
| **Push** | FCM | Push notifications | Yes (BloodLinkMessagingService, FcmTokenHelper) |
| **Server logic** | Cloud Functions | Triggers (screening complete, final outcome) | Yes (functions/index.js) |
| **QR generator** | ZXing (QRCodeWriter) | Encode event ID / screening ID | Yes (QrHelper.kt) |
| **QR scanner** | ML Kit Barcode + CameraX | Scan event/screening QR | Yes (QrScanner.kt, QrHelper.decodeFromBitmap) |
| **Camera** | CameraX, TakePicturePreview | Capture for screening and QR | Yes (app/build.gradle.kts, Screens.kt) |
| **ML / AI** | ML Kit Face Detection | Face/eye ROI for conjunctiva/sclera | Yes (MlKitFaceRoi.kt) |
| **Image pipeline** | Pure Kotlin | CLAHE on L, features, pallor heuristic, jaundice rule | Yes (ImagePipeline.kt, PhysicalTestEngine.kt) |

**Optional (not required for fully functional):** OpenCV (e.g. CLAHE comparison); trained ML model for pallor (e.g. TFLite or small classifier in assets).

---

## 3. ML/AI implementation

- **Face ROI:** ML Kit Face Detection → left-eye region → crop (or center fallback).
- **Preprocessing:** CLAHE on luminance (L) in Kotlin (ImagePipeline.kt); no OpenCV.
- **Features:** RGB mean, HSV S/V mean, redness ratio (ImagePipeline.extractFeatures).
- **Pallor:** Heuristic from features (low R, low redness → score; threshold 0.5); pluggable for a future trained model.
- **Jaundice sign:** Rule-based yellow index from HSV (hue 35–75°, S, V); threshold 0.35.
- **Persistence:** Only results (e.g. pallorResult, pallorScore, jaundiceResult, jaundiceIndex) are stored in Firestore; no images.

---

## 4. Fully functional checklist

| Item | Action |
|------|--------|
| Build & run | Use Android Studio; build and run on device/emulator. |
| Auth | Configure Firebase Auth (Email/Password + Google); add SHA-1/SHA-256 for Google Sign-In. |
| Database & rules | Deploy `firestore.rules`; no other DB. |
| Push & Functions | Deploy `functions/index.js`; no other server. |
| QR | ZXing + ML Kit + QrHelper; no extra libraries. |
| Camera | CameraX + TakePicturePreview; already in app. |
| ML/AI | ML Kit Face + Kotlin ImagePipeline; OpenCV and trained model optional. |
| Notifications | In-app alerts (Firestore) + FCM; FCM token written to `users/{uid}` by app. |

See **FIREBASE_SETUP_AND_TEST.md** for Firebase setup steps and test flows.
