package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.MovieRatingEntity
import com.example.network.GeminiClient
import com.example.network.TmdbClient
import com.example.network.TmdbTarget
import com.example.network.ParentsGuideState
import com.example.service.FloatingOverlayService
import com.example.service.OverlayState
import com.example.ui.MovieViewModel
import com.example.ui.SearchUiState
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Load custom saved API keys on startup
        val sharedPrefs = getSharedPreferences("RateifyPrefs", MODE_PRIVATE)
        com.example.network.GeminiClient.customApiKey = sharedPrefs.getString("gemini_api_key", null)
        com.example.network.TmdbClient.customApiKey = sharedPrefs.getString("tmdb_api_key", null)

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color(0xFF03030C)
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        MovieRatingsScreen()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Force refresh configuration checks
        _checkTriggers.value = !_checkTriggers.value
    }

    companion object {
        val _checkTriggers = mutableStateOf(false)
    }
}

@Composable
fun MovieRatingsScreen(
    viewModel: MovieViewModel = viewModel()
) {
    val context = LocalContext.current
    val checkTrigger by MainActivity._checkTriggers

    // Verify Active System Permissions
    var isOverlayGranted by remember(checkTrigger) {
        mutableStateOf(Settings.canDrawOverlays(context))
    }
    var isAccessibilityGranted by remember(checkTrigger) {
        mutableStateOf(isAccessibilityServiceEnabled(context))
    }

    // ViewModel State Bindings
    val searchUiState by viewModel.searchUiState.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val activeStreamingApp by OverlayState.currentStreamingApp.collectAsState()

    // Homepage Categorical Sources
    val trendingAll by viewModel.trendingAll.collectAsState()
    val trendingMovies by viewModel.trendingMovies.collectAsState()
    val trendingTv by viewModel.trendingTv.collectAsState()
    val topRatedMovies by viewModel.topRatedMovies.collectAsState()
    val topRatedTv by viewModel.topRatedTv.collectAsState()
    val trendingDocumentaries by viewModel.trendingDocumentaries.collectAsState()
    val mediaRatings by viewModel.mediaRatings.collectAsState()

    // Bottom Navigation State: 0: Home, 1: Search, 2: Settings
    var activeTab by remember { mutableStateOf(0) }
    
    // Bottom Sheet Detail Overlay Item
    var selectedDetailMovie by remember { mutableStateOf<MovieRatingEntity?>(null) }
    var showWatchlistSheet by remember { mutableStateOf(false) }

    // Home Filters State
    var selectedPlatform by remember { mutableStateOf("All") }
    var selectedType by remember { mutableStateOf("All") }

    // Load defaults if applicable
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("RateifyPrefs", Context.MODE_PRIVATE)
        selectedPlatform = prefs.getString("default_platform_preference", "All") ?: "All"
        viewModel.loadContentForPlatform(selectedPlatform)
    }

    // Global listener: Successful Search automatically triggers Detail Overlay Bottom Sheet
    LaunchedEffect(searchUiState) {
        if (searchUiState is SearchUiState.Success) {
            selectedDetailMovie = (searchUiState as SearchUiState.Success).movie
            viewModel.resetSearchState()
        }
    }

    // Base Frame Container
    Scaffold(
        bottomBar = {
            BottomNavBar(
                activeTab = activeTab,
                onTabSelect = { activeTab = it }
            )
        },
        containerColor = Color(0xFF0A0A0F)
    ) { paddingValues ->
        val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1.0f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = androidx.compose.animation.core.tween(1000, easing = androidx.compose.animation.core.LinearEasing),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "pulse"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // APP MODULE HEADER ROW - REDESIGNED
                val isHUDServiceActive by OverlayState.isOverlayVisible.collectAsState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(Color(0xFF0A0A0F))
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_rateify_logo),
                            contentDescription = "Rateify AI Logo",
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Rateify AI",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                            )
                            Text(
                                text = "Know before you watch.",
                                fontSize = 12.sp,
                                color = Color(0xFF606075),
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Watchlist Icon with badge
                        val watchlistCount by viewModel.watchlistCount.collectAsState()

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF1A1A24))
                                .clickable { showWatchlistSheet = true }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bookmark,
                                    contentDescription = "Watchlist",
                                    tint = Color(0xFFE50914),
                                    modifier = Modifier.size(14.dp)
                                )
                                if (watchlistCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(Color(0xFFE50914))
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = watchlistCount.toString(),
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        if (showWatchlistSheet) {
                            WatchlistSheet(
                                viewModel = viewModel,
                                onDismiss = { showWatchlistSheet = false },
                                onMovieClick = { movie ->
                                    selectedDetailMovie = movie
                                    showWatchlistSheet = false
                                }
                            )
                        }

                        // Live Scan System Indicator Pill
                        val dotColor = if (isHUDServiceActive) Color(0xFF2ECC71) else Color(0xFF606075)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF1A1A24))
                                .clickable {
                                    if (isOverlayGranted) {
                                        val action = if (isHUDServiceActive) FloatingOverlayService.ACTION_HIDE else FloatingOverlayService.ACTION_SHOW
                                        triggerOverlay(context, action)
                                        Toast.makeText(context, if (isHUDServiceActive) "Overlay Closed" else "Overlay Launched", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "System overlay permissions required", Toast.LENGTH_LONG).show()
                                        activeTab = 2 // Move to settings to setup
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isHUDServiceActive) dotColor.copy(alpha = alpha) else dotColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isHUDServiceActive) "HUD LIVE" else "HUD PAUSED",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = dotColor,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                        )
                    }
                }

                // SCREEN SHIFT CONTROLLER
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (activeTab) {
                        0 -> HomeScreen(
                            trendingAll = trendingAll,
                            trendingMovies = trendingMovies,
                            trendingTv = trendingTv,
                            topRatedMovies = topRatedMovies,
                            topRatedTv = topRatedTv,
                            trendingDocumentaries = trendingDocumentaries,
                            mediaRatings = mediaRatings,
                            selectedPlatform = selectedPlatform,
                            selectedType = selectedType,
                            onPlatformChange = { platform ->
                                selectedPlatform = platform
                                viewModel.loadContentForPlatform(platform)
                            },
                            onTypeChange = { selectedType = it },
                            viewModel = viewModel,
                            onMovieClick = { selectedDetailMovie = it }
                        )

                        1 -> SearchScreen(
                            searchUiState = searchUiState,
                            searchHistory = searchHistory,
                            viewModel = viewModel
                        )

                        2 -> SettingsScreen(
                            isOverlayGranted = isOverlayGranted,
                            isAccessibilityGranted = isAccessibilityGranted,
                            viewModel = viewModel,
                            searchUiState = searchUiState,
                            searchHistory = searchHistory
                        )
                    }
                }
            }

            // MODAL DETAILED RATING SHEET OVERLAY
            selectedDetailMovie?.let { movie ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.65f))
                        .clickable { selectedDetailMovie = null }
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .fillMaxHeight(0.92f)
                            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                            .background(Color(0xFF07070F))
                            .border(1.dp, Color(0xFF1B1B26), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                            .clickable(enabled = false) {}
                    ) {
                        Column {
                            // Top Drag Indicator Handle Bar
                            Box(
                                modifier = Modifier
                                    .padding(vertical = 12.dp)
                                    .size(width = 44.dp, height = 4.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2C2C3C))
                                    .align(Alignment.CenterHorizontally)
                            )
                            
                            Box(modifier = Modifier.weight(1f)) {
                                MovieReviewDetailCard(
                                    movie = movie,
                                    onClose = { selectedDetailMovie = null },
                                    viewModel = viewModel
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShimmerPlaceholder(
    modifier: Modifier
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1500f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "translate"
    )

    val shimmerColors = listOf(
        Color(0xFF1E1E2E),
        Color(0xFF2C2C3E),
        Color(0xFF1E1E2E)
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnim.value, y = translateAnim.value)
    )

    Box(
        modifier = modifier
            .background(brush)
    )
}

@Composable
fun ErrorStateUI(
    errorMessage: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("⚠️", fontSize = 48.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Something went wrong",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = errorMessage.ifBlank { "Check your internet connection" },
            fontSize = 13.sp,
            color = Color(0xFF8A8AB0),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B2FC9))
        ) {
            Text("Retry", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
fun EmptyStateUI(
    platform: String,
    onShowAll: () -> Unit
) {
    val specColor = platformColors[platform] ?: ChipColor(Color(0xFF1e1e2e), Color.White)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(specColor.bg.copy(alpha = 0.2f))
                .border(2.dp, specColor.bg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = platform.take(2).uppercase(),
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = specColor.text
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No content found for $platform",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "This platform may have limited titles in our database",
            fontSize = 13.sp,
            color = Color(0xFF8A8AB0),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onShowAll,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B2FC9))
        ) {
            Text("Show All Content", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

// ---------------------- HOME SCREEN MODULE ----------------------
@Composable
fun HomeScreen(
    trendingAll: List<TmdbTarget>,
    trendingMovies: List<TmdbTarget>,
    trendingTv: List<TmdbTarget>,
    topRatedMovies: List<TmdbTarget>,
    topRatedTv: List<TmdbTarget>,
    trendingDocumentaries: List<TmdbTarget>,
    mediaRatings: Map<Int, MovieRatingEntity>,
    selectedPlatform: String,
    selectedType: String,
    onPlatformChange: (String) -> Unit,
    onTypeChange: (String) -> Unit,
    viewModel: MovieViewModel,
    onMovieClick: (MovieRatingEntity) -> Unit
) {
    // Brand Chip Color Definitions (India Popular Networks First)
    val platformChipsList = listOf(
        "All", "Netflix", "Prime Video", "Disney+ Hotstar", "JioHotstar", "SonyLIV", 
        "Zee5", "Apple TV+", "Max", "Hulu", "YouTube Premium", "Peacock", "Paramount+",
        "Discovery+", "MUBI", "Aha", "Hoichoi", "Lionsgate Play", "MX Player", "Voot", 
        "Sun NXT"
    )

    val contentTypesList = listOf("Movies", "TV Series", "Documentaries")

    val selectedMovieGenres by viewModel.selectedMovieGenres.collectAsState()
    val selectedTvGenres by viewModel.selectedTvGenres.collectAsState()
    val selectedDocGenres by viewModel.selectedDocGenres.collectAsState()

    val risingNow by viewModel.risingNow.collectAsState()
    val criticallyAcclaimed by viewModel.criticallyAcclaimed.collectAsState()
    val hiddenGems by viewModel.hiddenGems.collectAsState()

    var expandedChip by remember { mutableStateOf<String?>(null) }

    val movieGenresMap = mapOf(
        "Action" to 28,
        "Comedy" to 35,
        "Drama" to 18,
        "Sci-Fi" to 878,
        "Horror" to 27,
        "Thriller" to 53
    )

    val tvGenresMap = mapOf(
        "Action & Adventure" to 10759,
        "Comedy" to 35,
        "Drama" to 18,
        "Mystery" to 9648,
        "Sci-Fi & Fantasy" to 10765
    )

    val docGenresMap = mapOf(
        "History" to 36,
        "Science" to 878, // Sci-Fi
        "Nature" to 12,    // Adventure/Nature
        "Crime" to 80
    )

    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    // The lists are already filtered, merged, and sorted by TMDb discovery in the ViewModel!
    val filteredAll = trendingAll
    val filteredMovies = trendingMovies
    val filteredTv = trendingTv
    val filteredTopMovies = topRatedMovies
    val filteredTopTv = topRatedTv
    val filteredDocs = trendingDocumentaries

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0F)),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        // 1. Sleek Filter Bar Row Context - REDESIGNED
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                // Section label above platform chips
                Text(
                    text = "STREAMING ON",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF606075),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                    letterSpacing = 1.0.sp,
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )

                // Platform Badges Scroll List with right edge fade overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 40.dp),
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(platformChipsList) { platform ->
                            val isSelected = selectedPlatform == platform
                            val specColor = platformColors[platform] ?: ChipColor(Color(0xFFE50914), Color.White)

                            val scale by androidx.compose.animation.core.animateFloatAsState(
                                targetValue = if (isSelected) 1.05f else 1.0f,
                                animationSpec = androidx.compose.animation.core.tween(durationMillis = 150),
                                label = "platformScale"
                            )
                            val chipBgColor by animateColorAsState(
                                targetValue = if (isSelected) specColor.bg else Color(0xFF1A1A24),
                                animationSpec = androidx.compose.animation.core.tween(durationMillis = 200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                label = "platformBg"
                            )
                            val chipTextColor by animateColorAsState(
                                targetValue = if (isSelected) specColor.text else Color(0xFFA0A0B8),
                                animationSpec = androidx.compose.animation.core.tween(durationMillis = 200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                label = "platformText"
                            )
                            val chipBorderColor by animateColorAsState(
                                targetValue = if (isSelected) Color.Transparent else Color(0xFF2A2A3A),
                                animationSpec = androidx.compose.animation.core.tween(durationMillis = 200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                label = "platformBorder"
                            )

                            Box(
                                modifier = Modifier
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                    }
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(chipBgColor)
                                    .border(
                                        width = 1.dp,
                                        color = chipBorderColor,
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                    .then(
                                        if (isSelected) {
                                            Modifier.shadow(
                                                elevation = 8.dp,
                                                shape = RoundedCornerShape(20.dp),
                                                ambientColor = specColor.bg.copy(alpha = 0.4f),
                                                spotColor = specColor.bg.copy(alpha = 0.4f)
                                            )
                                        } else Modifier
                                    )
                                    .clickable { onPlatformChange(platform) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = platform,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = chipTextColor,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                                )
                            }
                        }
                    }

                    // Right Edge Fade Overlay
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(32.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color.Transparent, Color(0xFF0A0A0F))
                                )
                            )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Content Types Selection row - Redesigned Pill Design
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // FIX 2 - RESTORE "ALL TYPES" CHIP
                    item {
                        val isAllSelected = selectedType == "All"
                        val scaleAll by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = if (isAllSelected) 1.03f else 1.0f,
                            animationSpec = androidx.compose.animation.core.tween(durationMillis = 150),
                            label = "allScale"
                        )
                        val allBgColor by animateColorAsState(
                            targetValue = if (isAllSelected) Color(0xFFFFFFFF) else Color.Transparent,
                            animationSpec = androidx.compose.animation.core.tween(durationMillis = 200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                            label = "allBg"
                        )
                        val allTextColor by animateColorAsState(
                            targetValue = if (isAllSelected) Color(0xFF0A0A0F) else Color(0xFF606075),
                            animationSpec = androidx.compose.animation.core.tween(durationMillis = 200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                            label = "allText"
                        )
                        val allBorderColor by animateColorAsState(
                            targetValue = if (isAllSelected) Color.Transparent else Color(0xFF2A2A3A),
                            animationSpec = androidx.compose.animation.core.tween(durationMillis = 200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                            label = "allBorder"
                        )

                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = scaleAll
                                    scaleY = scaleAll
                                }
                                .clip(RoundedCornerShape(16.dp))
                                .background(allBgColor)
                                .border(
                                    width = if (isAllSelected) 0.dp else 1.dp,
                                    color = allBorderColor,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    onTypeChange("All")
                                    viewModel.clearMovieGenres()
                                    viewModel.clearTvGenres()
                                    viewModel.clearDocGenres()
                                    viewModel.loadContentForPlatform(selectedPlatform)
                                    expandedChip = null
                                }
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "All",
                                fontSize = 12.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                                fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium,
                                color = allTextColor
                            )
                        }
                    }

                    // FIX 1 - SPLIT CHIP INTO TWO TAP ZONES
                    items(contentTypesList) { type ->
                        val isSelected = selectedType == type
                        val activeGenres = when (type) {
                            "Movies" -> selectedMovieGenres
                            "TV Series" -> selectedTvGenres
                            "Documentaries" -> selectedDocGenres
                            else -> emptySet()
                        }
                        val hasActiveFilters = activeGenres.isNotEmpty()

                        val emojiLabel = when (type) {
                            "Movies" -> "🎬 Movies"
                            "TV Series" -> "📺 TV Series"
                            "Documentaries" -> "🌍 Documentaries"
                            else -> type
                        }

                        val genreMapForType = when (type) {
                            "Movies" -> movieGenresMap
                            "TV Series" -> tvGenresMap
                            "Documentaries" -> docGenresMap
                            else -> emptyMap()
                        }

                        val displayText = if (activeGenres.isEmpty()) {
                            emojiLabel
                        } else if (activeGenres.size == 1) {
                            val genreId = activeGenres.first()
                            val genreName = genreMapForType.entries.firstOrNull { it.value == genreId }?.key ?: "Genre"
                            "$emojiLabel · $genreName"
                        } else {
                            "$emojiLabel · ${activeGenres.size} Genres"
                        }

                        val scaleType by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = if (isSelected) 1.03f else 1.0f,
                            animationSpec = androidx.compose.animation.core.tween(durationMillis = 150),
                            label = "typeScale"
                        )

                        val isDropExpanded = expandedChip == type
                        val arrowRotation by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = if (isDropExpanded) 180f else 0f,
                            animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
                            label = "arrowRotation"
                        )

                        val typeBorderColor = if (isSelected) Color(0xFFE50914) else Color(0xFF2A2A3A)
                        val typeBorderWidth = if (isSelected) 2.dp else 1.dp
                        val leftZoneBg = if (isSelected) Color(0xFFE50914).copy(alpha = 0.15f) else Color.Transparent
                        val typeTextColor = if (isSelected) Color.White else Color(0xFF606075)
                        val typeTextWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        val arrowTint = if (isSelected) Color(0xFFE50914) else Color(0xFF606075)

                        Box {
                            Row(
                                modifier = Modifier
                                    .graphicsLayer {
                                        scaleX = scaleType
                                        scaleY = scaleType
                                    }
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF111118))
                                    .border(
                                        width = typeBorderWidth,
                                        color = typeBorderColor,
                                        shape = RoundedCornerShape(16.dp)
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // LEFT ZONE - selects content type
                                Row(
                                    modifier = Modifier
                                        .clickable {
                                            onTypeChange(type)
                                            expandedChip = null
                                        }
                                        .background(leftZoneBg)
                                        .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = displayText,
                                        fontSize = 12.sp,
                                        fontWeight = typeTextWeight,
                                        color = typeTextColor,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                                    )
                                }

                                // DIVIDER
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(20.dp)
                                        .background(Color(0xFF2A2A3A))
                                )

                                // RIGHT ZONE - opens genre dropdown
                                Box(
                                    modifier = Modifier
                                        .clickable {
                                            expandedChip = if (isDropExpanded) null else type
                                        }
                                        .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "▼",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = arrowTint,
                                            modifier = Modifier.graphicsLayer {
                                                rotationZ = arrowRotation
                                            }
                                        )

                                        if (hasActiveFilters) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                                    .background(Color(0xFFE50914))
                                            )
                                        }
                                    }
                                }
                            }

                            if (isDropExpanded) {
                                androidx.compose.ui.window.Popup(
                                    alignment = Alignment.TopStart,
                                    onDismissRequest = { expandedChip = null },
                                    properties = androidx.compose.ui.window.PopupProperties(focusable = true)
                                ) {
                                    Card(
                                        modifier = Modifier
                                            .width(280.dp)
                                            .padding(top = 40.dp)
                                            .shadow(8.dp, RoundedCornerShape(12.dp)),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF111118)),
                                        border = BorderStroke(1.dp, Color(0xFF2A2A3A)),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Text(
                                                text = "GENRE",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF606075),
                                                letterSpacing = 1.0.sp
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            
                                            val genreList = genreMapForType.toList()
                                            val chunks = genreList.chunked(3)
                                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                chunks.forEach { rowItems ->
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        rowItems.forEach { (genreName, genreId) ->
                                                            val isGenreSelected = activeGenres.contains(genreId)
                                                            Box(
                                                                modifier = Modifier
                                                                    .weight(1f)
                                                                    .clip(RoundedCornerShape(8.dp))
                                                                    .background(if (isGenreSelected) Color(0xFFE50914) else Color(0xFF222230))
                                                                    .border(
                                                                        width = if (isGenreSelected) 0.dp else 1.dp,
                                                                        color = if (isGenreSelected) Color.Transparent else Color(0xFF2A2A3A),
                                                                        shape = RoundedCornerShape(8.dp)
                                                                    )
                                                                    .clickable {
                                                                        when (type) {
                                                                            "Movies" -> viewModel.toggleMovieGenre(genreId)
                                                                            "TV Series" -> viewModel.toggleTvGenre(genreId)
                                                                            "Documentaries" -> viewModel.toggleDocGenre(genreId)
                                                                        }
                                                                    }
                                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Row(
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    horizontalArrangement = Arrangement.Center
                                                                ) {
                                                                    Text(
                                                                        text = genreName,
                                                                        fontSize = 10.sp,
                                                                        fontWeight = FontWeight.Medium,
                                                                        color = if (isGenreSelected) Color.White else Color(0xFFA0A0B8),
                                                                        maxLines = 1
                                                                    )
                                                                    if (isGenreSelected) {
                                                                        Spacer(modifier = Modifier.width(4.dp))
                                                                        Text(
                                                                            text = "✖",
                                                                            fontSize = 8.sp,
                                                                            color = Color.White.copy(alpha = 0.8f)
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                        if (rowItems.size < 3) {
                                                            repeat(3 - rowItems.size) {
                                                                Spacer(modifier = Modifier.weight(1f))
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(14.dp))
                                            
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                TextButton(
                                                    onClick = {
                                                        when (type) {
                                                            "Movies" -> viewModel.clearMovieGenres()
                                                            "TV Series" -> viewModel.clearTvGenres()
                                                            "Documentaries" -> viewModel.clearDocGenres()
                                                        }
                                                        expandedChip = null
                                                        viewModel.loadContentForPlatform(selectedPlatform)
                                                    }
                                                ) {
                                                    Text("Clear", color = Color(0xFF606075), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Button(
                                                    onClick = {
                                                        expandedChip = null
                                                        viewModel.loadContentForPlatform(selectedPlatform)
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                                    shape = RoundedCornerShape(8.dp)
                                                ) {
                                                    Text("Apply", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (error != null) {
            item {
                ErrorStateUI(
                    errorMessage = error ?: "",
                    onRetry = { viewModel.loadContentForPlatform(selectedPlatform) }
                )
            }
        } else if (isLoading) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    ShimmerPlaceholder(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(230.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                    
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Top 10 Trending Now",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            repeat(3) {
                                ShimmerPlaceholder(
                                    modifier = Modifier
                                        .width(110.dp)
                                        .height(165.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            }
                        }
                    }

                    repeat(2) { rowIdx ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = if (rowIdx == 0) "Top Rated Movies" else "Top Rated Series",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                repeat(3) {
                                    ShimmerPlaceholder(
                                        modifier = Modifier
                                            .width(110.dp)
                                            .height(165.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else if (filteredAll.isEmpty()) {
            item {
                EmptyStateUI(
                    platform = selectedPlatform,
                    onShowAll = { onPlatformChange("All") }
                )
            }
        } else {

        // 2. HERO BANNER - #1 TRENDING TITLE (Large wide card) - REDESIGNED
        if (filteredAll.isNotEmpty() && selectedType == "All") {
            val heroItem = filteredAll[0]
            item {
                LaunchedEffect(heroItem.id) {
                    viewModel.fetchRatingsForTmdbItem(heroItem, isTv = heroItem.media_type == "tv")
                }
                val rating = mediaRatings[heroItem.id]
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0C0C14))
                        .border(1.dp, Color(0xFF2A2A3A), RoundedCornerShape(16.dp))
                        .clickable { rating?.let { onMovieClick(it) } }
                ) {
                    // Poster Backdrop
                    coil.compose.AsyncImage(
                        model = "https://image.tmdb.org/t/p/w780${heroItem.poster_path}",
                        contentDescription = "Backdrop",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Layer 2: 30% overall dark tint
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f))
                    )

                    // Layer 1: Bottom to top radial fading overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color(0xFF0A0A0F)),
                                    startY = 220f // starts from about 40% height of card
                                )
                            )
                    )

                    // Description Overlay Content
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFE50914))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "#1 TRENDING",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    letterSpacing = 0.8.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = TmdbClient.mapGenres(heroItem.genre_ids),
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Normal,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        Text(
                            text = heroItem.title ?: heroItem.name ?: "Trending Title",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            lineHeight = 28.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.8f),
                                    offset = Offset(0f, 2f),
                                    blurRadius = 8f
                                )
                            ),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                        )
                        
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (rating != null) {
                                // IMDb rating
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("⭐", fontSize = 12.sp)
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = rating.imdb.split("/").firstOrNull() ?: rating.imdb,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFFFD700),
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                                    )
                                }
                                
                                Text("·", color = Color(0xFF606075), fontSize = 14.sp)

                                // Rotten Tomatoes rating
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🍅", fontSize = 12.sp)
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = rating.rottenTomatoes,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFE50914),
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                                    )
                                }

                                if (selectedPlatform != "All") {
                                    Text("·", color = Color(0xFF606075), fontSize = 14.sp)
                                    
                                    val specColor = platformColors[selectedPlatform] ?: ChipColor(Color(0xFFE50914), Color.White)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(specColor.bg)
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = selectedPlatform,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = specColor.text,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = "Analyzing public reviews...",
                                    fontSize = 12.sp,
                                    color = Color.LightGray.copy(alpha = 0.8f),
                                    fontStyle = FontStyle.Italic,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3a. TOP 10 MOVIES
        if (filteredMovies.isNotEmpty() && (selectedType == "All" || selectedType == "Movies")) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "Top 10 Movies",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                        modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(start = 24.dp, end = 24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(filteredMovies.take(10)) { index, item ->
                            LaunchedEffect(item.id) {
                                viewModel.fetchRatingsForTmdbItem(item, isTv = false)
                            }
                            val rating = mediaRatings[item.id]

                            Box(
                                modifier = Modifier
                                    .width(135.dp)
                                    .height(175.dp)
                            ) {
                                // Red glow outline for Movies Rank number, filled with background color
                                Text(
                                    text = "${index + 1}",
                                    fontSize = 100.sp,
                                    fontStyle = FontStyle.Normal,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.background,
                                    style = TextStyle(
                                        shadow = Shadow(
                                            color = Color(0xFFE50914),
                                            offset = Offset(0f, 0f),
                                            blurRadius = 14f
                                        )
                                    ),
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .offset(x = (-16).dp, y = 14.dp)
                                )

                                TrendingCard(
                                    item = item,
                                    ratingEntity = rating,
                                    viewModel = viewModel,
                                    modifier = Modifier
                                        .width(110.dp)
                                        .height(165.dp)
                                        .align(Alignment.TopEnd)
                                ) {
                                    rating?.let { onMovieClick(it) }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3b. TOP 10 SHOWS
        if (filteredTv.isNotEmpty() && (selectedType == "All" || selectedType == "TV Series")) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Top 10 Shows",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                        )
                        Text(
                            text = "See all",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF7B2FBE),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                        )
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(start = 24.dp, end = 24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(filteredTv.take(10)) { index, item ->
                            LaunchedEffect(item.id) {
                                viewModel.fetchRatingsForTmdbItem(item, isTv = true)
                            }
                            val rating = mediaRatings[item.id]

                            Box(
                                modifier = Modifier
                                    .width(135.dp)
                                    .height(175.dp)
                            ) {
                                // Purple glow outline for TV Shows Rank number, filled with background color
                                Text(
                                    text = "${index + 1}",
                                    fontSize = 100.sp,
                                    fontStyle = FontStyle.Normal,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.background,
                                    style = TextStyle(
                                        shadow = Shadow(
                                            color = Color(0xFF7B2FBE),
                                            offset = Offset(0f, 0f),
                                            blurRadius = 14f
                                        )
                                    ),
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .offset(x = (-16).dp, y = 14.dp)
                                )

                                TrendingCard(
                                    item = item,
                                    ratingEntity = rating,
                                    viewModel = viewModel,
                                    modifier = Modifier
                                        .width(110.dp)
                                        .height(165.dp)
                                        .align(Alignment.TopEnd)
                                ) {
                                    rating?.let { onMovieClick(it) }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 7. 🔥 Rising Now Section
        if (risingNow.isNotEmpty() && selectedType == "All") {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)) {
                        Text(
                            text = "🔥 Rising Now",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Gaining momentum fast",
                            fontSize = 12.sp,
                            color = Color(0xFF606075),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                        )
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(risingNow) { item ->
                            LaunchedEffect(item.id) {
                                viewModel.fetchRatingsForTmdbItem(item, isTv = item.media_type == "tv")
                            }
                            val rating = mediaRatings[item.id]
                            
                            RisingCard(
                                item = item,
                                ratingEntity = rating,
                                viewModel = viewModel,
                                modifier = Modifier.width(130.dp).height(195.dp)
                            ) {
                                rating?.let { onMovieClick(it) }
                            }
                        }
                    }
                }
            }
        }

        // 8. ⭐ Critically Acclaimed Section
        if (criticallyAcclaimed.isNotEmpty() && selectedType == "All") {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)) {
                        Text(
                            text = "⭐ Critically Acclaimed",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Critics and audiences agree",
                            fontSize = 12.sp,
                            color = Color(0xFF606075),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                        )
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(criticallyAcclaimed) { item ->
                            LaunchedEffect(item.id) {
                                viewModel.fetchRatingsForTmdbItem(item, isTv = item.media_type == "tv")
                            }
                            val rating = mediaRatings[item.id]
                            
                            AcclaimedCard(
                                item = item,
                                ratingEntity = rating,
                                viewModel = viewModel,
                                modifier = Modifier.width(150.dp).height(210.dp)
                            ) {
                                rating?.let { onMovieClick(it) }
                            }
                        }
                    }
                }
            }
        }

        // 9. 💎 Hidden Gems Section
        if (hiddenGems.isNotEmpty() && selectedType == "All") {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)) {
                        Text(
                            text = "💎 Hidden Gems",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Underrated titles worth watching",
                            fontSize = 12.sp,
                            color = Color(0xFF606075),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                        )
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(hiddenGems) { item ->
                            LaunchedEffect(item.id) {
                                viewModel.fetchRatingsForTmdbItem(item, isTv = item.media_type == "tv")
                            }
                            val rating = mediaRatings[item.id]
                            
                            GemCard(
                                item = item,
                                ratingEntity = rating,
                                viewModel = viewModel,
                                modifier = Modifier.width(130.dp).height(195.dp)
                            ) {
                                rating?.let { onMovieClick(it) }
                            }
                        }
                    }
                }
            }
        }

        // 6. TRENDING DOCUMENTARIES (OPTIONAL ROW)
        if (filteredDocs.isNotEmpty() && (selectedType == "All" || selectedType == "Documentaries")) {
            item {
                CarouselRowSection(
                    title = "Trending Documentaries",
                    items = filteredDocs,
                    isTv = false,
                    viewModel = viewModel,
                    mediaRatings = mediaRatings,
                    platform = selectedPlatform,
                    onMovieClick = onMovieClick
                )
            }
        }
        }
    }
}

@Composable
fun CarouselRowSection(
    title: String,
    items: List<TmdbTarget>,
    isTv: Boolean,
    viewModel: MovieViewModel,
    mediaRatings: Map<Int, MovieRatingEntity>,
    platform: String,
    onMovieClick: (MovieRatingEntity) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
            modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(items) { item ->
                LaunchedEffect(item.id) {
                    viewModel.fetchRatingsForTmdbItem(item, isTv = isTv)
                }
                val rating = mediaRatings[item.id]
                
                TrendingCard(
                    item = item,
                    ratingEntity = rating,
                    viewModel = viewModel,
                    platform = platform,
                    modifier = Modifier.width(110.dp).height(165.dp)
                ) {
                    rating?.let { onMovieClick(it) }
                }
            }
        }
    }
}

@Composable
fun StreamingProviderLogo(
    tmdbId: Int,
    isTv: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var provider by remember(tmdbId, isTv) { mutableStateOf<com.example.network.TmdbProvider?>(null) }
    
    LaunchedEffect(tmdbId, isTv) {
        provider = com.example.network.TmdbClient.fetchFirstWatchProvider(context, tmdbId, isTv)
    }

    provider?.logo_path?.let { logoPath ->
        Box(
            modifier = modifier
                .size(28.dp)
                .shadow(2.dp, shape = CircleShape)
                .background(Color.White, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            coil.compose.AsyncImage(
                model = "https://image.tmdb.org/t/p/w45$logoPath",
                contentDescription = provider?.provider_name ?: "Streaming Provider Logo",
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
fun WatchlistButton(
    item: TmdbTarget,
    viewModel: MovieViewModel,
    modifier: Modifier = Modifier
) {
    val watchlist by viewModel.watchlist.collectAsState()
    val isSaved = remember(watchlist, item.id) { watchlist.any { it.tmdbId == item.id } }

    var scale by remember { mutableStateOf(1.0f) }
    val animatedScale by animateFloatAsState(
        targetValue = scale,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "WatchlistScale"
    )

    LaunchedEffect(scale) {
        if (scale == 1.3f) {
            kotlinx.coroutines.delay(120)
            scale = 1.0f
        }
    }

    Box(
        modifier = modifier
            .size(28.dp)
            .graphicsLayer(
                scaleX = animatedScale,
                scaleY = animatedScale
            )
            .clip(CircleShape)
            .background(
                if (isSaved) Color(0xFFE50914) else Color.Black.copy(alpha = 0.6f)
            )
            .clickable {
                scale = 1.3f
                viewModel.toggleWatchlist(item)
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isSaved) Icons.Default.Check else Icons.Default.Add,
            contentDescription = if (isSaved) "Remove from Watchlist" else "Add to Watchlist",
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun TrendingCard(
    item: TmdbTarget,
    ratingEntity: MovieRatingEntity?,
    viewModel: MovieViewModel,
    modifier: Modifier = Modifier,
    platform: String = "Premium",
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111118)),
        border = BorderStroke(1.dp, Color(0xFF2A2A3A))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Poster Cover
            coil.compose.AsyncImage(
                model = "https://image.tmdb.org/t/p/w342${item.poster_path}",
                contentDescription = item.title ?: item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Watchlist button on Top Right corner
            WatchlistButton(
                item = item,
                viewModel = viewModel,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            )

            // Bottom Gradient Backdrop (transparent to card bg #0A0A0F 85%)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xFF0A0A0F).copy(alpha = 0.85f), Color(0xFF0A0A0F))
                        )
                    )
            )

            // Bottom Core Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Streaming logo (bottom left, 28x28 box, 24x24 image inside)
                StreamingProviderLogo(
                    tmdbId = item.id,
                    isTv = item.media_type == "tv"
                )

                Spacer(modifier = Modifier.width(8.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // Title
                    Text(
                        text = item.title ?: item.name ?: "Unknown",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Ratings
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val scoreStr = if (ratingEntity != null) {
                            ratingEntity.imdb.split("/").firstOrNull() ?: String.format(java.util.Locale.US, "%.1f", item.vote_average ?: 8.2)
                        } else {
                            String.format(java.util.Locale.US, "%.1f", item.vote_average ?: 8.2)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⭐", fontSize = 10.sp)
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = scoreStr,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFFFD700),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                            )
                        }

                        val rtScore = ratingEntity?.rottenTomatoes ?: "84%"
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🍅", fontSize = 10.sp)
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = rtScore,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFFF6B6B),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Type badge (bottom right)
                val typeStr = when {
                    item.media_type == "tv" -> "SERIES"
                    item.genre_ids?.contains(99) == true -> "DOC"
                    else -> "MOVIE"
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = typeStr,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 0.5.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                    )
                }
            }
        }
    }
}

// ---------------------- SEARCH SCREEN MODULE ----------------------
@Composable
fun SearchScreen(
    searchUiState: SearchUiState,
    searchHistory: List<MovieRatingEntity>,
    viewModel: MovieViewModel
) {
    var queryText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0F))
            .padding(horizontal = 16.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF12121A)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF2A2A3E))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                OutlinedTextField(
                    value = queryText,
                    onValueChange = { queryText = it },
                    placeholder = { 
                        Text(
                            "Type movie or series title...", 
                            color = Color(0xFF787890), 
                            fontSize = 14.sp, 
                            fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                        ) 
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_search_field"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFE50914),
                        unfocusedBorderColor = Color(0xFF2A2A3A),
                        focusedContainerColor = Color(0xFF0C0C12),
                        unfocusedContainerColor = Color(0xFF0C0C12),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Icon",
                            tint = Color(0xFFE50914)
                        )
                    },
                    trailingIcon = {
                        if (queryText.isNotEmpty()) {
                            IconButton(onClick = {
                                queryText = ""
                                viewModel.resetSearchState()
                            }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Clear", tint = Color.White)
                            }
                        }
                    },
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = {
                            if (queryText.isNotBlank()) {
                                viewModel.searchMovie(queryText)
                            }
                        }
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Suggestion header
                Text(
                    text = "QUICK SUGGESTIONS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE50914),
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                val suggestions = listOf(
                    "Stranger Things", "Inception", "Breaking Bad", "Interstellar", "Dune", "Wednesday"
                )

                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 4.dp)
                ) {
                    items(suggestions) { keyword ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1E1E2C))
                                .clickable {
                                    queryText = keyword
                                    viewModel.searchMovie(keyword)
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = keyword,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            // Live Search Loading or Error widgets
            item {
                AnimatedVisibility(
                    visible = searchUiState !is SearchUiState.Idle,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    when (searchUiState) {
                        is SearchUiState.Loading -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF111118)),
                                border = BorderStroke(1.dp, Color(0xFF2A2A3A))
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(color = Color(0xFFE50914), strokeWidth = 3.dp)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("Analyzing ratings metrics...", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif)
                                    Text("Gemini AI synthesizing parent guidance reports...", fontSize = 10.sp, color = Color(0xFFA0A0B8), modifier = Modifier.padding(top = 4.dp), fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif)
                                }
                            }
                        }

                        is SearchUiState.SelectCandidate -> {
                            val state = searchUiState as SearchUiState.SelectCandidate
                            MovieSelectionCard(
                                query = state.query,
                                candidates = state.candidates,
                                onSelect = { id, title, year -> viewModel.fetchAndShowMovieById(id, title, year) },
                                onCancel = { viewModel.resetSearchState() }
                            )
                        }

                        is SearchUiState.Error -> {
                            val msg = (searchUiState as SearchUiState.Error).message
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B0B0C)),
                                border = BorderStroke(1.dp, Color(0xFF4A1517))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Default.Cancel, contentDescription = "Err", tint = Color(0xFFEF5350))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Lookup Incomplete", fontWeight = FontWeight.Black, color = Color(0xFFEF5350), fontSize = 14.sp)
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(msg, fontSize = 12.sp, color = Color(0xFFD8D2D2), lineHeight = 18.sp)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = { viewModel.resetSearchState() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3F1A1B)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Dismiss", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        else -> {}
                    }
                }
            }

            // Recent Searches History
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recent Lookups", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color.White)
                    if (searchHistory.isNotEmpty()) {
                        Text(
                            text = "Clear History",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE50914),
                            modifier = Modifier.clickable { viewModel.clearAllHistory() }
                        )
                    }
                }
            }

            if (searchHistory.isNotEmpty()) {
                items(searchHistory) { item ->
                    HistoryRatingRow(
                        item = item,
                        onClick = { viewModel.selectCachedMovie(item) },
                        onDelete = { viewModel.deleteHistoryItem(item.id) }
                    )
                }
            } else {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0C0C14))
                            .border(1.dp, Color(0xFF1F1F30), RoundedCornerShape(12.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Search history is empty.", fontSize = 11.sp, color = Color(0xFF5E5A6C))
                    }
                }
            }
        }
    }
}

// ---------------------- SETTINGS SCREEN MODULE ----------------------
@Composable
fun SettingsScreen(
    isOverlayGranted: Boolean,
    isAccessibilityGranted: Boolean,
    viewModel: MovieViewModel,
    searchUiState: SearchUiState,
    searchHistory: List<MovieRatingEntity>
) {
    val context = LocalContext.current
    val sharedPrefs = context.getSharedPreferences("RateifyPrefs", Context.MODE_PRIVATE)

    // OMDb setups
    val omdbSavedKey = remember { mutableStateOf(sharedPrefs.getString("omdb_api_key", "8171c492") ?: "8171c492") }
    var tempOnKeyText by remember { mutableStateOf(omdbSavedKey.value) }

    // Gemini sets
    val gemSavedKey = remember { mutableStateOf(sharedPrefs.getString("gemini_api_key", "") ?: "") }
    var tempGemKeyText by remember { mutableStateOf(gemSavedKey.value) }

    // TMDb sets
    val tmdbSavedKey = remember { mutableStateOf(sharedPrefs.getString("tmdb_api_key", "") ?: "") }
    var tempTmdbKeyText by remember { mutableStateOf(tmdbSavedKey.value) }

    // Dropdown platforms pref
    var showDropdownPref by remember { mutableStateOf(false) }
    var prefPlatformOfUser by remember { mutableStateOf(sharedPrefs.getString("default_platform_preference", "All") ?: "All") }

    val platformOptions = listOf("All", "Netflix", "Prime Video", "Disney+ Hotstar", "JioHotstar", "SonyLIV", "Zee5")

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0F)).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // TOP SECTION: Interactive Manual Search Bar (Change 5)
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111118)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF2A2A3A))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Manual Lookups Control Desk", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    
                    var configQueryText by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = configQueryText,
                        onValueChange = { configQueryText = it },
                        placeholder = { Text("Query, e.g. Stranger Things...", color = Color(0xFF8A8AB0), fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFE50914),
                            unfocusedBorderColor = Color(0xFF2A2A3A),
                            focusedContainerColor = Color(0xFF0A0A0F),
                            unfocusedContainerColor = Color(0xFF0A0A0F),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        trailingIcon = {
                            IconButton(onClick = {
                                if (configQueryText.isNotBlank()) {
                                    viewModel.searchMovie(configQueryText)
                                }
                            }) {
                                Icon(Icons.Default.ArrowForward, "Go", tint = Color(0xFFE50914))
                            }
                        }
                    )
                }
            }
        }

        // PERMISSIONS AND SERVICE CONTROLS
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111118)),
                border = BorderStroke(1.dp, Color(0xFF2A2A3A)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Background Overlay Scanner Services", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Configure permissions required to scan content titles overlays on external portals.", fontSize = 10.sp, color = Color(0xFF8B8894))
                    Spacer(modifier = Modifier.height(14.dp))

                    PermissionStatusRow(
                        title = "Overlay System Window",
                        desc = "Needed to rendering rating display bubbles over top video applications.",
                        granted = isOverlayGranted,
                        onGrantClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = Color(0xFF2A2A3A))
                    Spacer(modifier = Modifier.height(10.dp))

                    PermissionStatusRow(
                        title = "Accessibility Capture Scanner",
                        desc = "Taps into active title details stream inside third-party programs.",
                        granted = isAccessibilityGranted,
                        onGrantClick = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        }
                    )
                }
            }
        }

        // TRIPLE API KEY MANAGER MODULE
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111118)),
                border = BorderStroke(1.dp, Color(0xFF2A2A3A)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("API Key Integrations", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Synchronize keys securely to authorize live metric pipelines.", fontSize = 10.sp, color = Color(0xFF8B8894))
                    Spacer(modifier = Modifier.height(14.dp))

                    // OMDb Key
                    Text("OMDb Rating Key", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = tempOnKeyText,
                        onValueChange = { tempOnKeyText = it },
                        singleLine = true,
                        placeholder = { Text("Enter OMDb key...", color = Color.Gray, fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFE50914),
                            unfocusedBorderColor = Color(0xFF2A2A3A)
                        )
                    )

                    // Gemini Key
                    Text("Gemini AI API Key (Required for Consensuses & Guides)", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = tempGemKeyText,
                        onValueChange = { tempGemKeyText = it },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        placeholder = { Text("AIzaSy...", color = Color.Gray, fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFE50914),
                            unfocusedBorderColor = Color(0xFF2A2A3A)
                        )
                    )

                    // TMDb Key
                    Text("TMDb Discovery Key (Unlocks Homepage Feeds)", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = tempTmdbKeyText,
                        onValueChange = { tempTmdbKeyText = it },
                        singleLine = true,
                        placeholder = { Text("Enter TMDb key (V3 auth format)...", color = Color.Gray, fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 14.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFE50914),
                            unfocusedBorderColor = Color(0xFF2A2A3A)
                        )
                    )

                    Button(
                        onClick = {
                            val omdb = tempOnKeyText.trim().ifBlank { "8171c492" }
                            val gem = tempGemKeyText.trim()
                            val tmdb = tempTmdbKeyText.trim()
                            
                            sharedPrefs.edit()
                                .putString("omdb_api_key", omdb)
                                .putString("gemini_api_key", gem)
                                .putString("tmdb_api_key", tmdb)
                                .apply()

                            com.example.network.GeminiClient.customApiKey = gem.ifBlank { null }
                            com.example.network.TmdbClient.customApiKey = tmdb.ifBlank { null }
                            
                            Toast.makeText(context, "Configurations sync success!", Toast.LENGTH_SHORT).show()
                            viewModel.loadHomepageContent() // Refresh home feed!
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914), contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Connect and Authorize", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // PREFERENCES MODULE (Default homepage chips filters)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111118)),
                border = BorderStroke(1.dp, Color(0xFF2A2A3A)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Default Preferences", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Select default streamline outlet settings on startup.", fontSize = 10.sp, color = Color(0xFF8B8894))
                    Spacer(modifier = Modifier.height(14.dp))

                    Text("Network preference: $prefPlatformOfUser", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        platformOptions.take(4).forEach { opt ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (prefPlatformOfUser == opt) Color(0xFFE50914) else Color(0xFF1A1A24))
                                    .clickable {
                                        prefPlatformOfUser = opt
                                        sharedPrefs.edit().putString("default_platform_preference", opt).apply()
                                        Toast.makeText(context, "Startup filter set to $opt!", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(opt, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // THEME MODE MANAGER MODULE (Change 2)
        item {
            var currentThemeMode by remember {
                mutableStateOf(sharedPrefs.getString("theme_mode", "system") ?: "system")
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111118)),
                border = BorderStroke(1.dp, Color(0xFF2A2A3A)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Application Theme", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Select between Dark Mode, Light Mode or Follow System Settings", fontSize = 10.sp, color = Color(0xFF8B8894))
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(
                            "dark" to "Dark Mode",
                            "light" to "Light Mode",
                            "system" to "System Default"
                        ).forEach { (mode, label) ->
                            val isCurrentSelected = currentThemeMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isCurrentSelected) Color(0xFFE50914) else Color(0xFF1A1A24))
                                    .clickable {
                                        currentThemeMode = mode
                                        sharedPrefs.edit().putString("theme_mode", mode).apply()
                                        
                                        // Update AppCompatDelegate for immediate effect
                                        when (mode) {
                                            "dark" -> androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES)
                                            "light" -> androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO)
                                            else -> androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                                        }
                                        
                                        Toast.makeText(context, "$label activated!", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // HUD TOGGLE SWITCH EXTRA
        item {
            val isHUDActive by OverlayState.isOverlayVisible.collectAsState()
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111118)),
                border = BorderStroke(1.dp, Color(0xFF2A2A3A)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Real-Time HUD Overlay Screen", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Active floating scanner widget bubble dynamically.", fontSize = 10.sp, color = Color(0xFF8B8894))
                    }
                    Switch(
                        checked = isHUDActive,
                        onCheckedChange = { active ->
                            if (isOverlayGranted) {
                                triggerOverlay(context, if (active) FloatingOverlayService.ACTION_SHOW else FloatingOverlayService.ACTION_HIDE)
                            } else {
                                Toast.makeText(context, "Please configure overlays above first.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFFE50914),
                            checkedTrackColor = Color(0xFF2E1114),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color(0xFF1A1A24)
                        )
                    )
                }
            }
        }

        // THEME DETAILS
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111118)),
                border = BorderStroke(1.dp, Color(0xFF2A2A3A)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Deep-Space Dark Concept Mode", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("The exclusive premium aesthetic colorways layer.", fontSize = 10.sp, color = Color(0xFF8B8894))
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFE50914).copy(alpha = 0.1f))
                            .border(1.dp, Color(0xFFE50914), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("MANDATORY", fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFE50914))
                    }
                }
            }
        }

        // SLEEK ABOUT FOOTER
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Know before you watch.",
                    fontSize = 13.sp,
                    color = Color(0xFF8A8AB0),
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                    letterSpacing = 0.3.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Text(
                    text = "Rateify AI • Version 2.1.0 • Built with Google Gemini AI",
                    fontSize = 10.sp,
                    color = Color(0xFF4C4A5A),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "© 2026 Rateify Inc. All rights reserved.",
                    fontSize = 9.sp,
                    color = Color(0xFF323240)
                )
            }
        }
    }
}

// ---------------------- COMMON PRESENTATION COMPOSABLES ----------------------
@Composable
fun BottomNavBar(
    activeTab: Int,
    onTabSelect: (Int) -> Unit
) {
    NavigationBar(
        containerColor = Color(0xFF0A0A0F),
        tonalElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .border(1.dp, Color(0xFF2A2A3A), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
    ) {
        NavigationBarItem(
            selected = activeTab == 0,
            onClick = { onTabSelect(0) },
            icon = { Icon(Icons.Default.Home, "Home", modifier = Modifier.size(20.dp)) },
            label = { Text("Home", fontSize = 11.sp, fontWeight = FontWeight.Medium, fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFFE50914),
                selectedTextColor = Color.White,
                unselectedIconColor = Color(0xFF606075),
                unselectedTextColor = Color(0xFF606075),
                indicatorColor = Color(0xFF111118)
            )
        )
        NavigationBarItem(
            selected = activeTab == 1,
            onClick = { onTabSelect(1) },
            icon = { Icon(Icons.Default.Search, "Search", modifier = Modifier.size(20.dp)) },
            label = { Text("Search", fontSize = 11.sp, fontWeight = FontWeight.Medium, fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFFE50914),
                selectedTextColor = Color.White,
                unselectedIconColor = Color(0xFF606075),
                unselectedTextColor = Color(0xFF606075),
                indicatorColor = Color(0xFF111118)
            )
        )
        NavigationBarItem(
            selected = activeTab == 2,
            onClick = { onTabSelect(2) },
            icon = { Icon(Icons.Default.Settings, "Settings", modifier = Modifier.size(20.dp)) },
            label = { Text("Settings", fontSize = 11.sp, fontWeight = FontWeight.Medium, fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFFE50914),
                selectedTextColor = Color.White,
                unselectedIconColor = Color(0xFF606075),
                unselectedTextColor = Color(0xFF606075),
                indicatorColor = Color(0xFF111118)
            )
        )
    }
}

@Composable
fun PermissionStatusRow(
    title: String,
    desc: String,
    granted: Boolean,
    onGrantClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(desc, fontSize = 10.sp, color = Color(0xFF8B8894), lineHeight = 14.sp, modifier = Modifier.padding(top = 2.dp))
        }

        if (granted) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF10B981).copy(alpha = 0.15f))
                    .border(1.dp, Color(0xFF10B981), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("AUTHORIZED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
            }
        } else {
            Button(
                onClick = onGrantClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text("AUTHORIZE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
fun MovieSelectionCard(
    query: String,
    candidates: List<com.example.network.GeminiClient.CandidateWithScore>,
    onSelect: (String, String, String) -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("selection_container_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10101C)),
        border = BorderStroke(1.dp, Color(0xFF2C2C3F))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Confirm Exact Title Match",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Cancel",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFEF5350),
                    modifier = Modifier.clickable(onClick = onCancel)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Multiple candidate titles found on index for query \"$query\":",
                fontSize = 10.sp,
                color = Color(0xFF8B8894)
            )
            Spacer(modifier = Modifier.height(10.dp))

            candidates.take(5).forEachIndexed { index, wrapper ->
                val record = wrapper.candidate
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelect(record.imdbID ?: "", record.Title ?: "", record.Year ?: "") }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${index + 1}. ${record.Title ?: "N/A"}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Release Year: ${record.Year ?: "N/A"}",
                            fontSize = 10.sp,
                            color = Color(0xFF8B8894)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF8B2FC9).copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text("${wrapper.score}% Fit", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8B2FC9))
                    }
                }
            }
        }
    }
}

@Composable
fun MovieReviewDetailCard(
    movie: MovieRatingEntity,
    onClose: () -> Unit,
    viewModel: MovieViewModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // CLOSE ACTION HEADER
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = movie.title, 
                        fontSize = 24.sp, 
                        fontWeight = FontWeight.ExtraBold, 
                        color = Color.White,
                        lineHeight = 28.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Year: ${movie.year}  •  Director: ${movie.director}",
                        fontSize = 11.sp,
                        color = Color(0xFFE50914),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = movie.genre,
                        fontSize = 11.sp,
                        color = Color(0xFFA0A0B8),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1A1A24))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close, 
                        contentDescription = "Close", 
                        tint = Color.LightGray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // ENRICHED REVIEWS RATINGS GRID AREA
        item {
            val rtSource = try {
                com.example.network.Source.valueOf(movie.tomatoURL)
            } catch (e: Exception) {
                com.example.network.Source.UNAVAILABLE
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ALWAYS show IMDb Score on the left
                EnrichedRatingWidget(
                    title = "IMDb SCORE",
                    score = movie.imdb,
                    votes = if (movie.imdbVotes != "N/A" && movie.imdbVotes.isNotBlank()) "${movie.imdbVotes} votes" else "N/A",
                    logoColor = Color(0xFFE50914),
                    icon = Icons.Default.Star,
                    modifier = if (rtSource == com.example.network.Source.UNAVAILABLE) Modifier.fillMaxWidth() else Modifier.weight(1f)
                )

                if (rtSource != com.example.network.Source.UNAVAILABLE) {
                    val scoreColor = if (rtSource == com.example.network.Source.GEMINI) Color(0xFFE50914) else Color(0xFF2175D9)
                    val label = if (rtSource == com.example.network.Source.GEMINI) "Tomatometer" else "TMDb Score"
                    
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF111118))
                            .border(1.dp, Color(0xFF2A2A3A), RoundedCornerShape(14.dp))
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Text(
                                text = if (rtSource == com.example.network.Source.GEMINI) "🍅" else "🎬",
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = label,
                                fontSize = 9.sp,
                                color = Color(0xFFA0A0B8),
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = movie.rottenTomatoes,
                            fontSize = 16.sp,
                            color = scoreColor,
                            fontWeight = FontWeight.ExtraBold
                        )
                        
                        if (rtSource == com.example.network.Source.GEMINI) {
                            val status = movie.tomatoImage.trim()
                            val (badgeBg, badgeText, badgeIcon) = when {
                                status.contains("Certified", ignoreCase = true) -> Triple(Color(0xFF2ECC71), "Certified Fresh", "🏆")
                                status.contains("Fresh", ignoreCase = true) -> Triple(Color(0xFFE50914), "Fresh", "🍅")
                                status.contains("Rotten", ignoreCase = true) -> Triple(Color(0xFF7F8C8D), "Rotten", "🦠")
                                else -> Triple(Color(0xFFE50914), status.ifBlank { "Fresh" }, "🍅")
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(badgeBg.copy(alpha = 0.15f))
                                    .border(1.dp, badgeBg.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(badgeIcon, fontSize = 9.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(badgeText, fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            // TMDB_SUBSTITUTE badge
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF2175D9).copy(alpha = 0.15f))
                                    .border(1.dp, Color(0xFF2175D9).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("via TMDb", fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }
        }

        // COGNITIVE CONSENSUS GAP GRAPHIC INDICATOR
        item {
            val tmClean = movie.rottenTomatoes.replace("%", "").trim().toIntOrNull()
            val tumClean = movie.tomatoUserMeter.replace("%", "").trim().toIntOrNull()
            if (tmClean != null && tumClean != null) {
                val gap = tumClean - tmClean
                val absGap = kotlin.math.abs(gap)
                if (absGap >= 8) {
                    val message = when {
                        gap > 0 -> "Mainstream gap found: Audience favored this more than critics (+$absGap% Gap). Highly entertaining!"
                        else -> "Mainstream gap found: Critics scored this higher than general audiences (-$absGap% Gap). Strong auteur/artistic appeal!"
                    }
                    val accentColor = if (gap > 0) Color(0xFF2ECC71) else Color(0xFFE50914)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(accentColor.copy(alpha = 0.12f))
                            .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.TrendingUp, contentDescription = "Gap Indicator", tint = accentColor, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = message,
                            fontSize = 11.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        // COLLAPSIBLE PARENTS GUIDE SECTION (Change 4)
        item {
            ParentsGuideSection(movie = movie, viewModel = viewModel)
        }

        // STREAMING OUTLETS LAYOUT
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Streaming Outlets Available", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA0A0B8), letterSpacing = 0.5.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val providers = movie.platforms.split(",").map { it.trim() }.filter { it.isNotEmpty() && it != "N/A" && it != "None" }
                    if (providers.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF111118))
                                .border(1.dp, Color(0xFF2A2A3A), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("Web / Buy / Rent Released Only", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        }
                    } else {
                        providers.forEach { platform ->
                            val color = when (platform) {
                                "Netflix" -> Color(0xFFE50914)
                                "Prime Video" -> Color(0xFF00A8E1)
                                "Hotstar", "Disney+ Hotstar", "JioHotstar" -> Color(0xFF1F80E0)
                                "SonyLIV" -> Color(0xFFFFD700)
                                "Zee5" -> Color(0xFF7B2FBE)
                                else -> Color(0xFF1A1A24)
                            }
                            val textCol = if (platform == "SonyLIV") Color.Black else Color.White
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(color)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(platform, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textCol)
                            }
                        }
                    }
                }
            }
        }

        // CINEMATIC SYNOPSIS
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Cinematic Synopsis", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA0A0B8), letterSpacing = 0.5.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                     text = movie.synopsis, 
                     fontSize = 13.sp, 
                     color = Color.White, 
                     lineHeight = 20.sp
                )
            }
        }

        // DUAL CONSENSUS BLURBS BY GEMINI AI
        if ((movie.criticConsensus != "N/A" && movie.criticConsensus.isNotBlank()) || 
            (movie.audienceConsensus != "N/A" && movie.audienceConsensus.isNotBlank())) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF111118))
                        .border(1.dp, Color(0xFF2A2A3A), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Rateify AI Consensus summaries", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE50914))
                    
                    if (movie.criticConsensus != "N/A" && movie.criticConsensus.isNotBlank()) {
                        Row {
                            Icon(Icons.Default.Newspaper, null, tint = Color(0xFFE50914), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Critic Consensus (RT Critics)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA0A0B8))
                                Text(movie.criticConsensus, fontSize = 11.sp, color = Color.LightGray, lineHeight = 16.sp)
                            }
                        }
                    }

                    if (movie.audienceConsensus != "N/A" && movie.audienceConsensus.isNotBlank()) {
                        Row {
                            Icon(Icons.Default.People, null, tint = Color(0xFF2196F3), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("General Audience (IMDb/RT Users)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFA0A0B8))
                                Text(movie.audienceConsensus, fontSize = 11.sp, color = Color.LightGray, lineHeight = 16.sp)
                            }
                        }
                    }
                }
            }
        }

        // PRAISE AND COMMON CRITICISMS
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF111118))
                    .border(1.dp, Color(0xFF2A2A3A), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.ThumbUp, contentDescription = "Pros", tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Praise Consensus", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981), letterSpacing = 0.5.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(movie.positiveSummary, fontSize = 12.sp, color = Color(0xFFD4C8C8), lineHeight = 18.sp)

                Spacer(modifier = Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.ThumbDown, contentDescription = "Cons", tint = Color(0xFFEF5350), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Common Criticisms", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF5350), letterSpacing = 0.5.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(movie.negativeSummary, fontSize = 12.sp, color = Color(0xFFD4C8C8), lineHeight = 18.sp)
            }
        }
    }
}

// COLLAPSIBLE COMPOSABLE PARENTS GUIDE COMPONENT
@Composable
fun ShimmerRow() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(Color.Gray.copy(alpha = alpha))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Gray.copy(alpha = alpha))
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Gray.copy(alpha = alpha))
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(50.dp))
                .background(Color.Gray.copy(alpha = alpha))
        )
    }
}

fun getCategoryRatingColor(rating: String): Color {
    return when (rating.trim().lowercase()) {
        "none" -> Color(0xFF2ECC71)
        "mild" -> Color(0xFFF1C40F)
        "moderate" -> Color(0xFFE67E22)
        "severe" -> Color(0xFFE74C3C)
        else -> Color(0xFF8A8AB0)
    }
}

@Composable
fun ParentsGuideRowItem(categoryName: String, rating: String, description: String, icon: ImageVector) {
    val pillBg = getCategoryRatingColor(rating)
    val pillTextColor = if (rating.trim().lowercase() == "mild") Color.Black else Color.White

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF8B8894),
            modifier = Modifier.size(16.dp).padding(top = 1.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(categoryName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(pillBg)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = rating,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = pillTextColor
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                fontSize = 10.sp,
                color = Color(0xFF8B8894),
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
fun ParentsGuideSection(movie: MovieRatingEntity, viewModel: MovieViewModel) {
    var isExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(isExpanded, movie.title) {
        if (isExpanded) {
            viewModel.loadParentsGuide(movie.title, movie.year, movie.rated)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10101C)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFF222234))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Shield Icon",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Parents Guide",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(14.dp))

                val state by viewModel.parentsGuideState.collectAsState()

                when (val current = state) {
                    is ParentsGuideState.Loading -> {
                        Column {
                            repeat(5) {
                                ShimmerRow()
                            }
                        }
                    }
                    is ParentsGuideState.Error -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Error loading parents guide: ${current.message}",
                                color = Color(0xFFE74C3C),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { viewModel.loadParentsGuide(movie.title, movie.year, movie.rated) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B2FC9))
                            ) {
                                Text("Retry", color = Color.White)
                            }
                        }
                    }
                    is ParentsGuideState.Success -> {
                        val guide = current.guide
                        
                        ParentsGuideRowItem("Sex & Nudity", guide.sexNudity.rating, guide.sexNudity.description, Icons.Default.Favorite)
                        ParentsGuideRowItem("Violence & Gore", guide.violenceGore.rating, guide.violenceGore.description, Icons.Default.Warning)
                        ParentsGuideRowItem("Profanity & Language", guide.profanity.rating, guide.profanity.description, Icons.Default.Lock)
                        ParentsGuideRowItem("Alcohol, Drugs & Smoking", guide.substanceUse.rating, guide.substanceUse.description, Icons.Default.Warning)
                        ParentsGuideRowItem("Frightening Scenes", guide.frighteningScenes.rating, guide.frighteningScenes.description, Icons.Default.Info)

                        Spacer(modifier = Modifier.height(14.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1E1E2E))
                                .border(1.dp, Color(0xFF8B2FC9), RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "🎯 Rated ${guide.overallRating ?: movie.rated}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "India Certification: ${guide.certificationIndia ?: "U/A 13+"}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.LightGray
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Recommended age: ${guide.minAge ?: "13+"}+",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------- PRE-EXISTING COMMONLY USED HELPERS ----------------------
@Composable
fun TomatoBadge(status: String) {
    val clean = status.trim().lowercase()
    if (clean == "n/a" || clean.isBlank()) return
    val (color, text, icon) = when {
        clean.contains("certified") -> Triple(Color(0xFFE50914), "Certified", Icons.Default.Verified)
        clean.contains("fresh") -> Triple(Color(0xFFEF5350), "Fresh", Icons.Default.CheckCircle)
        clean.contains("rotten") -> Triple(Color(0xFF4CAF50), "Rotten", Icons.Default.Cancel)
        else -> return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Icon(imageVector = icon, contentDescription = text, tint = color, modifier = Modifier.size(10.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun EnrichedRatingWidget(
    title: String,
    score: String,
    votes: String,
    logoColor: Color,
    icon: ImageVector,
    status: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0C0C14))
            .border(1.dp, Color(0xFF222234), RoundedCornerShape(14.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(imageVector = icon, contentDescription = title, tint = logoColor, modifier = Modifier.size(12.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(title, fontSize = 9.sp, color = Color(0xFF8B8894), fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(score, fontSize = 16.sp, color = logoColor, fontWeight = FontWeight.ExtraBold)
        if (votes.isNotBlank() && votes != "N/A") {
            Text(
                text = votes,
                fontSize = 8.sp,
                color = Color.LightGray,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        status?.let {
            TomatoBadge(status = it)
        }
    }
}

@Composable
fun HistoryRatingRow(
    item: MovieRatingEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10101C)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFF212132))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0A0A10))
                    .border(1.dp, Color(0xFF212132), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.imdb.take(3),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF8B2FC9)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(item.genre, fontSize = 10.sp, color = Color(0xFF8B8894))
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1C1319))
            ) {
                Icon(
                    imageVector = Icons.Default.Delete, 
                    contentDescription = "Remove cached", 
                    tint = Color(0xFFEF5350),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

data class ChipColor(val bg: Color, val text: Color)

val platformColors = mapOf(
    "All" to ChipColor(Color(0xFFFFFFFF), Color(0xFF000000)),
    "Netflix" to ChipColor(Color(0xFFE50914), Color(0xFFFFFFFF)),
    "Prime Video" to ChipColor(Color(0xFF00A8E1), Color(0xFFFFFFFF)),
    "JioHotstar" to ChipColor(Color(0xFF1F80E0), Color(0xFFFFFFFF)),
    "SonyLIV" to ChipColor(Color(0xFFFFD700), Color(0xFF000000)),
    "Zee5" to ChipColor(Color(0xFF8B2FC9), Color(0xFFFFFFFF)),
    "Disney+ Hotstar" to ChipColor(Color(0xFF1F80E0), Color(0xFFFFFFFF)),
    "Apple TV+" to ChipColor(Color(0xFFA2AAAD), Color(0xFF000000)),
    "Max" to ChipColor(Color(0xFF5822B4), Color(0xFFFFFFFF)),
    "Hulu" to ChipColor(Color(0xFF1CE783), Color(0xFF000000)),
    "YouTube Premium" to ChipColor(Color(0xFFFF0000), Color(0xFFFFFFFF)),
    "Paramount+" to ChipColor(Color(0xFF0064FF), Color(0xFFFFFFFF)),
    "Discovery+" to ChipColor(Color(0xFF2175D9), Color(0xFFFFFFFF)),
    "MUBI" to ChipColor(Color(0xFF000000), Color(0xFFFFFFFF)),
    "Aha" to ChipColor(Color(0xFFF6C700), Color(0xFF000000)),
    "Hoichoi" to ChipColor(Color(0xFFE72744), Color(0xFFFFFFFF)),
    "Lionsgate Play" to ChipColor(Color(0xFFFF6B00), Color(0xFFFFFFFF)),
    "MX Player" to ChipColor(Color(0xFFFF6C00), Color(0xFFFFFFFF)),
    "Voot" to ChipColor(Color(0xFF6C3CE1), Color(0xFFFFFFFF)),
    "Sun NXT" to ChipColor(Color(0xFFFF4500), Color(0xFFFFFFFF)),
    "ShemarooMe" to ChipColor(Color(0xFFE31837), Color(0xFFFFFFFF)),
    "Kites (EPIC ON)" to ChipColor(Color(0xFF00B4D8), Color(0xFFFFFFFF)),
    "Curiosity Stream" to ChipColor(Color(0xFF1A1A2E), Color(0xFFE94560)),
    "DocuBay" to ChipColor(Color(0xFFFF6B35), Color(0xFFFFFFFF)),
    "Peacock" to ChipColor(Color(0xFF000000), Color(0xFFFFFFFF))
)

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expectedComponentName = "${context.packageName}/${com.example.service.MovieOverlayAccessibilityService::class.java.canonicalName}"
    val enabledServicesSetting = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabledServicesSetting.contains(expectedComponentName)
}

private fun triggerOverlay(context: Context, action: String) {
    try {
        val intent = Intent(context, FloatingOverlayService::class.java).apply {
            this.action = action
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    } catch (e: Exception) {
        Log.e("MainActivity", "Failed to interact with OverlayService: ", e)
    }
}

@Composable
fun RisingCard(
    item: TmdbTarget,
    ratingEntity: MovieRatingEntity?,
    viewModel: MovieViewModel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111118)),
        border = BorderStroke(1.dp, Color(0xFF2A2A3A))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            coil.compose.AsyncImage(
                model = "https://image.tmdb.org/t/p/w342${item.poster_path}",
                contentDescription = item.title ?: item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            WatchlistButton(
                item = item,
                viewModel = viewModel,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xFF0A0A0F).copy(alpha = 0.85f), Color(0xFF0A0A0F))
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StreamingProviderLogo(
                    tmdbId = item.id,
                    isTv = item.media_type == "tv"
                )

                Spacer(modifier = Modifier.width(8.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = item.title ?: item.name ?: "Unknown",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val scoreStr = if (ratingEntity != null) {
                            ratingEntity.imdb.split("/").firstOrNull() ?: String.format(java.util.Locale.US, "%.1f", item.vote_average ?: 8.2)
                        } else {
                            String.format(java.util.Locale.US, "%.1f", item.vote_average ?: 8.2)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⭐", fontSize = 10.sp)
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = scoreStr,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFFFD700),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                            )
                        }

                        val rtScore = ratingEntity?.rottenTomatoes ?: "84%"
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🍅", fontSize = 10.sp)
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = rtScore,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFFF6B6B),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                val typeStr = when {
                    item.media_type == "tv" -> "SERIES"
                    item.genre_ids?.contains(99) == true -> "DOC"
                    else -> "MOVIE"
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = typeStr,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 0.5.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                    )
                }
            }
        }
    }
}

@Composable
fun AcclaimedCard(
    item: TmdbTarget,
    ratingEntity: MovieRatingEntity?,
    viewModel: MovieViewModel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111118)),
        border = BorderStroke(1.dp, Color(0xFF2A2A3A))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            coil.compose.AsyncImage(
                model = "https://image.tmdb.org/t/p/w342${item.poster_path}",
                contentDescription = item.title ?: item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            WatchlistButton(
                item = item,
                viewModel = viewModel,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xFF0A0A0F).copy(alpha = 0.85f), Color(0xFF0A0A0F))
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StreamingProviderLogo(
                    tmdbId = item.id,
                    isTv = item.media_type == "tv"
                )

                Spacer(modifier = Modifier.width(8.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = item.title ?: item.name ?: "Unknown",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val scoreStr = if (ratingEntity != null) {
                            ratingEntity.imdb.split("/").firstOrNull() ?: String.format(java.util.Locale.US, "%.1f", item.vote_average ?: 8.2)
                        } else {
                            String.format(java.util.Locale.US, "%.1f", item.vote_average ?: 8.2)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⭐", fontSize = 10.sp)
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = scoreStr,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFFFD700),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                            )
                        }

                        val rtScore = ratingEntity?.rottenTomatoes ?: "84%"
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🍅", fontSize = 10.sp)
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = rtScore,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFFF6B6B),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                val typeStr = when {
                    item.media_type == "tv" -> "SERIES"
                    item.genre_ids?.contains(99) == true -> "DOC"
                    else -> "MOVIE"
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = typeStr,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 0.5.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                    )
                }
            }
        }
    }
}

@Composable
fun GemCard(
    item: TmdbTarget,
    ratingEntity: MovieRatingEntity?,
    viewModel: MovieViewModel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111118)),
        border = BorderStroke(1.dp, Color(0xFF2A2A3A))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            coil.compose.AsyncImage(
                model = "https://image.tmdb.org/t/p/w342${item.poster_path}",
                contentDescription = item.title ?: item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            WatchlistButton(
                item = item,
                viewModel = viewModel,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xFF0A0A0F).copy(alpha = 0.85f), Color(0xFF0A0A0F))
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StreamingProviderLogo(
                    tmdbId = item.id,
                    isTv = item.media_type == "tv"
                )

                Spacer(modifier = Modifier.width(8.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = item.title ?: item.name ?: "Unknown",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val scoreStr = if (ratingEntity != null) {
                            ratingEntity.imdb.split("/").firstOrNull() ?: String.format(java.util.Locale.US, "%.1f", item.vote_average ?: 8.2)
                        } else {
                            String.format(java.util.Locale.US, "%.1f", item.vote_average ?: 8.2)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⭐", fontSize = 10.sp)
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = scoreStr,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFFFD700),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                            )
                        }

                        val rtScore = ratingEntity?.rottenTomatoes ?: "84%"
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🍅", fontSize = 10.sp)
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = rtScore,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFFF6B6B),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                val typeStr = when {
                    item.media_type == "tv" -> "SERIES"
                    item.genre_ids?.contains(99) == true -> "DOC"
                    else -> "MOVIE"
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = typeStr,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 0.5.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun WatchlistSheet(
    viewModel: MovieViewModel,
    onDismiss: () -> Unit,
    onMovieClick: (MovieRatingEntity) -> Unit
) {
    val watchlist by viewModel.watchlist.collectAsState()
    
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F0F16),
        dragHandle = {
            androidx.compose.material3.BottomSheetDefaults.DragHandle(color = Color(0xFF404055))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "My Watchlist",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "${watchlist.size} items",
                    fontSize = 14.sp,
                    color = Color.LightGray
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (watchlist.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.BookmarkBorder,
                            contentDescription = "Empty",
                            tint = Color.Gray,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Your watchlist is empty",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    items(watchlist) { item ->
                        val ratings = viewModel.mediaRatings.collectAsState().value
                        val rating = ratings[item.tmdbId]

                        LaunchedEffect(item.tmdbId) {
                            val tmdbTarget = com.example.data.TmdbTarget(
                                id = item.tmdbId,
                                title = item.title,
                                name = item.title,
                                poster_path = item.posterPath,
                                media_type = item.mediaType,
                                genre_ids = emptyList(),
                                vote_average = 8.0
                            )
                            viewModel.fetchRatingsForTmdbItem(tmdbTarget, isTv = item.mediaType == "tv")
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF161622))
                                .clickable {
                                    rating?.let { onMovieClick(it) }
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            coil.compose.AsyncImage(
                                model = "https://image.tmdb.org/t/p/w185${item.posterPath}",
                                contentDescription = item.title,
                                modifier = Modifier
                                    .size(width = 50.dp, height = 75.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Crop
                            )
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = item.title,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    StreamingProviderLogo(
                                        tmdbId = item.tmdbId,
                                        isTv = item.mediaType == "tv"
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = item.mediaType.uppercase(java.util.Locale.US),
                                        color = Color.Gray,
                                        fontSize = 11.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("⭐", fontSize = 11.sp)
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = rating?.imdb?.split("/")?.firstOrNull() ?: "Awaiting...",
                                            color = Color(0xFFFFD700),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("🍅", fontSize = 11.sp)
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = rating?.rottenTomatoes ?: "Awaiting...",
                                            color = Color(0xFFFF6B6B),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                            
                            IconButton(
                                onClick = {
                                    val tmdbTarget = com.example.data.TmdbTarget(
                                        id = item.tmdbId,
                                        title = item.title,
                                        name = item.title,
                                        poster_path = item.posterPath,
                                        media_type = item.mediaType,
                                        genre_ids = emptyList(),
                                        vote_average = 8.0
                                    )
                                    viewModel.toggleWatchlist(tmdbTarget)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Remove",
                                    tint = Color.LightGray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
