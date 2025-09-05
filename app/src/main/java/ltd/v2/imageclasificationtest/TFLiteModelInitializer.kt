package ltd.v2.imageclasificationtest

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class TFLiteModelInitializer(private val context: Context) {

    private var interpreter: Interpreter? = null

    fun getInterpreter(modelPath: String = "ensemble_model.tflite"): Interpreter {
        if (interpreter == null) {
            interpreter = Interpreter(loadModelFile(modelPath), Interpreter.Options().apply {
                setNumThreads(4)
            })
        }
        return interpreter!!
    }

    @Throws(IOException::class)
    private fun loadModelFile(modelPath: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelPath) // No "assets/" prefix
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
