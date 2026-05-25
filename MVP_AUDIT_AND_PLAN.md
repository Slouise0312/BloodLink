# BloodLink MVP – Audit & Fix Plan

## 1. Audit summary: what exists vs missing

### Screens & routes (existing)
| Route | File | Status |
|-------|------|--------|
| Auth | `App.kt`, `Screens.kt` | Email/password only; **Google Sign-In missing** |
| Role selection | `Screens.kt` | Working; org ID free text |
| Applicant shell (5 tabs) | `Screens.kt` | Event, Screening, Questionnaire, Result, Profile – functional |
| Staff shell (5 tabs) | `Screens.kt` | Events, Applicants, Verify, Analytics, Settings – functional |
| QR scanner | `QrScanner.kt` | Camera + upload image; used for event join & staff verify |

### ViewModels (existing)
| ViewModel | File | Purpose |
|-----------|------|--------|
| AuthViewModel | `ViewModels.kt` | Login, signUp, createUserWithRole, loadUserProfile; FCM token after role set |
| ScreeningViewModel | `ViewModels.kt` | currentEvent, currentScreening, joinEvent, refreshScreening |
| EventViewModel | `ViewModels.kt` | events, selectedEvent, create/update/delete event |
| StaffApplicantsViewModel | `ViewModels.kt` | screenings by event, filter, updateFinalOutcome (creates applicant alert) |
| AlertsViewModel | `ViewModels.kt` | listen alerts, markRead |

### Repositories (existing)
| Repository | File | Notes |
|------------|------|--------|
| EventRepository | `EventRepository.kt` | create, get, listen by org, update, delete; event has qrPayload = id |
| ScreeningRepository | `ScreeningRepository.kt` | getOrCreate, listen, updatePhysicalResults, updateQuestionnaireResult, updateFinalOutcome |
| AlertRepository | `AlertRepository.kt` | create, listen, markRead. **Issue:** staff writing to `users/{applicantUid}/alerts` is denied by rules |
| FcmTokenHelper | `FcmTokenHelper.kt` | Writes fcmToken to users/{uid} (merge) |

### Physical screening (existing)
| Component | File | Notes |
|-----------|------|--------|
| PallorModule / JaundiceModule | `PhysicalTestEngine.kt` | ML Kit face ROI, heuristic pallor, jaundice threshold; **no OpenCV** (pure Kotlin in ImagePipeline) |
| MlKitFaceRoi | `MlKitFaceRoi.kt` | Face detection, left-eye ROI |
| ImagePipeline | `ImagePipeline.kt` | CLAHE on L, feature extraction, pallor/jaundice inference (no OpenCV dependency) |

### Questionnaire (existing)
| Item | File | Notes |
|------|------|--------|
| Questions + rule engine | `QuestionnaireRuleEngine.kt` | 10 questions, evaluate → ELIGIBLE/TEMP_DEFERRED/NOT_ELIGIBLE; **deferralUntil always null** |

### Notifications (existing)
| Item | File | Notes |
|------|------|--------|
| FCM service | `BloodLinkMessagingService.kt` | onNewToken → update token; onMessageReceived → local notification |
| Cloud Functions | `functions/index.js` | onScreeningComplete (alert staff), onFinalOutcomeUpdate (FCM to applicant only; **no in-app alert for applicant**) |

### Firestore rules (existing)
- **users**: read/write own. **Alerts subcollection:** read/write own only → **staff cannot create alerts for applicants** (PERMISSION_DENIED).

### Placeholder / to fix
- **MainActivity**: `testReadUser()` reads `users/test123` on launch – remove for production.
- **Alerts**: Either allow staff to create `users/{uid}/alerts` in rules, or create applicant in-app alert in Cloud Function (recommended: do both – function for reliability, rules relaxed so app works without Functions).
- **Result tab**: Should be locked until at least one module (physical or questionnaire) is done; stronger disclaimers.
- **Applicant flow**: No clear stepper; no “Save & continue later” hint; disclaimers could be more prominent.
- **Admin event**: Create event stores qrPayload = eventId but **event QR is not shown** after create or on event card.
- **Data model**: Alerts spec says `alerts/{uid}/items/{alertId}`; app uses `users/{uid}/alerts/{alertId}` – kept as-is (simpler).

---

## 2. Task list

### P0 (must-have MVP)
| # | Task | Deliverable |
|---|------|-------------|
| P0-1 | Firestore rules: allow STAFF/ADMIN to create alerts for any user (in-app alert when staff sets outcome) | `firestore.rules` |
| P0-2 | Cloud Function: on finalOutcome update, create in-app alert for applicant | `functions/index.js` |
| P0-3 | Remove test/debug Firestore read from MainActivity | `MainActivity.kt` |
| P0-4 | Questionnaire: set deferralUntil (e.g. +7 days) for temp deferrals | `QuestionnaireRuleEngine.kt` |
| P0-5 | Result tab: show locked state until at least one module complete; stronger disclaimers | `Screens.kt` |
| P0-6 | Applicant screening: stepper/progression indicator + safety disclaimers | `Screens.kt` |
| P0-7 | Admin: show event QR after create and on event card (eventId = QR payload) | `Screens.kt` |
| P0-8 | Google Sign-In (Auth + UI) | `build.gradle.kts`, `AuthViewModel`, `Screens.kt`, Firebase config |

### P1 (nice-to-have)
| # | Task |
|---|------|
| P1-1 | Questionnaire: persist “Save & continue later” draft to Firestore (e.g. questionnaireDraft map) |
| P1-2 | OpenCV CLAHE in native module (optional; current pure-Kotlin pipeline is acceptable) |
| P1-3 | Analytics: simple on-screen charts (e.g. bar for status counts) |
| P1-4 | Event announcement: admin posts to event → Cloud Function notifies all applicants in that event |
| P1-5 | Crashlytics integration |
| P1-6 | Offline/syncing indicator (Firestore persistence already enabled) |

---

## 3. Data model alignment

- **users/{uid}**: uid, role, orgId, name, email, phone, **fcmToken**, createdAt, updatedAt ✓ (FcmTokenHelper writes fcmToken)
- **events/{eventId}**: orgId, title, dateTime, location, status, createdByUid, qrPayload (= eventId) ✓
- **screenings/{screeningId}**: eventId, orgId, applicantUid, pallorResult, pallorScore, jaundiceResult, jaundiceIndex, questionnaireStatus, deferralReason, **deferralUntil**, overallStatus, finalOutcome, staffNotes, createdAt, updatedAt ✓
- **users/{uid}/alerts/{alertId}**: type, title, body, relatedEventId, relatedScreeningId, createdAt, read ✓

---

## 4. Files to add/modify (P0 implementation)

| File | Action |
|------|--------|
| `MVP_AUDIT_AND_PLAN.md` | Created (this doc) |
| `firestore.rules` | Modify: alerts create by staff |
| `functions/index.js` | Modify: create applicant in-app alert on finalOutcome |
| `app/.../MainActivity.kt` | Modify: remove testReadUser |
| `QuestionnaireRuleEngine.kt` | Modify: deferralUntil for temp deferrals |
| `Screens.kt` | Modify: Result lock, stepper, disclaimers, event QR |
| `ViewModels.kt` | Modify: Google Sign-In (if needed) |
| `app/build.gradle.kts` | Modify: Google auth dependency |
| `FIREBASE_SETUP_AND_TEST.md` | Create: setup + test checklist |

---

## 5. P0 implementation summary (done)

| P0 item | File(s) modified |
|--------|------------------|
| P0-1 Alerts rules | `firestore.rules`: STAFF/ADMIN can create `users/{userId}/alerts` |
| P0-2 Applicant in-app alert on outcome | `functions/index.js`: onFinalOutcomeUpdate creates alert doc + FCM |
| P0-3 Remove test read | `app/.../MainActivity.kt`: removed testReadUser() |
| P0-4 deferralUntil | `QuestionnaireRuleEngine.kt`: +7 days for temp deferrals |
| P0-5 Result lock + disclaimers | `Screens.kt`: ApplicantResultTab locked until hasPhysical \|\| hasQuestionnaire; deferralUntil shown; disclaimer card |
| P0-6 Stepper + disclaimers | `Screens.kt`: Step 1–4 labels; Physical/Questionnaire disclaimers; “Save & continue later” hint |
| P0-7 Event QR | `Screens.kt`: Show event QR after create; “Show event QR” on each event card (Staff/Admin) |
| P0-8 Google Sign-In | `app/build.gradle.kts`: play-services-auth; `ViewModels.kt`: signInWithGoogle(idToken); `Screens.kt`: AuthScreen launcher + “Sign in with Google” button |

**Test checklist and Firebase setup:** `FIREBASE_SETUP_AND_TEST.md`.
