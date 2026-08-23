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

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

/**
 * Looks for a QR code in each camera frame and reports the first one found.
 *
 * The decoder is zxing, which is maintained and which the conformance tests
 * already rely on; only the camera side is CameraX. [onFound] is called once
 * and then never again, so the caller can shut the camera down in peace.
 */
class QRCodeAnalyzer(private val onFound: (String) -> Unit) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(com.google.zxing.BarcodeFormat.QR_CODE)))
    }

    private var found = false

    override fun analyze(image: ImageProxy) {

        if (found) {
            image.close()
            return
        }

        try {
            decode(image)?.let {
                found = true
                onFound(it)
            }
        } finally {
            image.close()
        }

    }

    private fun decode(image: ImageProxy): String? {

        // The luminance plane alone carries everything a QR code needs, so the
        // colour planes are left untouched. Its rows can be wider than the
        // image, which is why the stride rather than the width describes the
        // data. Rotation is irrelevant: a QR code reads from any angle.
        val plane  = image.planes[0]
        val buffer = plane.buffer
        val data   = ByteArray(buffer.remaining())
        buffer.get(data)

        val source = PlanarYUVLuminanceSource(data,
                                              plane.rowStride,
                                              image.height,
                                              0,
                                              0,
                                              image.width,
                                              image.height,
                                              false)

        return try {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
        } catch (e: NotFoundException) {
            // No QR code in this frame, which is the normal case.
            null
        } finally {
            reader.reset()
        }

    }

}
