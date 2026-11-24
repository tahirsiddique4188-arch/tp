package com.titan.ide.storage

import android.content.Context
import android.os.Environment
import android.util.Log
import com.titan.ide.server.LocalProjectServer
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * TitanWorkspaceManager - Multi-Language Storage Dispatcher & Workspace Manager
 * Developer: Developer Tahir (@Tahirsiddik)
 */
object TitanWorkspaceManager {

    private const val TAG = "TitanWorkspace"

    data class ProjectManifest(
        val projectName: String,
        val language: String,
        val rootPath: String,
        val fileList: List<String>,
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Resolves project directory under /sdcard/TitanProjects/<AppName>/ with fallback.
     */
    fun resolveProjectDirectory(context: Context, projectName: String): File {
        val safeName = projectName.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.ifBlank { "TitanProject" }
        val isScopedStorageRestricted = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R && !Environment.isExternalStorageManager()
        return try {
            if (!isScopedStorageRestricted) {
                val sdcard = File("/sdcard/TitanProjects")
                if (sdcard.exists() || sdcard.mkdirs()) {
                    val target = File(sdcard, safeName)
                    if (target.exists() || target.mkdirs()) return target
                }
            }
            val ext = context.getExternalFilesDir("TitanProjects")
            if (ext != null) {
                File(ext, safeName).apply { mkdirs() }
            } else {
                File(context.filesDir, "TitanProjects/$safeName").apply { mkdirs() }
            }
        } catch (e: Exception) {
            File(context.filesDir, "TitanProjects/$safeName").apply { mkdirs() }
        }
    }

    /**
     * Parses generated code payload, extracts named files, and dispatches them to storage.
     * Matches patterns:
     * - `// File: MainActivity.kt`
     * - `<!-- File: index.html -->`
     * - `# File: main.py`
     * - `/* File: style.css */`
     */
    fun dispatchAndSaveFiles(
        context: Context,
        projectName: String,
        language: String,
        generatedCodePayload: Map<String, String>
    ): File {
        val targetDir = resolveProjectDirectory(context, projectName)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        val savedFiles = mutableListOf<String>()

        for ((fileName, fileContent) in generatedCodePayload) {
            val cleanName = fileName.removePrefix("/").trim()
            if (cleanName.isNotBlank()) {
                val file = File(targetDir, cleanName)
                file.parentFile?.mkdirs()
                file.writeText(fileContent, Charsets.UTF_8)
                savedFiles.add(cleanName)
            }
        }

        // Write Project Manifest
        val manifestContent = """
            {
                "projectName": "$projectName",
                "language": "$language",
                "files": [${savedFiles.joinToString { "\"$it\"" }}],
                "created": ${System.currentTimeMillis()}
            }
        """.trimIndent()
        File(targetDir, "titan.manifest.json").writeText(manifestContent, Charsets.UTF_8)

        // Dispatch based on Language
        if (language.equals("WEB", ignoreCase = true) || savedFiles.contains("index.html")) {
            LocalProjectServer.bindProject(targetDir)
            LocalProjectServer.fallbackHtml = generatedCodePayload["index.html"] ?: ""
            LocalProjectServer.fallbackCss = generatedCodePayload["style.css"] ?: ""
            LocalProjectServer.fallbackJs = generatedCodePayload["app.js"] ?: ""
            Log.d(TAG, "Bound Web Project '$projectName' to 127.0.0.1:8080")
        }

        Log.d(TAG, "Dispatched ${savedFiles.size} files to ${targetDir.absolutePath}")
        return targetDir
    }

    /**
     * Multi-format robust markdown code block parser.
     * Extracts named files from:
     * 1. ```lang:filename.ext or ```filename.ext
     * 2. // File: filename, <!-- File: filename -->, # File: filename, /* File: filename */
     * 3. ### filename.ext, **filename.ext**, `filename.ext` headers
     */
    fun parseNamedFilesFromMarkdown(rawText: String): Map<String, String> {
        val resultMap = mutableMapOf<String, String>()
        if (rawText.isBlank()) return resultMap

        // Pattern 1: ```lang:filename.ext or ```filename.ext
        val fencedRegex = Regex("""```(?:[a-zA-Z0-9_]+:)?([a-zA-Z0-9_.\-/]+\.[a-zA-Z0-9]+)\s*\n([\s\S]*?)```""")
        for (match in fencedRegex.findAll(rawText)) {
            val fileName = match.groupValues[1].substringAfterLast("/").trim()
            val code = match.groupValues[2].trim()
            if (fileName.isNotBlank() && code.isNotBlank()) {
                resultMap[fileName] = code
            }
        }

        // Pattern 2: // File: filename, <!-- File: filename -->, # File: filename, /* File: filename */
        val fileCommentRegex = Regex("""(?m)^(?:/{2}|<!--|#|/\*)\s*File:\s*([a-zA-Z0-9_.\-/]+)\s*(?:-->|\*/)?$""")
        val matches = fileCommentRegex.findAll(rawText).toList()
        if (matches.isNotEmpty()) {
            for (i in matches.indices) {
                val fileName = matches[i].groupValues[1].substringAfterLast("/").trim()
                val startIdx = matches[i].range.last + 1
                val endIdx = if (i < matches.size - 1) matches[i + 1].range.first else rawText.length
                val rawBlock = rawText.substring(startIdx, endIdx).trim()
                val cleanCode = rawBlock.replace(Regex("""^```[a-zA-Z0-9_]*\n?"""), "")
                    .replace(Regex("""\n?```$"""), "")
                    .trim()
                if (fileName.isNotBlank() && cleanCode.isNotBlank()) {
                    resultMap[fileName] = cleanCode
                }
            }
        }

        // Pattern 3: Header format (### index.html or **style.css** or `script.js`) followed by ```code```
        if (resultMap.isEmpty()) {
            val headerBlockRegex = Regex("""(?m)^(?:###\s+|\*\*|`)([a-zA-Z0-9_.\-/]+\.[a-zA-Z0-9]+)(?:\*\*|`)?\s*\n+```[a-zA-Z0-9_]*\n([\s\S]*?)```""")
            for (m in headerBlockRegex.findAll(rawText)) {
                val fileName = m.groupValues[1].substringAfterLast("/").trim()
                val code = m.groupValues[2].trim()
                if (fileName.isNotBlank() && code.isNotBlank()) {
                    resultMap[fileName] = code
                }
            }
        }

        return resultMap
    }

    /**
     * Exports workspace project as a Zip archive safely on Android 10-15 Scoped Storage.
     */
    fun exportWorkspaceToZip(context: Context, projectName: String, files: Map<String, String>): File? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val zipFileName = "${projectName}_$timestamp.zip"

            val targetDir = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            } else {
                val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                File(publicDownloads, "TitanProjects").apply { if (!exists()) mkdirs() }
            }
            val zipFile = File(targetDir, zipFileName)

            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                files.forEach { (name, content) ->
                    val entry = ZipEntry(name)
                    zos.putNextEntry(entry)
                    zos.write(content.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }
            }
            zipFile
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting workspace ZIP: ${e.message}")
            null
        }
    }
}
