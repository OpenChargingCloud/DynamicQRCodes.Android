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

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The same ground [URLTemplateTest] covers, but executed on a device.
 *
 * Android matches regular expressions with ICU while the local unit tests run
 * against the JVM's own engine, and the two disagree about what is a valid
 * pattern: a bare "}" compiles on the desktop and throws on a phone. Only a
 * test that runs where the app runs can catch that.
 */
@RunWith(AndroidJUnit4::class)
class URLTemplateInstrumentedTest {

    private val template = "https://charging.cloud/{evseId}/{totp}"

    @Test
    fun theTemplatePatternCompilesOnAndroid() {
        val values = URLTemplate.extract(template,
                                         "https://charging.cloud/DE*GEF*E12345*678*1/CN63y502maVh")
        assertEquals("DE*GEF*E12345*678*1", values?.get("evseId"))
        assertEquals("CN63y502maVh",        values?.get("totp"))
    }

    @Test
    fun aForeignUrlDoesNotMatch() {
        assertNull(URLTemplate.extract(template, "https://evil.example.com/EVSE1/CN63y502maVh"))
    }

    @Test
    fun validationRunsOnAndroid() {
        val totp = URLTemplate.extract(template,
                                       "https://charging.cloud/EVSE1/CN63y502maVh")!!["totp"]!!
        assertEquals(TOTPValidation.CURRENT,
                     TOTP.validate(totp, "secure!Charging!", timestamp = 1716423785000L))
    }

}
