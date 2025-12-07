package com.kronos.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.kronos.tv.network.TmdbMovie
import com.kronos.tv.utils.HistoryManager
import com.kronos.tv.utils.FavoriteItem

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DetailsScreen(
    movie: TmdbMovie,
    onPlayClick: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val historyManager = remember { HistoryManager(context) }
    var isFavorite by remember { mutableStateOf(false) }
    
    // FOCO INICIAL: Botón Reproducir
    val playButtonFocus = remember { FocusRequester() }

    LaunchedEffect(movie.id) {
        isFavorite = historyManager.isFavorite(movie.id)
        // Forzar foco
        kotlinx.coroutines.delay(100)
        try { playButtonFocus.requestFocus() } catch(e:Exception){}
    }

    fun toggleFav() {
        val item = FavoriteItem(
            tmdbId = movie.id,
            isMovie = movie.media_type != "tv", 
            title = movie.getDisplayTitle(),
            posterUrl = movie.getFullPosterUrl(),
            backdropUrl = movie.getFullBackdropUrl()
        )
        isFavorite = historyManager.toggleFavorite(item)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF121212))) {
        AsyncImage(
            model = movie.getFullBackdropUrl(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(0.5f)
        )
        
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color(0xFF121212)), startY = 100f)))
        Box(modifier = Modifier.fillMaxSize().background(Brush.horizontalGradient(colors = listOf(Color.Black.copy(alpha = 0.9f), Color.Transparent), endX = 1200f)))

        Row(
            modifier = Modifier.fillMaxSize().padding(50.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text(
                    text = movie.getDisplayTitle(),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    lineHeight = 50.sp
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Puntuación: ${String.format("%.1f", movie.vote_average)}", color = Color(0xFF46D369), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(15.dp))
                    BorderText(if (movie.media_type == "tv") "SERIE" else "PELÍCULA")
                    Spacer(modifier = Modifier.width(15.dp))
                    Text("HD", color = Color.Gray)
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                Text(
                    text = movie.getOverviewSafe(),
                    color = Color.White,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    maxLines = 6
                )
                
                Spacer(modifier = Modifier.height(30.dp))
                
                Row {
                    // BOTÓN 1: REPRODUCIR (Foco Inicial)
                    NetflixButton(
                        text = "Reproducir",
                        icon = Icons.Default.PlayArrow,
                        onClick = onPlayClick,
                        isPrimary = true, // Color rojo base
                        focusRequester = playButtonFocus // <--- CONECTADO
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))

                    // BOTÓN 2: FAVORITOS
                    NetflixButton(
                        text = if (isFavorite) "En Favoritos" else "Añadir a Lista",
                        icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        onClick = { toggleFav() }
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    // BOTÓN 3: VOLVER
                    NetflixButton(
                        text = "Volver",
                        icon = Icons.Default.ArrowBack, 
                        onClick = onBack
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(0.5f))
        }
    }
}