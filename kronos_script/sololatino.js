(function() {
    const provider = {
        id: 'sololatino',
        name: 'SoloLatino',
        baseUrl: 'https://sololatino.net',
        headers: {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36',
            'Referer': 'https://sololatino.net/'
        },

        // --- BÚSQUEDA VÍA API (CONFIRMADO POR TUS LOGS) ---
        search: async function(query) {
            try {
                // 1. Buscar Nonce en Home
                const homeHtml = await bridge.fetchHtml(this.baseUrl);
                const nonceMatch = homeHtml.match(/"nonce":"([^"]+)"/);
                if (!nonceMatch) return [];

                const nonce = nonceMatch[1];
                const cleanQuery = encodeURIComponent(query);
                // Tu log confirmó esta estructura:
                const apiUrl = `${this.baseUrl}/wp-json/dooplay/search/?keyword=${cleanQuery}&nonce=${nonce}`;
                
                const jsonStr = await bridge.fetchHtml(apiUrl);
                const data = JSON.parse(jsonStr);
                const results = [];
                
                // Normalizar respuesta (puede ser objeto o array)
                const items = Array.isArray(data) ? data : Object.values(data);

                items.forEach(item => {
                    if (item.url && item.title) {
                        let type = 'movie';
                        if (item.url.includes('/series/') || item.url.includes('/tvshows/')) type = 'tv';
                        // Tu log mostró que el año viene en 'extra.date'
                        let year = item.extra && item.extra.date ? item.extra.date : "0";
                        results.push({
                            title: item.title,
                            url: item.url,
                            img: item.img || "",
                            id: item.url,
                            type: type,
                            year: year
                        });
                    }
                });
                return results;
            } catch (e) { 
                bridge.log("JS Search Error: " + e.message);
                return []; 
            }
        },

        resolveEpisode: async function(showUrl, season, episode) {
            try {
                const html = await bridge.fetchHtml(showUrl);
                // Regex ajustada a tus logs: busca enlaces que contengan la temporada y episodio
                const epPad = episode < 10 ? "0" + episode : episode;
                // Busca hrefs que tengan "1x05" o "1x5"
                const regex = new RegExp(`href=["']([^"']*?(?:${season}x${episode}|${season}x${epPad})[^"']*)["']`, "i");
                const match = html.match(regex);
                
                if (match) {
                    return await this.resolveVideo(match[1], "tv");
                }
                return [];
            } catch (e) { return []; }
        },

        // --- RESOLUCIÓN HÍBRIDA (JS BUSCA -> KOTLIN EJECUTA) ---
        resolveVideo: async function(url, type) {
            try {
                bridge.log("JS: Analizando página: " + url);
                const html = await bridge.fetchHtml(url);
                const servers = [];

                // 1. BUSCAR IFRAMES "metaframe rptss" (Confirmado en tus logs)
                const iframeRegex = /<iframe[^>]*src=['"]([^'"]+)['"][^>]*class=['"]metaframe rptss['"][^>]*>/g;
                let match;
                
                while ((match = iframeRegex.exec(html)) !== null) {
                    let embedUrl = match[1];
                    
                    // Si encontramos Embed69 o XuPalace
                    if (embedUrl.includes("embed69") || embedUrl.includes("xupalace")) {
                        bridge.log("JS: Encontrado Target: " + embedUrl);
                        
                        // LLAMADA SINCRÓNICA A KOTLIN
                        // Kotlin hará el POST a /api/decrypt y devolverá el JSON listo
                        const nativeJson = bridge.resolveNative(embedUrl);
                        
                        try {
                            const nativeLinks = JSON.parse(nativeJson);
                            nativeLinks.forEach(l => servers.push(l));
                        } catch(err) {
                            bridge.log("JS: Error parseando respuesta nativa");
                        }
                    }
                }

                bridge.log("JS: Retornando " + servers.length + " servidores.");
                return servers;

            } catch (e) {
                bridge.log("JS ERROR: " + e.message);
                return [];
            }
        }
    };

    if (typeof KronosEngine !== 'undefined') {
        KronosEngine.providers[provider.id] = provider;
    }
})();
