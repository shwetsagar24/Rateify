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

    // Home Filters State
    var selectedPlatform by remember { mutableStateOf("All") }
    var selectedType by remember { mutableStateOf("All") }

    // Load defaults if applicable
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("RateifyPrefs", Context.MODE_PRIVATE)
        selectedPlatform = prefs.getString("default_platform_preference", "All") ?: "All"
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
        containerColor = Color(0xFF03030C)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x1F8B2FC9), Color.Transparent)
                        ),
                        radius = 450.dp.toPx(),
                        center = Offset(size.width / 2f, -100f)
                    )
                }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // APP MODULE HEADER ROW
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_rateify_logo),
                            contentDescription = "Rateify AI Logo",
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Rateify AI",
                                fontSize = 21.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                text = "Know before you watch.",
                                fontSize = 13.sp,
                                color = Color(0xFF8A8AB0),
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                                letterSpacing = 0.3.sp
                            )
                        }
                    }

                    // Live Scan System Indicator Pill
                    val isHUDServiceActive by OverlayState.isOverlayVisible.collectAsState()
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isHUDServiceActive) Color(0x2210B981) else Color(0x1A8B8894))
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
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isHUDServiceActive) Color(0xFF10B981) else Color(0xFF8B8894))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isHUDServiceActive) "HUD ACTIVE" else "HUD READY",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isHUDServiceActive) Color(0xFF10B981) else Color(0xFF8B8894),
                            letterSpacing = 0.5.sp
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
                            onPlatformChange = { selectedPlatform = it },
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

    val contentTypesList = listOf("All", "Movies", "TV Series", "Documentaries")

    // Dynamic Filter Implementations
    fun filterList(list: List<TmdbTarget>): List<TmdbTarget> {
        return list.filter { item ->
            // Network Provider Filtering
            val matchesPlatform = if (selectedPlatform == "All") {
                true
            } else {
                val rating = mediaRatings[item.id]
                rating?.platforms?.contains(selectedPlatform, ignoreCase = true) == true
            }
            matchesPlatform
        }
    }

    val filteredAll = filterList(trendingAll)
    val filteredMovies = filterList(trendingMovies)
    val filteredTv = filterList(trendingTv)
    val filteredTopMovies = filterList(topRatedMovies)
    val filteredTopTv = filterList(topRatedTv)
    val filteredDocs = filterList(trendingDocumentaries)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        // 1. Sleek Filter Bar Row Context
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                // Platform Badges Layout (With selected Glow Shadow)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(platformChipsList) { platform ->
                        val isSelected = selectedPlatform == platform
                        val specColor = platformColors[platform] ?: ChipColor(Color(0xFF17172B), Color.White)
                        
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) specColor.bg else Color(0xFF10101C))
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) specColor.bg else Color(0xFF1F1F35),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .then(
                                    if (isSelected) {
                                        Modifier.shadow(elevation = 8.dp, shape = RoundedCornerShape(20.dp), ambientColor = specColor.bg, spotColor = specColor.bg)
                                    } else Modifier
                                )
                                .clickable { onPlatformChange(platform) }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = platform,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) specColor.text else Color(0xFF8B8894)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(10.dp))

                // Content Types Selection row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    contentTypesList.forEach { type ->
                        val isSelected = selectedType == type
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFF8B2FC9) else Color(0xFF0C0C14))
                                .border(1.dp, if (isSelected) Color(0xFF8B2FC9) else Color(0xFF1C1C2A), RoundedCornerShape(8.dp))
                                .clickable { onTypeChange(type) }
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = type,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else Color(0xFF9491A0)
                            )
                        }
                    }
                }
            }
        }

        // 2. HERO BANNER - #1 TRENDING TITLE (Large wide card)
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
                        .height(230.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0E0E18))
                        .border(1.dp, Color(0xFF1E1E2D), RoundedCornerShape(16.dp))
                        .clickable { rating?.let { onMovieClick(it) } }
                ) {
                    // Poster Backdrop
                    coil.compose.AsyncImage(
                        model = "https://image.tmdb.org/t/p/w780${heroItem.poster_path}",
                        contentDescription = "Backdrop",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Cinematic Gradient Shade Overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f))
                                )
                            )
                    )

                    // Description text items
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFE50914))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("#1 TRENDING", fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = TmdbClient.mapGenres(heroItem.genre_ids),
                                fontSize = 10.sp,
                                color = Color(0xFFA2AAAD),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        Text(
                            text = heroItem.title ?: heroItem.name ?: "Trending Title",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            lineHeight = 26.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Render ratings on hero if loaded
                            if (rating != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("⭐", fontSize = 10.sp)
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(rating.imdb, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("🍅", fontSize = 10.sp)
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(rating.rottenTomatoes, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                if (rating.tomatoUserMeter != "N/A") {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("🍿", fontSize = 10.sp)
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(rating.tomatoUserMeter, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            } else {
                                Text("Analyzing public reviews...", fontSize = 10.sp, color = Color.LightGray, fontStyle = FontStyle.Italic)
                            }
                        }
                    }
                }
            }
        }

        // 3. TOP 10 TRENDING NOW - CAROUSEL WITH OVERLAPPED NETFLIX RANK NUMBERS
        if (filteredAll.isNotEmpty() && selectedType == "All") {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Top 10 Trending Now",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        modifier = Modifier.padding(start = 16.dp, bottom = 10.dp)
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(22.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(filteredAll.take(10)) { index, item ->
                            LaunchedEffect(item.id) {
                                viewModel.fetchRatingsForTmdbItem(item, isTv = item.media_type == "tv")
                            }
                            val rating = mediaRatings[item.id]

                            Box(
                                modifier = Modifier
                                    .width(135.dp)
                                    .height(170.dp)
                            ) {
                                // Netflix Rank overlay behind the card bottom-left
                                Text(
                                    text = "${index + 1}",
                                    fontSize = 105.sp,
                                    fontStyle = FontStyle.Italic,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF03030C),
                                    style = TextStyle(
                                        shadow = Shadow(
                                            color = Color(0xFF423C56),
                                            offset = Offset(2f, 2f),
                                            blurRadius = 8f
                                        )
                                    ),
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .offset(x = (-14).dp, y = 20.dp)
                                )

                                TrendingCard(
                                    item = item,
                                    ratingEntity = rating,
                                    modifier = Modifier
                                        .width(98.dp)
                                        .height(148.dp)
                                        .align(Alignment.BottomEnd)
                                ) {
                                    rating?.let { onMovieClick(it) }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. TOP RATED MOVIES SECTION
        if (filteredTopMovies.isNotEmpty() && (selectedType == "All" || selectedType == "Movies")) {
            item {
                CarouselRowSection(
                    title = "Top Rated Movies",
                    items = filteredTopMovies,
                    isTv = false,
                    viewModel = viewModel,
                    mediaRatings = mediaRatings,
                    onMovieClick = onMovieClick
                )
            }
        }

        // 5. TOP RATED TV SERIES SECTION
        if (filteredTopTv.isNotEmpty() && (selectedType == "All" || selectedType == "TV Series")) {
            item {
                CarouselRowSection(
                    title = "Top Rated Series",
                    items = filteredTopTv,
                    isTv = true,
                    viewModel = viewModel,
                    mediaRatings = mediaRatings,
                    onMovieClick = onMovieClick
                )
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
                    onMovieClick = onMovieClick
                )
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
    onMovieClick: (MovieRatingEntity) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            modifier = Modifier.padding(start = 16.dp, bottom = 10.dp)
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
                    modifier = Modifier.width(110.dp).height(165.dp)
                ) {
                    rating?.let { onMovieClick(it) }
                }
            }
        }
    }
}

@Composable
fun TrendingCard(
    item: TmdbTarget,
    ratingEntity: MovieRatingEntity?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10101C)),
        border = BorderStroke(1.dp, Color(0xFF222234))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Poster Cover
            coil.compose.AsyncImage(
                model = "https://image.tmdb.org/t/p/w342${item.poster_path}",
                contentDescription = item.title ?: item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Content Type Header Tag
            val typeStr = when {
                item.media_type == "tv" -> "SERIES"
                item.genre_ids?.contains(99) == true -> "DOC"
                else -> "MOVIE"
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(typeStr, fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            // Bottom Gradient Backdrop
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(65.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f))
                        )
                    )
            )

            // Bottom Core Ratings Row
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
            ) {
                Text(
                    text = item.title ?: item.name ?: "Unknown",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (ratingEntity != null) {
                        // IMDb Rating
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⭐", fontSize = 8.sp)
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = ratingEntity.imdb.split("/").firstOrNull() ?: "N/A",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }

                        // RT Critic Code
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🍅", fontSize = 8.sp)
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = ratingEntity.rottenTomatoes,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                    } else {
                        Text(
                            text = "Awaiting...",
                            fontSize = 7.sp,
                            color = Color.LightGray,
                            fontStyle = FontStyle.Italic
                        )
                    }
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
            .padding(horizontal = 16.dp)
    ) {
        OutlinedTextField(
            value = queryText,
            onValueChange = { queryText = it },
            placeholder = { Text("Search title manually...", color = Color(0xFF6F6A85), fontSize = 14.sp) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .testTag("manual_search_field"),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF8B2FC9),
                unfocusedBorderColor = Color(0xFF2C2C3E),
                focusedContainerColor = Color(0xFF10101C),
                unfocusedContainerColor = Color(0xFF10101C),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search Icon",
                    tint = Color(0xFF8B8894)
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
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF10101C)),
                                border = BorderStroke(1.dp, Color(0xFF1B1B26))
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(color = Color(0xFF8B2FC9), strokeWidth = 3.dp)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("Analyzing ratings metrics...", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("Gemini AI synthesizing parent guidance reports...", fontSize = 10.sp, color = Color(0xFF8B8894), modifier = Modifier.padding(top = 4.dp))
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
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // TOP SECTION: Interactive Manual Search Bar (Change 5)
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF11111E)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF222234))
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
                            focusedBorderColor = Color(0xFF8B2FC9),
                            unfocusedBorderColor = Color(0xFF2C2C3E),
                            focusedContainerColor = Color(0xFF030308),
                            unfocusedContainerColor = Color(0xFF030308),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        trailingIcon = {
                            IconButton(onClick = {
                                if (configQueryText.isNotBlank()) {
                                    viewModel.searchMovie(configQueryText)
                                }
                            }) {
                                Icon(Icons.Default.ArrowForward, "Go", tint = Color(0xFF8B2FC9))
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
                colors = CardDefaults.cardColors(containerColor = Color(0xFF10101C)),
                border = BorderStroke(1.dp, Color(0xFF212130)),
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
                    HorizontalDivider(color = Color(0xFF212132))
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
                colors = CardDefaults.cardColors(containerColor = Color(0xFF10101C)),
                border = BorderStroke(1.dp, Color(0xFF212130)),
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
                            focusedBorderColor = Color(0xFF8B2FC9),
                            unfocusedBorderColor = Color(0xFF212132)
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
                            focusedBorderColor = Color(0xFF8B2FC9),
                            unfocusedBorderColor = Color(0xFF212132)
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
                            focusedBorderColor = Color(0xFF8B2FC9),
                            unfocusedBorderColor = Color(0xFF212132)
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
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B2FC9), contentColor = Color.White),
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
                colors = CardDefaults.cardColors(containerColor = Color(0xFF10101C)),
                border = BorderStroke(1.dp, Color(0xFF212130)),
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
                                    .background(if (prefPlatformOfUser == opt) Color(0xFF8B2FC9) else Color(0xFF17172B))
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

        // HUD TOGGLE SWITCH EXTRA
        item {
            val isHUDActive by OverlayState.isOverlayVisible.collectAsState()
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF10101C)),
                border = BorderStroke(1.dp, Color(0xFF212130)),
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
                            checkedThumbColor = Color(0xFF8B2FC9),
                            checkedTrackColor = Color(0xFF251A31),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color(0xFF17172B)
                        )
                    )
                }
            }
        }

        // THEME DETAILS
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF10101C)),
                border = BorderStroke(1.dp, Color(0xFF212130)),
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
                            .background(Color(0xFF8B2FC9).copy(alpha = 0.1f))
                            .border(1.dp, Color(0xFF8B2FC9), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("MANDATORY", fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF8B2FC9))
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
        containerColor = Color(0xFF03030A),
        tonalElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF141421), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
    ) {
        NavigationBarItem(
            selected = activeTab == 0,
            onClick = { onTabSelect(0) },
            icon = { Icon(Icons.Default.Home, "Home", modifier = Modifier.size(22.dp)) },
            label = { Text("Home", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF8B2FC9),
                selectedTextColor = Color.White,
                unselectedIconColor = Color(0xFF535165),
                unselectedTextColor = Color(0xFF535165),
                indicatorColor = Color(0xFF0D0D19)
            )
        )
        NavigationBarItem(
            selected = activeTab == 1,
            onClick = { onTabSelect(1) },
            icon = { Icon(Icons.Default.Search, "Search", modifier = Modifier.size(22.dp)) },
            label = { Text("Search", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF8B2FC9),
                selectedTextColor = Color.White,
                unselectedIconColor = Color(0xFF535165),
                unselectedTextColor = Color(0xFF535165),
                indicatorColor = Color(0xFF0D0D19)
            )
        )
        NavigationBarItem(
            selected = activeTab == 2,
            onClick = { onTabSelect(2) },
            icon = { Icon(Icons.Default.Settings, "Settings", modifier = Modifier.size(22.dp)) },
            label = { Text("Settings", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF8B2FC9),
                selectedTextColor = Color.White,
                unselectedIconColor = Color(0xFF535165),
                unselectedTextColor = Color(0xFF535165),
                indicatorColor = Color(0xFF0D0D19)
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
                        color = Color(0xFF8B2FC9),
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = movie.genre,
                        fontSize = 11.sp,
                        color = Color(0xFF8B8894),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E1E2F))
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
                    logoColor = Color(0xFF8B2FC9),
                    icon = Icons.Default.Star,
                    modifier = if (rtSource == com.example.network.Source.UNAVAILABLE) Modifier.fillMaxWidth() else Modifier.weight(1f)
                )

                if (rtSource != com.example.network.Source.UNAVAILABLE) {
                    val scoreColor = if (rtSource == com.example.network.Source.GEMINI) Color(0xFFE8003D) else Color(0xFF2175D9)
                    val label = if (rtSource == com.example.network.Source.GEMINI) "Tomatometer" else "TMDb Score"
                    
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF0C0C14))
                            .border(1.dp, Color(0xFF222234), RoundedCornerShape(14.dp))
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
                                color = Color(0xFF8B8894),
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
                                status.contains("Certified", ignoreCase = true) -> Triple(Color(0xFF27AE60), "Certified Fresh", "🏆")
                                status.contains("Fresh", ignoreCase = true) -> Triple(Color(0xFFE8003D), "Fresh", "🍅")
                                status.contains("Rotten", ignoreCase = true) -> Triple(Color(0xFF7F8C8D), "Rotten", "🦠")
                                else -> Triple(Color(0xFFE8003D), status.ifBlank { "Fresh" }, "🍅")
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
                    val accentColor = if (gap > 0) Color(0xFF4CAF50) else Color(0xFF8B2FC9)
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
                Text("Streaming Outlets Available", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8B8894), letterSpacing = 0.5.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val providers = movie.platforms.split(",").map { it.trim() }.filter { it.isNotEmpty() && it != "N/A" && it != "None" }
                    if (providers.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF10101C))
                                .border(1.dp, Color(0xFF222234), RoundedCornerShape(8.dp))
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
                                "Zee5" -> Color(0xFF8B2FC9)
                                else -> Color(0xFF1D1B2A)
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
                Text("Cinematic Synopsis", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8B8894), letterSpacing = 0.5.sp)
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
                        .background(Color(0xFF12121E))
                        .border(1.dp, Color(0xFF212130), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Rateify AI Consensus summaries", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8B2FC9))
                    
                    if (movie.criticConsensus != "N/A" && movie.criticConsensus.isNotBlank()) {
                        Row {
                            Icon(Icons.Default.Newspaper, null, tint = Color(0xFFE50914), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Critic Consensus (RT Critics)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8B8894))
                                Text(movie.criticConsensus, fontSize = 11.sp, color = Color.LightGray, lineHeight = 16.sp)
                            }
                        }
                    }

                    if (movie.audienceConsensus != "N/A" && movie.audienceConsensus.isNotBlank()) {
                        Row {
                            Icon(Icons.Default.People, null, tint = Color(0xFF2196F3), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("General Audience (IMDb/RT Users)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8B8894))
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
                    .background(Color(0xFF12121E))
                    .border(1.dp, Color(0xFF212130), RoundedCornerShape(14.dp))
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
