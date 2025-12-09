// KRONOS PROVIDER: SoloLatino v11.0 (XuPalace & Strict Mode)
(function() {
    const provider = {
        id: 'sololatino',
        name: 'SoloLatino',
        baseUrl: 'https://sololatino.net',
        headers: {'User-Agent': 'Mozilla/5.0 (Linux; Android 10)'},

        search: async function(query) {
            // ... Lógica de búsqueda estándar (Igual que v10) ...
            try {
                const cleanQuery = encodeURIComponent(query.replace(/[:\-\.]/g, ' '));
                const searchUrl = this.baseUrl + "/?s=" + cleanQuery;
                bridge.log("JS: Buscando: " + searchUrl);
                let html = "";
                try { html = await bridge.fetchHtml(searchUrl); } catch (e) { return []; }
                if (!html || html.length < 100) return [];
                const results = [];
                const articleRegex = /<article[^>]*class="item\s+(movies|tvshows)"[^>]*>([\s\S]*?)<\/article>/g;
                let match;
                while ((match = articleRegex.exec(html)) !== null) {
                    const content = match[2];
                    const urlMatch = content.match(/href="([^"]+)"/);
                    const imgMatch = content.match(/data-srcset="([^"\s]+)/) || content.match(/src="([^"]+)"/);
                    const titleMatch = content.match(/<h3>([^<]+)<\/h3>/);
                    const yearMatch = content.match(/<p>(\d{4})<\/p>/);
                    if (urlMatch && titleMatch) {
                        results.push({
                            title: titleMatch[1].trim(),
                            url: urlMatch[1],
                            img: imgMatch ? imgMatch[1] : "",
                            id: urlMatch[1],
                            type: match[1] === 'tvshows' ? 'tv' : 'movie',
                            year: yearMatch ? yearMatch[1] : ""
                        });
                    }
                }
                if (results.length === 0) {
                    const canonicalMatch = html.match(/<link\s+rel=["']canonical["']\s+href=["']([^"']+)["']/i);
                    const titleMatch = html.match(/<meta\s+property=["']og:title["']\s+content=["']([^"']+)["']/i);
                    if (canonicalMatch && titleMatch) {
                        const directUrl = canonicalMatch[1];
                        if (directUrl.includes('/peliculas/') || directUrl.includes('/series/')) {
                            let rawTitle = titleMatch[1].replace(/^Ver\s+/i, '').replace(/\s+Online.*$/i, '').trim();
                            results.push({ title: rawTitle, url: directUrl, img: "", id: directUrl, type: 'movie', year: "" });
                        }
                    }
                }
                bridge.onResult(JSON.stringify(results));
            } catch (e) { bridge.onResult("[]"); }
        },

        resolveVideo: async function(url, type) {
            try {
                bridge.log("JS: Extrayendo de: " + url);
                const html = await bridge.fetchHtml(url);
                
                // Buscar iframe principal
                const iframeMatch = html.match(/<iframe[^>]*\s(src|data-src)=["']([^"']+)["'][^>]*>/i);
                
                if (!iframeMatch) { 
                    bridge.log("JS: ❌ No se encontró iframe inicial.");
                    bridge.onResult("[]"); 
                    return; 
                }

                let embedUrl = iframeMatch[2];
                if (embedUrl.startsWith("//")) embedUrl = "https:" + embedUrl;
                
                bridge.log("JS: Analizando Embed: " + embedUrl);
                const embedHtml = await bridge.fetchHtml(embedUrl);
                const servers = [];

                // --- ESTRATEGIA 1: EMBED69 (JSON) ---
                if (embedHtml.includes('eyJ')) {
                    const jsonMatch = embedHtml.match(/let dataLink = (\[.*?\]);/);
                    if (jsonMatch) {
                        const data = JSON.parse(jsonMatch[1]);
                        data.forEach(langGroup => {
                            const lang = langGroup.video_language || "Latino";
                            if (langGroup.sortedEmbeds) {
                                langGroup.sortedEmbeds.forEach(server => {
                                    const sName = server.servername.toLowerCase();
                                    if (sName.includes("download") || sName.includes("descarga")) return;
                                    try {
                                        const parts = server.link.split('.');
                                        const payload = atob(parts[1].replace(/-/g, '+').replace(/_/g, '/'));
                                        const linkData = JSON.parse(payload);
                                        servers.push({ server: server.servername, lang: lang, url: linkData.link });
                                    } catch(e) {}
                                });
                            }
                        });
                    }
                } 
                
                // --- ESTRATEGIA 2: XUPALACE / DOOPLAY (Tu HTML) ---
                // Aquí usamos Regex para encontrar 'go_to_playerVast' y 'go_to_player'
                const listItemsRegex = /<li[^>]*onclick=["'](go_to_playerVast|go_to_player)\(['"]([^'"]+)['"]/g;
                let liMatch;
                
                while ((liMatch = listItemsRegex.exec(embedHtml)) !== null) {
                    let rawUrl = liMatch[2]; // La URL capturada
                    
                    // Decodificar si está en Base64 (link=...)
                    if (rawUrl.includes('link=')) {
                        try { rawUrl = atob(rawUrl.split('link=')[1].split('&')[0]); } catch(e){}
                    }

                    // Adivinar nombre del servidor
                    let serverName = "Server";
                    if (rawUrl.includes('filemoon')) serverName = "Filemoon";
                    else if (rawUrl.includes('waaw')) serverName = "Waaw";
                    else if (rawUrl.includes('vidhide')) serverName = "Vidhide";
                    else if (rawUrl.includes('wish')) serverName = "Streamwish";
                    else if (rawUrl.includes('voe')) serverName = "Voe";
                    
                    // Extraer idioma del atributo data-lang si es posible, sino default
                    // (Simplificado para no complicar el regex)
                    let lang = "Latino"; 

                    servers.push({ server: serverName, lang: lang, url: rawUrl });
                }

                bridge.log("JS: Servidores extraídos: " + servers.length);
                bridge.onResult(JSON.stringify(servers));

            } catch (e) { 
                bridge.log("JS CRASH: " + e.message);
                bridge.onResult("[]"); 
            }
        }
    };

    if (typeof KronosEngine !== 'undefined') {
        KronosEngine.providers[provider.id] = provider;
    }
})();
