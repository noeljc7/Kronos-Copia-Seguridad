import json
import re
import requests
import base64
import urllib.parse
from java import jclass # Interfaz con Android

# --- CONFIGURACIÓN DE LOGS ---
Log = jclass("android.util.Log")
TAG = "KRONOS_PY"

def log(msg):
    Log.d(TAG, str(msg))

def error(msg):
    Log.e(TAG, str(msg))

class SoloLatinoScraper:
    def __init__(self):
        # Usamos requests.Session como en tu archivo funcional de Kodi
        self.session = requests.Session()
        self.session.headers.update({
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36',
            'Referer': 'https://sololatino.net/',
            'Accept-Language': 'es-ES,es;q=0.9,en;q=0.8'
        })
        self.base_url = "https://sololatino.net"

    def get_html(self, url):
        try:
            r = self.session.get(url, timeout=10)
            return r.text if r.status_code == 200 else None
        except Exception as e:
            error(f"Error descargando HTML: {e}")
            return None

    # --- LÓGICA DE BÚSQUEDA (Adaptada para Android) ---
    def do_search(self, query):
        try:
            # 1. Obtener Nonce de la Home
            home_html = self.get_html(self.base_url)
            if not home_html: return []

            match = re.search(r'["\']nonce["\']\s*:\s*["\']([^"\']+)["\']', home_html)
            if not match:
                error("No se encontró el Nonce de búsqueda")
                return []
            nonce = match.group(1)

            # 2. Consultar API
            api_url = f"{self.base_url}/wp-json/dooplay/search/"
            params = {'keyword': query, 'nonce': nonce}
            r = self.session.get(api_url, params=params)
            data = r.json()

            results = []
            items = data.values() if isinstance(data, dict) else data
            
            for item in items:
                if isinstance(item, dict) and 'url' in item and 'title' in item:
                    tipo = 'tv' if '/series/' in item['url'] or '/tvshows/' in item['url'] else 'movie'
                    year = item.get('extra', {}).get('date', '0') if 'extra' in item else '0'
                    results.append({
                        "title": item['title'],
                        "url": item['url'],
                        "img": item.get('img', ''),
                        "year": str(year),
                        "type": tipo
                    })
            return results
        except Exception as e:
            error(f"Error en Search: {e}")
            return []

    # --- LÓGICA DE EXTRACCIÓN (Importada de tu archivo Kodi) ---
    def scrape_url(self, url):
        found_links = []
        html = self.get_html(url)
        if not html: return []

        # 1. Buscar Iframes tipo "embed.php" (Doble Salto)
        iframe_match = re.search(r'src\s*=\s*["\']([^"\']*embed\.php\?id=\d+)[^"\']*["\']', html, re.IGNORECASE)
        if iframe_match:
            iframe_url = iframe_match.group(1)
            if iframe_url.startswith("//"): iframe_url = "https:" + iframe_url
            if "http" not in iframe_url: iframe_url = self.base_url + iframe_url
            log(f"Procesando Doble Salto: {iframe_url}")
            found_links.extend(self._scrape_double_hop(iframe_url))

        # 2. VAST (Reproductores nativos del tema)
        if "go_to_playerVast" in html:
            found_links.extend(self._scrape_vast(html))

        # 3. Embed69 JSON (El más importante)
        if "dataLink" in html:
            found_links.extend(self._scrape_embed69_json(html))
        
        # 4. Iframes normales (XuPalace/Embed69 directos)
        found_links.extend(self._scrape_iframes(html))

        return found_links

    def _scrape_double_hop(self, url):
        links = []
        try:
            headers = self.session.headers.copy()
            headers['Referer'] = self.base_url
            r = self.session.get(url, headers=headers, timeout=10)
            if r.status_code != 200: return []
            html = r.text

            matches = re.findall(r"onclick=\"go_to_player\('([^']+)'\)\"[^>]*>.*?<span>(.*?)</span>", html, re.DOTALL | re.IGNORECASE)
            for link, server_name in matches:
                clean_link = link.strip()
                server_clean = server_name.strip().title()
                
                # Caso Base64
                if "embed.php" in clean_link and "link=" in clean_link:
                    try:
                        parsed = urllib.parse.urlparse(clean_link)
                        params = urllib.parse.parse_qs(parsed.query)
                        if 'link' in params:
                            b64_link = params['link'][0]
                            # Relleno padding base64
                            b64_link += '=' * (-len(b64_link) % 4)
                            decoded_link = base64.b64decode(b64_link).decode('utf-8')
                            
                            links.append({
                                'server': server_clean,
                                'url': decoded_link,
                                'quality': '720p',
                                'provider': 'SoloLatino'
                            })
                    except: pass
                # Caso Directo
                else:
                    links.append({
                        'server': server_clean,
                        'url': clean_link,
                        'quality': '720p',
                        'provider': 'SoloLatino'
                    })
        except Exception as e: error(f"Error DoubleHop: {e}")
        return links

    def _scrape_vast(self, html):
        links = []
        try:
            matches = re.findall(r"onclick=\"go_to_playerVast\('([^']+)'[^>]*data-lang=\"(\d+)\"[^>]*>.*?<span>(.*?)</span>", html, re.DOTALL)
            for url, lid, name in matches:
                links.append({
                    'server': name.strip().title(),
                    'url': url,
                    'quality': '720p',
                    'provider': 'SoloLatino (Vast)'
                })
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
                            links.append({
                                'server': embed['servername'].title(),
                                'url': decoded,
                                'quality': '1080p',
                                'lang': lang,
                                'provider': 'SoloLatino (JSON)'
                            })
        except Exception as e: error(f"Error JSON: {e}")
        return links

    def _scrape_iframes(self, html):
        links = []
        # Buscar iframes anidados y extraer recursivamente
        frames = re.findall(r"src=['\"](https://embed69\.org/f/[^'\"]+)['\"]", html)
        for f_url in frames:
            try:
                r = self.session.get(f_url, timeout=5)
                if r.status_code == 200:
                    links.extend(self._scrape_embed69_json(r.text))
            except: pass
        return links

    def _decode_jwt(self, token):
        try:
            parts = token.split('.')
            payload = parts[1] + '=' * (-len(parts[1]) % 4)
            data = json.loads(base64.urlsafe_b64decode(payload).decode('utf-8'))
            return data.get('link')
        except: return None

# --- INSTANCIA GLOBAL PARA ANDROID ---
scraper_instance = SoloLatinoScraper()

# --- FUNCIONES PUENTE EXPORTADAS ---
# Estas son las que llama tu código Java/Kotlin

def search(query):
    log(f"Buscando: {query}")
    results = scraper_instance.do_search(query)
    return json.dumps(results)

def get_links(url):
    log(f"Extrayendo enlaces de: {url}")
    links = scraper_instance.scrape_url(url)
    log(f"Enlaces encontrados: {len(links)}")
    return json.dumps(links)
                               
