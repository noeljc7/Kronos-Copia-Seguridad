(function() {
    const provider = {
        id: 'zonaaps',
        name: 'ZonaAps',
        baseUrl: 'https://zonaaps.com',
        headers: {
            'User-Agent': 'Mozilla/5.0 (Linux; Android 10)',
            'X-Requested-With': 'XMLHttpRequest', 
            'Referer': 'https://zonaaps.com/'
        },

        search: async function(query) {
            try {
                const cleanQuery = encodeURIComponent(query.replace(/[:\-\.]/g, ' '));
                const searchUrl = this.baseUrl + "/?s=" + cleanQuery;
                bridge.log("JS [ZNA]: Buscando: " + searchUrl);

                let html = await bridge.fetchHtml(searchUrl);
                if (!html || html.length < 100) return [];

                const results = [];
                const regex = /<div class="result-item">[\s\S]*?<a href="([^"]+)"[\s\S]*?<img src="([^"]+)"[^>]*?alt="([^"]+)"/g;
                
                let match;
                while ((match = regex.exec(html)) !== null) {
                    const url = match[1];
                    const img = match[2];
                    const fullTitle = match[3];
                    
                    let title = fullTitle;
                    let year = "0";
                    const yearMatch = fullTitle.match(/(.*)\s\((\d{4})\)$/);
                    if (yearMatch) { 
                        title = yearMatch[1].trim(); 
                        year = yearMatch[2]; 
                    }
                    
                    let type = (url.includes('/tvshows/') || url.includes('/episodes/')) ? 'tv' : 'movie';
                    
                    results.push({ 
                        title: title, 
                        url: url, 
                        img: img, 
                        id: url, 
                        type: type, 
                        year: year 
                    });
                }
                
                // RETORNAR ARRAY
                return results;

            } catch (e) { return []; }
        },

        resolveVideo: async function(url, type) {
            try {
                bridge.log("JS [ZNA]: Resolviendo: " + url);
                const html = await bridge.fetchHtml(url);
                const servers = [];

                // 1. Extraer IDs de Dooplay (API)
                // Buscamos <li data-post="123" data-nume="1" ...>
                const liRegex = /<li[^>]+data-post=['"](\d+)['"][^>]*data-type=['"]([^"']+)['"][^>]*data-nume=['"](\d+)['"][^>]*>([\s\S]*?)<\/li>/g;
                let match;
                const tasks = [];

                while ((match = liRegex.exec(html)) !== null) {
                    const postId = match[1];
                    const pType = match[2];
                    const nume = match[3];
                    const content = match[4];

                    if (nume === "trailer") continue;

                    let serverName = "Opción " + nume;
                    const titleMatch = content.match(/<span class=['"]title['"]>(.*?)<\/span>/);
                    if (titleMatch) serverName = titleMatch[1].replace(/🔒/g, "").trim();

                    let lang = "Latino";
                    if (content.toLowerCase().includes("sub")) lang = "Subtitulado";
                    else if (content.toLowerCase().includes("castellano")) lang = "Castellano";

                    // URL API
                    const apiUrl = `${this.baseUrl}/wp-json/dooplayer/v2/${postId}/${pType}/${nume}`;
                    
                    // Añadimos la promesa al array de tareas
                    tasks.push(this.fetchApiLink(apiUrl, serverName, lang));
                }

                if (tasks.length > 0) {
                    // Esperamos todas las peticiones a la vez (Paralelismo dentro de JS)
                    const resolved = await Promise.all(tasks);
                    resolved.forEach(s => { 
                        if(s) servers.push(s); 
                    });
                }

                // 2. Respaldo Web si falla API
                if (servers.length === 0) {
                    servers.push({
                        server: "Ver en Web",
                        lang: "Latino",
                        url: url,
                        quality: "HD",
                        requiresWebView: true
                    });
                }

                // RETORNAR ARRAY
                return servers;

            } catch (e) { return []; }
        },

        // Helper interno (no es necesario exportarlo)
        fetchApiLink: async function(apiUrl, serverName, lang) {
            try {
                const jsonStr = await bridge.fetchHtml(apiUrl);
                if (!jsonStr || jsonStr.trim().startsWith("<")) return null;

                const json = JSON.parse(jsonStr);
                const targetUrl = json.embed_url || json.u;

                if (targetUrl) {
                    const isDirect = targetUrl.endsWith(".mp4") || targetUrl.endsWith(".m3u8");
                    return {
                        server: serverName,
                        lang: lang,
                        url: targetUrl,
                        quality: "HD",
                        requiresWebView: !isDirect,
                        isDirect: isDirect
                    };
                }
            } catch (e) {}
            return null;
        },

        resolveEpisode: async function(url, season, episode) {
            // ZonaAps suele listar episodios abajo o tener URLs predecibles
            try {
                // Si es URL de serie (no episodio), intentamos buscar el episodio
                if (url.includes('/tvshows/')) {
                    const html = await bridge.fetchHtml(url);
                    // Regex flexible para encontrar el link del episodio
                    const epPad = episode < 10 ? "0" + episode : episode;
                    // Buscamos algo como "1x1" o "1x01" en los hrefs
                    const regex = new RegExp(`href=["']([^"']*?(?:${season}x${episode}|${season}x${epPad})[^"']*)["']`, "i");
                    const match = html.match(regex);
                    
                    if (match) {
                        return await this.resolveVideo(match[1], 'tv');
                    }
                } 
                // Si ya es URL de episodio o fallback
                return await this.resolveVideo(url, 'tv');
            } catch (e) { return []; }
        }
    };

    if (typeof KronosEngine !== 'undefined') {
        KronosEngine.providers[provider.id] = provider;
    }
})();
