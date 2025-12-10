package com.kronos.tv

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.lifecycle.lifecycleScope
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.kronos.tv.engine.ScriptEngine
import com.kronos.tv.network.TmdbMovie
import com.kronos.tv.providers.ProviderManager 
import com.kronos.tv.ui.*
import kotlinx.coroutines.launch

enum class ScreenState { 
    HOME, DETAILS, SELECTION, PLAYER, SEARCH, SEASONS, EPISODES, 
    MOVIES_CATALOG, SERIES_CATALOG, GENRE_GRID, FAVORITES 
}

class MainActivity : ComponentActivity() {
@OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. INICIALIZAR EL MOTOR
        ScriptEngine.initialize(this)

        // 2. INSTANCIAR EL GESTOR DE PROVEEDORES
        // Esto carga primero el script 'sololatino.js' local (assets) como respaldo rápido
        val providerManager = ProviderManager(this)

        // 3. ACTIVAR MODO NUBE (DESCOMENTADO) ☁️
        // Esto descarga el manifest y actualiza los scripts desde GitHub en segundo plano
        lifecycleScope.launch {
            // Asegúrate que esta URL sea la RAW correcta de tu archivo manifest.json
            val manifestUrl = "https://raw.githubusercontent.com/noeljc7/Kronos-Copia-Seguridad/refs/heads/main/kronos_script/manifest.json"
            
            Log.d("KRONOS", "🌍 Iniciando actualización remota...")
            ProviderManager.loadRemoteProviders(manifestUrl)
            Log.d("KRONOS", "✅ Proceso de actualización remota finalizado.")
        }

        // --- CRASH HANDLER (Manejador de errores) ---
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            Log.e("KronosCrash", "CRASH DETECTADO: ${throwable.message}", throwable)
            
            val intent = Intent(this, com.kronos.tv.ui.CrashActivity::class.java).apply {
                putExtra("error", Log.getStackTraceString(throwable))
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(intent)
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(1)
        }

        setContent {
            Surface(modifier = Modifier.fillMaxSize(), shape = RectangleShape) {
                // Pasamos el providerManager (que ya tiene lo local y lo remoto) a la navegación
                AppNavigation(providerManager)
            }
        }
    }
}

@Composable
fun AppNavigation(providerManager: ProviderManager) { // <--- Recibimos el Manager aquí
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
                        media_type = if(historyItem.isMovie) "movie" else "tv"
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
                // --- ACTUALIZACIÓN ---
                SourceSelectionScreen(
                    tmdbId = selectedMovie!!.id,
                    title = selectedMovie!!.getDisplayTitle(),
                    
                    // Pasamos los nuevos datos usando los helpers seguros
                    originalTitle = selectedMovie!!.getOriginalTitleSafe(), 
                    year = selectedMovie!!.getYearSafe(),
                    
                    isMovie = selectedMovie!!.media_type != "tv",
                    season = selectedSeasonNum, 
                    episode = selectedEpisodeNum,
                    providerManager = providerManager,
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
