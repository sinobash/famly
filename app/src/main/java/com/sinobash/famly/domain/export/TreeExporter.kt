package com.sinobash.famly.domain.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import androidx.core.net.toUri
import com.sinobash.famly.data.local.Gender
import com.sinobash.famly.domain.TreeLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

enum class TreeExportFormat(val mimeType: String, val extension: String, val displayName: String) {
    PNG("image/png", "png", "Gambar PNG (.png)"),
    JPG("image/jpeg", "jpg", "Gambar JPEG (.jpg)"),
    WEBP("image/webp", "webp", "Gambar WebP (.webp)"),
    SVG("image/svg+xml", "svg", "Vektor SVG (.svg)"),
    PDF("application/pdf", "pdf", "Dokumen PDF (.pdf)")
}

object TreeExporter {

    private const val BASE_CARD_W = 120f
    private const val BASE_CARD_H = 72f
    private const val BASE_GAP_X = 24f
    private const val BASE_GAP_Y = 56f

    suspend fun export(
        context: Context,
        layout: TreeLayout,
        format: TreeExportFormat,
        uri: Uri
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri, "wt")?.use { outputStream ->
                when (format) {
                    TreeExportFormat.PNG -> exportBitmap(context, layout, Bitmap.CompressFormat.PNG, 100, outputStream)
                    TreeExportFormat.JPG -> exportBitmap(context, layout, Bitmap.CompressFormat.JPEG, 90, outputStream)
                    TreeExportFormat.WEBP -> {
                        val compressFormat =
                            Bitmap.CompressFormat.WEBP_LOSSLESS
                        exportBitmap(context, layout, compressFormat, 100, outputStream)
                    }
                    TreeExportFormat.PDF -> exportPdf(context, layout, outputStream)
                    TreeExportFormat.SVG -> exportSvg(context, layout, outputStream)
                }
            } ?: throw IllegalStateException("Gagal membuka file tujuan untuk menulis data.")
        }
    }

    private fun exportBitmap(
        context: Context,
        layout: TreeLayout,
        compressFormat: Bitmap.CompressFormat,
        quality: Int,
        outputStream: OutputStream
    ) {
        val density = 2f
        val (widthPx, heightPx) = calculateDimensions(layout, density)
        val bitmap = createBitmap(widthPx.toInt(), heightPx.toInt())
        val canvas = Canvas(bitmap)

        drawTreeToCanvas(canvas, layout, context, density)

        bitmap.compress(compressFormat, quality, outputStream)
        bitmap.recycle()
    }

    private fun exportPdf(context: Context, layout: TreeLayout, outputStream: OutputStream) {
        val density = 2f
        val (widthPx, heightPx) = calculateDimensions(layout, density)
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(widthPx.toInt(), heightPx.toInt(), 1).create()
        val page = pdfDocument.startPage(pageInfo)

        drawTreeToCanvas(page.canvas, layout, context, density)

        pdfDocument.finishPage(page)
        pdfDocument.writeTo(outputStream)
        pdfDocument.close()
    }

    private fun exportSvg(context: Context, layout: TreeLayout, outputStream: OutputStream) {
        val density = 2f
        val (widthPx, heightPx) = calculateDimensions(layout, density)
        val svgBuilder = StringBuilder()

        val cardW = BASE_CARD_W * density
        val cardH = BASE_CARD_H * density
        val gapX = BASE_GAP_X * density
        val gapY = BASE_GAP_Y * density

        fun xDp(col: Float): Float = gapX + (cardW + gapX) * col
        fun yDp(gen: Int): Float = gapY + (cardH + gapY) * gen

        svgBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        svgBuilder.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"${widthPx.toInt()}\" height=\"${heightPx.toInt()}\" viewBox=\"0 0 ${widthPx.toInt()} ${heightPx.toInt()}\">\n")
        svgBuilder.append("  <rect width=\"100%\" height=\"100%\" fill=\"#FFFFFF\"/>\n")

        val byId = layout.nodes.associateBy { it.person.id }

        // Edges
        layout.edges.forEach { edge ->
            val a = byId[edge.fromId] ?: return@forEach
            val b = byId[edge.toId] ?: return@forEach
            val ax = xDp(a.column); val ay = yDp(a.generation)
            val bx = xDp(b.column); val byy = yDp(b.generation)

            if (edge.isSpouse) {
                val (left, right) = if (ax <= bx) ax to bx else bx to ax
                val startX = left + cardW
                val endX = right
                val y = ay + cardH / 2f
                svgBuilder.append("  <line x1=\"$startX\" y1=\"$y\" x2=\"$endX\" y2=\"$y\" stroke=\"#7D5260\" stroke-width=\"6\" stroke-linecap=\"round\"/>\n")
            } else {
                val startX = ax + cardW / 2f
                val startY = ay + cardH
                val endX = bx + cardW / 2f
                val endY = byy
                val midY = (startY + endY) / 2f
                svgBuilder.append("  <path d=\"M $startX $startY L $startX $midY L $endX $midY L $endX $endY\" stroke=\"#79747E\" stroke-width=\"4\" fill=\"none\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>\n")
            }
        }

        // Nodes
        layout.nodes.forEach { node ->
            val px = xDp(node.column)
            val py = yDp(node.generation)
            val gender = node.person.gender

            val (bgColor, textColor, avatarBg) = when (gender) {
                Gender.MALE -> Triple("#EADDFF", "#21005D", "#D0BCFF")
                Gender.FEMALE -> Triple("#FFD8E4", "#31111D", "#FFB2C8")
                else -> Triple("#E7E0EC", "#1D1B20", "#CCC2DC")
            }

            // Card Rect
            svgBuilder.append("  <rect x=\"$px\" y=\"$py\" width=\"$cardW\" height=\"$cardH\" rx=\"24\" ry=\"24\" fill=\"$bgColor\" stroke=\"#79747E\" stroke-width=\"1\"/>\n")

            // Avatar Circle
            val avatarCX = px + cardW / 2f
            val avatarCY = py + 36f
            val avatarR = 24f
            svgBuilder.append("  <circle cx=\"$avatarCX\" cy=\"$avatarCY\" r=\"$avatarR\" fill=\"$avatarBg\"/>\n")

            // Initial
            val initial = node.person.fullName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            svgBuilder.append("  <text x=\"$avatarCX\" y=\"${avatarCY + 8f}\" font-family=\"sans-serif\" font-size=\"20\" font-weight=\"bold\" text-anchor=\"middle\" fill=\"$textColor\">${escapeXml(initial)}</text>\n")

            // Text Name
            val textY = py + 88f
            val escapedName = escapeXml(node.person.fullName)
            svgBuilder.append("  <text x=\"$avatarCX\" y=\"$textY\" font-family=\"sans-serif\" font-size=\"18\" text-anchor=\"middle\" fill=\"$textColor\">$escapedName</text>\n")
        }

        svgBuilder.append("</svg>")

        outputStream.write(svgBuilder.toString().toByteArray(Charsets.UTF_8))
    }

    private fun calculateDimensions(layout: TreeLayout, density: Float): Pair<Float, Float> {
        val cardW = BASE_CARD_W * density
        val cardH = BASE_CARD_H * density
        val gapX = BASE_GAP_X * density
        val gapY = BASE_GAP_Y * density

        val widthPx = (cardW + gapX) * layout.columnCount + gapX
        val heightPx = (cardH + gapY) * layout.generationCount + gapY
        return Pair(maxOf(widthPx, 300f * density), maxOf(heightPx, 300f * density))
    }

    private fun drawTreeToCanvas(
        canvas: Canvas,
        layout: TreeLayout,
        context: Context,
        density: Float
    ) {
        val cardW = BASE_CARD_W * density
        val cardH = BASE_CARD_H * density
        val gapX = BASE_GAP_X * density
        val gapY = BASE_GAP_Y * density

        fun xDp(col: Float): Float = gapX + (cardW + gapX) * col
        fun yDp(gen: Int): Float = gapY + (cardH + gapY) * gen

        // Background
        canvas.drawColor(Color.WHITE)

        val spousePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7D5260")
            strokeWidth = 3f * density
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        val relationPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#79747E")
            strokeWidth = 2f * density
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val byId = layout.nodes.associateBy { it.person.id }

        // Draw Edges
        layout.edges.forEach { edge ->
            val a = byId[edge.fromId] ?: return@forEach
            val b = byId[edge.toId] ?: return@forEach
            val ax = xDp(a.column); val ay = yDp(a.generation)
            val bx = xDp(b.column); val byy = yDp(b.generation)

            if (edge.isSpouse) {
                val (left, right) = if (ax <= bx) ax to bx else bx to ax
                canvas.drawLine(
                    left + cardW,
                    ay + cardH / 2f,
                    right,
                    byy + cardH / 2f,
                    spousePaint
                )
            } else {
                val startX = ax + cardW / 2f
                val startY = ay + cardH
                val endX = bx + cardW / 2f
                val endY = byy
                val midY = (startY + endY) / 2f

                val path = Path().apply {
                    moveTo(startX, startY)
                    lineTo(startX, midY)
                    lineTo(endX, midY)
                    lineTo(endX, endY)
                }
                canvas.drawPath(path, relationPaint)
            }
        }

        // Draw Nodes
        val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val avatarBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val avatarTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12f * density
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10f * density
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.CENTER
        }

        layout.nodes.forEach { node ->
            val px = xDp(node.column)
            val py = yDp(node.generation)
            val gender = node.person.gender

            val (bgColor, textColor, avatarBg) = when (gender) {
                Gender.MALE -> Triple(Color.parseColor("#EADDFF"), Color.parseColor("#21005D"), Color.parseColor("#D0BCFF"))
                Gender.FEMALE -> Triple(Color.parseColor("#FFD8E4"), Color.parseColor("#31111D"), Color.parseColor("#FFB2C8"))
                else -> Triple(Color.parseColor("#E7E0EC"), Color.parseColor("#1D1B20"), Color.parseColor("#CCC2DC"))
            }

            // Card Rect
            cardBgPaint.color = bgColor
            val rect = RectF(px, py, px + cardW, py + cardH)
            val cornerR = 12f * density
            canvas.drawRoundRect(rect, cornerR, cornerR, cardBgPaint)

            // Avatar
            val avatarR = 16f * density
            val avatarCX = px + cardW / 2f
            val avatarCY = py + 20f * density

            var photoBitmap: Bitmap? = null
            val photoPath = node.person.photoPath
            if (!photoPath.isNullOrBlank()) {
                photoBitmap = loadBitmap(context, photoPath)
            }

            if (photoBitmap != null) {
                drawCircularBitmap(canvas, photoBitmap, avatarCX, avatarCY, avatarR)
            } else {
                avatarBgPaint.color = avatarBg
                canvas.drawCircle(avatarCX, avatarCY, avatarR, avatarBgPaint)

                avatarTextPaint.color = textColor
                val initial = node.person.fullName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                val fontMetrics = avatarTextPaint.fontMetrics
                val baseline = avatarCY - (fontMetrics.ascent + fontMetrics.descent) / 2f
                canvas.drawText(initial, avatarCX, baseline, avatarTextPaint)
            }

            // Name
            textPaint.color = textColor
            val maxTextWidth = (cardW - 12f * density).toInt().coerceAtLeast(10)
            val nameText = node.person.fullName

            canvas.withTranslation(avatarCX, py + 42f * density) {
                val builder = StaticLayout.Builder.obtain(
                    nameText,
                    0,
                    nameText.length,
                    textPaint,
                    maxTextWidth
                )
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setLineSpacing(0f, 1f)
                    .setMaxLines(2)
                    .setEllipsize(android.text.TextUtils.TruncateAt.END)
                val staticLayout = builder.build()
                staticLayout.draw(this)

            }
        }
    }

    private fun loadBitmap(context: Context, path: String): Bitmap? {
        return try {
            val uri = path.toUri()
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun drawCircularBitmap(canvas: Canvas, bitmap: Bitmap, cx: Float, cy: Float, radius: Float) {
        val size = minOf(bitmap.width, bitmap.height)
        val x = (bitmap.width - size) / 2
        val y = (bitmap.height - size) / 2
        val cropped = Bitmap.createBitmap(bitmap, x, y, size, size)
        val scaled = cropped.scale((radius * 2).toInt(), (radius * 2).toInt())

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = Path().apply {
            addCircle(cx, cy, radius, Path.Direction.CW)
        }
        canvas.withClip(path) {
            drawBitmap(scaled, cx - radius, cy - radius, paint)
        }
    }

    private fun escapeXml(input: String): String {
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}