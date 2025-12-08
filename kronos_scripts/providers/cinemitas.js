(function() {
    KronosEngine.register({
        name: "Cinemitas",
        baseUrl: "https://cinemitas.org",
        lang: "Latino",
        type: "mixed",

        // Variables para caché
        _nonce: null,

        // --- 1. FUNCIÓN PARA ROBAR LA LLAVE (Igual que en Python) ---
        _getNonce: function() {
            if (this._nonce) return this._nonce; // Si ya la tenemos, la usamos

            try {
                // Descargamos el HTML del Home
                var html = Android.fetchHtml(this.baseUrl);
                
                // Buscamos scripts en Base64: src='data:text/javascript;base64,...'
                var regex = /src=["']data:text\/javascript;base64,([^"']+)["']/;
                var match = html.match(regex);

                if (match && match[1]) {
                    // Decodificamos Base64 usando utilidades de Android (porque JS puro en Rhino no tiene atob)
                    var b64 = match[1];
                    var decoded = new java.lang.String(android.util.Base64.decode(b64, 0));
                    
                    // Buscamos "nonce":"xxxx" dentro del texto decodificado
                    var nonceMatch = decoded.match(/["']nonce["']\s*:\s*["']([a-zA-Z0-9]+)["']/);
                    if (nonceMatch) {
                        this._nonce = nonceMatch[1];
                        Android.log("🔑 Nonce encontrado: " + this._nonce);
                        return this._nonce;
                    }
                }
            } catch (e) {
                Android.log("❌ Error obteniendo Nonce: " + e);
            }
            return null;
        },

        // --- 2. BÚSQUEDA VÍA API (La Puerta Trasera) ---
        search: function(query) {
            try {
                var items = [];
                var nonce = this._getNonce();

                if (nonce) {
                    // MODO API (Igual que Python)
                    var apiUrl = this.baseUrl + "/wp-json/dooplay/search/?keyword=" + encodeURIComponent(query) + "&nonce=" + nonce;
                    var jsonStr = Android.fetchHtml(apiUrl); // fetchHtml sirve para GET, aunque devuelva JSON
                    
                    // A veces Jsoup envuelve el JSON en <body>, limpiamos si es necesario
                    // Pero normalmente fetchHtml devuelve el texto crudo si el servidor responde JSON
                    
                    try {
                        // El JSON puede venir como Objeto {"1":{...}, "2":{...}} o Array [...]
                        var data = JSON.parse(jsonStr);
                        
                        // Convertimos a array si es objeto
                        var list = [];
                        if (Array.isArray(data)) {
                            list = data;
                        } else {
                            for (var key in data) {
                                list.push(data[key]);
                            }
                        }

                        // Procesamos resultados
                        for (var i = 0; i < list.length; i++) {
                            var item = list[i];
                            var type = "movie";
                            if (item.url && item.url.indexOf("/tvshows/") !== -1) {
                                type = "tv";
                            }

                            items.push({
                                title: item.title,
                                url: item.url,
                                poster: item.img,
                                isMovie: (type === "movie"),
                                year: (item.extra && item.extra.date) ? item.extra.date : ""
                            });
                        }
                        
                    } catch(e) {
                        Android.log("Error parseando JSON de búsqueda: " + e);
                    }
                } 

                // SI FALLA LA API O NO HAY NONCE, INTENTAMOS WEB SCRAPING (Respaldo)
                if (items.length === 0) {
                    Android.log("⚠️ Falló API, intentando Web Scraping clásico...");
                    var webUrl = this.baseUrl + "/?s=" + encodeURIComponent(query);
                    var html = Android.fetchHtml(webUrl);
                    var doc = Jsoup.parse(html);
                    var elements = doc.select("article.item"); 
                    
                    for(var i=0; i<elements.size(); i++) {
                        var el = elements.get(i);
                        var link = el.select("a").attr("href");
                        var img = el.select("img").attr("src");
                        var title = el.select(".title").text();
                        
                        if (link && title) {
                            items.push({
                                title: title,
                                url: link,
                                poster: img,
                                isMovie: !link.includes("/tvshows/")
                            });
                        }
                    }
                }

                return JSON.stringify(items);
            } catch(e) {
                Android.log("Error Fatal en Search: " + e);
                return "[]";
            }
        },

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

                // Lógica mejorada para episodios (Python buscaba hrefs específicos)
                var eps = doc.select("ul.episodios li");
                for(var i=0; i<eps.size(); i++) {
                    var ep = eps.get(i);
                    var epLink = ep.select("a").attr("href");
                    var epImg = ep.select("img").attr("src");
                    var epTitle = ep.select(".episodiotitle a").text();
                    
                    // Intentar sacar numero de "1 - 1"
                    var epNumText = ep.select(".numerando").text();
                    var season = 1;
                    var episode = i + 1;
                    
                    if (epNumText.includes("-")) {
                        var parts = epNumText.split("-");
                        season = parseInt(parts[0].trim());
                        episode = parseInt(parts[1].trim());
                    }

                    details.episodes.push({
                        name: epTitle,
                        url: epLink,
                        season: season,
                        episode: episode,
                        poster: epImg
                    });
                }
                
                // ID para peliculas
                if (details.episodes.length === 0) {
                     var shortlink = doc.select("link[rel=shortlink]").attr("href");
                     if(shortlink) details.internalId = shortlink.split("?p=")[1];
                }

                return JSON.stringify(details);
            } catch(e) {
                return "{}";
            }
        },

        getLinks: function(url) {
            try {
                var html = Android.fetchHtml(url);
                var doc = Jsoup.parse(html);
                var links = [];
                var postId = "";

                // 1. Obtener ID (Especialmente crítico para episodios)
                var shortlink = doc.select("link[rel=shortlink]").attr("href");
                if (shortlink) {
                    postId = shortlink.split("?p=")[1];
                }

                if (!postId) return "[]";

                // 2. Buscar opciones
                var options = doc.select("#player-options-ul li");

                for(var i=0; i<options.size(); i++) {
                    var opt = options.get(i);
                    var type = opt.attr("data-type");
                    var post = opt.attr("data-post");
                    var nume = opt.attr("data-nume");
                    var title = opt.select(".title").text();

                    // AJAX POST
                    var ajaxUrl = this.baseUrl + "/wp-admin/admin-ajax.php";
                    var postData = "action=doo_player_ajax&post=" + post + "&nume=" + nume + "&type=" + type;
                    var jsonResponse = Android.post(ajaxUrl, postData); 
                    
                    if (jsonResponse) {
                        try {
                            var json = JSON.parse(jsonResponse);
                            if (json.embed_url) {
                                var embedUrl = json.embed_url.replace(/\\/g, "");
                                
                                // DETECCIÓN DE REDIRECTS (Resolución final)
                                // Si es cvid.lat o similar, intentamos resolver
                                if (embedUrl.includes("cvid.lat") || embedUrl.includes("player")) {
                                     // Aquí podrías agregar lógica extra si quieres resolverlo en JS
                                     // Por ahora lo mandamos al Sniffer que es infalible con redirects
                                     links.push({
                                        server: title,
                                        url: embedUrl,
                                        lang: "Latino",
                                        quality: "HD",
                                        isDirect: false // Sniffer se encarga de los redirects complejos
                                    });
                                } else {
                                    links.push({
                                        server: title,
                                        url: embedUrl,
                                        lang: "Latino",
                                        quality: "HD",
                                        isDirect: false
                                    });
                                }
                            }
                        } catch(e) {}
                    }
                }
                return JSON.stringify(links);
            } catch(e) {
                return "[]";
            }
        }
    });
})();
                                    
