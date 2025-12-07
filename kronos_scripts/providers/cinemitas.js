(function() {
    KronosEngine.register({
        name: "Cinemitas",
        baseUrl: "https://cinemitas.org",
        lang: "Latino",
        type: "mixed", // Pelis y Series

        // 1. BUSCAR
        search: function(query) {
            var url = this.baseUrl + "/?s=" + encodeURIComponent(query);
            var html = Android.fetchHtml(url);
            var doc = Jsoup.parse(html);
            var items = [];
            
            // Selector basado en tu HTML (article.result-item)
            var elements = doc.select("article.item"); 
            
            for(var i=0; i<elements.size(); i++) {
                var el = elements.get(i);
                var link = el.select("a").attr("href");
                var img = el.select("img").attr("src");
                var title = el.select(".title").text();
                var year = el.select(".year").text();
                
                // Detectar si es serie o peli por la URL o clase
                var isMovie = !link.includes("/tvshows/");

                items.push({
                    title: title,
                    year: year,
                    url: link,
                    poster: img,
                    isMovie: isMovie
                });
            }
            return JSON.stringify(items);
        },

        // 2. DETALLES Y EPISODIOS
        getDetails: function(url) {
            var html = Android.fetchHtml(url);
            var doc = Jsoup.parse(html);
            
            var details = {
                title: doc.select("h1.s-title").text(),
                desc: doc.select("div.wp-content p").text(),
                background: doc.select("meta[property=og:image]").attr("content"),
                episodes: []
            };

            // Extraer episodios (si es serie)
            var eps = doc.select("ul.episodios li");
            for(var i=0; i<eps.size(); i++) {
                var ep = eps.get(i);
                var epLink = ep.select("a").attr("href");
                var epImg = ep.select("img").attr("src");
                var epTitle = ep.select(".episodiotitle a").text();
                var epNum = ep.select(".numerando").text().split("-")[1] || (i+1); // "1 - 1" -> 1
                
                details.episodes.push({
                    name: epTitle,
                    url: epLink,
                    season: 1, // Cinemitas suele separar temporadas por URL, asumo 1 por ahora
                    episode: parseInt(epNum),
                    poster: epImg
                });
            }
            
            // Si es película, guardamos el ID para pedir enlaces luego
            // El ID suele estar en un input hidden o en el shortlink
            // <link rel='shortlink' href='https://cinemitas.org/?p=12528' />
            var shortlink = doc.select("link[rel=shortlink]").attr("href");
            var id = shortlink.split("?p=")[1];
            details.internalId = id; 

            return JSON.stringify(details);
        },

        // 3. OBTENER ENLACES (La magia del AJAX)
        getLinks: function(url) {
            var html = Android.fetchHtml(url);
            var doc = Jsoup.parse(html);
            var links = [];

            // 1. Obtener el ID del post (vital para el AJAX)
            var shortlink = doc.select("link[rel=shortlink]").attr("href");
            var postId = shortlink.split("?p=")[1];

            // 2. Buscar los botones de servidores (tabs)
            // En Dooplay (el tema de la web), los IDs de opción están en #player-options-ul li
            var options = doc.select("#player-options-ul li");

            for(var i=0; i<options.size(); i++) {
                var opt = options.get(i);
                var type = opt.attr("data-type"); // "iframe", "mp4"
                var post = opt.attr("data-post"); // El ID del post
                var nume = opt.attr("data-nume"); // El número de opción (1, 2, 3...)
                var title = opt.select(".title").text(); // "Latino - Voe"

                // 3. HACER LA LLAMADA AJAX A LA API OCULTA
                // URL: https://cinemitas.org/wp-admin/admin-ajax.php
                // POST Data: action=doo_player_ajax&post=123&nume=1&type=iframe
                
                var ajaxUrl = this.baseUrl + "/wp-admin/admin-ajax.php";
                var postData = "action=doo_player_ajax&post=" + post + "&nume=" + nume + "&type=" + type;
                
                // Usamos una función nueva del puente para hacer POST (necesitamos añadirla)
                var jsonResponse = Android.post(ajaxUrl, postData); 
                var json = JSON.parse(jsonResponse);
                
                if (json.embed_url) {
                    var finalLink = json.embed_url;
                    // Limpieza: A veces viene con comillas escapadas o redirecciones
                    finalLink = finalLink.replace(/\\/g, ""); 
                    
                    links.push({
                        server: title,
                        url: finalLink,
                        lang: "Latino", // Casi todo en Cinemitas es Latino
                        quality: "HD"
                    });
                }
            }
            return JSON.stringify(links);
        }
    });
})();

