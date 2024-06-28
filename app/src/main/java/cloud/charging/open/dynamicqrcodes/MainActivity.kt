package cloud.charging.open.dynamicqrcodes

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
//import androidx.navigation.ui.AppBarConfiguration
import cloud.charging.open.dynamicqrcodes.databinding.ActivityMainBinding
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class MainActivity : AppCompatActivity() {

    //private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding:             ActivityMainBinding

    private lateinit var autoUpdateSwitch: SwitchMaterial
    private lateinit var timestamp: TextInputEditText
    private val handler         = Handler(Looper.getMainLooper())
    private val updateInterval  = 1000L // 1 second

    private val updateTimestampTask = object : Runnable {
        override fun run() {
            if (autoUpdateSwitch.isChecked) {
                val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                //val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
                //dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                //return dateFormat.format(Date())
                timestamp.setText(currentTime)
                handler.postDelayed(this, updateInterval)
            }
        }
    }

    private val barcodeLauncher = registerForActivityResult(ScanContract()) {

        result: ScanIntentResult ->

            if (result.contents == null) {
                val originalIntent = result.originalIntent
                if (originalIntent == null) {
                    Log.d("MainActivity", "Cancelled scan")
                    Toast.makeText(this@MainActivity, "Cancelled", Toast.LENGTH_LONG)
                        .show()
                } else if (originalIntent.hasExtra(Intents.Scan.MISSING_CAMERA_PERMISSION)) {
                    Log.d(
                        "MainActivity",
                        "Cancelled scan due to missing camera permission"
                    )
                    Toast.makeText(
                        this@MainActivity,
                        "Cancelled due to missing camera permission",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } else {
                Log.d("MainActivity", "Scanned")
                Toast.makeText(
                    this@MainActivity,
                    "Scanned: " + result.contents,
                    Toast.LENGTH_LONG
                ).show()
            }

    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

            timestamp         = findViewById(R.id.timestamp)
            autoUpdateSwitch  = findViewById(R.id.autoUpdateSwitch)
        val urlTemplate       = findViewById<EditText>      (R.id.urlTemplate)
        val evseId            = findViewById<EditText>      (R.id.evseId)
        val sharedSecret      = findViewById<EditText>      (R.id.sharedSecret)
        val validityTime      = findViewById<EditText>      (R.id.validityTime)
        val totpLength        = findViewById<EditText>      (R.id.totpLength)
        val alphabet          = findViewById<EditText>      (R.id.alphabet)

        val resultingURL      = findViewById<TextView>      (R.id.resultingURL)
        val remainingTime     = findViewById<TextView>      (R.id.remainingTime)

        autoUpdateSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                handler.post(updateTimestampTask)
            } else {
                handler.removeCallbacks(updateTimestampTask)
            }
        }

        autoUpdateSwitch.isChecked = true

        timestamp.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                // Deactivate the autoUpdateSwitch
                autoUpdateSwitch.isChecked = false
                // Remove any pending update tasks
                handler.removeCallbacks(updateTimestampTask)
                // Call performClick to ensure proper accessibility behavior
                view.performClick()
            }
            false
        }

        timestamp.setOnClickListener {
            // Handle click event if needed
        }

        urlTemplate. setText(R.string.defaultURLTemplate)
        evseId.      setText(R.string.defaultEVSEId)
        sharedSecret.setText(generateRandomString(16))
        validityTime.setText(R.string.defaultValidityTime)
        totpLength.  setText(R.string.defaultTOTPLength)
        alphabet.    setText(R.string.defaultAlphabet)

        val textWatcher = object : TextWatcher {

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

                val timestampText = timestamp.text.toString()

                if (timestampText != "")
                {

                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val date: Date = dateFormat.parse(timestamp.text.toString()) ?: throw IllegalArgumentException("Invalid date format!")

                    val validityTimeUInt: UInt = try {
                        validityTime.text.toString().toUInt()
                    } catch (e: NumberFormatException) {
                        throw IllegalArgumentException("Invalid validity time format!")
                    }

                    val totpLengthUInt: UInt = try {
                        totpLength.text.toString().toUInt()
                    } catch (e: NumberFormatException) {
                        throw IllegalArgumentException("Invalid totp length format!")
                    }

                    val totp            = generateTOTPs(sharedSecret.text.toString(),
                                                        validityTimeUInt,
                                                        totpLengthUInt,
                                                        alphabet.text.toString(),
                                                        date.time)

                    var updatedUrl      = urlTemplate.text.toString()
                    updatedUrl          = updatedUrl.replace("{evseId}", evseId.text.toString())
                    updatedUrl          = updatedUrl.replace("{totp}",   totp.current)

                    resultingURL. text  = updatedUrl
                    remainingTime.text  = getString(R.string.remaining_time, totp.remainingTime.toLong())

                }

            }

            override fun afterTextChanged(s: Editable?) { }

        }

        sharedSecret.addTextChangedListener(textWatcher)
        evseId.      addTextChangedListener(textWatcher)
        timestamp.   addTextChangedListener(textWatcher)

        // Manually trigger the onTextChanged event
        textWatcher.onTextChanged(null,0,0,0)

        binding.scanButton.setOnClickListener {
            val options = ScanOptions()
            options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            options.setPrompt("Scan a QR code")
            options.setCameraId(0) // Use a specific camera of the device
            options.setBeepEnabled(true)
            options.setBarcodeImageEnabled(true)
            barcodeLauncher.launch(options)
        }

    }

    private fun generateRandomString(length: Int): String {
        val allowedChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_+!$%&/()=?@#*"
        return (1..length)
            .map { allowedChars.random() }
            .joinToString("")
    }

    private fun isLittleEndian(): Boolean {
        return ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN
    }

    private fun reverseBytes(bytes: ByteArray) {
        bytes.reverse()
    }

    private fun calcTOTPSlot(slotBytes:     ByteArray,
                             totpLength:    Int,
                             alphabet:      String,
                             sharedSecret:  String): String {

        if (isLittleEndian())
            reverseBytes(slotBytes)

        val hmac         = Mac.getInstance("HmacSHA256")
        val keySpec      = SecretKeySpec(sharedSecret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        hmac.init(keySpec)
        val currentHash  = hmac.doFinal(slotBytes)
        val offset       = currentHash[currentHash.size - 1].toInt() and 0x0F
        val result       = StringBuilder()

        for (i in 0 until totpLength)
            result.append(alphabet[(currentHash[(offset + i) % currentHash.size].toInt() and 0xFF) % alphabet.length])

        return result.toString()

    }

    private fun generateTOTPs(sharedSecret:  String,
                              validityTime:  UInt?     = null,
                              totpLength:    UInt?     = null,
                              alphabet:      String?   = null,
                              timestamp:     Long?     = null
    ): TOTPResult {

        val sharedSecretLocal  =  sharedSecret.trim()
        val validityTimeLocal  = (validityTime        ?: 30U).toInt()
        val totpLengthLocal    = (totpLength          ?: 12U).toInt()
        val alphabetLocal      =  alphabet?.   trim() ?: "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val currentUnixTime    = (timestamp           ?: System.currentTimeMillis()) / 1000

        require(sharedSecretLocal.isNotEmpty())                           { "The given shared secret must not be null or empty!" }
        require(!sharedSecretLocal.contains("\\s".toRegex()))             { "The given shared secret must not contain any whitespace characters!" }
        require(sharedSecretLocal.length >= 16)                     { "The length of the given shared secret must be at least 16 characters!" }
        require(totpLengthLocal in 4..255)                    { "The expected length of the TOTP must be between 4 and 255 characters!" }
        require(alphabetLocal.isNotEmpty())                               { "The given alphabet must not be null or empty!" }
        require(alphabetLocal.length >= 4)                          { "The given alphabet must contain at least 4 characters!" }
        require(alphabetLocal.toSet().size == alphabetLocal.length) { "The given alphabet must not contain duplicate characters!" }
        require(!alphabetLocal.contains("\\s".toRegex()))                 { "The given alphabet must not contain any whitespace characters!" }

        val currentSlot        = currentUnixTime / validityTimeLocal
        val remainingTime      = validityTimeLocal - (currentUnixTime % validityTimeLocal)

        // For interoperability we use 8 byte timestamps
        val previousSlotBytes  = ByteBuffer.allocate(8).putLong(currentSlot - 1).array()
        val currentSlotBytes   = ByteBuffer.allocate(8).putLong(currentSlot).array()
        val nextSlotBytes      = ByteBuffer.allocate(8).putLong(currentSlot + 1).array()

        val previous           = calcTOTPSlot(previousSlotBytes, totpLengthLocal, alphabetLocal, sharedSecretLocal)
        val current            = calcTOTPSlot(currentSlotBytes,  totpLengthLocal, alphabetLocal, sharedSecretLocal)
        val next               = calcTOTPSlot(nextSlotBytes,     totpLengthLocal, alphabetLocal, sharedSecretLocal)

        return TOTPResult(previous,
                          current,
                          next,
                          remainingTime.toULong())

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        //val navController = findNavController(R.id.nav_host_fragment_content_main)
        //return navController.navigateUp(appBarConfiguration)
        //        || super.onSupportNavigateUp()
        return true
    }

}