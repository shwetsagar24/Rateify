package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class MovieOverlayAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MovieRatingsAccessibility"
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            val packageName = event.packageName?.toString() ?: return
            
            val appName = when {
                packageName.contains("netflix") -> "Netflix"
                packageName.contains("amazon.avod") || packageName.contains("amazon.amazonvideo") -> "Prime Video"
                packageName.contains("hotstar") || packageName.contains("jio.media.ondemand") -> "Hotstar"
                else -> null
            }

            if (appName != null) {
                OverlayState.setStreamingApp(appName)
                
                // Traverse active window nodes to fetch raw text strings on the screen
                val rootNode = rootInActiveWindow
                if (rootNode != null) {
                    val detectedTexts = mutableListOf<String>()
                    extractTextNodes(rootNode, detectedTexts)
                    
                    // Keep only valid textual elements (caps-start, typical lengths for movie titles)
                    val filteredTitles = detectedTexts.filter { text ->
                        text.length in 3..50 && 
                        !text.any { it.isDigit() && text.length < 5 } && // Avoid timestamps/runtimes
                        !listOf("play", "pause", "resume", "episodes", "search", "home", "my list", "like", "share", "audio", "subtitles", "skip", "next", "more").contains(text.lowercase())
                    }
                    
                    OverlayState.setDetectedTitles(filteredTitles)
                    Log.d(TAG, "Active App: $appName | Detected Texts on Screen: $filteredTitles")
                }

                // Optional Auto-show: If Overlay isn't already active, trigger it when entering these apps
                if (!OverlayState.isOverlayVisible.value) {
                    startOverlayService()
                }
            } else {
                // If the user left the app, you can optionally clear status or auto-close
                // We keep it alive for standard overlays unless they exit to launcher, etc.
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onAccessibilityEvent: ", e)
        }
    }

    private fun extractTextNodes(rootNode: AccessibilityNodeInfo?, list: MutableList<String>) {
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
                if (!text.isNullOrEmpty()) {
                    list.add(text)
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
                } catch (e: Exception) {
                    // Ignore recycle exceptions
                }
            }
        }
        
        // Clean up remaining nodes in queue
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            try {
                node?.recycle()
            } catch (e: Exception) {
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

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected")
    }
}
