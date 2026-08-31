package it.w4ll

import android.app.WallpaperManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import coil.load
import it.w4ll.databinding.ActivityMainBinding
import it.w4ll.databinding.ItemWallpaperBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val client = OkHttpClient()
    private val adapter = WallpaperAdapter(::setWallpaper)
    private var currentPage = Page.HOME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { view, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.setPadding(view.paddingLeft, statusBar.top, view.paddingRight, view.paddingBottom)
            insets
        }

        binding.wallpaperList.layoutManager = GridLayoutManager(this, 2)
        binding.wallpaperList.adapter = adapter
        loadFetchSettings()
        scheduleBackgroundWork(
            getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
                .getLong(WALLPAPER_INTERVAL_HOURS_KEY, DEFAULT_WALLPAPER_INTERVAL_HOURS)
        )
        val cachedWallpapers = loadCachedWallpapers()
        adapter.submit(cachedWallpapers)
        updateHomeState()

        binding.settingsButton.setOnClickListener {
            showPage(Page.SETTINGS)
        }
        binding.settingsFetchButton.setOnClickListener { fetchWallpapers() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentPage != Page.HOME) {
                    showPage(Page.HOME)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        if (cachedWallpapers.isEmpty()) {
            fetchWallpapers()
        }
    }

    private fun showPage(page: Page) {
        currentPage = page
        binding.homeContainer.visibility = if (page == Page.HOME) View.VISIBLE else View.GONE
        binding.settingsContainer.visibility = if (page == Page.SETTINGS) View.VISIBLE else View.GONE
        binding.infoContainer.visibility = if (page == Page.INFO) View.VISIBLE else View.GONE
        binding.feedbackContainer.visibility = if (page == Page.HOME) View.VISIBLE else View.GONE
        binding.toolbar.title = when (page) {
            Page.HOME -> "w4ll.it"
            Page.SETTINGS -> "Settings"
            Page.INFO -> "Info"
        }
        binding.settingsButton.contentDescription =
            if (page == Page.SETTINGS) "Return to wallpapers" else "Open settings"
        if (page == Page.HOME) updateHomeState()
    }

    private fun fetchWallpapers() {
        val count = binding.imageCountInput.text?.toString()?.toIntOrNull()
        if (count == null || count !in 1..100) {
            binding.imageCountInput.error = "Enter a number from 1 to 100"
            return
        }
        val intervalHours = binding.wallpaperIntervalInput.text?.toString()?.toLongOrNull()
        if (intervalHours == null || intervalHours !in 0..168) {
            binding.wallpaperIntervalInput.error = "Enter 0 to 168 hours"
            return
        }
        val tags = binding.searchQueryInput.text?.toString()?.trim().orEmpty()
        val tagQueries = tags.toWallhavenTagQueries()
        saveFetchSettings(count, tags, intervalHours)
        scheduleBackgroundWork(intervalHours)

        lifecycleScope.launch {
            binding.settingsFetchButton.isEnabled = false
            binding.progress.visibility = View.VISIBLE
            binding.feedbackContainer.visibility = View.VISIBLE
            binding.statusText.text = "Fetching wallpapers…"

            try {
                val wallpapers = withContext(Dispatchers.IO) {
                    WallhavenRepository(client).fetch(tagQueries, count)
                }
                if (wallpapers.isNotEmpty()) {
                    saveCachedWallpapers(wallpapers)
                    adapter.submit(wallpapers)
                    showPage(Page.HOME)
                    binding.statusText.text = "Found ${wallpapers.size} wallpaper${if (wallpapers.size == 1) "" else "s"}. Choose where to apply one."
                } else {
                    binding.statusText.text = "No safe wallpapers matched that search. Try different keywords."
                }
            } catch (e: WallpaperFetchException) {
                binding.statusText.text = e.message
                Snackbar.make(binding.root, e.message ?: "Wallpaper request failed", Snackbar.LENGTH_LONG).show()
            } catch (e: Exception) {
                binding.statusText.text = "Couldn’t fetch wallpapers. Check your connection and try again."
                Snackbar.make(binding.root, e.message ?: "Wallpaper request failed", Snackbar.LENGTH_LONG).show()
            } finally {
                binding.progress.visibility = View.GONE
                binding.settingsFetchButton.isEnabled = true
                updateHomeState()
            }
        }
    }

    private fun updateHomeState() {
        val hasWallpapers = adapter.itemCount > 0
        binding.emptyState.visibility = if (hasWallpapers) View.GONE else View.VISIBLE
        binding.wallpaperList.visibility = if (hasWallpapers) View.VISIBLE else View.GONE
    }

    /**
     * Creates one Wallhaven query per comma-separated tag. Fetching every query and
     * merging the results implements OR semantics while keeping Wallhaven's native
     * tag matching intact. For example, "nature, abstract art" becomes
     * ["nature", "{abstract art}"].
     */
    private fun String.toWallhavenTagQueries(): List<String> =
        split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { tag -> if (tag.any(Char::isWhitespace)) "{$tag}" else tag }
            .ifEmpty { listOf("") }

    private fun saveFetchSettings(count: Int, tags: String, intervalHours: Long) {
        getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE).edit()
            .putInt(IMAGE_COUNT_KEY, count)
            .putString(TAGS_KEY, tags)
            .putLong(WALLPAPER_INTERVAL_HOURS_KEY, intervalHours)
            .apply()
    }

    private fun loadFetchSettings() {
        val preferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
        binding.imageCountInput.setText(preferences.getInt(IMAGE_COUNT_KEY, DEFAULT_IMAGE_COUNT).toString())
        binding.searchQueryInput.setText(preferences.getString(TAGS_KEY, DEFAULT_TAGS) ?: DEFAULT_TAGS)
        binding.wallpaperIntervalInput.setText(
            preferences.getLong(WALLPAPER_INTERVAL_HOURS_KEY, DEFAULT_WALLPAPER_INTERVAL_HOURS).toString()
        )
    }

    private fun scheduleBackgroundWork(intervalHours: Long) {
        val workManager = WorkManager.getInstance(this)
        val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val refreshRequest = PeriodicWorkRequestBuilder<WallpaperRefreshWorker>(24, TimeUnit.HOURS)
            .setConstraints(networkConstraints)
            .build()
        workManager.enqueueUniquePeriodicWork(
            DAILY_REFRESH_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            refreshRequest
        )

        if (intervalHours == 0L) {
            workManager.cancelUniqueWork(WALLPAPER_CHANGE_WORK_NAME)
        } else {
            val changeRequest = PeriodicWorkRequestBuilder<WallpaperChangeWorker>(intervalHours, TimeUnit.HOURS)
                .setConstraints(networkConstraints)
                .build()
            workManager.enqueueUniquePeriodicWork(
                WALLPAPER_CHANGE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                changeRequest
            )
        }
    }

    private fun saveCachedWallpapers(wallpapers: List<WallpaperPost>) {
        val values = JSONArray()
        wallpapers.forEach { wallpaper ->
            values.put(JSONObject().apply {
                put("title", wallpaper.title)
                put("details", wallpaper.details)
                put("imageUrl", wallpaper.imageUrl)
                put("addedAt", wallpaper.addedAt)
            })
        }
        getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE).edit()
            .putString(CACHED_WALLPAPERS_KEY, values.toString())
            .apply()
    }

    private fun loadCachedWallpapers(): List<WallpaperPost> {
        val serialized = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
            .getString(CACHED_WALLPAPERS_KEY, null) ?: return emptyList()
        return runCatching {
            val values = JSONArray(serialized)
            List(values.length()) { index ->
                values.getJSONObject(index).let { item ->
                    WallpaperPost(
                        title = item.optString("title", "Wallpaper"),
                        details = item.optString("details", ""),
                        imageUrl = item.getString("imageUrl"),
                        addedAt = item.optString("addedAt")
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun setWallpaper(post: WallpaperPost, target: Int) {
        lifecycleScope.launch {
            binding.feedbackContainer.visibility = View.VISIBLE
            binding.progress.visibility = View.VISIBLE
            binding.statusText.text = "Downloading and applying wallpaper…"
            try {
                withContext(Dispatchers.IO) {
                    val request = Request.Builder().url(post.imageUrl).header("User-Agent", USER_AGENT).build()
                    client.newCall(request).execute().use { response ->
                        check(response.isSuccessful) { "Image download failed (${response.code})" }
                        val body = response.body ?: throw IllegalStateException("Empty image response")
                        WallpaperManager.getInstance(this@MainActivity).setStream(
                            ByteArrayInputStream(body.bytes()), null, true, target
                        )
                    }
                }
                CachedWallpaperRotation.rememberWallpaperApplication(this@MainActivity, post.imageUrl, target)
                binding.statusText.text = "Wallpaper applied."
                Toast.makeText(this@MainActivity, "Wallpaper applied", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                binding.statusText.text = "Couldn’t apply this wallpaper."
                Snackbar.make(binding.root, e.message ?: "Wallpaper error", Snackbar.LENGTH_LONG).show()
            } finally {
                binding.progress.visibility = View.GONE
            }
        }
    }

    private enum class Page { HOME, SETTINGS, INFO }

    companion object {
        const val USER_AGENT = "WallhavenWallpaperApp/1.0 (Android)"
        const val PREFERENCES_NAME = "wallpaper_cache"
        const val CACHED_WALLPAPERS_KEY = "cached_wallpapers"
        const val IMAGE_COUNT_KEY = "image_count"
        const val TAGS_KEY = "wallpaper_tags"
        const val WALLPAPER_INTERVAL_HOURS_KEY = "wallpaper_interval_hours"
        const val LAST_APPLIED_WALLPAPER_URL_KEY = "last_applied_wallpaper_url"
        const val LAST_WALLPAPER_TARGET_KEY = "last_wallpaper_target"
        const val DEFAULT_IMAGE_COUNT = 50
        const val DEFAULT_TAGS = "nature, abstract, landscape, city"
        const val DEFAULT_WALLPAPER_INTERVAL_HOURS = 6L
        const val DAILY_REFRESH_WORK_NAME = "daily_wallpaper_refresh"
        const val WALLPAPER_CHANGE_WORK_NAME = "periodic_wallpaper_change"
    }
}

data class WallpaperPost(
    val title: String,
    val details: String,
    val imageUrl: String,
    val addedAt: String = ""
)

private class WallpaperFetchException(message: String) : Exception(message)

/** Uses Wallhaven's public, SFW-only v1 API. No account or credential is sent. */
class WallhavenRepository(private val client: OkHttpClient) {
    /** Fetches and merges recent results for every tag, so tags are ORed together. */
    fun fetch(queries: List<String>, maxItems: Int): List<WallpaperPost> {
        val collected = linkedMapOf<String, WallpaperPost>()
        val pagesPerQuery = (maxItems + RESULTS_PER_PAGE - 1) / RESULTS_PER_PAGE

        for (query in queries) {
            for (page in 1..pagesPerQuery) {
                val url = "https://wallhaven.cc/api/v1/search".toHttpUrl().newBuilder()
                    .addQueryParameter("q", query)
                    .addQueryParameter("categories", "111")
                    .addQueryParameter("purity", "100")
                    .addQueryParameter("sorting", "date_added")
                    .addQueryParameter("order", "desc")
                    .addQueryParameter("page", page.toString())
                    .build()
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", MainActivity.USER_AGENT)
                    .header("Accept", "application/json")
                    .build()

                val data = client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw WallpaperFetchException("Wallhaven could not load wallpapers (${response.code}). Please try again later.")
                    }
                    try {
                        JSONObject(body).getJSONArray("data")
                    } catch (_: Exception) {
                        throw WallpaperFetchException("Wallhaven returned an invalid response. Please try again later.")
                    }
                }
                if (data.length() == 0) break

                for (index in 0 until data.length()) {
                    val item = data.optJSONObject(index) ?: continue
                    val imageUrl = item.optString("path").takeIf(::isImageUrl) ?: continue
                    val resolution = item.optString("resolution", "Unknown resolution")
                    val category = item.optString("category", "wallpaper")
                    collected.putIfAbsent(
                        imageUrl,
                        WallpaperPost(
                            title = "${category.replaceFirstChar { it.titlecase(Locale.US) }} wallpaper",
                            details = resolution,
                            imageUrl = imageUrl,
                            addedAt = item.optString("created_at")
                        )
                    )
                }
            }
        }
        return collected.values
            .sortedByDescending(WallpaperPost::addedAt)
            .take(maxItems)
    }

    private fun isImageUrl(url: String): Boolean {
        val path = runCatching { URI(url).path.lowercase(Locale.US) }.getOrDefault("")
        return url.startsWith("https://") &&
            (path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") || path.endsWith(".webp"))
    }

    private companion object {
        const val RESULTS_PER_PAGE = 24
    }
}

/** Refreshes the cache once every 24 hours without requiring the app to be open. */
class WallpaperRefreshWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val preferences = applicationContext.getSharedPreferences(
            MainActivity.PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
        val count = preferences.getInt(MainActivity.IMAGE_COUNT_KEY, MainActivity.DEFAULT_IMAGE_COUNT)
        val tags = preferences.getString(MainActivity.TAGS_KEY, MainActivity.DEFAULT_TAGS)
            ?: MainActivity.DEFAULT_TAGS
        val queries = tags.toWorkerWallhavenTagQueries()

        try {
            val wallpapers = WallhavenRepository(OkHttpClient()).fetch(queries, count)
            if (wallpapers.isEmpty()) return@withContext Result.success()

            val values = JSONArray()
            wallpapers.forEach { wallpaper ->
                values.put(JSONObject().apply {
                    put("title", wallpaper.title)
                    put("details", wallpaper.details)
                    put("imageUrl", wallpaper.imageUrl)
                    put("addedAt", wallpaper.addedAt)
                })
            }
            preferences.edit().putString(MainActivity.CACHED_WALLPAPERS_KEY, values.toString()).apply()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

/** Applies the next cached wallpaper at the interval chosen in Settings. */
class WallpaperChangeWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            CachedWallpaperRotation.applyNextWallpaper(applicationContext)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

private fun String.toWorkerWallhavenTagQueries(): List<String> =
    split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .map { tag -> if (tag.any(Char::isWhitespace)) "{$tag}" else tag }
        .ifEmpty { listOf("") }

private class WallpaperAdapter(
    private val onApply: (WallpaperPost, Int) -> Unit
) : RecyclerView.Adapter<WallpaperAdapter.ViewHolder>() {
    private var posts: List<WallpaperPost> = emptyList()

    fun submit(items: List<WallpaperPost>) {
        posts = items
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        ItemWallpaperBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(posts[position])
    override fun getItemCount() = posts.size

    inner class ViewHolder(private val binding: ItemWallpaperBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(post: WallpaperPost) {
            binding.thumbnail.load(post.imageUrl) { crossfade(true) }
            binding.titleText.text = post.title
            binding.communityText.text = post.details
            binding.homeButton.setOnClickListener { onApply(post, WallpaperManager.FLAG_SYSTEM) }
            binding.lockButton.setOnClickListener { onApply(post, WallpaperManager.FLAG_LOCK) }
            binding.bothButton.setOnClickListener {
                onApply(post, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
            }
        }
    }
}
