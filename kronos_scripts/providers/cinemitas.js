(function() {
    KronosEngine.register({
        name: "Cinemitas",
        baseUrl: "https://cinemitas.org",
        lang: "Latino",
        type: "mixed",

        search: function(query) {
            try {
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
                    
                    if (link && title) {
                        items.push({
                            title: title,
                            url: link,
                            poster: img,
                            isMovie: !link.includes("/tvshows/")
                        });
                    }
                }
                return JSON.stringify(items);
            } catch(e) {
                Android.log("Error en Search: " + e);
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

                var eps = doc.select("ul.episodios li");
                for(var i=0; i<eps.size(); i++) {
                    var ep = eps.get(i);
                    var epLink = ep.select("a").attr("href");
                    var epImg = ep.select("img").attr("src");
                    var epTitle = ep.select(".episodiotitle a").text();
                    var epNum = ep.select(".numerando").text().split("-")[1] || (i+1);
                    
                    details.episodes.push({
                        name: epTitle,
                        url: epLink,
                        season: 1, 
                        episode: parseInt(epNum),
                        poster: epImg
                    });
                }
                return JSON.stringify(details);
            } catch(e) {
                Android.log("Error en Details: " + e);
                return "{}";
            }
        },

        getLinks: function(url) {
            try {
                var html = Android.fetchHtml(url);
                var doc = Jsoup.parse(html);
                var links = [];

                var options = doc.select("#player-options-ul li");

                for(var i=0; i<options.size(); i++) {
                    var opt = options.get(i);
                    var type = opt.attr("data-type");
                    var post = opt.attr("data-post");
                    var nume = opt.attr("data-nume");
                    var title = opt.select(".title").text();

                    var ajaxUrl = this.baseUrl + "/wp-admin/admin-ajax.php";
                    var postData = "action=doo_player_ajax&post=" + post + "&nume=" + nume + "&type=" + type;
                    
                    // Usamos el POST del puente
                    var jsonResponse = Android.post(ajaxUrl, postData); 
                    
                    if (jsonResponse) {
                        var json = JSON.parse(jsonResponse);
                        if (json.embed_url) {
                            var finalLink = json.embed_url.replace(/\\/g, "");
                            links.push({
                                server: title,
                                url: finalLink,
                                lang: "Latino",
                                quality: "HD",
                                isDirect: false
                            });
                        }
                    }
                }
                return JSON.stringify(links);
            } catch(e) {
                Android.log("Error en GetLinks: " + e);
                return "[]";
            }
        }
    });
})();
