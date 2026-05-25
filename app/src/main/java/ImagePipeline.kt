package com.example.bloodlink

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.min
import kotlin.math.sqrt

/**
 * CLAHE (Contrast Limited Adaptive Histogram Equalization) on L channel.
 * Converts ROI to grayscale as L, applies tile-based CLAHE, returns enhanced bitmap (grayscale used for feature extraction).
 * No OpenCV dependency: pure Kotlin implementation.
 */
object ImagePipeline {

    // ── LiteRT interpreter pool ──────────────────────────────────────────────
    // LiteRT is the official rename of TensorFlow Lite (2024).
    // Same Interpreter API, no namespace conflicts.
    // Model files must be placed in app/src/main/assets/

    private var pallorInterpreter: Interpreter? = null
    private var jaundiceInterpreter: Interpreter? = null
    private var cyanosisInterpreter: Interpreter? = null
    private var skinLesionInterpreter: Interpreter? = null

    private const val MODEL_INPUT_SIZE = 224
    private const val MODEL_INPUT_CHANNELS = 3

    /**
     * Call once at app startup (e.g. from ScreeningViewModel.init).
     * Loads all 4 .tflite models from assets. Missing files are tolerated —
     * the corresponding module will fall back to heuristic detection.
     */
    fun initInterpreters(context: Context) {
        val options = Interpreter.Options().apply { setNumThreads(2) }
        if (pallorInterpreter == null)
            pallorInterpreter = loadInterpreter(context, "pallor_model.tflite", options)
        if (jaundiceInterpreter == null)
            jaundiceInterpreter = loadInterpreter(context, "jaundice_model.tflite", options)
        if (cyanosisInterpreter == null)
            cyanosisInterpreter = loadInterpreter(context, "cyanosis_model.tflite", options)
        if (skinLesionInterpreter == null)
            skinLesionInterpreter = loadInterpreter(context, "skin_lesion_model.tflite", options)
    }

    fun areModelsReady(): Boolean =
        pallorInterpreter != null &&
                jaundiceInterpreter != null &&
                cyanosisInterpreter != null &&
                skinLesionInterpreter != null

    private fun loadInterpreter(context: Context, fileName: String, options: Interpreter.Options): Interpreter? {
        return try {
            android.util.Log.d("BloodLink-TFLite", "Loading: $fileName")
            val model = loadModelFile(context, fileName)
            android.util.Log.d("BloodLink-TFLite", "Buffer: ${model.capacity()} bytes")
            val interp = Interpreter(model, options)
            android.util.Log.d("BloodLink-TFLite", "INPUT  shape: ${interp.getInputTensor(0).shape().joinToString()}")
            android.util.Log.d("BloodLink-TFLite", "OUTPUT shape: ${interp.getOutputTensor(0).shape().joinToString()}")
            android.util.Log.d("BloodLink-TFLite", "✅ Loaded: $fileName")
            interp
        } catch (e: Throwable) {
            android.util.Log.e("BloodLink-TFLite", "❌ FAILED $fileName: ${e.javaClass.name}: ${e.message}")
            null
        }
    }

    private fun loadModelFile(context: Context, fileName: String): MappedByteBuffer {
        android.util.Log.d("BloodLink-TFLite", "Opening asset: $fileName")
        val fd = context.assets.openFd(fileName)
        android.util.Log.d("BloodLink-TFLite", "Asset opened: offset=${fd.startOffset} length=${fd.declaredLength}")
        val inputStream = FileInputStream(fd.fileDescriptor)
        val fileChannel = inputStream.channel
        val buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        inputStream.close()
        fd.close()
        return buffer
    }

    /**
     * Run MobileNetV2 + Random Forest TFLite inference on an ROI bitmap.
     * 1. Resize to 224x224
     * 2. Normalize pixels to [-1, 1] (MobileNetV2 standard)
     * 3. Run interpreter
     * 4. Return probability [0,1]
     */
    /**
     * Run the full TFLite model (MobileNetV2 + Dense classifier).
     *
     * The model already includes pixel normalization internally (Lambda layer),
     * so we pass RAW pixel values (0-255) directly.
     *
     * Input:  [1, 224, 224, 3] float32 — raw RGB pixel values 0-255
     * Output: [1, 1] float32 — probability score 0.0 to 1.0
     *         Score > 0.5 = POSSIBLE SIGN, Score ≤ 0.5 = NORMAL
     */
    fun runTfliteInference(roi: Bitmap, interpreter: Interpreter): Float {
        val resized = Bitmap.createScaledBitmap(roi, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, true)

        val byteBuffer = ByteBuffer.allocateDirect(
            4 * MODEL_INPUT_SIZE * MODEL_INPUT_SIZE * MODEL_INPUT_CHANNELS
        ).apply { order(ByteOrder.nativeOrder()) }

        val pixels = IntArray(MODEL_INPUT_SIZE * MODEL_INPUT_SIZE)
        resized.getPixels(pixels, 0, MODEL_INPUT_SIZE, 0, 0, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE)

        for (pixel in pixels) {
            // Pass raw pixel values 0-255 — the model's Lambda layer handles normalization
            byteBuffer.putFloat(Color.red(pixel).toFloat())
            byteBuffer.putFloat(Color.green(pixel).toFloat())
            byteBuffer.putFloat(Color.blue(pixel).toFloat())
        }

        // Output is a single probability score [0, 1]
        val output = Array(1) { FloatArray(1) }
        byteBuffer.rewind()
        interpreter.run(byteBuffer, output)

        val score = output[0][0]
        android.util.Log.d("BloodLink-TFLite", "Inference score: $score")
        return score.coerceIn(0f, 1f)
    }

    fun getPallorInterpreter(): Interpreter?    = pallorInterpreter
    fun getJaundiceInterpreter(): Interpreter?  = jaundiceInterpreter
    fun getCyanosisInterpreter(): Interpreter?  = cyanosisInterpreter
    fun getSkinLesionInterpreter(): Interpreter? = skinLesionInterpreter

    fun releaseInterpreters() {
        pallorInterpreter?.close();    pallorInterpreter = null
        jaundiceInterpreter?.close();  jaundiceInterpreter = null
        cyanosisInterpreter?.close();  cyanosisInterpreter = null
        skinLesionInterpreter?.close(); skinLesionInterpreter = null
    }

    // ── CLAHE ────────────────────────────────────────────────────────────────
    private const val TILE_SIZE = 8
    private const val CLIP_LIMIT = 2.0f

    /**
     * Apply CLAHE on the L (luminance) channel. Input bitmap is RGB; we use grayscale as L.
     * Returns a new bitmap (same size) with L channel equalized; R=G=B for output (grayscale display).
     */
    fun applyClaheOnL(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val gray = FloatArray(w * h)
        for (i in pixels.indices) {
            val r = Color.red(pixels[i]) / 255f
            val g = Color.green(pixels[i]) / 255f
            val b = Color.blue(pixels[i]) / 255f
            gray[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }
        clahe1D(gray, w, h)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val outPixels = IntArray(w * h)
        for (i in gray.indices) {
            val v = (gray[i].coerceIn(0f, 1f) * 255).toInt()
            outPixels[i] = Color.rgb(v, v, v)
        }
        out.setPixels(outPixels, 0, w, 0, 0, w, h)
        return out
    }

    private fun clahe1D(data: FloatArray, width: Int, height: Int) {
        val nx = (width + TILE_SIZE - 1) / TILE_SIZE
        val ny = (height + TILE_SIZE - 1) / TILE_SIZE
        val clipLimit = (CLIP_LIMIT * TILE_SIZE * TILE_SIZE / 256f).toInt().coerceAtLeast(1)
        for (ty in 0 until ny) {
            for (tx in 0 until nx) {
                val x0 = tx * TILE_SIZE
                val y0 = ty * TILE_SIZE
                val x1 = min(x0 + TILE_SIZE, width)
                val y1 = min(y0 + TILE_SIZE, height)
                val hist = IntArray(256)
                for (y in y0 until y1) {
                    for (x in x0 until x1) {
                        val v = (data[y * width + x] * 255f).toInt().coerceIn(0, 255)
                        hist[v]++
                    }
                }
                val clipped = clipHistogram(hist, clipLimit)
                val cdf = IntArray(256)
                cdf[0] = clipped[0]
                for (i in 1 until 256) cdf[i] = cdf[i - 1] + clipped[i]
                val total = cdf[255]
                if (total > 0) {
                    for (y in y0 until y1) {
                        for (x in x0 until x1) {
                            val idx = y * width + x
                            val v = (data[idx] * 255f).toInt().coerceIn(0, 255)
                            data[idx] = cdf[v] / total.toFloat()
                        }
                    }
                }
            }
        }
    }

    private fun clipHistogram(hist: IntArray, clipLimit: Int): IntArray {
        val out = hist.copyOf()
        var excess = 0
        for (i in out.indices) {
            if (out[i] > clipLimit) {
                excess += out[i] - clipLimit
                out[i] = clipLimit
            }
        }
        val perBin = excess / 256
        var remainder = excess % 256
        for (i in out.indices) {
            out[i] += perBin
            if (remainder > 0) {
                out[i]++
                remainder--
            }
        }
        return out
    }

    /**
     * Extract features from ROI (after optional CLAHE): RGB mean, HSV S/V mean, redness ratio.
     */
    data class RoiFeatures(
        val rMean: Float,
        val gMean: Float,
        val bMean: Float,
        val sMean: Float,
        val vMean: Float,
        val rednessRatio: Float
    )

    fun extractFeatures(roi: Bitmap, applyClahe: Boolean = true): RoiFeatures {
        val src = if (applyClahe) applyClaheOnL(roi) else roi
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        var rSum = 0f; var gSum = 0f; var bSum = 0f
        var sSum = 0f; var vSum = 0f
        var redCount = 0
        var total = 0
        val hsv = FloatArray(3)
        for (p in pixels) {
            val r = Color.red(p) / 255f
            val g = Color.green(p) / 255f
            val b = Color.blue(p) / 255f
            rSum += r; gSum += g; bSum += b
            Color.RGBToHSV(Color.red(p), Color.green(p), Color.blue(p), hsv)
            sSum += hsv[1]; vSum += hsv[2]
            if (r > 0.4f && r > g && r > b) redCount++
            total++
        }
        val n = total.toFloat().coerceAtLeast(1f)
        val rednessRatio = redCount / n
        return RoiFeatures(
            rMean = rSum / n,
            gMean = gSum / n,
            bMean = bSum / n,
            sMean = sSum / n,
            vMean = vSum / n,
            rednessRatio = rednessRatio
        )
    }

    /**
     * Pallor inference: pluggable. With no trained weights, use heuristic: low R and low redness = possible pallor.
     * Returns score in [0,1]; higher = more likely possible sign.
     */
    fun pallorInference(features: RoiFeatures): Float {
        val r = features.rMean
        val g = features.gMean
        val redRatio = features.rednessRatio
        if (g < 0.01f) return 0.3f
        val rToG = r / g
        val lowRed = (1f - rToG.coerceIn(0.3f, 1.2f)).coerceIn(0f, 1f)
        val lowRedness = (1f - redRatio).coerceIn(0f, 1f)
        return (lowRed * 0.5f + lowRedness * 0.5f).coerceIn(0f, 1f)
    }

    /**
     * Jaundice index from features: yellow contribution (high H in yellow range, S, V).
     * We already have sMean, vMean; for yellow we need hue. Recompute from ROI if needed, or use a simple proxy.
     * This version uses the original ROI for yellow hue count (caller can pass features + optional hue ratio).
     */
    fun jaundiceIndexFromRoi(roi: Bitmap): Float {
        val w = roi.width
        val h = roi.height
        val pixels = IntArray(w * h)
        roi.getPixels(pixels, 0, w, 0, 0, w, h)
        val hsv = FloatArray(3)
        var yellowSum = 0f
        for (p in pixels) {
            Color.RGBToHSV(Color.red(p), Color.green(p), Color.blue(p), hsv)
            val (h, s, v) = hsv
            if (h in 35f..75f && s > 0.15f && v > 0.2f)
                yellowSum += s * v
        }
        return (yellowSum / pixels.size).coerceIn(0f, 1f)
    }
    /**
     * Cyanosis index from nail bed ROI.
     * Looks for blue/purple hue dominance vs healthy pink (H: 180-270 deg = blue/cyan/purple range).
     * Healthy nail beds: pink (H: 330-360 or 0-20, high S, high V).
     * Returns index in [0,1]; higher = more blue/purple shift = possible cyanosis sign.
     */
    fun cyanosisIndexFromRoi(roi: Bitmap): Float {
        val pixels = IntArray(roi.width * roi.height)
        roi.getPixels(pixels, 0, roi.width, 0, 0, roi.width, roi.height)
        val hsv = FloatArray(3)
        var blueSum = 0f
        var pinkCount = 0
        val n = pixels.size.toFloat().coerceAtLeast(1f)
        for (p in pixels) {
            Color.RGBToHSV(Color.red(p), Color.green(p), Color.blue(p), hsv)
            val (h, s, v) = hsv
            if (v < 0.15f || s < 0.10f) continue  // skip dark/unsaturated pixels (shadows, noise)
            // Blue/purple range: H 180-270
            if (h in 180f..270f && s > 0.20f)
                blueSum += s * v
            // Healthy pink range: H 330-360 or 0-20
            if ((h >= 330f || h <= 20f) && s > 0.15f)
                pinkCount++
        }
        val blueIndex = (blueSum / n).coerceIn(0f, 1f)
        val pinkRatio = (pinkCount / n).coerceIn(0f, 1f)
        // Cyanosis score: weighted blue index penalised by presence of healthy pink
        return (blueIndex * 0.7f + (1f - pinkRatio) * 0.3f).coerceIn(0f, 1f)
    }

    /**
     * Skin lesion index from forearm ROI.
     * Detects abnormal color variance, dark patches, or redness clusters
     * inconsistent with uniform healthy skin tone.
     * Returns index in [0,1]; higher = more abnormal texture/color pattern.
     */
    fun skinLesionIndexFromRoi(roi: Bitmap): Float {
        val pixels = IntArray(roi.width * roi.height)
        roi.getPixels(pixels, 0, roi.width, 0, 0, roi.width, roi.height)
        val n = pixels.size.toFloat().coerceAtLeast(1f)
        val hsv = FloatArray(3)

        // 1. Compute mean hue and saturation for baseline skin tone
        var hSum = 0f; var sSum = 0f; var vSum = 0f
        for (p in pixels) {
            Color.RGBToHSV(Color.red(p), Color.green(p), Color.blue(p), hsv)
            hSum += hsv[0]; sSum += hsv[1]; vSum += hsv[2]
        }
        val hMean = hSum / n
        val sMean = sSum / n
        val vMean = vSum / n

        // 2. Measure abnormality: pixels deviating strongly from the mean in H, S, or V
        var anomalyScore = 0f
        for (p in pixels) {
            Color.RGBToHSV(Color.red(p), Color.green(p), Color.blue(p), hsv)
            val hDev = kotlin.math.abs(hsv[0] - hMean) / 360f
            val sDev = kotlin.math.abs(hsv[1] - sMean)
            val vDev = kotlin.math.abs(hsv[2] - vMean)
            // Strong red clusters (rash/inflammation): H 0-20 or 340-360 with high S
            val redCluster = if ((hsv[0] <= 20f || hsv[0] >= 340f) && hsv[1] > 0.45f && hsv[2] > 0.3f) 0.6f else 0f
            // Dark patches (bruising/marks): very low V against a brighter background
            val darkPatch = if (hsv[2] < 0.25f && vMean > 0.4f) 0.5f else 0f
            // High hue deviation (abnormal color vs baseline skin tone)
            val hueDev = if (hDev > 0.12f) hDev * 0.4f else 0f
            anomalyScore += maxOf(redCluster, darkPatch, hueDev)
        }
        return (anomalyScore / n).coerceIn(0f, 1f)
    }


    // ── IMAGE QUALITY GATE ───────────────────────────────────────────────────

    sealed class QualityCheckResult {
        object Pass : QualityCheckResult()
        data class Fail(val reason: String) : QualityCheckResult()
    }

    fun checkImageQuality(bitmap: Bitmap, testId: String = ""): QualityCheckResult {

        if (bitmap.width < 200 || bitmap.height < 200) {
            return QualityCheckResult.Fail(
                "Image is too small (${bitmap.width}×${bitmap.height}). " +
                        "Please take or upload a higher-resolution photo."
            )
        }

        val sample = Bitmap.createScaledBitmap(bitmap, 200, 200, true)
        val pixels = IntArray(sample.width * sample.height)
        sample.getPixels(pixels, 0, sample.width, 0, 0, sample.width, sample.height)
        val n = pixels.size.toFloat()

        var lumSum = 0f
        for (p in pixels) {
            val r = Color.red(p)
            val g = Color.green(p)
            val b = Color.blue(p)
            lumSum += 0.299f * r + 0.587f * g + 0.114f * b
        }
        val meanLum = lumSum / n
        if (meanLum < 35f) {
            return QualityCheckResult.Fail(
                "Image is too dark. Move to a brighter area or turn on a light and retake the photo."
            )
        }
        if (meanLum > 230f) {
            return QualityCheckResult.Fail(
                "Image is overexposed (too bright). Avoid direct flash or sunlight and retake."
            )
        }

        val grayVals = FloatArray(pixels.size)
        for (i in pixels.indices) {
            val p = pixels[i]
            grayVals[i] = 0.299f * Color.red(p) + 0.587f * Color.green(p) + 0.114f * Color.blue(p)
        }
        val W = sample.width
        val H = sample.height
        var lapVarSum = 0f
        var lapCount = 0
        for (y in 1 until H - 1) {
            for (x in 1 until W - 1) {
                val lap = (
                        grayVals[(y - 1) * W + x] +
                                grayVals[(y + 1) * W + x] +
                                grayVals[y * W + (x - 1)] +
                                grayVals[y * W + (x + 1)] -
                                4f * grayVals[y * W + x]
                        )
                lapVarSum += lap * lap
                lapCount++
            }
        }
        val blurScore = if (lapCount > 0) lapVarSum / lapCount else 0f
        android.util.Log.d("BloodLink-Quality", "blurScore=$blurScore meanLum=$meanLum testId=$testId")

        if (blurScore < 80f) {
            return QualityCheckResult.Fail(
                "Image appears blurry. Hold your phone steady, tap to focus, and retake the photo."
            )
        }

        var lumSumSq = 0f
        for (p in pixels) {
            val lum = 0.299f * Color.red(p) + 0.587f * Color.green(p) + 0.114f * Color.blue(p)
            lumSumSq += (lum - meanLum) * (lum - meanLum)
        }
        val stdDev = sqrt(lumSumSq / n)
        android.util.Log.d("BloodLink-Quality", "stdDev=$stdDev")

        if (stdDev < 8f) {
            return QualityCheckResult.Fail(
                "Image has no detail — it may be a solid color or featureless surface. " +
                        "Please point the camera at the correct area and retake."
            )
        }

        return QualityCheckResult.Pass
    }

}