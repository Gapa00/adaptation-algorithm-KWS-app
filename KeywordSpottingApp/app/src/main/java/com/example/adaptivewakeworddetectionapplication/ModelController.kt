package com.example.adaptivewakeworddetectionapplication


import android.content.Context
import org.tensorflow.lite.Interpreter
import java.nio.channels.FileChannel
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import org.tensorflow.lite.flex.FlexDelegate
import java.nio.ByteBuffer
import java.nio.ByteOrder



class ModelController(private val context: Context, private val smallThresh: Float, private val mediumThresh: Float) {

    private var currentModel: Interpreter? = null
    private var currentModelPath: String = ""
    private var lastKperformances = mutableListOf<Float>()
    private var classLabels = mutableListOf<String>()

    private val NUM_MFCC = 13
    private val SAMPLE_RATE = 16000

    init {
        loadClassLabels()
        chooseModel()
        Log.d("ModelController", "Small to Medium Threshold: $smallThresh")
        Log.d("ModelController", "Medium to Large Threshold: $mediumThresh")
    }

    private fun loadClassLabels() {
        try {
            context.assets.open("labels.txt").bufferedReader().useLines { lines ->
                classLabels = lines.toMutableList()
            }
            Log.d(TAG, "Class labels loaded: $classLabels")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading class labels: ${e.message}")
        }
    }

    fun chooseModel() {
        val batteryLevel = getBatteryLevel(context)

        val initialModelPath = when {
            batteryLevel < smallThresh -> context.getText(R.string.small_model).toString()
            batteryLevel < mediumThresh -> context.getText(R.string.medium_model).toString()
            else -> context.getText(R.string.large_model).toString()
        }

        val averageConfidence = if (lastKperformances.isNotEmpty()) {
            lastKperformances.average().toFloat()
        } else {
            1.0f
        }

        var modelPath = initialModelPath
        if (averageConfidence < 0.5f) {
            modelPath = when (initialModelPath) {
                context.getText(R.string.small_model).toString() -> context.getText(R.string.medium_model).toString()
                context.getText(R.string.medium_model).toString() -> context.getText(R.string.large_model).toString()
                else -> initialModelPath
            }
        }

        if (currentModel == null || currentModelPath != modelPath) {
            currentModel?.close()
            currentModel = loadModel(modelPath)
            currentModelPath = modelPath
        }
    }


    private fun getBatteryLevel(context: Context): Float {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            context.registerReceiver(null, ifilter)
        }
        val batteryPct: Float? = batteryStatus?.let { intent ->
            val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            (level / scale.toFloat() * 100)
        }
        return batteryPct ?: 100.0f
    }

    private fun loadModel(modelPath: String): Interpreter {
        val assetFileDescriptor = context.assets.openFd(modelPath)
        val fileInputStream = assetFileDescriptor.createInputStream()
        val fileChannel = fileInputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

        val flexDelegate = FlexDelegate()
        val options = Interpreter.Options().addDelegate(flexDelegate)

        return Interpreter(modelBuffer, options)
    }

    private fun extractMFCC(audioData: DoubleArray): Array<Array<FloatArray>> {
        val mfccConvert = MFCC()
        mfccConvert.setSampleRate(SAMPLE_RATE)
        val nMFCC = NUM_MFCC
        mfccConvert.setN_mfcc(nMFCC)
        val mfccInput = mfccConvert.process(audioData)

        val nFFT = mfccInput.size / nMFCC
        val mfccValues = Array(nFFT) { Array(nMFCC) { FloatArray(1) } }

        for (i in 0 until nFFT) {
            for (j in 0 until nMFCC) {
                mfccValues[i][j][0] = mfccInput[i * nMFCC + j]
            }
        }
        Log.d(TAG, "$currentModelPath")
        return mfccValues
    }

    fun processShiftedWindows(buffer: DoubleArray): DetectionResult? {
        chooseModel()
        val windowSize = SAMPLE_RATE
        var bestConfidence = Float.MIN_VALUE
        var bestClassification: DetectionResult? = null

        for (shift in 0..5) {
            val startIndex = (shift * 0.2 * SAMPLE_RATE).toInt()

            if (startIndex + windowSize <= buffer.size) {
                val window = buffer.copyOfRange(startIndex, startIndex + windowSize)
                val classification = classify(window)

                if (classification!!.confidences[0] > bestConfidence) {
                    bestConfidence = classification.confidences[0]
                    bestClassification = classification
                }
            }
        }
        return bestClassification
    }

    fun updateLastKperformances(newConfidence: Float) {
        if (lastKperformances.size >= 3) {
            lastKperformances.removeAt(0)
        }
        lastKperformances.add(newConfidence)
    }

    private fun classify(audioData: DoubleArray): DetectionResult? {
        try {
            val interpreter = currentModel

            val mfccFeatures = extractMFCC(audioData)

            val inputBuffer = ByteBuffer.allocateDirect(32 * 13 * 1 * 4).order(ByteOrder.nativeOrder())
            for (i in 0 until 32) {
                for (j in 0 until 13) {
                    inputBuffer.putFloat(mfccFeatures[i][j][0])
                }
            }

            val numClasses = classLabels.size
            val outputBuffer = Array(1) { FloatArray(numClasses) }

            interpreter?.run(inputBuffer, outputBuffer)

            val outputArray = outputBuffer[0]
            val top3Indices = outputArray.indices.sortedByDescending { outputArray[it] }.take(3)
            val topClassLabels = top3Indices.map { classLabels[it] }
            val confidences = top3Indices.map { outputArray[it] }

            val detectionResult = DetectionResult(
                topClassLabels = topClassLabels,
                confidences = confidences,
                modelUsed = currentModelPath,
                timestamp = System.currentTimeMillis()
            )

            return detectionResult

        } catch (e: Exception) {
            Log.e(TAG, "Error running model: ${e.message}")
            return null
        }
    }

    companion object {
        private val TAG = ModelController::class.simpleName
    }
}
