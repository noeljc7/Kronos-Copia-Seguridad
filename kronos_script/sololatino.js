(function() {
    const provider = {
        id: 'sololatino',
        name: 'SoloLatino',
        baseUrl: 'https://sololatino.net',
        headers: {'User-Agent': 'Mozilla/5.0 (Linux; Android 10)'},

        search: async function(query) {
            try {
                // CAMBIO SOLICITADO: No limpiamos caracteres. Solo codificamos URL.
                // Esto permite buscar "Spider-Man" o "Avengers: Endgame" tal cual.
                const cleanQuery = encodeURIComponent(query.trim());
                const searchUrl = this.baseUrl + "/?s=" + cleanQuery;
                
                bridge.log("JS: Buscando Exacto: " + searchUrl);
                
                let html = "";
                try { html = await bridge.fetchHtml(searchUrl); } catch (e) { return []; }
                
                if (!html || html.length < 100) return [];
                
                const results = [];
                const articleRegex = /<article[^>]*class="item\s+(movies|tvshows)"[^>]*>([\s\S]*?)<\/article>/g;
                
                let match;
                while ((match = articleRegex.exec(html)) !== null) {
                    const content = match[2];
                    const urlMatch = content.match(/href="([^"]+)"/);
                    const imgMatch = content.match(/src="([^"]+)"/) || content.match(/data-src="([^"]+)"/) || content.match(/data-srcset="([^"\s]+)/);
                    const titleMatch = content.match(/<h3>([^<]+)<\/h3>/);
                    
                    let year = "0";
                    const yearMatch = content.match(/(\d{4})/);
                    if (yearMatch) year = yearMatch[1];
                    
                    if (urlMatch && titleMatch) {
                        results.push({
                            title: titleMatch[1].trim(), // El título original suele venir en el HTML
                            url: urlMatch[1],
                            img: imgMatch ? imgMatch[1] : "",
                            id: urlMatch[1],
                            type: match[1] === 'tvshows' ? 'tv' : 'movie',
                            year: year
                        });
                    }
                }
                
                // Fallback de redirección (Si hay coincidencia exacta)
                if (results.length === 0) {
                    const canonical = html.match(/<link\s+rel=["']canonical["']\s+href=["']([^"']+)["']/i);
                    const ogTitle = html.match(/<meta\s+property=["']og:title["']\s+content=["']([^"']+)["']/i);
                    
                    if (canonical && ogTitle) {
                        const directUrl = canonical[1];
                        if (directUrl.includes('/peliculas/') || directUrl.includes('/series/')) {
                            let rawTitle = ogTitle[1].replace(/^Ver\s+/i, '').replace(/\s+Online.*$/i, '').trim();
                            results.push({ 
                                title: rawTitle, 
                                url: directUrl, 
                                img: "", 
                                id: directUrl, 
                                type: directUrl.includes('/series/') ? 'tv' : 'movie', 
                                year: "0" 
                            });
                        }
                    }
                }
                
                return results;

            } catch (e) { 
                bridge.log("JS ERROR: " + e.message);
                return []; 
            }
        },

        resolveEpisode: async function(showUrl, season, episode) {
            try {
                bridge.log("JS: Buscando ep " + season + "x" + episode);
                const html = await bridge.fetchHtml(showUrl);
                const epPad = episode < 10 ? "0" + episode : episode;
                
                // Regex para capturar enlaces de episodios
                const regex = new RegExp(`href=["']([^"']*?(?:${season}x${episode}|${season}x${epPad})[^"']*)["']`, "i");
                const match = html.match(regex);
                
                if (match) {
                    return await this.resolveVideo(match[1], "tv");
                } 
                return [];
            } catch (e) { return []; }
        },

        resolveVideo: async function(url, type) {
            try {
                bridge.log("JS: Extrayendo servidores (API v2)...");
                const html = await bridge.fetchHtml(url);
                const servers = [];
                const tasks = [];

                // 1. ESTRATEGIA API (Dooplay v2) - La más efectiva para sacar TODOS los servidores
                const apiLiRegex = /<li[^>]+data-post=['"](\d+)['"][^>]*data-type=['"]([^"']+)['"][^>]*data-nume=['"](\d+)['"][^>]*>([\s\S]*?)<\/li>/g;
                let apiMatch;
                
                while ((apiMatch = apiLiRegex.exec(html)) !== null) {
                    const postId = apiMatch[1];
                    const pType = apiMatch[2];
                    const nume = apiMatch[3];
                    const content = apiMatch[4];

                    if (nume === "trailer") continue;

                    let serverName = "Opción " + nume;
                    const titleMatch = content.match(/<span class=['"]title['"]>(.*?)<\/span>/);
                    if (titleMatch) serverName = titleMatch[1].replace(/🔒/g, "").trim();

                    let lang = "Latino";
                    if (content.toLowerCase().includes("sub")) lang = "Subtitulado";
                    else if (content.toLowerCase().includes("castellano") || content.includes("es.png")) lang = "Castellano";

                    const apiUrl = `${this.baseUrl}/wp-json/dooplayer/v2/${postId}/${pType}/${nume}`;
                    tasks.push(this.fetchApiLink(apiUrl, serverName, lang));
                }

                // 2. ESTRATEGIA HTML (Respaldo)
                const staticLiRegex = /<li[^>]*onclick=["']go_to_player\(['"]([^'"]+)['"]\)/g;
                let staticMatch;
                while ((staticMatch = staticLiRegex.exec(html)) !== null) {
                    let rawUrl = staticMatch[1];
                    if (rawUrl.includes("link=")) {
                        try { rawUrl = atob(rawUrl.split("link=")[1]); } catch(e){}
                    }
                    
                    // Solo agregamos si no viene duplicado de la API
                    if (!tasks.some(t => JSON.stringify(t).includes(rawUrl))) {
                         servers.push({
                            server: "Web Player",
                            lang: "Latino",
                            url: rawUrl,
                            quality: "HD",
                            requiresWebView: true
                        });
                    }
                }

                if (tasks.length > 0) {
                    const apiResults = await Promise.all(tasks);
                    apiResults.forEach(res => { if (res) servers.push(res); });
                }

                return servers;

            } catch (e) { return []; }
        },

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
        }
    };

    if (typeof KronosEngine !== 'undefined') {
        KronosEngine.providers[provider.id] = provider;
    }
})();
