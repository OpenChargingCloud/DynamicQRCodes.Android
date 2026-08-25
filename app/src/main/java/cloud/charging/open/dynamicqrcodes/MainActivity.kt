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

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import cloud.charging.open.dynamicqrcodes.databinding.ActivityMainBinding
import com.google.android.material.color.MaterialColors
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

    private val buildInfoFormat  = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    /**
     * The URL currently on display, or null while the field below the QR code
     * is showing an input error instead. Doubles as the cache key of the
     * rendered code: this runs once a second, but the URL only changes when
     * the time slot does.
     */
    private var currentURL: String? = null

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

    private val barcodeLauncher = registerForActivityResult(ScannerActivity.Contract()) { contents ->

        if (contents == null) {
            Log.d("MainActivity", "Cancelled scan")
            Toast.makeText(this, R.string.scanCancelled, Toast.LENGTH_LONG).show()
        } else {
            Log.d("MainActivity", "Scanned")
            validateScannedURL(contents)
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

        binding.resultingURL.setOnLongClickListener {
            // Nothing worth copying while an error message stands here, and
            // returning false then leaves the gesture unhandled rather than
            // pretending it did something.
            val url = currentURL
            if (url != null)
                copyToClipboard(url, R.string.resultingURLCopied)
            url != null
        }

        showBuildInfo()

        // The camera is optional: generating URLs works without one.
        binding.scanButton.isVisible = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

        binding.scanButton.setOnClickListener {
            binding.scanResult.isVisible    = false
            binding.scannedEvseId.isVisible = false
            binding.scannedURL.isVisible    = false
            barcodeLauncher.launch(Unit)
        }

    }

    /**
     * Name the build running on this device. The version alone cannot tell a
     * store release from a local build, so the git revision and the moment the
     * package was installed are shown as well - the latter is what gives away
     * an app that was never replaced by the newest build.
     */
    private fun showBuildInfo() {

        @Suppress("DEPRECATION")
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                              packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                          else
                              packageManager.getPackageInfo(packageName, 0)

        binding.buildInfo.text = getString(R.string.buildInfo,
                                           BuildConfig.VERSION_NAME,
                                           BuildConfig.VERSION_CODE,
                                           BuildConfig.GIT_REVISION,
                                           buildInfoFormat.format(Date(packageInfo.lastUpdateTime)))

        binding.buildInfo.setOnLongClickListener {
            copyToClipboard(binding.buildInfo.text, R.string.buildInfoCopied)
            true
        }

    }

    /**
     * Put [text] on the clipboard and confirm it with [confirmation] - except
     * on Android 13 and up, which shows a confirmation of its own and would
     * otherwise be saying it twice.
     */
    private fun copyToClipboard(text: CharSequence, @StringRes confirmation: Int) {

        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), text))

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
            Toast.makeText(this, confirmation, Toast.LENGTH_SHORT).show()

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

        showURL(binding.urlTemplate.text.toString()
                       .replace("{evseId}", binding.evseId.text.toString())
                       .replace("{totp}",   totp.current))

        showRemainingValidity(totp.remainingTime.toInt(), validityTime.toInt())

    }

    /**
     * Show [url] as text, and as the QR code above it.
     *
     * The code is only re-encoded when the URL actually changed. Called once
     * per second while the clock runs, and the TOTP within it turns over only
     * once per time slot, so most calls have nothing to do.
     */
    private fun showURL(url: String) {

        binding.resultingURL.setTextColor(resultColor)
        binding.resultingURL.text = url

        if (url == currentURL)
            return

        currentURL = url

        // One pixel per module, so the scaling happens here - and it has to
        // happen without interpolation, or the module edges blur into grey
        // and the code stops decoding.
        val bitmap = QRCodeGenerator.bitmap(url)

        binding.qrCode.setImageDrawable(
            bitmap?.toDrawable(resources)?.apply { isFilterBitmap = false }
        )

        // A URL too long for the largest QR version leaves the text standing
        // on its own rather than showing an empty frame.
        binding.qrCode.isVisible = bitmap != null

    }

    /**
     * Count the current time slot down: the bar drains from full to nearly
     * empty, the label below it says the same thing in seconds.
     *
     * [remainingTime] runs from [validityTime] down to 1, so the bar is scaled
     * in seconds rather than in percent - no rounding, and each tick moves it
     * by exactly one step.
     */
    private fun showRemainingValidity(remainingTime: Int, validityTime: Int) {

        val bar         = binding.remainingValidityBar
        val sameSlotLength = bar.max == validityTime

        bar.max = validityTime

        // Animated while the slot drains, but not when it refills: sliding the
        // bar back up to full reads as a rewind rather than as a fresh start.
        bar.setProgressCompat(remainingTime,
                              sameSlotLength && remainingTime < bar.progress)

        binding.remainingTime.text = getString(R.string.remainingSeconds, remainingTime)

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

        val values = URLTemplate.extract(binding.urlTemplate.text.toString(), scannedURL)

        showScannedEvseId(values?.get("evseId"))

        val totp = values?.get("totp")

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

    /**
     * Name the EVSE a scanned code belongs to, and say so when that is not the
     * one configured here.
     *
     * Deliberately kept out of the verdict: OCPP 2.1 C25 makes the TOTP the
     * proof of authenticity and has the CSMS decode the URL parameters only
     * afterwards. A different EVSE ID therefore means a valid code of another
     * charging point, not an invalid one - which is why this says it in words
     * instead of colouring it like a failure.
     */
    private fun showScannedEvseId(evseId: String?) {

        if (evseId == null) {
            binding.scannedEvseId.isVisible = false
            return
        }

        binding.scannedEvseId.text      = if (evseId == binding.evseId.text.toString().trim())
                                              getString(R.string.scannedEvseId, evseId)
                                          else
                                              getString(R.string.scannedEvseIdDiffers, evseId)
        binding.scannedEvseId.isVisible = true

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

        // There is no URL, so there is nothing to encode and nothing to copy.
        currentURL                = null
        binding.qrCode.isVisible  = false
        binding.remainingValidityBar.setProgressCompat(0, false)

    }

    private fun generateRandomSecret(length: Int): String {
        val allowedChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_+!$%&/()=?@#*"
        return (1..length)
            .map { allowedChars[secureRandom.nextInt(allowedChars.length)] }
            .joinToString("")
    }

}
