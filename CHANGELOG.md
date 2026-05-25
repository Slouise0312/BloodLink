# Changelog

All notable changes to BloodLink are documented here.

---

## [v1.0.0] — 2026-05-25 (Pre-Defense Build)

### Added
- **Image quality gate** — 4-check system (resolution, brightness, blur, contrast) that validates photos before ML inference runs. Rejected images show actionable warning messages.
- **Runtime camera permission** — App now requests CAMERA permission at click time, fixing SecurityException on Android 13+.
- **Positioning guide** — Blue info banner above Take/Upload buttons tells users exactly how to position their eye for each test module (pallor, jaundice, cyanosis).
- **Session persistence** — Event ID saved to SharedPreferences. App reopens into the active event after kill/restart — no QR re-scan needed.
- **Data Privacy Act (RA 10173) consent** — Full NPC-compliant consent screen with 6 disclosure points. Shown at sign-up AND event join. Checkbox consent references RA 10173 by name.
- **Consent gate at sign-up** — Users must read and accept the Data Privacy Notice before their account is created.

### Changed
- **Pallor threshold** raised from 0.50 to 0.70 to reduce false positives caused by domain mismatch between clinical training images and real-world phone photos.
- **Result display** simplified to show only "Score: X%" — removed technical model/ROI metadata.

### Fixed
- Camera permission crash (SecurityException) on physical screening tab.
- `currentPhotoFile` closure capture bug causing "Take photo" button to silently fail.

---

## [v0.9.0] — 2026-05-16

### Added
- Pallor (anemia) detection module with TFLite MobileNetV2 model.
- Jaundice detection module (heuristic mode — HSV color analysis).
- Cyanosis and skin lesion modules (placeholder — heuristic only).
- ML Kit Face Detection for automatic ROI extraction (lower eyelid, sclera).
- CLAHE preprocessing in ImagePipeline for contrast normalization.
- Health questionnaire with rule-based eligibility engine.
- Firebase Authentication (email/password + Google Sign-In).
- Event creation, QR code generation, and applicant management for staff.
- Real-time screening status updates via Firestore listeners.
- Firebase Cloud Messaging for event notifications.

---

## [v0.1.0] — 2026-04-XX (Initial Prototype)

### Added
- Basic app scaffold with Jetpack Compose navigation.
- Firebase project setup and Firestore data model.
- Staff and applicant role separation.
