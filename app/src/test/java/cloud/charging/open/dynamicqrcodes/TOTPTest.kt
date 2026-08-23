package cloud.charging.open.dynamicqrcodes

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/**
 * Conformance vectors of the "Open Charging Cloud TOTP Token Format 1.0",
 * shared with the other implementations of this algorithm and generated from
 * TOTP.ts, test/vectors/. Only the HMAC-SHA256 vectors apply here, as [TOTP]
 * implements OCPP 2.1 C25 TOTP version 1 only.
 */
class TOTPTest {

    private data class Vector(
        val id:             String,
        val sharedSecret:   String,
        val timestamp:      Long,
        val validityTime:   UInt?,
        val totpLength:     UInt?,
        val alphabet:       String?,
        val previous:       String,
        val current:        String,
        val next:           String,
        val remainingTime:  ULong
    )

    private data class Invalid(
        val id:             String,
        val sharedSecret:   String,
        val timestamp:      Long,
        val validityTime:   UInt?,
        val totpLength:     UInt?,
        val alphabet:       String?,
        val expectedError:  String
    )

    private val vectors = listOf(
        Vector("defaults-slot-boundary",
               "secure!Charging!", 1718611200000L, 30U, 12U, null,
               "QT1cCdKsIb9e", "akF3c7qY2uiu", "1U70OgaBA48M", 30UL),
        Vector("defaults-mid-slot",
               "secure!Charging!", 1716423785000L, null, null, null,
               "MdPU0jCm5tXz", "CN63y502maVh", "dI54vnA25m2h", 25UL),
        Vector("length-23",
               "secure!Charging!", 1716423785000L, null, 23U, null,
               "MdPU0jCm5tXzkaPrPj61KwI", "CN63y502maVhAsv27Sd7JlE", "dI54vnA25m2hWW3bUcdY13q", 25UL),
        Vector("alphabet-decimal",
               "secure!Charging!", 1716423785000L, null, null, "0123456789",
               "233045043555", "894361286613", "545817627227", 25UL),
        Vector("validity-60",
               "secure!Charging!", 1716423785000L, 60U, null, null,
               "nTdkiuG6yUyg", "XJZr0L1DGKn0", "ft0ONZ62MdMj", 55UL),
        Vector("epoch-previous-slot-wraps",
               "secure!Charging!", 0L, 30U, 12U, null,
               "SzcwtcR5qcY7", "u5CoKdo5HUS1", "tVGiyLys7Y1V", 30UL),
        Vector("length-64-sha256-ring-buffer",
               "secure!Charging!", 1718611200000L, 30U, 64U, null,
               "QT1cCdKsIb9e1gSulDAx9PYnnkX3e9h0QT1cCdKsIb9e1gSulDAx9PYnnkX3e9h0", "akF3c7qY2uiuO4rpyU0SC0W8VFE6nvxzakF3c7qY2uiuO4rpyU0SC0W8VFE6nvxz", "1U70OgaBA48Mul8hQCdP4MZ0eskSGKY31U70OgaBA48Mul8hQCdP4MZ0eskSGKY3", 30UL),
        Vector("length-6-decimal",
               "secure!Charging!", 1718611200000L, 30U, 6U, "0123456789",
               "277289", "441749", "127406", 30UL),
        Vector("length-min-4",
               "secure!Charging!", 1716423785000L, null, 4U, null,
               "MdPU", "CN63", "dI54", 25UL),
        Vector("length-33-sha256",
               "secure!Charging!", 1716423785000L, null, 33U, null,
               "MdPU0jCm5tXzkaPrPj61KwIMNKAF7gMtM", "CN63y502maVhAsv27Sd7JlErFxtg9tAUC", "dI54vnA25m2hWW3bUcdY13qmNkNzzB06d", 25UL),
        Vector("alphabet-min-4",
               "secure!Charging!", 1716423785000L, null, null, "ACGT",
               "ACCAACAACTCT", "GCGTATGAGACT", "TACATTAGTGGC", 25UL),
        Vector("alphabet-hex",
               "secure!Charging!", 1716423785000L, null, null, "0123456789abcdef",
               "cd14cd44d753", "61e30fec245b", "bc50f3423621", 25UL),
        Vector("alphabet-base32",
               "secure!Charging!", 1716423785000L, null, null, "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567",
               "MNRU4NEU5XVD", "GR6DA764SEVL", "LMFA7TECDWCR", 25UL),
        Vector("alphabet-base64url",
               "secure!Charging!", 1716423785000L, null, null, "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ-_",
               "IdNQYdAkZnRz", "CN-3w_-Yi4Rb", "bI50vjA23m2h", 25UL),
        Vector("validity-1",
               "secure!Charging!", 1716423785000L, 1U, null, null,
               "4tCOMnYC1jyO", "QypcM5hxJ94T", "NaZqaahbFnb0", 1UL),
        Vector("validity-5",
               "secure!Charging!", 1716423785000L, 5U, null, null,
               "LYhXalw0w2O5", "3bp8qljbQ6Th", "ZELD377HHJpx", 5UL),
        Vector("validity-7",
               "secure!Charging!", 1716423785000L, 7U, null, null,
               "9tmY7YPiGS9k", "RpYIMd0IlQCZ", "ihflnOIQeXKG", 1UL),
        Vector("validity-86400",
               "secure!Charging!", 1716423785000L, 86400U, null, null,
               "YRo0d3AAG9h3", "FRmyHBzwle4I", "qr4tp5AYFetR", 85015UL),
        Vector("remaining-1-second",
               "secure!Charging!", 1716423779000L, null, null, null,
               "nl0iABVo6jRY", "MdPU0jCm5tXz", "CN63y502maVh", 1UL),
        Vector("milliseconds-floor",
               "secure!Charging!", 1716423785999L, null, null, null,
               "MdPU0jCm5tXz", "CN63y502maVh", "dI54vnA25m2h", 25UL),
        Vector("milliseconds-floor-slot-edge",
               "secure!Charging!", 1716423809999L, null, null, null,
               "MdPU0jCm5tXz", "CN63y502maVh", "dI54vnA25m2h", 1UL),
        Vector("timestamp-y2038",
               "secure!Charging!", 2147483647000L, null, null, null,
               "jf6k3NFpulnR", "cSNjvhC7U5UN", "ozVde74ViFzJ", 23UL),
        Vector("timestamp-year-9999",
               "secure!Charging!", 253402300768000L, null, null, null,
               "nYAGviXNwhe0", "9ko7CWj5OI4E", "qt0uZS0w35J0", 2UL),
        Vector("secret-utf8",
               "s\u00e9c\u00fcr\u00e8!Ch\u00e4rg\u00eeng!", 1716423785000L, null, null, null,
               "2z9YFjk3E4Cs", "SLuwWQOYh3g0", "gG7F7AI3znvY", 25UL),
        Vector("secret-64-chars",
               "0ZPatRVe1DTLBHRipD5cyOU9d1TCsdLLYhtLXGajUATuOwaVSVFPnbUAJyTFrPFI", 1716423785000L, null, null, null,
               "zxI4teacfH85", "9Sk0VMKOc9nx", "WOdiJz2UlhES", 25UL),
        Vector("secret-surrounding-whitespace",
               "  secure!Charging!  ", 1716423785000L, null, null, null,
               "MdPU0jCm5tXz", "CN63y502maVh", "dI54vnA25m2h", 25UL),
        Vector("alphabet-surrounding-whitespace",
               "secure!Charging!", 1716423785000L, null, null, " 0123456789 ",
               "233045043555", "894361286613", "545817627227", 25UL),
        Vector("ocpp-v1-length-8",
               "secure!Charging!", 1716423785000L, 30U, 8U, null,
               "MdPU0jCm", "CN63y502", "dI54vnA2", 25UL),
        Vector("ocpp-v1-min-profile",
               "secure!Charging!", 1716423785000L, 3600U, 6U, null,
               "kDDM3V", "Ik0dNs", "7eNFbx", 2215UL),
    )

    private val invalidInputs = listOf(
        Invalid("secret-empty",
                "", 1716423785000L, null, null, null,
                "The given shared secret must not be null or empty!"),
        Invalid("secret-whitespace-only",
                "        ", 1716423785000L, null, null, null,
                "The given shared secret must not be null or empty!"),
        Invalid("secret-too-short",
                "secure!Charging", 1716423785000L, null, null, null,
                "The length of the given shared secret must be at least 16 characters!"),
        Invalid("secret-inner-whitespace",
                "secure Charging!", 1716423785000L, null, null, null,
                "The given shared secret must not contain any whitespace characters!"),
        Invalid("length-3",
                "secure!Charging!", 1716423785000L, null, 3U, null,
                "The expected length of the TOTP must be between 4 and 255 characters!"),
        Invalid("length-256",
                "secure!Charging!", 1716423785000L, null, 256U, null,
                "The expected length of the TOTP must be between 4 and 255 characters!"),
        Invalid("validity-zero",
                "secure!Charging!", 1716423785000L, 0U, null, null,
                "The validity time must be a positive integer number of seconds!"),
        Invalid("timestamp-negative",
                "secure!Charging!", -1000L, null, null, null,
                "The timestamp must be a non-negative Unix timestamp in milliseconds!"),
        Invalid("alphabet-empty",
                "secure!Charging!", 1716423785000L, null, null, "",
                "The given alphabet must not be null or empty!"),
        Invalid("alphabet-whitespace-only",
                "secure!Charging!", 1716423785000L, null, null, "   ",
                "The given alphabet must not be null or empty!"),
        Invalid("alphabet-3-chars",
                "secure!Charging!", 1716423785000L, null, null, "abc",
                "The given alphabet must contain at least 4 characters!"),
        Invalid("alphabet-duplicates",
                "secure!Charging!", 1716423785000L, null, null, "abcdeff",
                "The given alphabet must not contain duplicate characters!"),
        Invalid("alphabet-inner-whitespace",
                "secure!Charging!", 1716423785000L, null, null, "ab cdef",
                "The given alphabet must not contain any whitespace characters!"),
    )

    @Test
    fun conformanceVectors() {

        for (vector in vectors) {

            val result = TOTP.generateTOTPs(vector.sharedSecret,
                                            vector.validityTime,
                                            vector.totpLength,
                                            vector.alphabet,
                                            vector.timestamp)

            assertEquals(vector.id + ": previous",      vector.previous,      result.previous)
            assertEquals(vector.id + ": current",       vector.current,       result.current)
            assertEquals(vector.id + ": next",          vector.next,          result.next)
            assertEquals(vector.id + ": remainingTime", vector.remainingTime, result.remainingTime)

        }

    }

    @Test
    fun invalidInputsAreRejected() {

        for (invalid in invalidInputs) {

            val error = try {

                TOTP.generateTOTPs(invalid.sharedSecret,
                                   invalid.validityTime,
                                   invalid.totpLength,
                                   invalid.alphabet,
                                   invalid.timestamp)

                null

            } catch (e: IllegalArgumentException) {
                e.message
            }

            if (error == null)
                fail(invalid.id + ": expected an IllegalArgumentException!")

            assertEquals(invalid.id, invalid.expectedError, error)

        }

    }

}
