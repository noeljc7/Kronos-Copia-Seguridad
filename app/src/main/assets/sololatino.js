// KRONOS PROVIDER: SoloLatino v2.0 (FINAL)
// Soporta: Embed69 (JWT), Re.Latino (Base64), XuPalace (Directo)

(function() {
    const provider = {
        id: 'sololatino',
        name: 'SoloLatino',
        baseUrl: 'https://sololatino.net',
        
        headers: {
            'User-Agent': 'Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36',
            'Referer': 'https://sololatino.net/'
        },

        // --- 1. BUSQUEDA ---
        search: async function(query) {
            try {
                const cleanQuery = encodeURIComponent(query);
                const searchUrl = `${this.baseUrl}/?s=${cleanQuery}`;
                const html = await bridge.fetchHtml(searchUrl);
                
                const results = [];
                // Regex ajustado para capturar tanto películas como series
                const regex = /<article[^>]*class="[^"]*result-item[^"]*"[^>]*>[\s\S]*?<a href="([^"]+)"[\s\S]*?<img src="([^"]+)"[\s\S]*?<div class="title">([^<]+)<\/div>[\s\S]*?<span class="year">(\d{4})?<\/span>/g;
                
                let match;
                while ((match = regex.exec(html)) !== null) {
                    const url = match[1];
                    results.push({
                        title: match[3].trim(),
                        url: url,
                        img: match[2],
                        id: url,
                        type: url.includes('/peliculas/') ? 'movie' : 'tv',
                        year: match[4] || ""
                    });
                }
                return results;
            } catch (e) {
                bridge.log("Error Search: " + e.message);
                return [];
            }
        },

        // --- 2. RESOLVER VIDEO (El Núcleo) ---
        resolveVideo: async function(url, type) {
            try {
                bridge.log("Investigando URL: " + url);
                const html = await bridge.fetchHtml(url);

                // Paso 1: Buscar el IFRAME inicial (el portal)
                const iframeRegex = /<iframe[^>]*src=["']([^"']+)["'][^>]*>/i;
                const iframeMatch = html.match(iframeRegex);

                if (!iframeMatch) {
                    bridge.log("Alerta: No hay iframe inicial. Puede ser un link directo o error.");
                    return null;
                }

                let embedUrl = iframeMatch[1];
                bridge.log("Entrando al portal: " + embedUrl);

                // Paso 2: Descargar el contenido del portal
                const embedHtml = await bridge.fetchHtml(embedUrl);

                // --- SELECTOR DE ESTRATEGIA ---

                // CASO A: EMBED69 (JWT)
                if (embedUrl.includes('embed69') || embedHtml.includes('eyJ')) {
                    bridge.log("Estrategia Activada: Embed69 (JWT)");
                    return this.extractEmbed69(embedHtml);
                } 
                
                // CASO B: XUPALACE (Directo en onclick)
                else if (embedUrl.includes('xupalace') || embedHtml.includes('go_to_playerVast')) {
                    bridge.log("Estrategia Activada: XuPalace");
                    return this.extractXuPalace(embedHtml);
                }

                // CASO C: RE.SOLOLATINO (Base64 Clásico)
                else if (embedHtml.includes('go_to_player') || embedHtml.includes('SelectLangDisp')) {
                    bridge.log("Estrategia Activada: Clásica (Base64)");
                    return this.extractClassic(embedHtml);
                }

                return null;

            } catch (e) {
                bridge.log("Error Fatal Resolve: " + e.message);
                return null;
            }
        },

        // --- EXTRACTORES ---

        extractEmbed69: function(html) {
            // Busca token JWT
            const jwtRegex = /"link"\s*:\s*"([a-zA-Z0-9\-_]+\.[a-zA-Z0-9\-_]+\.[a-zA-Z0-9\-_]+)"/g;
            let match;
            while ((match = jwtRegex.exec(html)) !== null) {
                try {
                    const parts = match[1].split('.');
                    const payload = atob(parts[1].replace(/-/g, '+').replace(/_/g, '/'));
                    const data = JSON.parse(payload);
                    if (data && data.link) return data.link; // Retorna el primer link válido
                } catch (err) {}
            }
            return null;
        },

        extractXuPalace: function(html) {
            // Busca: go_to_playerVast('URL', ...)
            // El HTML es: onclick="go_to_playerVast('https://voe.sx/...', 2, 1);"
            const regex = /go_to_playerVast\(['"]([^'"]+)['"]/;
            const match = html.match(regex);
            
            if (match && match[1]) {
                bridge.log("Link XuPalace encontrado: " + match[1]);
                return match[1];
            }
            
            // Fallback: A veces usan go_to_player normal en xupalace también
            return this.extractClassic(html);
        },

        extractClassic: function(html) {
            // Busca: go_to_player('URL')
            const regex = /go_to_player\(['"]([^'"]+)['"]\)/g;
            let match;
            const priorities = ['mega.nz', '1fichier', 'voe.sx', 'vidhide', 'streamwish'];
            let lastFound = null;

            while ((match = regex.exec(html)) !== null) {
                let rawUrl = match[1];
                
                // Decodificar Base64 si es necesario (param link=...)
                if (rawUrl.includes('link=')) {
                    try {
                        const b64 = rawUrl.split('link=')[1].split('&')[0];
                        rawUrl = atob(b64);
                    } catch(e) {}
                }

                lastFound = rawUrl;
                // Si encontramos un servidor VIP, retornamos de inmediato
                for (let p of priorities) {
                    if (rawUrl.includes(p)) return rawUrl;
                }
            }
            return lastFound; // Si no hay VIP, devuelve el último que encontró
        }
    };

    // Inyección en el Motor
    if (typeof KronosEngine !== 'undefined') {
        KronosEngine.providers[provider.id] = provider;
    }
})();
