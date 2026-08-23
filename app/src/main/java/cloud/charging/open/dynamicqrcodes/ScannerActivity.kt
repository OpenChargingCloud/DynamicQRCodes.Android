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

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import cloud.charging.open.dynamicqrcodes.databinding.ActivityScannerBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Scans a QR code with CameraX and hands the contents back to the caller.
 *
 * Replaces the CaptureActivity of zxing-android-embedded, which was built on
 * the Camera1 API deprecated since Android 5 and pinned itself to landscape -
 * a restriction Android 17 ignores on large screens anyway.
 */
class ScannerActivity : AppCompatActivity() {

    /** Returns the scanned contents, or null when the scan was cancelled. */
    class Contract : ActivityResultContract<Unit, String?>() {

        override fun createIntent(context: Context, input: Unit) =
            Intent(context, ScannerActivity::class.java)

        override fun parseResult(resultCode: Int, intent: Intent?) =
            if (resultCode == Activity.RESULT_OK) intent?.getStringExtra(EXTRA_RESULT)
            else null

    }

    companion object {
        private const val EXTRA_RESULT = "contents"
        private const val TAG          = "ScannerActivity"
    }

    private lateinit var binding:  ActivityScannerBinding
    private lateinit var executor: ExecutorService

    private val requestCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted)
            startCamera()
        else {
            binding.scannerHint.setText(R.string.scanMissingCameraPermission)
            Log.d(TAG, "Camera permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        enableEdgeToEdge()

        super.onCreate(savedInstanceState)

        binding = ActivityScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        executor = Executors.newSingleThreadExecutor()

        ViewCompat.setOnApplyWindowInsetsListener(binding.scannerHint) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or
                                                WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(bottom = insets.bottom + view.paddingTop)
            windowInsets
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED)
            startCamera()
        else
            requestCamera.launch(Manifest.permission.CAMERA)

    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }

    private fun startCamera() {

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = binding.preview.surfaceProvider
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(executor, QRCodeAnalyzer(::onQRCodeFound))
                }

            try {
                cameraProviderFuture.get().apply {
                    unbindAll()
                    bindToLifecycle(this@ScannerActivity,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    analysis)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Camera could not be started", e)
                binding.scannerHint.setText(R.string.scanCameraUnavailable)
            }

        }, ContextCompat.getMainExecutor(this))

    }

    /** Called from the analyzer thread, once. */
    private fun onQRCodeFound(contents: String) {
        runOnUiThread {
            Log.d(TAG, "Scanned")
            setResult(RESULT_OK, Intent().putExtra(EXTRA_RESULT, contents))
            finish()
        }
    }

}
