package com.metrolist.music.ui.screens.wrapped

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.metrolist.music.R

/**
 * Draws the recap playlist cover: "METROLIST", the period's digits in outline and "WRAPPED", in the
 * recap's black and white style. Built per period, so a weekly recap never wears a yearly cover.
 */
fun renderWrappedCover(
    context: Context,
    label: List<String>,
    size: Int = 1024,
): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val scale = size / 1024f
    val center = size / 2f
    canvas.drawColor(Color.BLACK)

    val glow =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(center, center, size * 0.6f, 0x2EFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        }
    canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), glow)

    val outline =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f * scale
        }
    val inset = 64f * scale
    val arm = 110f * scale
    val far = size - inset
    canvas.drawLines(
        floatArrayOf(
            inset, inset, inset + arm, inset, inset, inset, inset, inset + arm,
            far, inset, far - arm, inset, far, inset, far, inset + arm,
            inset, far, inset + arm, far, inset, far, inset, far - arm,
            far, far, far - arm, far, far, far, far, far - arm,
        ),
        outline,
    )
    val dashed =
        Paint(outline).apply {
            alpha = 90
            pathEffect = DashPathEffect(floatArrayOf(14f * scale, 12f * scale), 0f)
        }
    canvas.drawCircle(center, center, 400f * scale, dashed)

    val typeface = runCatching { ResourcesCompat.getFont(context, R.font.bbh_bartle_regular) }.getOrNull() ?: Typeface.DEFAULT_BOLD
    val text =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }

    fun Paint.fitTo(lines: List<String>, maxWidth: Float, maxSize: Float) {
        textSize = maxSize
        val widest = lines.maxOf { measureText(it) }
        if (widest > maxWidth) textSize = maxSize * maxWidth / widest
    }

    val title = Paint(text).apply { fitTo(listOf("METROLIST"), 600f * scale, 110f * scale) }
    val digits =
        Paint(text).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f * scale
            fitTo(label, 640f * scale, if (label.size > 1) 190f * scale else 260f * scale)
        }
    val footer =
        Paint(text).apply {
            alpha = 200
            letterSpacing = 0.3f
            fitTo(listOf("WRAPPED"), 340f * scale, 52f * scale)
        }

    // Stack the lines by their ink bounds so the block is centred optically, not by font metrics.
    val blocks = listOf("METROLIST" to title) + label.map { it to digits } + ("WRAPPED" to footer)
    val gaps = List(blocks.size - 1) { index -> if (index == 0 || index == blocks.size - 2) 44f * scale else 24f * scale }
    val bounds = blocks.map { (line, paint) -> Rect().also { paint.getTextBounds(line, 0, line.length, it) } }
    var top = center - (bounds.sumOf { it.height() } + gaps.sum()) / 2f
    blocks.forEachIndexed { index, (line, paint) ->
        canvas.drawText(line, center, top - bounds[index].top, paint)
        top += bounds[index].height() + (gaps.getOrNull(index) ?: 0f)
    }
    return bitmap
}
