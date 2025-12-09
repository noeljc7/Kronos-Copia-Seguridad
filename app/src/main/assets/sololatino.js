// KRONOS PROVIDER: SoloLatino v10.0 (Lazy Load Support)
(function() {
    const provider = {
        id: 'sololatino',
        name: 'SoloLatino',
        baseUrl: 'https://sololatino.net',
        headers: {'User-Agent': 'Mozilla/5.0 (Linux; Android 10)'},

        search: async function(query) {
            // (Lógica de búsqueda v9.0 - NO TOCAR, YA FUNCIONA)
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
                // Fallback redirección
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

        // --- AQUÍ ESTÁ LA MEJORA (RESOLVE) ---
        resolveVideo: async function(url, type) {
            try {
                bridge.log("JS: Extrayendo de: " + url);
                const html = await bridge.fetchHtml(url);
                
                bridge.log("JS: HTML Película descargado (" + html.length + " bytes)");

                // BUSCAR EL IFRAME (Soporte Lazy Load)
                // Buscamos src="..." O data-src="..."
                const iframeMatch = html.match(/<iframe[^>]*\s(src|data-src)=["']([^"']+)["'][^>]*>/i);
                
                if (!iframeMatch) { 
                    bridge.log("JS: ❌ No se encontró ningún iframe en la página.");
                    bridge.onResult("[]"); 
                    return; 
                }

                let embedUrl = iframeMatch[2]; // El grupo 2 es la URL
                if (embedUrl.startsWith("//")) embedUrl = "https:" + embedUrl;
                
                bridge.log("JS: Iframe encontrado: " + embedUrl);
                const embedHtml = await bridge.fetchHtml(embedUrl);
                const servers = [];

                // 1. EMBED69
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
                // 2. XUPALACE / GENERICO
                else {
                    const regex = /go_to_player.*?\('([^']+)'\)/g;
                    let match;
                    while ((match = regex.exec(embedHtml)) !== null) {
                        let rawUrl = match[1];
                        if (rawUrl.includes('link=')) {
                            try { rawUrl = atob(rawUrl.split('link=')[1].split('&')[0]); } catch(e){}
                        }
                        
                        let serverName = "Server";
                        if (rawUrl.includes('vidhide')) serverName = "Vidhide";
                        else if (rawUrl.includes('wish')) serverName = "Streamwish";
                        else if (rawUrl.includes('voe')) serverName = "Voe";
                        
                        servers.push({ server: serverName, lang: "Latino", url: rawUrl });
                    }
                }

                bridge.log("JS: Servidores extraídos: " + servers.length);
                bridge.onResult(JSON.stringify(servers));

            } catch (e) { 
                bridge.log("JS CRASH Resolve: " + e.message);
                bridge.onResult("[]"); 
            }
        }
    };

    if (typeof KronosEngine !== 'undefined') {
        KronosEngine.providers[provider.id] = provider;
    }
})();
