(function() {
    const provider = {
        id: 'sololatino',
        name: 'SoloLatino (Native JS)',
        baseUrl: 'https://sololatino.net',
        headers: {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36',
            'Referer': 'https://sololatino.net/',
            'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8'
        },

        // --- BÚSQUEDA (Mantenemos la v19 que es la más robusta para encontrar el contenido) ---
        search: async function(query) {
            try {
                // 1. Obtener Nonce
                const homeHtml = await bridge.fetchHtml(this.baseUrl);
                const nonceMatch = homeHtml.match(/"nonce":"([^"]+)"/);
                if (!nonceMatch) return []; 

                const nonce = nonceMatch[1];
                const cleanQuery = encodeURIComponent(query);
                const apiUrl = `${this.baseUrl}/wp-json/dooplay/search/?keyword=${cleanQuery}&nonce=${nonce}`;
                
                const jsonStr = await bridge.fetchHtml(apiUrl);
                const data = JSON.parse(jsonStr);
                const results = [];
                const items = [];
                
                if (Array.isArray(data)) data.forEach(x => items.push(x));
                else if (typeof data === 'object') Object.values(data).forEach(val => items.push(val));

                items.forEach(item => {
                    if (item.url && item.title) {
                        let type = 'movie';
                        if (item.url.includes('/series/') || item.url.includes('/tvshows/')) type = 'tv';
                        let year = item.extra && item.extra.date ? item.extra.date : "0";
                        results.push({
                            title: item.title,
                            url: item.url,
                            img: item.img || "",
                            id: item.url, // Aquí usamos la URL como ID para procesarla luego
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

        // --- AQUÍ ESTÁ LA LÓGICA DE TU KOTLIN TRADUCIDA ---
        resolveVideo: async function(url, type) {
            try {
                bridge.log("JS: Procesando URL: " + url);
                
                // Si la URL es directa de embed69/xupalace (caso raro en JS directo, pero posible)
                if (url.includes("embed69") || url.includes("xupalace")) {
                    return await this.processUrl(url);
                }

                // Si es una página de SoloLatino (lo normal)
                const html = await bridge.fetchHtml(url);
                const servers = [];
                const tasks = [];

                // LÓGICA KOTLIN: val iframeMatcher = Pattern.compile(...)
                // Buscamos iframes o embed.php
                // Regex ajustado para JS global
                const iframeRegex = /src\s*=\s*["']([^"']*embed\.php\?id=\d+|[^"']*\/f\/[^"']+|[^"']*\/video\/[^"']+)[^"']*["']/g;
                
                let match;
                while ((match = iframeRegex.exec(html)) !== null) {
                    let iframeUrl = match[1];
                    
                    if (iframeUrl) {
                        if (iframeUrl.startsWith("//")) iframeUrl = "https:" + iframeUrl;
                        if (!iframeUrl.startsWith("http")) iframeUrl = "https://sololatino.net" + iframeUrl;
                        
                        bridge.log("JS: Iframe encontrado: " + iframeUrl);
                        
                        // Añadimos a la cola de procesamiento (Equivalente a links.addAll(scrapeDoubleHop...))
                        tasks.push(this.processUrl(iframeUrl));
                    }
                }

                // También buscamos iframes con clase 'metaframe' (común en SoloLatino moderno)
                const metaframeRegex = /<iframe[^>]*src=['"]([^'"]+)['"][^>]*class=['"]metaframe rptss['"][^>]*>/g;
                while ((match = metaframeRegex.exec(html)) !== null) {
                    let iframeUrl = match[1];
                    tasks.push(this.processUrl(iframeUrl));
                }

                // Ejecutamos todo en paralelo
                if (tasks.length > 0) {
                    const results = await Promise.all(tasks);
                    results.forEach(list => {
                        if (list) list.forEach(s => servers.push(s));
                    });
                }

                bridge.log("JS: Total servidores encontrados: " + servers.length);
                return servers;

            } catch (e) { 
                bridge.log("JS ERROR: " + e.message);
                return []; 
            }
        },

        // --- TRADUCCIÓN DE: private fun processUrl(url: String) ---
        processUrl: async function(url) {
            const foundLinks = [];
            try {
                // Headers dinámicos como en tu Kotlin (Referer para xupalace)
                if (url.includes("embed69") || url.includes("xupalace")) {
                    // Nota: El bridge nuevo ya maneja headers automáticamente, pero el referer es clave
                    // Aquí no podemos cambiar headers por request individual en el bridge actual tan facil
                    // pero el bridge global debería funcionar.
                }

                // Fetch HTML
                const html = await bridge.fetchHtml(url);
                if (!html) return [];

                // 1. TRADUCCIÓN: JSON Embed69 (dataLink)
                if (html.includes("dataLink")) {
                    const embedLinks = this.scrapeEmbed69Json(html);
                    embedLinks.forEach(l => foundLinks.push(l));
                }

                // 2. TRADUCCIÓN: Scrape Double Hop (go_to_player)
                // Tu Kotlin llama a scrapeDoubleHop si encuentra iframes anidados, 
                // pero también revisa el HTML actual para ver si TIENE los botones.
                const hopLinks = this.scrapeDoubleHop(html);
                hopLinks.forEach(l => foundLinks.push(l));

                return foundLinks;

            } catch (e) { return []; }
        },

        // --- TRADUCCIÓN DE: private fun scrapeEmbed69Json(html: String) ---
        scrapeEmbed69Json: function(html) {
            const links = [];
            try {
                // Regex: let\s+dataLink\s*=\s*(\[.*?\]);
                const regex = /let\s+dataLink\s*=\s*(\[.*?\]);/s; // 's' flag para dotAll
                const match = html.match(regex);
                
                if (match) {
                    const jsonArray = JSON.parse(match[1]);
                    
                    jsonArray.forEach(item => {
                        const lang = item.video_language || "UNK";
                        const prettyLang = this.mapLanguage(lang);
                        
                        const embeds = item.sortedEmbeds;
                        if (embeds) {
                            embeds.forEach(embed => {
                                const server = embed.servername;
                                const token = embed.link;
                                
                                if (server !== "download" && token) {
                                    const decodedUrl = this.decodeJwt(token);
                                    if (decodedUrl) {
                                        this.addLinkOptimized(links, server, decodedUrl, prettyLang);
                                    }
                                }
                            });
                        }
                    });
                }
            } catch (e) { bridge.log("JS Json Error: " + e.message); }
            return links;
        },

        // --- TRADUCCIÓN DE: private fun scrapeDoubleHop(html: String) ---
        // (Nota: En tu Kotlin recibía URL, aquí recibe HTML para ahorrar una petición si ya lo tenemos)
        scrapeDoubleHop: function(html) {
            const links = [];
            try {
                // Regex: onclick="go_to_player\('([^']+)'\)"[^>]*>.*?<span>(.*?)</span>
                const regex = /onclick="go_to_player\('([^']+)'\)"[^>]*>.*?<span>(.*?)<\/span>/g;
                let match;
                
                while ((match = regex.exec(html)) !== null) {
                    const rawLink = match[1];
                    const serverName = match[2].trim() || "Server";
                    
                    if (rawLink.startsWith("http")) {
                        this.addLinkOptimized(links, serverName, rawLink, "🇲🇽 Latino");
                    }
                }
            } catch (e) { }
            return links;
        },

        // --- TRADUCCIÓN DE: private fun decodeJwt(token: String) ---
        decodeJwt: function(token) {
            try {
                const parts = token.split(".");
                if (parts.length < 2) return null;
                
                let base64Url = parts[1];
                // Ajustar Padding (=)
                while (base64Url.length % 4 !== 0) base64Url += "=";
                
                // Reemplazar caracteres URL Safe
                const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
                
                // Decodificar Base64 a String (Equivalente a Base64.decode)
                const jsonStr = atob(base64);
                
                const json = JSON.parse(jsonStr);
                return json.link || null;
            } catch (e) { return null; }
        },

        // --- TRADUCCIÓN DE: mapLanguage ---
        mapLanguage: function(code) {
            const up = code.toUpperCase();
            if (up === "LAT" || up === "LATINO") return "🇲🇽 Latino";
            if (up === "SUB" || up === "SUBTITULADO") return "🇺🇸 Subtitulado";
            if (up === "ESP" || up === "CASTELLANO") return "🇪🇸 Castellano";
            return "❓ " + code;
        },

        // --- TRADUCCIÓN DE: addLinkOptimized ---
        addLinkOptimized: function(links, serverName, url, lang) {
            const isDirect = url.endsWith(".mp4") || url.endsWith(".m3u8") || url.includes("stream.php");
            
            // Capitalizar primera letra (Pretty Server)
            const prettyServer = serverName.charAt(0).toUpperCase() + serverName.slice(1);

            links.push({
                name: "JS - " + prettyServer,
                url: url,
                quality: "HD",
                language: lang,
                provider: "SoloLatino (JS)",
                isDirect: isDirect,
                requiresWebView: !isDirect
            });
        }
    };

    if (typeof KronosEngine !== 'undefined') {
        KronosEngine.providers[provider.id] = provider;
    }
})();
