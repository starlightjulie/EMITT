package com.emitt.qr

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer

/** Reads codes from camera frames and images. A new instance per thread: ZXing readers aren't thread-safe. */
class QrDecoder(tryHarder: Boolean = false) {
    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(
                    BarcodeFormat.QR_CODE, BarcodeFormat.DATA_MATRIX, BarcodeFormat.AZTEC, BarcodeFormat.PDF_417,
                    BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E,
                    BarcodeFormat.CODE_128, BarcodeFormat.CODE_39,
                ),
                DecodeHintType.ALSO_INVERTED to true,
                DecodeHintType.CHARACTER_SET to "UTF-8",
            ) + (if (tryHarder) mapOf(DecodeHintType.TRY_HARDER to true) else emptyMap()),
        )
    }

    fun decode(source: LuminanceSource): Result? = try {
        reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
    } catch (_: NotFoundException) {
        null
    } catch (_: Exception) {
        null
    } finally {
        reader.reset()
    }

    fun decode(bitmap: Bitmap): Result? {
        // Big photos slow the binarizer down a lot without helping it
        val scaled = if (maxOf(bitmap.width, bitmap.height) > 1600) {
            val k = 1600f / maxOf(bitmap.width, bitmap.height)
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * k).toInt(), (bitmap.height * k).toInt(), true)
        } else {
            bitmap
        }
        val soft = if (scaled.config == Bitmap.Config.HARDWARE) scaled.copy(Bitmap.Config.ARGB_8888, false) else scaled
        val px = IntArray(soft.width * soft.height)
        soft.getPixels(px, 0, soft.width, 0, 0, soft.width, soft.height)
        return decode(RGBLuminanceSource(soft.width, soft.height, px))
    }

    /** [y] is the luminance plane, [rowStride] bytes per row. */
    fun decodeLuma(y: ByteArray, rowStride: Int, width: Int, height: Int): Result? =
        decode(PlanarYUVLuminanceSource(y, rowStride, height, 0, 0, width, height, false))

    companion object {
        /** Checks a freshly rendered code really scans, the way a phone camera would see it. */
        fun selfTest(bitmap: Bitmap, expected: String): Boolean {
            // Composite transparent codes onto white, like most places they get printed or shown
            val flat = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(flat).apply { drawColor(android.graphics.Color.WHITE); drawBitmap(bitmap, 0f, 0f, null) }
            return QrDecoder(tryHarder = true).decode(flat)?.text == expected
        }
    }
}
