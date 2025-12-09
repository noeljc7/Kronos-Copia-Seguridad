// KRONOS PROVIDER: SoloLatino v8.0 (Multi-Server + Smart Search)
(function() {
    const provider = {
        id: 'sololatino',
        name: 'SoloLatino',
        baseUrl: 'https://sololatino.net',
        headers: {'User-Agent': 'Mozilla/5.0 (Linux; Android 10)'},

        search: async function(query) {
            // ... (La misma lógica de búsqueda de la v7.0 que ya funcionaba) ...
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
                    
                    if (urlMatch && titleMatch) {
                        results.push({
                            title: titleMatch[1].trim(),
                            url: urlMatch[1],
                            img: imgMatch ? imgMatch[1] : "",
                            id: urlMatch[1],
                            type: match[1] === 'tvshows' ? 'tv' : 'movie'
                        });
                    }
                }
                
                // Redirección Fallback
                if (results.length === 0) {
                    const canonicalMatch = html.match(/<link\s+rel=["']canonical["']\s+href=["']([^"']+)["']/i);
                    const titleMatch = html.match(/<meta\s+property=["']og:title["']\s+content=["']([^"']+)["']/i);
                    if (canonicalMatch && titleMatch) {
                        const directUrl = canonicalMatch[1];
                        if (directUrl.includes('/peliculas/') || directUrl.includes('/series/')) {
                            let rawTitle = titleMatch[1].replace(/^Ver\s+/i, '').replace(/\s+Online.*$/i, '').trim();
                            results.push({ title: rawTitle, url: directUrl, img: "", id: directUrl, type: 'movie' });
                        }
                    }
                }
                bridge.onResult(JSON.stringify(results)); // CALLBACK
            } catch (e) { bridge.onResult("[]"); }
        },

        resolveVideo: async function(url, type) {
            try {
                bridge.log("JS: Extrayendo servidores de: " + url);
                const html = await bridge.fetchHtml(url);
                const iframeMatch = html.match(/<iframe[^>]*src=["']([^"']+)["'][^>]*>/i);
                if (!iframeMatch) { bridge.onResult("[]"); return; }

                let embedUrl = iframeMatch[1];
                if (embedUrl.startsWith("//")) embedUrl = "https:" + embedUrl;
                
                const embedHtml = await bridge.fetchHtml(embedUrl);
                const servers = [];

                // 1. EMBED69 (Multi-Server)
                if (embedHtml.includes('eyJ')) {
                    const jsonMatch = embedHtml.match(/let dataLink = (\[.*?\]);/);
                    if (jsonMatch) {
                        const data = JSON.parse(jsonMatch[1]);
                        data.forEach(langGroup => {
                            const lang = langGroup.video_language || "Latino";
                            if (langGroup.sortedEmbeds) {
                                langGroup.sortedEmbeds.forEach(server => {
                                    try {
                                        // Decodificar JWT
                                        const parts = server.link.split('.');
                                        const payload = atob(parts[1].replace(/-/g, '+').replace(/_/g, '/'));
                                        const linkData = JSON.parse(payload);
                                        servers.push({
                                            server: server.servername,
                                            lang: lang,
                                            url: linkData.link
                                        });
                                    } catch(e) {}
                                });
                            }
                        });
                    }
                } 
                // 2. CLASICO/XUPALACE (Multi-Server)
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
                        else if (rawUrl.includes('mega')) serverName = "Mega";

                        servers.push({ server: serverName, lang: "Latino", url: rawUrl });
                    }
                }

                bridge.onResult(JSON.stringify(servers)); // CALLBACK LISTA COMPLETA

            } catch (e) { bridge.onResult("[]"); }
        }
    };

    if (typeof KronosEngine !== 'undefined') {
        KronosEngine.providers[provider.id] = provider;
    }
})();
