(function() {
    const provider = {
        id: 'sololatino',
        name: 'SoloLatino',
        baseUrl: 'https://sololatino.net',
        headers: {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36',
            'Referer': 'https://sololatino.net/'
        },

        // --- BÚSQUEDA (MANTENEMOS LA v17 QUE YA FUNCIONA CON API) ---
        search: async function(query) {
            try {
                // 1. Obtener Nonce
                const homeHtml = await bridge.fetchHtml(this.baseUrl);
                const nonceMatch = homeHtml.match(/"nonce":"([^"]+)"/);
                if (!nonceMatch) return this.searchLegacy(query);

                const nonce = nonceMatch[1];
                const cleanQuery = encodeURIComponent(query);
                const apiUrl = `${this.baseUrl}/wp-json/dooplay/search/?keyword=${cleanQuery}&nonce=${nonce}`;
                
                const jsonStr = await bridge.fetchHtml(apiUrl);
                const data = JSON.parse(jsonStr);
                const results = [];
                const items = [];
                
                if (Array.isArray(data)) data.forEach(x => items.push(x));
                else if (typeof data === 'object') Object.values(data).forEach(val => items.push(val));

                items.forEach(item => {
                    if (item.url && item.title) {
                        let type = 'movie';
                        if (item.url.includes('/series/') || item.url.includes('/tvshows/')) type = 'tv';
                        let year = item.extra && item.extra.date ? item.extra.date : "0";
                        results.push({
                            title: item.title,
                            url: item.url,
                            img: item.img || "",
                            id: item.url,
                            type: type,
                            year: year
                        });
                    }
                });
                return results;
            } catch (e) { return this.searchLegacy(query); }
        },

        searchLegacy: async function(query) {
            try {
                const searchUrl = this.baseUrl + "/?s=" + encodeURIComponent(query);
                let html = await bridge.fetchHtml(searchUrl);
                const results = [];
                const regex = /<article[^>]*class="item\s+(movies|tvshows)"[^>]*>([\s\S]*?)<\/article>/g;
                let match;
                while ((match = regex.exec(html)) !== null) {
                    const content = match[2];
                    const url = content.match(/href="([^"]+)"/)[1];
                    const title = content.match(/<h3>([^<]+)<\/h3>/)[1];
                    const type = match[1] === 'tvshows' ? 'tv' : 'movie';
                    results.push({ title: title.trim(), url: url, img: "", id: url, type: type, year: "0" });
                }
                return results;
            } catch(e) { return []; }
        },

        resolveEpisode: async function(showUrl, season, episode) {
            try {
                const html = await bridge.fetchHtml(showUrl);
                const epPad = episode < 10 ? "0" + episode : episode;
                const regex = new RegExp(`href=["']([^"']*?(?:${season}x${episode}|${season}x${epPad})[^"']*)["']`, "i");
                const match = html.match(regex);
                if (match) return await this.resolveVideo(match[1], "tv");
                return [];
            } catch (e) { return []; }
        },

        // --- EL EXTRUJAR DE IFRAMES (VERSION DECRYPT) ---
        resolveVideo: async function(url, type) {
            try {
                bridge.log("JS: Analizando: " + url);
                const html = await bridge.fetchHtml(url);
                const servers = [];

                // 1. Buscamos el IFRAME del metaframe
                const iframeRegex = /<iframe[^>]*src=['"]([^'"]+)['"][^>]*class=['"]metaframe rptss['"][^>]*>/g;
                let match;
                
                while ((match = iframeRegex.exec(html)) !== null) {
                    let embedUrl = match[1];
                    bridge.log("JS: Iframe detectado: " + embedUrl);

                    // DETECTAMOS SI ES EMBED69 O XUPALACE PARA DESTRIPARLO
                    if (embedUrl.includes("embed69") || embedUrl.includes("xupalace")) {
                        
                        // Extraemos el ID (ej: tt14074586 o tt32063263-1x05)
                        // Suele estar después de /f/ o /video/
                        const idMatch = embedUrl.match(/\/(?:f|video)\/([a-zA-Z0-9-]+)/);
                        
                        if (idMatch) {
                            const videoId = idMatch[1];
                            bridge.log("JS: Intentando desencriptar ID: " + videoId);
                            
                            // Determinamos la API correcta
                            let apiUrl = "";
                            if (embedUrl.includes("embed69")) apiUrl = "https://embed69.org/api/decrypt";
                            else if (embedUrl.includes("xupalace")) apiUrl = "https://xupalace.org/api/decrypt"; // Asumimos mismo endpoint

                            if (apiUrl) {
                                // LLAMADA POST (USANDO OKHTTP DEL PUENTE)
                                // Enviamos 'id=tt...' como form-data
                                const jsonResp = await bridge.post(apiUrl, "id=" + videoId);
                                bridge.log("JS: Respuesta API: " + jsonResp);

                                try {
                                    const data = JSON.parse(jsonResp);
                                    if (data.success && data.links) {
                                        data.links.forEach(linkObj => {
                                            // linkObj.link tiene la URL final (ej: https://voe.sx/e/...)
                                            const finalUrl = linkObj.link;
                                            let host = "Server";
                                            
                                            // Detectar nombre bonito
                                            if (finalUrl.includes("voe")) host = "Voe";
                                            else if (finalUrl.includes("dintezuvio") || finalUrl.includes("aglayo")) host = "Doodstream"; // Dintezuvio suele ser Dood
                                            else if (finalUrl.includes("filemoon")) host = "Filemoon";
                                            else if (finalUrl.includes("streamwish")) host = "Streamwish";
                                            else if (finalUrl.includes("hglink")) host = "HgLink";
                                            else if (finalUrl.includes("bysedikamoum")) host = "Bysed";

                                            servers.push({
                                                server: host,
                                                lang: "Multi", // Estos suelen ser multi-audio o detectado luego
                                                url: finalUrl,
                                                quality: "HD",
                                                requiresWebView: true, // Para el reproductor final (Voe, etc)
                                                isDirect: false
                                            });
                                        });
                                    }
                                } catch (err) {
                                    bridge.log("JS: Error parseando JSON: " + err.message);
                                }
                            }
                        }
                    }
                }

                // Si falló lo de arriba, intentamos el método de API Dooplay como respaldo
                if (servers.length === 0) {
                     // ... (Podemos agregar el código v16 aquí si esto falla, pero confiemos en el decrypt)
                }

                bridge.log("JS: SERVIDORES EXTRAÍDOS: " + servers.length);
                return servers;

            } catch (e) {
                bridge.log("JS ERROR: " + e.message);
                return [];
            }
        }
    };

    if (typeof KronosEngine !== 'undefined') {
        KronosEngine.providers[provider.id] = provider;
    }
})();
