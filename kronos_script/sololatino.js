// KRONOS PROVIDER: SoloLatino v14.0 
(function() {
    const provider = {
        id: 'sololatino',
        name: 'SoloLatino',
        baseUrl: 'https://sololatino.net',
        headers: {
            'User-Agent': 'Mozilla/5.0 (Linux; Android 10)',
            'Referer': 'https://sololatino.net/'
        },

        search: async function(query) {
            try {
                // 1. LIMPIEZA INTELIGENTE DEL TÍTULO
                // Quitamos años, "la película", "parte", etc. para que la búsqueda sea más efectiva
                let smartQuery = query
                    .replace(/\(\d{4}\)/g, '') // Quitar año (2025)
                    .replace(/la película/gi, '')
                    .replace(/el arco/gi, '')
                    .replace(/[:\-\.]/g, ' ') // Quitar signos raros
                    .trim();

                // Si el título quedó muy corto, usamos el original, si no, usamos el limpio
                const finalQuery = (smartQuery.length > 2) ? smartQuery : query;
                
                const searchUrl = this.baseUrl + "/?s=" + encodeURIComponent(finalQuery);
                bridge.log("JS [SL]: Buscando limpio: " + finalQuery);

                let html = await bridge.fetchHtml(searchUrl);
                const results = [];

                // 2. REGEX PARA RESULTADOS (Dooplay Clásico)
                const regex = /<article[^>]*>[\s\S]*?<a href="([^"]+)"[^>]*>[\s\S]*?<img src="([^"]+)"[^>]*?alt="([^"]+)"/g;
                
                let match;
                while ((match = regex.exec(html)) !== null) {
                    const url = match[1];
                    const img = match[2];
                    const fullTitle = match[3];
                    
                    let title = fullTitle;
                    let year = "0";

                    // Extraer año si existe
                    const yearMatch = fullTitle.match(/(\d{4})/);
                    if (yearMatch) {
                        year = yearMatch[1];
                        title = fullTitle.replace(year, '').replace('()', '').trim();
                    }

                    // Filtrar si es película o serie
                    let type = 'movie';
                    if (url.includes('/series/') || url.includes('/episodios/') || url.includes('/animes/')) {
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
                
                bridge.onResult(JSON.stringify(results));
            } catch (e) { bridge.onResult("[]"); }
        },

        resolveVideo: async function(url, type) {
            try {
                bridge.log("JS [SL]: Extrayendo de: " + url);
                const html = await bridge.fetchHtml(url);
                const servers = [];

                // 1. BUSCAR IFRAMES DIRECTOS (SoloLatino suele tenerlos visibles)
                const iframeRegex = /<iframe[^>]*src=["']([^"']+)["'][^>]*>/g;
                let m;
                while ((m = iframeRegex.exec(html)) !== null) {
                    let src = m[1];
                    if (src.startsWith("//")) src = "https:" + src;
                    
                    // Filtros de basura
                    if (src.includes('facebook') || src.includes('twitter')) continue;

                    let name = "Server";
                    let requiresWeb = true; // Por defecto webview

                    if (src.includes('sololatino')) name = "Nativo";
                    else if (src.includes('embed69')) { name = "Embed69"; requiresWeb = false; }
                    else if (src.includes('streamwish')) name = "Streamwish";
                    else if (src.includes('filemoon')) name = "Filemoon";
                    else if (src.includes('waaw')) name = "Waaw";
                    else if (src.includes('dood')) name = "Doodstream";

                    servers.push({
                        server: name,
                        lang: "Latino", // SoloLatino es casi 100% Latino
                        url: src,
                        requiresWebView: requiresWeb
                    });
                }

                // 2. BUSCAR ENLACES EN LISTA (Si están ocultos en botones)
                // Estructura típica: <li data-post="123" data-nume="1">
                if (servers.length === 0) {
                     // Si no encontramos iframes, probamos buscar la tabla de opciones
                     if (html.includes('id="playeroptionsul"') || html.includes('class="dooplay_player_option"')) {
                        bridge.log("JS [SL]: Menú de opciones detectado. Usando modo Web.");
                        servers.push({
                            server: "SoloLatino (Web)",
                            lang: "Latino",
                            url: url,
                            requiresWebView: true
                        });
                     }
                }

                bridge.onResult(JSON.stringify(servers));
            } catch (e) { bridge.onResult("[]"); }
        },

        resolveEpisode: async function(url, season, episode) {
            try {
                // SoloLatino usa urls tipo: /episodios/nombre-serie-1x1/
                if (url.includes('/series/')) {
                    const slug = url.split('/series/')[1].replace('/', '');
                    // Construcción manual de la URL del episodio
                    const episodeUrl = `${this.baseUrl}/episodios/${slug}-${season}x${episode}/`;
                    bridge.log("JS [SL]: Intentando episodio: " + episodeUrl);
                    await this.resolveVideo(episodeUrl, 'tv');
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
