package cloud.charging.open.dynamicqrcodes

/**
 * The URL template of OCPP 2.1 (Edition 2) use case C25, for example
 * `https://charging.cloud/{evseId}/{totp}`.
 *
 * A Charging Station fills the placeholders in; a CSMS reads them back out of
 * a scanned URL. [extract] is that second direction.
 */
object URLTemplate {

    private val PLACEHOLDER = Regex("""\{(\w+)}""")

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
