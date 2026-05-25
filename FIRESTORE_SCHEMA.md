# Firestore schema (BloodLink)

## Collections

### `users/{uid}`
| Field | Type | Description |
|-------|------|-------------|
| name | string | Display name |
| email | string | Email |
| phone | string | Optional |
| role | string | `APPLICANT` \| `STAFF` \| `ADMIN` |
| orgId | string? | Required for STAFF/ADMIN (organization id) |
| fcmToken | string? | FCM token for push notifications |
| fcmTokenUpdatedAt | number? | Timestamp |
| createdAt | number | Timestamp |
| updatedAt | number | Timestamp |

### `users/{uid}/alerts/{alertId}`
| Field | Type | Description |
|-------|------|-------------|
| type | string | e.g. `SCREENING_COMPLETE`, `FINAL_OUTCOME` |
| title | string | |
| body | string | |
| relatedEventId | string? | |
| relatedScreeningId | string? | |
| createdAt | number | Timestamp |
| read | boolean | Default false |

### `organizations/{orgId}`
| Field | Type | Description |
|-------|------|-------------|
| name | string | |
| address | string | |
| contact | string | |
| createdAt | number | Timestamp |

### `events/{eventId}`
| Field | Type | Description |
|-------|------|-------------|
| orgId | string | |
| title | string | |
| dateTime | number | Timestamp (event date/time) |
| location | string | |
| status | string | `ACTIVE` \| `CLOSED` |
| createdByUid | string | |
| createdAt | number | Timestamp |
| updatedAt | number | Timestamp |
| qrPayload | string? | Usually same as eventId for QR scan |

### `screenings/{screeningId}`
| Field | Type | Description |
|-------|------|-------------|
| eventId | string | |
| orgId | string | |
| applicantUid | string | |
| pallorResult | string | `NOT_DONE` \| `NORMAL` \| `POSSIBLE_SIGN` |
| pallorScore | number? | Optional |
| jaundiceResult | string | Same enum |
| jaundiceIndex | number? | Optional |
| questionnaireStatus | string | `NOT_DONE` \| `ELIGIBLE` \| `TEMP_DEFERRED` \| `NOT_ELIGIBLE` |
| deferralReason | string? | |
| deferralUntil | number? | Timestamp (optional) |
| overallStatus | string | `INCOMPLETE` \| `ELIGIBLE` \| `TEMP_DEFERRED` \| `NOT_ELIGIBLE` |
| finalOutcome | string | `NONE` \| `ARRIVED` \| `ACCEPTED_ONSITE` \| `DEFERRED_ONSITE` \| `DONATED` \| `NO_SHOW` |
| staffNotes | string? | |
| updatedByStaffUid | string? | Set when staff updates finalOutcome |
| createdAt | number | Timestamp |
| updatedAt | number | Timestamp |

## Indexes

- **users/{uid}/alerts**: order by `createdAt` descending (create when first query runs or in Firebase Console).
