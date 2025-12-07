package com.kronos.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.CircularProgressIndicator
import androidx.tv.material3.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.kronos.tv.network.RetrofitInstance
import com.kronos.tv.network.TmdbMovie

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun GenreGridScreen(
    genreId: Int,
    genreName: String,
    isMovie: Boolean,
    onMovieClick: (TmdbMovie) -> Unit,
    onBack: () -> Unit
) {
    val movies = remember { mutableStateListOf<TmdbMovie>() }
    var page by remember { mutableIntStateOf(1) }
    var isLoading by remember { mutableStateOf(true) }
    var canLoadMore by remember { mutableStateOf(true) }
    
    val scope = rememberCoroutineScope()
    val listState = rememberLazyGridState()
    
    // 1. EL IMÁN DE FOCO
    val firstPosterFocus = remember { FocusRequester() }
    // Variable para saber si ya forzamos el foco la primera vez (para no molestar al scroll después)
    var initialFocusSet by remember { mutableStateOf(false) }

    fun loadNextPage() {
        if (isLoading && page > 1) return
        if (!canLoadMore) return

        isLoading = true
        
        scope.launch {
            try {
                val api = RetrofitInstance.api
                val key = RetrofitInstance.getApiKey()
                
                val response = if (isMovie) {
                    api.getMoviesByGenre(key, genreId, page = page)
                } else {
                    api.getTvByGenre(key, genreId, page = page)
                }

                val newItems = response.results.map { 
                    it.copy(media_type = if (isMovie) "movie" else "tv") 
                }
                
                if (newItems.isEmpty()) {
                    canLoadMore = false
                } else {
                    val existingIds = movies.map { it.id }.toSet()
                    val uniqueNewItems = newItems.filter { it.id !in existingIds }
                    movies.addAll(uniqueNewItems)
                    page++
                    
                    // 2. DISPARAR EL FOCO (Solo la primera vez que cargan datos)
                    if (!initialFocusSet && movies.isNotEmpty()) {
                        initialFocusSet = true
                        delay(200) // Damos tiempo al Grid para pintarse
                        try { firstPosterFocus.requestFocus() } catch(e:Exception){}
                    }
                }
            } catch (e: Exception) {
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadNextPage() }

    val shouldLoadMore = remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItemIndex >= (totalItems - 8)
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && !isLoading && canLoadMore) {
            loadNextPage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141414))
            .padding(start = 40.dp, end = 40.dp, top = 30.dp)
    ) {
        // ENCABEZADO
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Botón Volver Estandarizado (Gris -> Blanco)
            NetflixButton(
                text = "Volver", 
                icon = Icons.Default.ArrowBack, 
                onClick = onBack
            )
            
            Spacer(modifier = Modifier.width(20.dp))
            
            Text(
                text = genreName, 
                style = MaterialTheme.typography.headlineMedium,
                color = Color(0xFFE50914) 
            )
        }
        
        Spacer(modifier = Modifier.height(20.dp))

        // GRID
        LazyVerticalGrid(
            state = listState,
            columns = GridCells.Adaptive(130.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 50.dp),
            modifier = Modifier.weight(1f)
        ) {
            itemsIndexed(
                items = movies,
                key = { index, movie -> "${movie.id}-$index" }
            ) { index, movie ->
                
                // 3. CONECTAR EL IMÁN AL PRIMER ELEMENTO (Índice 0)
                val modifier = if (index == 0) Modifier.focusRequester(firstPosterFocus) else Modifier
                
                NetflixPoster(
                    movie = movie, 
                    onClick = { onMovieClick(movie) },
                    modifier = modifier // Pasamos el modificador con el foco
                )
            }
            
            if (isLoading && movies.isNotEmpty()) {
                item {
                    Box(modifier = Modifier.height(150.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFFE50914))
                    }
                }
            }
        }
        
        if (isLoading && movies.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFE50914))
            }
        }
    }
}