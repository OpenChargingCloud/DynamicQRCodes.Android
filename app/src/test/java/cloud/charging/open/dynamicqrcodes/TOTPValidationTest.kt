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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validation as a CSMS performs it, checked against the same conformance
 * vector the generation tests use: shared secret "secure!Charging!" at
 * 2024-05-22T22:23:05Z with the default parameters.
 */
class TOTPValidationTest {

    private val secret    = "secure!Charging!"
    private val timestamp = 1716423785000L

    private val previous  = "MdPU0jCm5tXz"
    private val current   = "CN63y502maVh"
    private val next      = "dI54vnA25m2h"

    private fun validate(totp: String) =
        TOTP.validate(totp, secret, timestamp = timestamp)

    @Test
    fun currentSlotIsAccepted() {
        assertEquals(TOTPValidation.CURRENT, validate(current))
    }

    /** OCPP 2.1 C25 has the CSMS accept both neighbouring slots. */
    @Test
    fun neighbouringSlotsAreAccepted() {
        assertEquals(TOTPValidation.PREVIOUS, validate(previous))
        assertEquals(TOTPValidation.NEXT,     validate(next))
    }

    @Test
    fun allThreeSlotsCountAsValid() {
        assertTrue(validate(previous).isValid)
        assertTrue(validate(current).isValid)
        assertTrue(validate(next).isValid)
        assertFalse(TOTPValidation.INVALID.isValid)
    }

    @Test
    fun theSlotBeforePreviousIsRejected() {
        // One slot further out than the tolerance: still a well-formed token,
        // just too old.
        val tooOld = TOTP.generateTOTPs(secret, timestamp = timestamp - 60_000).previous
        assertEquals(TOTPValidation.INVALID, validate(tooOld))
    }

    @Test
    fun aDifferentSecretIsRejected() {
        assertEquals(TOTPValidation.INVALID,
                     TOTP.validate(current, "insecure!Charging", timestamp = timestamp))
    }

    @Test
    fun garbageIsRejected() {
        assertEquals(TOTPValidation.INVALID, validate(""))
        assertEquals(TOTPValidation.INVALID, validate("000000000000"))
        assertEquals(TOTPValidation.INVALID, validate(current.dropLast(1)))
        assertEquals(TOTPValidation.INVALID, validate(current + "x"))
    }

    @Test
    fun comparisonIsCaseSensitive() {
        assertEquals(TOTPValidation.INVALID, validate(current.lowercase()))
        assertEquals(TOTPValidation.INVALID, validate(current.uppercase()))
    }

    @Test
    fun parametersMustMatchTheGenerator() {
        // Right secret, right moment, but the token was made with other settings.
        assertEquals(TOTPValidation.INVALID,
                     TOTP.validate(current, secret, validityTime = 60U, timestamp = timestamp))
        assertEquals(TOTPValidation.INVALID,
                     TOTP.validate(current, secret, totpLength = 8U, timestamp = timestamp))
        assertEquals(TOTPValidation.INVALID,
                     TOTP.validate(current, secret, alphabet = "0123456789", timestamp = timestamp))
    }

}
