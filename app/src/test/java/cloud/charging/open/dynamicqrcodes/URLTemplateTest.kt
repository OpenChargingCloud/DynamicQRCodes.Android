package cloud.charging.open.dynamicqrcodes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class URLTemplateTest {

    private val template = "https://charging.cloud/{evseId}/{totp}"

    @Test
    fun placeholdersAreExtracted() {
        val values = URLTemplate.extract(template,
                                         "https://charging.cloud/DE*GEF*E12345*678*1/CN63y502maVh")
        assertEquals("DE*GEF*E12345*678*1", values?.get("evseId"))
        assertEquals("CN63y502maVh",        values?.get("totp"))
    }

    /** An EVSE ID is full of regex metacharacters and must stay literal. */
    @Test
    fun literalPartsAreNotTreatedAsRegex() {
        val values = URLTemplate.extract("https://charging.cloud/a.b/{totp}",
                                         "https://charging.cloud/axb/CN63y502maVh")
        assertNull(values)
    }

    @Test
    fun surroundingWhitespaceIsIgnored() {
        val values = URLTemplate.extract(template,
                                         "  https://charging.cloud/EVSE1/CN63y502maVh  ")
        assertEquals("CN63y502maVh", values?.get("totp"))
    }

    @Test
    fun theOcppOrderIsSupportedToo() {
        val values = URLTemplate.extract("https://qr.example.com/{totp}/{version}/{chargingstationid}/",
                                         "https://qr.example.com/aHicux89/v1/CS-001/")
        assertEquals("aHicux89", values?.get("totp"))
        assertEquals("v1",       values?.get("version"))
        assertEquals("CS-001",   values?.get("chargingstationid"))
    }

    @Test
    fun aForeignUrlDoesNotMatch() {
        // The fraud case C25 exists for: a sticker pointing somewhere else.
        assertNull(URLTemplate.extract(template, "https://evil.example.com/EVSE1/CN63y502maVh"))
    }

    @Test
    fun anIncompleteUrlDoesNotMatch() {
        assertNull(URLTemplate.extract(template, "https://charging.cloud/EVSE1"))
        assertNull(URLTemplate.extract(template, "https://charging.cloud/EVSE1/tok/extra"))
        assertNull(URLTemplate.extract(template, ""))
    }

    @Test
    fun aPlaceholderDoesNotSwallowTheQueryString() {
        val values = URLTemplate.extract(template,
                                         "https://charging.cloud/EVSE1/CN63y502maVh?maxenergy=20000")
        assertNull(values)
    }

    @Test
    fun aTemplateWithoutPlaceholdersIsNoTemplate() {
        assertNull(URLTemplate.extract("https://charging.cloud/static",
                                       "https://charging.cloud/static"))
    }

}
