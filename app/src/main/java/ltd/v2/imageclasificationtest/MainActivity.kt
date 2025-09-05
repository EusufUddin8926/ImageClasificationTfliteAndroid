package ltd.v2.imageclasificationtest

import TFLiteObjectDetector
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var modelInitializer: TFLiteModelInitializer
    private lateinit var detector: TFLiteObjectDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val imageView = findViewById<ImageView>(R.id.imageView)
        val resultText = findViewById<TextView>(R.id.resultText)

        // Initialize model
        modelInitializer = TFLiteModelInitializer(this)
        val interpreter = modelInitializer.getInterpreter("ensemble_model.tflite")

        detector = TFLiteObjectDetector(
            interpreter = interpreter,
            inputSize = 224,
            isQuantized = false
        )

        // Load a sample image
        val bitmap = BitmapFactory.decodeResource(resources, R.drawable.test_two)
        imageView.setImageBitmap(bitmap)

        // Run detection
        val predictions = detector.detect(bitmap)

        predictions.forEach { Log.d("Prediction", it.toString()) }

        val maxIndex = predictions.withIndex().maxByOrNull { it.value }?.index ?: -1
        val confidence = predictions.getOrNull(maxIndex) ?: 0f
        Log.d("Prediction", "Top-1 class: $maxIndex, confidence: $confidence")

        resultText.text = "Predicted Class: $maxIndex\nConfidence: %.2f".format(confidence)
    }

    override fun onDestroy() {
        super.onDestroy()
        modelInitializer.close()
    }
}
