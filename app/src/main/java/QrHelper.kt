package com.example.bloodlink

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Encode/decode strings (e.g. screening ID) as QR code.
 * - encodeToBitmap: for applicant to show QR to staff.
 * - decodeFromBitmap: for staff to scan from camera frame or uploaded image.
 */
object QrHelper {

    fun encodeToBitmap(content: String, sizePx: Int = 256): Bitmap? {
        return try {
            val hints = mapOf(EncodeHintType.CHARACTER_SET to "UTF-8")
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    pixels[y * width + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                }
            }
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Decode QR code from a bitmap (e.g. from gallery). Returns the raw string or null.
     */
    fun decodeFromBitmap(bitmap: Bitmap): String? {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val reader = MultiFormatReader()
            reader.setHints(mapOf(DecodeHintType.PURE_BARCODE to true))
            val result = reader.decodeWithState(binaryBitmap)
            result.text
        } catch (e: NotFoundException) {
            null
        } catch (e: Exception) {
            null
        }
    }
}
