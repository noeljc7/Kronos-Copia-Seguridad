(function() {
    const provider = {
        id: 'zonaaps',
        name: 'ZonaAps',
        baseUrl: 'https://zonaaps.com',
        headers: {'User-Agent': 'Mozilla/5.0 (Linux; Android 10)'},

        search: async function(query) {
            try {
                const cleanQuery = encodeURIComponent(query.replace(/[:\-\.]/g, ' '));
                const searchUrl = this.baseUrl + "/?s=" + cleanQuery;
                bridge.log("JS: Buscando: " + searchUrl);

                let html = "";
                try { html = await bridge.fetchHtml(searchUrl); } catch (e) { return []; }
                if (!html || html.length < 100) return [];

                const results = [];
                
                // --- REGEX BASADO EN TU CAPTURA DE PANTALLA ---
                // Estructura: <div class="result-item"> ... <a href="..."> ... <img src="..." alt="Titulo (Año)"> ... <span class="type">
                
                // 1. Buscamos cada bloque "result-item" individualmente
                const itemRegex = /<div class="result-item">([\s\S]*?)<\/article>/g;
                let itemMatch;

                while ((itemMatch = itemRegex.exec(html)) !== null) {
                    const content = itemMatch[1]; // El HTML de una sola película

                    // 2. Extraer URL
                    const urlMatch = content.match(/href="([^"]+)"/);
                    
                    // 3. Extraer Imagen y Datos del ALT
                    // El alt suele ser "Titulo (Año)" o solo "Titulo"
                    const imgMatch = content.match(/<img[^>]+src="([^"]+)"[^>]+alt="([^"]+)"/);
                    
                    // 4. Extraer Tipo (movies/tvshows)
                    // Busca <span class="movies"> o <span class="tvshows">
                    const typeMatch = content.match(/<span class="(movies|tvshows|series)"/);

                    if (urlMatch && imgMatch) {
                        const url = urlMatch[1];
                        const img = imgMatch[1];
                        const rawTitle = imgMatch[2]; // Ej: "The Batman (2022)"
                        
                        let title = rawTitle;
                        let year = "0";

                        // Separar Año del Título: "Batman (2022)" -> Title: "Batman", Year: "2022"
                        const yearExtract = rawTitle.match(/(.*)\s\((\d{4})\)$/);
                        if (yearExtract) {
                            title = yearExtract[1].trim();
                            year = yearExtract[2];
                        }

                        // Determinar tipo
                        let type = 'movie';
                        if (typeMatch && (typeMatch[1] === 'tvshows' || typeMatch[1] === 'series')) {
                            type = 'tv';
                        } else if (url.includes('/tvshows/') || url.includes('/episodes/')) {
                            type = 'tv';
                        }

                        results.push({
                            title: title,
                            url: url,
                            img: img,
                            id: url,
                            type: type,
                            year: year
                        });
                    }
                }

                bridge.onResult(JSON.stringify(results));
            } catch (e) { bridge.onResult("[]"); }
        },

        resolveVideo: async function(url, type) {
            try {
                bridge.log("JS: Extrayendo de: " + url);
                const html = await bridge.fetchHtml(url);
                const servers = [];

                // 1. EXTRAER IFRAMES (ZonaAps usa varios iframes directos)
                const iframeRegex = /<iframe[^>]*src=["']([^"']+)["'][^>]*>/g;
                let iframeMatch;
                
                while ((iframeMatch = iframeRegex.exec(html)) !== null) {
                    let src = iframeMatch[1];
                    
                    // Limpieza de URL
                    if (src.startsWith("//")) src = "https:" + src;
                    
                    // Ignorar redes sociales
                    if (src.includes('facebook') || src.includes('twitter')) continue;

                    let name = "Server";
                    let requiresWeb = true; // Por seguridad, asumimos WebPlayer primero

                    // Identificar servidor
                    if (src.includes('zonaaps-player')) name = "ZonaPlayer";
                    else if (src.includes('youtube')) name = "Trailer";
                    else if (src.includes('embed69')) { name = "Embed69"; requiresWeb = false; } 
                    else if (src.includes('waaw')) name = "Waaw";
                    else if (src.includes('filemoon')) name = "Filemoon";
                    else if (src.includes('streamwish')) name = "Streamwish";
                    else if (src.includes('vidhide')) { name = "Vidhide"; requiresWeb = false; }

                    if (name !== "Trailer") {
                        servers.push({
                            server: name,
                            lang: "Latino", // Asumimos Latino por defecto
                            url: src,
                            requiresWebView: requiresWeb
                        });
                    }
                }

                bridge.onResult(JSON.stringify(servers));
            } catch (e) { bridge.onResult("[]"); }
        },

        resolveEpisode: async function(url, season, episode) {
            // Lógica para Series en ZonaAps (Basada en tu archivo ZNA Series.txt)
            try {
                bridge.log("JS: Resolviendo episodio: " + url);
                
                // Caso 1: Nos llega la URL de la Ficha de la Serie
                if (url.includes('/tvshows/')) {
                    const html = await bridge.fetchHtml(url);
                    
                    // ZonaAps lista los episodios con enlaces directos
                    // Buscamos algo que contenga "1x7" o "1x07"
                    // Formato posible: <a href="..."> ... 1x7 ... </a>
                    
                    const epPad = episode < 10 ? "0" + episode : episode;
                    // Regex busca "1x7" o "1x07" dentro del texto de un enlace
                    const linkRegex = new RegExp(`<a[^>]+href=["']([^"']+)["'][^>]*>.*?(?:${season}x${episode}|${season}x${epPad}).*?<\/a>`, "i");
                    
                    const match = html.match(linkRegex);
                    
                    if (match) {
                        const epUrl = match[1];
                        bridge.log("JS: Link episodio encontrado: " + epUrl);
                        await this.resolveVideo(epUrl, 'tv');
                    } else {
                        // Intento de adivinanza (Fallback)
                        // De: https://zonaaps.com/tvshows/loki/ 
                        // A:  https://zonaaps.com/episodes/loki-1x7/
                        // Limpiamos la url base para sacar el "slug"
                        const slug = url.split('/tvshows/')[1].replace('/', ''); 
                        const guessUrl = `${this.baseUrl}/episodes/${slug}-${season}x${episode}/`;
                        
                        bridge.log("JS: Intentando URL directa: " + guessUrl);
                        await this.resolveVideo(guessUrl, 'tv');
                    }
                } 
                // Caso 2: Ya es la URL del episodio
                else {
                    await this.resolveVideo(url, 'tv');
                }
            } catch (e) { bridge.onResult("[]"); }
        }
    };

    if (typeof KronosEngine !== 'undefined') {
        KronosEngine.providers[provider.id] = provider;
    }
})();
