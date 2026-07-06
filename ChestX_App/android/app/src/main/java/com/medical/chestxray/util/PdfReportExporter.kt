package com.medical.chestxray.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import android.widget.Toast
import androidx.core.content.FileProvider
import com.medical.chestxray.data.model.AnalysisResponse
import com.medical.chestxray.data.model.PatientInfo
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfReportExporter {

    // ── Document palette (restrained — a proper report, not an app screenshot) ──
    private const val COLOR_ACCENT = "#0891B2"    // Clinical cyan (used sparingly)
    private const val COLOR_INK = "#10242B"       // Near-black heading ink
    private const val COLOR_BODY = "#2B3A40"      // Body text
    private const val COLOR_MUTED = "#5C7882"     // Labels / secondary
    private const val COLOR_SUBTLE = "#93A7AD"    // Fine print
    private const val COLOR_RULE = "#C7D6DB"      // Hairline rules
    private const val COLOR_RULE_SOFT = "#E3ECEF" // Very light row rules
    private const val COLOR_CARD = "#F4F8FA"      // Image placeholder fill
    private const val COLOR_SUCCESS = "#0E7A5F"
    private const val COLOR_WARNING = "#B26A00"
    private const val COLOR_NEUTRAL = "#5C7882"

    private const val PAGE_LEFT = 40f
    private const val PAGE_RIGHT = 555f
    private const val CONTENT_WIDTH = PAGE_RIGHT - PAGE_LEFT

    private fun buildPdfDocument(
        context: Context,
        imageUri: Uri,
        patientInfo: PatientInfo?,
        result: AnalysisResponse
    ): PdfDocument {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val cAccent = Color.parseColor(COLOR_ACCENT)
        val cInk = Color.parseColor(COLOR_INK)
        val cBody = Color.parseColor(COLOR_BODY)
        val cMuted = Color.parseColor(COLOR_MUTED)
        val cSubtle = Color.parseColor(COLOR_SUBTLE)
        val cRule = Color.parseColor(COLOR_RULE)
        val cRuleSoft = Color.parseColor(COLOR_RULE_SOFT)

        val serifBold = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        val sans = Typeface.DEFAULT
        val sansBold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val mono = Typeface.MONOSPACE

        val reportNo = "CXR-" + result.id.filter { it.isDigit() }.takeLast(8)

        // ───────────────────────────── LETTERHEAD ─────────────────────────────
        canvas.drawText("ChestX", PAGE_LEFT, 52f,
            Paint().apply { color = cInk; textSize = 25f; typeface = serifBold; isAntiAlias = true })
        canvas.drawText("Automated Thoracic Radiograph Analysis", PAGE_LEFT, 65f,
            Paint().apply { color = cMuted; textSize = 8f; typeface = sans; isAntiAlias = true })

        val titleRight = Paint().apply {
            color = cInk; textSize = 12f; typeface = sansBold
            textAlign = Paint.Align.RIGHT; letterSpacing = 0.12f; isAntiAlias = true
        }
        canvas.drawText("CHEST RADIOGRAPH REPORT", PAGE_RIGHT, 44f, titleRight)
        val metaRight = Paint().apply {
            color = cMuted; textSize = 8f; typeface = sans
            textAlign = Paint.Align.RIGHT; isAntiAlias = true
        }
        canvas.drawText("Report No.  $reportNo", PAGE_RIGHT, 58f, metaRight)
        canvas.drawText("Generated  ${formatTimestamp(result.timestamp)}", PAGE_RIGHT, 69f, metaRight)

        // Double rule (classic letterhead separator)
        canvas.drawLine(PAGE_LEFT, 80f, PAGE_RIGHT, 80f,
            Paint().apply { color = cInk; strokeWidth = 1.4f; isAntiAlias = true })
        canvas.drawLine(PAGE_LEFT, 83.5f, PAGE_RIGHT, 83.5f,
            Paint().apply { color = cInk; strokeWidth = 0.6f; isAntiAlias = true })

        var y = 102f

        // ─────────────────────── PATIENT & STUDY DETAILS ───────────────────────
        drawHeading(canvas, "PATIENT & STUDY DETAILS", y, cInk, cAccent, cRule, sansBold)
        y += 14f

        val labelPaint = Paint().apply {
            color = cMuted; textSize = 7.5f; typeface = sansBold; letterSpacing = 0.04f; isAntiAlias = true
        }
        val valuePaint = Paint().apply { color = cBody; textSize = 9.5f; typeface = sans; isAntiAlias = true }

        val lLabelX = PAGE_LEFT
        val lValueX = 135f
        val rLabelX = 315f
        val rValueX = 410f
        val rowH = 19f

        fun gridRow(index: Int, l1: String, v1: String, l2: String, v2: String) {
            val top = y + index * rowH
            val base = top + 13f
            canvas.drawText(l1, lLabelX, base, labelPaint)
            canvas.drawText(v1, lValueX, base, valuePaint)
            canvas.drawText(l2, rLabelX, base, labelPaint)
            canvas.drawText(v2, rValueX, base, valuePaint)
            canvas.drawLine(PAGE_LEFT, top + rowH, PAGE_RIGHT, top + rowH,
                Paint().apply { color = cRuleSoft; strokeWidth = 0.8f; isAntiAlias = true })
        }
        // top rule of the grid
        canvas.drawLine(PAGE_LEFT, y, PAGE_RIGHT, y,
            Paint().apply { color = cRuleSoft; strokeWidth = 0.8f; isAntiAlias = true })
        gridRow(0, "PATIENT NAME", patientInfo?.name?.ifBlank { "—" } ?: "—", "DATE OF STUDY", formatDateOnly(result.timestamp))
        gridRow(1, "AGE / SEX", "${patientInfo?.age ?: "—"} yrs  /  ${patientInfo?.sex ?: "—"}", "REPORT DATE", formatDateOnly(result.timestamp))
        gridRow(2, "MODALITY", "Chest Radiograph (CXR)", "ACCESSION", reportNo)
        gridRow(3, "REFERRING", "Self-referred", "STATUS", "Final · Software-generated")
        y += 4 * rowH + 16f

        // ─────────────────────── CLINICAL INDICATION ───────────────────────
        drawHeading(canvas, "CLINICAL INDICATION", y, cInk, cAccent, cRule, sansBold)
        y += 16f
        canvas.drawText(
            "Automated screening of a digital chest radiograph for thoracic abnormalities.",
            PAGE_LEFT, y, Paint().apply { color = cBody; textSize = 9.5f; typeface = sans; isAntiAlias = true }
        )
        y += 18f

        // ─────────────────────────── FINDINGS ───────────────────────────
        drawHeading(canvas, "FINDINGS", y, cInk, cAccent, cRule, sansBold)
        y += 16f
        val bodyPaint = Paint().apply { color = cBody; textSize = 9.5f; typeface = sans; isAntiAlias = true }
        y = drawWrapped(canvas, buildFindings(result), PAGE_LEFT, y, CONTENT_WIDTH, bodyPaint, 13f)
        y += 8f

        // ─────────────────────────── IMPRESSION ───────────────────────────
        drawHeading(canvas, "IMPRESSION", y, cInk, cAccent, cRule, sansBold)
        y += 16f
        val impPaint = Paint().apply { color = cInk; textSize = 9.5f; typeface = sansBold; isAntiAlias = true }
        buildImpression(result).forEachIndexed { i, line ->
            y = drawWrapped(canvas, "${i + 1}.  $line", PAGE_LEFT + 6f, y, CONTENT_WIDTH - 6f, impPaint, 14f)
        }
        y += 10f

        // ─────────────────── QUANTITATIVE ANALYSIS (table) ───────────────────
        drawHeading(canvas, "QUANTITATIVE ANALYSIS", y, cInk, cAccent, cRule, sansBold)
        y += 14f

        val thPaint = Paint().apply { color = cMuted; textSize = 7.5f; typeface = sansBold; letterSpacing = 0.04f; isAntiAlias = true }
        val thRight = Paint().apply { color = cMuted; textSize = 7.5f; typeface = sansBold; textAlign = Paint.Align.RIGHT; isAntiAlias = true }
        canvas.drawText("FINDING", PAGE_LEFT + 4f, y + 10f, thPaint)
        canvas.drawText("PROBABILITY", 300f, y + 10f, thPaint)
        canvas.drawText("SCORE", 470f, y + 10f, thRight)
        canvas.drawText("ASSESSMENT", PAGE_RIGHT, y + 10f, thRight)
        y += 15f
        canvas.drawLine(PAGE_LEFT, y, PAGE_RIGHT, y, Paint().apply { color = cRule; strokeWidth = 0.9f; isAntiAlias = true })

        val namePaint = Paint().apply { color = cBody; textSize = 9.5f; typeface = sans; isAntiAlias = true }
        val scorePaint = Paint().apply { color = cInk; textSize = 9.5f; typeface = mono; textAlign = Paint.Align.RIGHT; isAntiAlias = true }
        val trackPaint = Paint().apply { color = cRuleSoft; style = Paint.Style.FILL; isAntiAlias = true }
        val assessPaint = Paint().apply { textSize = 8.5f; typeface = sansBold; textAlign = Paint.Align.RIGHT; isAntiAlias = true }

        val tRowH = 18f
        result.predictions.take(5).forEachIndexed { index, prediction ->
            val base = y + index * tRowH + 12f
            val mid = y + index * tRowH + tRowH / 2f
            canvas.drawText(prettify(prediction.disease), PAGE_LEFT + 4f, base, namePaint)

            // slim probability bar
            val barStart = 300f
            val barWidth = 140f
            val barH = 5f
            val prog = barWidth * (prediction.confidence / 100f)
            canvas.drawRect(RectF(barStart, mid - barH / 2f, barStart + barWidth, mid + barH / 2f), trackPaint)
            canvas.drawRect(
                RectF(barStart, mid - barH / 2f, barStart + prog, mid + barH / 2f),
                Paint().apply { color = severityColor(prediction.confidence); style = Paint.Style.FILL; isAntiAlias = true }
            )

            canvas.drawText("${prediction.confidence.toInt()}%", 470f, base, scorePaint)
            assessPaint.color = severityColor(prediction.confidence)
            canvas.drawText(severityLabel(prediction.confidence), PAGE_RIGHT, base, assessPaint)

            canvas.drawLine(PAGE_LEFT, y + (index + 1) * tRowH, PAGE_RIGHT, y + (index + 1) * tRowH,
                Paint().apply { color = cRuleSoft; strokeWidth = 0.7f; isAntiAlias = true })
        }
        y += result.predictions.take(5).size * tRowH + 18f

        // ─────────────────── REPRESENTATIVE IMAGES ───────────────────
        drawHeading(canvas, "REPRESENTATIVE IMAGES", y, cInk, cAccent, cRule, sansBold)
        y += 14f

        val imgSize = 135f
        val gap = 20f
        val startX = PAGE_LEFT + (CONTENT_WIDTH - (3 * imgSize + 2 * gap)) / 2f
        val rectOriginal = RectF(startX, y, startX + imgSize, y + imgSize)
        val rectHeatmap = RectF(startX + imgSize + gap, y, startX + 2 * imgSize + gap, y + imgSize)
        val rectOverlay = RectF(startX + 2 * (imgSize + gap), y, startX + 3 * imgSize + 2 * gap, y + imgSize)

        val originalBitmap = loadOriginalBitmap(context, imageUri)
        val heatmapBitmap = loadHeatmapBitmap(result.heatmap_data)
        val overlayBitmap = if (originalBitmap != null && heatmapBitmap != null) {
            createOverlayBitmap(originalBitmap, heatmapBitmap)
        } else null

        drawFramedImage(canvas, originalBitmap, rectOriginal, "Original N/A", cRule)
        drawFramedImage(canvas, heatmapBitmap, rectHeatmap, "Heatmap N/A", cRule)
        drawFramedImage(canvas, overlayBitmap, rectOverlay, "Overlay N/A", cRule)

        originalBitmap?.recycle()
        heatmapBitmap?.recycle()
        overlayBitmap?.recycle()

        val captionPaint = Paint().apply {
            color = cMuted; textSize = 8f; typeface = sans; textAlign = Paint.Align.CENTER; isAntiAlias = true
        }
        val captionY = y + imgSize + 13f
        canvas.drawText("Fig 1 — Original Radiograph", rectOriginal.centerX(), captionY, captionPaint)
        canvas.drawText("Fig 2 — Saliency Map", rectHeatmap.centerX(), captionY, captionPaint)
        canvas.drawText("Fig 3 — Fused Overlay", rectOverlay.centerX(), captionY, captionPaint)
        y += imgSize + 30f

        // ─────────────────────── AUTHENTICATION ───────────────────────
        canvas.drawLine(PAGE_LEFT, y, PAGE_RIGHT, y, Paint().apply { color = cRule; strokeWidth = 0.9f; isAntiAlias = true })
        y += 16f
        val authPaint = Paint().apply { color = cBody; textSize = 8.5f; typeface = sans; isAntiAlias = true }
        canvas.drawText("Electronically generated by the ChestX analysis software.", PAGE_LEFT, y, authPaint)
        canvas.drawText("No physician review has been performed on this report.", PAGE_LEFT, y + 12f,
            Paint().apply { color = cMuted; textSize = 8.5f; typeface = sans; isAntiAlias = true })

        // Signature line (right)
        canvas.drawLine(390f, y + 8f, PAGE_RIGHT, y + 8f, Paint().apply { color = cSubtle; strokeWidth = 0.9f; isAntiAlias = true })
        canvas.drawText("Authorised Signature", (390f + PAGE_RIGHT) / 2f, y + 20f,
            Paint().apply { color = cMuted; textSize = 8f; typeface = sans; textAlign = Paint.Align.CENTER; isAntiAlias = true })

        // ─────────────────────────── FOOTER ───────────────────────────
        canvas.drawLine(PAGE_LEFT, 796f, PAGE_RIGHT, 796f, Paint().apply { color = cRule; strokeWidth = 0.8f; isAntiAlias = true })
        canvas.drawText(
            "CONFIDENTIAL — FOR RESEARCH & EDUCATIONAL USE ONLY · NOT A CLINICAL DIAGNOSIS",
            PAGE_LEFT, 809f,
            Paint().apply { color = cMuted; textSize = 7.5f; typeface = sansBold; letterSpacing = 0.03f; isAntiAlias = true }
        )
        canvas.drawText(
            "ChestX · $reportNo",
            PAGE_LEFT, 820f,
            Paint().apply { color = cSubtle; textSize = 7.5f; typeface = sans; isAntiAlias = true }
        )
        canvas.drawText("Page 1 of 1", PAGE_RIGHT, 820f,
            Paint().apply { color = cSubtle; textSize = 7.5f; typeface = sans; textAlign = Paint.Align.RIGHT; isAntiAlias = true })

        pdfDocument.finishPage(page)
        return pdfDocument
    }

    // ─────────────────────────── Content builders ───────────────────────────

    private fun buildFindings(result: AnalysisResponse): String {
        val significant = result.predictions.filter { it.confidence >= 40f }.sortedByDescending { it.confidence }
        val sb = StringBuilder()
        sb.append("The submitted chest radiograph was analysed using the ChestX deep-learning classification model. ")
        if (significant.isEmpty()) {
            sb.append("No thoracic abnormality was identified at or above the significance threshold. ")
            sb.append("All screened conditions returned low probability scores, and the examination is considered unremarkable by the model. ")
        } else {
            val top = significant.first()
            sb.append("The study demonstrates imaging features most consistent with ${prettify(top.disease)}, ")
            sb.append("assigned a ${band(top.confidence)} probability of ${top.confidence.toInt()}%. ")
            val others = significant.drop(1).take(3)
            if (others.isNotEmpty()) {
                sb.append("Lesser elevated probabilities were also recorded for ")
                sb.append(others.joinToString(", ") { "${prettify(it.disease)} (${it.confidence.toInt()}%)" })
                sb.append(". ")
            }
        }
        sb.append("These results are probabilistic estimates and should be correlated with the clinical history, physical examination, and any available prior imaging.")
        return sb.toString()
    }

    private fun buildImpression(result: AnalysisResponse): List<String> {
        val significant = result.predictions.filter { it.confidence >= 40f }
            .sortedByDescending { it.confidence }
            .take(3)
        if (significant.isEmpty()) {
            return listOf("No significant abnormality detected on automated screening.")
        }
        return significant.map { "${prettify(it.disease)} — ${band(it.confidence)} probability (${it.confidence.toInt()}%)." }
    }

    private fun band(confidence: Float): String = when {
        confidence >= 70f -> "high"
        confidence >= 40f -> "moderate"
        else -> "low"
    }

    private fun prettify(disease: String): String = disease.replace('_', ' ')

    // ─────────────────────────── Drawing helpers ───────────────────────────

    /** Bold section heading with a full-width hairline underneath and a short accent tick. */
    private fun drawHeading(canvas: Canvas, text: String, y: Float, ink: Int, accent: Int, rule: Int, bold: Typeface) {
        canvas.drawText(text, PAGE_LEFT, y,
            Paint().apply { color = ink; textSize = 10f; typeface = bold; letterSpacing = 0.1f; isAntiAlias = true })
        canvas.drawLine(PAGE_LEFT, y + 5f, PAGE_RIGHT, y + 5f,
            Paint().apply { color = rule; strokeWidth = 0.9f; isAntiAlias = true })
        canvas.drawLine(PAGE_LEFT, y + 5f, PAGE_LEFT + 46f, y + 5f,
            Paint().apply { color = accent; strokeWidth = 2f; isAntiAlias = true })
    }

    /** Word-wraps [text] within [maxWidth]; returns the y baseline just below the last line. */
    private fun drawWrapped(canvas: Canvas, text: String, x: Float, startY: Float, maxWidth: Float, paint: Paint, lineHeight: Float): Float {
        var y = startY
        var line = StringBuilder()
        for (word in text.split(" ")) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                canvas.drawText(line.toString(), x, y, paint)
                y += lineHeight
                line = StringBuilder(word)
            } else {
                line = StringBuilder(candidate)
            }
        }
        if (line.isNotEmpty()) {
            canvas.drawText(line.toString(), x, y, paint)
            y += lineHeight
        }
        return y
    }

    private fun drawFramedImage(canvas: Canvas, bitmap: Bitmap?, rect: RectF, placeholder: String, borderColor: Int) {
        if (bitmap != null) {
            drawBitmapInRect(canvas, bitmap, rect)
            canvas.drawRect(rect, Paint().apply {
                color = borderColor; style = Paint.Style.STROKE; strokeWidth = 1f; isAntiAlias = true
            })
        } else {
            drawPlaceholder(canvas, rect, placeholder)
        }
    }

    private fun severityColor(confidence: Float): Int = when {
        confidence >= 70f -> Color.parseColor(COLOR_SUCCESS)
        confidence >= 40f -> Color.parseColor(COLOR_WARNING)
        else -> Color.parseColor(COLOR_NEUTRAL)
    }

    private fun severityLabel(confidence: Float): String = when {
        confidence >= 70f -> "HIGH"
        confidence >= 40f -> "MODERATE"
        else -> "LOW"
    }

    fun exportReport(
        context: Context,
        imageUri: Uri,
        patientInfo: PatientInfo?,
        result: AnalysisResponse
    ) {
        try {
            val pdfDocument = buildPdfDocument(context, imageUri, patientInfo, result)
            val file = File(context.cacheDir, "ChestX_Report_${result.id}.pdf")
            val fileOutputStream = FileOutputStream(file)
            pdfDocument.writeTo(fileOutputStream)
            fileOutputStream.close()
            pdfDocument.close()

            val pdfUri = FileProvider.getUriForFile(
                context,
                "com.medical.chestxray.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(pdfUri, "application/pdf")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }

            context.startActivity(intent)
            Toast.makeText(context, "PDF Report exported successfully.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to export PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun shareReport(
        context: Context,
        imageUri: Uri?,
        patientInfo: PatientInfo?,
        result: AnalysisResponse?
    ) {
        if (imageUri == null || result == null) {
            Toast.makeText(context, "Report data not available to share.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val pdfDocument = buildPdfDocument(context, imageUri, patientInfo, result)
            val file = File(context.cacheDir, "ChestX_Report_${result.id}.pdf")
            val fileOutputStream = FileOutputStream(file)
            pdfDocument.writeTo(fileOutputStream)
            fileOutputStream.close()
            pdfDocument.close()

            val pdfUri = FileProvider.getUriForFile(
                context,
                "com.medical.chestxray.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, pdfUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Diagnostic Report")
            chooser.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to share report: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun downloadViewImage(
        context: Context,
        viewModeName: String, // "Original", "Heatmap", "Overlay"
        imageUri: Uri?,
        heatmapData: String?
    ) {
        try {
            val original = imageUri?.let { loadOriginalBitmap(context, it) }
            val heatmap = loadHeatmapBitmap(heatmapData)

            val bitmapToSave = when (viewModeName) {
                "Original" -> original
                "Heatmap" -> heatmap
                "Overlay" -> {
                    if (original != null && heatmap != null) {
                        createOverlayBitmap(original, heatmap)
                    } else {
                        original ?: heatmap
                    }
                }
                else -> null
            }

            if (bitmapToSave != null) {
                saveBitmapToGallery(context, bitmapToSave, "ChestX_${viewModeName}_${System.currentTimeMillis()}")

                // Clean up overlay memory if it was allocated separately
                if (viewModeName == "Overlay" && bitmapToSave != original && bitmapToSave != heatmap) {
                    bitmapToSave.recycle()
                }
                original?.recycle()
                heatmap?.recycle()
            } else {
                Toast.makeText(context, "$viewModeName image is not loaded yet.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Download failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveBitmapToGallery(context: Context, bitmap: Bitmap, filename: String) {
        val resolver = context.contentResolver
        val imageDetails = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$filename.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/ChestX")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val uri = resolver.insert(collection, imageDetails)
        if (uri != null) {
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    imageDetails.clear()
                    imageDetails.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, imageDetails, null, null)
                }
                Toast.makeText(context, "Image saved to gallery successfully.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Failed to save image: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Failed to create gallery directory.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadOriginalBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            null
        }
    }

    private fun loadHeatmapBitmap(heatmapData: String?): Bitmap? {
        return try {
            if (!heatmapData.isNullOrBlank()) {
                val cleanBase64 = if (heatmapData.startsWith("data:image")) {
                    heatmapData.substringAfter("base64,")
                } else {
                    heatmapData
                }
                val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun createOverlayBitmap(original: Bitmap, heatmap: Bitmap): Bitmap? {
        return try {
            val overlay = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(overlay)
            canvas.drawBitmap(original, 0f, 0f, null)
            val paint = Paint().apply {
                alpha = (0.55f * 255).toInt()
            }
            val src = Rect(0, 0, heatmap.width, heatmap.height)
            val dest = Rect(0, 0, original.width, original.height)
            canvas.drawBitmap(heatmap, src, dest, paint)
            overlay
        } catch (e: Exception) {
            null
        }
    }

    private fun drawBitmapInRect(canvas: Canvas, bitmap: Bitmap, rect: RectF) {
        val src = Rect(0, 0, bitmap.width, bitmap.height)
        canvas.drawBitmap(bitmap, src, rect, Paint(Paint.FILTER_BITMAP_FLAG))
    }

    private fun drawPlaceholder(canvas: Canvas, rect: RectF, text: String) {
        val bgPaint = Paint().apply {
            color = Color.parseColor(COLOR_CARD)
            style = Paint.Style.FILL
        }
        val strokePaint = Paint().apply {
            color = Color.parseColor(COLOR_RULE)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val textPaint = Paint().apply {
            color = Color.parseColor(COLOR_MUTED)
            textSize = 9f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawRect(rect, bgPaint)
        canvas.drawRect(rect, strokePaint)
        canvas.drawText(text, rect.centerX(), rect.centerY() + 3f, textPaint)
    }

    private fun formatTimestamp(timestamp: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
            val date = inputFormat.parse(timestamp)
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            timestamp
        }
    }

    private fun formatDateOnly(timestamp: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            val date = inputFormat.parse(timestamp)
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            timestamp
        }
    }
}
