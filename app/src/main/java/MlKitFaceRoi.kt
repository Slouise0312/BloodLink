package com.example.bloodlink

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * ML Kit Face Detection for ROI localization.
 *
 * Clinical basis:
 *   - Pallor:   Palpebral conjunctiva (inner lower eyelid) — goes pale when Hb is low
 *   - Jaundice: Sclera (white of eye) — yellows due to bilirubin deposits
 *
 * Both regions are accessed via the eye landmark + vertical offset to reach
 * the lower eyelid / sclera area below the iris.
 */
object MlKitFaceRoi {

    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setMinFaceSize(0.10f)  // Detect faces down to 10% of image width
        .build()

    private val detector by lazy { FaceDetection.getClient(options) }

    /**
     * Returns ROI for the LOWER EYELID / PALPEBRAL CONJUNCTIVA.
     * This is the clinically correct region for pallor detection.
     *
     * The crop is centered BELOW the eye landmark (where the lower lid is)
     * and sized proportionally to the face.
     */
    suspend fun getLowerEyelidRoi(bitmap: Bitmap): Rect? =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, 0)
            detector.process(image)
                .addOnSuccessListener { faces ->
                    val face = faces.firstOrNull() ?: run {
                        android.util.Log.d("BloodLink-ROI", "No face detected")
                        cont.resume(null)
                        return@addOnSuccessListener
                    }

                    val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
                    val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)

                    // Use whichever eye landmark we have
                    val eyeLandmark = leftEye ?: rightEye
                    if (eyeLandmark == null) {
                        android.util.Log.d("BloodLink-ROI", "No eye landmark")
                        cont.resume(null)
                        return@addOnSuccessListener
                    }

                    val ex = eyeLandmark.position.x
                    val ey = eyeLandmark.position.y
                    val faceW = face.boundingBox.width()

                    // Crop size: 30% of face width — large enough for good inference
                    val halfW = (faceW * 0.30f).toInt().coerceAtLeast(40)
                    val halfH = (faceW * 0.15f).toInt().coerceAtLeast(20)

                    // Shift DOWN from eye center to reach lower eyelid
                    // The palpebral conjunctiva is approximately 0.12 * faceWidth below eye center
                    val verticalOffset = (faceW * 0.12f).toInt()

                    val cx = ex.toInt()
                    val cy = (ey + verticalOffset).toInt()  // shifted DOWN

                    val left   = (cx - halfW).coerceIn(0, bitmap.width - 1)
                    val top    = (cy - halfH).coerceIn(0, bitmap.height - 1)
                    val right  = (cx + halfW).coerceAtMost(bitmap.width)
                    val bottom = (cy + halfH).coerceAtMost(bitmap.height)

                    val roi = Rect(left, top, right, bottom)
                    android.util.Log.d("BloodLink-ROI",
                        "Lower eyelid ROI: $roi (face=${faceW}px, eye=${ex.toInt()},${ey.toInt()})")
                    cont.resume(roi)
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("BloodLink-ROI", "ML Kit failed: ${e.message}")
                    cont.resume(null)
                }
        }

    /**
     * Returns ROI for the SCLERA (white of eye).
     * This is the clinically correct region for jaundice detection.
     *
     * The sclera is visible above AND below the iris — we crop the full eye
     * region centered at the eye landmark at a wider crop to capture both.
     */
    suspend fun getScleraRoi(bitmap: Bitmap): Rect? =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, 0)
            detector.process(image)
                .addOnSuccessListener { faces ->
                    val face = faces.firstOrNull() ?: run {
                        cont.resume(null)
                        return@addOnSuccessListener
                    }

                    val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
                    val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)
                    val eyeLandmark = leftEye ?: rightEye

                    if (eyeLandmark == null) {
                        cont.resume(null)
                        return@addOnSuccessListener
                    }

                    val ex = eyeLandmark.position.x
                    val ey = eyeLandmark.position.y
                    val faceW = face.boundingBox.width()

                    // Wider crop for sclera — captures both sides of iris
                    val halfW = (faceW * 0.35f).toInt().coerceAtLeast(50)
                    val halfH = (faceW * 0.18f).toInt().coerceAtLeast(25)

                    val left   = (ex.toInt() - halfW).coerceIn(0, bitmap.width - 1)
                    val top    = (ey.toInt() - halfH).coerceIn(0, bitmap.height - 1)
                    val right  = (ex.toInt() + halfW).coerceAtMost(bitmap.width)
                    val bottom = (ey.toInt() + halfH).coerceAtMost(bitmap.height)

                    cont.resume(Rect(left, top, right, bottom))
                }
                .addOnFailureListener { cont.resume(null) }
        }

    /**
     * Legacy method — kept for backward compatibility.
     * New code should use getLowerEyelidRoi() or getScleraRoi() directly.
     */
    suspend fun getLeftEyeRegion(bitmap: Bitmap): Rect? = getLowerEyelidRoi(bitmap)

    fun cropFromRect(bitmap: Bitmap, rect: Rect): Bitmap {
        val w = rect.width().coerceAtLeast(1).coerceAtMost(bitmap.width - rect.left)
        val h = rect.height().coerceAtLeast(1).coerceAtMost(bitmap.height - rect.top)
        return Bitmap.createBitmap(bitmap, rect.left, rect.top, w, h)
    }
}