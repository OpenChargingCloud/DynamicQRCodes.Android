/*
 * Copyright (c) 2024-2026 GraphDefined GmbH <achim.friedland@graphdefined.com>
 * This file is part of DynamicQRCodes.Android <https://github.com/OpenChargingCloud/DynamicQRCodes.Android>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cloud.charging.open.dynamicqrcodes

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Renders a URL as the QR code a driver would find on the charging station.
 *
 * The counterpart of [QRCodeAnalyzer], and zxing on both ends - the same
 * library that reads these codes back in also writes them, which is what makes
 * a round trip through the two a test worth having.
 */
object QRCodeGenerator {

    /**
     * The quiet zone the QR standard asks for, in modules.
     *
     * Part of the bitmap rather than of the layout around it: a code whose
     * white border comes from the view behind it stops being scannable the
     * moment somebody screenshots it onto a dark background.
     */
    private const val QUIET_ZONE = 4

    private val writer = QRCodeWriter()

    private val hints = mapOf(

        // A URL is ASCII, so this never changes what is encoded - it only
        // stops zxing from guessing, and from emitting the ECI header that
        // some older scanners cannot read.
        EncodeHintType.CHARACTER_SET    to "UTF-8",

        // Level M still decodes through glare or a fingerprint on the screen,
        // at the price of a slightly denser symbol than the default of L.
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,

        EncodeHintType.MARGIN           to QUIET_ZONE

    )

    /**
     * The module matrix of [contents], one cell per module and the quiet zone
     * included - or null when it cannot be encoded at all, which for a URL
     * means it is longer than the largest QR version can hold.
     *
     * Deliberately free of Android types: this is the part that can be checked
     * against zxing's own decoder in a local unit test.
     */
    fun encode(contents: String): BitMatrix? {

        if (contents.isEmpty())
            return null

        return try {
            // Width and height of zero ask for the natural size - one pixel
            // per module - instead of a scaled rendering.
            writer.encode(contents, BarcodeFormat.QR_CODE, 0, 0, hints)
        } catch (e: WriterException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }

    }

    /**
     * [contents] as a bitmap of exactly one pixel per module. Scaling it up to
     * a size worth looking at is left to the ImageView, which must do that
     * without interpolation - see MainActivity.
     */
    fun bitmap(contents: String): Bitmap? {

        val matrix = encode(contents) ?: return null
        val width  = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)

        // Black on white in both themes. A QR code is defined as dark modules
        // on a light background; inverting it for dark mode would look tidier
        // and decode on fewer devices.
        for (y in 0 until height)
            for (x in 0 until width)
                pixels[y * width + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE

        return createBitmap(width, height).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }

    }

}
