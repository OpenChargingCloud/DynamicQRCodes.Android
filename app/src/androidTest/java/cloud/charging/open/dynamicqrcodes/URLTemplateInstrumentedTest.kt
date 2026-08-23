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
