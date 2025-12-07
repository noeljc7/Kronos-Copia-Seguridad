package com.kronos.tv.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kronos.tv.network.RetrofitInstance
import com.kronos.tv.network.TmdbMovie
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Estado de UI: Define todo lo que la pantalla necesita pintar
data class HomeUiState(
    val featuredMovie: TmdbMovie? = null,
    val popularMovies: List<TmdbMovie> = emptyList(),
    val popularSeries: List<TmdbMovie> = emptyList(),
    val actionMovies: List<TmdbMovie> = emptyList(),
    val animationMovies: List<TmdbMovie> = emptyList(),
    val comedySeries: List<TmdbMovie> = emptyList(), // Agregamos Comedia
    val isLoading: Boolean = true,
    val error: String? = null
)

class HomeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadContent()
    }

    private fun loadContent() {
        viewModelScope.launch {
            val api = RetrofitInstance.api
            val key = RetrofitInstance.getApiKey()

            try {
                // 1. CARGA CRÍTICA (Hero Section)
                // Cargamos Populares PRIMERO para llenar la pantalla principal rápido
                launch {
                    try {
                        val movies = api.getPopularMovies(key).results.map { it.copy(media_type = "movie") }
                        
                        // Actualizamos estado con películas y apagamos el Loading AQUÍ
                        // Así aseguramos que el spinner se vaya solo cuando haya algo que ver
                        _uiState.value = _uiState.value.copy(
                            popularMovies = movies,
                            isLoading = false // ¡Apagamos el spinner solo cuando tenemos datos!
                        )
                        updateFeatured(movies)
                    } catch (e: Exception) {
                        // Si falla lo principal, mostramos error
                        _uiState.value = _uiState.value.copy(isLoading = false, error = "Error al cargar inicio")
                    }
                }

                // 2. CARGA SECUNDARIA (En paralelo / Segundo plano)
                // Estas cargan a su ritmo y aparecen mágicamente cuando están listas
                
                // Series Populares
                launch {
                    try {
                        val series = api.getPopularTvShows(key).results.map { it.copy(media_type = "tv") }
                        _uiState.value = _uiState.value.copy(popularSeries = series)
                    } catch (_: Exception) {}
                }
                
                // Acción (ID 28)
                launch {
                    try {
                        val action = api.getMoviesByGenre(key, 28).results.map { it.copy(media_type = "movie") }
                        _uiState.value = _uiState.value.copy(actionMovies = action)
                    } catch (_: Exception) {}
                }

                // Animación (ID 16)
                launch {
                    try {
                        val anim = api.getMoviesByGenre(key, 16).results.map { it.copy(media_type = "movie") }
                        _uiState.value = _uiState.value.copy(animationMovies = anim)
                    } catch (_: Exception) {}
                }

                // Comedia (ID 35 - Series)
                launch {
                    try {
                        val comedy = api.getTvByGenre(key, 35).results.map { it.copy(media_type = "tv") }
                        _uiState.value = _uiState.value.copy(comedySeries = comedy)
                    } catch (_: Exception) {}
                }

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    private fun updateFeatured(movies: List<TmdbMovie>) {
        // Escoge una película al azar para el banner gigante, pero evita que cambie si rotamos pantalla
        if (_uiState.value.featuredMovie == null && movies.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(featuredMovie = movies.random())
        }
    }
}