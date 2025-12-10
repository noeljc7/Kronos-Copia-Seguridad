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
                bridge.log("JS: Buscando en ZonaAps: " + searchUrl);

                let html = await bridge.fetchHtml(searchUrl);
                if (!html || html.length < 100) return [];

                const results = [];
                
                // --- REGEX CORREGIDO (Busca los 3 datos CLAVE) ---
                // Captura: 1: URL, 2: Imagen SRC, 3: Imagen ALT (Título + Año)
                const itemRegex = /<article[\s\S]*?<a href=["']([^"']+)["'][^>]*>[\s\S]*?<img src=["']([^"']+)["'][^>]*?alt=["']([^"']+)["'][^>]*>[\s\S]*?<span class="(movies|tvshows|series)"/g;
                
                let match;
                while ((match = itemRegex.exec(html)) !== null) {
                    const url = match[1];
                    const img = match[2];
                    const rawTitle = match[3]; // Ej: "The Batman (2022)"
                    const typeClass = match[4];
                    
                    let title = rawTitle;
                    let year = "0";

                    // 4. Separar Año del Título
                    const yearExtract = rawTitle.match(/(.*)\s\((\d{4})\)$/);
                    if (yearExtract) {
                        title = yearExtract[1].trim();
                        year = yearExtract[2];
                    }

                    // 5. Determinar tipo (Prioriza URL si la clase es genérica)
                    let type = 'movie';
                    if (typeClass === 'tvshows' || typeClass === 'series') {
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
                
                // Si la App falló en la búsqueda directa, siempre puede intentar el JSON LD como respaldo.
                // Aunque este Regex debería ser suficiente.

                bridge.onResult(JSON.stringify(results));
            } catch (e) { bridge.onResult("[]"); }
        },

        resolveVideo: async function(url, type) {
            // ... (El código de resolveVideo y resolveEpisode sigue siendo el mismo) ...
            try {
                bridge.log("JS: Extrayendo de: " + url);
                const html = await bridge.fetchHtml(url);
                const servers = [];

                const iframeRegex = /<iframe[^>]*src=["']([^"']+)["'][^>]*>/g;
                let iframeMatch;
                
                while ((iframeMatch = iframeRegex.exec(html)) !== null) {
                    let src = iframeMatch[1];
                    if (src.startsWith("//")) src = "https:" + src;
                    if (src.includes('facebook') || src.includes('twitter')) continue;

                    let name = "Server";
                    let requiresWeb = true; 

                    if (src.includes('zonaaps-player')) name = "ZonaPlayer";
                    else if (src.includes('youtube')) name = "Trailer";
                    else if (src.includes('embed69')) { name = "Embed69"; requiresWeb = false; } 
                    else if (src.includes('waaw')) name = "Waaw";
                    else if (src.includes('filemoon')) name = "Filemoon";
                    else if (src.includes('streamwish')) name = "Streamwish";
                    else if (src.includes('vidhide')) { name = "Vidhide"; requiresWeb = false; }

                    if (name !== "Trailer" && !servers.some(s => s.url === src)) { // Evitar duplicados
                        servers.push({
                            server: name,
                            lang: "Latino", 
                            url: src,
                            requiresWebView: requiresWeb
                        });
                    }
                }
                bridge.onResult(JSON.stringify(servers));
            } catch (e) { bridge.onResult("[]"); }
        },

        resolveEpisode: async function(url, season, episode) {
            try {
                bridge.log("JS: Resolviendo episodio: " + url);
                
                if (url.includes('/tvshows/')) {
                    const html = await bridge.fetchHtml(url);
                    const epPad = episode < 10 ? "0" + episode : episode;
                    // Regex busca enlace que contenga el SxE
                    const epRegex = new RegExp(`<a[^>]+href=["']([^"']+)["'][^>]*>.*?(?:${season}x${episode}|${season}x${epPad}).*?<\/a>`, "i");
                    const match = html.match(epRegex);
                    
                    if (match) {
                        const epUrl = match[1];
                        await this.resolveVideo(epUrl, 'tv');
                    } else {
                        // Fallback: Adivinar la URL
                        const slug = url.split('/tvshows/')[1].replace('/', ''); 
                        const guessUrl = `${this.baseUrl}/episodes/${slug}-${season}x${episode}/`;
                        await this.resolveVideo(guessUrl, 'tv');
                    }
                } 
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
