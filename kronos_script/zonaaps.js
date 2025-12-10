(function() {
    const provider = {
        id: 'zonaaps',
        name: 'ZonaAps',
        baseUrl: 'https://zonaaps.com',
        headers: {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36',
            'Referer': 'https://zonaaps.com/',
            'X-Requested-With': 'XMLHttpRequest' // ¡CRUCIAL! Sin esto, la API te bloquea
        },

        search: async function(query) {
            // Tu búsqueda HTML v3.0 funcionaba bien, la mantenemos para no complicarnos con Nonces
            try {
                const cleanQuery = encodeURIComponent(query.replace(/[:\-\.]/g, ' '));
                const searchUrl = this.baseUrl + "/?s=" + cleanQuery;
                bridge.log("JS [ZNA]: Buscando: " + searchUrl);

                let html = await bridge.fetchHtml(searchUrl);
                if (!html || html.length < 100) return [];

                const results = [];
                const regex = /<div class="result-item">[\s\S]*?<a href="([^"]+)"[\s\S]*?<img src="([^"]+)"[^>]*?alt="([^"]+)"/g;
                let match;
                
                while ((match = regex.exec(html)) !== null) {
                    const url = match[1];
                    const img = match[2];
                    const fullTitle = match[3];
                    let title = fullTitle;
                    let year = "0";
                    const yearMatch = fullTitle.match(/(.*)\s\((\d{4})\)$/);
                    if (yearMatch) { title = yearMatch[1].trim(); year = yearMatch[2]; }
                    let type = (url.includes('/tvshows/') || url.includes('/episodes/')) ? 'tv' : 'movie';
                    results.push({ title: title, url: url, img: img, id: url, type: type, year: year });
                }
                bridge.onResult(JSON.stringify(results));
            } catch (e) { bridge.onResult("[]"); }
        },

        resolveVideo: async function(url, type) {
            try {
                bridge.log("JS [ZNA]: Escaneando página -> " + url);
                const html = await bridge.fetchHtml(url);
                const servers = [];

                // 1. Regex idéntico al de Kotlin para buscar los botones <li>
                // <li id='player-option-1' ... data-post='28776' data-type='movie' data-nume='1'>
                const liRegex = /<li[^>]+data-post=['"](\d+)['"][^>]*data-type=['"]([^"']+)['"][^>]*data-nume=['"](\d+)['"][^>]*>([\s\S]*?)<\/li>/g;
                
                let match;
                // Array para guardar las promesas (peticiones) y hacerlas todas juntas
                const apiRequests = [];

                while ((match = liRegex.exec(html)) !== null) {
                    const postId = match[1];
                    const pType = match[2];
                    const nume = match[3];
                    const content = match[4]; // Contenido para sacar el nombre del server

                    // Filtramos el trailer
                    if (nume === "trailer") continue;

                    // Extraer nombre del servidor (igual que en Kotlin)
                    let serverName = "Server";
                    const titleMatch = content.match(/<span class=['"]title['"]>(.*?)<\/span>/);
                    if (titleMatch) serverName = titleMatch[1].replace("🔒", "").trim();

                    // Detectar idioma (igual que en Kotlin)
                    let lang = "Latino";
                    const lowerContent = content.toLowerCase();
                    if (lowerContent.includes("castellano") || lowerContent.includes("es.png")) lang = "Castellano";
                    else if (lowerContent.includes("sub") || lowerContent.includes("en.png")) lang = "Subtitulado";

                    bridge.log(`JS [ZNA]: Encontrada Opción ${nume} (${serverName})`);

                    // 2. CONSTRUIR URL MÁGICA (La joya del código Kotlin)
                    const apiUrl = `${this.baseUrl}/wp-json/dooplayer/v2/${postId}/${pType}/${nume}`;
                    
                    // Añadimos la tarea de ir a buscar el link real
                    apiRequests.push(this.fetchApiLink(apiUrl, serverName, lang));
                }

                // Esperamos a que todas las peticiones a la API terminen
                if (apiRequests.length > 0) {
                    bridge.log(`JS [ZNA]: Consultando API para ${apiRequests.length} servidores...`);
                    const resolvedServers = await Promise.all(apiRequests);
                    // Filtramos los nulos (los que fallaron) y agregamos al array final
                    resolvedServers.forEach(s => { if (s) servers.push(s); });
                }

                bridge.onResult(JSON.stringify(servers));

            } catch (e) { 
                bridge.log("JS [ZNA]: Error -> " + e.message);
                bridge.onResult("[]"); 
            }
        },

        // Función auxiliar para llamar a la API REST
        fetchApiLink: async function(apiUrl, serverName, lang) {
            try {
                // Hacemos fetch a la API. El puente usará los headers definidos arriba (X-Requested-With)
                const jsonStr = await bridge.fetchHtml(apiUrl);
                const json = JSON.parse(jsonStr);
                
                // Kotlin buscaba "embed_url" o "u"
                const targetUrl = json.embed_url || json.u;

                if (targetUrl) {
                    // Determinar si necesita WebView
                    const isDirect = targetUrl.endsWith(".mp4") || targetUrl.endsWith(".m3u8");
                    
                    return {
                        server: serverName,
                        lang: lang,
                        url: targetUrl,
                        requiresWebView: !isDirect
                    };
                }
            } catch (e) {
                // bridge.log("Fallo API individual: " + apiUrl);
            }
            return null;
        },

        resolveEpisode: async function(url, season, episode) {
            // (Misma lógica v2.0 - funciona bien)
            try {
                if (url.includes('/tvshows/')) {
                    const html = await bridge.fetchHtml(url);
                    const epPad = episode < 10 ? "0" + episode : episode;
                    const epRegex = new RegExp(`href="([^"]*?(?:${season}x${episode}|${season}x${epPad})[^"]*)"`, "i");
                    const match = html.match(epRegex);
                    if (match) await this.resolveVideo(match[1], 'tv');
                    else {
                        const slug = url.split('/tvshows/')[1].replace('/','');
                        await this.resolveVideo(`${this.baseUrl}/episodes/${slug}-${season}x${episode}/`, 'tv');
                    }
                } else {
                    await this.resolveVideo(url, 'tv');
                }
            } catch (e) { bridge.onResult("[]"); }
        }
    };

    if (typeof KronosEngine !== 'undefined') {
        KronosEngine.providers[provider.id] = provider;
    }
})();
