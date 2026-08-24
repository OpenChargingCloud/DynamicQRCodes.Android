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
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * [TOTPTest] against the vectors as *currently published*, rather than against
 * the copies pinned in this repository.
 *
 * The distinction matters for a case the id comparison in vector-drift.yml
 * cannot see: a vector whose id stays but whose expected output changes,
 * because the specification corrected it. Comparing id sets would call that
 * "no drift" and be wrong in the one direction that produces tokens a CSMS
 * rejects - which is exactly the class of bug this app already shipped once.
 *
 * Note what it compares: the upstream expectations against what [TOTP]
 * *computes*, not against the literals in [TOTPTest]. Those literals are
 * covered by [TOTPTest] itself, so when both suites are green the pinned
 * copies equal the published originals by transitivity, and neither test has
 * to know about the other's data.
 *
 * The vector file is not in this repository and this test does not fetch it -
 * a unit test that needs the network is a unit test that fails on a train.
 * .github/workflows/vector-drift.yml downloads the published vectors, converts
 * the ones that conformance-vector-scope.txt marks "covered" into
 * upstream-generation-vectors.tsv, and drops it onto the test classpath before
 * running Gradle. Without that file every case below reports as skipped, which
 * is what happens in a normal build and on every developer machine.
 */
class UpstreamVectorTest {

    private companion object {
        const val RESOURCE = "/upstream-generation-vectors.tsv"
    }

    /**
     * Tab separated, in the column order the workflow's jq filter emits:
     *
     *   id, sharedSecret, unixTimestampMillis, validityTimeSeconds,
     *   totpLength, alphabet, previous, current, next, remainingTimeSeconds
     *
     * An empty field means "not specified in the vector", so the default
     * applies. Fields are deliberately *not* trimmed: two vectors surround the
     * shared secret and the alphabet with spaces on purpose, to pin down that
     * the implementation trims them itself. Trimming here would turn both into
     * tautologies. The vector data contains no tabs, which is what makes a
     * separator this simple safe; it does contain non-ASCII (secret-utf8), so
     * the file is read as UTF-8.
     */
    private fun readVectors(): List<List<String>>? =

        javaClass.getResourceAsStream(RESOURCE)
                 ?.bufferedReader()
                 ?.useLines { lines ->
                     lines.filter { it.isNotBlank() }
                          .map    { it.split('\t') }
                          .toList()
                 }

    @Test
    fun publishedVectorsStillMatch() {

        val vectors = readVectors()

        assumeTrue("$RESOURCE is not on the classpath - see the class documentation",
                   vectors != null)

        // An empty file would satisfy every assertion below and report green,
        // so the count is part of the verdict: whatever the workflow wrote, it
        // has to have written something.
        assertEquals("$RESOURCE is present but empty", true, vectors!!.isNotEmpty())

        for (fields in vectors) {

            assertEquals("malformed line: " + fields.joinToString(" | "), 10, fields.size)

            val id     = fields[0]
            val result = TOTP.generateTOTPs(fields[1],
                                            fields[3].ifEmpty { null }?.toUInt(),
                                            fields[4].ifEmpty { null }?.toUInt(),
                                            fields[5].ifEmpty { null },
                                            fields[2].toLong())

            assertEquals("$id: previous",      fields[6],            result.previous)
            assertEquals("$id: current",       fields[7],            result.current)
            assertEquals("$id: next",          fields[8],            result.next)
            assertEquals("$id: remainingTime", fields[9].toULong(),  result.remainingTime)

        }

    }

}
