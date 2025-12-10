(function() {
    const provider = {
        id: 'zonaaps',
        name: 'ZonaAps',
        baseUrl: 'https://zonaaps.com',
        headers: {
            'User-Agent': 'Mozilla/5.0 (Linux; Android 10)',
            'Referer': 'https://zonaaps.com/',
            'X-Requested-With': 'XMLHttpRequest' // Necesario para la API
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

                // --- ESTRATEGIA FLEXIBLE (V7.0) ---
                // 1. Encontrar todos los bloques <li> que sean opciones, sin importar atributos
                const listItemsMatch = html.match(/<li[^>]*class=['"][^'"]*dooplay_player_option[^'"]*['"][^>]*>([\s\S]*?)<\/li>/g);

                if (listItemsMatch) {
                    bridge.log("JS [ZNA]: Se encontraron " + listItemsMatch.length + " botones.");
                    
                    const apiRequests = [];

                    for (const itemHtml of listItemsMatch) {
                        // 2. Extraer atributos INDIVIDUALMENTE (No importa el orden)
                        const postMatch = itemHtml.match(/data-post=['"](\d+)['"]/);
                        const typeMatch = itemHtml.match(/data-type=['"]([^"']+)['"]/);
                        const numeMatch = itemHtml.match(/data-nume=['"](\d+|trailer)['"]/); // Acepta números o 'trailer'

                        if (postMatch && typeMatch && numeMatch) {
                            const postId = postMatch[1];
                            const pType = typeMatch[1];
                            const nume = numeMatch[1];

                            if (nume === "trailer") continue;

                            // Nombre del servidor
                            let serverName = "Opción " + nume;
                            const titleMatch = itemHtml.match(/<span class=['"]title['"]>(.*?)<\/span>/);
                            if (titleMatch) serverName = titleMatch[1].replace(/🔒/g, "").trim();

                            // Idioma
                            let lang = "Latino";
                            if (itemHtml.toLowerCase().includes("sub")) lang = "Subtitulado";
                            else if (itemHtml.toLowerCase().includes("castellano") || itemHtml.includes("es.png")) lang = "Castellano";

                            bridge.log(`JS [ZNA]: Procesando ${serverName} (ID: ${postId})`);

                            // 3. Llamada a la API
                            const apiUrl = `${this.baseUrl}/wp-json/dooplayer/v2/${postId}/${pType}/${nume}`;
                            apiRequests.push(this.fetchApiLink(apiUrl, serverName, lang));
                        }
                    }

                    // Esperar todas las respuestas
                    if (apiRequests.length > 0) {
                        const resolved = await Promise.all(apiRequests);
                        resolved.forEach(s => { if(s) servers.push(s); });
                    }
                } else {
                    bridge.log("JS [ZNA]: No se encontraron botones <li> compatibles.");
                }

                // ESTRATEGIA DE RESPALDO (WEB)
                if (servers.length === 0) {
                    bridge.log("JS [ZNA]: Fallo API. Activando Modo Web.");
                    servers.push({
                        server: "ZonaAps (Modo Web)",
                        lang: "Latino",
                        url: url,
                        requiresWebView: true
                    });
                }

                bridge.onResult(JSON.stringify(servers));

            } catch (e) { 
                bridge.log("JS Error: " + e.message);
                bridge.onResult("[]"); 
            }
        },

        fetchApiLink: async function(apiUrl, serverName, lang) {
            try {
                // Tu JsBridge compilado con headers hará magia aquí
                const jsonStr = await bridge.fetchHtml(apiUrl);
                
                if (!jsonStr || jsonStr.trim().startsWith("<")) return null;

                const json = JSON.parse(jsonStr);
                const targetUrl = json.embed_url || json.u;

                if (targetUrl) {
                    // Si el link es un MP4 real, es nativo. Si es un embed (ej: fembed), usa webview.
                    const isDirect = targetUrl.endsWith(".mp4") || targetUrl.endsWith(".m3u8");
                    return {
                        server: serverName, // Ej: "Opción 1"
                        lang: lang,
                        url: targetUrl,
                        requiresWebView: !isDirect 
                    };
                }
            } catch (e) { }
            return null;
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
