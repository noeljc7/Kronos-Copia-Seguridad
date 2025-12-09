(function() {
    // 1. Definimos la Clase con la lógica
    class CinemitasEngine {
        constructor() {
            this.baseUrl = "https://cinemitas.org";
            this.headers = {
                "User-Agent": "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36",
                "Referer": "https://cinemitas.org/",
                "X-Requested-With": "XMLHttpRequest"
            };
            this.nonce = null;
        }

        async _fetchText(url, referer) {
            // Intenta usar el puente nativo si existe (para evitar CORS estricto)
            if (typeof bridge !== 'undefined' && bridge.fetchHtml) {
               // return bridge.fetchHtml(url); 
            }
            
            const headers = { ...this.headers };
            if (referer) headers['Referer'] = referer;
            try {
                const response = await fetch(url, { headers });
                return await response.text();
            } catch (e) { console.error("Fetch Error: " + e); return ""; }
        }

        async getNonce() {
            if (this.nonce) return this.nonce;
            try {
                const html = await this._fetchText(this.baseUrl);
                const scriptRegex = /src=["']data:text\/javascript;base64,([^"']+)["']/g;
                let match;
                while ((match = scriptRegex.exec(html)) !== null) {
                    try {
                        const decoded = atob(match[1]);
                        const nonceMatch = decoded.match(/["']nonce["']\s*:\s*["']([a-zA-Z0-9]+)["']/);
                        if (nonceMatch) {
                            this.nonce = nonceMatch[1];
                            return this.nonce;
                        }
                    } catch (e) { continue; }
                }
            } catch (e) { console.error(e); }
            return null;
        }

        async search(query) {
            const nonce = await this.getNonce();
            if (!nonce) return [];

            const url = `${this.baseUrl}/wp-json/dooplay/search/?keyword=${encodeURIComponent(query)}&nonce=${nonce}`;
            try {
                const response = await fetch(url, { headers: this.headers });
                const data = await response.json();
                const items = Array.isArray(data) ? data : Object.values(data);
                
                return items.map(item => ({
                    title: item.title,
                    url: item.url,
                    img: item.img,
                    id: (item.id || 0).toString(),
                    type: (item.url && item.url.includes("/tvshows/")) ? "tv" : "movie",
                    year: item.extra ? item.extra.date : ""
                }));
            } catch (e) { return []; }
        }

        async getEpisodes(seriesUrl) {
            try {
                const html = await this._fetchText(seriesUrl);
                const linkRegex = /href=["']([^"']+\/episodes\/[^"']+)["']/g;
                const episodes = [];
                let match;
                while ((match = linkRegex.exec(html)) !== null) {
                    const link = match[1];
                    const nameMatch = link.match(/(\d+)x(\d+)/);
                    const title = nameMatch ? `T${nameMatch[1]} - E${nameMatch[2]}` : "Episodio";
                    episodes.push({ title: title, url: link });
                }
                return episodes;
            } catch (e) { return []; }
        }

        async resolveVideo(urlOrPostId, type) {
            try {
                let postId = urlOrPostId;
                // Si llega una URL completa, extraemos el ID primero
                if (urlOrPostId.toString().indexOf("http") === 0) {
                     const html = await this._fetchText(urlOrPostId);
                     const match = html.match(/[\?&]p=(\d+)/);
                     if (match) postId = match[1];
                     else return null;
                }

                const formData = new URLSearchParams();
                formData.append('action', 'doo_player_ajax');
                formData.append('post', postId);
                formData.append('nume', '1');
                formData.append('type', type);

                const r1 = await fetch(`${this.baseUrl}/wp-admin/admin-ajax.php`, {
                    method: 'POST',
                    headers: { ...this.headers, 'Content-Type': 'application/x-www-form-urlencoded' },
                    body: formData
                });
                const d1 = await r1.json();
                if (!d1.embed_url) return null;

                // Lógica de redirección (Wait Room /f/ -> /e/)
                let html = await this._fetchText(d1.embed_url, this.baseUrl);
                const redMatch = html.match(/location\.replace\(['"](\/e\/[^'"]+)['"]\)/);
                if (redMatch) {
                    html = await this._fetchText("https://cvid.lat" + redMatch[1], d1.embed_url);
                }

                const m3u8 = html.match(/(https?:\/\/[^\s"']+\.m3u8)/);
                if (m3u8) return m3u8[1];

            } catch (e) { console.error(e); }
            return null;
        }
    }

    // 2. REGISTRO EN EL MOTOR KRONOS
    var KronosEngine = window.KronosEngine || { providers: {} };
    KronosEngine.providers['Cinemitas'] = new CinemitasEngine();
    window.KronosEngine = KronosEngine;

})();
