@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.kronos.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
// --- IMPORT CRÍTICO AGREGADO ---
import androidx.compose.material3.CircularProgressIndicator
// ------------------------------
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text as MobileText 
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text as TvText 
import androidx.tv.material3.ExperimentalTvMaterial3Api
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import com.kronos.tv.network.RetrofitInstance
import com.kronos.tv.network.TmdbMovie

@Composable
fun SearchScreen(
    initialQuery: String,
    initialResults: List<TmdbMovie>,
    initialType: Boolean, 
    onStateChange: (String, List<TmdbMovie>, Boolean) -> Unit,
    onMovieSelected: (TmdbMovie) -> Unit,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf(initialQuery) }
    var results by remember { mutableStateOf(initialResults) }
    var isMovieSearch by remember { mutableStateOf(initialType) }
    var isSearching by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    fun performSearch() {
        if (query.isBlank()) return
        searchJob?.cancel()
        
        searchJob = scope.launch {
            isSearching = true
            try {
                val apiKey = RetrofitInstance.getApiKey()
                val api = RetrofitInstance.api
                
                val response = if (isMovieSearch) {
                    api.searchMovies(apiKey, query)
                } else {
                    api.searchTvShows(apiKey, query)
                }
                
                val items = response.results.map { 
                    it.copy(media_type = if (isMovieSearch) "movie" else "tv") 
                }
                
                val uniqueItems = items.distinctBy { it.id }
                
                results = uniqueItems
                onStateChange(query, uniqueItems, isMovieSearch)
                
            } catch (e: Exception) {
            } finally {
                isSearching = false
            }
        }
    }

    fun updateType(isMovie: Boolean) {
        if (isMovieSearch == isMovie) return 
        isMovieSearch = isMovie
        onStateChange(query, results, isMovieSearch)
        if (query.isNotEmpty()) performSearch()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141414))
            .padding(30.dp)
    ) {
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            TypeButton(text = "Películas", isSelected = isMovieSearch) { updateType(true) }
            Spacer(modifier = Modifier.width(20.dp))
            TypeButton(text = "Series", isSelected = !isMovieSearch) { updateType(false) }
        }
        
        Spacer(modifier = Modifier.height(20.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { MobileText("Escribe para buscar...", color = Color.Gray) },
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFFE50914),
                    containerColor = Color(0xFF1E1E1E),
                    focusedBorderColor = Color(0xFFE50914),
                    unfocusedBorderColor = Color.Transparent
                ),
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { performSearch() }),
                modifier = Modifier.weight(1f)
            )
            
            Spacer(modifier = Modifier.width(10.dp))
            
            Box(modifier = Modifier.clickable { performSearch() }) {
                Button(
                    onClick = { performSearch() }, 
                    colors = ButtonDefaults.colors(containerColor = Color(0xFFE50914))
                ) { 
                    Icon(Icons.Default.Search, null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    TvText("BUSCAR") 
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (isSearching) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFE50914))
            }
        } else if (results.isEmpty() && query.isNotEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                TvText("No se encontraron resultados", color = Color.Gray)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(120.dp), 
                verticalArrangement = Arrangement.spacedBy(15.dp),
                horizontalArrangement = Arrangement.spacedBy(15.dp),
                contentPadding = PaddingValues(bottom = 50.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(
                    items = results,
                    key = { index, item -> "${item.id}-$index" }
                ) { _, item ->
                    NetflixPoster(movie = item, onClick = { onMovieSelected(item) })
                }
            }
        }
        
        Spacer(modifier = Modifier.height(15.dp))
        
        Box(modifier = Modifier.align(Alignment.CenterHorizontally).clickable { onBack() }) {
            Button(
                onClick = onBack, 
                colors = ButtonDefaults.colors(containerColor = Color(0xFF333333))
            ) { TvText("Volver al Inicio") }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TypeButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.colors(
            containerColor = if (isSelected) Color(0xFFE50914) else Color(0xFF333333),
            contentColor = Color.White
        ),
        modifier = Modifier.width(150.dp)
    ) {
        TvText(text, fontWeight = if(isSelected) FontWeight.Bold else FontWeight.Normal)
    }
}