package com.kronos.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed // Indexado para el foco
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.withContext
import com.kronos.tv.network.RetrofitInstance
import com.kronos.tv.network.TmdbEpisode
import com.kronos.tv.utils.HistoryManager

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun EpisodeSelectionScreen(
    tmdbId: Int,
    showTitle: String,
    seasonNumber: Int,
    listState: LazyListState,
    onEpisodeSelected: (Int) -> Unit,
    onBack: () -> Unit
) {
    var episodes by remember { mutableStateOf(emptyList<TmdbEpisode>()) }
    var isLoading by remember { mutableStateOf(true) }
    var watchedEpisodes by remember { mutableStateOf(emptySet<Int>()) }
    
    val context = LocalContext.current 
    // FOCO INICIAL
    val firstEpFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (episodes.isEmpty()) {
            GlobalScope.launch(Dispatchers.IO) { 
                try {
                    val response = RetrofitInstance.api.getSeasonDetails(tmdbId, seasonNumber, RetrofitInstance.getApiKey())
                    val fetchedEpisodes = response.episodes
                    val historyManager = HistoryManager(context)
                    val watchedSet = mutableSetOf<Int>()
                    fetchedEpisodes.forEach { ep ->
                        if (historyManager.isEpisodeWatched(tmdbId, seasonNumber, ep.episode_number)) {
                            watchedSet.add(ep.episode_number)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        episodes = fetchedEpisodes
                        watchedEpisodes = watchedSet
                        isLoading = false
                        // Pedir foco
                        kotlinx.coroutines.delay(200)
                        try { firstEpFocus.requestFocus() } catch(e:Exception){}
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { isLoading = false }
                }
            }
        } else isLoading = false
    }

    Row(modifier = Modifier.fillMaxSize().background(Color(0xFF141414))) {
        Column(
            modifier = Modifier.width(300.dp).fillMaxHeight().background(Color.Black.copy(alpha=0.5f)).padding(40.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "Temporada $seasonNumber", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = showTitle, style = MaterialTheme.typography.titleMedium, color = Color.Gray)
            Spacer(modifier = Modifier.height(30.dp))
            
            // Botón Estandarizado
            NetflixButton(text = "Atrás", icon = Icons.Default.ArrowBack, onClick = onBack)
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFE50914))
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(40.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(episodes) { index, ep ->
                    val modifier = if (index == 0) Modifier.focusRequester(firstEpFocus) else Modifier
                    
                    EpisodeCardMinimal(
                        episode = ep, 
                        isWatched = watchedEpisodes.contains(ep.episode_number), 
                        onClick = { onEpisodeSelected(ep.episode_number) },
                        modifier = modifier
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun EpisodeCardMinimal(episode: TmdbEpisode, isWatched: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.02f else 1f, label = "scale")
    val backgroundColor = if (isFocused) Color(0xFF2A2A2A) else if (isWatched) Color(0xFF1E251E) else Color(0xFF1E1E1E)
    val borderColor = if (isFocused) Color.White else if (isWatched) Color(0xFF4CAF50).copy(alpha=0.5f) else Color.Transparent

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(90.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(160.dp).fillMaxHeight()) {
                AsyncImage(model = episode.getThumbUrl(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (isWatched && !isFocused) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.6f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(32.dp))
                    }
                } else if (isFocused) {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.4f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PlayArrow, null, tint = Color.White)
                    }
                }
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 20.dp), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Episodio ${episode.episode_number}", color = if (isWatched) Color(0xFF4CAF50) else if (isFocused) Color(0xFFE50914) else Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    if (isWatched) { Spacer(modifier = Modifier.width(8.dp)); Text("VISTO", color = Color(0xFF4CAF50), fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = episode.name, color = if (isWatched && !isFocused) Color.Gray else Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}