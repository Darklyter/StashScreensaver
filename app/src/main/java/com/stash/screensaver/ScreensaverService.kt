package com.stash.screensaver

import android.content.Context
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Rect
import android.service.dreams.DreamService
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.google.gson.Gson
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt
import kotlin.random.Random

class ScreensaverService : DreamService() {

    companion object {
        private const val TAG = "StashScreensaver"
        private const val DEFAULT_STASH_URL = "http://192.168.1.71:9999/graphql"
        private const val DEFAULT_RETRIEVE_COUNT = 250
        private const val DEFAULT_DISPLAY_COUNT = 4
        private const val DEFAULT_REFRESH_DELAY = 30
        private const val DEFAULT_DELAY_VARIANCE = 20

        private const val SPLASH_FEMALE_URL = "https://images.unsplash.com/photo-1594616091765-92c6f3f7b91a?q=80&w=1740&auto=format&fit=crop&ixlib=rb-4.1.0&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D"
        private const val SPLASH_MALE_URL = "https://images.unsplash.com/photo-1683449155666-7531f07a9b68?q=80&w=1744&auto=format&fit=crop&ixlib=rb-4.1.0&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D"
        
        private const val FADE_MS = 1000L
        private const val MIN_SPLASH_TIME_MS = 5000L
    }

    private var stashUrl = DEFAULT_STASH_URL
    private var retrieveCount = DEFAULT_RETRIEVE_COUNT
    private var displayCount = DEFAULT_DISPLAY_COUNT
    private var baseRefreshDelayMs = DEFAULT_REFRESH_DELAY * 1000L
    private var delayVariancePercent = DEFAULT_DELAY_VARIANCE
    private var splashGender = "female"
    private var imageOrientation = "portrait"
    private var includedTags = ""
    private var excludedTags = ""

    private data class ImageData(val url: String, val width: Int, val height: Int)
    
    private var masterImageList = mutableListOf<ImageData>()
    private var currentShuffledQueue = mutableListOf<ImageData>()
    private var pendingImageList = mutableListOf<ImageData>()
    private val isFetchingBatch = AtomicBoolean(false)

    private val imageViews = mutableListOf<ImageView>()
    private val imageJobs = mutableListOf<Job?>()
    private val currentRects = mutableMapOf<Int, Rect>()
    private val layoutMutex = Any()

    private var mainJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
        
    private val gson = Gson()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        loadSettings()
        isInteractive = false
        isFullscreen = true
        try {
            setContentView(R.layout.screensaver_layout)
            setupSplashScreen()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onAttachedToWindow", e)
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("stash_prefs", Context.MODE_PRIVATE)
        val address = prefs.getString("stash_address", "192.168.1.71")
        val port = prefs.getString("stash_port", "9999")
        stashUrl = "http://$address:$port/graphql"
        retrieveCount = prefs.getInt("retrieve_count", 250)
        displayCount = prefs.getInt("display_count", 4)
        baseRefreshDelayMs = prefs.getInt("refresh_delay", 30) * 1000L
        delayVariancePercent = prefs.getInt("delay_variance", 20)
        splashGender = prefs.getString("splash_gender", "female") ?: "female"
        imageOrientation = prefs.getString("image_orientation", "portrait") ?: "portrait"
        includedTags = prefs.getString("included_tags", "") ?: ""
        excludedTags = prefs.getString("excluded_tags", "") ?: ""
    }

    private fun setupSplashScreen() {
        val splashContainer = findViewById<View>(R.id.splash_container)
        val splashBg = findViewById<ImageView>(R.id.splash_background)
        val tvLoading = findViewById<TextView>(R.id.tv_loading)
        
        splashContainer?.visibility = View.VISIBLE
        splashContainer?.alpha = 1.0f
        splashContainer?.bringToFront()
        
        tvLoading?.text = "Loading images, please wait"

        val matrix = ColorMatrix()
        matrix.setSaturation(0f)
        splashBg?.colorFilter = ColorMatrixColorFilter(matrix)

        val splashUrl = if (splashGender == "male") SPLASH_MALE_URL else SPLASH_FEMALE_URL

        if (splashBg != null) {
            Glide.with(applicationContext)
                .load(splashUrl)
                .centerCrop()
                .into(splashBg)
        }
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        val container = findViewById<FrameLayout>(R.id.screensaver_container) ?: return
        val splash = findViewById<View>(R.id.splash_container)
        val tvLoading = findViewById<TextView>(R.id.tv_loading)

        val viewsToRemove = mutableListOf<View>()
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child != splash) viewsToRemove.add(child)
        }
        viewsToRemove.forEach { container.removeView(it) }

        imageViews.clear()
        imageJobs.forEach { it?.cancel() }
        imageJobs.clear()
        currentRects.clear()
        
        for (i in 0 until displayCount) {
            val iv = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(100, 100)
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(0xFF111111.toInt())
                alpha = 0f
                visibility = View.GONE
            }
            container.addView(iv, 0)
            imageViews.add(iv)
            imageJobs.add(null)
        }

        mainJob?.cancel()
        mainJob = scope.launch {
            val startTime = System.currentTimeMillis()
            tvLoading.text = "Retrieving Images..."
            fetchImagesFromStash(true)
            
            if (currentShuffledQueue.isNotEmpty()) {
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed < MIN_SPLASH_TIME_MS) {
                    delay(MIN_SPLASH_TIME_MS - elapsed)
                }

                while (container.width == 0 || container.height == 0) {
                    delay(50)
                }
                
                planGlobalInitialLayout(container.width, container.height)

                splash?.animate()?.alpha(0f)?.setDuration(1500)?.withEndAction {
                    splash.visibility = View.GONE
                }?.start()

                imageViews.forEachIndexed { index, view ->
                    startImageCycle(index, view)
                }
            } else {
                tvLoading.text = "Error: Stash server unreachable"
            }
        }
    }

    private fun planGlobalInitialLayout(screenW: Int, screenH: Int) {
        synchronized(layoutMutex) {
            var coverageTarget = 0.75
            var success = false
            val random = Random(System.currentTimeMillis())

            while (!success && coverageTarget > 0.2) {
                currentRects.clear()
                val targetAreaPerImage = (screenW * screenH * coverageTarget) / displayCount
                
                var allPlaced = true
                for (i in 0 until displayCount) {
                    var placed = false
                    val imgData = if (i < currentShuffledQueue.size) currentShuffledQueue[i] else null
                    val ratio = if (imgData != null && imgData.height > 0) imgData.width.toDouble() / imgData.height else 0.66
                    
                    val baseH = sqrt(targetAreaPerImage / ratio)
                    
                    repeat(400) {
                        val h = (baseH * (0.9 + random.nextFloat() * 0.2)).toInt()
                        val w = (h * ratio).toInt()
                        val left = random.nextInt(0, (screenW - w).coerceAtLeast(1))
                        val top = random.nextInt(0, (screenH - h).coerceAtLeast(1))
                        val candidate = Rect(left, top, left + w, top + h)
                        
                        val buffer = (screenW * 0.02).toInt()
                        val candidateWithBuffer = Rect(candidate.left - buffer, candidate.top - buffer, candidate.right + buffer, candidate.bottom + buffer)
                        
                        var hasOverlap = false
                        for (placedRect in currentRects.values) {
                            if (Rect.intersects(candidateWithBuffer, placedRect)) {
                                hasOverlap = true
                                break
                            }
                        }
                        
                        if (!hasOverlap) {
                            currentRects[i] = candidate
                            placed = true
                            return@repeat
                        }
                    }
                    if (!placed) {
                        allPlaced = false
                        break
                    }
                }
                
                if (allPlaced) {
                    success = true
                } else {
                    coverageTarget -= 0.05
                }
            }
            
            imageViews.forEachIndexed { index, iv ->
                val rect = currentRects[index]
                if (rect != null) {
                    val lp = FrameLayout.LayoutParams(rect.width(), rect.height())
                    lp.leftMargin = rect.left
                    lp.topMargin = rect.top
                    iv.layoutParams = lp
                    iv.rotation = (random.nextFloat() * 16f) - 8f
                }
            }
        }
    }

    private suspend fun fetchImagesFromStash(isInitial: Boolean) = withContext(Dispatchers.IO) {
        if (isFetchingBatch.getAndSet(true)) return@withContext
        try {
            val orientationFilter = when (imageOrientation) {
                "portrait" -> "orientation: { value: PORTRAIT },"
                "landscape" -> "orientation: { value: LANDSCAPE },"
                else -> "" 
            }

            var galleriesFilter = ""
            val includedList = includedTags.split(",").mapNotNull { it.trim().toIntOrNull() }
            val excludedList = excludedTags.split(",").mapNotNull { it.trim().toIntOrNull() }

            if (includedList.isNotEmpty() || excludedList.isNotEmpty()) {
                val modifier = if (includedList.isNotEmpty()) "INCLUDES" else "EXCLUDES"
                val valuePart = if (includedList.isNotEmpty()) "value: [${includedList.joinToString(",")}]" else "value: [${excludedList.joinToString(",")}]"
                val excludePart = if (includedList.isNotEmpty() && excludedList.isNotEmpty()) "\n          excludes: [${excludedList.joinToString(",")}]" else ""
                
                galleriesFilter = """
                    galleries_filter: {
                        tags: {
                          modifier: $modifier
                          $valuePart$excludePart
                        }
                    }
                """.trimIndent()
            }

            val query = """
                query {
                  findImages(
                    filter: { per_page: $retrieveCount, sort: "random", direction: DESC }
                    image_filter: { 
                      $orientationFilter
                      resolution: { value: STANDARD_HD, modifier: GREATER_THAN } 
                      $galleriesFilter
                    }
                  ) { 
                    images { 
                      paths { image } 
                      visual_files {
                        ... on ImageFile {
                          width
                          height
                        }
                      }
                    } 
                  }
                }
            """.trimIndent()

            val body = gson.toJson(mapOf("query" to query)).toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(stashUrl).post(body).addHeader("Accept", "application/json").build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val data = gson.fromJson(response.body?.string(), StashResponse::class.java)
                    val newImages = mutableListOf<ImageData>()
                    data.data?.findImages?.images?.forEach { image ->
                        val url = image.paths?.image
                        val file = image.visual_files?.firstOrNull()
                        if (url != null) {
                            newImages.add(ImageData(url, file?.width ?: 0, file?.height ?: 0))
                        }
                    }
                    if (newImages.isNotEmpty()) {
                        synchronized(this@ScreensaverService) {
                            if (isInitial) {
                                masterImageList = newImages
                                currentShuffledQueue = newImages.shuffled().toMutableList()
                            } else {
                                pendingImageList = newImages
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fetch failed", e)
        } finally {
            isFetchingBatch.set(false)
        }
    }

    private fun getNextImageData(): ImageData? {
        synchronized(this) {
            if (currentShuffledQueue.isEmpty()) {
                if (pendingImageList.isNotEmpty()) {
                    masterImageList = pendingImageList.toMutableList()
                    pendingImageList.clear()
                }
                if (masterImageList.isNotEmpty()) {
                    currentShuffledQueue = masterImageList.shuffled().toMutableList()
                } else {
                    return null
                }
            }
            
            return if (currentShuffledQueue.isNotEmpty()) {
                val data = currentShuffledQueue.removeAt(0)
                if (currentShuffledQueue.size < (displayCount * 3) && pendingImageList.isEmpty()) {
                    scope.launch { fetchImagesFromStash(false) }
                }
                data
            } else null
        }
    }

    private fun repositionImageView(view: ImageView, index: Int, imgData: ImageData) {
        synchronized(layoutMutex) {
            val container = findViewById<FrameLayout>(R.id.screensaver_container) ?: return
            val screenW = container.width
            val screenH = container.height
            if (screenW <= 0 || screenH <= 0) return

            val ratio = if (imgData.height > 0) imgData.width.toDouble() / imgData.height else 0.66
            val targetAreaPerImage = (screenW * screenH * 0.75) / displayCount
            val idealH = sqrt(targetAreaPerImage / ratio)

            var bestScore = Double.NEGATIVE_INFINITY
            var bestParams: FrameLayout.LayoutParams? = null
            var bestRot = 0f
            var bestCandidateRect: Rect? = null
            
            val random = Random(System.currentTimeMillis() + index + view.hashCode())

            repeat(150) {
                val isHero = random.nextFloat() < 0.20
                val sizeMultiplier = if (isHero) (1.1 + random.nextFloat() * 0.4) else (0.85 + random.nextFloat() * 0.4)
                val h = (idealH * sizeMultiplier).toInt().coerceIn(250, (screenH * 0.95).toInt())
                val w = (h * ratio).toInt()
                
                val left = random.nextInt(0, (screenW - w).coerceAtLeast(1))
                val top = random.nextInt(0, (screenH - h).coerceAtLeast(1))
                val candidate = Rect(left, top, left + w, top + h)
                
                var overlapArea = 0L
                for (entry in currentRects) {
                    if (entry.key == index) continue
                    val intersection = Rect()
                    if (intersection.setIntersect(candidate, entry.value)) {
                        overlapArea += (intersection.width().toLong() * intersection.height().toLong())
                    }
                }
                
                val score = (w.toDouble() * h) - (overlapArea * 30.0)
                if (score > bestScore) {
                    bestScore = score
                    bestParams = FrameLayout.LayoutParams(w, h).apply {
                        leftMargin = left
                        topMargin = top
                    }
                    bestRot = (random.nextFloat() * 24f) - 12f
                    bestCandidateRect = candidate
                }
                if (overlapArea == 0L && isHero) return@repeat
            }

            bestParams?.let {
                view.layoutParams = it
                view.rotation = bestRot
                view.bringToFront()
                bestCandidateRect?.let { rect -> currentRects[index] = rect }
            }
        }
    }

    private fun startImageCycle(index: Int, view: ImageView) {
        view.visibility = View.VISIBLE
        imageJobs[index]?.cancel()
        imageJobs[index] = scope.launch {
            if (index > 0 && view.alpha == 0f) delay(index * 1500L)
            
            var isFirstLoad = true
            while (isActive) {
                val imgData = getNextImageData()
                if (imgData != null) {
                    if (!isFirstLoad) {
                        view.animate().alpha(0f).setDuration(FADE_MS).start()
                        delay(FADE_MS)
                        repositionImageView(view, index, imgData)
                    } else {
                        view.alpha = 0f
                    }
                    
                    withContext(Dispatchers.Main) {
                        Glide.with(applicationContext).load(imgData.url).centerCrop().into(view)
                    }
                    view.animate().alpha(1f).setDuration(FADE_MS).start()
                    isFirstLoad = false
                } else {
                    delay(2000)
                    continue
                }
                
                val varianceMs = (baseRefreshDelayMs * (delayVariancePercent / 100.0)).toLong()
                val randomOffset = if (varianceMs > 0) Random.nextLong(-varianceMs, varianceMs + 1) else 0L
                val finalDelayMs = (baseRefreshDelayMs + randomOffset).coerceAtLeast(2000L)
                delay(finalDelayMs)
            }
        }
    }

    override fun onDreamingStopped() {
        mainJob?.cancel()
        imageJobs.forEach { it?.cancel() }
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        scope.cancel()
        imageViews.forEach { Glide.with(applicationContext).clear(it) }
        super.onDetachedFromWindow()
    }

    private data class StashResponse(val data: StashData?)
    private data class StashData(val findImages: FindImages?)
    private data class FindImages(val images: List<StashImage>?)
    private data class StashImage(val paths: StashPaths?, val visual_files: List<VisualFile>?)
    private data class StashPaths(val image: String?)
    private data class VisualFile(val width: Int, val height: Int)
}
