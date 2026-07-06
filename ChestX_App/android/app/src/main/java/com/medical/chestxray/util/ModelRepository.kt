package com.medical.chestxray.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.IOException

/** Metadata + location of a classification model. [filePath] == null means the bundled asset. */
data class ModelInfo(
    val name: String,
    val architecture: String,
    val parameters: String,
    val version: String,
    val filePath: String?,
    val sizeBytes: Long
) {
    val isBundled: Boolean get() = filePath == null
}

/**
 * Stores the active CNN model. The app ships a bundled default ONNX; the user can
 * side-load their own `.onnx` (float32) model, whose architecture / parameters /
 * version they describe at import time. Metadata is persisted in SharedPreferences
 * and the file is copied into app-private storage.
 */
object ModelRepository {

    const val BUNDLED_ASSET = "model_float32.onnx"

    private const val PREFS = "chestx_model_prefs"
    private const val KEY_PATH = "active_model_path"
    private const val KEY_NAME = "active_model_name"
    private const val KEY_ARCH = "active_model_arch"
    private const val KEY_PARAMS = "active_model_params"
    private const val KEY_VERSION = "active_model_version"
    private const val KEY_SIZE = "active_model_size"

    private val bundledInfo = ModelInfo(
        name = "Default (bundled)",
        architecture = "EfficientNetV2-S",
        parameters = "20 M",
        version = "v1.0.4-stable",
        filePath = null,
        sizeBytes = 0L
    )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The model currently in use — the side-loaded one if present & valid, else the bundled default. */
    fun getActiveModel(context: Context): ModelInfo {
        val p = prefs(context)
        val path = p.getString(KEY_PATH, null)
        if (path.isNullOrBlank() || !File(path).exists()) {
            return bundledInfo
        }
        return ModelInfo(
            name = p.getString(KEY_NAME, "Custom model") ?: "Custom model",
            architecture = p.getString(KEY_ARCH, "Unknown") ?: "Unknown",
            parameters = p.getString(KEY_PARAMS, "—") ?: "—",
            version = p.getString(KEY_VERSION, "—") ?: "—",
            filePath = path,
            sizeBytes = p.getLong(KEY_SIZE, File(path).length())
        )
    }

    /** Changes whenever the active model changes — used to know when to rebuild the runner. */
    fun signature(context: Context): String {
        val info = getActiveModel(context)
        return (info.filePath ?: "bundled") + ":" + info.sizeBytes
    }

    /** Copies the picked ONNX into private storage and records its metadata. */
    fun importModel(
        context: Context,
        uri: Uri,
        name: String,
        architecture: String,
        parameters: String,
        version: String
    ): Result<ModelInfo> {
        return try {
            val dir = File(context.filesDir, "models").apply { mkdirs() }
            val dest = File(dir, "active_model.onnx")

            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Unable to open the selected file.")
            input.use { source ->
                dest.outputStream().use { out -> source.copyTo(out) }
            }

            if (dest.length() <= 0L) throw IOException("The selected file is empty.")

            prefs(context).edit()
                .putString(KEY_PATH, dest.absolutePath)
                .putString(KEY_NAME, name)
                .putString(KEY_ARCH, architecture)
                .putString(KEY_PARAMS, parameters)
                .putString(KEY_VERSION, version)
                .putLong(KEY_SIZE, dest.length())
                .apply()

            Result.success(getActiveModel(context))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Deletes the side-loaded model and reverts to the bundled default. */
    fun resetToDefault(context: Context) {
        prefs(context).getString(KEY_PATH, null)?.let { File(it).delete() }
        prefs(context).edit().clear().apply()
    }
}
