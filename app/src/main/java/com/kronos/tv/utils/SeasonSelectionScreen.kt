package com.kronos.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed // Usamos Indexed para detectar el primero
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import androidx.compose.material3.CircularProgressIndicator
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import com.kronos.tv.network.RetrofitInstance
import com.kronos.tv.network.TmdbSeason
import com.kronos.tv.network.TmdbTvDetail

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SeasonSelectionScreen(
    tmdbId: Int,
    showTitle: String,
    onSeasonSelected: (Int) -> Unit,
    onBack: () -> Unit
) {
    var seriesDetail by remember { mutableStateOf<TmdbTvDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    
    // FOCO INICIAL: Apuntará a la primera temporada
    val firstItemFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        GlobalScope.launch(Dispatchers.Main) {
            try {
                seriesDetail = RetrofitInstance.api.getTvShowDetails(tmdbId, RetrofitInstance.getApiKey())
            } catch (e: Exception) {
                println("Error: ${e.message}")
            } finally {
                isLoading = false
                // Intentamos pedir el foco después de cargar
                kotlinx.coroutines.delay(200)
                try { firstItemFocus.requestFocus() } catch(e:Exception){}
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF141414))) {
        // FONDO SUTIL
        if (seriesDetail != null) {
            AsyncImage(
                model = seriesDetail!!.getFullBackdropUrl(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.2f)
            )
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color(0xFF141414)), startY = 0f, endY = 1000f)))
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFE50914))
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(40.dp)) {
                Text(text = showTitle, style = MaterialTheme.typography.headlineMedium, color = Color.Gray, fontWeight = FontWeight.Bold)
                Text(text = "Selecciona una Temporada", style = MaterialTheme.typography.titleLarge, color = Color.White)
                
                Spacer(modifier = Modifier.height(30.dp))

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(140.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = PaddingValues(bottom = 20.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    val seasons = seriesDetail?.seasons?.filter { it.season_number > 0 } ?: emptyList()
                    
                    itemsIndexed(seasons) { index, season ->
                        // Si es el primero (índice 0), le asignamos el FocusRequester
                        val modifier = if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier
                        
                        SeasonCardOptimized(
                            season = season, 
                            onClick = { onSeasonSelected(season.season_number) },
                            modifier = modifier
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Botón Volver Estandarizado
                NetflixButton(
                    text = "Volver",
                    icon = Icons.Default.ArrowBack,
                    onClick = onBack,
                    isPrimary = false
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SeasonCardOptimized(season: TmdbSeason, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.1f else 1f, label = "scale")
    val borderAlpha by animateFloatAsState(if (isFocused) 1f else 0f, label = "border")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(140.dp) // El modifier trae el FocusRequester si es el primero
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(2f/3f)
                .onFocusChanged { isFocused = it.isFocused }
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .border(2.dp, Color.White.copy(alpha = borderAlpha), RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .clickable { onClick() }
        ) {
            AsyncImage(
                model = season.getPosterUrl(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (!isFocused) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.3f)))
            }
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        
        Text(text = "Temporada ${season.season_number}", color = if (isFocused) Color.White else Color.Gray, fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp)
        Text(text = "${season.episode_count} Eps", color = Color.Gray, fontSize = 12.sp)
    }
}