package com.example.util

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

sealed class MessagePart {
    data class Text(val content: String) : MessagePart()
    data class Code(
        val language: String,
        val code: String,
        val isRunnableWeb: Boolean
    ) : MessagePart()
}

object CodeBlockParser {

    private val CODE_FENCE_REGEX = Regex("```([a-zA-Z0-9_+#.-]*)\\n?([\\s\\S]*?)```")

    fun parseMessage(rawText: String): List<MessagePart> {
        val parts = mutableListOf<MessagePart>()
        var lastIndex = 0

        val matches = CODE_FENCE_REGEX.findAll(rawText).toList()

        if (matches.isEmpty()) {
            val unclosedIndex = rawText.indexOf("```")
            if (unclosedIndex != -1) {
                val pre = rawText.substring(0, unclosedIndex).trim()
                if (pre.isNotEmpty()) {
                    parts.add(MessagePart.Text(pre))
                }
                val codeAfterFence = rawText.substring(unclosedIndex + 3)
                val firstNewline = codeAfterFence.indexOf('\n')
                val rawLang = if (firstNewline != -1) codeAfterFence.substring(0, firstNewline).trim() else ""
                val codeBody = if (firstNewline != -1) codeAfterFence.substring(firstNewline + 1) else ""
                val lang = if (rawLang.isEmpty()) detectLanguage(codeBody) else rawLang.lowercase()
                parts.add(
                    MessagePart.Code(
                        language = lang.ifEmpty { "code" },
                        code = codeBody,
                        isRunnableWeb = isRunnableWebCode(lang, codeBody)
                    )
                )
                return parts
            }
            return listOf(MessagePart.Text(rawText))
        }

        for (match in matches) {
            val range = match.range
            if (range.first > lastIndex) {
                val preText = rawText.substring(lastIndex, range.first).trim()
                if (preText.isNotEmpty()) {
                    parts.add(MessagePart.Text(preText))
                }
            }

            val rawLang = match.groupValues[1].trim()
            val codeContent = match.groupValues[2].trimEnd()
            val lang = if (rawLang.isEmpty()) detectLanguage(codeContent) else rawLang.lowercase()
            val isWeb = isRunnableWebCode(lang, codeContent)

            parts.add(
                MessagePart.Code(
                    language = lang.ifEmpty { "code" },
                    code = codeContent,
                    isRunnableWeb = isWeb
                )
            )

            lastIndex = range.last + 1
        }

        if (lastIndex < rawText.length) {
            val postText = rawText.substring(lastIndex)
            val unclosedIndex = postText.indexOf("```")
            if (unclosedIndex != -1) {
                val pre = postText.substring(0, unclosedIndex).trim()
                if (pre.isNotEmpty()) {
                    parts.add(MessagePart.Text(pre))
                }
                val codeAfterFence = postText.substring(unclosedIndex + 3)
                val firstNewline = codeAfterFence.indexOf('\n')
                val rawLang = if (firstNewline != -1) codeAfterFence.substring(0, firstNewline).trim() else ""
                val codeBody = if (firstNewline != -1) codeAfterFence.substring(firstNewline + 1) else ""
                val lang = if (rawLang.isEmpty()) detectLanguage(codeBody) else rawLang.lowercase()
                parts.add(
                    MessagePart.Code(
                        language = lang.ifEmpty { "code" },
                        code = codeBody,
                        isRunnableWeb = isRunnableWebCode(lang, codeBody)
                    )
                )
            } else {
                val trimmedPost = postText.trim()
                if (trimmedPost.isNotEmpty()) {
                    parts.add(MessagePart.Text(trimmedPost))
                }
            }
        }

        return parts
    }

    private fun detectLanguage(code: String): String {
        val trimmed = code.trim()
        return when {
            trimmed.startsWith("<!DOCTYPE html", ignoreCase = true) ||
            trimmed.contains("<html", ignoreCase = true) ||
            trimmed.contains("<canvas", ignoreCase = true) -> "html"
            trimmed.startsWith("import React", ignoreCase = true) || trimmed.contains("function ") || trimmed.contains("const ") || trimmed.contains("let ") -> "javascript"
            trimmed.contains("def ") || trimmed.contains("import ") && trimmed.contains("print(") -> "python"
            trimmed.contains("package ") || trimmed.contains("fun ") || trimmed.contains("val ") -> "kotlin"
            trimmed.contains("{") && trimmed.contains(":") && trimmed.contains(";") -> "css"
            else -> "text"
        }
    }

    fun isRunnableWebCode(language: String, code: String): Boolean {
        val lang = language.lowercase()
        if (lang in listOf("html", "htm", "javascript", "js", "web", "css")) return true
        val trimmed = code.trim()
        return trimmed.contains("<html", ignoreCase = true) ||
               trimmed.contains("<!DOCTYPE", ignoreCase = true) ||
               trimmed.contains("<script", ignoreCase = true) ||
               trimmed.contains("<canvas", ignoreCase = true) ||
               trimmed.contains("<style", ignoreCase = true)
    }

    fun wrapAsCompleteHtml(code: String, language: String): String {
        val trimmed = code.trim()
        if (trimmed.contains("<html", ignoreCase = true) || trimmed.contains("<!DOCTYPE", ignoreCase = true)) {
            return trimmed
        }
        return when (language.lowercase()) {
            "javascript", "js" -> """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <script src="https://cdn.tailwindcss.com"></script>
                    <style>
                        body { background: #0f172a; color: #f8fafc; font-family: sans-serif; padding: 16px; margin: 0; }
                    </style>
                </head>
                <body>
                    <div id="app"></div>
                    <script>
                        try {
                            $code
                        } catch (err) {
                            document.body.innerHTML += '<pre style="color:#ef4444;background:#1e293b;padding:12px;border-radius:8px;">' + err.toString() + '</pre>';
                        }
                    </script>
                </body>
                </html>
            """.trimIndent()
            "css" -> """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <style>
                        $code
                    </style>
                </head>
                <body>
                    <h1>CSS Preview</h1>
                    <p>Live stylesheet applied.</p>
                </body>
                </html>
            """.trimIndent()
            else -> """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <script src="https://cdn.tailwindcss.com"></script>
                    <style>
                        * { box-sizing: border-box; }
                        body { margin: 0; padding: 12px; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
                    </style>
                </head>
                <body>
                    $code
                </body>
                </html>
            """.trimIndent()
        }
    }

    fun saveCodeToTitanProjects(context: Context, code: String, language: String): String {
        return try {
            val extension = when (language.lowercase()) {
                "html", "htm" -> "html"
                "javascript", "js" -> "js"
                "css" -> "css"
                "python", "py" -> "py"
                "kotlin", "kt" -> "kt"
                "json" -> "json"
                "java" -> "java"
                else -> "txt"
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "Titan_${language.uppercase()}_$timestamp.$extension"

            val targetDir = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                File(context.getExternalFilesDir(null) ?: context.filesDir, "TitanProjects").apply { mkdirs() }
            } else {
                val baseDir = File(Environment.getExternalStorageDirectory(), "TitanProjects")
                if (baseDir.exists() || baseDir.mkdirs()) baseDir else File(context.getExternalFilesDir(null), "TitanProjects").apply { mkdirs() }
            }

            val file = File(targetDir, fileName)
            file.writeText(code, Charsets.UTF_8)

            val displayPath = file.absolutePath
            Toast.makeText(context, "Saved to $displayPath", Toast.LENGTH_LONG).show()
            displayPath
        } catch (e: Exception) {
            try {
                // Fallback to internal storage files
                val internalDir = File(context.filesDir, "TitanProjects").apply { mkdirs() }
                val fallbackFile = File(internalDir, "Titan_Code_${System.currentTimeMillis()}.txt")
                fallbackFile.writeText(code, Charsets.UTF_8)
                Toast.makeText(context, "Saved to ${fallbackFile.name}", Toast.LENGTH_SHORT).show()
                fallbackFile.absolutePath
            } catch (err: Exception) {
                Toast.makeText(context, "Failed to save file: ${err.message}", Toast.LENGTH_SHORT).show()
                ""
            }
        }
    }

    fun exportProjectAsZip(context: Context, codeBlocks: List<MessagePart.Code>): String {
        if (codeBlocks.isEmpty()) return ""
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val zipFileName = "Titan_Project_$timestamp.zip"

            // Target public Downloads directory
            val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val titanDownloadsDir = File(publicDownloads, "TitanProjects")
            val targetDir = if (titanDownloadsDir.exists() || titanDownloadsDir.mkdirs()) {
                titanDownloadsDir
            } else {
                File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "TitanProjects").apply { mkdirs() }
            }

            val zipFile = File(targetDir, zipFileName)
            val zipOut = ZipOutputStream(FileOutputStream(zipFile))

            val usedNames = mutableSetOf<String>()
            var htmlCount = 0
            var cssCount = 0
            var jsCount = 0
            var pyCount = 0

            codeBlocks.forEachIndexed { index, block ->
                val lang = block.language.lowercase()
                var name = when (lang) {
                    "html", "htm" -> if (htmlCount++ == 0) "index.html" else "page$htmlCount.html"
                    "css" -> if (cssCount++ == 0) "styles.css" else "style$cssCount.css"
                    "javascript", "js" -> if (jsCount++ == 0) "script.js" else "module$jsCount.js"
                    "python", "py" -> if (pyCount++ == 0) "main.py" else "script$pyCount.py"
                    "json" -> "config.json"
                    "kotlin", "kt" -> "Main.kt"
                    else -> "file_${index + 1}.${lang.ifEmpty { "txt" }}"
                }

                if (usedNames.contains(name)) {
                    name = "file_${index + 1}_$name"
                }
                usedNames.add(name)

                val entry = ZipEntry(name)
                zipOut.putNextEntry(entry)
                zipOut.write(block.code.toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()
            }

            zipOut.finish()
            zipOut.close()

            Toast.makeText(context, "Project exported: ${zipFile.name}", Toast.LENGTH_LONG).show()

            // Trigger Share Intent with FileProvider
            try {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    zipFile
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Titan Project Export: ${zipFile.name}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val chooser = Intent.createChooser(shareIntent, "Share or Open Project ZIP").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } catch (shareErr: Exception) {
                shareErr.printStackTrace()
            }

            zipFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "ZIP export error: ${e.message}", Toast.LENGTH_SHORT).show()
            ""
        }
    }
}
