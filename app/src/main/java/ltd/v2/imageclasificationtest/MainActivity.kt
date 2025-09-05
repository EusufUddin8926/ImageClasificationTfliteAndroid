package ltd.v2.imageclasificationtest

import ltd.v2.imageclasificationtest.helper.TFLiteObjectDetector
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.yalantis.ucrop.UCrop
import ltd.v2.imageclasificationtest.helper.TFLiteModelInitializer
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var modelInitializer: TFLiteModelInitializer
    private lateinit var detector: TFLiteObjectDetector

    private lateinit var imageView: ImageView
    private lateinit var resultText: TextView

    private var pendingAction: (() -> Unit)? = null

    // Class labels
    private val classLabels = mapOf(
        0 to "battery",
        1 to "biological",
        2 to "cardboard",
        3 to "clothes",
        4 to "glass",
        5 to "metal",
        6 to "paper",
        7 to "plastic",
        8 to "shoes",
        9 to "trash"
    )

    // Camera launcher
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val bitmap = result.data?.extras?.get("data") as? Bitmap
            bitmap?.let {
                val uri = saveTempBitmap(it)
                startCrop(uri)
            }
        }
    }

    // Gallery launcher
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.data
            uri?.let { startCrop(it) }
        }
    }

    // Crop launcher
    private val cropLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val resultUri = UCrop.getOutput(result.data!!)
            resultUri?.let { uri ->
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                val argbBitmap = ensureARGB8888(bitmap)
                imageView.setImageBitmap(argbBitmap)
                runPrediction(argbBitmap)
            }
        } else if (result.resultCode == UCrop.RESULT_ERROR) {
            val cropError = UCrop.getError(result.data!!)
            Log.e("UCrop", "Crop error: $cropError")
        }
    }

    // Permission launcher
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) {
            pendingAction?.invoke()
        } else {
            Log.e("Permission", "Some permissions denied")
        }
        pendingAction = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.imageView)
        resultText = findViewById(R.id.resultText)

        val btnCamera = findViewById<Button>(R.id.btnCamera)
        val btnGallery = findViewById<Button>(R.id.btnGallery)

        // Initialize model
        modelInitializer = TFLiteModelInitializer(this)
        val interpreter = modelInitializer.getInterpreter("ensemble_model.tflite")

        detector = TFLiteObjectDetector(
            interpreter = interpreter,
            inputSize = 224,
            isQuantized = false
        )

        btnCamera.setOnClickListener { handleCameraClick() }
        btnGallery.setOnClickListener { handleGalleryClick() }

        // Default test image
        val bitmap = BitmapFactory.decodeResource(resources, R.drawable.test_two)
        val argbBitmap = ensureARGB8888(bitmap)
        imageView.setImageBitmap(argbBitmap)
        runPrediction(argbBitmap)
    }

    private fun handleCameraClick() {
        val permission = Manifest.permission.CAMERA
        if (checkPermission(permission)) {
            openCamera()
        } else {
            pendingAction = { openCamera() }
            requestPermissions(arrayOf(permission))
        }
    }

    private fun handleGalleryClick() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (checkPermission(permission)) {
            openGallery()
        } else {
            pendingAction = { openGallery() }
            requestPermissions(arrayOf(permission))
        }
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraLauncher.launch(intent)
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryLauncher.launch(intent)
    }

    private fun startCrop(uri: Uri) {
        val destinationUri = Uri.fromFile(File(cacheDir, "croppedImage.jpg"))
        val options = UCrop.Options().apply {
            setFreeStyleCropEnabled(true) // allow free crop
            setCompressionFormat(Bitmap.CompressFormat.JPEG)
        }
        val cropIntent = UCrop.of(uri, destinationUri)
            .withAspectRatio(1f, 1f) // square crop
            .withMaxResultSize(160, 200) // resize for model
            .withOptions(options)
            .getIntent(this)

        cropLauncher.launch(cropIntent)
    }

    private fun runPrediction(bitmap: Bitmap) {
        val predictions = detector.detect(bitmap)
        predictions.forEach { Log.d("Prediction", it.toString()) }

        val maxIndex = predictions.withIndex().maxByOrNull { it.value }?.index ?: -1
        val confidence = predictions.getOrNull(maxIndex) ?: 0f
        val className = classLabels[maxIndex] ?: "Unknown"

        resultText.text = "Predicted Class: $className\nConfidence: %.2f".format(confidence)
    }

    private fun requestPermissions(permissions: Array<String>) {
        permissionLauncher.launch(permissions)
    }

    private fun checkPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureARGB8888(bitmap: Bitmap): Bitmap {
        return if (bitmap.config != Bitmap.Config.ARGB_8888) {
            bitmap.copy(Bitmap.Config.ARGB_8888, true)
        } else bitmap
    }

    private fun saveTempBitmap(bitmap: Bitmap): Uri {
        val file = File(cacheDir, "temp_camera.jpg")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
        }
        return Uri.fromFile(file)
    }

    override fun onDestroy() {
        super.onDestroy()
        modelInitializer.close()
    }
}
