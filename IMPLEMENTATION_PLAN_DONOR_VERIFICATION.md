# Implementation Plan: Donor Verification & Document Upload

This plan covers three features in order: (1) donor approval status with FakeRepository, (2) donor “Pending Approval” screen, (3) document upload with Firebase Storage.

---

## Phase 1: Donor Approval Status (Local / FakeRepository First)

### 1.1 Model & enum

| Step | File | Action |
|------|------|--------|
| 1.1.1 | `Models.kt` | Add `enum class VerificationStatus { PENDING, APPROVED, REJECTED }`. |
| 1.1.2 | `Models.kt` | Update `Donor`: add `verificationStatus: VerificationStatus = VerificationStatus.PENDING` and `documents: List<String> = emptyList()` (URLs; optional for Phase 1). |

### 1.2 Repository layer

| Step | File | Action |
|------|------|--------|
| 1.2.1 | `HospitalRepository.kt` | Add `fun setDonorVerificationStatus(donorId: String, status: VerificationStatus)`. |
| 1.2.2 | `FakeRepository.kt` | Implement `setDonorVerificationStatus`: update the donor in `_donors` and move between “pending” and “approved” conceptually (same list, filter by status in UI). |
| 1.2.3 | `FakeRepository.kt` | In `addDonor`, set new donor with `verificationStatus = VerificationStatus.PENDING` (and `documents = emptyList()`). |
| 1.2.4 | `FakeRepository.kt` | Ensure existing seed donors have a mix: e.g. some `APPROVED`, some `PENDING` for testing. |
| 1.2.5 | `FirebaseRepository.kt` | Implement `setDonorVerificationStatus`: update Firestore document `hospitals/{uid}/donors/{donorId}` field `verificationStatus`. |
| 1.2.6 | `FirebaseRepository.kt` | Update Donor serialization: `toMap`/`parseDonor` include `verificationStatus` (and `documents` when added). |

### 1.3 ViewModel

| Step | File | Action |
|------|------|--------|
| 1.3.1 | `ViewModels.kt` | In `HospitalViewModel`, add `fun setDonorVerificationStatus(donorId: String, status: VerificationStatus)` that calls `repo.setDonorVerificationStatus`. |

### 1.4 Hospital Donors screen

| Step | File | Action |
|------|------|--------|
| 1.4.1 | `Screens.kt` | In `HospitalDonorsScreen`: split donors into `pendingDonors = donors.filter { it.verificationStatus == PENDING }` and `approvedDonors = donors.filter { it.verificationStatus == APPROVED }`. Optionally show `rejectedDonors` if desired. |
| 1.4.2 | `Screens.kt` | Add a **“Pending”** section: title “Pending approval”, list `pendingDonors` with `DonorCard`-like UI; each row has **Approve** and **Reject** buttons that call `vm.setDonorVerificationStatus(donor.id, APPROVED)` or `REJECTED`. |
| 1.4.3 | `Screens.kt` | Add an **“Approved”** section: title “Approved donors”, list `approvedDonors` with existing `DonorCard` (availability toggle). |
| 1.4.4 | `Screens.kt` | Update stats (e.g. “Total donors” / “Available now”) to count only **APPROVED** donors (and “available” among approved). |
| 1.4.5 | `Screens.kt` | In `RegisterDonorDialog`, when creating the `Donor` object, set `verificationStatus = VerificationStatus.PENDING` and `documents = emptyList()`. |

### 1.5 Matching uses only APPROVED donors

| Step | File | Action |
|------|------|--------|
| 1.5.1 | `Screens.kt` | In `HospitalMatchScreen`, if/when you wire “Find Matching Donors” to real data: pass `donors` from `HospitalViewModel` and filter to `donors.filter { it.verificationStatus == VerificationStatus.APPROVED }` (and any other criteria like availability). For now, document this rule so Phase 2/3 don’t forget. |

---

## Phase 2: Donor “Pending Approval” Screen

### 2.1 Donor identity and status

Donors need a **donor profile** tied to the logged-in user so the app can show “your” verification status.

| Step | File | Action |
|------|------|--------|
| 2.1.1 | Decide storage | **Option A (simpler for now):** Add to Firestore `users/{uid}`: optional `donorProfileId: String?` and/or `verificationStatus: VerificationStatus?` when role is DONOR. **Option B:** Global `donors` collection with `userId` field; one document per donor user. Use Option A for minimal change: when role is DONOR, read `verificationStatus` from user doc. |
| 2.1.2 | `Models.kt` / `ViewModels.kt` | Extend `AppUser` with `verificationStatus: VerificationStatus? = null` for donors, **or** introduce a small `DonorProfile` (id, userId, verificationStatus, ...) and load it in a DonorViewModel. Easiest: add `verificationStatus: VerificationStatus?` to `AppUser`, set when role is DONOR. |
| 2.1.3 | AuthViewModel / Firestore | On signup as DONOR: create user doc with `role`, `verificationStatus = PENDING`. When loading user profile in `loadUserProfile`, read `verificationStatus` for donors. |
| 2.1.4 | FakeRepository | If donor flow uses only Firestore in production, you can skip FakeRepository for “my donor status” and read from AuthViewModel + Firestore. For a local-first donor screen, add a way to get “current donor’s status” (e.g. a stub that returns PENDING after donor signup). |

### 2.2 DonorPendingApprovalScreen

| Step | File | Action |
|------|------|--------|
| 2.2.1 | `Screens.kt` | Add composable `DonorPendingApprovalScreen(verificationStatus: VerificationStatus, onLogout: () -> Unit)`: show status (PENDING / APPROVED / REJECTED), short message (“Waiting for approval”, “You’re approved”, “Not approved”). If PENDING: do **not** show availability toggle (or show it disabled). If APPROVED: can show “You’re approved” and a button to go to main Donor home (with availability toggle there). |
| 2.2.2 | `Screens.kt` | Reuse or create a simple “Donor home” content (e.g. `DonorHomeScreen`) that shows availability toggle **only when** `verificationStatus == APPROVED`. |

### 2.3 Routing

| Step | File | Action |
|------|------|--------|
| 2.3.1 | `App.kt` | Add route e.g. `Route.DonorPending = "donor_pending"` (or reuse `Route.Donor` with different content based on status). |
| 2.3.2 | `App.kt` | When navigating to Donor after login/signup: read donor’s `verificationStatus`. If `PENDING` (or `REJECTED`), navigate to `DonorPendingApprovalScreen`; if `APPROVED`, navigate to main Donor home. |
| 2.3.3 | `App.kt` | Donor home: if status becomes APPROVED (e.g. after refresh), show main donor UI; otherwise show pending screen. So Donor route can be a single composable that chooses between `DonorPendingApprovalScreen` and `DonorHomeScreen` based on `authVm.loggedInUser.value?.verificationStatus`. |

### 2.4 Availability toggle

| Step | File | Action |
|------|------|--------|
| 2.4.1 | `Screens.kt` | In donor UI, only enable “Mark Available / Unavailable” when `verificationStatus == APPROVED`; when PENDING or REJECTED, show disabled toggle or message “Available after approval”. |

---

## Phase 3: Document Upload (Firebase Storage)

### 3.1 Dependencies & permissions

| Step | File | Action |
|------|------|--------|
| 3.1.1 | `app/build.gradle.kts` | Add `implementation("com.google.firebase:firebase-storage-ktx")` (use same BOM as existing Firebase). |
| 3.1.2 | `AndroidManifest.xml` | Add `android.permission.READ_MEDIA_IMAGES` (and optionally `READ_MEDIA_VIDEO` / `READ_EXTERNAL_STORAGE` for older API if needed). For PDFs, consider `ACTION_OPEN_DOCUMENT` / `READ_EXTERNAL_STORAGE` as per SDK. |

### 3.2 Storage structure

| Step | Action |
|------|--------|
| 3.2.1 | Decide paths | e.g. `donor_documents/{donorId}/{documentId}.{ext}` or `donor_documents/{userId}/{filename}`. Use `userId` (auth uid) if donor profile is per-user; else `donorId` from hospital’s donor list. |

### 3.3 Upload flow (donor app)

| Step | File | Action |
|------|------|--------|
| 3.3.1 | Donor UI | Add “Upload document” (e.g. in DonorPendingApprovalScreen or Donor home): button that launches file picker (images + PDF). Use `ActivityResultContracts.GetContent()` or `PickVisualMedia()` for images and a separate contract for PDF. |
| 3.3.2 | Repository / UseCase | Create an upload function: input `Uri` (or file), upload to Firebase Storage to chosen path, get download URL. |
| 3.3.3 | Firestore | After upload, append the download URL (and optional metadata: name, type, uploadedAt) to the donor’s `documents` list. Write to: **Option A:** `users/{uid}` field `documentUrls` or `documents` (list of maps). **Option B:** `donors/{donorId}` in hospital’s donors subcollection, or a global `donors` doc by userId. Prefer one place: e.g. `users/{uid}.documents` (array) or subcollection `users/{uid}/documents/{docId}`. |
| 3.3.4 | Donor model | Ensure `Donor.documents` is `List<String>` (URLs) or `List<DocumentInfo>` (url + name + type). Update Firestore serialization in `FirebaseRepository.kt` (Donor `toMap`/`parseDonor`) to include `documents`. |

### 3.4 Hospital: view documents and approve/reject

| Step | File | Action |
|------|------|--------|
| 3.4.1 | `Screens.kt` | In Hospital Donors screen, in the Pending section (or in a donor detail bottom sheet / dialog): show “Documents” with list of links or thumbnails; each item opens the URL in browser or in-app WebView. |
| 3.4.2 | `Screens.kt` | Keep Approve/Reject buttons; optionally add “View documents” that opens a dialog/screen listing `donor.documents` and opening each URL. |
| 3.4.3 | Security | Storage rules: allow read/write for `donor_documents/{userId}/{file}` only if `request.auth.uid == userId`. Firestore rules: already scoped by hospital uid; ensure document URLs are only readable by the hospital that owns the donor list (if donors are under `hospitals/{hospitalId}/donors`). |

### 3.5 Firebase Storage rules

| Step | File | Action |
|------|------|--------|
| 3.5.1 | `storage.rules` (or Firebase Console) | Add rule: only authenticated user can read/write their own folder, e.g. `match /donor_documents/{userId}/{allPaths=**} { allow read, write: if request.auth != null && request.auth.uid == userId; }`. |

---

## Summary checklist

- **Phase 1:** VerificationStatus enum → Donor model (verificationStatus, documents) → FakeRepository + FirebaseRepository (addDonor PENDING, setDonorVerificationStatus) → Hospital Donors UI (Pending / Approved sections, Approve/Reject) → Matching uses only APPROVED.
- **Phase 2:** Donor profile status (e.g. on AppUser or DonorProfile) → DonorPendingApprovalScreen + Donor home → Route donors by verificationStatus → Availability toggle only when APPROVED.
- **Phase 3:** Firebase Storage dependency + permissions → Storage path and rules → Donor upload UI + save URLs to Firestore → Donor model/repo documents → Hospital view document URLs + approve/reject.

Implement in this order; Phase 2 and 3 can be broken into smaller PRs as needed.
