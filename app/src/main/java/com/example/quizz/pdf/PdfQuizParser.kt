package com.example.quizz.pdf

import android.content.Context
import com.example.quizz.model.QuizItem
import java.io.InputStream
import kotlin.math.max
import org.apache.pdfbox.android.util.PDFBoxResourceLoader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup
import org.apache.pdfbox.text.PDFTextStripperByArea
import java.awt.geom.Rectangle2D

class PdfQuizParser {

    fun parse(context: Context, inputStream: InputStream): List<QuizItem> {
        ensurePdfBoxInitialised(context)
        PDDocument.load(inputStream).use { document ->
            val highlights = mutableListOf<HighlightEntry>()
            val pages = document.pages
            for ((pageIndex, page) in pages.withIndex()) {
                val annotations: List<PDAnnotation> = page.annotations
                val pageHeight = page.mediaBox?.height ?: continue
                val stripper = PDFTextStripperByArea().apply {
                    sortByPosition = true
                }
                var regionCounter = 0

                annotations
                    .filterIsInstance<PDAnnotationTextMarkup>()
                    .filter { it.subtype == PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT }
                    .forEach { annotation ->
                        val rgb = annotation.color?.let { color ->
                            try {
                                color.colorSpace?.toRGB(color.components)
                            } catch (ex: Exception) {
                                null
                            }
                        }
                        val highlightType = rgb?.let { determineType(it) } ?: return@forEach
                        val text = extractText(page, page.mediaBox, pageHeight, annotation, stripper, regionCounter)
                        regionCounter += countRegions(annotation)
                        val cleaned = cleanText(text)
                        if (cleaned.isNotEmpty()) {
                            highlights.add(
                                HighlightEntry(
                                    type = highlightType,
                                    text = cleaned,
                                    pageIndex = pageIndex,
                                    top = annotation.rectangle?.let { pageHeight - it.upperRightY } ?: 0f
                                )
                            )
                        }
                    }
            }

            val ordered = highlights.sortedWith(
                compareBy<HighlightEntry> { it.pageIndex }
                    .thenBy { it.top }
            )

            return buildQuiz(ordered)
        }
    }

    private fun ensurePdfBoxInitialised(context: Context) {
        try {
            PDFBoxResourceLoader.init(context)
        } catch (_: Exception) {
            // Ignore repeated initialisation attempts
        }
    }

    private fun extractText(
        page: PDPage,
        mediaBox: PDRectangle?,
        pageHeight: Float,
        annotation: PDAnnotationTextMarkup,
        stripper: PDFTextStripperByArea,
        startIndex: Int
    ): String {
        val quadPoints = annotation.quadPoints ?: return annotation.contents.orEmpty()
        if (mediaBox == null) return annotation.contents.orEmpty()
        var regionIndex = startIndex
        for (quadIndex in quadPoints.indices step 8) {
            if (quadIndex + 7 >= quadPoints.size) break
            val xs = floatArrayOf(
                quadPoints[quadIndex],
                quadPoints[quadIndex + 2],
                quadPoints[quadIndex + 4],
                quadPoints[quadIndex + 6]
            )
            val ys = floatArrayOf(
                quadPoints[quadIndex + 1],
                quadPoints[quadIndex + 3],
                quadPoints[quadIndex + 5],
                quadPoints[quadIndex + 7]
            )
            val minX = xs.minOrNull() ?: 0f
            val maxX = xs.maxOrNull() ?: 0f
            val minY = ys.minOrNull() ?: 0f
            val maxY = ys.maxOrNull() ?: 0f
            val height = max(4f, maxY - minY)
            val rect = Rectangle2D.Float(
                minX,
                pageHeight - maxY,
                max(4f, maxX - minX),
                height
            )
            val regionName = "highlight_${regionIndex++}"
            stripper.addRegion(regionName, rect)
        }
        stripper.extractRegions(page)
        val regions = stripper.regions.toList()
        return buildString {
            regions.forEach { region ->
                val value = stripper.getTextForRegion(region).trim()
                if (value.isNotEmpty()) {
                    if (isNotEmpty()) append('\n')
                    append(value)
                }
                stripper.removeRegion(region)
            }
        }
    }

    private fun countRegions(annotation: PDAnnotationTextMarkup): Int {
        val quadPoints = annotation.quadPoints ?: return 0
        return quadPoints.size / 8
    }

    private fun cleanText(raw: String): String {
        return raw
            .replace("\r", "\n")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(separator = " ")
    }

    private fun buildQuiz(entries: List<HighlightEntry>): List<QuizItem> {
        if (entries.isEmpty()) return emptyList()
        val items = mutableListOf<QuizItem>()
        var currentQuestion: String? = null
        val pendingAnswers = mutableListOf<String>()

        fun flushQuestion() {
            val question = currentQuestion
            if (question != null && pendingAnswers.isNotEmpty()) {
                items.add(
                    QuizItem(
                        question = question,
                        answers = pendingAnswers.toList(),
                        correctAnswerIndex = 0
                    )
                )
            }
            pendingAnswers.clear()
        }

        entries.forEach { entry ->
            when (entry.type) {
                HighlightType.QUESTION -> {
                    flushQuestion()
                    currentQuestion = entry.text
                }
                HighlightType.ANSWER -> {
                    pendingAnswers.add(entry.text)
                }
            }
        }
        flushQuestion()
        return items
    }

    private fun determineType(rgb: FloatArray): HighlightType? {
        if (rgb.size < 3) return null
        val r = (rgb[0] * 255).toInt().coerceIn(0, 255)
        val g = (rgb[1] * 255).toInt().coerceIn(0, 255)
        val b = (rgb[2] * 255).toInt().coerceIn(0, 255)
        return when {
            r >= 220 && g <= 140 && b >= 200 -> HighlightType.QUESTION
            r >= 220 && g in 60..160 && b <= 80 -> HighlightType.ANSWER
            else -> null
        }
    }

    private data class HighlightEntry(
        val type: HighlightType,
        val text: String,
        val pageIndex: Int,
        val top: Float
    )

    private enum class HighlightType {
        QUESTION,
        ANSWER
    }
}
