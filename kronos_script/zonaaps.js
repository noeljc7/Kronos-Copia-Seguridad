(function() {
    const provider = {
        id: 'zonaaps',
        name: 'ZonaAps',
        baseUrl: 'https://zonaaps.com',
        headers: {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36',
            'X-Requested-With': 'XMLHttpRequest', // Para la API
            'Referer': 'https://zonaaps.com/'     // ¡CRUCIAL! Tu captura lo confirmó
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
                bridge.log("JS [ZNA]: Analizando URL: " + url);
                const html = await bridge.fetchHtml(url);
                const servers = [];

                // BUSCAR BOTONES DE LA API (Igual que antes)
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
                    apiRequests.push(this.processServer(apiUrl, serverName, lang));
                }

                if (apiRequests.length > 0) {
                    const resolved = await Promise.all(apiRequests);
                    resolved.forEach(s => { if(s) servers.push(s); });
                }

                // SI TODO FALLA, MODO WEB
                if (servers.length === 0) {
                    bridge.log("JS [ZNA]: Fallo extracción nativa. Usando Web.");
                    servers.push({ server: "ZonaAps (Web)", lang: "Latino", url: url, requiresWebView: true });
                }

                bridge.onResult(JSON.stringify(servers));

            } catch (e) { bridge.onResult("[]"); }
        },

        // --- FUNCIÓN DE EXTRACTOR DE DOBLE PASO ---
        processServer: async function(apiUrl, serverName, lang) {
            try {
                // PASO 1: Pedir a la API el link del embed (zonaaps-player.xyz)
                const jsonStr = await bridge.fetchHtml(apiUrl);
                if (!jsonStr || jsonStr.trim().startsWith("<")) return null;
                
                const json = JSON.parse(jsonStr);
                const embedUrl = json.embed_url || json.u;

                if (!embedUrl) return null;

                // PASO 2: Si es el reproductor propio, entramos a robar el MP4
                if (embedUrl.includes("zonaaps-player.xyz") || embedUrl.includes("1a-1791.com")) {
                    bridge.log("JS [ZNA]: Extrayendo MP4 de: " + embedUrl);
                    
                    // Aquí el Bridge usará el Referer: zonaaps.com (Crucial por tu captura)
                    const playerHtml = await bridge.fetchHtml(embedUrl);
                    
                    // Buscamos: file: "https://...mp4"
                    const mp4Match = playerHtml.match(/file:\s*["']([^"']+\.mp4)["']/);
                    
                    if (mp4Match) {
                        return {
                            server: serverName + " (Nativo)",
                            lang: lang,
                            url: mp4Match[1], // ¡EL ENLACE PURO!
                            requiresWebView: false // ¡REPRODUCTOR NATIVO!
                        };
                    }
                }

                // Si no pudimos sacar el MP4, devolvemos el embed (necesitará WebView)
                const isDirect = embedUrl.endsWith(".mp4") || embedUrl.endsWith(".m3u8");
                return {
                    server: serverName,
                    lang: lang,
                    url: embedUrl,
                    requiresWebView: !isDirect
                };

            } catch (e) { return null; }
        },

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
