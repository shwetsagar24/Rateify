package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

private data class ScannedNode(
    val text: String,
    val id: String?,
    val className: String?
)

private data class ScannedNodeScore(
    val node: ScannedNode,
    val finalScore: Int
)

class MovieOverlayAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MovieRatingsAccessibility"
    }

    private var lastDetectedTitle: String? = null
    private var lastDetailTime: Long = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            val packageName = event.packageName?.toString() ?: return
            val pkg = packageName.lowercase()
            
            // Critical safety: Do NOT dismiss HUD or clear states when user touches HUD views, or during system UI/keyboard interactions.
            val isIgnoredPkg = pkg == "android" ||
                    pkg.startsWith("com.android.systemui") ||
                    pkg.contains("inputmethod") ||
                    pkg.contains("keyboard") ||
                    pkg.contains("com.example") ||
                    pkg.contains("com.aistudio") ||
                    pkg.contains("aistudio")
            
            if (isIgnoredPkg) {
                return
            }
            
            val appName = when {
                pkg.contains("netflix") -> "Netflix"
                pkg.contains("amazon.avod") || pkg.contains("amazon.amazonvideo") -> "Prime Video"
                pkg.contains("hotstar") || pkg.contains("startv") ||
                pkg.contains("jiocinema") || pkg.contains("jio.media.ondemand") || pkg.contains("jiotv") -> "Jio Hotstar"
                pkg.contains("sonyliv") || pkg.contains("sony.tv") -> "SonyLIV"
                pkg.contains("zee5") || pkg.contains("graymatrix") -> "ZEE5"
                else -> null
            }

            if (appName != null) {
                OverlayState.setStreamingApp(appName)
                
                // Traverse active window nodes to fetch raw text strings on the screen
                val rootNode = rootInActiveWindow
                if (rootNode != null) {
                    val scannedNodes = mutableListOf<ScannedNode>()
                    extractTextNodes(rootNode, scannedNodes)
                    
                    // 1. Check if screen has a 4-digit release year and capture it
                    val yearPattern = Regex("""\b(19\d\d|20[0-2]\d)\b""")
                    val foundYearNode = scannedNodes.find { node ->
                        val text = node.text.trim()
                        yearPattern.containsMatchIn(text) && text.length <= 15 && 
                        !text.contains("/") && !text.contains(":") // Avoid date-times/timestamps
                    }
                    val detectedYearOnScreen = foundYearNode?.let { node ->
                        yearPattern.find(node.text)?.value
                    }

                    // 2. Is there a synopsis/description?
                    val hasSynopsis = scannedNodes.any { node -> 
                        val text = node.text
                        val id = node.id ?: ""
                        text.length in 40..600 && 
                        !text.contains("http") &&
                        !text.contains("www.") &&
                        (id.contains("synopsis") || id.contains("desc") || id.contains("summary") || id.contains("plot") || id.contains("description") || id.contains("metadata") ||
                        (text.contains(".") && text.contains(" ")))
                    }
                    
                    // 3. Are there active playback controls or player traits?
                    val hasPlaybackControls = scannedNodes.any { node ->
                        val textLower = node.text.lowercase()
                        val id = node.id ?: ""
                        textLower.contains("skip intro") || 
                        textLower.contains("skip recap") || 
                        textLower.contains("next episode") || 
                        textLower.contains("audio & subtitles") ||
                        textLower.contains("subtitles") ||
                        textLower.contains("audio and subtitles") ||
                        textLower.contains("languages") ||
                        (id.contains("play") && id.contains("control")) ||
                        (id.contains("pause") && id.contains("control")) ||
                        id.contains("player_view") ||
                        id.contains("playback") ||
                        id.contains("rewind") ||
                        id.contains("forward")
                    }
                    
                    // 4. Are there detail-page metadata features?
                    val hasDetailElements = scannedNodes.any { node ->
                        val textLower = node.text.lowercase()
                        val id = node.id ?: ""
                        textLower == "episodes" || 
                        textLower == "seasons" ||
                        textLower == "more like this" || 
                        textLower.contains("cast & crew") || 
                        textLower.contains("cast and crew") || 
                        textLower.contains("about this movie") ||
                        textLower.contains("trailers & more") ||
                        textLower.contains("similar titles") ||
                        id.contains("watchlist") ||
                        id.contains("wishlist") ||
                        id.contains("add_to_list") ||
                        id.contains("add_to_watchlist") ||
                        id.contains("favorite")
                    }

                    // 5. Check for Play or Watch hooks
                    val hasPlayButton = scannedNodes.any { node ->
                        val textLower = node.text.lowercase()
                        textLower == "play" || textLower == "watch" || textLower == "resume" ||
                        textLower.contains("play movie") || textLower.contains("watch now") ||
                        textLower.contains("start movie") || textLower.contains("play episode")
                    }

                    // 6. Check for maturity ratings or movie durations (very common on detail pages)
                    val hasMaturityOrDuration = scannedNodes.any { node ->
                        val text = node.text.trim()
                        text.contains("Season") || text.matches(Regex("""\d+\s*(Season|Episode|seasons|episodes|Episodes|Seasons)""")) ||
                        text.matches(Regex("""^(U/A\s*)?\d+\+?$""")) || text.contains("U/A") || text.lowercase().contains("rated") ||
                        text.matches(Regex("""^\d+\s*h\s*\d+\s*m$""")) || text.matches(Regex("""^\d+\s*m\s*\d+\s*s$"""))
                    }

                    // Package-aware custom ID priority list for exact matches
                    val exactTitleIDs = setOf(
                        "com.amazon.avod.thirdparty:id/title",
                        "com.amazon.avod:id/title",
                        "com.amazon.amazonvideo:id/title",
                        "com.amazon.avod:id/title_text",
                        "com.amazon.amazonvideo:id/title_text",
                        "com.netflix.mediaclient:id/video_title",
                        "com.netflix.mediaclient:id/title",
                        "in.startv.hotstar:id/title",
                        "in.startv.hotstar:id/movie_title",
                        "in.startv.hotstar:id/show_title",
                        "com.jio.media.ondemand:id/action_bar_title",
                        "com.jio.media.ondemand:id/title",
                        "com.jio.media.ondemand:id/metadata_title",
                        "com.sonyliv:id/title",
                        "com.sonyliv:id/txt_title",
                        "com.sonyliv:id/title_text",
                        "com.zee5:id/title",
                        "com.zee5:id/title_txt",
                        "com.zee5:id/txt_title"
                    )

                    val candidatesWithScores = scannedNodes.map { node ->
                        val text = node.text.trim()
                        val textLower = text.lowercase()
                        val id = (node.id ?: "").lowercase()
                        val className = (node.className ?: "").lowercase()

                        var score = 0

                        // Length weight
                        if (text.length in 2..45) {
                            score += 15
                        } else {
                            score -= 50
                        }

                        // Format weight
                        if (text.isNotEmpty() && text.first().isLetter() && text.first().isUpperCase()) {
                            score += 25
                        }

                        // Exact matching ID
                        val hasExactId = exactTitleIDs.any { id == it || id.endsWith(it) }
                        if (hasExactId) {
                            score += 160
                        }

                        // general title IDs
                        if (id.contains("title") || id.contains("heading") || id.contains("header") || id.contains("movie_name") || id.contains("show_name") || id.contains("view_title") || id.contains("text_title") || id.contains("txt_title")) {
                            if (!id.contains("tab") && !id.contains("menu") && !id.contains("carousel") && !id.contains("nav") && !id.contains("list") && !id.contains("search") && !id.contains("btn") && !id.contains("button") && !id.contains("logo") && !id.contains("icon")) {
                                score += 90
                            }
                        }

                        // Blacklisted words penalty
                        val blacklistedWords = setOf(
                            "play", "pause", "resume", "episodes", "search", "home", "my list", "like", "share", 
                            "audio", "subtitles", "skip", "next", "more", "downloads", "download", "settings", 
                            "info", "help", "profile", "account", "categories", "tv shows", "movies", "live tv",
                            "sports", "my space", "watchlist", "more like this", "episodes & info", "about", 
                            "trailer", "trailers", "cast", "crew", "season", "episode", "details", "watch",
                            "skip intro", "skip recap", "next episode", "audio & subtitles", "audio and subtitles",
                            "add to list", "add to watchlist", "remove from list", "rate", "rated", "reviews",
                            "netflix", "prime video", "disney", "hotstar", "jiocinema", "sonyliv", "zee5",
                            "watch now", "play movie", "watch free", "subscribe", "buy", "rent", "share link",
                            "more", "similar", "you may also like", "creators", "director", "starring",
                            "languages", "audio details", "subtitles details", "rating", "age rating", "maturity rating",
                            "clip", "clips", "teaser", "teasers", "video", "videos", "promo", "promos", "preview", "previews", 
                            "snippet", "snippets", "extra", "extras", "behind the scenes", "sneak peek", "sneak peeks"
                        )

                        if (blacklistedWords.contains(textLower)) {
                            score -= 300
                        }

                        if (blacklistedWords.any { textLower.contains(it) && textLower.length < 15 }) {
                            score -= 150
                        }

                        // Video players metadata or promo tags (e.g. CLIP, TEASER, SNEAK PEEK)
                        val isPromoMarker = textLower.contains("clip") || 
                                            textLower.contains("teaser") || 
                                            textLower.contains("promo") || 
                                            textLower.contains("sneak peek") || 
                                            textLower.contains("extra") || 
                                            textLower.contains("behind the scenes") ||
                                            textLower.contains("preview")
                        if (isPromoMarker) {
                            score -= 250
                        }

                        // UI components penalty
                        if (id.contains("button") || id.contains("btn") || id.contains("click") || id.contains("close") || id.contains("nav") || id.contains("tab") || id.contains("menu")) {
                            score -= 80
                        }

                        // Formatting tags penalty
                        if (text.matches(Regex("""^\d+$""")) || text.contains("/") || text.contains(":") || text.contains("AM") || text.contains("PM")) {
                            score -= 100
                        }

                        if (text.matches(Regex("""^\d+\s*h\s*\d+\s*m$""")) || text.matches(Regex("""^\d+\s*m$""")) || text.contains("season") || text.contains("episode") || text.lowercase().contains("rated")) {
                            score -= 150
                        }

                        if (text.contains("•") || text.contains("|") || text.contains("·") || text.contains(",")) {
                            score -= 120
                        }

                        if (className.contains("textview")) {
                            score += 15
                        }

                        ScannedNodeScore(node = node, finalScore = score)
                    }

                    val validCandidates = candidatesWithScores
                        .filter { it.finalScore >= 50 }
                        .sortedByDescending { it.finalScore }

                    val topCandidateNode = validCandidates.firstOrNull()
                    val bestTitle = topCandidateNode?.node?.text?.trim()
                    val topCandidateScore = topCandidateNode?.finalScore ?: 0

                    val hasHomeOrBrowseTabs = scannedNodes.any { node ->
                        val t = node.text.lowercase()
                        t == "home" || t == "tv shows" || t == "movies" || t == "categories" || t == "my list" || t == "sports" || t == "live tv" || t == "store"
                    } && scannedNodes.any { node ->
                        val t = node.text.lowercase()
                        t == "search" || t == "find" || t.contains("profile") || t.contains("notification")
                    }

                    var isDetailOrPlayback = topCandidateScore >= 60 || hasSynopsis || hasPlaybackControls || hasDetailElements || hasPlayButton || hasMaturityOrDuration || (detectedYearOnScreen != null)
                    
                    if (isDetailOrPlayback && !hasHomeOrBrowseTabs) {
                        lastDetailTime = System.currentTimeMillis()
                    } else {
                        val timeSinceLastDetail = System.currentTimeMillis() - lastDetailTime
                        val withinGracePeriod = lastDetailTime > 0 && timeSinceLastDetail < 3000
                        if (withinGracePeriod && !hasHomeOrBrowseTabs) {
                            isDetailOrPlayback = true // Keep active during scroll fluctuations
                        } else {
                            lastDetailTime = 0L // Clear
                        }
                    }
                    
                    var finalTitle: String? = null
                    if (bestTitle != null) {
                        finalTitle = if (detectedYearOnScreen != null && !bestTitle.contains(detectedYearOnScreen)) {
                            "$bestTitle ($detectedYearOnScreen)"
                        } else {
                            bestTitle
                        }
                        lastDetectedTitle = finalTitle
                    } else {
                        if (isDetailOrPlayback) {
                            finalTitle = lastDetectedTitle
                        }
                    }

                    if (isDetailOrPlayback && finalTitle != null) {
                        OverlayState.setDetectedTitles(listOf(finalTitle))
                        Log.d(TAG, "Active App: $appName | Selected Title: $finalTitle | top score: $topCandidateScore")
                        
                        if (!OverlayState.isOverlayVisible.value) {
                            startOverlayService()
                        }
                    } else {
                        lastDetectedTitle = null
                        lastDetailTime = 0L
                        hideOverlayService()
                    }
                }
            } else {
                lastDetectedTitle = null
                lastDetailTime = 0L
                OverlayState.setStreamingApp(null)
                hideOverlayService()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onAccessibilityEvent: ", e)
        }
    }

    private fun extractTextNodes(rootNode: AccessibilityNodeInfo?, list: MutableList<ScannedNode>) {
        if (rootNode == null) return
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(rootNode)
        
        var count = 0
        val maxNodes = 500 // Balance limit
        
        while (queue.isNotEmpty() && count < maxNodes) {
            val node = queue.removeFirst() ?: continue
            count++
            
            try {
                val text = node.text?.toString()?.trim()
                val id = node.viewIdResourceName?.lowercase()
                val className = node.className?.toString()?.lowercase()
                
                if (!text.isNullOrEmpty()) {
                    list.add(ScannedNode(text, id, className))
                }
                
                val childCount = node.childCount
                for (i in 0 until childCount) {
                    val child = node.getChild(i)
                    if (child != null) {
                        queue.add(child)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing node: ${e.message}")
            } finally {
                try {
                    node.recycle()
                } catch (er: Exception) {
                    // Ignore exceptions during recycling
                }
            }
        }
        
        // Clean up any remaining nodes still in queue if we broke early
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            try {
                node?.recycle()
            } catch (er: Exception) {
                // Ignore
            }
        }
    }

    private fun startOverlayService() {
        try {
            val intent = Intent(this, FloatingOverlayService::class.java).apply {
                action = FloatingOverlayService.ACTION_SHOW
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to auto-start overlay service", e)
        }
    }

    private fun hideOverlayService() {
        try {
            val intent = Intent(this, FloatingOverlayService::class.java).apply {
                action = FloatingOverlayService.ACTION_HIDE
            }
            startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to auto-hide overlay service", e)
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected")
    }
}
