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

import android.graphics.Color
import androidx.core.graphics.get
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The half of [QRCodeGeneratorTest] that needs a device.
 *
 * [QRCodeGenerator.bitmap] turns the module matrix into an Android Bitmap, and
 * a Bitmap is one of the classes a local unit test only gets as a stub. What
 * it adds is a pixel per module in the same orientation - and a transposed
 * loop would mirror the code, which several decoders still read, so a round
 * trip through the reader would not notice.
 */
@RunWith(AndroidJUnit4::class)
class QRCodeGeneratorInstrumentedTest {

    private val url = "https://charging.cloud/DE*GEF*E12345*678*1/oIrlYH6xF8EF"

    @Test
    fun everyModuleBecomesExactlyOnePixel() {

        val matrix = QRCodeGenerator.encode(url)!!
        val bitmap = QRCodeGenerator.bitmap(url)!!

        assertEquals("width",  matrix.width,  bitmap.width)
        assertEquals("height", matrix.height, bitmap.height)

        for (y in 0 until matrix.height)
            for (x in 0 until matrix.width)
                assertEquals("module at $x/$y",
                             if (matrix[x, y]) Color.BLACK else Color.WHITE,
                             bitmap[x, y])

    }

    /**
     * The quiet zone travels inside the bitmap, so its corners are light no
     * matter what the view behind it is painted with.
     */
    @Test
    fun theCornersAreWhite() {

        val bitmap = QRCodeGenerator.bitmap(url)!!

        assertEquals("top left",     Color.WHITE, bitmap[0, 0])
        assertEquals("top right",    Color.WHITE, bitmap[bitmap.width - 1, 0])
        assertEquals("bottom left",  Color.WHITE, bitmap[0, bitmap.height - 1])
        assertEquals("bottom right", Color.WHITE, bitmap[bitmap.width - 1, bitmap.height - 1])

    }

    @Test
    fun emptyContentHasNoBitmap() {
        assertNull(QRCodeGenerator.bitmap(""))
    }

}
