package com.example.followbot

import android.annotation.SuppressLint
import android.media.Image
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.ObjectDetectorOptionsBase.STREAM_MODE
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.android.synthetic.main.camera_activity.*
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.jar.Manifest

class CameraActivity : AppCompatActivity() {


    private var imageCapture: ImageCapture? = null

    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.camera_activity)



        startCamera()

        val cameraCaptureButton = findViewById<Button>(R.id.camera_capture_button)
        cameraCaptureButton.setOnClickListener { takePhoto() }

        outputDirectory = getOutputDirectory()

        cameraExecutor = Executors.newSingleThreadExecutor()

    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        // Create time-stamped output file to hold the image
        val photoFile = File(
            outputDirectory,
            SimpleDateFormat(FILENAME_FORMAT, Locale.US
            ).format(System.currentTimeMillis()) + ".jpg")

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    val msg = "Photo capture succeeded: $savedUri"
                    val options = ObjectDetectorOptions.Builder()
                        .setDetectorMode(STREAM_MODE)
                        .build()

                    val objectDetector = ObjectDetection.getClient(options)
                    var image: InputImage? = null

                    try {
                        image = InputImage.fromFilePath(applicationContext, savedUri)
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                    /*val imageAnalyzer = ImageAnalysis.Builder()
                        .build()
                        .also {
                            it.setAnalyzer(cameraExecutor, BallImageAnalyzer() )
                       }*/

                    val imageAnalyzer = BallImageAnalyzer()


                    BallImageAnalyzer.
                    image?.let {
                        objectDetector.process(image)
                            .addOnSuccessListener { detectedObjects ->
                                //send data to bot
                                if(detectedObjects.size > 0) {
                                    Toast.makeText(
                                        applicationContext,
                                        "Detected: ${detectedObjects[0].labels}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                Log.e(TAG, detectedObjects.toString())
                            }
                            .addOnFailureListener {
                                //do nothing (silent fail)
                            }
                    }
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }
            })
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener( {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val viewFinder = findViewById<PreviewView>(R.id.viewFinder)
            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            imageCapture = ImageCapture.Builder()
                .build()



            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))


    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() } }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXBasic"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }

    private class BallImageAnalyzer : ImageAnalysis.Analyzer {

        @SuppressLint("UnsafeExperimentalUsageError")
        @RequiresApi(Build.VERSION_CODES.KITKAT)
        override fun analyze(imageProxy: ImageProxy) {
            var mediaImage: Image? = null
            imageProxy.image?.let {
                mediaImage = it
            }

            mediaImage?.let{
                val image = InputImage.fromMediaImage(it, imageProxy.imageInfo.rotationDegrees)
                // Pass image to an ML Kit Vision API
                // ...
            }
        }
    }
}