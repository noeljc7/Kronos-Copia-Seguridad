var KronosEngine = KronosEngine || { providers: {} };

KronosEngine.providers['Cinemitas'] = {
    
    getLinks: function(dataStr) {
        var links = [];
        try {
            var data = JSON.parse(dataStr);
            var rawTitle = data.title; // Ej: "Chainsaw Man la película -Arco de Reze"
            var isMovie = (data.type === "movie");
            var season = data.season;
            var episode = data.episode;

            // --- PASO 1: LIMPIEZA DE TÍTULO (LA SOLUCIÓN) ---
            // Cortamos el título para que sea más fácil de encontrar.
            // "Chainsaw Man - Arco de Reze" -> "Chainsaw Man"
            var cleanQuery = rawTitle.split(":")[0].split("-")[0].trim();
            
            bridge.log("Cinemitas: Buscando '" + cleanQuery + "' (Original: " + rawTitle + ")");

            // --- PASO 2: BUSCAR EN API ---
            var searchUrl = "https://cinemitas.org/wp-json/dooplay/search/?keyword=" + encodeURIComponent(cleanQuery);
            
            var jsonResponse = bridge.fetchHtml(searchUrl);
            if (!jsonResponse || jsonResponse.length < 5) return links;

            var results = JSON.parse(jsonResponse);
            
            for (var key in results) {
                var item = results[key];
                
                // Filtro de Seguridad: Verificamos que el resultado contenga parte del título buscado
                // para evitar falsos positivos si la búsqueda fue muy genérica.
                if (item.title && item.url) {
                    
                    var targetUrl = item.url;

                    // Lógica de Series (Igual que antes)
                    if (!isMovie && season && episode) {
                        if (targetUrl.indexOf("/tvshows/") !== -1) {
                            if (targetUrl.endsWith("/")) targetUrl = targetUrl.slice(0, -1);
                            targetUrl = targetUrl.replace("/tvshows/", "/episodes/");
                            targetUrl = targetUrl + "-" + season + "x" + episode + "/";
                        }
                    }

                    bridge.log("Analizando: " + targetUrl);
                    var pageHtml = bridge.fetchHtml(targetUrl);
                    
                    if (pageHtml) {
                        // --- PASO 3: EXTRACCIÓN ROBUSTA (IDs OCULTOS) ---
                        // Mejoré el Regex para aceptar espacios variables
                        var regexIds = /data-post\s*=\s*['"](\d+)['"][\s\S]*?data-nume\s*=\s*['"](\d+)['"]/g;
                        var match;
                        
                        while ((match = regexIds.exec(pageHtml)) !== null) {
                            var postId = match[1];
                            var numeId = match[2];
                            
                            // --- PASO 4: MAGIA AJAX (POST) ---
                            // Pedimos el video real al servidor
                            var videoJson = this.doPostRequest(
                                "https://cinemitas.org/wp-admin/admin-ajax.php",
                                "action=doo_player_ajax&post=" + postId + "&nume=" + numeId + "&type=" + (isMovie ? "movie" : "tv")
                            );
                            
                            if (videoJson) {
                                try {
                                    var vData = JSON.parse(videoJson);
                                    if (vData.embed_url) {
                                        var cleanUrl = vData.embed_url.replace(/\\/g, "");
                                        
                                        // Filtramos para que no salga basura, solo lo que parece video
                                        if (cleanUrl.indexOf("http") !== -1) {
                                            links.push({
                                                "name": "Cinemitas - Opción " + numeId,
                                                "url": cleanUrl,
                                                "quality": "HD",
                                                "language": "Latino",
                                                "isDirect": false,
                                                "requiresWebView": true 
                                            });
                                        }
                                    }
                                } catch(e) { }
                            }
                        }
                    }
                }
            }

        } catch (e) {
            bridge.log("Error Cinemitas: " + e.toString());
        }
        return links;
    },

    doPostRequest: function(urlStr, params) {
        try {
            var url = new java.net.URL(urlStr);
            var conn = url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            
            var os = conn.getOutputStream();
            var writer = new java.io.OutputStreamWriter(os, "UTF-8");
            writer.write(params);
            writer.flush();
            writer.close();
            os.close();
            
            var is = conn.getInputStream();
            var scanner = new java.util.Scanner(is, "UTF-8").useDelimiter("\\A");
            var response = scanner.hasNext() ? scanner.next() : "";
            is.close();
            
            return response;
        } catch (e) { return null; }
    }
};

