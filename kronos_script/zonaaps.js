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
                    if (yearMatch) { title = yearMatch[1].trim(); year = yearMatch[2]; }
                    let type = (url.includes('/tvshows/') || url.includes('/episodes/')) ? 'tv' : 'movie';
                    results.push({ title: title, url: url, img: img, id: url, type: type, year: year });
                }
                bridge.onResult(JSON.stringify(results));
            } catch (e) { bridge.onResult("[]"); }
        },

        resolveVideo: async function(url, type) {
            try {
                bridge.log("JS [ZNA]: Analizando API: " + url);
                const html = await bridge.fetchHtml(url);
                const servers = [];

                // 1. BUSCAR BOTONES DE LA API
                const liRegex = /<li[^>]+data-post=['"](\d+)['"][^>]*data-type=['"]([^"']+)['"][^>]*data-nume=['"](\d+)['"][^>]*>([\s\S]*?)<\/li>/g;
                let match;
                const apiRequests = [];

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
                    else if (content.toLowerCase().includes("castellano") || content.includes("es.png")) lang = "Castellano";

                    const apiUrl = `${this.baseUrl}/wp-json/dooplayer/v2/${postId}/${pType}/${nume}`;
                    apiRequests.push(this.fetchApiLink(apiUrl, serverName, lang));
                }

                if (apiRequests.length > 0) {
                    const resolved = await Promise.all(apiRequests);
                    resolved.forEach(s => { if(s) servers.push(s); });
                }

                // 2. SI FALLA LA API, MODO WEB
                if (servers.length === 0) {
                    bridge.log("JS [ZNA]: Fallo API. Usando respaldo Web.");
                    servers.push({
                        server: "ZonaAps (Ver en Web)",
                        lang: "Latino",
                        url: url,
                        requiresWebView: true // Obliga a abrir navegador
                    });
                }

                bridge.onResult(JSON.stringify(servers));

            } catch (e) { bridge.onResult("[]"); }
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
                        requiresWebView: !isDirect 
                    };
                }
            } catch (e) { }
            return null;
        },

        // ... (resolveEpisode igual que antes) ...
        resolveEpisode: async function(url, season, episode) {
            try {
                if (url.includes('/tvshows/')) {
                    const html = await bridge.fetchHtml(url);
                    const epPad = episode < 10 ? "0" + episode : episode;
                    const epRegex = new RegExp(`href="([^"]*?(?:${season}x${episode}|${season}x${epPad})[^"]*)"`, "i");
                    const match = html.match(epRegex);
                    if (match) await this.resolveVideo(match[1], 'tv');
                    else {
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
