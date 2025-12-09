// KRONOS PROVIDER: SoloLatino v6.0 (Dooplay Surgical Fix)
(function() {
    const provider = {
        id: 'sololatino',
        name: 'SoloLatino',
        baseUrl: 'https://sololatino.net',
        
        headers: {
            'User-Agent': 'Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36',
            'Referer': 'https://sololatino.net/'
        },

        search: async function(query) {
            try {
                // 1. Preparamos la URL
                const cleanQuery = encodeURIComponent(query.replace(/[:\-\.]/g, ' '));
                const searchUrl = this.baseUrl + "/?s=" + cleanQuery;
                
                bridge.log("JS: Buscando URL: " + searchUrl);
                
                let html = "";
                try {
                    html = await bridge.fetchHtml(searchUrl);
                } catch (e) {
                    bridge.log("JS Error: " + e.message);
                    return [];
                }

                if (!html || html.length < 100) return [];

                const results = [];

                // --- ESTRATEGIA QUIRÚRGICA (Basada en tu HTML) ---
                // El HTML usa <article class="item movies"> o "item tvshows"
                // Buscamos bloques <article ... /article>
                
                // Regex para encontrar cada bloque de película/serie individualmente
                const articleRegex = /<article[^>]*class="item\s+(movies|tvshows)"[^>]*>([\s\S]*?)<\/article>/g;
                
                let match;
                while ((match = articleRegex.exec(html)) !== null) {
                    const typeFound = match[1]; // "movies" o "tvshows"
                    const content = match[2]; // El HTML dentro del article

                    // Extraemos datos DENTRO del bloque encontrado
                    
                    // 1. URL
                    const urlMatch = content.match(/href="([^"]+)"/);
                    if (!urlMatch) continue;
                    
                    // 2. Imagen (Usamos data-srcset porque src es lazy.gif)
                    const imgMatch = content.match(/data-srcset="([^"\s]+)/) || content.match(/src="([^"]+)"/);
                    
                    // 3. Título (Está dentro de <h3>)
                    const titleMatch = content.match(/<h3>([^<]+)<\/h3>/);
                    
                    // 4. Año (Está dentro de <p>)
                    const yearMatch = content.match(/<p>(\d{4})<\/p>/);

                    if (titleMatch) {
                        results.push({
                            title: titleMatch[1].trim(),
                            url: urlMatch[1],
                            img: imgMatch ? imgMatch[1] : "",
                            id: urlMatch[1],
                            type: typeFound === 'tvshows' ? 'tv' : 'movie',
                            year: yearMatch ? yearMatch[1] : ""
                        });
                    }
                }

                // --- ESTRATEGIA RESPALDO: REDIRECCIÓN ---
                // (Solo por si acaso WordPress decide redirigir alguna vez)
                if (results.length === 0) {
                    const canonicalMatch = html.match(/<link\s+rel=["']canonical["']\s+href=["']([^"']+)["']/i);
                    const titleMatch = html.match(/<meta\s+property=["']og:title["']\s+content=["']([^"']+)["']/i);
                    
                    if (canonicalMatch && titleMatch) {
                        const directUrl = canonicalMatch[1];
                        if (directUrl.includes('/peliculas/') || directUrl.includes('/series/')) {
                            bridge.log("JS: Redirección detectada.");
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
                
                bridge.log("JS: Resultados finales: " + results.length);
                return results;

            } catch (e) {
                bridge.log("JS CRASH: " + e.message);
                return [];
            }
        },

        resolveVideo: async function(url, type) {
            try {
                bridge.log("JS: Resolviendo: " + url);
                const html = await bridge.fetchHtml(url);

                const iframeMatch = html.match(/<iframe[^>]*src=["']([^"']+)["'][^>]*>/i);
                if (!iframeMatch) return null;

                let embedUrl = iframeMatch[1];
                if (embedUrl.startsWith("//")) embedUrl = "https:" + embedUrl;
                
                const embedHtml = await bridge.fetchHtml(embedUrl);

                // 1. EMBED69 (JWT)
                if (embedUrl.includes('embed69') || embedHtml.includes('eyJ')) {
                    const jwtRegex = /"link"\s*:\s*"([a-zA-Z0-9\-_]+\.[a-zA-Z0-9\-_]+\.[a-zA-Z0-9\-_]+)"/g;
                    let match;
                    while ((match = jwtRegex.exec(embedHtml)) !== null) {
                        try {
                            const parts = match[1].split('.');
                            const payload = atob(parts[1].replace(/-/g, '+').replace(/_/g, '/'));
                            const data = JSON.parse(payload);
                            if (data && data.link) return data.link;
                        } catch (err) {}
                    }
                } 
                // 2. XUPALACE
                else if (embedUrl.includes('xupalace') || embedHtml.includes('go_to_playerVast')) {
                    const match = embedHtml.match(/go_to_playerVast\(['"]([^'"]+)['"]/);
                    if (match) return match[1];
                }
                // 3. CLASICO
                else {
                    const regex = /go_to_player\(['"]([^'"]+)['"]\)/g;
                    let match;
                    while ((match = regex.exec(embedHtml)) !== null) {
                        let rawUrl = match[1];
                        if (rawUrl.includes('link=')) {
                            try {
                                const b64 = rawUrl.split('link=')[1].split('&')[0];
                                rawUrl = atob(b64);
                            } catch(e) {}
                        }
                        if (rawUrl.includes('vidhide') || rawUrl.includes('wish') || rawUrl.includes('voe')) return rawUrl;
                    }
                }
                return null;
            } catch (e) { return null; }
        }
    };

    if (typeof KronosEngine !== 'undefined') {
        KronosEngine.providers[provider.id] = provider;
    }
})();
