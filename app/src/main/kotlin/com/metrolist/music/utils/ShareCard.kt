/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.metrolist.music.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** A story-sized picture of a song or album — cover, title, artist — to send along with its link. */
object ShareCard {
    private const val WIDTH = 1080
    private const val HEIGHT = 1920
    private const val COVER = 760f

    // The menu that asks for a card closes at once, so drawing it must not depend on the menu's scope.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun share(
        context: Context,
        coverUrl: String?,
        title: String,
        subtitle: String,
        link: String,
    ) {
        val appContext = context.applicationContext
        scope.launch {
            runCatching { shareNow(appContext, coverUrl, title, subtitle, link) }
                .onFailure { Toast.makeText(appContext, appContext.getString(R.string.failed_to_create_image, it.message), Toast.LENGTH_SHORT).show() }
        }
    }

    private suspend fun shareNow(
        context: Context,
        coverUrl: String?,
        title: String,
        subtitle: String,
        link: String,
    ) {
        val uri =
            withContext(Dispatchers.IO) {
                val bitmap = draw(context, loadCover(context, coverUrl), title, subtitle)
                val dir = File(context.cacheDir, "share").apply { mkdirs() }
                val file = File(dir, "card.png")
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
                FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", file)
            }
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, link)
                clipData = ClipData.newRawUri(null, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        context.startActivity(
            Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private suspend fun loadCover(
        context: Context,
        url: String?,
    ): Bitmap? {
        if (url == null) return null
        return runCatching {
            val request =
                ImageRequest
                    .Builder(context)
                    .data(url)
                    .size(1024)
                    .allowHardware(false)
                    .build()
            context.imageLoader
                .execute(request)
                .image
                ?.toBitmap()
        }.getOrNull()
    }

    private fun draw(
        context: Context,
        cover: Bitmap?,
        title: String,
        subtitle: String,
    ): Bitmap {
        val bitmap = createBitmap(WIDTH, HEIGHT)
        val canvas = Canvas(bitmap)
        val full = RectF(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat())
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val swatch = cover?.let { Palette.from(it).generate() }
        val deep = swatch?.darkMutedSwatch?.rgb ?: swatch?.dominantSwatch?.rgb ?: 0xFF1E1A24.toInt()

        // Background: the cover itself, blown up and softened, so every card carries its record's colours.
        paint.color = deep
        canvas.drawRect(full, paint)
        if (cover != null) {
            val tiny = cover.scale(16, 16)
            val src = Rect(1, 1, 15, 15)
            paint.alpha = 190
            canvas.drawBitmap(tiny, src, RectF(-200f, -200f, WIDTH + 200f, HEIGHT + 200f), paint)
            paint.alpha = 255
            tiny.recycle()
        }
        paint.shader =
            LinearGradient(0f, 0f, 0f, HEIGHT.toFloat(), intArrayOf(0x33000000, 0x66000000, 0xCC000000.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawRect(full, paint)
        paint.shader = null

        val left = (WIDTH - COVER) / 2f
        val top = 380f
        val coverRect = RectF(left, top, left + COVER, top + COVER)
        val radius = 44f

        val shadow =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x99000000.toInt()
                maskFilter = BlurMaskFilter(60f, BlurMaskFilter.Blur.NORMAL)
            }
        canvas.drawRoundRect(RectF(coverRect).apply { offset(0f, 30f) }, radius, radius, shadow)

        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(coverRect, radius, radius, Path.Direction.CW) })
        if (cover != null) {
            val side = minOf(cover.width, cover.height)
            val src = Rect((cover.width - side) / 2, (cover.height - side) / 2, (cover.width + side) / 2, (cover.height + side) / 2)
            canvas.drawBitmap(cover, src, coverRect, paint)
        } else {
            paint.color = 0xFF3A3340.toInt()
            canvas.drawRect(coverRect, paint)
        }
        canvas.restore()

        val bold = ResourcesCompat.getFont(context, R.font.google_sans_bold)
        val regular = ResourcesCompat.getFont(context, R.font.google_sans_regular)
        val textWidth = (WIDTH - 2 * 120)

        val titlePaint =
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 72f
                typeface = bold
            }
        val titleLayout =
            StaticLayout.Builder
                .obtain(title, 0, title.length, titlePaint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setMaxLines(2)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setLineSpacing(0f, 1.05f)
                .build()
        var y = coverRect.bottom + 110f
        canvas.save()
        canvas.translate(120f, y)
        titleLayout.draw(canvas)
        canvas.restore()
        y += titleLayout.height + 26f

        val subtitlePaint =
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xCCFFFFFF.toInt()
                textSize = 46f
                typeface = regular
            }
        val subtitleLayout =
            StaticLayout.Builder
                .obtain(subtitle, 0, subtitle.length, subtitlePaint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setMaxLines(1)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
        canvas.save()
        canvas.translate(120f, y)
        subtitleLayout.draw(canvas)
        canvas.restore()

        val brand =
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x99FFFFFF.toInt()
                textSize = 36f
                typeface = bold
                textAlign = Paint.Align.CENTER
                letterSpacing = 0.08f
            }
        canvas.drawText(context.getString(R.string.app_name), WIDTH / 2f, HEIGHT - 150f, brand)
        return bitmap
    }
}
