// KRONOS PROVIDER: SoloLatino DEBUG v3.0
(function() {
    const provider = {
        id: 'sololatino',
        name: 'SoloLatino',
        baseUrl: 'https://sololatino.net',
        
        headers: {
            'User-Agent': 'Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36'
        },

        search: async function(query) {
            try {
                bridge.log("JS: Iniciando búsqueda para: " + query);
                
                const cleanQuery = encodeURIComponent(query);
                const searchUrl = this.baseUrl + "/?s=" + cleanQuery;
                
                bridge.log("JS: Fetching URL: " + searchUrl);
                
                // Intento de descarga
                let html = "";
                try {
                    html = await bridge.fetchHtml(searchUrl);
                } catch (netError) {
                    bridge.log("JS: Error en fetchHtml: " + netError.message);
                    return []; // Retorna array vacío, no null ni objeto
                }

                if (!html || html.length < 100) {
                    bridge.log("JS: HTML vacío o muy corto. Cloudflare?");
                    return [];
                }

                bridge.log("JS: HTML descargado (" + html.length + " bytes)");

                const results = [];
                // Regex simplificado para evitar errores de sintaxis
                const regex = /<article[^>]*class="[^"]*result-item[^"]*"[^>]*>[\s\S]*?<a href="([^"]+)"[\s\S]*?<img src="([^"]+)"[\s\S]*?<div class="title">([^<]+)<\/div>[\s\S]*?<span class="year">(\d{4})?<\/span>/g;
                
                let match;
                while ((match = regex.exec(html)) !== null) {
                    results.push({
                        title: match[3].trim(),
                        url: match[1],
                        img: match[2],
                        id: match[1],
                        type: match[1].includes('/peliculas/') ? 'movie' : 'tv',
                        year: match[4] || ""
                    });
                }
                
                bridge.log("JS: Encontrados " + results.length + " resultados");
                
                // OJO: Kotlin espera un JSON String, no un objeto JS
                // Pero el puente se encarga de eso. Devolvemos el array.
                return results;

            } catch (e) {
                bridge.log("JS CRASH: " + e.message);
                return [];
            }
        },

        resolveVideo: async function(url, type) {
            // ... (Dejar el resto igual por ahora, el problema es search)
            return null;
        }
    };

    if (typeof KronosEngine !== 'undefined') {
        KronosEngine.providers[provider.id] = provider;
        console.log("SoloLatino JS Cargado OK");
    }
})();
