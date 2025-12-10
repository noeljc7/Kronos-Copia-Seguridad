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

                let html = await bridge.fetchHtml(searchUrl);
                if (!html || html.length < 100) return [];

                const results = [];
                
                // Regex basado en tu captura: <div class="result-item"> ... <a href> ... <img alt="Titulo (Año)">
                const regex = /<div class="result-item">[\s\S]*?<a href="([^"]+)"[\s\S]*?<img src="([^"]+)"[^>]*?alt="([^"]+)"/g;
                
                let match;
                while ((match = regex.exec(html)) !== null) {
                    const url = match[1];
                    const img = match[2];
                    const fullTitle = match[3]; // "The Batman (2022)"
                    
                    let title = fullTitle;
                    let year = "0";

                    // Extraer año de los paréntesis (2022)
                    const yearMatch = fullTitle.match(/(.*)\s\((\d{4})\)$/);
                    if (yearMatch) {
                        title = yearMatch[1].trim();
                        year = yearMatch[2];
                    }

                    // Determinar tipo por la URL
                    let type = 'movie';
                    if (url.includes('/tvshows/') || url.includes('/series/') || url.includes('/episodes/')) {
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
                
                bridge.log("JS: ZonaAps encontró " + results.length + " resultados");
                bridge.onResult(JSON.stringify(results));
            } catch (e) { bridge.onResult("[]"); }
        },

        // ... (MANTÉN IGUAL LAS FUNCIONES resolveVideo Y resolveEpisode) ...
        resolveVideo: async function(url, type) {
            try {
                bridge.log("JS: Extrayendo: " + url);
                const html = await bridge.fetchHtml(url);
                const servers = [];
                
                const iframeRegex = /<iframe[^>]*src=["']([^"']+)["'][^>]*>/g;
                let m;
                while ((m = iframeRegex.exec(html)) !== null) {
                    let src = m[1];
                    if(src.startsWith("//")) src = "https:"+src;
                    
                    if(src.includes('facebook') || src.includes('twitter')) continue;
                    
                    let name = "Server";
                    let requiresWeb = true;
                    
                    if(src.includes('zonaaps')) name = "ZonaPlayer";
                    else if(src.includes('embed69')) { name="Embed69"; requiresWeb=false; }
                    else if(src.includes('waaw')) name="Waaw";
                    else if(src.includes('filemoon')) name="Filemoon";
                    else if(src.includes('vidhide')) { name="Vidhide"; requiresWeb=false; }
                    else if(src.includes('streamwish')) name="Streamwish";
                    else if(src.includes('youtube')) name="Trailer";

                    if(name !== "Trailer") {
                        servers.push({server: name, lang: "Latino", url: src, requiresWebView: requiresWeb});
                    }
                }
                bridge.onResult(JSON.stringify(servers));
            } catch(e) { bridge.onResult("[]"); }
        },

        resolveEpisode: async function(url, season, episode) {
            try {
                bridge.log("JS: Resolviendo episodio: " + url);
                if (url.includes('/tvshows/')) {
                    const html = await bridge.fetchHtml(url);
                    // Buscar link tipo: .../episodes/titulo-1x7/
                    const epRegex = new RegExp(`href="([^"]+\/episodes\/[^"]*?${season}x${episode}[^"]*)"`, "i");
                    const match = html.match(epRegex);
                    if(match) await this.resolveVideo(match[1], 'tv');
                    else {
                        // Fallback adivinanza
                        const slug = url.split('/tvshows/')[1].replace('/','');
                        await this.resolveVideo(`${this.baseUrl}/episodes/${slug}-${season}x${episode}/`, 'tv');
                    }
                } else {
                    await this.resolveVideo(url, 'tv');
                }
            } catch (e) { bridge.onResult("[]"); }
        }
    };

    if (typeof KronosEngine !== 'undefined') {
        KronosEngine.providers[provider.id] = provider;
    }
})();
