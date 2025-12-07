@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package com.kronos.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
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
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.ExperimentalTvMaterial3Api
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.kronos.tv.network.TmdbMovie
import com.kronos.tv.utils.HistoryManager
import kotlinx.coroutines.delay

@Composable
fun FavoritesScreen(
    onMovieClick: (TmdbMovie) -> Unit
) {
    val context = LocalContext.current
    var favorites by remember { mutableStateOf(emptyList<TmdbMovie>()) }
    val gridState = rememberLazyGridState()
    
    // 1. EL IMÁN DE FOCO
    val firstItemFocus = remember { FocusRequester() }

    // Cargar favoritos al entrar
    LaunchedEffect(Unit) {
        val rawFavs = HistoryManager(context).getFavorites()
        
        favorites = rawFavs.map { fav ->
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
    }

    // 2. LÓGICA DE FOCO REFORZADA
    // Vigilamos cuando la lista deje de estar vacía
    LaunchedEffect(favorites.isNotEmpty()) {
        if (favorites.isNotEmpty()) {
            // Le damos tiempo a Compose para dibujar los cuadros
            delay(100) 
            // Forzamos al Grid a mirar al inicio
            gridState.scrollToItem(0)
            delay(100)
            // Disparamos el foco
            try { firstItemFocus.requestFocus() } catch(e:Exception){}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141414))
            .padding(start = 20.dp, top = 20.dp, end = 20.dp)
    ) {
        // ENCABEZADO
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Favorite, null, tint = Color(0xFFE50914), modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text("Mi Lista", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        }

        Spacer(modifier = Modifier.height(20.dp))

        // GRID DE FAVORITOS
        if (favorites.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Aún no tienes favoritos guardados.", color = Color.Gray)
            }
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(120.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 50.dp)
            ) {
                itemsIndexed(favorites) { index, movie ->
                    // Si es el primero, le pegamos el FocusRequester
                    val modifier = if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier
                    
                    // Usamos un póster local para asegurar que el modificador funcione
                    FavPosterLocal(
                        movie = movie, 
                        onClick = { onMovieClick(movie) },
                        modifier = modifier
                    )
                }
            }
        }
    }
}

// --- PÓSTER LOCAL AISLADO (GARANTIZA EL FOCO) ---
@Composable
fun FavPosterLocal(
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
            .build()
    }

    Box(
        modifier = modifier // <--- AQUÍ RECIBIMOS EL FOCUS REQUESTER
            .width(130.dp)
            .aspectRatio(2f / 3f)
            .onFocusChanged { isFocused = it.isFocused } // Detectamos foco
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .border(2.dp, Color.White.copy(alpha = borderAlpha), RoundedCornerShape(8.dp))
            .background(Color.DarkGray, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() } // Hacemos clic
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = null, 
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}