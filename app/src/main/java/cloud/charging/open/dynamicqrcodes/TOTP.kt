package cloud.charging.open.dynamicqrcodes

import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Time-based one-time passwords as defined by OCPP 2.1 (Edition 2),
 * use case C25 "Ad hoc payment via a QR code", TOTP algorithm version 1.
 *
 * Free of Android dependencies, so that [generateTOTPs] can be verified against
 * the conformance test vectors shared by the other implementations.
 */
object TOTP {

    private const val DEFAULT_VALIDITY_TIME  = 30
    private const val DEFAULT_TOTP_LENGTH    = 12
    private const val DEFAULT_ALPHABET       = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

    private fun calcTOTPSlot(slot:          Long,
                             totpLength:    Int,
                             alphabet:      String,
                             sharedSecret:  String): String {

        // The time interval is hashed big-endian on every platform: both peers
        // have to agree on these bytes, and OCPP C25 defines them as the
        // reversed byte order of a little-endian machine.
        val slotBytes    = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(slot).array()

        val hmac         = Mac.getInstance("HmacSHA256")
        hmac.init(SecretKeySpec(sharedSecret.toByteArray(Charsets.UTF_8), "HmacSHA256"))

        val currentHash  = hmac.doFinal(slotBytes)
        val offset       = currentHash[currentHash.size - 1].toInt() and 0x0F
        val result       = StringBuilder(totpLength)

        for (i in 0 until totpLength)
            result.append(alphabet[(currentHash[(offset + i) % currentHash.size].toInt() and 0xFF) % alphabet.length])

        return result.toString()

    }

    /**
     * Calculate the TOTPs of the previous, the current and the next time slot,
     * along with the remaining time of the current slot.
     *
     * @throws IllegalArgumentException  when a parameter violates the algorithm constraints.
     */
    fun generateTOTPs(sharedSecret:  String,
                      validityTime:  UInt?     = null,
                      totpLength:    UInt?     = null,
                      alphabet:      String?   = null,
                      timestamp:     Long?     = null): TOTPResult {

        val sharedSecretLocal  =  sharedSecret.trim()
        // An out-of-range UInt overflows into a negative Int and is rejected below.
        val validityTimeLocal  =  validityTime?.toInt() ?: DEFAULT_VALIDITY_TIME
        val totpLengthLocal    =  totpLength?.  toInt() ?: DEFAULT_TOTP_LENGTH
        val alphabetLocal      =  alphabet?.    trim()  ?: DEFAULT_ALPHABET
        val timestampLocal     =  timestamp ?: System.currentTimeMillis()

        require(sharedSecretLocal.isNotEmpty())                     { "The given shared secret must not be null or empty!" }
        require(!sharedSecretLocal.contains("\\s".toRegex()))       { "The given shared secret must not contain any whitespace characters!" }
        require(sharedSecretLocal.length >= 16)                     { "The length of the given shared secret must be at least 16 characters!" }
        require(validityTimeLocal > 0)                              { "The validity time must be a positive integer number of seconds!" }
        require(totpLengthLocal in 4..255)                          { "The expected length of the TOTP must be between 4 and 255 characters!" }
        require(timestampLocal >= 0)                                { "The timestamp must be a non-negative Unix timestamp in milliseconds!" }
        require(alphabetLocal.isNotEmpty())                         { "The given alphabet must not be null or empty!" }
        require(alphabetLocal.length >= 4)                          { "The given alphabet must contain at least 4 characters!" }
        require(alphabetLocal.toSet().size == alphabetLocal.length) { "The given alphabet must not contain duplicate characters!" }
        require(!alphabetLocal.contains("\\s".toRegex()))           { "The given alphabet must not contain any whitespace characters!" }

        val currentUnixTime    = timestampLocal / 1000
        val currentSlot        = currentUnixTime / validityTimeLocal
        val remainingTime      = validityTimeLocal - (currentUnixTime % validityTimeLocal)

        return TOTPResult(
                   calcTOTPSlot(currentSlot - 1, totpLengthLocal, alphabetLocal, sharedSecretLocal),
                   calcTOTPSlot(currentSlot,     totpLengthLocal, alphabetLocal, sharedSecretLocal),
                   calcTOTPSlot(currentSlot + 1, totpLengthLocal, alphabetLocal, sharedSecretLocal),
                   remainingTime.toULong()
               )

    }

}
