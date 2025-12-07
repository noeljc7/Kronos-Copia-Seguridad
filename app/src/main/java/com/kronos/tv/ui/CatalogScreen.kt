@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package com.kronos.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import androidx.tv.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import com.kronos.tv.network.RetrofitInstance
import com.kronos.tv.network.TmdbMovie
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

// Modelo de datos
data class GenreRowData(val id: Int, val name: String, val movies: List<TmdbMovie>)

@Composable
fun CatalogScreen(
    isMovieCatalog: Boolean,
    onPlayClick: (TmdbMovie) -> Unit,
    onGenreClick: (Int, String) -> Unit,
    onBack: () -> Unit
) {
    var genreRows by remember { mutableStateOf(emptyList<GenreRowData>()) }
    var isLoading by remember { mutableStateOf(true) }
    
    // 1. EL IMÁN DE FOCO (Para el primer póster de la primera fila)
    val firstItemFocus = remember { FocusRequester() }

    // Carga de Datos
    LaunchedEffect(isMovieCatalog) {
        isLoading = true
        try {
            val api = RetrofitInstance.api
            val key = RetrofitInstance.getApiKey()
            val genresResponse = if (isMovieCatalog) api.getMovieGenres(key) else api.getTvGenres(key)
            
            val loadedRows = coroutineScope {
                genresResponse.genres.take(15).map { genre ->
                    async {
                        try {
                            val resp = if (isMovieCatalog) api.getMoviesByGenre(key, genre.id) else api.getTvByGenre(key, genre.id)
                            val items = resp.results.map { it.copy(media_type = if(isMovieCatalog) "movie" else "tv") }
                            if (items.isNotEmpty()) {
                                val uniqueItems = items.distinctBy { it.id }
                                GenreRowData(genre.id, genre.name, uniqueItems)
                            } else null
                        } catch (e: Exception) { null }
                    }
                }
            }
            genreRows = loadedRows.awaitAll().filterNotNull()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    // 2. CORRECCIÓN DE FOCO: Esperamos a que isLoading sea false
    // Esto garantiza que la UI ya existe antes de pedir el foco
    LaunchedEffect(isLoading) {
        if (!isLoading && genreRows.isNotEmpty()) {
            delay(500) // Damos medio segundo para asegurar que todo se pintó
            try { firstItemFocus.requestFocus() } catch(e:Exception){ }
        }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF141414)), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFFE50914))
        }
    } else {
        Row(modifier = Modifier.fillMaxSize().background(Color(0xFF141414))) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(start = 20.dp),
                contentPadding = PaddingValues(bottom = 50.dp, top = 20.dp)
            ) {
                item(key = "header") {
                    Text(
                        text = if(isMovieCatalog) "Catálogo de Películas" else "Catálogo de Series",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )
                }

                itemsIndexed(items = genreRows, key = { _, item -> item.id }) { index, section ->
                    GenreSectionRow(
                        section = section,
                        onPlayClick = onPlayClick,
                        onGenreClick = onGenreClick,
                        // Solo pasamos el imán a la fila 0
                        parentFocusRequester = if (index == 0) firstItemFocus else null
                    )
                }
            }
        }
    }
}

@Composable
fun GenreSectionRow(
    section: GenreRowData,
    onPlayClick: (TmdbMovie) -> Unit,
    onGenreClick: (Int, String) -> Unit,
    parentFocusRequester: FocusRequester? = null 
) {
    Column(modifier = Modifier.padding(bottom = 25.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 40.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = section.name, 
                color = Color(0xFFE5E5E5), 
                fontWeight = FontWeight.Bold, 
                style = MaterialTheme.typography.titleMedium
            )
            
            // Usamos NetflixButton para uniformidad visual
            NetflixButton(
                text = "Ver todo ", 
                onClick = { onGenreClick(section.id, section.name) },
                isPrimary = false 
            )
        }
        
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(end = 40.dp)) {
            itemsIndexed(items = section.movies, key = { _, it -> it.id }) { index, movie ->
                
                // Aplicamos el imán al primer póster de la primera fila
                val modifier = if (parentFocusRequester != null && index == 0) {
                    Modifier.focusRequester(parentFocusRequester)
                } else {
                    Modifier
                }

                NetflixPoster(
                    movie = movie, 
                    onClick = { onPlayClick(movie) },
                    modifier = modifier 
                )
            }
        }
    }
}