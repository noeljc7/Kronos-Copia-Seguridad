var KronosEngine = KronosEngine || { providers: {} };

KronosEngine.providers['Cinemitas'] = {
    
    // Función Maestra: Convierte la petición de Kronos en enlaces de video
    getLinks: function(dataStr) {
        var links = [];
        try {
            var data = JSON.parse(dataStr);
            var query = data.title; // Ej: "Chainsaw Man"
            var isMovie = (data.type === "movie");
            var season = data.season;
            var episode = data.episode;

            // 1. CONSULTAR LA API JSON (Tu descubrimiento clave)
            var searchUrl = "https://cinemitas.org/wp-json/dooplay/search/?keyword=" + encodeURIComponent(query);
            bridge.log("Cinemitas buscando API: " + searchUrl);
            
            var jsonResponse = bridge.fetchHtml(searchUrl);
            if (!jsonResponse || jsonResponse.length < 5) return links;

            var results = JSON.parse(jsonResponse);
            
            // La API devuelve un objeto con IDs como claves: {"1234": {...}, "5678": {...}}
            for (var key in results) {
                var item = results[key];
                
                // Verificamos que tenga título y URL
                if (item.title && item.url) {
                    
                    var targetUrl = item.url;

                    // 2. LÓGICA INTELIGENTE PARA SERIES
                    // La API devuelve la URL de la serie (ej: .../tvshows/robin-hood/)
                    // Pero necesitamos el episodio (ej: .../episodes/robin-hood-1x4/)
                    if (!isMovie && season && episode) {
                        // Paso A: Detectar si la URL es de una serie (/tvshows/)
                        if (targetUrl.indexOf("/tvshows/") !== -1) {
                            // Paso B: Transformar la URL. 
                            // De: https://cinemitas.org/tvshows/slug-serie/
                            // A:  https://cinemitas.org/episodes/slug-serie-1x4/
                            
                            // Quitamos la barra final si existe
                            if (targetUrl.endsWith("/")) targetUrl = targetUrl.slice(0, -1);
                            
                            // Reemplazamos 'tvshows' por 'episodes'
                            targetUrl = targetUrl.replace("/tvshows/", "/episodes/");
                            
                            // Agregamos la temporada y episodio
                            targetUrl = targetUrl + "-" + season + "x" + episode + "/";
                        }
                    }

                    bridge.log("Objetivo detectado (" + item.title + ") -> URL: " + targetUrl);

                    // 3. ENTRAR A LA PÁGINA FINAL (Película o Episodio)
                    var pageHtml = bridge.fetchHtml(targetUrl);
                    
                    if (pageHtml) {
                        // 4. EXTRAER IFRAMES DE VIDEO
                        // Buscamos patrones src="https://..." que contengan servidores de video conocidos
                        // Basado en tus archivos, buscamos cosas como 'cvid.lat'
                        
                        var iframeRegex = /src\s*=\s*"([^"]+)"/g;
                        var match;
                        
                        while ((match = iframeRegex.exec(pageHtml)) !== null) {
                            var src = match[1];
                            
                            // Filtro de calidad: Solo iframes que parezcan videos reales
                            // Agregamos 'cvid.lat' porque apareció en tus archivos de texto
                            if (src.indexOf("cvid.lat") !== -1 || src.indexOf("video") !== -1 || src.indexOf("embed") !== -1 || src.indexOf("player") !== -1) {
                                
                                // Limpieza básica de URL
                                if (src.startsWith("//")) src = "https:" + src;
                                
                                links.push({
                                    "name": "Cinemitas - " + item.title,
                                    "url": src,
                                    "quality": "SD/HD",
                                    "language": "Latino", // Cinemitas es mayormente Latino
                                    "isDirect": false    // Es un iframe, requiere que el Resolver (ej: Voe) lo procese después
                                });
                            }
                        }
                    }
                }
            }

        } catch (e) {
            bridge.log("Error crítico en Cinemitas: " + e.toString());
        }
        
        return links;
    }
};

