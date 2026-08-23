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

/**
 * The URL template of OCPP 2.1 (Edition 2) use case C25, for example
 * `https://charging.cloud/{evseId}/{totp}`.
 *
 * A Charging Station fills the placeholders in; a CSMS reads them back out of
 * a scanned URL. [extract] is that second direction.
 */
object URLTemplate {

    // The closing brace is escaped on purpose: Android matches with ICU, which
    // rejects a bare "}" that the desktop JVM still accepts.
    private val PLACEHOLDER = Regex("""\{(\w+)\}""")

    // A placeholder never spans a path segment or reaches into the query.
    private const val PLACEHOLDER_PATTERN = "([^/?#]+)"

    /**
     * Read the placeholder values of [template] out of [url].
     *
     * @return the placeholder names mapped to the values found in [url], or
     *         null when [url] does not follow [template] at all.
     */
    fun extract(template: String, url: String): Map<String, String>? {

        val names   = mutableListOf<String>()
        val pattern = StringBuilder()
        var index   = 0

        for (placeholder in PLACEHOLDER.findAll(template)) {
            pattern.append(Regex.escape(template.substring(index, placeholder.range.first)))
            pattern.append(PLACEHOLDER_PATTERN)
            names.add(placeholder.groupValues[1])
            index = placeholder.range.last + 1
        }

        pattern.append(Regex.escape(template.substring(index)))

        if (names.isEmpty())
            return null

        val match = Regex(pattern.toString()).matchEntire(url.trim())
                        ?: return null

        return names.withIndex().associate { (i, name) -> name to match.groupValues[i + 1] }

    }

}
