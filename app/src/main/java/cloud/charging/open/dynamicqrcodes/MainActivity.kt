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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import cloud.charging.open.dynamicqrcodes.databinding.ActivityMainBinding
import com.google.android.material.color.MaterialColors
import com.google.zxing.client.android.Intents
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import java.security.SecureRandom
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val handler          = Handler(Looper.getMainLooper())
    private val updateInterval   = 1000L // 1 second
    private val secureRandom     = SecureRandom()

    private val timestampFormat  = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).apply {
        // Reject "2024-99-99" instead of rolling it over into a valid date.
        isLenient = false
    }

    private var resultColor      = 0
    private val errorColor by lazy {
        MaterialColors.getColor(binding.resultingURL, androidx.appcompat.R.attr.colorError)
    }

    private val updateTimestampTask = object : Runnable {
        override fun run() {
            if (binding.autoUpdateSwitch.isChecked) {
                binding.timestamp.setText(timestampFormat.format(Date()))
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

        resultColor = binding.resultingURL.currentTextColor

        binding.autoUpdateSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked)
                handler.post(updateTimestampTask)
            else
                handler.removeCallbacks(updateTimestampTask)
        }

        binding.autoUpdateSwitch.isChecked = true

        binding.timestamp.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                // Editing the timestamp by hand stops the clock.
                binding.autoUpdateSwitch.isChecked = false
                handler.removeCallbacks(updateTimestampTask)
                // Call performClick to ensure proper accessibility behavior
                view.performClick()
            }
            false
        }

        binding.timestamp.   setText(timestampFormat.format(Date()))
        binding.urlTemplate. setText(R.string.defaultURLTemplate)
        binding.evseId.      setText(R.string.defaultEVSEId)
        binding.sharedSecret.setText(generateRandomSecret(16))
        binding.validityTime.setText(R.string.defaultValidityTime)
        binding.totpLength.  setText(R.string.defaultTOTPLength)
        binding.alphabet.    setText(R.string.defaultAlphabet)

        val textWatcher = object : TextWatcher {

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateResultingURL()
            }

            override fun afterTextChanged(s: Editable?) { }

        }

        binding.timestamp.   addTextChangedListener(textWatcher)
        binding.urlTemplate. addTextChangedListener(textWatcher)
        binding.evseId.      addTextChangedListener(textWatcher)
        binding.sharedSecret.addTextChangedListener(textWatcher)
        binding.validityTime.addTextChangedListener(textWatcher)
        binding.totpLength.  addTextChangedListener(textWatcher)
        binding.alphabet.    addTextChangedListener(textWatcher)

        updateResultingURL()

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

    override fun onDestroy() {
        handler.removeCallbacks(updateTimestampTask)
        super.onDestroy()
    }

    /**
     * Recalculate the resulting URL from the current input. Invalid input is
     * reported in place of the URL: this runs on every keystroke and once per
     * second while the clock is running, so it must never throw.
     */
    private fun updateResultingURL() {

        val date = try {
            timestampFormat.parse(binding.timestamp.text.toString())
        } catch (e: ParseException) {
            null
        } catch (e: NumberFormatException) {
            // Numeric fields far outside their range never reach ParseException.
            null
        }

        if (date == null) {
            showError(getString(R.string.error_invalid_timestamp))
            return
        }

        val validityTime = binding.validityTime.text.toString().toUIntOrNull()
        if (validityTime == null) {
            showError(getString(R.string.error_invalid_validity_time))
            return
        }

        val totpLength = binding.totpLength.text.toString().toUIntOrNull()
        if (totpLength == null) {
            showError(getString(R.string.error_invalid_totp_length))
            return
        }

        val totp = try {
            TOTP.generateTOTPs(binding.sharedSecret.text.toString(),
                               validityTime,
                               totpLength,
                               binding.alphabet.text.toString(),
                               date.time)
        } catch (e: IllegalArgumentException) {
            showError(e.message ?: getString(R.string.error_invalid_input))
            return
        }

        binding.resultingURL.setTextColor(resultColor)
        binding.resultingURL.text  = binding.urlTemplate.text.toString()
                                            .replace("{evseId}", binding.evseId.text.toString())
                                            .replace("{totp}",   totp.current)

        binding.remainingTime.text = getString(R.string.remaining_time, totp.remainingTime.toLong())

    }

    private fun showError(message: String) {
        binding.resultingURL.setTextColor(errorColor)
        binding.resultingURL. text = message
        binding.remainingTime.text = ""
    }

    private fun generateRandomSecret(length: Int): String {
        val allowedChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_+!$%&/()=?@#*"
        return (1..length)
            .map { allowedChars[secureRandom.nextInt(allowedChars.length)] }
            .joinToString("")
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
        return true
    }

}
