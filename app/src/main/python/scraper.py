import json
import re
import requests
import base64
import urllib.parse
from java import jclass

# --- LOGS ---
Log = jclass("android.util.Log")
TAG = "KRONOS_PY"

def log(msg):
    Log.d(TAG, str(msg))

def error(msg):
    Log.e(TAG, str(msg))

class SoloLatinoScraper:
    def __init__(self):
        self.session = requests.Session()
        self.session.headers.update({
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36',
            'Referer': 'https://sololatino.net/',
            'Accept-Language': 'es-ES,es;q=0.9,en;q=0.8'
        })
        self.base_url = "https://sololatino.net"

    def get_html(self, url):
        try:
            # verify=False es vital en Android
            r = self.session.get(url, timeout=15, verify=False)
            return r.text if r.status_code == 200 else None
        except Exception as e:
            error(f"Error Red: {e}")
            return None

    # --- NUEVA BÚSQUEDA POR HTML (LO QUE PEDISTE) ---
    def do_search(self, query):
        try:
            # 1. Búsqueda web clásica (HTML)
            search_url = f"{self.base_url}/?s={urllib.parse.quote(query)}"
            log(f"Descargando HTML de búsqueda: {search_url}")
            
            html = self.get_html(search_url)
            if not html: return []

            results = []
            
            # 2. Regex basado en tu snippet HTML:
            # <article ... data-id="..."> ... <a href="..."> ... <p>2025</p> ... <h3>Titulo</h3>
            
            # Buscamos cada bloque <article>
            articles = re.findall(r'<article(.*?)</article>', html, re.DOTALL)
            
            for article in articles:
                try:
                    # Extraer URL
                    url_match = re.search(r'href=["\']([^"\']+)["\']', article)
                    url = url_match.group(1) if url_match else ""
                    
                    # Extraer Título (del alt de la imagen o del h3)
                    title_match = re.search(r'alt=["\']([^"\']+)["\']', article)
                    if not title_match:
                        title_match = re.search(r'<h3>(.*?)</h3>', article)
                    title = title_match.group(1) if title_match else "Sin Titulo"
                    
                    # Extraer Año (Tu requerimiento específico: <div class="data"><p>2025</p>)
                    # Buscamos cualquier <p>4 digitos</p> dentro del article
                    year_match = re.search(r'<p>\s*(\d{4})\s*</p>', article)
                    year = year_match.group(1) if year_match else "0"
                    
                    # Extraer Imagen
                    img_match = re.search(r'src=["\']([^"\']+)["\']', article)
                    img = img_match.group(1) if img_match else ""

                    # Detectar tipo por URL
                    tipo = 'tv' if '/series/' in url or '/tvshows/' in url else 'movie'

                    if url:
                        results.append({
                            "title": title,
                            "url": url,
                            "img": img,
                            "year": year,
                            "type": tipo
                        })
                except: pass
            
            log(f"Encontrados {len(results)} resultados vía HTML")
            return results

        except Exception as e:
            error(f"Error Search HTML: {e}")
            return []

    # --- EXTRACCIÓN DE ENLACES (Igual que antes, funciona bien) ---
    def scrape_url(self, url):
        found_links = []
        html = self.get_html(url)
        if not html: return []
        
        # 1. Doble Salto (embed.php)
        iframe_match = re.search(r'src\s*=\s*["\']([^"\']*embed\.php\?id=\d+)[^"\']*["\']', html, re.IGNORECASE)
        if iframe_match:
            iframe_url = iframe_match.group(1)
            if iframe_url.startswith("//"): iframe_url = "https:" + iframe_url
            if "http" not in iframe_url: iframe_url = self.base_url + iframe_url
            found_links.extend(self._scrape_double_hop(iframe_url))

        # 2. VAST
        if "go_to_playerVast" in html:
            found_links.extend(self._scrape_vast(html))

        # 3. Embed69 JSON
        if "dataLink" in html:
            found_links.extend(self._scrape_embed69_json(html))
        
        # 4. Iframes
        found_links.extend(self._scrape_iframes(html))

        return found_links

    # --- HELPERS (Sin cambios, ya funcionaban) ---
    def _scrape_double_hop(self, url):
        links = []
        try:
            headers = self.session.headers.copy()
            headers['Referer'] = self.base_url
            r = self.session.get(url, headers=headers, timeout=10, verify=False)
            html = r.text
            matches = re.findall(r"onclick=\"go_to_player\('([^']+)'\)\"[^>]*>.*?<span>(.*?)</span>", html, re.DOTALL | re.IGNORECASE)
            for link, server_name in matches:
                clean_link = link.strip()
                server_clean = server_name.strip().title()
                if "embed.php" in clean_link and "link=" in clean_link:
                    try:
                        parsed = urllib.parse.urlparse(clean_link)
                        params = urllib.parse.parse_qs(parsed.query)
                        if 'link' in params:
                            b64 = params['link'][0]
                            b64 += '=' * (-len(b64) % 4)
                            decoded = base64.b64decode(b64).decode('utf-8')
                            links.append({'server': server_clean, 'url': decoded, 'quality': '720p', 'provider': 'SoloLatino'})
                    except: pass
                else:
                    links.append({'server': server_clean, 'url': clean_link, 'quality': '720p', 'provider': 'SoloLatino'})
        except: pass
        return links

    def _scrape_vast(self, html):
        links = []
        try:
            matches = re.findall(r"onclick=\"go_to_playerVast\('([^']+)'[^>]*data-lang=\"(\d+)\"[^>]*>.*?<span>(.*?)</span>", html, re.DOTALL)
            for url, lid, name in matches:
                links.append({'server': name.strip().title(), 'url': url, 'quality': '720p', 'provider': 'SoloLatino (Vast)'})
        except: pass
        return links

    def _scrape_embed69_json(self, html):
        links = []
        try:
            match = re.search(r'let\s+dataLink\s*=\s*(\[.*?\]);', html, re.DOTALL)
            if not match: return []
            data = json.loads(match.group(1))
            for item in data:
                lang = item.get('video_language', 'UNK')
                for embed in item.get('sortedEmbeds', []):
                    if embed.get('servername') == 'download': continue
                    if embed.get('link'):
                        decoded = self._decode_jwt(embed.get('link'))
                        if decoded:
                            links.append({'server': embed['servername'].title(), 'url': decoded, 'quality': '1080p', 'lang': lang, 'provider': 'SoloLatino'})
        except: pass
        return links

    def _scrape_iframes(self, html):
        links = []
        frames = re.findall(r"src=['\"](https://embed69\.org/f/[^'\"]+)['\"]", html)
        for f_url in frames:
            try:
                r = self.session.get(f_url, timeout=5, verify=False)
                if r.status_code == 200:
                    links.extend(self._scrape_embed69_json(r.text))
            except: pass
        return links

    def _decode_jwt(self, token):
        try:
            parts = token.split('.')
            payload = parts[1] + '=' * (-len(parts[1]) % 4)
            return json.loads(base64.urlsafe_b64decode(payload).decode('utf-8')).get('link')
        except: return None

# --- INSTANCIA ---
scraper = SoloLatinoScraper()

def search(query):
    return json.dumps(scraper.do_search(query))

def get_links(url):
    results = scraper.scrape_url(url)
    return json.dumps(results)
                                  
