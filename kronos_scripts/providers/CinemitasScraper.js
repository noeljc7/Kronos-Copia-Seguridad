class CinemitasScraper {
    constructor() {
        this.baseUrl = "https://cinemitas.org";
        this.headers = {
            "User-Agent": "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36",
            "Referer": "https://cinemitas.org/",
            "X-Requested-With": "XMLHttpRequest"
        };
        this.nonce = null;
    }

    /**
     * 1. Obtiene la llave de seguridad (Nonce) descodificando los scripts Base64
     */
    async getNonce() {
        if (this.nonce) return this.nonce;

        try {
            console.log("Obteniendo Nonce...");
            const html = await this._fetchHtml(this.baseUrl);
            
            // Regex para encontrar los scripts en base64: src="data:text/javascript;base64,..."
            const scriptRegex = /src=["']data:text\/javascript;base64,([^"']+)["']/g;
            let match;

            while ((match = scriptRegex.exec(html)) !== null) {
                try {
                    // Decodificar Base64 (atob es nativo en JS)
                    const decoded = atob(match[1]);
                    
                    // Buscar patrón de nonce
                    const nonceMatch = decoded.match(/["']nonce["']\s*:\s*["']([a-zA-Z0-9]+)["']/);
                    if (nonceMatch) {
                        this.nonce = nonceMatch[1];
                        console.log("Nonce encontrado:", this.nonce);
                        return this.nonce;
                    }
                } catch (e) {
                    continue; // Si falla uno, probamos el siguiente
                }
            }
        } catch (error) {
            console.error("Error obteniendo nonce:", error);
        }
        return null;
    }

    /**
     * 2. Busca Películas o Series
     */
    async search(query) {
        const nonce = await this.getNonce();
        if (!nonce) return [];

        const url = `${this.baseUrl}/wp-json/dooplay/search/?keyword=${encodeURIComponent(query)}&nonce=${nonce}`;
        
        try {
            const response = await fetch(url, { headers: this.headers });
            const data = await response.json();
            
            // Normalizar respuesta (Dooplay devuelve objeto o array)
            const items = Array.isArray(data) ? data : Object.values(data);
            const results = [];

            for (const item of items) {
                // Detectar tipo
                let type = "movie";
                if (item.url && item.url.includes("/tvshows/")) type = "tv";

                results.push({
                    title: item.title,
                    id: item.id || 0, // Si es serie, a veces necesitamos entrar a la URL para sacar el ID real
                    img: item.img,
                    url: item.url,
                    type: type,
                    year: item.extra ? item.extra.date : ""
                });
            }
            return results;
        } catch (error) {
            console.error("Error en búsqueda:", error);
            return [];
        }
    }

    /**
     * 3. Extrae episodios de una serie (Parseo HTML)
     */
    async getEpisodes(seriesUrl) {
        try {
            const html = await this._fetchHtml(seriesUrl);
            // Regex para enlaces de episodios: href=".../episodes/..."
            const linkRegex = /href=["']([^"']+\/episodes\/[^"']+)["']/g;
            const episodes = [];
            let match;

            while ((match = linkRegex.exec(html)) !== null) {
                const link = match[1];
                // Intentar extraer temporada y capitulo del slug
                const nameMatch = link.match(/(\d+)x(\d+)/);
                const title = nameMatch ? `T${nameMatch[1]} - E${nameMatch[2]}` : "Episodio";

                episodes.push({
                    title: title,
                    url: link
                });
            }
            return episodes;
        } catch (error) {
            console.error("Error obteniendo episodios:", error);
            return [];
        }
    }

    /**
     * 4. Obtiene el Post ID real de una URL (necesario para series)
     */
    async getPostIdFromUrl(url) {
        try {
            const html = await this._fetchHtml(url);
            // Busca ?p=12345
            const match = html.match(/[\?&]p=(\d+)/);
            return match ? match[1] : null;
        } catch (e) {
            return null;
        }
    }

    /**
     * 5. Obtiene la URL del iframe (API doo_player_ajax)
     */
    async getServerUrl(postId, type) {
        try {
            const formData = new URLSearchParams();
            formData.append('action', 'doo_player_ajax');
            formData.append('post', postId);
            formData.append('nume', '1');
            formData.append('type', type);

            const response = await fetch(`${this.baseUrl}/wp-admin/admin-ajax.php`, {
                method: 'POST',
                headers: {
                    ...this.headers,
                    'Content-Type': 'application/x-www-form-urlencoded'
                },
                body: formData
            });

            const data = await response.json();
            return data.embed_url || null;
        } catch (error) {
            console.error("Error obteniendo servidor:", error);
            return null;
        }
    }

    /**
     * 6. Resuelve el video final (La lógica de persecución /f/ -> /e/)
     */
    async resolveVideo(url) {
        try {
            // Paso 1: Ir a la sala de espera /f/
            let currentHtml = await this._fetchHtml(url, "https://cvid.lat/");
            
            // Paso 2: Buscar redirección JS: location.replace('/e/...')
            const redirectMatch = currentHtml.match(/location\.replace\(['"](\/e\/[^'"]+)['"]\)/);
            
            if (redirectMatch) {
                const nextUrl = `https://cvid.lat${redirectMatch[1]}`;
                console.log("Redirección detectada a:", nextUrl);
                // Paso 3: Ir a la sala final /e/
                currentHtml = await this._fetchHtml(nextUrl, url);
            }

            // Paso 4: Buscar .m3u8
            const m3u8Match = currentHtml.match(/(https?:\/\/[^\s"']+\.m3u8)/);
            
            if (m3u8Match) {
                return {
                    url: m3u8Match[1],
                    headers: {
                        "User-Agent": this.headers["User-Agent"],
                        "Referer": "https://cvid.lat/"
                    }
                };
            }
        } catch (error) {
            console.error("Error resolviendo video:", error);
        }
        return null;
    }

    // Helper privado para hacer fetch texto
    async _fetchHtml(url, referer = null) {
        const headers = { ...this.headers };
        if (referer) headers['Referer'] = referer;
        
        const response = await fetch(url, { headers });
        return await response.text();
    }
}

// Ejemplo de uso (esto lo llamarías desde tu Kotlin):
// (async () => {
//     const api = new CinemitasScraper();
//     const results = await api.search("Chainsaw");
//     console.log(results);
// })();
