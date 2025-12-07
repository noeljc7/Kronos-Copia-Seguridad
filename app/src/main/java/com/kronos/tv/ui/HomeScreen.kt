@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package com.kronos.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// Imports TV
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Border
import androidx.tv.material3.Icon
import androidx.tv.material3.ExperimentalTvMaterial3Api

import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kronos.tv.network.TmdbMovie
import com.kronos.tv.ScreenState
import com.kronos.tv.utils.HistoryManager
import com.kronos.tv.utils.WatchProgress
import com.kronos.tv.ui.viewmodels.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    onMovieClick: (TmdbMovie) -> Unit, 
    onHeroPlayClick: (TmdbMovie) -> Unit,
    onNavigate: (ScreenState) -> Unit,
    onResumeClick: (WatchProgress) -> Unit,
    vm: HomeViewModel = viewModel() 
) {
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    var continueWatchingList by remember { mutableStateOf(emptyList<WatchProgress>()) }
    var myFavoritesList by remember { mutableStateOf(emptyList<TmdbMovie>()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { 
            val historyManager = HistoryManager(context)
            val history = historyManager.getContinueWatching()
            val favs = historyManager.getFavorites()
            val mappedFavs = favs.map { fav ->
                TmdbMovie(
                    id = fav.tmdbId,
                    title = if(fav.isMovie) fav.title else null,
                    name = if(!fav.isMovie) fav.title else null,
                    poster_path = fav.posterUrl.replace("https://image.tmdb.org/t/p/w500", ""),
                    backdrop_path = fav.backdropUrl.replace("https://image.tmdb.org/t/p/original", ""),
                    media_type = if(fav.isMovie) "movie" else "tv",
                    overview = null, 
                    vote_average = 0.0
                )
            }
            withContext(Dispatchers.Main) {
                continueWatchingList = history
                myFavoritesList = mappedFavs
            }
        }
    }

    Row(modifier = Modifier.fillMaxSize().background(Color(0xFF141414))) {
        Sidebar(onNavigate, ScreenState.HOME)

        // LÓGICA DE CARGA MEJORADA
        // Si está cargando, llamamos al componente del OTRO archivo
        if (state.isLoading && state.popularMovies.isEmpty()) {
            SkeletonHomeScreen() // <--- ¡AQUÍ ESTÁ LA MAGIA LIMPIA!
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(bottom = 50.dp)
            ) {
                // 1. HERO
                state.featuredMovie?.let { 
                    HeroSection(movie = it, onPlayClick = onHeroPlayClick) 
                }
                
                // 2. FILAS
                if (continueWatchingList.isNotEmpty()) {
                    ContentRow("Continuar Viendo", continueWatchingList, isResume = true) { 
                            onResumeClick(it as WatchProgress) 
                    }
                }
                if (myFavoritesList.isNotEmpty()) {
                    ContentRow("Mi Lista", myFavoritesList, onClick = onMovieClick)
                }
                if (state.popularMovies.isNotEmpty()) {
                    ContentRow("Películas Populares", state.popularMovies, onClick = onMovieClick)
                }
                if (state.popularSeries.isNotEmpty()) {
                    ContentRow("Series del Momento", state.popularSeries, onClick = onMovieClick)
                }
            }
        }
    }
}

// ... COMPONENTES AUXILIARES ...

@Composable
fun <T> ContentRow(
    title: String, 
    items: List<T>, 
    isResume: Boolean = false,
    onClick: (T) -> Unit
) {
    val uniqueItems = remember(items) {
        items.distinctBy { item ->
            if (item is TmdbMovie) item.id else item.hashCode()
        }
    }

    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            text = title, 
            style = MaterialTheme.typography.titleMedium, 
            color = Color(0xFFE5E5E5), 
            fontWeight = FontWeight.Bold, 
            modifier = Modifier.padding(start = 50.dp, bottom = 10.dp)
        )
        
        LazyRow(
            contentPadding = PaddingValues(horizontal = 50.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(
                items = uniqueItems,
                key = { item -> 
                    if (item is TmdbMovie) "movie_${item.id}"
                    else if (item is WatchProgress) "prog_${item.tmdbId}"
                    else item.hashCode()
                }
            ) { item ->
                if (isResume) {
                    ContinueWatchingCard(item as WatchProgress, onClick = { onClick(item) })
                } else {
                    if (item is TmdbMovie) {
                        NetflixPoster(movie = item, onClick = { onClick(item) })
                    }
                }
            }
        }
    }
}

@Composable
fun NetflixPoster(
    movie: TmdbMovie, 
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.1f else 1f, label = "scale")
    val borderAlpha by animateFloatAsState(if (isFocused) 1f else 0f, label = "border")
    val context = LocalContext.current

    val imageRequest = remember(movie.getFullPosterUrl()) {
        ImageRequest.Builder(context)
            .data(movie.getFullPosterUrl())
            .crossfade(true)
            .size(300, 450)
            .build()
    }

    Box(
        modifier = modifier
            .width(130.dp)
            .aspectRatio(2f / 3f)
            .onFocusChanged { isFocused = it.isFocused }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .border(2.dp, Color.White.copy(alpha = borderAlpha), RoundedCornerShape(8.dp))
            .background(Color.DarkGray, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = null, 
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            filterQuality = FilterQuality.Low
        )
    }
}

@Composable
fun ContinueWatchingCard(item: WatchProgress, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f, label = "scale")
    val borderAlpha by animateFloatAsState(if (isFocused) 1f else 0f, label = "border")

    Box(
        modifier = Modifier
            .width(220.dp)
            .aspectRatio(16f / 9f)
            .onFocusChanged { isFocused = it.isFocused }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .border(2.dp, Color.White.copy(alpha = borderAlpha), RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = item.backdropUrl, 
            contentDescription = null, 
            contentScale = ContentScale.Crop, 
            modifier = Modifier.fillMaxSize()
        )
        
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha=0.9f)), startY = 100f)))

        Column(modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).padding(bottom = 6.dp)) {
            val infoText = if (item.isMovie) item.title else "T${item.season}:E${item.episode} - ${item.title}"
            Text(
                text = infoText,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp).background(Color.Gray.copy(alpha=0.5f))) {
            Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(item.getProgressPercent()).background(Color(0xFFE50914)))
        }
        
        if (isFocused) {
            Box(modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha=0.6f), CircleShape).padding(8.dp)) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White)
            }
        }
    }
}

@Composable
fun Sidebar(onNavigate: (ScreenState) -> Unit, currentScreen: ScreenState) {
    Column(
        modifier = Modifier.fillMaxHeight().width(80.dp).background(Color.Black.copy(alpha = 0.95f)).padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(30.dp)
    ) {
        SidebarItem(Icons.Default.Search, isSelected = currentScreen == ScreenState.SEARCH) { onNavigate(ScreenState.SEARCH) }
        Spacer(modifier = Modifier.height(10.dp))
        SidebarItem(Icons.Default.Home, isSelected = currentScreen == ScreenState.HOME) { onNavigate(ScreenState.HOME) }
        SidebarItem(Icons.Default.Movie, isSelected = currentScreen == ScreenState.MOVIES_CATALOG) { onNavigate(ScreenState.MOVIES_CATALOG) }
        SidebarItem(Icons.Default.Tv, isSelected = currentScreen == ScreenState.SERIES_CATALOG) { onNavigate(ScreenState.SERIES_CATALOG) }
        SidebarItem(Icons.Default.Favorite, isSelected = currentScreen == ScreenState.FAVORITES) { onNavigate(ScreenState.FAVORITES) }
    }
}

@Composable
fun SidebarItem(icon: ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.2f else 1f, label = "scale")
    val iconColor = if (isSelected) Color(0xFFE50914) else if (isFocused) Color.White else Color(0xFFB3B3B3)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(50.dp).onFocusChanged { isFocused = it.isFocused }.clickable { onClick() }.background(if (isFocused) Color.White.copy(alpha = 0.1f) else Color.Transparent, CircleShape)) {
        Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(28.dp).graphicsLayer { scaleX = scale; scaleY = scale })
    }
}

@Composable
fun HeroSection(
    movie: TmdbMovie, 
    onPlayClick: (TmdbMovie) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    
    LaunchedEffect(Unit) {
        delay(200) 
        try { focusRequester.requestFocus() } catch (e: Exception) {}
    }

    Box(modifier = Modifier.fillMaxWidth().height(550.dp)) {
        AsyncImage(
            model = movie.getFullBackdropUrl(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.7f
        )
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color(0xFF141414)), startY = 300f)))
        Box(modifier = Modifier.fillMaxSize().background(Brush.horizontalGradient(colors = listOf(Color.Black.copy(alpha=0.8f), Color.Transparent), endX = 1000f)))
        
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(60.dp).width(500.dp)) {
            Text(text = movie.getDisplayTitle().uppercase(), style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Black, shadow = androidx.compose.ui.graphics.Shadow(color = Color.Black, blurRadius = 10f)), color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("98% Coincidencia", color = Color(0xFF46D369), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(12.dp))
                BorderText(if(movie.media_type=="tv") "SERIE" else "PELÍCULA") 
                Spacer(modifier = Modifier.width(12.dp))
                Text("HD", color = Color(0xFFB3B3B3), fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = movie.overview ?: "Sin descripción", color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            NetflixButton(
                text = "Reproducir",
                icon = Icons.Default.PlayArrow,
                onClick = { onPlayClick(movie) },
                isPrimary = true,
                focusRequester = focusRequester
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NetflixButton(
    text: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val baseContainer = if (isPrimary) Color(0xFFE50914) else Color(0xFF333333)

    val finalModifier = if (focusRequester != null) modifier.focusRequester(focusRequester) else modifier

    Button(
        onClick = onClick,
        modifier = finalModifier.onFocusChanged { isFocused = it.isFocused },
        scale = ButtonDefaults.scale(focusedScale = 1.1f),
        colors = ButtonDefaults.colors(
            containerColor = baseContainer, 
            contentColor = Color.White,
            focusedContainerColor = Color.White, 
            focusedContentColor = Color.Black    
        ),
        border = ButtonDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White))
        ),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(4.dp)),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text, 
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge
        )
    }
}