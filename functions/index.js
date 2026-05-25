/**
 * BloodLink Cloud Functions
 * - onScreeningComplete: when screening overallStatus becomes ELIGIBLE/TEMP_DEFERRED/NOT_ELIGIBLE, notify staff/admin of that org
 * - onFinalOutcomeUpdate: when staff sets finalOutcome, send FCM to applicant (in-app alert is created by the app)
 */

const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

const db = admin.firestore();

/**
 * When a screening document is updated and overallStatus is complete (not INCOMPLETE),
 * create in-app alerts for all staff/admin of that org and send FCM to their fcmToken.
 */
exports.onScreeningComplete = functions.firestore
  .document("screenings/{screeningId}")
  .onUpdate(async (change, context) => {
    const after = change.after.data();
    const before = change.before.data();
    const screeningId = context.params.screeningId;

    const statusAfter = after.overallStatus;
    const statusBefore = before.overallStatus;
    if (statusAfter === "INCOMPLETE" || statusAfter === statusBefore) return;

    const orgId = after.orgId;
    const eventId = after.eventId;
    if (!orgId) return;

    const staffSnapshot = await db.collection("users")
      .where("orgId", "==", orgId)
      .where("role", "in", ["STAFF", "ADMIN"])
      .get();

    const title = "New screening completed";
    const body = `Screening ${screeningId} is ${statusAfter}. Event: ${eventId}.`;

    const batch = db.batch();
    const fcmTokens = [];

    staffSnapshot.docs.forEach((doc) => {
      const uid = doc.id;
      const alertRef = db.collection("users").doc(uid).collection("alerts").doc();
      batch.set(alertRef, {
        type: "SCREENING_COMPLETE",
        title,
        body,
        relatedEventId: eventId,
        relatedScreeningId: screeningId,
        createdAt: Date.now(),
        read: false,
      });
      const token = doc.data().fcmToken;
      if (token) fcmTokens.push(token);
    });

    await batch.commit();

    if (fcmTokens.length > 0) {
      await admin.messaging().sendEachForMulticast({
        tokens: fcmTokens,
        notification: { title, body },
        data: { screeningId, eventId, type: "SCREENING_COMPLETE" },
      }).catch(() => {});
    }
  });

/**
 * When a screening's finalOutcome is set (not NONE): create in-app alert for applicant and send FCM.
 */
exports.onFinalOutcomeUpdate = functions.firestore
  .document("screenings/{screeningId}")
  .onUpdate(async (change, context) => {
    const after = change.after.data();
    const before = change.before.data();
    const screeningId = context.params.screeningId;

    const outcomeAfter = after.finalOutcome;
    const outcomeBefore = before.finalOutcome;
    if (outcomeAfter === "NONE" || outcomeAfter === outcomeBefore) return;

    const applicantUid = after.applicantUid;
    const eventId = after.eventId || "";
    if (!applicantUid) return;

    const title = "Screening outcome updated";
    const body = `Your screening outcome was set to: ${outcomeAfter}.`;

    // In-app alert for applicant (so alerts work even without push)
    const alertRef = db.collection("users").doc(applicantUid).collection("alerts").doc();
    await alertRef.set({
      type: "FINAL_OUTCOME",
      title,
      body,
      relatedEventId: eventId,
      relatedScreeningId: screeningId,
      createdAt: Date.now(),
      read: false,
    }).catch(() => {});

    const applicantDoc = await db.collection("users").doc(applicantUid).get();
    const token = applicantDoc.exists && applicantDoc.data().fcmToken;
    if (token) {
      await admin.messaging().send({
        token,
        notification: { title, body },
        data: { screeningId, type: "FINAL_OUTCOME" },
      }).catch(() => {});
    }
  });
