(function() {
    const provider = {
        id: 'sololatino',
        name: 'SoloLatino (Hybrid v25)',
        baseUrl: 'https://sololatino.net',
        headers: {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36',
            'Referer': 'https://sololatino.net/'
        },

        search: async function(query) {
            try {
                const homeHtml = await bridge.fetchHtml(this.baseUrl);
                const nonceMatch = homeHtml.match(/"nonce":"([^"]+)"/);
                if (!nonceMatch) return [];

                const nonce = nonceMatch[1];
                const cleanQuery = encodeURIComponent(query);
                const apiUrl = `${this.baseUrl}/wp-json/dooplay/search/?keyword=${cleanQuery}&nonce=${nonce}`;
                
                const jsonStr = await bridge.fetchHtml(apiUrl);
                const data = JSON.parse(jsonStr);
                const results = [];
                const items = Array.isArray(data) ? data : Object.values(data);

                items.forEach(item => {
                    if (item.url && item.title) {
                        let type = 'movie';
                        if (item.url.includes('/series/') || item.url.includes('/tvshows/')) type = 'tv';
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
            } catch (e) { return []; }
        },

        resolveEpisode: async function(showUrl, season, episode) {
            try {
                const html = await bridge.fetchHtml(showUrl);
                const epPad = episode < 10 ? "0" + episode : episode;
                const regex = new RegExp(`href=["']([^"']*?(?:${season}x${episode}|${season}x${epPad})[^"']*)["']`, "i");
                const match = html.match(regex);
                if (match) return await this.resolveVideo(match[1], "tv");
                return [];
            } catch (e) { return []; }
        },

        resolveVideo: async function(url, type) {
            try {
                bridge.log("JS: Analizando URL: " + url);
                const html = await bridge.fetchHtml(url);
                const servers = [];
                
                // 1. IFRAMES DIRECTOS
                const iframeRegex = /<iframe[^>]*src=['"]([^'"]+)['"][^>]*class=['"]metaframe rptss['"][^>]*>/g;
                let match;
                while ((match = iframeRegex.exec(html)) !== null) {
                    this.processEmbedUrl(match[1], servers);
                }

                // 2. BOTONES DE API (AJAX)
                // Regex flexible para encontrar cualquier botón de opción
                const liTagRegex = /<li[^>]+data-post=['"]\d+['"][^>]*>/g; 
                let liMatch;
                const tasks = [];

                while ((liMatch = liTagRegex.exec(html)) !== null) {
                    const fullTag = liMatch[0];
                    const postMatch = fullTag.match(/data-post=['"](\d+)['"]/);
                    const typeMatch = fullTag.match(/data-type=['"]([^"']+)['"]/);
                    const numeMatch = fullTag.match(/data-nume=['"](\d+)['"]/);

                    if (postMatch && typeMatch && numeMatch) {
                        const postId = postMatch[1];
                        const pType = typeMatch[1];
                        const nume = numeMatch[1];
                        if (nume === "trailer") continue;

                        const apiUrl = `${this.baseUrl}/wp-json/dooplayer/v2/${postId}/${pType}/${nume}`;
                        tasks.push(this.fetchAndProcessApi(apiUrl));
                    }
                }

                if (tasks.length > 0) {
                    bridge.log("JS: Consultando " + tasks.length + " opciones por API...");
                    const results = await Promise.all(tasks);
                    results.forEach(subList => {
                        if (subList) subList.forEach(s => servers.push(s));
                    });
                }

                bridge.log("JS: Retornando " + servers.length + " servidores.");
                return servers;

            } catch (e) {
                bridge.log("JS ERROR: " + e.message);
                return [];
            }
        },

        processEmbedUrl: function(embedUrl, serverList) {
            // Si es Embed69/XuPalace, usamos Kotlin
            if (embedUrl.includes("embed69") || embedUrl.includes("xupalace")) {
                const nativeJson = bridge.resolveNative(embedUrl);
                try {
                    const nativeLinks = JSON.parse(nativeJson);
                    if (nativeLinks.length > 0) {
                        nativeLinks.forEach(l => serverList.push(l));
                    } else {
                        // Si Kotlin falló, añadimos el link web por si acaso
                        serverList.push({name: "Embed69 (Web)", url: embedUrl, quality: "HD", lang: "Multi", requiresWebView: true});
                    }
                } catch(err) {}
            } else {
                // Si NO ES Embed69 (ej: fembed, doodstream directos), lo añadimos tal cual
                serverList.push({
                    server: "Direct Link",
                    url: embedUrl,
                    quality: "HD",
                    lang: "Latino", // Asumimos latino
                    requiresWebView: true,
                    isDirect: false
                });
            }
        },

        fetchAndProcessApi: async function(apiUrl) {
            try {
                const jsonStr = await bridge.fetchHtml(apiUrl);
                const json = JSON.parse(jsonStr);
                const targetUrl = json.embed_url || json.u;
                
                // LOG DE DEPURACIÓN (Míralo en la terminal)
                // bridge.log("JS API Result: " + targetUrl);
                
                const tempServers = [];
                if (targetUrl) {
                    this.processEmbedUrl(targetUrl, tempServers);
                }
                return tempServers;
            } catch (e) { return []; }
        }
    };

    if (typeof KronosEngine !== 'undefined') {
        KronosEngine.providers[provider.id] = provider;
    }
})();
