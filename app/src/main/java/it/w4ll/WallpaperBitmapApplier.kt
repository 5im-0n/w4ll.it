package it.w4ll

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import java.io.File
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Prepares wallpapers with a modest amount of horizontal room for launchers that pan
 * wallpapers between home-screen pages.
 *
 * The wallpaper is 125% of the display width and exactly the display height. Its center is
 * therefore centered on a non-scrolling home screen, while a launcher that supports wallpaper
 * scrolling can reveal the portions on either side. The same prepared bitmap is used for the
 * home and lock targets when both are requested.
 */
internal object WallpaperBitmapApplier {
    private const val HOME_WIDTH_MULTIPLIER = 1.25f
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

    /**
     * Streams the response into a temporary disk file before decoding it. This avoids retaining
     * the complete compressed download in memory.
     */
    fun apply(context: Context, imageStream: InputStream, target: Int) {
        val imageFile = File.createTempFile("wallpaper-", ".image", context.cacheDir)
        try {
            imageStream.use { input ->
                imageFile.outputStream().buffered().use(input::copyTo)
            }
            apply(context, imageFile, target)
        } finally {
            imageFile.delete()
        }
    }

    private fun apply(context: Context, imageFile: File, target: Int) {
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        require(screenWidth > 0 && screenHeight > 0) { "Display dimensions are unavailable." }

        val homeWidth = (screenWidth * HOME_WIDTH_MULTIPLIER).roundToInt()
        val wallpaperBitmap = decodeCenteredHomeBitmap(imageFile, homeWidth, screenHeight)
            ?: throw IllegalArgumentException("The downloaded file is not a valid image.")

        try {
            val hasSystemTarget = target and WallpaperManager.FLAG_SYSTEM != 0
            val hasLockTarget = target and WallpaperManager.FLAG_LOCK != 0
            val manager = WallpaperManager.getInstance(context)

            // Reuse the exact same prepared bitmap. The lock screen centers it itself and does
            // not require a separate screen-width crop.
            if (hasSystemTarget) {
                manager.setBitmap(wallpaperBitmap, null, true, WallpaperManager.FLAG_SYSTEM)
            }
            if (hasLockTarget) {
                manager.setBitmap(wallpaperBitmap, null, true, WallpaperManager.FLAG_LOCK)
            }
        } finally {
            wallpaperBitmap.recycle()
        }
    }

    /**
     * Decodes only the centered source rectangle that can contribute to the final wallpaper,
     * rather than decoding the original image's unused outer areas.
     */
    private fun decodeCenteredHomeBitmap(
        imageFile: File,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imageFile.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sourceRegion = centeredRegion(bounds.outWidth, bounds.outHeight, targetWidth, targetHeight)
        val decodedRegion = decodeRegion(imageFile, sourceRegion, targetWidth, targetHeight) ?: return null

        if (decodedRegion.width == targetWidth && decodedRegion.height == targetHeight) {
            return decodedRegion
        }

        return try {
            centerCrop(decodedRegion, targetWidth, targetHeight)
        } finally {
            decodedRegion.recycle()
        }
    }

    private fun decodeRegion(
        imageFile: File,
        sourceRegion: Rect,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? {
        val decoder = BitmapRegionDecoder.newInstance(imageFile.path, false) ?: return null
        return try {
            val sampleSize = sampleSizeFor(sourceRegion, targetWidth, targetHeight)
            decoder.decodeRegion(sourceRegion, BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })
        } finally {
            decoder.recycle()
        }
    }

    private fun centeredRegion(
        sourceWidth: Int,
        sourceHeight: Int,
        targetWidth: Int,
        targetHeight: Int
    ): Rect {
        val targetAspectRatio = targetWidth.toFloat() / targetHeight
        val sourceAspectRatio = sourceWidth.toFloat() / sourceHeight

        return if (sourceAspectRatio > targetAspectRatio) {
            val regionWidth = min(sourceWidth, (sourceHeight * targetAspectRatio).roundToInt())
            val left = (sourceWidth - regionWidth) / 2
            Rect(left, 0, left + regionWidth, sourceHeight)
        } else {
            val regionHeight = min(sourceHeight, (sourceWidth / targetAspectRatio).roundToInt())
            val top = (sourceHeight - regionHeight) / 2
            Rect(0, top, sourceWidth, top + regionHeight)
        }
    }

    private fun sampleSizeFor(sourceRegion: Rect, targetWidth: Int, targetHeight: Int): Int {
        var sampleSize = 1
        while (
            sourceRegion.width() / (sampleSize * 2) >= targetWidth &&
            sourceRegion.height() / (sampleSize * 2) >= targetHeight
        ) {
            sampleSize *= 2
        }
        return sampleSize
    }

    /** Scales to fill [targetWidth] x [targetHeight], then crops equally around the center. */
    private fun centerCrop(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val scale = max(
            targetWidth.toFloat() / source.width,
            targetHeight.toFloat() / source.height
        )
        val scaledWidth = source.width * scale
        val scaledHeight = source.height * scale
        val left = (targetWidth - scaledWidth) / 2f
        val top = (targetHeight - scaledHeight) / 2f

        return Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888).also { output ->
            Canvas(output).drawBitmap(
                source,
                null,
                RectF(left, top, left + scaledWidth, top + scaledHeight),
                paint
            )
        }
    }
}
