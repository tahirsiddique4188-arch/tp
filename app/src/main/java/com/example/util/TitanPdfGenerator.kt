package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object TitanPdfGenerator {

    fun generateAndOpenFeaturesPdf(context: Context): File? {
        try {
            val pdfDocument = PdfDocument()
            val pageWidth = 595 // A4 width in points
            val pageHeight = 842 // A4 height in points

            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas

            val titlePaint = Paint().apply {
                color = Color.parseColor("#101828")
                textSize = 20f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val subtitlePaint = Paint().apply {
                color = Color.parseColor("#7C4DFF")
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val sectionHeadingPaint = Paint().apply {
                color = Color.parseColor("#00E5FF")
                textSize = 14f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val featureTitlePaint = Paint().apply {
                color = Color.parseColor("#1F2937")
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val bodyPaint = Paint().apply {
                color = Color.parseColor("#4B5563")
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }

            val linePaint = Paint().apply {
                color = Color.parseColor("#E5E7EB")
                strokeWidth = 1f
            }

            var y = 50f
            val margin = 40f
            val contentWidth = pageWidth - (margin * 2)

            // Header Banner
            canvas.drawRect(margin, y, pageWidth - margin, y + 60f, Paint().apply { color = Color.parseColor("#131314") })
            canvas.drawText("TITAN IDE — Complete Feature Documentation", margin + 15f, y + 35f, Paint().apply {
                color = Color.parseColor("#00E5FF")
                textSize = 16f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            })
            canvas.drawText("Engineered by Developer Tahir (@Tahirsiddik)", margin + 15f, y + 50f, Paint().apply {
                color = Color.parseColor("#FB923C")
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            })

            y += 80f

            val featuresList = listOf(
                FeatureItem("1. Pure Google AI Ecosystem", "Active Google Gemini Suite (Gemini 2.5 Pro, Flash, Thinking, Live, Google Imagen 3, and Google Veo 3.1 Flow)."),
                FeatureItem("2. Local REST API Server & Developer Hub", "Local REST Gateway at http://127.0.0.1:8080/v1/chat/completions. Connect from Termux, Python, or Web Apps directly with Titan API Keys (titan-sk-xxxx-xxxx)."),
                FeatureItem("3. Gemini Studio 30-Voice Character Library", "30 official Gemini Studio AI voice characters (Aryan J.A.R.V.I.S., Zara, Kabir, Dev, etc.). Audition with real-time test speech sample buttons."),
                FeatureItem("4. Live Web App Preview Engine (127.0.0.1:8080)", "Run HTML5, CSS3, and JavaScript web applications and games instantly. Built-in Chrome-style WebView Preview, Console Trace, and Auto-Fix with AI."),
                FeatureItem("5. Creative Broadcast & Podcast Studio", "AI Audio Story Podcast and Shorts/Reels Script Generator. Multi-speaker dialogue generation with direct MP4 video export."),
                FeatureItem("6. Public GitHub Repository Cloner", "Clone public GitHub repositories directly to /sdcard/TitanProjects/ to run and edit code in Titan IDE."),
                FeatureItem("7. Surgical AI Code Refactor", "Automated suite for whole-project refactoring, dark mode migration, bug fixing, and custom optimizations."),
                FeatureItem("8. Surgical Git Diff Viewer & Direct File Apply", "Visual diff viewer with (+) Green added and (-) Red deleted line highlights and direct [Apply to File] action."),
                FeatureItem("9. In-App AI Image & Video Preview Cards", "Markdown image auto-detection with Coil AsyncImage zoom preview and in-app video playback cards."),
                FeatureItem("10. Smart Web Reader & Link Intelligence", "Auto-detect URLs in user input, scrape pages with OkHttp and Jsoup, and provide context-grounded summaries."),
                FeatureItem("11. Multi-Format Document Attachment", "Read and inspect .py, .js, .html, .kt, .json, .pdf, and .txt files to debug and refactor full codebases."),
                FeatureItem("12. Hey Titan Live Voice Overlay & Assistant", "Hands-free continuous voice interaction with customizable wake words and real-time live streaming audio."),
                FeatureItem("13. Automation & Device Control Suite", "Flashlight toggle, System Volume/Mute, QR/Barcode Scanner, Picture-in-Picture, and Shortcut Launcher."),
                FeatureItem("14. Memory Vault (Identity & Facts)", "Store user preferences, project identity, and key-value memories in secure on-device encrypted storage."),
                FeatureItem("15. Multi-Theme Engine & Dynamic Soft Colors", "Custom themes including Aurora Dream (Sky Blue, Soft Pink, Sunset Orange), Cyberpunk Obsidian, and Pastel Sunset.")
            )

            fun checkPageBreak(neededHeight: Float) {
                if (y + neededHeight > pageHeight - margin) {
                    pdfDocument.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    y = 50f

                    // Draw Header on new page
                    canvas.drawText("TITAN IDE — Features Manual (Page $pageNumber)", margin, y, subtitlePaint)
                    y += 25f
                    canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
                    y += 20f
                }
            }

            featuresList.forEach { item ->
                checkPageBreak(65f)

                canvas.drawText(item.title, margin, y, featureTitlePaint)
                y += 16f

                // Break text into lines
                val words = item.description.split(" ")
                var currentLine = ""
                for (word in words) {
                    val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                    if (bodyPaint.measureText(testLine) > contentWidth) {
                        canvas.drawText(currentLine, margin, y, bodyPaint)
                        y += 14f
                        currentLine = word
                    } else {
                        currentLine = testLine
                    }
                }
                if (currentLine.isNotEmpty()) {
                    canvas.drawText(currentLine, margin, y, bodyPaint)
                    y += 14f
                }

                y += 10f
                canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
                y += 15f
            }

            pdfDocument.finishPage(page)

            // Save PDF File to SDCard
            val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            val targetDir = File(baseDir, "TitanProjects")
            if (!targetDir.exists()) targetDir.mkdirs()

            val pdfFile = File(targetDir, "Titan_IDE_Features_Manual.pdf")
            FileOutputStream(pdfFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()

            // Open / Share PDF Intent
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", pdfFile)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            try {
                context.startActivity(Intent.createChooser(intent, "Open Titan Features Manual PDF"))
            } catch (e: Exception) {
                Toast.makeText(context, "PDF saved to: ${pdfFile.absolutePath}", Toast.LENGTH_LONG).show()
            }

            return pdfFile
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "PDF Error: ${e.message}", Toast.LENGTH_SHORT).show()
            return null
        }
    }

    private data class FeatureItem(
        val title: String,
        val description: String
    )
}
