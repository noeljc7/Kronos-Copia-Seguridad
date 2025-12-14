import json
import re
import requests
import base64
import urllib.parse
from java import jclass

# --- CONFIGURACIÓN DE LOGS (PUENTE ANDROID) ---
Log = jclass("android.util.Log")
TAG = "KRONOS_PY"

def log(msg):
    Log.d(TAG, str(msg))

def error(msg):
    Log.e(TAG, str(msg))

class SoloLatinoScraper:
    def __init__(self):
        # Usamos requests.Session para mantener cookies y headers
        self.session = requests.Session()
        self.session.headers.update({
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36',
            'Referer': 'https://sololatino.net/',
            'Accept-Language': 'es-ES,es;q=0.9,en;q=0.8'
        })
        self.base_url = "https://sololatino.net"

    def get_html(self, url):
        try:
            # IMPORTANTE: verify=False evita errores de SSL comunes en Android
            r = self.session.get(url, timeout=15, verify=False)
            if r.status_code == 200:
                return r.text
            else:
                error(f"Error HTTP {r.status_code} en {url}")
                return None
        except Exception as e:
            error(f"Error de Conexión: {str(e)}")
            return None

    # --- LÓGICA DE EXTRACCIÓN (IDÉNTICA A TU PRUEBA DE TERMINAL) ---
    def scrape_url(self, url):
        found_links = []
        html = self.get_html(url)
        if not html: return []
        
        log(f"Analizando HTML ({len(html)} chars)...")

        # 1. Buscar Iframe 'embed.php' (Doble Salto)
        iframe_match = re.search(r'src\s*=\s*["\']([^"\']*embed\.php\?id=\d+)[^"\']*["\']', html, re.IGNORECASE)
        
        if iframe_match:
            iframe_url = iframe_match.group(1)
            if iframe_url.startswith("//"): iframe_url = "https:" + iframe_url
            if "sololatino" not in iframe_url and "http" not in iframe_url:
                 iframe_url = self.base_url + iframe_url
            
            log(f"Iframe interno detectado: {iframe_url}")
            found_links.extend(self._scrape_double_hop(iframe_url))

        # 2. VAST (Reproductores nativos)
        if "go_to_playerVast" in html:
            found_links.extend(self._scrape_vast(html))

        # 3. Embed69 JSON (Variable dataLink)
        if "dataLink" in html:
            found_links.extend(self._scrape_embed69_json(html))
        
        # 4. Iframes normales (Fallback - DE AQUÍ SALIERON TUS 12 ENLACES)
        found_links.extend(self._scrape_iframes(html))

        return found_links

    # --- FUNCIONES AUXILIARES ---

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
                            b64_link = params['link'][0]
                            b64_link += '=' * (-len(b64_link) % 4)
                            decoded_link = base64.b64decode(b64_link).decode('utf-8')
                            
                            links.append({'server': server_clean, 'url': decoded_link, 'quality': '720p', 'provider': 'SoloLatino'})
                    except: pass
                else:
                    links.append({'server': server_clean, 'url': clean_link, 'quality': '720p', 'provider': 'SoloLatino'})
        except Exception as e: error(f"Error DoubleHop: {e}")
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
                            links.append({
                                'server': embed['servername'].title(),
                                'url': decoded,
                                'quality': '1080p',
                                'lang': lang,
                                'provider': 'SoloLatino'
                            })
        except Exception as e: error(f"Error JSON: {e}")
        return links

    def _scrape_iframes(self, html):
        links = []
        # Este es el regex que funcionó en tu terminal
        frames = re.findall(r"src=['\"](https://embed69\.org/f/[^'\"]+)['\"]", html)
        for f_url in frames:
            try:
                # verify=False es CRÍTICO aquí también
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

    # --- BÚSQUEDA (Necesaria para que la app no falle al buscar) ---
    def do_search(self, query):
        try:
            html = self.get_html(self.base_url)
            if not html: return []
            nonce_match = re.search(r'["\']nonce["\']\s*:\s*["\']([^"\']+)["\']', html)
            nonce = nonce_match.group(1) if nonce_match else ""
            api_url = f"{self.base_url}/wp-json/dooplay/search/"
            params = {'keyword': query, 'nonce': nonce}
            r = self.session.get(api_url, params=params, verify=False)
            data = r.json()
            results = []
            items = data.values() if isinstance(data, dict) else data
            for item in items:
                if isinstance(item, dict) and 'url' in item:
                    tipo = 'tv' if '/series/' in item['url'] else 'movie'
                    results.append({
                        "title": item.get('title'),
                        "url": item['url'],
                        "img": item.get('img', ''),
                        "year": item.get('extra', {}).get('date', '0'),
                        "type": tipo
                    })
            return results
        except: return []

# --- INSTANCIA GLOBAL ---
scraper = SoloLatinoScraper()

# --- FUNCIONES EXPORTADAS A JAVA ---
def search(query):
    return json.dumps(scraper.do_search(query))

def get_links(url):
    log(f"--- INICIANDO EXTRACCIÓN: {url} ---")
    results = scraper.scrape_url(url)
    log(f"--- FINALIZADO. ENLACES: {len(results)} ---")
    return json.dumps(results)
    
