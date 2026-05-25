# Data Privacy Compliance — Republic Act No. 10173

## Overview

BloodLink complies with the **Data Privacy Act of 2012 (RA 10173)** and its Implementing Rules and Regulations (IRR) as enforced by the National Privacy Commission (NPC) of the Philippines.

---

## Personal Data Collected

| Data Type | Collected | Stored | Purpose |
|---|---|---|---|
| Full name | ✅ | Firebase Auth | Account identification |
| Email address | ✅ | Firebase Auth | Authentication |
| Age / Sex | ✅ | Firestore | Screening eligibility assessment |
| Screening scores (numerical) | ✅ | Firestore | Pre-screening results |
| Eye/face photos | ❌ | **Never stored** | Processed on-device only, discarded after inference |
| Location data | ❌ | Not collected | — |
| Device identifiers | ❌ | Not collected | — |

---

## Key Compliance Measures

### 1. Lawful Basis for Processing
- **Consent** — Explicit, informed consent is obtained at two points:
  - **Account creation** — User must read and accept the Data Privacy Notice before sign-up
  - **Event participation** — User must re-consent before joining each screening event
- Consent checkbox explicitly references RA 10173 by name

### 2. Purpose Limitation
- Personal data is collected solely for the purpose of pre-screening eligibility assessment at blood donation events
- Data is not used for marketing, profiling, or any secondary purpose

### 3. Data Minimization
- Only the minimum necessary data is collected
- No biometric images are stored or transmitted — only extracted numerical scores
- Photos are processed entirely on-device and discarded immediately after inference

### 4. Storage Security
- All data is stored in Google Firebase (Firestore + Authentication)
- Firebase provides encryption at rest and in transit (TLS 1.2+)
- Access is restricted to authenticated users with appropriate role (applicant or staff)

### 5. Data Subject Rights
Users are informed of their rights under RA 10173 Section 16:
- **Right to be informed** — Privacy notice shown at sign-up and event join
- **Right to access** — Users can view their screening results in-app
- **Right to object** — Users can decline consent and not use the app
- **Right to erasure** — Users can request data deletion (Leave Event deletes screening records)
- **Right to rectification** — Users can retake screening tests before saving

### 6. Retention Policy
- Screening records are retained for the duration of the blood donation event
- Archived records are retained for a maximum of one (1) year
- Users may request earlier deletion at any time

### 7. Breach Notification
- In the event of a data breach, the NPC and affected data subjects will be notified within 72 hours as required by RA 10173 Section 20(f)

---

## Consent Screen Content

The full consent text displayed to users includes:
1. Statement of compliance with RA 10173 and its IRR
2. List of personal data collected (name, age, sex, numerical scores)
3. Statement that no biometric images are stored
4. Purpose of data collection (pre-screening aid)
5. Storage security description (Firebase, encrypted)
6. Aggregated/anonymized research use disclosure
7. Data subject rights enumeration
8. Retention period disclosure

---

## Contact

For data privacy inquiries or data subject access requests:
- [Your University Data Protection Officer contact]
- [Your email]
