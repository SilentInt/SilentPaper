package com.silent.paper

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.util.EnumMap

object QrCodes {

    /**
     * ZXing Core 3.5.1 的 Encoder / ECIEncoderSet 使用
     * java.nio.charset.StandardCharsets（Android 4.4 / API 19 才提供）。
     * Android 4.2–4.3（API 17–18）上不能调用 [generate]。
     */
    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= 19

    fun generate(content: String, sizePx: Int): Bitmap {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
        hints[EncodeHintType.MARGIN] = 1
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        for (y in 0 until sizePx) {
            for (x in 0 until sizePx) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }
}
