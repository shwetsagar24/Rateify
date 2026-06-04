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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
        
        // Initialize custom API Key from persistent local SharedPreferences storage
        val sharedPrefs = getSharedPreferences("RateifyPrefs", MODE_PRIVATE)
        com.example.network.GeminiClient.customApiKey = sharedPrefs.getString("gemini_api_key", null)

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
    var showSettingsDialog by remember { mutableStateOf(false) }

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
                            text = "Rateify AI",
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
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

                    // Settings Icon
                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF131116))
                            .border(1.dp, Color(0xFF222027), CircleShape)
                            .testTag("open_settings_icon")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
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
                    val isSetupComplete = isOverlayGranted && isAccessibilityGranted
                    if (!isSetupComplete) {
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
                                        desc = "Grants authority to compile HUD feedback bubbles on Prime Video, Netflix, Jio Hotstar, SonyLIV, and ZEE5.",
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
                    }

                    // GEMINI API KEY CONFIGURATION CARD
                    item {
                        var isEditingKey by remember { mutableStateOf(false) }
                        val currentSavedKey = remember { mutableStateOf(com.example.network.GeminiClient.customApiKey ?: "") }
                        var tempKeyText by remember { mutableStateOf(currentSavedKey.value) }
                        var isPasswordVisible by remember { mutableStateOf(false) }

                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("gemini_key_card"),
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
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VpnKey,
                                        contentDescription = "Gemini Key Icon",
                                        tint = Color(0xFFFFC529),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        "Gemini AI API Key",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Configure your personal Gemini API key to query high-fidelity critic ratings, summaries, and platform details. Alternatively, configure 'GEMINI_API_KEY' in the AI Studio Secrets panel.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF8B8894)
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                if (isEditingKey) {
                                    OutlinedTextField(
                                        value = tempKeyText,
                                        onValueChange = { tempKeyText = it },
                                        placeholder = { Text("AIzaSy...", color = Color(0xFF63616B), fontSize = 13.sp) },
                                        singleLine = true,
                                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                        modifier = Modifier.fillMaxWidth().testTag("gemini_key_input"),
                                        shape = RoundedCornerShape(12.dp),
                                        trailingIcon = {
                                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                                Icon(
                                                    imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                    contentDescription = "Toggle Key Visibility",
                                                    tint = Color(0xFF8B8894)
                                                )
                                            }
                                        },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFFFFC529),
                                            unfocusedBorderColor = Color(0xFF1E1C24),
                                            focusedContainerColor = Color(0xFF0D0C0E),
                                            unfocusedContainerColor = Color(0xFF0D0C0E),
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(
                                            onClick = {
                                                isEditingKey = false
                                                tempKeyText = currentSavedKey.value
                                            }
                                        ) {
                                            Text("Cancel", color = Color(0xFF8B8894))
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = {
                                                val saved = tempKeyText.trim()
                                                val sharedPrefs = context.getSharedPreferences("RateifyPrefs", Context.MODE_PRIVATE)
                                                sharedPrefs.edit().putString("gemini_api_key", saved.ifBlank { null }).apply()
                                                com.example.network.GeminiClient.customApiKey = saved.ifBlank { null }
                                                currentSavedKey.value = saved
                                                isEditingKey = false
                                                Toast.makeText(context, "Gemini API Key Saved!", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC529), contentColor = Color.Black),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("Save & Connect", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (currentSavedKey.value.isNotBlank() || (BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY")) Color(0xFF10B981)
                                                        else Color(0xFFEF4444)
                                                    )
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (currentSavedKey.value.isNotBlank()) {
                                                    "Custom Key Connected"
                                                } else if (BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY") {
                                                    "AI Studio Key Linked"
                                                } else {
                                                    "Key Missing (Fallback Active)"
                                                },
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (currentSavedKey.value.isNotBlank() || (BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY")) Color(0xFF10B981)
                                                else Color(0xFFEF4444)
                                            )
                                        }

                                        Row {
                                            if (currentSavedKey.value.isNotBlank()) {
                                                IconButton(
                                                    onClick = {
                                                        val sharedPrefs = context.getSharedPreferences("RateifyPrefs", Context.MODE_PRIVATE)
                                                        sharedPrefs.edit().remove("gemini_api_key").apply()
                                                        com.example.network.GeminiClient.customApiKey = null
                                                        currentSavedKey.value = ""
                                                        tempKeyText = ""
                                                        Toast.makeText(context, "Custom Key Cleared!", Toast.LENGTH_SHORT).show()
                                                    }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = "Delete key",
                                                        tint = Color(0xFFEF4444)
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = { isEditingKey = true }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Edit key",
                                                    tint = Color(0xFFFFC529)
                                                )
                                            }
                                        }
                                    }
                                }
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
                                "Recent Searches",
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
                                    "No recent searches yet.",
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

    if (showSettingsDialog) {
        val localPrefs = context.getSharedPreferences("RateifyPrefs", Context.MODE_PRIVATE)
        var tempKeyText by remember { mutableStateOf(com.example.network.GeminiClient.customApiKey ?: "") }
        var isPasswordVisible by remember { mutableStateOf(false) }
        var isEditingKey by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings Icon",
                        tint = Color(0xFFFFC529),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "System Settings",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Configure your Real-Time Overlay scanning permissions and Gemini AI key integrations in one integrated control desk.",
                        color = Color(0xFF8B8894),
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )

                    // Card 1: Permissions Integration
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF1B191F),
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, Color(0xFF2C2933))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Background Scanner Setup",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Required to scan active streaming content:",
                                fontSize = 10.sp,
                                color = Color(0xFF8B8894)
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            PermissionStatusRow(
                                title = "Display Over Other Apps",
                                desc = "Required to render the rating overlay feedback bubbles over other video services.",
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
                            HorizontalDivider(color = Color(0xFF2C2933))
                            Spacer(modifier = Modifier.height(10.dp))

                            PermissionStatusRow(
                                title = "Accessibility Scanner",
                                desc = "Required to read active movie/show titles in your favorite streaming apps.",
                                granted = isAccessibilityGranted,
                                onGrantClick = {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    context.startActivity(intent)
                                }
                            )
                        }
                    }

                    // Card 2: Gemini API Key Setup
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF1B191F),
                            contentColor = Color.White
                        ),
                        border = BorderStroke(1.dp, Color(0xFF2C2933))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VpnKey,
                                    contentDescription = "Key Icon",
                                    tint = Color(0xFFFFC529),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Gemini AI API Key",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Configure your custom key to unlock critic consensus summaries.",
                                fontSize = 10.sp,
                                color = Color(0xFF8B8894)
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            if (isEditingKey) {
                                OutlinedTextField(
                                    value = tempKeyText,
                                    onValueChange = { tempKeyText = it },
                                    placeholder = { Text("AIzaSy...", color = Color(0xFF63616B), fontSize = 12.sp) },
                                    singleLine = true,
                                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    trailingIcon = {
                                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                            Icon(
                                                imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = "Toggle Visibility",
                                                tint = Color(0xFF8B8894),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFFFFC529),
                                        unfocusedBorderColor = Color(0xFF2C2933),
                                        focusedContainerColor = Color(0xFF0D0C0E),
                                        unfocusedContainerColor = Color(0xFF0D0C0E),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = { isEditingKey = false }) {
                                        Text("Cancel", color = Color(0xFF8B8894), fontSize = 12.sp)
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Button(
                                        onClick = {
                                            val saved = tempKeyText.trim()
                                            localPrefs.edit().putString("gemini_api_key", saved.ifBlank { null }).apply()
                                            com.example.network.GeminiClient.customApiKey = saved.ifBlank { null }
                                            isEditingKey = false
                                            Toast.makeText(context, "API Key Saved!", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC529), contentColor = Color.Black),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Save", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val hasKey = com.example.network.GeminiClient.customApiKey.let { !it.isNullOrBlank() } || (BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY")
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(if (hasKey) Color(0xFF10B981) else Color(0xFFEF4444))
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (com.example.network.GeminiClient.customApiKey.let { !it.isNullOrBlank() }) "Custom Key Connected" else if (hasKey) "AI Studio Key Linked" else "Key Missing",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (hasKey) Color(0xFF10B981) else Color(0xFFEF4444)
                                        )
                                    }
                                    IconButton(
                                        onClick = { isEditingKey = true },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Key",
                                            tint = Color(0xFFFFC529),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showSettingsDialog = false }
                ) {
                    Text("Close", color = Color(0xFFFFC529), fontWeight = FontWeight.ExtraBold)
                }
            },
            containerColor = Color(0xFF121015),
            shape = RoundedCornerShape(24.dp)
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
                        val trimmed = platform.trim()
                        val color = when (trimmed) {
                            "Netflix" -> Color(0xFFE50914)
                            "Prime Video" -> Color(0xFF00A8E1)
                            "Hotstar", "Jio Hotstar" -> Color(0xFFFFC629)
                            "SonyLIV" -> Color(0xFFE25C3E)
                            "ZEE5" -> Color(0xFF8E24AA)
                            else -> Color(0xFF1F1C25)
                        }
                        val textCol = if (trimmed == "Hotstar" || trimmed == "Jio Hotstar") Color.Black else Color.White
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
    var selectedPlatform by remember { mutableStateOf("All") }
    var selectedType by remember { mutableStateOf("All") }

    val allCatalog = listOf(
        // NETFLIX
        CatalogItem("Stranger Things", "Sci-Fi, Supernatural, Mystery", "Series", "8.7/10", "Netflix"),
        CatalogItem("Squid Game", "Survival, Thriller, Drama", "Series", "8.0/10", "Netflix"),
        CatalogItem("The Irishman", "Crime, Biographical, Drama", "Movie", "7.8/10", "Netflix"),
        CatalogItem("Black Mirror", "Sci-Fi, Satire, Anthology", "Series", "8.7/10", "Netflix"),
        CatalogItem("Red Notice", "Action, Comedy, Heist", "Movie", "6.3/10", "Netflix"),
        CatalogItem("The Last Dance", "Sports, History, Biography", "Documentary", "9.1/10", "Netflix"),
        CatalogItem("Our Planet", "Nature, Science, Environment", "Documentary", "9.3/10", "Netflix"),
        CatalogItem("Glass Onion: Knives Out", "Mystery, Comedy, Crime", "Movie", "7.1/10", "Netflix"),

        // PRIME VIDEO
        CatalogItem("The Boys", "Action, Dark Comedy, Superhero", "Series", "8.7/10", "Prime Video"),
        CatalogItem("Inception", "Sci-Fi, Heist, Suspense", "Movie", "8.8/10", "Prime Video"),
        CatalogItem("The Rings of Power", "Fantasy, Adventure, Drama", "Series", "7.0/10", "Prime Video"),
        CatalogItem("Road House", "Action, Thriller, Combat", "Movie", "6.2/10", "Prime Video"),
        CatalogItem("Fleabag", "Comedy, Drama, Romantic", "Series", "8.7/10", "Prime Video"),
        CatalogItem("Val", "Biographical, Arts, Cinema", "Documentary", "7.6/10", "Prime Video"),
        CatalogItem("One Child Nation", "History, Politics, Investigative", "Documentary", "7.5/10", "Prime Video"),
        CatalogItem("Interstellar", "Sci-Fi, Space Travel, Drama", "Movie", "8.7/10", "Prime Video"),

        // JIO HOTSTAR
        CatalogItem("Sholay", "Classic Action, Western, Drama", "Movie", "8.2/10", "Jio Hotstar"),
        CatalogItem("Chhichhore", "Drama, Comedy, Inspiring", "Movie", "8.3/10", "Jio Hotstar"),
        CatalogItem("Loki", "Fantasy, Sci-Fi, Adventure", "Series", "8.2/10", "Jio Hotstar"),
        CatalogItem("Special Ops", "Spy, Thriller, Action", "Series", "8.6/10", "Jio Hotstar"),
        CatalogItem("Free Solo", "Extreme Sports, Adventure", "Documentary", "8.1/10", "Jio Hotstar"),
        CatalogItem("The Beatles: Get Back", "Music, Bio, Historical", "Documentary", "9.0/10", "Jio Hotstar"),
        CatalogItem("Guardians of the Galaxy 3", "Sci-Fi, Comedy, Adventure", "Movie", "7.9/10", "Jio Hotstar"),

        // SONYLIV
        CatalogItem("Scam 1992", "Drama, Financial Crime, Biographical", "Series", "9.3/10", "SonyLIV"),
        CatalogItem("Gullak", "Comedy, Family, Heartwarming", "Series", "9.1/10", "SonyLIV"),
        CatalogItem("Rocket Boys", "Drama, Biography, Science", "Series", "8.9/10", "SonyLIV"),
        CatalogItem("Undekhi", "Crime, Thriller, Suspense", "Series", "7.9/10", "SonyLIV"),
        CatalogItem("Maharani", "Political, Drama, Women empowerment", "Series", "7.9/10", "SonyLIV"),
        CatalogItem("Sachin: A Billion Dreams", "Sports, Biographical, History", "Documentary", "8.5/10", "SonyLIV"),

        // ZEE5
        CatalogItem("Sunflower", "Comedy, Crime, Mystery", "Series", "8.0/10", "ZEE5"),
        CatalogItem("Sirf Ek Bandaa Kaafi Hai", "Drama, Courtroom, Battle", "Movie", "8.9/10", "ZEE5"),
        CatalogItem("Taj: Divided by Blood", "History, Royal Drama, War", "Series", "7.0/10", "ZEE5"),
        CatalogItem("Pitchers", "Drama, Comedy, Startup life", "Series", "9.1/10", "ZEE5"),
        CatalogItem("Bob Biswas", "Thriller, Crime, Mystery", "Movie", "6.7/10", "ZEE5"),
        CatalogItem("Kargil: Valour of Army", "Military, Biography, Patriotic", "Documentary", "8.2/10", "ZEE5")
    )

    val platforms = listOf("All", "Netflix", "Prime Video", "Jio Hotstar", "SonyLIV", "ZEE5")
    val types = listOf("All", "Movie", "Series", "Documentary")

    val filteredCatalog = allCatalog.filter { item ->
        val matchesPlatform = selectedPlatform == "All" || item.platform == selectedPlatform
        val matchesType = selectedType == "All" || item.type == selectedType
        matchesPlatform && matchesType
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Trending Catalog Hub",
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White
        )
        Text(
            text = "Explore trending movies, series, and documentaries dynamically sorted across major networks.",
            fontSize = 11.sp,
            color = Color(0xFF8B8894),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Platform Filter Row
        Text(
            text = "NETWORK OUTLETS",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFFC629),
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(platforms) { platform ->
                val active = selectedPlatform == platform
                val activeColor = when (platform) {
                    "Netflix" -> Color(0xFFE50914)
                    "Prime Video" -> Color(0xFF00A8E1)
                    "Jio Hotstar" -> Color(0xFFFFC629)
                    "SonyLIV" -> Color(0xFFE25C3E)
                    "ZEE5" -> Color(0xFF8E24AA)
                    else -> Color(0xFFFFC529)
                }
                val activeTextCol = if (platform == "Jio Hotstar" || (platform == "All" && active)) Color.Black else Color.White
                PlatformBadgeButton(
                    title = platform,
                    active = active,
                    color = activeColor,
                    activeTextColor = activeTextCol,
                    onClick = { selectedPlatform = platform }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Content Type Filter Row
        Text(
            text = "CONTENT TYPE",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFFC629),
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(types) { cType ->
                val active = selectedType == cType
                val label = when (cType) {
                    "Series" -> "TV Series"
                    "Movie" -> "Movies"
                    "Documentary" -> "Documentaries"
                    else -> "All Types"
                }
                PlatformBadgeButton(
                    title = label,
                    active = active,
                    color = Color(0xFF1F1C25),
                    activeTextColor = Color(0xFFFFC529),
                    onClick = { selectedType = cType }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (filteredCatalog.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF121015))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No matching titles found in this filter combination.", color = Color(0xFF8B8894), fontSize = 12.sp)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredCatalog) { movie ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onMovieSelect(movie.title) },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B191F)),
                        border = BorderStroke(1.dp, Color(0xFF2C2933)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF2C2933)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val icon = when (movie.type) {
                                            "Movie" -> Icons.Default.Movie
                                            "Series" -> Icons.Default.Tv
                                            else -> Icons.Default.TrendingUp
                                        }
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = "Movie card logo",
                                            tint = Color.LightGray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = movie.title,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0xFF2C2933))
                                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = movie.type.uppercase(),
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFFFC629)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${movie.genre}  •  On ${movie.platform}",
                                            fontSize = 11.sp,
                                            color = Color(0xFF8B8894)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Rotten Tomatoes styled rating stats
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                val baseNum = movie.score.replace("/10", "").toDoubleOrNull() ?: 7.5
                                val criticPercentage = (baseNum * 10 + 6).coerceIn(40.0, 99.0).toInt()
                                val audiencePercentage = (baseNum * 10 + 2).coerceIn(45.0, 98.0).toInt()

                                // Critics (Tomato)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(if (criticPercentage >= 60) Color(0xFFFF385C) else Color(0xFF4CAF50)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(4.dp)
                                                .clip(CircleShape)
                                                .background(Color.White)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "$criticPercentage%",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Tomatometer", fontSize = 10.sp, color = Color(0xFF8B8894))
                                }

                                // Audience (Popcorn)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                                            .background(Color(0xFFFFC629)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(5.dp)
                                                .align(Alignment.TopCenter)
                                                .background(Color(0xFFE50914))
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "$audiencePercentage%",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Audience", fontSize = 10.sp, color = Color(0xFF8B8894))
                                }
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
            .padding(horizontal = 14.dp, vertical = 10.dp),
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
    val type: String, // "Movie", "Series", "Documentary"
    val score: String,
    val platform: String
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
