// KRONOS PROVIDER: SoloLatino v13.0 (Year Fix + Hybrid Extraction)
(function() {
    const provider = {
        id: 'sololatino',
        name: 'SoloLatino',
        baseUrl: 'https://sololatino.net',
        headers: {'User-Agent': 'Mozilla/5.0 (Linux; Android 10)'},

        search: async function(query) {
            try {
                const cleanQuery = encodeURIComponent(query.replace(/[:\-\.]/g, ' '));
                const searchUrl = this.baseUrl + "/?s=" + cleanQuery;
                bridge.log("JS: Buscando: " + searchUrl);
                
                let html = "";
                try { html = await bridge.fetchHtml(searchUrl); } catch (e) { return []; }
                if (!html || html.length < 100) return [];
                
                const results = [];
                // Regex mejorado para capturar bloques de peliculas
                const articleRegex = /<article[^>]*class="item\s+(movies|tvshows)"[^>]*>([\s\S]*?)<\/article>/g;
                let match;
                
                while ((match = articleRegex.exec(html)) !== null) {
                    const content = match[2];
                    const urlMatch = content.match(/href="([^"]+)"/);
                    // Soporte para lazy load images
                    const imgMatch = content.match(/data-srcset="([^"\s]+)/) || content.match(/src="([^"]+)"/);
                    const titleMatch = content.match(/<h3>([^<]+)<\/h3>/);
                    
                    // --- ARREGLO DEL AÑO ---
                    // Buscamos 4 dígitos dentro de p, span o div class="year"
                    // Ej: <p>2024</p> o <span class="year">2024</span>
                    let yearMatch = content.match(/<(?:p|span|div)[^>]*>(\d{4})<\/(?:p|span|div)>/);
                    if (!yearMatch) yearMatch = content.match(/(\d{4})/); // Último recurso: cualquier 4 dígitos
                    
                    if (urlMatch && titleMatch) {
                        results.push({
                            title: titleMatch[1].trim(),
                            url: urlMatch[1],
                            img: imgMatch ? imgMatch[1] : "",
                            id: urlMatch[1],
                            type: match[1] === 'tvshows' ? 'tv' : 'movie',
                            year: yearMatch ? yearMatch[1] : "0" // Si falla, 0
                        });
                    }
                }
                
                // Fallback Redirección (Si WordPress manda directo al post)
                if (results.length === 0) {
                    const canonicalMatch = html.match(/<link\s+rel=["']canonical["']\s+href=["']([^"']+)["']/i);
                    const titleMatch = html.match(/<meta\s+property=["']og:title["']\s+content=["']([^"']+)["']/i);
                    if (canonicalMatch && titleMatch) {
                        const directUrl = canonicalMatch[1];
                        if (directUrl.includes('/peliculas/') || directUrl.includes('/series/')) {
                            let rawTitle = titleMatch[1].replace(/^Ver\s+/i, '').replace(/\s+Online.*$/i, '').trim();
                            // Intentar sacar año del meta title o dejar 0
                            results.push({ title: rawTitle, url: directUrl, img: "", id: directUrl, type: 'movie', year: "0" });
                        }
                    }
                }
                bridge.onResult(JSON.stringify(results));
            } catch (e) { bridge.onResult("[]"); }
        },

        resolveEpisode: async function(showUrl, season, episode) {
            // (Misma lógica v12, funciona bien)
            try {
                bridge.log("JS: Buscando episodio " + season + "x" + episode);
                const html = await bridge.fetchHtml(showUrl);
                const epPad = episode < 10 ? "0" + episode : episode;
                const pattern1 = new RegExp(season + "x" + episode + "[^0-9]", "i");
                const pattern2 = new RegExp(season + "x" + epPad + "[^0-9]", "i");
                const linkRegex = /<a[^>]+href=["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/g;
                let linkMatch;
                let episodeUrl = null;
                while ((linkMatch = linkRegex.exec(html)) !== null) {
                    const href = linkMatch[1];
                    const text = linkMatch[2];
                    if (!href.includes('/episodios/')) continue;
                    if (pattern1.test(text) || pattern2.test(text) || pattern1.test(href) || pattern2.test(href)) {
                        episodeUrl = href;
                        break;
                    }
                }
                if (episodeUrl) {
                    bridge.log("JS: Episodio encontrado: " + episodeUrl);
                    await this.resolveVideo(episodeUrl, "tv");
                } else {
                    bridge.log("JS: ❌ Episodio no encontrado.");
                    bridge.onResult("[]");
                }
            } catch (e) { bridge.onResult("[]"); }
        },

        // --- ARREGLO DRÁCULA (EXTRACCIÓN HÍBRIDA) ---
        resolveVideo: async function(url, type) {
            try {
                bridge.log("JS: Extrayendo de: " + url);
                const html = await bridge.fetchHtml(url);
                const servers = [];

                // Función auxiliar para escanear HTML buscando servidores
                const scanHtml = (sourceHtml, sourceName) => {
                    // 1. EMBED69
                    if (sourceHtml.includes('eyJ')) {
                        const jsonMatch = sourceHtml.match(/let dataLink = (\[.*?\]);/);
                        if (jsonMatch) {
                            try {
                                const data = JSON.parse(jsonMatch[1]);
                                data.forEach(group => {
                                    const lang = group.video_language || "Latino";
                                    if (group.sortedEmbeds) {
                                        group.sortedEmbeds.forEach(srv => {
                                            const sName = srv.servername.toLowerCase();
                                            if (sName.includes("download") || sName.includes("descarga")) return;
                                            try {
                                                const parts = srv.link.split('.');
                                                const payload = atob(parts[1].replace(/-/g, '+').replace(/_/g, '/'));
                                                const linkData = JSON.parse(payload);
                                                servers.push({ server: srv.servername, lang: lang, url: linkData.link });
                                            } catch(e){}
                                        });
                                    }
                                });
                            } catch(e){}
                        }
                    }
                    
                    // 2. XUPALACE / LISTA DE ITEMS (go_to_player)
                    const listRegex = /<li[^>]*onclick=["'](go_to_playerVast|go_to_player)\(['"]([^'"]+)['"]/g;
                    let m;
                    while ((m = listRegex.exec(sourceHtml)) !== null) {
                        let rawUrl = m[2];
                        if (rawUrl.includes('link=')) {
                            try { rawUrl = atob(rawUrl.split('link=')[1].split('&')[0]); } catch(e){}
                        }
                        // Detectar nombre
                        let name = "Server";
                        if (rawUrl.includes('filemoon')) name = "Filemoon";
                        else if (rawUrl.includes('waaw')) name = "Waaw";
                        else if (rawUrl.includes('vidhide')) name = "Vidhide";
                        else if (rawUrl.includes('voe')) name = "Voe";
                        else if (rawUrl.includes('streamwish')) name = "Streamwish";
                        
                        // Evitar duplicados exactos
                        if (!servers.some(s => s.url === rawUrl)) {
                            servers.push({ server: name, lang: "Latino", url: rawUrl });
                        }
                    }
                };

                // PASO 1: Escanear la página PRINCIPAL (Aquí suelen estar los <li> de Drácula)
                scanHtml(html, "MainPage");

                // PASO 2: Buscar iframe y escanearlo también (Si existe)
                const iframeMatch = html.match(/<iframe[^>]*\s(src|data-src)=["']([^"']+)["'][^>]*>/i);
                if (iframeMatch) {
                    let embedUrl = iframeMatch[2];
                    if (embedUrl.startsWith("//")) embedUrl = "https:" + embedUrl;
                    bridge.log("JS: Escaneando iframe: " + embedUrl);
                    try {
                        const embedHtml = await bridge.fetchHtml(embedUrl);
                        scanHtml(embedHtml, "Iframe");
                    } catch(e) { bridge.log("JS: Error cargando iframe"); }
                }

                bridge.log("JS: Total servidores: " + servers.length);
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
