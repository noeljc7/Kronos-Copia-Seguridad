(function() {
    KronosEngine.register({
        name: "Cinemitas",
        baseUrl: "https://cinemitas.org",
        lang: "Latino",
        type: "mixed",

        // --- VARIABLES INTERNAS (Para guardar estado) ---
        _headers: {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
            "Referer": "https://cinemitas.org",
            "X-Requested-With": "XMLHttpRequest"
        },
        _nonce: null,

        // --- HELPER: Obtener Nonce (Igual que en Python) ---
        _getNonce: function() {
            if (this._nonce) return this._nonce;
            try {
                var html = Android.fetchHtml(this.baseUrl);
                // Buscamos scripts base64
                // Python: re.findall(r'src=["\']data:text/javascript;base64,([^"\']+)["\']', r.text)
                var regex = /src=["']data:text\/javascript;base64,([^"']+)["']/;
                var matches = html.match(new RegExp(regex, "g")); // Buscar todos

                if (matches) {
                    for (var i = 0; i < matches.length; i++) {
                        // Limpiamos la cadena para obtener solo el b64
                        var b64 = matches[i].match(regex)[1];
                        try {
                            // Decodificar Base64 (En JS usamos atob)
                            // Ojo: Android Rhino podría no tener atob, usamos helper java o truco
                            var decoded = java.lang.String(android.util.Base64.decode(b64, 0)); 
                            
                            // Buscar nonce dentro del decodificado
                            var nonceMatch = decoded.match(/["']nonce["']\s*:\s*["']([a-zA-Z0-9]+)["']/);
                            if (nonceMatch) {
                                this._nonce = nonceMatch[1];
                                return this._nonce;
                            }
                        } catch (e) {}
                    }
                }
            } catch (e) { Android.log("Error Nonce: " + e); }
            return "";
        },

        // 1. BUSCAR (Replicando lógica Python)
        search: function(query) {
            try {
                // var nonce = this._getNonce(); // TODO: Implementar si la búsqueda falla sin nonce
                // Nota: A veces Dooplay funciona sin nonce si usas la búsqueda web normal (?s=)
                // Usaremos la búsqueda web normal primero por simplicidad, como en el test anterior
                
                var url = this.baseUrl + "/?s=" + encodeURIComponent(query);
                var html = Android.fetchHtml(url);
                var doc = Jsoup.parse(html);
                var items = [];
                
                var elements = doc.select("article.item");
                for(var i=0; i<elements.size(); i++) {
                    var el = elements.get(i);
                    var link = el.select("a").attr("href");
                    var img = el.select("img").attr("src");
                    var title = el.select(".title").text();
                    var year = el.select(".year").text();
                    
                    if (link && title) {
                        items.push({
                            title: title,
                            year: year,
                            url: link,
                            poster: img,
                            isMovie: !link.includes("/tvshows/")
                        });
                    }
                }
                return JSON.stringify(items);
            } catch(e) {
                return "[]";
            }
        },

        // 2. DETALLES (Replicando get_episodes de Python)
        getDetails: function(url) {
            try {
                var html = Android.fetchHtml(url);
                var doc = Jsoup.parse(html);
                
                var details = {
                    title: doc.select("h1.s-title").text(),
                    desc: doc.select("div.wp-content p").text(),
                    background: doc.select("meta[property='og:image']").attr("content"),
                    episodes: []
                };

                // Si es serie, extraemos episodios
                // Python buscaba enlaces: .../episodes/serie-1x1/
                var eps = doc.select("div#seasons .se-c .se-a ul.episodios li");
                
                for(var i=0; i<eps.size(); i++) {
                    var ep = eps.get(i);
                    var link = ep.select("a").attr("href");
                    var img = ep.select("img").attr("src");
                    var title = ep.select(".episodiotitle a").text();
                    var numText = ep.select(".numerando").text(); // "1 - 1"
                    
                    var season = 1;
                    var episode = i + 1;
                    
                    // Intentar sacar S y E del texto "1 - 1"
                    if (numText.includes("-")) {
                        var parts = numText.split("-");
                        season = parseInt(parts[0].trim());
                        episode = parseInt(parts[1].trim());
                    }

                    details.episodes.push({
                        name: title,
                        url: link, // Guardamos la URL del episodio para luego sacar el ID
                        season: season,
                        episode: episode,
                        poster: img
                    });
                }
                
                // Si es película, necesitamos el ID ahora
                if (details.episodes.length === 0) {
                    var shortlink = doc.select("link[rel=shortlink]").attr("href");
                    if (shortlink) {
                        details.internalId = shortlink.split("?p=")[1];
                    }
                }

                return JSON.stringify(details);
            } catch(e) {
                return "{}";
            }
        },

        // 3. OBTENER ENLACES (Replicando play_video de Python)
        getLinks: function(url) {
            try {
                var postId = "";
                
                // PASO 1: Obtener ID (Si es serie, hay que entrar a la URL del episodio primero)
                // Python: api.get_post_id_from_url(url_ref)
                var html = Android.fetchHtml(url);
                var doc = Jsoup.parse(html);
                var shortlink = doc.select("link[rel=shortlink]").attr("href");
                
                if (shortlink) {
                    postId = shortlink.split("?p=")[1];
                } else {
                    return "[]"; // No ID, no party
                }

                // PASO 2: Obtener iframe URL (AJAX)
                // Python: api.get_server_url(post_id, c_type)
                // Simulamos buscar todos los servidores disponibles en la página del episodio/peli
                var links = [];
                var options = doc.select("#player-options-ul li");

                for(var i=0; i<options.size(); i++) {
                    var opt = options.get(i);
                    var type = opt.attr("data-type");
                    var post = opt.attr("data-post"); // Debería ser el mismo postId
                    var nume = opt.attr("data-nume");
                    var title = opt.select(".title").text();

                    // Llamada AJAX
                    var ajaxUrl = this.baseUrl + "/wp-admin/admin-ajax.php";
                    var postData = "action=doo_player_ajax&post=" + post + "&nume=" + nume + "&type=" + type;
                    var jsonResponse = Android.post(ajaxUrl, postData);
                    
                    if (jsonResponse) {
                        var json = JSON.parse(jsonResponse);
                        if (json.embed_url) {
                            var embedUrl = json.embed_url.replace(/\\/g, "");
                            
                            // PASO 3: Resolver CVID/Filemoon (Replicando resolve_video de Python)
                            // Python: api.resolve_video(iframe_url)
                            // Aquí decidimos si resolvemos en JS o mandamos al Sniffer
                            
                            // Si es un hoster difícil (CVID con redirects), mejor lo mandamos al Sniffer (isDirect: false)
                            // Si queremos intentar resolverlo aquí:
                            
                            if (embedUrl.includes("cvid.lat") || embedUrl.includes("filemoon")) {
                                // Intentamos sacar el m3u8 final como en Python
                                var m3u8 = this._resolveCvid(embedUrl);
                                if (m3u8) {
                                    links.push({
                                        server: title,
                                        url: m3u8,
                                        lang: "Latino",
                                        quality: "HD",
                                        isDirect: true // ¡Éxito!
                                    });
                                } else {
                                    // Falló la resolución JS, mandamos el embed al Sniffer
                                    links.push({
                                        server: title + " (Web)",
                                        url: embedUrl,
                                        lang: "Latino",
                                        quality: "HD",
                                        isDirect: false 
                                    });
                                }
                            } else {
                                // Otros servidores, directo al sniffer por seguridad
                                links.push({
                                    server: title,
                                    url: embedUrl,
                                    lang: "Latino",
                                    quality: "HD",
                                    isDirect: false
                                });
                            }
                        }
                    }
                }
                return JSON.stringify(links);
            } catch(e) {
                Android.log("Error: " + e);
                return "[]";
            }
        },

        // --- HELPER: Resolver CVID (Traducido de Python) ---
        _resolveCvid: function(url) {
            try {
                // 1. Ir a /f/
                // Python: self.session.headers.update({"Referer": self.base_url})
                // Android.fetchHtml usa headers por defecto, podría fallar si validan Referer estricto
                var html = Android.fetchHtml(url); 
                
                // 2. Buscar redirect /e/
                // Python: re.search(r"location\.replace\(['\"](/e/[^'\"]+)['\"]\)", html)
                var redirectMatch = html.match(/location\.replace\(['"](\/e\/[^'"]+)['"]\)/);
                
                if (redirectMatch) {
                    var nextUrl = "https://cvid.lat" + redirectMatch[1];
                    html = Android.fetchHtml(nextUrl);
                }

                // 3. Extraer m3u8
                // Python: re.search(r'(https?://[^\s"\']+\.m3u8)', html)
                var m3u8Match = html.match(/(https?:\/\/[^\s"']+\.m3u8)/);
                
                if (m3u8Match) {
                    return m3u8Match[1];
                }
            } catch(e) {}
            return null;
        }
    });
})();
                    
