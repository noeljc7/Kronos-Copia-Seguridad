package com.kronos.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.kronos.tv.network.TmdbMovie
import com.kronos.tv.providers.ProviderManager

// --- AQUÍ VIVE AHORA EL SCREEN STATE ---
enum class ScreenState { 
    HOME, DETAILS, SELECTION, PLAYER, SEARCH, SEASONS, EPISODES, 
    MOVIES_CATALOG, SERIES_CATALOG, GENRE_GRID, FAVORITES 
}

@Composable
fun AppNavigation(providerManager: ProviderManager) {
    // --- TU LÓGICA ORIGINAL ---
    val backStack = remember { mutableStateListOf(ScreenState.HOME) }
    val currentScreen = backStack.last()

    // --- ESTADOS GLOBALES ---
    var selectedMovie by remember { mutableStateOf<TmdbMovie?>(null) }
    var selectedSeasonNum by remember { mutableStateOf(1) }
    var selectedEpisodeNum by remember { mutableStateOf(1) }
    var videoUrlToPlay by remember { mutableStateOf("") }
    
    // MEMORIA DEL BUSCADOR
    var savedSearchQuery by remember { mutableStateOf("") }
    var savedSearchResults by remember { mutableStateOf(emptyList<TmdbMovie>()) }
    var savedIsMovieSearch by remember { mutableStateOf(true) }
    
    // MEMORIA DE CATÁLOGOS
    var selectedGenreId by remember { mutableStateOf(0) }
    var selectedGenreName by remember { mutableStateOf("") }
    var isGenreMovie by remember { mutableStateOf(true) }
    
    val episodesListState = rememberLazyListState()

    fun navigateTo(screen: ScreenState) {
        if (backStack.last() != screen) backStack.add(screen)
    }

    fun goBack() {
        if (backStack.size > 1) backStack.removeLast()
    }

    BackHandler(enabled = backStack.size > 1) { goBack() }

    when (currentScreen) {
        ScreenState.HOME -> {
            HomeScreen(
                onMovieClick = { movie -> 
                    selectedMovie = movie
                    navigateTo(ScreenState.DETAILS)
                },
                onHeroPlayClick = { movie ->
                    selectedMovie = movie
                    if (movie.media_type == "tv") navigateTo(ScreenState.SEASONS)
                    else navigateTo(ScreenState.SELECTION)
                },
                onNavigate = { screen -> navigateTo(screen) },
                onResumeClick = { historyItem -> 
                    selectedMovie = TmdbMovie(
                        id = historyItem.tmdbId,
                        title = if(historyItem.isMovie) historyItem.title else null,
                        name = if(!historyItem.isMovie) historyItem.title else null,
                        overview = null,
                        poster_path = historyItem.posterUrl.replace("https://image.tmdb.org/t/p/w500", ""),
                        backdrop_path = historyItem.backdropUrl.replace("https://image.tmdb.org/t/p/original", ""),
                        vote_average = 0.0,
                        media_type = if(historyItem.isMovie) "movie" else "tv",
                        original_title = null,
                        original_name = null,
                        release_date = null,
                        first_air_date = null
                    )
                    selectedSeasonNum = historyItem.season
                    selectedEpisodeNum = historyItem.episode
                    navigateTo(ScreenState.SELECTION)
                }
            )
        }
        
        ScreenState.FAVORITES -> {
            Row(modifier = Modifier.fillMaxSize()) {
                Sidebar(onNavigate = { navigateTo(it) }, currentScreen = ScreenState.FAVORITES)
                FavoritesScreen(
                    onMovieClick = { movie ->
                        selectedMovie = movie
                        navigateTo(ScreenState.DETAILS)
                    }
                )
            }
        }
        
        ScreenState.SEARCH -> {
            SearchScreen(
                initialQuery = savedSearchQuery,
                initialResults = savedSearchResults,
                initialType = savedIsMovieSearch,
                onStateChange = { q, r, t -> 
                    savedSearchQuery = q
                    savedSearchResults = r
                    savedIsMovieSearch = t
                },
                onMovieSelected = { movie ->
                    selectedMovie = movie
                    navigateTo(ScreenState.DETAILS)
                },
                onBack = { goBack() }
            )
        }
        
        ScreenState.DETAILS -> {
            if (selectedMovie != null) {
                DetailsScreen(
                    movie = selectedMovie!!,
                    onPlayClick = {
                        if (selectedMovie!!.media_type == "tv") navigateTo(ScreenState.SEASONS)
                        else navigateTo(ScreenState.SELECTION)
                    },
                    onBack = { goBack() }
                )
            }
        }

        ScreenState.SEASONS -> {
            if (selectedMovie != null) {
                SeasonSelectionScreen(
                    tmdbId = selectedMovie!!.id,
                    showTitle = selectedMovie!!.getDisplayTitle(),
                    onSeasonSelected = { seasonNum ->
                        selectedSeasonNum = seasonNum
                        navigateTo(ScreenState.EPISODES)
                    },
                    onBack = { goBack() }
                )
            }
        }

        ScreenState.EPISODES -> {
            if (selectedMovie != null) {
                EpisodeSelectionScreen(
                    tmdbId = selectedMovie!!.id,
                    showTitle = selectedMovie!!.getDisplayTitle(),
                    seasonNumber = selectedSeasonNum,
                    listState = episodesListState,
                    onEpisodeSelected = { epNum ->
                        selectedEpisodeNum = epNum
                        navigateTo(ScreenState.SELECTION)
                    },
                    onBack = { goBack() }
                )
            }
        }

        ScreenState.SELECTION -> {
            if (selectedMovie != null) {
                // INYECCIÓN DEL CEREBRO JS
                SourceSelectionScreen(
                    tmdbId = selectedMovie!!.id,
                    title = selectedMovie!!.getDisplayTitle(),
                    originalTitle = selectedMovie!!.getOriginalTitleSafe(),
                    year = selectedMovie!!.getYearSafe(),
                    isMovie = selectedMovie!!.media_type != "tv",
                    season = selectedSeasonNum, 
                    episode = selectedEpisodeNum,
                    providerManager = providerManager, // <--- AQUÍ
                    onLinkSelected = { url, _ ->
                        videoUrlToPlay = url
                        navigateTo(ScreenState.PLAYER)
                    },
                    onBack = { goBack() }
                )
            }
        }
        
        ScreenState.PLAYER -> {
            val playerTitle = if (selectedMovie?.media_type == "tv") 
                "${selectedMovie?.getDisplayTitle()} - T${selectedSeasonNum} E${selectedEpisodeNum}"
            else 
                selectedMovie?.getDisplayTitle() ?: "Película"

            PlayerScreen(
                videoUrl = videoUrlToPlay,
                videoTitle = playerTitle,
                tmdbId = selectedMovie!!.id,
                isMovie = selectedMovie!!.media_type != "tv",
                season = selectedSeasonNum,
                episode = selectedEpisodeNum,
                posterUrl = selectedMovie!!.getFullPosterUrl(),
                backdropUrl = selectedMovie!!.getFullBackdropUrl(),
                onBackPressed = { goBack() }
            )
        }

        ScreenState.MOVIES_CATALOG -> {
            Row(modifier = Modifier.fillMaxSize()) {
                Sidebar(onNavigate = { navigateTo(it) }, currentScreen = ScreenState.MOVIES_CATALOG)
                CatalogScreen(
                    isMovieCatalog = true, 
                    onPlayClick = { movie ->
                        selectedMovie = movie
                        navigateTo(ScreenState.DETAILS)
                    }, 
                    onGenreClick = { id, name ->
                        selectedGenreId = id
                        selectedGenreName = name
                        isGenreMovie = true
                        navigateTo(ScreenState.GENRE_GRID)
                    },
                    onBack = { goBack() }
                )
            }
        }
        
        ScreenState.SERIES_CATALOG -> {
             Row(modifier = Modifier.fillMaxSize()) {
                Sidebar(onNavigate = { navigateTo(it) }, currentScreen = ScreenState.SERIES_CATALOG)
                CatalogScreen(
                    isMovieCatalog = false, 
                    onPlayClick = { movie ->
                        selectedMovie = movie
                        navigateTo(ScreenState.DETAILS)
                    },
                    onGenreClick = { id, name ->
                        selectedGenreId = id
                        selectedGenreName = name
                        isGenreMovie = false
                        navigateTo(ScreenState.GENRE_GRID)
                    },
                    onBack = { goBack() }
                )
            }
        }

        ScreenState.GENRE_GRID -> {
            GenreGridScreen(
                genreId = selectedGenreId,
                genreName = selectedGenreName,
                isMovie = isGenreMovie,
                onMovieClick = { movie ->
                    selectedMovie = movie
                    navigateTo(ScreenState.DETAILS)
                },
                onBack = { goBack() }
            )
        }
    }
}
