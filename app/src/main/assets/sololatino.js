// KRONOS PROVIDER: SoloLatino v4.0 (Final - Soporte Redirección)
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
                // Limpiamos la query: "Batman: Inicia" -> "Batman Inicia"
                // Esto ayuda a que WordPress encuentre mejor la coincidencia
                const cleanQuery = encodeURIComponent(query.replace(/[:\-\.]/g, ' '));
                const searchUrl = this.baseUrl + "/?s=" + cleanQuery;
                
                bridge.log("JS: Buscando: " + searchUrl);
                
                let html = "";
                try {
                    html = await bridge.fetchHtml(searchUrl);
                } catch (netError) {
                    bridge.log("JS: Error red: " + netError.message);
                    return [];
                }

                if (!html || html.length < 100) return [];

                const results = [];
                
                // --- ESTRATEGIA 1: Búsqueda Normal (Lista de resultados) ---
                // Si el sitio nos devuelve una lista, la procesamos aquí
                const regexList = /<article[^>]*class="[^"]*result-item[^"]*"[^>]*>[\s\S]*?<a href="([^"]+)"[\s\S]*?<img src="([^"]+)"[\s\S]*?<div class="title">([^<]+)<\/div>[\s\S]*?<span class="year">(\d{4})?<\/span>/g;
                
                let match;
                while ((match = regexList.exec(html)) !== null) {
                    results.push({
                        title: match[3].trim(),
                        url: match[1],
                        img: match[2],
                        id: match[1],
                        type: match[1].includes('/peliculas/') ? 'movie' : 'tv',
                        year: match[4] || ""
                    });
                }

                // --- ESTRATEGIA 2: DETECCIÓN DE REDIRECCIÓN (El arreglo para tu problema) ---
                // Si la lista está vacía, verificamos si nos mandaron directo a la película
                if (results.length === 0) {
                    bridge.log("JS: Lista vacía. Verificando si hubo redirección directa...");
                    
                    // Buscamos la URL canónica (indica la url real de la página actual)
                    const canonicalMatch = html.match(/<link\s+rel=["']canonical["']\s+href=["']([^"']+)["']/i);
                    // Buscamos el título en los metadatos
                    const titleMatch = html.match(/<meta\s+property=["']og:title["']\s+content=["']([^"']+)["']/i);
                    // Buscamos la imagen
                    const imgMatch = html.match(/<meta\s+property=["']og:image["']\s+content=["']([^"']+)["']/i);

                    if (canonicalMatch && titleMatch) {
                        const directUrl = canonicalMatch[1];
                        
                        // Validamos que sea una URL de contenido (no la home ni error 404)
                        if (directUrl.includes('/peliculas/') || directUrl.includes('/episodios/') || directUrl.includes('/series/')) {
                            bridge.log("JS: ¡REDIRECCIÓN DETECTADA! Usando página actual como resultado.");
                            
                            // Limpiamos el título (a veces dice "Ver Batman Online...")
                            let rawTitle = titleMatch[1];
                            rawTitle = rawTitle.replace(/^Ver\s+/i, '').replace(/\s+Online.*$/i, '').trim();

                            results.push({
                                title: rawTitle,
                                url: directUrl,
                                img: imgMatch ? imgMatch[1] : "",
                                id: directUrl,
                                type: directUrl.includes('/peliculas/') ? 'movie' : 'tv',
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

        // El resto sigue igual (resolveVideo)...
        resolveVideo: async function(url, type) {
            try {
                bridge.log("JS: Resolviendo: " + url);
                const html = await bridge.fetchHtml(url);

                const iframeMatch = html.match(/<iframe[^>]*src=["']([^"']+)["'][^>]*>/i);
                if (!iframeMatch) return null;

                let embedUrl = iframeMatch[1];
                if (embedUrl.startsWith("//")) embedUrl = "https:" + embedUrl;
                
                bridge.log("JS: Portal: " + embedUrl);
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
