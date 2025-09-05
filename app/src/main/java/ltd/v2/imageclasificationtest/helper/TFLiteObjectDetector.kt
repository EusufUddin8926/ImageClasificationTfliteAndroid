package ltd.v2.imageclasificationtest.helper

import android.graphics.Bitmap
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer

class TFLiteObjectDetector(
    private val interpreter: Interpreter,
    private val inputSize: Int = 224,    // Model input size
    private val isQuantized: Boolean = false
) {

    fun detect(bitmap: Bitmap): FloatArray {
        // 1️⃣ Load bitmap into TensorImage
        var tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)

        // 2️⃣ Create ImageProcessor: resize + normalization
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(inputSize, inputSize, ResizeOp.ResizeMethod.BILINEAR))
            .build()

        tensorImage = imageProcessor.process(tensorImage)

        // Optional: normalization if model expects 0-1
        if (!isQuantized) {
            val floatBuffer = tensorImage.buffer.asFloatBuffer()
            for (i in 0 until floatBuffer.capacity()) {
                floatBuffer.put(i, floatBuffer.get(i) / 255.0f)
            }
        }

        // 3️⃣ Prepare output buffer
        val numClasses = interpreter.getOutputTensor(0).shape()[1]
        val outputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, numClasses), DataType.FLOAT32)

        // 4️⃣ Run inference
        interpreter.run(tensorImage.buffer, outputBuffer.buffer.rewind())

        return outputBuffer.floatArray
    }
}