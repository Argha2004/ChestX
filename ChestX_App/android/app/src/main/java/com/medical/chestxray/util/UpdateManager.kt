package com.medical.chestxray.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

data class UpdateInfo(
    val latestVersion: String,
    val currentVersion: String,
    val releaseNotes: String,
    val apkUrl: String?,
    val releasePageUrl: String
)

/** Outcome of a GitHub release lookup, so the UI can show an accurate reason. */
sealed interface ReleaseResult {
    data class Ok(val info: UpdateInfo) : ReleaseResult
    /** The repo exists path returned 404 — no published release, or the repo name is wrong. */
    object NoReleases : ReleaseResult
    data class Error(val reason: String) : ReleaseResult
}

/**
 * Checks GitHub Releases for a newer version of the app and downloads/installs the APK.
 *
 * IMPORTANT — this reads a PUBLIC repository (no token is shipped in the app, so nothing
 * can be leaked by decompiling the APK). If your source code is in a PRIVATE repo, keep it
 * private and create a small SEPARATE PUBLIC repo (e.g. "ChestX-releases") that hosts only
 * the release APKs, then point [GITHUB_REPO] at that public repo.
 *
 * How to publish an update:
 *  1. Bump `versionCode` and `versionName` in app/build.gradle.kts (versionCode MUST increase).
 *  2. Build `ChestX.apk`.
 *  3. In the PUBLIC releases repo, create a Release tagged like `v1.1.0` and attach `ChestX.apk`.
 * The app detects the new tag, downloads the attached APK, and prompts to install.
 */
object UpdateManager {

    // ── Point these at a PUBLIC repo that hosts your release APKs ──
    private const val GITHUB_OWNER = "Argha2004"
    private const val GITHUB_REPO = "ChestX-releases"

    private const val TAG = "UpdateManager"

    private val client = OkHttpClient()
    private val gson = Gson()

    private data class Release(
        @SerializedName("tag_name") val tagName: String?,
        @SerializedName("name") val name: String?,
        @SerializedName("body") val body: String?,
        @SerializedName("html_url") val htmlUrl: String?,
        @SerializedName("assets") val assets: List<Asset>?
    )

    private data class Asset(
        @SerializedName("name") val name: String?,
        @SerializedName("browser_download_url") val downloadUrl: String?
    )

    private fun releasesPageUrl() = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    /** Queries the GitHub "latest release" API and reports an accurate outcome. */
    fun fetchLatestRelease(currentVersion: String): ReleaseResult {
        return try {
            val url = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "ChestX-App") // GitHub API requires a User-Agent
                .build()

            client.newCall(request).execute().use { response ->
                when {
                    // 404 = repo has no published release yet, OR the owner/repo name is wrong.
                    response.code == 404 -> {
                        Log.w(TAG, "404 for $GITHUB_OWNER/$GITHUB_REPO — no release published or wrong repo name.")
                        ReleaseResult.NoReleases
                    }
                    response.code == 403 -> {
                        Log.w(TAG, "403 — GitHub API rate limit reached.")
                        ReleaseResult.Error("GitHub rate limit reached. Try again later.")
                    }
                    !response.isSuccessful -> {
                        Log.w(TAG, "GitHub API returned HTTP ${response.code}")
                        ReleaseResult.Error("Server responded with HTTP ${response.code}")
                    }
                    else -> {
                        val json = response.body?.string()
                            ?: return ReleaseResult.Error("Empty response from server")
                        val release = gson.fromJson(json, Release::class.java)
                        val tag = release.tagName ?: return ReleaseResult.NoReleases
                        val apk = release.assets
                            ?.firstOrNull { it.name?.endsWith(".apk", ignoreCase = true) == true }
                            ?.downloadUrl

                        ReleaseResult.Ok(
                            UpdateInfo(
                                latestVersion = tag,
                                currentVersion = currentVersion,
                                releaseNotes = release.body?.trim().takeUnless { it.isNullOrBlank() }
                                    ?: (release.name ?: "A new version is available."),
                                apkUrl = apk,
                                releasePageUrl = release.htmlUrl ?: releasesPageUrl()
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed", e)
            ReleaseResult.Error(e.message ?: "Network error")
        }
    }

    /** True when [latest] (e.g. "v1.2.0") is a higher version than [current] (e.g. "1.0.0"). */
    fun isNewer(latest: String, current: String): Boolean {
        val l = parse(latest)
        val c = parse(current)
        val n = maxOf(l.size, c.size)
        for (i in 0 until n) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv != cv) return lv > cv
        }
        return false
    }

    private fun parse(version: String): List<Int> =
        version.trim().removePrefix("v").removePrefix("V")
            .split(".", "-", "_")
            .mapNotNull { part -> part.takeWhile { it.isDigit() }.toIntOrNull() }

    /** Downloads the APK into cache, reporting 0..100 progress. Returns the file or null. */
    fun downloadApk(context: Context, url: String, onProgress: (Int) -> Unit): File? {
        return try {
            val request = Request.Builder().url(url).header("User-Agent", "ChestX-App").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                val total = body.contentLength()
                val file = File(context.cacheDir, "ChestX-update.apk")
                if (file.exists()) file.delete()

                body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var downloaded = 0L
                        var lastPercent = -1
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (total > 0) {
                                val percent = ((downloaded * 100) / total).toInt()
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    onProgress(percent)
                                }
                            }
                        }
                    }
                }
                file
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Launches the system package installer for the downloaded APK. */
    fun installApk(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "com.medical.chestxray.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Opens the GitHub release page (used when a release has no attached APK). */
    fun openReleasePage(context: Context, url: String) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
