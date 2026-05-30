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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.MovieRatingEntity
import com.example.network.GeminiClient
import com.example.service.FloatingOverlayService
import com.example.service.OverlayState
import com.example.ui.MovieViewModel
import com.example.ui.SearchUiState
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color(0xFF0D0C0E) // Custom deep cinematic black
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
        // Trigger a UI re-eval for accessibility / overlay service status checks
        _checkTriggers.value = !_checkTriggers.value
    }

    // A state trigger to refresh permission state when coming back from settings
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

    // Check permissions
    var isOverlayGranted by remember(checkTrigger) {
        mutableStateOf(Settings.canDrawOverlays(context))
    }
    var isAccessibilityGranted by remember(checkTrigger) {
        mutableStateOf(isAccessibilityServiceEnabled(context))
    }

    val searchUiState by viewModel.searchUiState.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val activeStreamingApp by OverlayState.currentStreamingApp.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var activeTab by remember { mutableStateOf(0) } // 0: Search & Settings, 1: Platform Hub

    // Ambient glow animation for active streaming tracker status
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                // Highly elegant subtle amber/rose background radial flare
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x19FFC529), Color.Transparent)
                    ),
                    radius = 450.dp.toPx(),
                    center = Offset(size.width / 2f, -100f)
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Beautiful App Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFFFFC529), Color(0xFFE50914))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = "Movie ratings logo",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "MovieRatings AI",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = "Real-time overlay scanner",
                            fontSize = 11.sp,
                            color = Color(0xFF8B8894),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Interactive Quick HUD controller pill
                val overlayActive by OverlayState.isOverlayVisible.collectAsState()
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(30.dp))
                        .background(Color(0xFF131116))
                        .border(1.dp, Color(0xFF222027), RoundedCornerShape(30.dp))
                        .clickable {
                            if (isOverlayGranted) {
                                val isServiceActive = OverlayState.isOverlayVisible.value
                                val action = if (isServiceActive) {
                                    FloatingOverlayService.ACTION_HIDE
                                } else {
                                    FloatingOverlayService.ACTION_SHOW
                                }
                                triggerOverlay(context, action)
                                Toast.makeText(
                                    context,
                                    if (isServiceActive) "Overlay Suspended" else "Overlay Activated",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(context, "Please grant Overlay permission below", Toast.LENGTH_LONG).show()
                            }
                        }
                        .testTag("toggle_overlay_quick")
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (overlayActive) Color(0xFF10B981).copy(alpha = pulseAlpha)
                                else Color(0xFF4A4950)
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (overlayActive) "HUD ONLINE" else "HUD PAUSED",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (overlayActive) Color(0xFF10B981) else Color(0xFF8B8894),
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Elegant Customized Tab Segmented Row Controller
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF121015))
                    .border(1.dp, Color(0xFF1E1C24), RoundedCornerShape(16.dp))
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (activeTab == 0) Color(0xFF1F1C25) else Color.Transparent)
                        .clickable { activeTab = 0 }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Search & Setup Icon",
                            tint = if (activeTab == 0) Color(0xFFFFC529) else Color(0xFF8B8894),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Search & Setup",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (activeTab == 0) Color.White else Color(0xFF8B8894)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (activeTab == 1) Color(0xFF1F1C25) else Color.Transparent)
                        .clickable { activeTab = 1 }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = "Platforms Icon",
                            tint = if (activeTab == 1) Color(0xFFFFC529) else Color(0xFF8B8894),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Catalog Hub",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (activeTab == 1) Color.White else Color(0xFF8B8894)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (activeTab == 0) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // SETUP PERMISSIONS COMPANION CARD
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF121015),
                                contentColor = Color.White
                            ),
                            border = BorderStroke(1.dp, Color(0xFF1E1C24))
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column {
                                        Text(
                                            "Background Scanner Setup",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            "Enable permissions to auto-inject reviews as you browse streaming lists.",
                                            fontSize = 11.sp,
                                            color = Color(0xFF8B8894)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                PermissionStatusRow(
                                    title = "Display Over Other Apps",
                                    desc = "Grants authority to compile HUD feedback bubbles on Prime Video, Netflix, and Jio Hotstar.",
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

                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = Color(0xFF1E1C24))
                                Spacer(modifier = Modifier.height(12.dp))

                                PermissionStatusRow(
                                    title = "Accessibility Auto-Scanner",
                                    desc = "Inspects passive title changes when on media apps to query relevant review aggregates.",
                                    granted = isAccessibilityGranted,
                                    onGrantClick = {
                                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                        context.startActivity(intent)
                                    }
                                )
                            }
                        }
                    }

                    // SEARCH CONTROLLER INPUT BAR
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search title (e.g. Stranger Things)...", color = Color(0xFF63616B), fontSize = 14.sp) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("movie_search_input"),
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFFFC529),
                                    unfocusedBorderColor = Color(0xFF1E1C24),
                                    focusedContainerColor = Color(0xFF121015),
                                    unfocusedContainerColor = Color(0xFF121015),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search icon",
                                        tint = Color(0xFF8B8894)
                                    )
                                }
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Button(
                                modifier = Modifier
                                    .height(56.dp)
                                    .testTag("movie_search_button"),
                                onClick = {
                                    if (searchQuery.isNotBlank()) {
                                        viewModel.searchMovie(searchQuery)
                                        searchQuery = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFFC529),
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text("Fetch", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // ACTIVE DETECTED STREAMING NOTIFICATION
                    activeStreamingApp?.let { app ->
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF0F1513),
                                    contentColor = Color.White
                                ),
                                border = BorderStroke(1.dp, Color(0xFF0D241C))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF142C21)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.TrendingUp,
                                            contentDescription = "Active scanning status",
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column {
                                        Text("LIVE TELEMETRY ACTIVE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981), letterSpacing = 1.sp)
                                        Text("Sensing media inside: $app", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                        }
                    }

                    // INLINE SEARCH PROCESSES OR SUCCESS DETAILS
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
                                        shape = RoundedCornerShape(20.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF121015)),
                                        border = BorderStroke(1.dp, Color(0xFF1E1C24))
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(28.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            CircularProgressIndicator(color = Color(0xFFFFC529), strokeWidth = 3.dp)
                                            Spacer(modifier = Modifier.height(14.dp))
                                            Text(
                                                "Synthesizing public reviews...",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color.White
                                            )
                                            Text(
                                                "Analyzing critics Consensus via Gemini AI...",
                                                fontSize = 11.sp,
                                                color = Color(0xFF8B8894),
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                    }
                                }
                                is SearchUiState.Success -> {
                                    val movie = (searchUiState as SearchUiState.Success).movie
                                    MovieReviewDetailCard(movie = movie, onClose = { viewModel.resetSearchState() })
                                }
                                is SearchUiState.Error -> {
                                    val msg = (searchUiState as SearchUiState.Error).message
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF201314)),
                                        shape = RoundedCornerShape(20.dp),
                                        border = BorderStroke(1.dp, Color(0xFF3F1A1B))
                                    ) {
                                        Column(modifier = Modifier.padding(18.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(imageVector = Icons.Default.Cancel, contentDescription = "Error", tint = Color(0xFFEF5350))
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text("Lookup Interrupted", fontWeight = FontWeight.Bold, color = Color(0xFFEF5350))
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(msg, fontSize = 12.sp, color = Color(0xFFD4C8C8), lineHeight = 18.sp)
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Button(
                                                onClick = { viewModel.resetSearchState() },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3F1A1B)),
                                                shape = RoundedCornerShape(10.dp)
                                            ) {
                                                Text("Acknowledge", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                                else -> {}
                            }
                        }
                    }

                    // HISTORY OF LOCAL RATINGS
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Offline Cache (${searchHistory.size} Items)",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            if (searchHistory.isNotEmpty()) {
                                Text(
                                    "Clear All",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE50914),
                                    modifier = Modifier.clickable { viewModel.clearAllHistory() }
                                )
                            }
                        }
                    }

                    if (searchHistory.isNotEmpty()) {
                        items(searchHistory) { historyItem ->
                            HistoryRatingRow(
                                item = historyItem,
                                onClick = {
                                    viewModel.selectCachedMovie(historyItem)
                                    activeTab = 0
                                },
                                onDelete = { viewModel.deleteHistoryItem(historyItem.id) }
                            )
                        }
                    } else {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF121015))
                                    .border(1.dp, Color(0xFF1E1C24), RoundedCornerShape(16.dp))
                                    .padding(28.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No queries cached offline yet.",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF63616B)
                                )
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            } else {
                // CURATED STREAM CHANNELS LISTING
                CuratedPlatformCatalog(
                    onMovieSelect = { title ->
                        viewModel.searchMovie(title)
                        activeTab = 0
                    }
                )
            }
        }
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
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(if (granted) Color(0x3310B981) else Color(0x33EF5350)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (granted) Color(0xFF10B981) else Color(0xFFEF5350))
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(desc, fontSize = 11.sp, color = Color(0xFF8B8894), lineHeight = 16.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        if (!granted) {
            Button(
                onClick = onGrantClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F1C25)),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFF2C2933))
            ) {
                Text("Configure", fontSize = 11.sp, color = Color(0xFFFFC629), fontWeight = FontWeight.Bold)
            }
        } else {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x3310B981))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "READY",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF10B981),
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
fun MovieReviewDetailCard(
    movie: MovieRatingEntity,
    onClose: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF121015),
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color(0xFF2A2832))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // HEADER BAR
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = movie.title, 
                        fontSize = 24.sp, 
                        fontWeight = FontWeight.ExtraBold, 
                        color = Color.White,
                        lineHeight = 28.sp,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Year: ${movie.year}  |  Director: ${movie.director}",
                        fontSize = 11.sp,
                        color = Color(0xFFFFC629),
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
                        .background(Color(0xFF1F1C25))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close, 
                        contentDescription = "Close detail", 
                        tint = Color.LightGray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // CRITICS RATINGS GRID
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RatingIndicatorWidget(
                    title = "IMDb SCORE", 
                    score = movie.imdb, 
                    logoColor = Color(0xFFFFC629), 
                    icon = Icons.Default.Star,
                    modifier = Modifier.weight(1f)
                )
                RatingIndicatorWidget(
                    title = "TOMATOMETER", 
                    score = movie.rottenTomatoes, 
                    logoColor = Color(0xFFE50914), 
                    icon = Icons.Default.Favorite,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // STREAMING AVAILABILITY
            Text("Streaming Outlets Supported", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8B8894), letterSpacing = 0.5.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val platforms = movie.platforms.split(",").map { it.trim() }.filter { it.isNotEmpty() && it != "None" }
                if (platforms.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1F1C25))
                            .border(1.dp, Color(0xFF2C2933), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("Web / Digital Release Only", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    }
                } else {
                    platforms.forEach { platform ->
                        val color = when (platform) {
                            "Netflix" -> Color(0xFFE50914)
                            "Prime Video" -> Color(0xFF00A8E1)
                            "Hotstar" -> Color(0xFFFFC629)
                            else -> Color(0xFF1F1C25)
                        }
                        val textCol = if (platform == "Hotstar") Color.Black else Color.White
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

            Spacer(modifier = Modifier.height(16.dp))

            // SYNOPSIS
            Text("Cinematic Synopsis", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8B8894), letterSpacing = 0.5.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = movie.synopsis, 
                fontSize = 13.sp, 
                color = Color.White, 
                lineHeight = 20.sp,
                fontWeight = FontWeight.Normal
            )

            Spacer(modifier = Modifier.height(16.dp))

            // GEMINI CRITIC SUMMARIES
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1B191F))
                    .border(1.dp, Color(0xFF2C2933), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.ThumbUp, contentDescription = "Pros", tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Praise Consensus", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981), letterSpacing = 0.5.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(movie.positiveSummary, fontSize = 12.sp, color = Color(0xFFD4C8C8), lineHeight = 18.sp)

                Spacer(modifier = Modifier.height(12.dp))

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

@Composable
fun RatingIndicatorWidget(
    title: String,
    score: String,
    logoColor: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1B191F))
            .border(1.dp, Color(0xFF2C2933), RoundedCornerShape(14.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = title, tint = logoColor, modifier = Modifier.size(12.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(title, fontSize = 9.sp, color = Color(0xFF8B8894), fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(score, fontSize = 18.sp, color = logoColor, fontWeight = FontWeight.ExtraBold)
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121015)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFF1E1C24))
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
                    .background(Color(0xFF1B191F))
                    .border(1.dp, Color(0xFF2C2933), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.imdb.take(3),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFFC629)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(item.genre, fontSize = 11.sp, color = Color(0xFF8B8894))
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1F1C25))
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

@Composable
fun CuratedPlatformCatalog(
    onMovieSelect: (String) -> Unit
) {
    var platformTabIndex by remember { mutableStateOf(0) } // 0: Netflix, 1: Prime Video, 2: Jio Hotstar

    val netflixCatalog = listOf(
        CatalogItem("Stranger Things", "Sci-Fi, Horror • Series", "8.7/10"),
        CatalogItem("Squid Game", "Thriller, Drama • Series", "8.0/10"),
        CatalogItem("The Irishman", "Crime, Drama • Movie", "7.8/10"),
        CatalogItem("Black Mirror", "Sci-Fi • Series", "8.7/10"),
        CatalogItem("Red Notice", "Action, Comedy • Movie", "6.3/10")
    )

    val primeCatalog = listOf(
        CatalogItem("The Boys", "Action, Sci-Fi • Series", "8.7/10"),
        CatalogItem("Inception", "Sci-Fi, Action • Movie", "8.8/10"),
        CatalogItem("The Rings of Power", "Fantasy • Series", "7.0/10"),
        CatalogItem("Road House", "Action, Thriller • Movie", "6.2/10"),
        CatalogItem("Fleabag", "Comedy • Series", "8.7/10")
    )

    val hotstarCatalog = listOf(
        CatalogItem("Sholay", "Classic, Action • Movie", "8.2/10"),
        CatalogItem("Chhichhore", "Drama, Comedy • Movie", "8.3/10"),
        CatalogItem("Loki", "Fantasy, Sci-Fi • Series", "8.2/10"),
        CatalogItem("Chon Chon", "Historical, Action • Movie", "7.9/10"),
        CatalogItem("Special Ops", "Spy, Action • Series", "8.6/10")
    )

    val selectedCatalog = when (platformTabIndex) {
        0 -> netflixCatalog
        1 -> primeCatalog
        else -> hotstarCatalog
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Streaming Libraries",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            "Inspect popular listings to auto-fetch critical intelligence metrics instantly.",
            fontSize = 11.sp,
            color = Color(0xFF8B8894)
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Platform sub tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PlatformBadgeButton("Netflix", active = platformTabIndex == 0, color = Color(0xFFE50914), activeTextColor = Color.White, onClick = { platformTabIndex = 0 }, modifier = Modifier.weight(1f))
            PlatformBadgeButton("Prime Video", active = platformTabIndex == 1, color = Color(0xFF00A8E1), activeTextColor = Color.White, onClick = { platformTabIndex = 1 }, modifier = Modifier.weight(1f))
            PlatformBadgeButton("Jio Hotstar", active = platformTabIndex == 2, color = Color(0xFFFFC629), activeTextColor = Color.Black, onClick = { platformTabIndex = 2 }, modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(selectedCatalog) { movie ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onMovieSelect(movie.title) },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF121015)),
                    border = BorderStroke(1.dp, Color(0xFF1E1C24)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1B191F)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Movie,
                                    contentDescription = "Movie card",
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(movie.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(movie.genre, fontSize = 11.sp, color = Color(0xFF8B8894))
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1B191F))
                                .border(1.dp, Color(0xFF2C2933), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Score logo",
                                tint = Color(0xFFFFC629),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = movie.score,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun PlatformBadgeButton(
    title: String,
    active: Boolean,
    color: Color,
    activeTextColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) color else Color(0xFF121015))
            .border(1.dp, if (active) Color.Transparent else Color(0xFF1E1C24), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) activeTextColor else Color(0xFF8B8894)
        )
    }
}

data class CatalogItem(
    val title: String,
    val genre: String,
    val score: String
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
