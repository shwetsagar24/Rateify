const fs = require('fs');

let content = fs.readFileSync('app/src/main/java/com/example/MainActivity.kt', 'utf8');

const newComponents = `
data class ChipColor(
    val bg: Color,
    val text: Color,
    val border: Color = Color.Transparent
)

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

@Composable
fun CuratedPlatformCatalog(
    onMovieSelect: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
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

        // JIOHOTSTAR
        CatalogItem("Sholay", "Classic Action, Western, Drama", "Movie", "8.2/10", "JioHotstar"),
        CatalogItem("Chhichhore", "Drama, Comedy, Inspiring", "Movie", "8.3/10", "JioHotstar"),
        CatalogItem("Loki", "Fantasy, Sci-Fi, Adventure", "Series", "8.2/10", "JioHotstar"),

        // SONYLIV
        CatalogItem("Scam 1992", "Drama, Financial Crime, Biographical", "Series", "9.3/10", "SonyLIV"),
        CatalogItem("Gullak", "Comedy, Family, Heartwarming", "Series", "9.1/10", "SonyLIV"),

        // ZEE5
        CatalogItem("Sunflower", "Comedy, Crime, Mystery", "Series", "8.0/10", "Zee5"),
        CatalogItem("Sirf Ek Bandaa Kaafi Hai", "Drama, Courtroom, Battle", "Movie", "8.9/10", "Zee5"),
        
        // DISNEY+ HOTSTAR
        CatalogItem("The Mandalorian", "Sci-Fi, Adventure", "Series", "8.7/10", "Disney+ Hotstar"),
        CatalogItem("Hamilton", "Musical, History", "Movie", "8.4/10", "Disney+ Hotstar"),

        // APPLE TV+
        CatalogItem("Ted Lasso", "Comedy, Sports, Heartwarming", "Series", "8.8/10", "Apple TV+"),
        CatalogItem("Severance", "Sci-Fi, Thriller, Mystery", "Series", "8.7/10", "Apple TV+"),

        // MAX
        CatalogItem("Succession", "Drama, Satire", "Series", "8.9/10", "Max"),
        CatalogItem("The Last of Us", "Action, Drama, Sci-Fi", "Series", "8.8/10", "Max"),

        // HULU
        CatalogItem("The Bear", "Comedy, Drama", "Series", "8.6/10", "Hulu"),
        CatalogItem("Only Murders in the Building", "Comedy, Crime, Mystery", "Series", "8.1/10", "Hulu"),

        // YOUTUBE PREMIUM
        CatalogItem("Cobra Kai", "Action, Comedy, Drama", "Series", "8.5/10", "YouTube Premium"),
        
        // PARAMOUNT+
        CatalogItem("Yellowstone", "Drama, Western", "Series", "8.7/10", "Paramount+"),
        
        // DISCOVERY+
        CatalogItem("Planet Earth II", "Documentary, Nature", "Documentary", "9.5/10", "Discovery+"),
        
        // MUBI
        CatalogItem("Perfect Days", "Drama", "Movie", "7.9/10", "MUBI"),
        
        // AHA
        CatalogItem("Telugu Drama", "Action, Drama", "Movie", "7.5/10", "Aha"),
        
        // HOICHOI
        CatalogItem("Byomkesh", "Detective, Mystery", "Series", "8.1/10", "Hoichoi"),
        
        // LIONSGATE PLAY
        CatalogItem("John Wick: Chapter 4", "Action, Thriller", "Movie", "7.7/10", "Lionsgate Play"),
        
        // MX PLAYER
        CatalogItem("Ashram", "Crime, Drama", "Series", "7.4/10", "MX Player"),
        
        // VOOT
        CatalogItem("Asur", "Crime, Thriller", "Series", "8.4/10", "Voot"),
        
        // SUN NXT
        CatalogItem("Doctor", "Action, Thriller, Dark Comedy", "Movie", "7.4/10", "Sun NXT"),

        // SHEMAROOME
        CatalogItem("Malgudi Days", "Classic, Drama, Nostalgia", "Series", "8.9/10", "ShemarooMe"),

        // CURIOSITY STREAM
        CatalogItem("Secrets of the Solar System", "Space, Science", "Documentary", "8.2/10", "Curiosity Stream")
    )

    val platforms = listOf(
        "All", "Netflix", "Prime Video", "JioHotstar", "Zee5", "SonyLIV", 
        "Voot", "MX Player", "Sun NXT", "Disney+ Hotstar", "Aha", "Hoichoi", 
        "Max", "Apple TV+", "Hulu", "YouTube Premium", "MUBI", "Paramount+", 
        "Discovery+", "Lionsgate Play", "ShemarooMe", "Curiosity Stream"
    )
    val types = listOf("All", "Movie", "Series", "Documentary")

    val filteredCatalog = allCatalog.filter { item ->
        val matchesPlatform = selectedPlatform == "All" || item.platform == selectedPlatform
        val matchesType = selectedType == "All" || item.type == selectedType
        val matchesSearch = searchQuery.isBlank() || 
            item.title.contains(searchQuery, ignoreCase = true) || 
            item.genre.contains(searchQuery, ignoreCase = true) || 
            item.platform.contains(searchQuery, ignoreCase = true)
        
        matchesPlatform && matchesType && matchesSearch
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search movies, series...", color = Color(0xFF8A8AB0), fontSize = 14.sp) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF8B2FC9),
                unfocusedBorderColor = Color(0xFF8B2FC9),
                focusedContainerColor = Color(0xFF1E1E30),
                unfocusedContainerColor = Color(0xFF1E1E30),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search icon",
                    tint = Color(0xFF8A8AB0)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = Color.White
                        )
                    }
                }
            }
        )

        Text(
            text = "Trending Catalog Hub",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            letterSpacing = 0.5.sp
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
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Box(modifier = Modifier.fillMaxWidth()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(end = 32.dp) // Leave space for fade
            ) {
                items(platforms) { platform ->
                    val active = selectedPlatform == platform
                    val chipColor = platformColors[platform] ?: ChipColor(Color(0xFFFFFFFF), Color(0xFF000000))
                    
                    PlatformBadgeButton(
                        title = platform,
                        active = active,
                        platformColor = chipColor,
                        onClick = { selectedPlatform = platform }
                    )
                }
            }
            // Right edge fade gradient
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(40.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, Color(0xFF0A0A1A))
                        )
                    )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Content Type Filter Row
        Text(
            text = "CONTENT TYPE",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(bottom = 8.dp)
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
                    platformColor = ChipColor(Color(0xFFE8003D), Color(0xFFFFFFFF)),
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
                    .background(Color(0xFF12121F))
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
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF12121F)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Box {
                            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                                // Thin left border accent in red when Tomatometer is high (simulated as criticPercentage >= 60)
                            val baseNum = movie.score.replace("/10", "").toDoubleOrNull() ?: 7.5
                            val criticPercentage = (baseNum * 10 + 6).coerceIn(40.0, 99.0).toInt()
                            val audiencePercentage = (baseNum * 10 + 2).coerceIn(45.0, 98.0).toInt()
                            
                            if (criticPercentage >= 60) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .fillMaxHeight()
                                        .background(Color(0xFFE8003D))
                                )
                            }
                            
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = movie.title,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = movie.genre,
                                            fontSize = 12.sp,
                                            color = Color(0xFF9E9E9E)
                                        )
                                    }
                                    
                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        // Streaming Platform Badge
                                        val pColor = platformColors[movie.platform] ?: ChipColor(Color.DarkGray, Color.White)
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .background(pColor.bg)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = movie.platform.uppercase(),
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = pColor.text
                                            )
                                        }
                                        
                                        // Content Type Badge
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .background(Color.White.copy(alpha = 0.15f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = movie.type.uppercase(),
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Score Layout: [🍅 93% Tomatometer] [🍿 89% Audience]
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("🍅", fontSize = 14.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "$criticPercentage%",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Tomatometer", fontSize = 12.sp, color = Color(0xFF9E9E9E))
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("🍿", fontSize = 14.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "$audiencePercentage%",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Audience", fontSize = 12.sp, color = Color(0xFF9E9E9E))
                                    }
                                }
                            }
                        } // Closes the Row(IntrinsicSize)
                        
                        // Subtle gradient overlay at card bottom
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(30.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, Color(0xFF12121F).copy(alpha=0.5f))
                                    )
                                )
                        )
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
    platformColor: ChipColor,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (title == "Peacock" && active) {
        Brush.horizontalGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Blue, Color.Magenta))
    } else if (!active) {
        androidx.compose.ui.graphics.SolidColor(Color(0xFF2A2A3E))
    } else {
        androidx.compose.ui.graphics.SolidColor(Color.Transparent)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50.dp))
            .background(if (active) platformColor.bg else Color(0xFF1E1E2E))
            .border(1.dp, borderColor, RoundedCornerShape(50.dp))
            .then(
                if (active) {
                    Modifier.shadow(4.dp, RoundedCornerShape(50.dp), ambientColor = platformColor.bg, spotColor = platformColor.bg)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (active) platformColor.text else Color(0xFF8A8AB0)
        )
    }
}
`

const startIdx = content.indexOf('@Composable\nfun CuratedPlatformCatalog');
const endIdx = content.indexOf('data class CatalogItem(');
if (startIdx === -1 || endIdx === -1) {
  console.error('Could not find functions to replace.');
  process.exit(1);
}

const before = content.substring(0, startIdx);
const after = content.substring(endIdx);

fs.writeFileSync('app/src/main/java/com/example/MainActivity.kt', before + newComponents + "\n" + after);
console.log('REPLACED');
