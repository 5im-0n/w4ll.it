package it.w4ll

import android.app.WallpaperManager
import android.content.Context
import android.os.IBinder
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.ByteArrayInputStream

/**
 * Quick Settings tile which applies the next cached wallpaper to the same target
 * (home screen, lock screen, or both) chosen when the user last applied one in the app.
 */
class NextWallpaperTileService : TileService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        refreshTileState()
    }

    override fun onClick() {
        super.onClick()
        if (!CachedWallpaperRotation.hasCachedWallpapers(this)) {
            refreshTileState()
            Toast.makeText(this, "No cached wallpapers yet", Toast.LENGTH_SHORT).show()
            return
        }

        qsTile?.let { tile ->
            tile.state = Tile.STATE_ACTIVE
            tile.updateTile()
        }
        serviceScope.launch {
            val applied = runCatching {
                withContext(Dispatchers.IO) {
                    CachedWallpaperRotation.applyNextWallpaper(this@NextWallpaperTileService)
                }
            }.getOrDefault(false)

            Toast.makeText(
                this@NextWallpaperTileService,
                if (applied) "Next wallpaper applied" else "Couldn’t apply the next wallpaper",
                Toast.LENGTH_SHORT
            ).show()
            refreshTileState()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun refreshTileState() {
        qsTile?.let { tile ->
            tile.label = getString(R.string.next_wallpaper_tile_label)
            tile.state = if (CachedWallpaperRotation.hasCachedWallpapers(this)) {
                Tile.STATE_INACTIVE
            } else {
                Tile.STATE_UNAVAILABLE
            }
            tile.updateTile()
        }
    }
}

/** Shared cache rotation logic used by the Quick Settings tile and periodic worker. */
internal object CachedWallpaperRotation {
    fun hasCachedWallpapers(context: Context): Boolean = cachedImageUrls(context).isNotEmpty()

    /** Applies the entry following the most recently applied wallpaper, wrapping to the first entry. */
    fun applyNextWallpaper(context: Context): Boolean {
        val preferences = context.getSharedPreferences(MainActivity.PREFERENCES_NAME, Context.MODE_PRIVATE)
        val wallpapers = cachedImageUrls(context)
        if (wallpapers.isEmpty()) return false

        val previousUrl = preferences.getString(MainActivity.LAST_APPLIED_WALLPAPER_URL_KEY, null)
        val previousIndex = wallpapers.indexOf(previousUrl)
        val nextUrl = wallpapers[(previousIndex + 1).mod(wallpapers.size)]
        val target = savedTarget(preferences.getInt(
            MainActivity.LAST_WALLPAPER_TARGET_KEY,
            WallpaperManager.FLAG_SYSTEM
        ))

        val request = Request.Builder()
            .url(nextUrl)
            .header("User-Agent", MainActivity.USER_AGENT)
            .build()
        OkHttpClient().newCall(request).execute().use { response ->
            if (!response.isSuccessful) return false
            val body = response.body ?: return false
            WallpaperManager.getInstance(context).setStream(
                ByteArrayInputStream(body.bytes()),
                null,
                true,
                target
            )
        }
        rememberWallpaperApplication(context, nextUrl, target)
        return true
    }

    fun rememberWallpaperApplication(context: Context, imageUrl: String, target: Int) {
        context.getSharedPreferences(MainActivity.PREFERENCES_NAME, Context.MODE_PRIVATE).edit()
            .putString(MainActivity.LAST_APPLIED_WALLPAPER_URL_KEY, imageUrl)
            .putInt(MainActivity.LAST_WALLPAPER_TARGET_KEY, savedTarget(target))
            .apply()
    }

    private fun cachedImageUrls(context: Context): List<String> {
        val serialized = context.getSharedPreferences(MainActivity.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(MainActivity.CACHED_WALLPAPERS_KEY, null) ?: return emptyList()
        return runCatching {
            val values = JSONArray(serialized)
            List(values.length()) { index -> values.getJSONObject(index).getString("imageUrl") }
        }.getOrDefault(emptyList())
    }

    private fun savedTarget(target: Int): Int = when (target) {
        WallpaperManager.FLAG_SYSTEM,
        WallpaperManager.FLAG_LOCK,
        WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK -> target
        else -> WallpaperManager.FLAG_SYSTEM
    }
}
