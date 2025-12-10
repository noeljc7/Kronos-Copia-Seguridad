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
                bridge.log("JS [ZonaAps]: Buscando en: " + searchUrl); // LOG

                let html = await bridge.fetchHtml(searchUrl);
                if (!html || html.length < 100) {
                    bridge.log("JS [ZonaAps]: HTML vacío o error de carga"); // LOG
                    return [];
                }

                const results = [];
                // Regex ajustado (busca dentro de div.result-item)
                const regex = /<div class="result-item">[\s\S]*?<a href="([^"]+)"[\s\S]*?<img src="([^"]+)"[^>]*?alt="([^"]+)"/g;
                
                let match;
                while ((match = regex.exec(html)) !== null) {
                    const url = match[1];
                    const img = match[2];
                    const fullTitle = match[3]; // "The Batman (2022)"
                    
                    let title = fullTitle;
                    let year = "0";

                    const yearExtract = fullTitle.match(/(.*)\s\((\d{4})\)$/);
                    if (yearExtract) {
                        title = yearExtract[1].trim();
                        year = yearExtract[2];
                    }

                    let type = 'movie';
                    if (url.includes('/tvshows/') || url.includes('/series/') || url.includes('/episodes/')) {
                        type = 'tv';
                    }

                    // LOG PARA VER QUÉ ENCUENTRA
                    bridge.log("JS [ZonaAps]: Encontrado -> " + title + " (" + year + ") URL: " + url);

                    results.push({
                        title: title,
                        url: url,
                        img: img,
                        id: url,
                        type: type,
                        year: year
                    });
                }
                bridge.onResult(JSON.stringify(results));
            } catch (e) { 
                bridge.log("JS [ZonaAps]: Error en search -> " + e.message);
                bridge.onResult("[]"); 
            }
        },

        resolveVideo: async function(url, type) {
            try {
                bridge.log("JS [ZonaAps]: Extrayendo video de -> " + url); // LOG CRÍTICO
                const html = await bridge.fetchHtml(url);
                const servers = [];

                // BUSCAR IFRAMES
                const iframeRegex = /<iframe[^>]*src=["']([^"']+)["'][^>]*>/g;
                let iframeMatch;
                let foundAny = false;
                
                while ((iframeMatch = iframeRegex.exec(html)) !== null) {
                    let src = iframeMatch[1];
                    if (src.startsWith("//")) src = "https:" + src;
                    
                    // LOG DE CADA IFRAME ENCONTRADO
                    bridge.log("JS [ZonaAps]: Iframe detectado -> " + src);
                    foundAny = true;

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

                    if (name !== "Trailer") {
                        servers.push({
                            server: name,
                            lang: "Latino", 
                            url: src,
                            requiresWebView: requiresWeb
                        });
                    }
                }

                if (!foundAny) {
                    bridge.log("JS [ZonaAps]: ⚠️ No se encontraron iframes en el HTML.");
                    // Si no hay iframes, a veces usan un sistema raro de "options"
                    // Intento de ver si hay un JSON de opciones oculto
                    if (html.includes('data-type')) {
                         bridge.log("JS [ZonaAps]: Posible sistema Dooplay (data-type detectado).");
                    }
                }

                bridge.onResult(JSON.stringify(servers));
            } catch (e) { 
                bridge.log("JS [ZonaAps]: Error en resolveVideo -> " + e.message);
                bridge.onResult("[]"); 
            }
        },

        resolveEpisode: async function(url, season, episode) {
            try {
                bridge.log("JS [ZonaAps]: Resolviendo episodio: " + url);
                
                if (url.includes('/tvshows/')) {
                    const html = await bridge.fetchHtml(url);
                    const epPad = episode < 10 ? "0" + episode : episode;
                    
                    // Buscamos enlace que contenga 1x7 o 1x07
                    // LOG DE AYUDA
                    bridge.log("JS [ZonaAps]: Buscando enlace con texto: " + season + "x" + episode);

                    const epRegex = new RegExp(`<a[^>]+href=["']([^"']+)["'][^>]*>.*?(?:${season}x${episode}|${season}x${epPad}).*?<\/a>`, "i");
                    const match = html.match(epRegex);
                    
                    if (match) {
                        const epUrl = match[1];
                        bridge.log("JS [ZonaAps]: Link episodio encontrado -> " + epUrl);
                        await this.resolveVideo(epUrl, 'tv');
                    } else {
                        bridge.log("JS [ZonaAps]: No se encontró enlace directo en la lista.");
                        const slug = url.split('/tvshows/')[1].replace('/', ''); 
                        const guessUrl = `${this.baseUrl}/episodes/${slug}-${season}x${episode}/`;
                        bridge.log("JS [ZonaAps]: Probando URL adivinada -> " + guessUrl);
                        await this.resolveVideo(guessUrl, 'tv');
                    }
                } else {
                    await this.resolveVideo(url, 'tv');
                }
            } catch (e) { 
                bridge.log("JS [ZonaAps]: Error resolveEpisode -> " + e.message);
                bridge.onResult("[]"); 
            }
        }
    };

    if (typeof KronosEngine !== 'undefined') {
        KronosEngine.providers[provider.id] = provider;
    }
})();
