package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.data.MovieDatabase
import com.example.data.MovieRatingEntity
import com.example.network.GeminiClient
import com.example.network.MovieRatingResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class FloatingOverlayService : Service() {

    companion object {
        const val ACTION_SHOW = "ACTION_SHOW"
        const val ACTION_HIDE = "ACTION_HIDE"
        private const val NOTIFICATION_ID = 8801
        private const val CHANNEL_ID = "movie_ratings_overlay_channel"
    }

    private var windowManager: WindowManager? = null
    private var overlayView: FrameLayout? = null
    private var customLifecycleOwner: CustomFloatingLifecycleOwner? = null
    private var isExpanded = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, getServiceNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> {
                showOverlay()
            }
            ACTION_HIDE -> {
                hideOverlay()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun showOverlay() {
        if (overlayView != null) return // Already showing

        OverlayState.setOverlayVisible(true)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 400
        }

        // Define a custom LifecycleOwner + Service support for Composable inputs
        val lifecycleOwner = CustomFloatingLifecycleOwner()
        lifecycleOwner.start()
        customLifecycleOwner = lifecycleOwner

        overlayView = FrameLayout(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                MaterialTheme {
                    FloatingWidgetUI(
                        onToggleExpand = { expanded ->
                            isExpanded = expanded
                            updateWindowManagerParams(expanded, params)
                        },
                        onCloseOverlay = {
                            hideOverlay()
                            stopSelf()
                        },
                        onDrag = { dx, dy ->
                            if (!isExpanded) {
                                params.x = (params.x + dx).toInt()
                                params.y = (params.y + dy).toInt()
                                OverlayState.updateDragState(true, params.y)
                                overlayView?.let { windowManager?.updateViewLayout(it, params) }
                            }
                        },
                        onDragStart = {
                            OverlayState.updateDragState(true, params.y)
                        },
                        onDragEnd = {
                            val screenHeight = resources.displayMetrics.heightPixels
                            val isDismiss = OverlayState.dragY.value > (screenHeight * 0.72f)
                            OverlayState.updateDragState(false, 0)
                            if (isDismiss) {
                                hideOverlay()
                                stopSelf()
                            }
                        }
                    )
                }
            }
        }

        overlayView?.addView(composeView)
        windowManager?.addView(overlayView, params)
    }

    private fun updateWindowManagerParams(expanded: Boolean, params: WindowManager.LayoutParams) {
        if (expanded) {
            params.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.gravity = Gravity.CENTER
            params.x = 0
            params.y = 0
        } else {
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.gravity = Gravity.TOP or Gravity.START
            params.x = 20
            params.y = 400
        }
        overlayView?.let { windowManager?.updateViewLayout(it, params) }
    }

    private fun hideOverlay() {
        OverlayState.setOverlayVisible(false)
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                // Ignore removal bugs
            }
            overlayView = null
        }
        customLifecycleOwner?.stop()
        customLifecycleOwner = null
    }

    override fun onDestroy() {
        hideOverlay()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Movie Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows movie ratings floating panel on top of streaming apps."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun getServiceNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Rateify Floating Overlay")
            .setContentText("Active and overlaying on Netflix, Prime Video, Jio Hotstar, SonyLIV, ZEE5")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    // --- Custom Lifecycle Owner for enabling Compose inside WindowManager ---
    private class CustomFloatingLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)
        private val _viewModelStore = ViewModelStore()

        init {
            savedStateRegistryController.performRestore(Bundle())
        }

        fun start() {
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        }

        fun stop() {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
            _viewModelStore.clear()
        }

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val viewModelStore: ViewModelStore get() = _viewModelStore
        override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    }
}

// --- COMPOSE FLOATING WIDGET CONTENT ---

@Composable
fun FloatingWidgetUI(
    onToggleExpand: (Boolean) -> Unit,
    onCloseOverlay: () -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }

    val activeApp by OverlayState.currentStreamingApp.collectAsState()
    val detectedTitles by OverlayState.detectedTitles.collectAsState()
    val selectedTitle by OverlayState.selectedTitle.collectAsState()
    val ratingResult by OverlayState.ratingResult.collectAsState()
    val isLoading by OverlayState.isOverlayLoading.collectAsState()

    val isDragging by OverlayState.isDragging.collectAsState()
    val dragY by OverlayState.dragY.collectAsState()
    val screenHeight = androidx.compose.ui.platform.LocalContext.current.resources.displayMetrics.heightPixels
    val isInDismissZone = isDragging && dragY > (screenHeight * 0.72f)

    val composeScope = rememberCoroutineScope()

    LaunchedEffect(detectedTitles) {
        if (detectedTitles.isNotEmpty()) {
            val firstTitle = detectedTitles.first()
            val currentResult = OverlayState.ratingResult.value
            if (currentResult == null || currentResult.title.lowercase() != firstTitle.lowercase()) {
                OverlayState.setOverlayLoading(true)
                try {
                    val res = GeminiClient.fetchMovieReviews(firstTitle)
                    OverlayState.setRatingResult(res)
                } catch (e: Exception) {
                    // Ignore
                } finally {
                    OverlayState.setOverlayLoading(false)
                }
            }
        }
    }

    @Composable
    fun PlatformIndicator(appName: String) {
        val color = when (appName) {
            "Netflix" -> Color(0xFFE50914)
            "Prime Video" -> Color(0xFF00A8E1)
            "Jio Hotstar", "Hotstar" -> Color(0xFFFFC629)
            "SonyLIV" -> Color(0xFFE25C3E)
            "ZEE5" -> Color(0xFF8E24AA)
            else -> Color(0xFF888888)
        }
        val textCol = if (appName == "Jio Hotstar" || appName == "Hotstar") Color.Black else Color.White
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(color)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(appName, fontSize = 10.sp, color = textCol)
        }
    }

    if (!expanded) {
        // COLLAPSED: Floating Draggable Pill
        Box(
            modifier = Modifier
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { onDragStart() },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    )
                }
                .clip(RoundedCornerShape(24.dp))
                .background(if (isInDismissZone) Color(0xFFEF4444) else Color(0xE01C1B1F))
                .clickable {
                    expanded = true
                    onToggleExpand(true)
                }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (isInDismissZone) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Release to dismiss",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "RELEASE TO DISMISS",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Rating badge",
                        tint = Color(0xFFFFC629),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        if (ratingResult != null) {
                            val movie = ratingResult!!
                            Text(movie.title, color = Color.White, fontSize = 11.sp, maxLines = 1)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("IMDb: ${movie.imdb}", color = Color(0xFFFFC629), fontSize = 9.sp)
                                if (movie.rottenTomatoes != "N/A" && movie.rottenTomatoes.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("RT: ${movie.rottenTomatoes}", color = Color(0xFFFF5252), fontSize = 9.sp)
                                }
                            }
                        } else if (isLoading) {
                            Text("Fetching rating...", color = Color.LightGray, fontSize = 11.sp)
                        } else {
                            Text("Rating Widget", color = Color.White, fontSize = 11.sp)
                            activeApp?.let {
                                Text(it, color = Color.LightGray, fontSize = 8.sp)
                            }
                        }
                    }
                }
            }
        }
    } else {
        // EXPANDED: Full Review Overlay panel
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(480.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF161517),
                    contentColor = Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Title Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = "Movie ratings",
                                tint = Color(0xFFFFC629)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Rateify AI",
                                fontSize = 18.sp,
                                color = Color.White
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            activeApp?.let { PlatformIndicator(it) }
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = {
                                    expanded = false
                                    onToggleExpand(false)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Collapse panel",
                                    tint = Color.Gray
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Main display column
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Title selector if titles detected on screen
                        if (detectedTitles.isNotEmpty() && ratingResult == null && !isLoading) {
                            Text(
                                "Titles Detected on Screen:",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                detectedTitles.forEach { title ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color(0xFF2B292D))
                                            .clickable {
                                                OverlayState.selectTitle(title)
                                                OverlayState.setOverlayLoading(true)
                                                composeScope.launch {
                                                    val res = GeminiClient.fetchMovieReviews(title)
                                                    OverlayState.setRatingResult(res)
                                                    OverlayState.setOverlayLoading(false)
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(title, fontSize = 10.sp, color = Color.White)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // Search entry point inside overlay
                        var searchQuery by remember { mutableStateOf("") }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search title manually...", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFFFC629),
                                    unfocusedBorderColor = Color.DarkGray,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = {
                                    if (searchQuery.isNotEmpty()) {
                                        OverlayState.selectTitle(searchQuery)
                                        OverlayState.setOverlayLoading(true)
                                        composeScope.launch {
                                            val res = GeminiClient.fetchMovieReviews(searchQuery)
                                            OverlayState.setRatingResult(res)
                                            OverlayState.setOverlayLoading(false)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFFFC629))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = Color.Black
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (isLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color(0xFFFFC629))
                            }
                        } else if (ratingResult != null) {
                            val movie = ratingResult!!
                            // MOVIE RESULT CARDS
                            Text(
                                text = movie.title,
                                fontSize = 21.sp,
                                color = Color.White
                            )
                            Text(
                                text = "${movie.year} • Led by ${movie.director} • ${movie.genre}",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // RATINGS ROW
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("IMDb", fontSize = 11.sp, color = Color.Gray, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))
                                    Text(movie.imdb, fontSize = 16.sp, color = Color(0xFFFFC629), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Rotten Tomatoes", fontSize = 11.sp, color = Color.Gray, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))
                                    Text(movie.rottenTomatoes, fontSize = 16.sp, color = Color(0xFFE50914), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Synopsis
                            Text("Synopsis", fontSize = 12.sp, color = Color.Gray)
                            Text(movie.synopsis, fontSize = 12.sp, color = Color.LightGray)

                            Spacer(modifier = Modifier.height(14.dp))

                            // IMDb Parents Guide in IMDb Style
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF221F24))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    "IMDb Parents Guide",
                                    fontSize = 12.sp,
                                    color = Color(0xFFFFC629),
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                val categories = listOf(
                                    Triple("Sex & Nudity", movie.parentsGuideSex ?: "None", Color(0xFFE57373)),
                                    Triple("Violence & Gore", movie.parentsGuideViolence ?: "None", Color(0xFFFF5252)),
                                    Triple("Profanity", movie.parentsGuideProfanity ?: "None", Color(0xFFFFB74D)),
                                    Triple("Alcohol, Drugs & Smoking", movie.parentsGuideDrugs ?: "None", Color(0xFF64B5F6)),
                                    Triple("Frightening & Intense", movie.parentsGuideIntense ?: "None", Color(0xFFBA68C8))
                                )

                                categories.forEach { (categoryName, value, tintColor) ->
                                    val level = when {
                                        value.lowercase().startsWith("severe") -> "Severe"
                                        value.lowercase().startsWith("moderate") -> "Moderate"
                                        value.lowercase().startsWith("mild") -> "Mild"
                                        else -> "None"
                                    }

                                    val badgeColor = when (level) {
                                        "Severe" -> Color(0xFFEF4444)       // M3 Red
                                        "Moderate" -> Color(0xFFF97316)     // M3 Orange
                                        "Mild" -> Color(0xFF10B981)         // M3 Green
                                        else -> Color(0xFF6B7280)           // M3 Grey
                                    }

                                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = categoryName,
                                                fontSize = 11.sp,
                                                color = Color.White,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                            )

                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(badgeColor)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = level.uppercase(),
                                                    fontSize = 8.sp,
                                                    color = Color.White,
                                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                                )
                                            }
                                        }

                                        val desc = if (value.contains(" - ")) {
                                            value.substringAfter(" - ").trim()
                                        } else if (value.contains(": ")) {
                                            value.substringAfter(": ").trim()
                                        } else {
                                            value
                                        }

                                        if (desc.isNotBlank() && desc != "None" && !desc.lowercase().startsWith("none")) {
                                            Text(
                                                text = desc,
                                                fontSize = 10.sp,
                                                color = Color(0xFF9CA3AF),
                                                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Critics Synthesis
                            Text("Positive Reviews", fontSize = 12.sp, color = Color(0xFF4CAF50))
                            Text(movie.positiveSummary, fontSize = 11.sp, color = Color.LightGray)

                            Spacer(modifier = Modifier.height(8.dp))

                            Text("Critical Consensus", fontSize = 12.sp, color = Color(0xFFE50914))
                            Text(movie.negativeSummary, fontSize = 11.sp, color = Color.LightGray)

                            Spacer(modifier = Modifier.height(16.dp))
                        } else {
                            // Empty State
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Movie,
                                        contentDescription = "Search placeholder",
                                        tint = Color.DarkGray,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "Select a detected title or type a query above to load live review card",
                                        fontSize = 11.sp,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(horizontal = 24.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Reset Button
                    if (ratingResult != null) {
                        Button(
                            onClick = { OverlayState.setRatingResult(null) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2B292D),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Search another film", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
