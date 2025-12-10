// KRONOS PROVIDER: SoloLatino v12.0 (Full Series Support)
(function() {
    const provider = {
        id: 'sololatino',
        name: 'SoloLatino',
        baseUrl: 'https://sololatino.net',
        headers: {'User-Agent': 'Mozilla/5.0 (Linux; Android 10)'},

        search: async function(query) {
            // (Lógica de búsqueda v11 - IDÉNTICA)
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
                    // Fallback redirección
                    const canonicalMatch = html.match(/<link\s+rel=["']canonical["']\s+href=["']([^"']+)["']/i);
                    const titleMatch = html.match(/<meta\s+property=["']og:title["']\s+content=["']([^"']+)["']/i);
                    if (canonicalMatch && titleMatch) {
                        const directUrl = canonicalMatch[1];
                        if (directUrl.includes('/peliculas/') || directUrl.includes('/series/')) {
                            let rawTitle = titleMatch[1].replace(/^Ver\s+/i, '').replace(/\s+Online.*$/i, '').trim();
                            results.push({ 
                                title: rawTitle, 
                                url: directUrl, 
                                img: "", 
                                id: directUrl, 
                                type: directUrl.includes('/series/') ? 'tv' : 'movie', 
                                year: "" 
                            });
                        }
                    }
                }
                bridge.onResult(JSON.stringify(results));
            } catch (e) { bridge.onResult("[]"); }
        },

        // --- NUEVA FUNCIÓN: RESOLVER EPISODIO ---
        resolveEpisode: async function(showUrl, season, episode) {
            try {
                bridge.log("JS: Buscando episodio " + season + "x" + episode + " en " + showUrl);
                const html = await bridge.fetchHtml(showUrl);
                
                // Estrategia Dooplay: Buscar en la lista de episodios
                // Formato típico: <div class='episodiotitle'><a href='...'>1x1 Titulo</a></div>
                // O attributes: <div data-season='1' data-episode='1'>...<a href='...'>
                
                // 1. Intentar encontrar el bloque exacto SxE
                // Buscamos algo como "1x1" o "1x01" dentro de un <a> o cerca de él
                
                // Normalizamos "1" a "01" por si acaso
                const epPad = episode < 10 ? "0" + episode : episode;
                const pattern1 = new RegExp(season + "x" + episode + "[^0-9]", "i");
                const pattern2 = new RegExp(season + "x" + epPad + "[^0-9]", "i");
                
                // Extraer todos los links de episodios
                const linkRegex = /<a[^>]+href=["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/g;
                let linkMatch;
                let episodeUrl = null;

                while ((linkMatch = linkRegex.exec(html)) !== null) {
                    const href = linkMatch[1];
                    const text = linkMatch[2];
                    
                    // Solo nos interesan links de episodios
                    if (!href.includes('/episodios/')) continue;

                    // Chequeo 1: Texto contiene "1x1"
                    if (pattern1.test(text) || pattern2.test(text)) {
                        episodeUrl = href;
                        break;
                    }
                    
                    // Chequeo 2: La URL misma contiene "1x1" (ej: .../breaking-bad-1x1/)
                    if (pattern1.test(href) || pattern2.test(href)) {
                        episodeUrl = href;
                        break;
                    }
                }

                if (episodeUrl) {
                    bridge.log("JS: Episodio encontrado: " + episodeUrl);
                    // Reutilizamos la lógica de video normal
                    await this.resolveVideo(episodeUrl, "tv");
                } else {
                    bridge.log("JS: ❌ Episodio no encontrado en la lista.");
                    bridge.onResult("[]");
                }

            } catch (e) {
                bridge.log("JS CRASH Episode: " + e.message);
                bridge.onResult("[]");
            }
        },

        // --- RESOLVER VIDEO (Ya funciona perfecto con Embed69/XuPalace) ---
        resolveVideo: async function(url, type) {
            try {
                bridge.log("JS: Extrayendo servidores de: " + url);
                const html = await bridge.fetchHtml(url);
                
                const iframeMatch = html.match(/<iframe[^>]*\s(src|data-src)=["']([^"']+)["'][^>]*>/i);
                if (!iframeMatch) { bridge.onResult("[]"); return; }

                let embedUrl = iframeMatch[2];
                if (embedUrl.startsWith("//")) embedUrl = "https:" + embedUrl;
                
                const embedHtml = await bridge.fetchHtml(embedUrl);
                const servers = [];

                // EMBED69
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
                // XUPALACE
                else {
                    const listItemsRegex = /<li[^>]*onclick=["'](go_to_playerVast|go_to_player)\(['"]([^'"]+)['"]/g;
                    let liMatch;
                    while ((liMatch = listItemsRegex.exec(embedHtml)) !== null) {
                        let rawUrl = liMatch[2];
                        if (rawUrl.includes('link=')) {
                            try { rawUrl = atob(rawUrl.split('link=')[1].split('&')[0]); } catch(e){}
                        }
                        let serverName = "Server";
                        if (rawUrl.includes('filemoon')) serverName = "Filemoon";
                        else if (rawUrl.includes('waaw')) serverName = "Waaw";
                        else if (rawUrl.includes('vidhide')) serverName = "Vidhide";
                        else if (rawUrl.includes('voe')) serverName = "Voe";
                        
                        servers.push({ server: serverName, lang: "Latino", url: rawUrl });
                    }
                }
                bridge.onResult(JSON.stringify(servers));
            } catch (e) { bridge.onResult("[]"); }
        }
    };

    if (typeof KronosEngine !== 'undefined') {
        KronosEngine.providers[provider.id] = provider;
    }
})();
