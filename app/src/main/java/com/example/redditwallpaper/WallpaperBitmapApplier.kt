package it.w4ll

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Prepares wallpapers with a modest amount of horizontal room for launchers that pan
 * wallpapers between home-screen pages.
 *
 * The home wallpaper is 125% of the display width and exactly the display height. Its
 * center is therefore the center of the image on a non-scrolling home screen, while a
 * launcher that supports wallpaper scrolling can reveal the portions on either side.
 */
internal object WallpaperBitmapApplier {
    private const val HOME_WIDTH_MULTIPLIER = 1.25f
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)

    fun apply(context: Context, imageBytes: ByteArray, target: Int) {
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        require(screenWidth > 0 && screenHeight > 0) { "Display dimensions are unavailable." }

        val source = decodeForDisplay(imageBytes, screenWidth, screenHeight)
            ?: throw IllegalArgumentException("The downloaded file is not a valid image.")

        var homeBitmap: Bitmap? = null
        var lockBitmap: Bitmap? = null
        try {
            val hasSystemTarget = target and WallpaperManager.FLAG_SYSTEM != 0
            val hasLockTarget = target and WallpaperManager.FLAG_LOCK != 0
            val manager = WallpaperManager.getInstance(context)

            if (hasSystemTarget || !hasLockTarget) {
                val homeWidth = (screenWidth * HOME_WIDTH_MULTIPLIER).roundToInt()
                homeBitmap = centerCrop(source, homeWidth, screenHeight)
                manager.setBitmap(homeBitmap, null, true, WallpaperManager.FLAG_SYSTEM)
            }

            if (hasLockTarget) {
                // Lock screens do not pan, so show a normal centered screen-sized crop.
                lockBitmap = centerCrop(source, screenWidth, screenHeight)
                manager.setBitmap(lockBitmap, null, true, WallpaperManager.FLAG_LOCK)
            }
        } finally {
            homeBitmap?.recycle()
            lockBitmap?.recycle()
            source.recycle()
        }
    }

    private fun decodeForDisplay(imageBytes: ByteArray, targetWidth: Int, targetHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val homeWidth = (targetWidth * HOME_WIDTH_MULTIPLIER).roundToInt()
        var sampleSize = 1
        while (
            bounds.outWidth / (sampleSize * 2) >= homeWidth &&
            bounds.outHeight / (sampleSize * 2) >= targetHeight
        ) {
            sampleSize *= 2
        }

        return BitmapFactory.decodeByteArray(
            imageBytes,
            0,
            imageBytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        )
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
