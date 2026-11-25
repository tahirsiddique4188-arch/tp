package com.example.util

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object GitHubCloner {

    private const val TAG = "GitHubCloner"

    fun getProjectsDir(context: Context? = null): File {
        if (context != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            return File(context.getExternalFilesDir(null) ?: context.filesDir, "TitanProjects").apply { mkdirs() }
        }
        val root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val titanDir = File(root, "TitanProjects")
        if (!titanDir.exists() && !titanDir.mkdirs() && context != null) {
            return File(context.getExternalFilesDir(null) ?: context.filesDir, "TitanProjects").apply { mkdirs() }
        }
        return titanDir
    }

    /**
     * Converts standard GitHub URL into a zip download URL if needed.
     * e.g. https://github.com/user/repo -> https://github.com/user/repo/archive/refs/heads/main.zip
     */
    fun normalizeToZipUrl(inputUrl: String): String {
        val trimmed = inputUrl.trim()
        if (trimmed.endsWith(".zip", ignoreCase = true)) {
            return trimmed
        }
        val cleanUrl = trimmed.removeSuffix("/").removeSuffix(".git")
        if (cleanUrl.contains("github.com") && !cleanUrl.contains("/archive/")) {
            return "$cleanUrl/archive/refs/heads/main.zip"
        }
        return trimmed
    }

    fun extractRepoName(inputUrl: String): String {
        val cleanUrl = inputUrl.trim().removeSuffix("/").removeSuffix(".git").removeSuffix(".zip")
        val segments = cleanUrl.split("/")
        val lastSegment = segments.lastOrNull() ?: "project_${System.currentTimeMillis()}"
        return if (lastSegment == "main" || lastSegment == "master") {
            val secondLast = segments.getOrNull(segments.size - 2)
            if (!secondLast.isNullOrBlank() && secondLast != "heads" && secondLast != "refs") {
                secondLast
            } else {
                "project_${System.currentTimeMillis()}"
            }
        } else {
            lastSegment
        }
    }

    suspend fun cloneRepository(context: Context, repoUrl: String): Result<File> {
        return cloneAndExtractRepo(repoUrl = repoUrl, onProgress = { _, _ -> })
    }

    suspend fun cloneAndExtractRepo(
        repoUrl: String,
        targetRepoName: String? = null,
        onProgress: (progressPercent: Int, statusMessage: String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val zipUrl = normalizeToZipUrl(repoUrl)
            val repoName = targetRepoName ?: extractRepoName(repoUrl)
            val targetFolder = File(getProjectsDir(), repoName)

            if (!targetFolder.exists()) {
                targetFolder.mkdirs()
            }

            onProgress(10, "Connecting to GitHub ($zipUrl)...")

            val client = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()

            var response = client.newCall(Request.Builder().url(zipUrl).build()).execute()

            // Fallback from main.zip to master.zip if main is 404
            if (!response.isSuccessful && zipUrl.endsWith("/main.zip")) {
                val masterUrl = zipUrl.replace("/main.zip", "/master.zip")
                onProgress(15, "Trying master branch ($masterUrl)...")
                response = client.newCall(Request.Builder().url(masterUrl).build()).execute()
            }

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP Error ${response.code}: ${response.message}"))
            }

            val body = response.body ?: return@withContext Result.failure(Exception("Empty response body from GitHub"))
            val contentLength = body.contentLength()
            val inputStream: InputStream = body.byteStream()

            onProgress(30, "Downloading & Unpacking repository archive...")

            val zipInputStream = ZipInputStream(inputStream)
            var entry: ZipEntry? = zipInputStream.nextEntry

            // Check if all files are inside a root folder like repo-main/
            var rootPrefixToStrip = ""
            val buffer = ByteArray(8192)
            var extractedFilesCount = 0
            var bytesReadTotal = 0L

            while (entry != null) {
                val entryName = entry.name

                if (extractedFilesCount == 0 && entry.isDirectory) {
                    rootPrefixToStrip = entryName
                }

                val relativePath = if (rootPrefixToStrip.isNotEmpty() && entryName.startsWith(rootPrefixToStrip)) {
                    entryName.substring(rootPrefixToStrip.length)
                } else {
                    entryName
                }

                if (relativePath.isNotEmpty()) {
                    val outFile = File(targetFolder, relativePath)

                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        val fos = FileOutputStream(outFile)
                        var read = zipInputStream.read(buffer)
                        while (read != -1) {
                            fos.write(buffer, 0, read)
                            bytesReadTotal += read
                            read = zipInputStream.read(buffer)
                        }
                        fos.close()
                        extractedFilesCount++
                    }
                }

                if (contentLength > 0) {
                    val progress = (30 + (bytesReadTotal.toFloat() / contentLength.toFloat() * 60f)).toInt().coerceIn(30, 95)
                    onProgress(progress, "Unpacking $extractedFilesCount files...")
                } else {
                    val progress = (30 + (extractedFilesCount % 60)).coerceIn(30, 95)
                    onProgress(progress, "Extracted $extractedFilesCount files...")
                }

                zipInputStream.closeEntry()
                entry = zipInputStream.nextEntry
            }

            zipInputStream.close()
            inputStream.close()

            // If index.html doesn't exist, create default index.html if empty
            val indexFile = File(targetFolder, "index.html")
            if (!indexFile.exists()) {
                val foundHtml = targetFolder.walkTopDown().firstOrNull { it.extension.equals("html", ignoreCase = true) }
                if (foundHtml == null) {
                    indexFile.writeText(
                        """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <title>$repoName</title>
                            <style>body { background: #131314; color: #00E5FF; font-family: sans-serif; padding: 24px; text-align: center; }</style>
                        </head>
                        <body>
                            <h1>Project $repoName</h1>
                            <p>Cloned successfully from GitHub!</p>
                        </body>
                        </html>
                        """.trimIndent()
                    )
                }
            }

            onProgress(100, "Successfully imported $extractedFilesCount files!")
            Result.success(targetFolder)
        } catch (e: Exception) {
            Log.e(TAG, "Clone error: ${e.message}", e)
            Result.failure(e)
        }
    }
}
