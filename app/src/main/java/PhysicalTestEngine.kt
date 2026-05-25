package com.example.bloodlink

import android.content.Context
import android.graphics.Bitmap

/**
 * Modular physical test: run(bitmap) -> TestResult.
 *
 * Clinical ROI strategy per condition:
 *   - Pallor:      Lower eyelid / palpebral conjunctiva — goes pale when Hb < 12 g/dL
 *   - Jaundice:    Sclera (white of eye) — yellows due to bilirubin
 *   - Cyanosis:    Nail bed — turns blue/purple when SpO2 is low
 *   - Skin Lesion: Center of image — user positions forearm to fill frame
 *
 * ML pipeline (when .tflite model is loaded):
 *   Photo → ROI crop → resize 224x224 → MobileNetV2 → Dense classifier → score [0,1]
 *
 * Fallback (when model not yet loaded):
 *   Photo → ROI crop → HSV/color heuristic → threshold → score [0,1]
 */
interface PhysicalTestModule {
    val id: String
    val title: String
    val instructions: String
    suspend fun run(bitmap: Bitmap): TestResult
}

object PhysicalTestEngine {
    fun init(context: Context) { ImagePipeline.initInterpreters(context) }
    fun release() { ImagePipeline.releaseInterpreters() }
    val areModelsReady: Boolean get() = ImagePipeline.areModelsReady()
}

// ─── PALLOR MODULE ────────────────────────────────────────────────────────────
class PallorModule : PhysicalTestModule {
    override val id = "pallor"
    override val title = "Pallor / Anemia Risk (Conjunctiva)"
    override val instructions =
        "Pull your lower eyelid down gently so the pink inner surface is visible. " +
                "Hold still in good lighting — natural light or white fluorescent is best. " +
                "The camera will analyze the inner lower eyelid color. " +
                "Pale or white inner eyelid may indicate low hemoglobin. " +
                "This is a screening aid only — NOT a medical diagnosis."

    override suspend fun run(bitmap: Bitmap): TestResult {
        android.util.Log.d("BloodLink-Pallor", "Input: ${bitmap.width}x${bitmap.height}")

        // ROI: Lower eyelid (palpebral conjunctiva) — clinically correct for pallor
        // Falls back to full image if no face detected (e.g. uploaded dataset close-up)
        val eyelidRect = MlKitFaceRoi.getLowerEyelidRoi(bitmap)
        val roi = if (eyelidRect != null) {
            android.util.Log.d("BloodLink-Pallor", "✅ Eyelid ROI detected: $eyelidRect")
            MlKitFaceRoi.cropFromRect(bitmap, eyelidRect)
        } else {
            android.util.Log.d("BloodLink-Pallor", "⚠️ No face — using full image fallback")
            bitmap  // Full image — works for close-up dataset images
        }
        android.util.Log.d("BloodLink-Pallor", "ROI: ${roi.width}x${roi.height}")

        val interpreter = ImagePipeline.getPallorInterpreter()
        val score = if (interpreter != null) {
            android.util.Log.d("BloodLink-Pallor", "Running TFLite inference")
            val s = ImagePipeline.runTfliteInference(roi, interpreter)
            android.util.Log.d("BloodLink-Pallor", "TFLite score: $s")
            s
        } else {
            android.util.Log.w("BloodLink-Pallor", "Model not loaded — heuristic fallback")
            val features = ImagePipeline.extractFeatures(roi, applyClahe = true)
            ImagePipeline.pallorInference(features)
        }

        val result = if (score > PALLOR_THRESHOLD) PhysicalTestResult.POSSIBLE_SIGN
        else PhysicalTestResult.NORMAL
        android.util.Log.d("BloodLink-Pallor", "Result: $result (score=$score)")

        val warning = "Score: ${(score * 100).toInt()}%"
        return TestResult(result = result, scoreOrIndex = score, qualityWarning = warning)
    }
}
// Threshold raised to 0.70 to reduce false positives from domain mismatch.
// Model was trained on clinical close-up images; app captures selfie-style photos.
// Retraining on app-captured images validated against CBC is required for clinical accuracy.
private const val PALLOR_THRESHOLD = 0.70f

// ─── JAUNDICE MODULE ──────────────────────────────────────────────────────────
class JaundiceModule : PhysicalTestModule {
    override val id = "jaundice"
    override val title = "Possible Jaundice Sign (Sclera)"
    override val instructions =
        "Look slightly upward so the white part of your eye (sclera) is well exposed. " +
                "Hold your eye wide open in good lighting. " +
                "The camera will analyze the sclera color for yellow discoloration. " +
                "This is a screening aid only — NOT a diagnosis of hepatitis or liver disease."

    override suspend fun run(bitmap: Bitmap): TestResult {
        // ROI: Sclera — clinically correct for jaundice
        val scleraRect = MlKitFaceRoi.getScleraRoi(bitmap)
        val roi = if (scleraRect != null) {
            MlKitFaceRoi.cropFromRect(bitmap, scleraRect)
        } else {
            // Fallback: upper center crop where sclera appears when looking up
            cropCenterRegion(bitmap, 0.4f, 0.25f)
        }

        val interpreter = ImagePipeline.getJaundiceInterpreter()
        val score = if (interpreter != null) {
            ImagePipeline.runTfliteInference(roi, interpreter)
        } else {
            ImagePipeline.jaundiceIndexFromRoi(roi)
        }

        val result = if (score > JAUNDICE_THRESHOLD) PhysicalTestResult.POSSIBLE_SIGN
        else PhysicalTestResult.NORMAL
        val warning = "Score: ${(score * 100).toInt()}%"
        return TestResult(result = result, scoreOrIndex = score, qualityWarning = warning)
    }
}
private const val JAUNDICE_THRESHOLD = 0.35f

// ─── CYANOSIS MODULE ──────────────────────────────────────────────────────────
class CyanosisModule : PhysicalTestModule {
    override val id = "cyanosis"
    override val title = "Possible Cyanosis Sign (Nail Bed)"
    override val instructions =
        "Hold your hand flat with fingernails facing the camera. " +
                "Spread fingers slightly and fill the frame with your nails. " +
                "Use good white or natural lighting. Remove nail polish if present. " +
                "The camera analyzes nail bed color for blue or purple discoloration. " +
                "This is a screening aid only — NOT a medical diagnosis."

    override suspend fun run(bitmap: Bitmap): TestResult {
        // ROI: Center 70% width × 60% height — user fills frame with hand
        val roi = cropCenterRegion(bitmap, 0.70f, 0.60f)

        val interpreter = ImagePipeline.getCyanosisInterpreter()
        val score = if (interpreter != null) {
            ImagePipeline.runTfliteInference(roi, interpreter)
        } else {
            ImagePipeline.cyanosisIndexFromRoi(roi)
        }

        val result = if (score > CYANOSIS_THRESHOLD) PhysicalTestResult.POSSIBLE_SIGN
        else PhysicalTestResult.NORMAL
        val warning = if (interpreter == null)
            "Heuristic mode — cyanosis_model.tflite not loaded"
        else
            "AI confidence: ${(score * 100).toInt()}%"
        return TestResult(result = result, scoreOrIndex = score, qualityWarning = warning)
    }
}
private const val CYANOSIS_THRESHOLD = 0.30f

// ─── SKIN LESION MODULE ───────────────────────────────────────────────────────
class SkinLesionModule : PhysicalTestModule {
    override val id = "skin_lesion"
    override val title = "Skin Lesion / Rash Check (Forearm)"
    override val instructions =
        "Hold your inner forearm (soft side facing camera) under good lighting. " +
                "Position so the forearm fills most of the frame from wrist to elbow. " +
                "The camera analyzes skin color and texture for rashes, marks, or lesions. " +
                "This is a screening aid only — NOT a medical diagnosis."

    override suspend fun run(bitmap: Bitmap): TestResult {
        // ROI: Center 80% × 70% — user positions forearm to fill frame
        val roi = cropCenterRegion(bitmap, 0.80f, 0.70f)

        val interpreter = ImagePipeline.getSkinLesionInterpreter()
        val score = if (interpreter != null) {
            ImagePipeline.runTfliteInference(roi, interpreter)
        } else {
            ImagePipeline.skinLesionIndexFromRoi(roi)
        }

        val result = if (score > SKIN_LESION_THRESHOLD) PhysicalTestResult.POSSIBLE_SIGN
        else PhysicalTestResult.NORMAL
        val warning = if (interpreter == null)
            "Heuristic mode — skin_lesion_model.tflite not loaded"
        else
            "AI confidence: ${(score * 100).toInt()}%"
        return TestResult(result = result, scoreOrIndex = score, qualityWarning = warning)
    }
}
private const val SKIN_LESION_THRESHOLD = 0.40f

// ─── SHARED UTILITY ───────────────────────────────────────────────────────────
internal fun cropCenterRegion(bitmap: Bitmap, widthFrac: Float, heightFrac: Float): Bitmap {
    val w  = bitmap.width
    val h  = bitmap.height
    val cw = (w * widthFrac).toInt().coerceAtLeast(1)
    val ch = (h * heightFrac).toInt().coerceAtLeast(1)
    val x  = (w - cw) / 2
    val y  = (h - ch) / 2
    return Bitmap.createBitmap(bitmap, x, y, cw, ch)
}