package cloud.charging.open.dynamicqrcodes

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
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
    private val validColor by lazy { getColor(R.color.totpValid) }

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
                    Toast.makeText(this, R.string.scanCancelled, Toast.LENGTH_LONG)
                        .show()
                } else if (originalIntent.hasExtra(Intents.Scan.MISSING_CAMERA_PERMISSION)) {
                    Log.d("MainActivity", "Cancelled scan due to missing camera permission")
                    Toast.makeText(this, R.string.scanMissingCameraPermission, Toast.LENGTH_LONG)
                        .show()
                }
            } else {
                Log.d("MainActivity", "Scanned")
                validateScannedURL(result.contents)
            }

    }

    // The touch listener on the timestamp field forwards to performClick, which
    // is all TextInputEditText needs; the check only knows how to verify that
    // on a view we subclass ourselves.
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {

        enableEdgeToEdge()

        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        applyWindowInsets()

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

        // The camera is optional: generating URLs works without one.
        binding.scanButton.isVisible = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

        binding.scanButton.setOnClickListener {
            binding.scanResult.isVisible = false
            binding.scannedURL.isVisible = false
            val options = ScanOptions()
            options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            options.setPrompt(getString(R.string.scanQRCodePrompt))
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
     * Keep the scrollable content clear of the system bars, the display cutout
     * and the keyboard. Since targetSdk 35 the window always spans the whole
     * screen, so nothing else keeps the scan button above the gesture bar.
     */
    private fun applyWindowInsets() {

        val scrollView    = binding.contentScrollView
        val paddingLeft   = scrollView.paddingLeft
        val paddingRight  = scrollView.paddingRight
        val paddingBottom = scrollView.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { view, windowInsets ->

            val insets = windowInsets.getInsets(
                             WindowInsetsCompat.Type.systemBars() or
                             WindowInsetsCompat.Type.displayCutout() or
                             WindowInsetsCompat.Type.ime()
                         )

            view.updatePadding(
                left   = paddingLeft   + insets.left,
                right  = paddingRight  + insets.right,
                bottom = paddingBottom + insets.bottom
            )

            windowInsets

        }

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

        val remainingTime = totp.remainingTime.toInt()

        binding.resultingURL.setTextColor(resultColor)
        binding.resultingURL.text  = binding.urlTemplate.text.toString()
                                            .replace("{evseId}", binding.evseId.text.toString())
                                            .replace("{totp}",   totp.current)

        binding.remainingTime.text = resources.getQuantityString(R.plurals.remaining_time,
                                                                 remainingTime,
                                                                 remainingTime)

    }

    /**
     * Check a scanned URL the way a CSMS would: pull the TOTP out of it using
     * the URL template, then verify it against the configured shared secret.
     *
     * The check uses the timestamp from the input field rather than the system
     * clock, so that it agrees with the URL shown above it. With auto update
     * running - the default - the two are the same thing.
     */
    private fun validateScannedURL(scannedURL: String) {

        binding.scannedURL.text      = scannedURL
        binding.scannedURL.isVisible = true

        val totp = URLTemplate.extract(binding.urlTemplate.text.toString(), scannedURL)
                              ?.get("totp")

        if (totp == null) {
            showScanResult(getString(R.string.scanNoTemplateMatch), false)
            return
        }

        val timestamp = try {
            timestampFormat.parse(binding.timestamp.text.toString())?.time
        } catch (e: ParseException) {
            null
        } catch (e: NumberFormatException) {
            null
        }

        val validation = try {
            TOTP.validate(totp,
                          binding.sharedSecret.text.toString(),
                          binding.validityTime.text.toString().toUIntOrNull(),
                          binding.totpLength.  text.toString().toUIntOrNull(),
                          binding.alphabet.    text.toString(),
                          timestamp)
        } catch (e: IllegalArgumentException) {
            showScanResult(e.message ?: getString(R.string.error_invalid_input), false)
            return
        }

        showScanResult(
            getString(
                when (validation) {
                    TOTPValidation.CURRENT  -> R.string.scanValidCurrent
                    TOTPValidation.PREVIOUS -> R.string.scanValidPrevious
                    TOTPValidation.NEXT     -> R.string.scanValidNext
                    TOTPValidation.INVALID  -> R.string.scanInvalid
                }
            ),
            validation.isValid
        )

    }

    private fun showScanResult(message: String, valid: Boolean) {
        binding.scanResult.text      = message
        binding.scanResult.setTextColor(if (valid) validColor else errorColor)
        binding.scanResult.isVisible = true
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

}
