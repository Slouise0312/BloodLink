# BloodLink — Functional test checklist

Use this to verify end-to-end flows. Assumes Firebase is configured (see FIREBASE_SETUP.md).

---

## Prerequisites

- Two accounts: one **APPLICANT**, one **STAFF** or **ADMIN** (same org).
- **ADMIN** must have `orgId` set (e.g. create an organization doc and use its id, or use a test id like `org1` and create that org in Firestore).
- At least one **Event** created by ADMIN (note the Event ID shown on the Events tab).

---

## A. Applicant flow (end-to-end)

1. **Login as APPLICANT**
   - Open app → Login with applicant email/password (or register then choose role APPLICANT).

2. **Join event**
   - Go to **Event** tab.
   - Enter the **Event ID** (from admin’s Events list or from the event card “ID: xxx”).
   - Tap **Join event**.
   - Expect: loading, then event title/location and “Your screening: INCOMPLETE”.

3. **Physical screening**
   - Go to **Physical Screening** tab.
   - Tap **Pallor** card → **Take photo** → take/capture a photo.
   - Expect: result (NORMAL or POSSIBLE_SIGN) and **Save result**.
   - Tap **Save result**.
   - Repeat for **Jaundice** (take photo, save result).

4. **Questionnaire**
   - Go to **Questionnaire** tab.
   - Answer all questions (Yes/No).
   - After last question, expect outcome (ELIGIBLE / TEMP_DEFERRED / NOT_ELIGIBLE) and **Save & continue**.
   - Tap **Save & continue**.

5. **Result**
   - Go to **Result** tab.
   - Expect: Overall status, pallor/jaundice/questionnaire summary, disclaimer, **Screening ID** and **QR code**.
   - Note the Screening ID (or use the QR) for staff verification.

6. **Alerts**
   - Go to **Profile** tab.
   - If staff updated your outcome, an alert may appear under **Alerts** (after staff verification).

---

## B. Staff / Admin verification (end-to-end)

1. **Login as STAFF or ADMIN**
   - Use an account with role STAFF or ADMIN and same `orgId` as the event.

2. **Events**
   - **Events** tab: expect list of events for your org.
   - **ADMIN only**: tap **Create event** → set Title and Location → Create. Copy the new Event ID.

3. **Applicants list**
   - Go to **Applicants** tab.
   - Enter the **Event ID** (same as applicant used).
   - Expect: live list of screenings (applicants) for that event. One row per screening with status and final outcome.

4. **Verify**
   - Go to **Verify** tab.
   - Enter the **Screening ID** from the applicant’s Result screen (or scan their QR when implemented).
   - Tap **Load screening**.
   - Expect: Applicant UID, Overall status.
   - Choose a **Final outcome** (e.g. ARRIVED, ACCEPTED_ONSITE, DONATED).
   - Optionally add **Staff notes**.
   - Tap **Save outcome**.
   - Expect: success; form clears. Applicant should get an in-app alert (and FCM if configured).

5. **Analytics**
   - Go to **Analytics** tab.
   - Expect: counts (Eligible, Temp deferred, Not eligible, Incomplete) and completion rate for the event selected in Applicants.

6. **Alerts**
   - Go to **Settings** tab.
   - After an applicant completes screening, an alert may appear under **Alerts** (from Cloud Function or in-app).

---

## C. Optional: Firebase emulator

- Run Firestore (and optionally Functions) emulator for local testing without touching production.
- In `firebase.json` add:
  - `"firestore": { "rules": "firestore.rules" }`
  - `"emulators": { "firestore": { "port": 8080 }, "functions": { "port": 5001 } }`
- Use `firebase emulators:start --only firestore,functions` and point the app to the emulator (e.g. in MainActivity or a debug build).

---

## D. Quick smoke checks

- [ ] Auth: Login, Register, Role selection (when user doc missing), Logout.
- [ ] Applicant: Join event (valid + invalid ID), leave event.
- [ ] Applicant: Run both physical tests, save; submit questionnaire, save.
- [ ] Applicant: Result screen shows correct overall status and QR.
- [ ] Staff: Events list loads; Admin can create event.
- [ ] Staff: Applicants list updates when event ID is set (live).
- [ ] Staff: Verify screen loads by Screening ID and saves final outcome; applicant receives alert.
- [ ] Alerts: Profile/Settings show alerts; tapping marks read.
- [ ] No crash on empty states (e.g. no events, no applicants, no alerts).
