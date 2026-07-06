package com.medical.chestxray.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.medical.chestxray.data.model.DiseasePrediction
import com.medical.chestxray.data.model.AnalysisResponse
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.FloatBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random
import kotlin.math.exp

class OnnxModelRunner(private val context: Context) {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var session: OrtSession? = null

    companion object {
        const val MODEL_FILE_NAME = "model_float32.onnx"
        
        val DISEASE_LABELS = listOf(
            "Atelectasis",
            "Consolidation",
            "Infiltration",
            "Pneumothorax",
            "Edema",
            "Emphysema",
            "Fibrosis",
            "Effusion",
            "Pneumonia",
            "Pleural_Thickening",
            "Cardiomegaly",
            "Nodule",
            "Mass",
            "Hernia"
        )
    }

    private var initError: Exception? = null

    /** Identifies which model this runner loaded, so callers can rebuild it when the model changes. */
    val loadedSignature: String = ModelRepository.signature(context)

    init {
        loadModel()
    }

    private fun loadModel() {
        try {
            val activeModel = ModelRepository.getActiveModel(context)
            val modelBytes = if (activeModel.filePath != null) {
                // Side-loaded model from private storage.
                File(activeModel.filePath).readBytes()
            } else {
                // Bundled default from assets.
                context.assets.open(MODEL_FILE_NAME).use { it.readBytes() }
            }
            session = env.createSession(modelBytes)
        } catch (e: Exception) {
            initError = e
            e.printStackTrace()
        }
    }

    private fun validateXRayImage(bitmap: Bitmap) {
        val width = bitmap.width
        val height = bitmap.height
        val sampleCount = 200
        var totalChannelDiff = 0.0f
        val random = Random()
        
        val brightnesses = FloatArray(sampleCount)
        var sumBrightness = 0.0f

        for (i in 0 until sampleCount) {
            val x = random.nextInt(width)
            val y = random.nextInt(height)
            val pixel = bitmap.getPixel(x, y)
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            
            totalChannelDiff += (Math.abs(r - g) + Math.abs(g - b) + Math.abs(b - r)) / 3.0f
            
            val gray = (r + g + b) / 3.0f
            brightnesses[i] = gray
            sumBrightness += gray
        }
        
        val averageDiff = totalChannelDiff / sampleCount
        val meanBrightness = sumBrightness / sampleCount
        
        var varianceSum = 0.0f
        for (i in 0 until sampleCount) {
            varianceSum += (brightnesses[i] - meanBrightness) * (brightnesses[i] - meanBrightness)
        }
        val stdDev = Math.sqrt((varianceSum / sampleCount).toDouble())
        
        if (averageDiff > 12.0f || stdDev < 10.0) {
            throw IllegalArgumentException("Please Upload Proper X-Ray Image")
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun runInference(imageUri: Uri): AnalysisResponse {
        val session = this.session ?: throw IllegalStateException("ONNX Session is not initialized: ${initError?.localizedMessage ?: "Unknown initialization error"}")
        
        // 1. Load and Validate Bitmap
        val bitmap = loadBitmap(imageUri) ?: throw IllegalArgumentException("Could not load bitmap from URI.")
        validateXRayImage(bitmap)
        
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 384, 384, true)
        
        val width = 384
        val height = 384
        val pixels = IntArray(width * height)
        resizedBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val inputData = FloatArray(1 * 3 * 384 * 384)
        
        // Normalization Constants
        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)
        
        val channelSize = width * height
        for (i in 0 until channelSize) {
            val color = pixels[i]
            val r = ((color shr 16) and 0xFF) / 255.0f
            val g = ((color shr 8) and 0xFF) / 255.0f
            val b = (color and 0xFF) / 255.0f
            
            inputData[i] = (r - mean[0]) / std[0]
            inputData[i + channelSize] = (g - mean[1]) / std[1]
            inputData[i + 2 * channelSize] = (b - mean[2]) / std[2]
        }

        // 2. Create Input Tensor and Run Baseline Inference
        val inputBuffer = FloatBuffer.wrap(inputData)
        val inputTensor = OnnxTensor.createTensor(env, inputBuffer, longArrayOf(1, 3, 384, 384))
        
        val outputs = session.run(mapOf("image" to inputTensor))
        
        // Extract Logits
        val rawLogits = (outputs[0].value as Array<FloatArray>)[0]
        
        // Apply Sigmoid and map to DiseasePredictions
        val predictions = DISEASE_LABELS.mapIndexed { index, diseaseName ->
            val logit = rawLogits[index]
            val confidence = sigmoid(logit) * 100f
            DiseasePrediction(disease = diseaseName, confidence = confidence)
        }.sortedByDescending { it.confidence }

        val topPrediction = predictions.firstOrNull()
        val topClassIndex = DISEASE_LABELS.indexOf(topPrediction?.disease)
        val baselineConfidence = topPrediction?.confidence ?: 0.0f
        
        // 3. Saliency heatmap
        var heatmapBase64: String? = null
        
        if (topPrediction != null && baselineConfidence > 5.0f) {
            
            // Class-specific saliency via occlusion sensitivity (gradient-free, mobile-friendly
            // alternative to Grad-CAM, which requires backprop unavailable during inference).
            heatmapBase64 = generateOcclusionHeatmap(session, inputData, topClassIndex, baselineConfidence)
        }

        // Clean up Input Tensor & Bitmaps
        inputTensor.close()
        outputs.close()
        if (bitmap != resizedBitmap) {
            bitmap.recycle()
        }
        resizedBitmap.recycle()

        // Return AnalysisResponse
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())
        val scanId = "local_scan_" + System.currentTimeMillis()

        return AnalysisResponse(
            id = scanId,
            timestamp = timestamp,
            top_prediction = topPrediction?.disease ?: "Normal",
            confidence = baselineConfidence,
            predictions = predictions,
            heatmap_data = heatmapBase64,
            image_url = imageUri.toString()
        )
    }

    /**
     * PATH A: High-speed, high-resolution Class Activation Mapping (CAM/Saliency)
     * extracts features from the last conv layer outputs and maps them in 1 pass.
     */
    private fun generateCAMFromFeatures(featuresTensor: OnnxTensor): String? {
        val shape = featuresTensor.info.shape
        if (shape.size < 4) return null
        
        val channels = shape[1].toInt()
        val height = shape[2].toInt()
        val width = shape[3].toInt()
        
        val buffer = featuresTensor.floatBuffer
        val data = FloatArray(buffer.remaining())
        buffer.get(data)
        
        // Compute Class-Agnostic Saliency (Sum of activations across channels)
        val importanceGrid = FloatArray(height * width)
        var maxVal = 0.0f
        var minVal = Float.MAX_VALUE
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                var sum = 0.0f
                for (c in 0 until channels) {
                    val idx = c * (height * width) + y * width + x
                    if (idx < data.size) {
                        val valAct = data[idx]
                        if (valAct > 0.0f) { // ReLU activation filter
                            sum += valAct
                        }
                    }
                }
                val gridIdx = y * width + x
                importanceGrid[gridIdx] = sum
                if (sum > maxVal) maxVal = sum
                if (sum < minVal) minVal = sum
            }
        }
        
        // Normalize values to [0.0, 1.0]
        val diff = maxVal - minVal
        for (i in importanceGrid.indices) {
            importanceGrid[i] = if (diff > 0.0f) {
                (importanceGrid[i] - minVal) / diff
            } else {
                0.5f
            }
        }
        
        // Upscale using Bilinear Interpolation to 384x384 Heatmap
        val heatmapBitmap = Bitmap.createBitmap(384, 384, Bitmap.Config.ARGB_8888)
        val scaleX = width.toFloat() / 384.0f
        val scaleY = height.toFloat() / 384.0f
        
        for (y in 0 until 384) {
            for (x in 0 until 384) {
                val uf = x * scaleX
                val vf = y * scaleY
                
                val u0 = Math.max(0, Math.min(width - 1, Math.floor(uf.toDouble()).toInt()))
                val u1 = Math.max(0, Math.min(width - 1, u0 + 1))
                val v0 = Math.max(0, Math.min(height - 1, Math.floor(vf.toDouble()).toInt()))
                val v1 = Math.max(0, Math.min(height - 1, v0 + 1))
                
                val wu = uf - u0
                val wv = vf - v0
                
                val score = (1.0f - wu) * (1.0f - wv) * importanceGrid[v0 * width + u0] +
                            wu * (1.0f - wv) * importanceGrid[v0 * width + u1] +
                            (1.0f - wu) * wv * importanceGrid[v1 * width + u0] +
                            wu * wv * importanceGrid[v1 * width + u1]
                
                // Colorize Jet
                val r: Float
                val g: Float
                val b: Float
                if (score < 0.25f) {
                    r = 0.0f
                    g = score / 0.25f
                    b = 1.0f
                } else if (score < 0.5f) {
                    r = 0.0f
                    g = 1.0f
                    b = 1.0f - (score - 0.25f) / 0.25f
                } else if (score < 0.75f) {
                    r = (score - 0.5f) / 0.25f
                    g = 1.0f
                    b = 0.0f
                } else {
                    r = 1.0f
                    g = 1.0f - (score - 0.75f) / 0.25f
                    b = 0.0f
                }
                
                val alpha = (score * 160f).toInt().coerceIn(0, 160)
                val color = (alpha shl 24) or ((r * 255).toInt() shl 16) or ((g * 255).toInt() shl 8) or (b * 255).toInt()
                heatmapBitmap.setPixel(x, y, color)
            }
        }
        
        return try {
            val outputStream = ByteArrayOutputStream()
            heatmapBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            val base64Bytes = android.util.Base64.encodeToString(outputStream.toByteArray(), android.util.Base64.DEFAULT)
            heatmapBitmap.recycle()
            "data:image/png;base64,$base64Bytes"
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * PATH B: Fallback Occlusion Sensitivity Mapping (36 forward passes)
     */
    @Suppress("UNCHECKED_CAST")
    private fun generateOcclusionHeatmap(
        session: OrtSession,
        inputData: FloatArray,
        targetClassIndex: Int,
        baselineConfidence: Float
    ): String? {
        val gridSize = 10 // Finer grid → smoother, better-localized occlusion saliency
        val importanceGrid = Array(gridSize) { FloatArray(gridSize) }
        
        val baselineConfFloat = baselineConfidence / 100.0f
        val totalBlocks = gridSize * gridSize
        val batchSize = 16 // Batched parallel runs of size 16 to maximize CPU thread utilization without thrashing
        
        val singleInputSize = 3 * 384 * 384
        val channelSize = 384 * 384
        
        var blockIdx = 0
        while (blockIdx < totalBlocks) {
            val currentBatchSize = Math.min(batchSize, totalBlocks - blockIdx)
            val batchData = FloatArray(currentBatchSize * singleInputSize)
            val batchBlocks = ArrayList<Pair<Int, Int>>()
            
            for (i in 0 until currentBatchSize) {
                val idx = blockIdx + i
                val row = idx / gridSize
                val col = idx % gridSize
                batchBlocks.add(Pair(row, col))
                
                // Copy original inputData to batchOffset
                val batchOffset = i * singleInputSize
                System.arraycopy(inputData, 0, batchData, batchOffset, singleInputSize)
                
                // Occlude the current block (proportional bounds cover the full 384px)
                val rowStart = row * 384 / gridSize
                val rowEnd = (row + 1) * 384 / gridSize
                val colStart = col * 384 / gridSize
                val colEnd = (col + 1) * 384 / gridSize
                
                for (y in rowStart until rowEnd) {
                    for (x in colStart until colEnd) {
                        val pixelIdx = y * 384 + x
                        batchData[batchOffset + pixelIdx] = 0.0f
                        batchData[batchOffset + pixelIdx + channelSize] = 0.0f
                        batchData[batchOffset + pixelIdx + 2 * channelSize] = 0.0f
                    }
                }
            }
            
            try {
                val inputBuffer = FloatBuffer.wrap(batchData)
                val inputTensor = OnnxTensor.createTensor(env, inputBuffer, longArrayOf(currentBatchSize.toLong(), 3, 384, 384))
                val outputs = session.run(mapOf("image" to inputTensor))
                val logitsArray = outputs[0].value as Array<FloatArray>
                
                for (i in 0 until currentBatchSize) {
                    val (row, col) = batchBlocks[i]
                    val logits = logitsArray[i]
                    val confidence = sigmoid(logits[targetClassIndex])
                    importanceGrid[row][col] = Math.max(0.0f, baselineConfFloat - confidence)
                }
                
                inputTensor.close()
                outputs.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            blockIdx += currentBatchSize
        }
        
        // Smooth the low-resolution grid with a 3x3 box blur to remove blocky artifacts.
        val smoothed = Array(gridSize) { r ->
            FloatArray(gridSize) { c ->
                var sum = 0.0f
                var count = 0
                for (dr in -1..1) {
                    for (dc in -1..1) {
                        val nr = r + dr
                        val nc = c + dc
                        if (nr in 0 until gridSize && nc in 0 until gridSize) {
                            sum += importanceGrid[nr][nc]
                            count++
                        }
                    }
                }
                sum / count
            }
        }
        for (r in 0 until gridSize) {
            for (c in 0 until gridSize) {
                importanceGrid[r][c] = smoothed[r][c]
            }
        }

        var maxVal = 0.0f
        var minVal = Float.MAX_VALUE
        for (r in 0 until gridSize) {
            for (c in 0 until gridSize) {
                val v = importanceGrid[r][c]
                if (v > maxVal) maxVal = v
                if (v < minVal) minVal = v
            }
        }

        val diff = maxVal - minVal
        for (r in 0 until gridSize) {
            for (c in 0 until gridSize) {
                val norm = if (diff > 1e-6f) (importanceGrid[r][c] - minVal) / diff else 0.0f
                // Gamma > 1 suppresses low/mid noise so only the salient region stays hot.
                importanceGrid[r][c] = Math.pow(norm.toDouble(), 1.6).toFloat()
            }
        }
        
        val heatmapBitmap = Bitmap.createBitmap(384, 384, Bitmap.Config.ARGB_8888)
        val blockSizeF = 384f / gridSize
        val halfBlock = blockSizeF / 2f
        
        for (y in 0 until 384) {
            for (x in 0 until 384) {
                val uf = (x - halfBlock) / blockSizeF
                val vf = (y - halfBlock) / blockSizeF
                
                val u0 = Math.max(0, Math.min(gridSize - 1, Math.floor(uf.toDouble()).toInt()))
                val u1 = Math.max(0, Math.min(gridSize - 1, u0 + 1))
                val v0 = Math.max(0, Math.min(gridSize - 1, Math.floor(vf.toDouble()).toInt()))
                val v1 = Math.max(0, Math.min(gridSize - 1, v0 + 1))
                
                val wu = uf - u0
                val wv = vf - v0
                
                val score = (1.0f - wu) * (1.0f - wv) * importanceGrid[v0][u0] +
                            wu * (1.0f - wv) * importanceGrid[v0][u1] +
                            (1.0f - wu) * wv * importanceGrid[v1][u0] +
                            wu * wv * importanceGrid[v1][u1]
                
                val r: Float
                val g: Float
                val b: Float
                if (score < 0.25f) {
                    r = 0.0f
                    g = score / 0.25f
                    b = 1.0f
                } else if (score < 0.5f) {
                    r = 0.0f
                    g = 1.0f
                    b = 1.0f - (score - 0.25f) / 0.25f
                } else if (score < 0.75f) {
                    r = (score - 0.5f) / 0.25f
                    g = 1.0f
                    b = 0.0f
                } else {
                    r = 1.0f
                    g = 1.0f - (score - 0.75f) / 0.25f
                    b = 0.0f
                }
                
                val alpha = (score * 160f).toInt().coerceIn(0, 160)
                val color = (alpha shl 24) or ((r * 255).toInt() shl 16) or ((g * 255).toInt() shl 8) or (b * 255).toInt()
                heatmapBitmap.setPixel(x, y, color)
            }
        }
        
        return try {
            val outputStream = ByteArrayOutputStream()
            heatmapBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            val base64Bytes = android.util.Base64.encodeToString(outputStream.toByteArray(), android.util.Base64.DEFAULT)
            heatmapBitmap.recycle()
            "data:image/png;base64,$base64Bytes"
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun sigmoid(x: Float): Float {
        return (1.0 / (1.0 + exp(-x.toDouble()))).toFloat()
    }

    private fun loadBitmap(uri: Uri): Bitmap? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            null
        }
    }
}
