@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kronos.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import coil.compose.AsyncImage
import com.kronos.tv.providers.ProviderManager
import com.kronos.tv.providers.SourceLink
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun SourceSelectionScreen(
    tmdbId: Int,
    title: String,
    originalTitle: String,
    year: Int,
    isMovie: Boolean,
    season: Int,
    episode: Int,
    providerManager: ProviderManager,
    onLinkSelected: (String, Boolean) -> Unit,
    onBack: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var links by remember { mutableStateOf(emptyList<SourceLink>()) }
    var isResolving by remember { mutableStateOf(false) }
    
    val firstLinkFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        links = providerManager.getLinks(
            tmdbId = tmdbId,
            title = title,
            originalTitle = originalTitle,
            isMovie = isMovie,
            year = year,
            season = season,
            episode = episode
        )
        isLoading = false
        delay(200)
        try { firstLinkFocus.requestFocus() } catch(e:Exception){}
    }

    Row(modifier = Modifier.fillMaxSize().background(Color(0xFF141414))) {
        // PANEL IZQUIERDO (INFO)
        Column(
            modifier = Modifier
                .width(350.dp)
                .fillMaxHeight()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(40.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(title, style = MaterialTheme.typography.displaySmall, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(10.dp))
            
            if (!isMovie) {
                Text("Temporada $season - Episodio $episode", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
            } else if (year > 0) {
                Text("Año: $year", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
            }
            
            Spacer(modifier = Modifier.height(40.dp))
            Button(
                onClick = onBack,
                colors = ButtonDefaults.colors(containerColor = Color(0xFF333333), focusedContainerColor = Color(0xFFE50914)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.ArrowBack, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Volver")
            }
        }

        // PANEL DERECHO (LISTA)
        Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(40.dp), contentAlignment = Alignment.Center) {
            if (isLoading) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFFE50914))
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("Buscando servidores...", color = Color.Gray)
                    Text("Original: $originalTitle", color = Color.DarkGray, style = MaterialTheme.typography.labelSmall)
                }
            } else if (links.isEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Warning, null, tint = Color(0xFFEF5350), modifier = Modifier.size(48.dp))
                    Text("No se encontraron servidores", color = Color.White, modifier = Modifier.padding(top=10.dp))
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(links) { index, link ->
                        val modifier = if (index == 0) Modifier.focusRequester(firstLinkFocus) else Modifier
                        SourceCardPremium(
                            link = link,
                            onClick = {
                                if (!link.isDirect) isResolving = true
                                onLinkSelected(link.url, link.requiresWebView)
                            },
                            modifier = modifier
                        )
                    }
                }
            }
        }
    }
    
    // OVERLAY RESOLVING
    if (isResolving) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.8f)), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SourceCardPremium(link: SourceLink, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.02f else 1f, label = "scale")
    val bgColor = if (isFocused) Color(0xFF353535) else Color(0xFF1F1F1F)
    val borderColor = if (isFocused) Color.White else Color.Transparent
    
    // Si requiere WebView (Waaw, Filemoon) mostramos "WEB", si no "VIDEO"
    val badgeText = if (link.requiresWebView) "WEB" else "VIDEO"
    val badgeColor = if (link.requiresWebView) Color(0xFFFFA726) else Color(0xFF66BB6A)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(bgColor, RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
            // Bandera + Idioma
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(140.dp)) {
                AsyncImage(
                    model = getFlagUrl(link.language),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp).clip(RoundedCornerShape(2.dp)),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(cleanLanguageText(link.language), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
            }
            
            // Info Central
            Column(modifier = Modifier.weight(1f)) {
                Text(link.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                Text("${link.quality} • ${link.provider}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
            
            // Badge Tipo
            Box(modifier = Modifier.background(badgeColor.copy(alpha=0.2f), RoundedCornerShape(4.dp)).padding(horizontal=8.dp, vertical=2.dp)) {
                Text(badgeText, color = badgeColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// --- UTILS ---
fun getFlagUrl(language: String): String {
    val lang = language.lowercase()
    val code = when {
        lang.contains("latino") || lang.contains("mx") -> "mx"
        lang.contains("castellano") || lang.contains("es") -> "es"
        lang.contains("english") || lang.contains("en") || lang.contains("sub") -> "us"
        lang.contains("port") || lang.contains("br") -> "br"
        lang.contains("jap") -> "jp"
        else -> "un"
    }
    return "https://flagcdn.com/w80/$code.png"
}

fun cleanLanguageText(language: String): String {
    val cleaned = language.replace(Regex("[^\\p{L}\\p{N}\\s]"), "").trim()
    return if (cleaned.isNotEmpty()) {
        cleaned.lowercase().split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.titlecase(Locale.getDefault()) } }
    } else "Desconocido"
}
