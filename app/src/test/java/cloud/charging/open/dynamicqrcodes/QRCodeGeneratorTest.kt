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

import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.Decoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app writes QR codes and reads them back, and it uses the same library
 * for both - so encoding a URL and decoding the result is a round trip through
 * exactly the two halves that have to agree in the field, where one runs on a
 * charging station and the other in a driver's phone.
 *
 * [QRCodeGenerator.bitmap] is not covered here: it turns a matrix into an
 * Android Bitmap and nothing else, and the matrix is what carries the data.
 */
class QRCodeGeneratorTest {

    private val url = "https://charging.cloud/DE*GEF*E12345*678*1/oIrlYH6xF8EF"

    /**
     * Read a generated matrix back with zxing's own decoder.
     *
     * The decoder wants the symbol alone while the generator surrounds it with
     * the quiet zone; the finder patterns sit in the symbol's corners, so the
     * enclosing rectangle of the dark modules is exactly the symbol.
     */
    private fun decode(matrix: BitMatrix): String {

        val (left, top, width, height) = matrix.enclosingRectangle

        val symbol = BitMatrix(width, height)
        for (y in 0 until height)
            for (x in 0 until width)
                if (matrix[left + x, top + y])
                    symbol.set(x, y)

        return Decoder().decode(symbol).text

    }

    @Test
    fun aGeneratedCodeDecodesBackToTheURL() {
        assertEquals(url, decode(QRCodeGenerator.encode(url)!!))
    }

    /**
     * The default EVSE ID is full of asterisks and the alphabet spans both
     * cases, which pushes zxing out of its alphanumeric mode and into byte
     * mode. Both have to survive the trip.
     */
    @Test
    fun mixedCaseAndPunctuationSurvive() {
        val awkward = "https://CHARGING.cloud/DE*GEF*E12345*678*1/aZ09-_+!\$%&/()=?@#*"
        assertEquals(awkward, decode(QRCodeGenerator.encode(awkward)!!))
    }

    /** Non-ASCII would silently turn into question marks without a charset. */
    @Test
    fun nonAsciiSurvives() {
        val umlauts = "https://ladesäule.example/DE*GEF*E12345*678*1/übermorgen"
        assertEquals(umlauts, decode(QRCodeGenerator.encode(umlauts)!!))
    }

    /**
     * The quiet zone travels inside the bitmap rather than coming from the
     * view behind it - four empty modules on every side, as the standard asks.
     */
    @Test
    fun theQuietZoneIsPartOfTheMatrix() {

        val matrix = QRCodeGenerator.encode(url)!!
        val (left, top, width, height) = matrix.enclosingRectangle

        assertEquals("quiet zone on the left",   4, left)
        assertEquals("quiet zone at the top",    4, top)
        assertEquals("quiet zone on the right",  4, matrix.width  - (left + width))
        assertEquals("quiet zone at the bottom", 4, matrix.height - (top  + height))

    }

    /** One pixel per module: the scaling is the ImageView's job. */
    @Test
    fun theMatrixIsNotScaledUp() {

        val matrix = QRCodeGenerator.encode(url)!!

        // A QR symbol is 4 * version + 17 modules square, plus the quiet zone
        // on both sides. Anything larger means somebody asked for a rendering.
        assertEquals("a QR code is square", matrix.width, matrix.height)
        assertTrue("unexpected size: ${matrix.width}",
                   (matrix.width - 8 - 17) % 4 == 0 && matrix.width < 60)

    }

    @Test
    fun emptyContentHasNoCode() {
        assertNull(QRCodeGenerator.encode(""))
    }

    /**
     * A URL longer than the largest QR version can hold is reported rather
     * than thrown: MainActivity leaves the text standing on its own for it.
     */
    @Test
    fun contentTooLongHasNoCode() {
        assertNull(QRCodeGenerator.encode("https://charging.cloud/" + "x".repeat(3000)))
    }

    /** The boundary the case above is on the far side of. */
    @Test
    fun aLongButEncodableURLStillWorks() {
        val long = "https://charging.cloud/" + "x".repeat(200)
        assertNotNull(QRCodeGenerator.encode(long))
        assertEquals(long, decode(QRCodeGenerator.encode(long)!!))
    }

}
