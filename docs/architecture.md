# BloodLink — System Architecture

## Overview

BloodLink follows a 5-layer architecture designed for separation of concerns, testability, and on-device ML inference without network dependency for screening.

---

## Layer Diagram

```
┌──────────────────────────────────────────────────────────────┐
│                       UI LAYER                               │
│  Screens.kt (~2,500 lines)                                   │
│  Jetpack Compose + Material 3                                │
│  Screens: Auth, Consent, Event Join, Screening, Results,     │
│           Staff Dashboard, Profile                           │
├──────────────────────────────────────────────────────────────┤
│                     STATE LAYER                              │
│  ViewModels.kt                                               │
│  AuthViewModel · ScreeningViewModel · EventViewModel         │
│  StaffApplicantsViewModel · AlertsViewModel                  │
│  All use StateFlow for reactive UI binding                   │
├──────────────────────────────────────────────────────────────┤
│                    DOMAIN LAYER                              │
│  ScreeningRepository.kt · EventRepository.kt                │
│  PhysicalTestEngine.kt · QuestionnaireRuleEngine             │
│  Business logic isolated from UI and data sources            │
├──────────────────────────────────────────────────────────────┤
│                      ML LAYER                                │
│  ImagePipeline.kt — TFLite inference + CLAHE + quality gate  │
│  MlKitFaceRoi.kt — Face detection + anatomical ROI crop      │
│  PhysicalTestEngine.kt — Test modules (pallor, jaundice)     │
│  On-device inference — no network required for screening     │
├──────────────────────────────────────────────────────────────┤
│                     DATA LAYER                               │
│  Firebase Authentication (email + Google Sign-In)            │
│  Cloud Firestore (events, screenings, users)                 │
│  Firebase Cloud Messaging (push notifications)               │
│  SharedPreferences (session persistence)                     │
└──────────────────────────────────────────────────────────────┘
```

---

## ML Inference Pipeline

```
Photo captured
      │
      ▼
┌─────────────┐
│ Quality Gate │ ── FAIL → Show warning, block inference
│ (4 checks)  │
└──────┬──────┘
       │ PASS
       ▼
┌──────────────┐
│ ML Kit Face  │ ── No face → Fall back to full image
│ Detection    │
└──────┬───────┘
       │ Face found
       ▼
┌──────────────┐
│ ROI Crop     │  Pallor → lower eyelid region
│              │  Jaundice → sclera region
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ Preprocessing│  Resize to 224×224, CLAHE contrast
│              │  normalization, RGB normalize
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ TFLite Model │  MobileNetV2 transfer learning
│ Inference    │  Output: score [0.0 – 1.0]
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ Threshold    │  Pallor: > 0.70 → Possible sign
│ Decision     │  Jaundice: > 0.35 → Possible sign
└──────────────┘
```

---

## Data Model (Firestore)

### Collection: `events`
| Field | Type | Description |
|---|---|---|
| id | String | Auto-generated document ID |
| title | String | Event name |
| orgId | String | Organization ID |
| createdBy | String | Staff UID |
| date | Timestamp | Event date |
| status | String | ACTIVE / CLOSED |

### Collection: `screenings`
| Field | Type | Description |
|---|---|---|
| id | String | Auto-generated document ID |
| eventId | String | Reference to event |
| applicantUid | String | Donor's Firebase UID |
| pallorResult | String | NOT_DONE / NORMAL / POSSIBLE_SIGN |
| pallorScore | Float? | Model confidence score |
| jaundiceResult | String | NOT_DONE / NORMAL / POSSIBLE_SIGN |
| jaundiceIndex | Float? | Model confidence score |
| questionnaireStatus | String | NOT_DONE / ELIGIBLE / TEMP_DEFERRED / NOT_ELIGIBLE |
| overallStatus | String | Computed from all results |
| finalOutcome | String | Staff's final decision |

---

## Key Design Decisions

1. **On-device inference** — All ML runs locally via TFLite. No images are uploaded to any server. This ensures privacy compliance and works without internet during screening.

2. **Quality gate before inference** — Prevents garbage-in-garbage-out by rejecting blurry, dark, or featureless images before the model runs.

3. **ROI extraction via ML Kit** — Rather than running the model on the full face, we crop the specific anatomical region (eyelid for pallor, sclera for jaundice). This matches the training data distribution and improves accuracy.

4. **Session persistence** — SharedPreferences stores the active event ID so donors don't lose progress if the app is killed or the phone dies mid-screening.

5. **RA 10173 compliance** — Consent is gated at both sign-up and event join. No biometric images are stored. Only numerical scores are persisted.
