import json
import re
import requests
import base64
import urllib.parse
from java import jclass

# --- CORRECCIÓN AQUÍ ---
# Apuntamos a la dirección correcta de tu archivo Kotlin:
# Paquete: com.kronos.tv.ui
# Objeto: AppLogger
try:
    AppLogger = jclass("com.kronos.tv.ui.AppLogger")
    has_logger = True
except:
    # Si falla (por ejemplo en pruebas de PC), evitamos que crashee
    has_logger = False

def log(msg):
    if has_logger:
        AppLogger.log("PY_INFO", str(msg))

def warn(msg):
    if has_logger:
        AppLogger.log("PY_WARN", str(msg))

def error(msg):
    if has_logger:
        AppLogger.log("PY_ERR", str(msg))

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
            log(f"🌐 GET: {url}")
            r = self.session.get(url, timeout=15, verify=False)
            
            log(f"📡 Status Code: {r.status_code}")
            
            if r.status_code != 200:
                error(f"Error HTTP: {r.status_code}")
                if r.status_code == 403: error("⛔ Acceso Denegado (Cloudflare)")
                return None
                
            html = r.text
            log(f"📄 HTML recibido: {len(html)} chars")
            
            if "Just a moment" in html or "cloudflare" in html.lower():
                error("🔥 CLOUDFLARE DETECTADO")
            
            return html
        except Exception as e:
            error(f"Excepción Red: {e}")
            return None

    def do_search(self, query):
        try:
            log(f"🔍 BUSCANDO: '{query}'")
            search_url = f"{self.base_url}/?s={urllib.parse.quote(query)}"
            html = self.get_html(search_url)
            
            if not html: 
                error("HTML vacío")
                return []

            results = []
            
            # Debug HTML
            count_articles = html.count("<article")
            log(f"🧩 Etiquetas <article>: {count_articles}")
            
            if count_articles == 0:
                warn("⚠️ No se encontraron artículos. El HTML podría ser distinto.")

            # Regex
            articles = re.findall(r'<article(.*?)</article>', html, re.DOTALL)
            
            for i, article in enumerate(articles):
                try:
                    title_match = re.search(r'alt=["\']([^"\']+)["\']', article)
                    if not title_match: title_match = re.search(r'<h3>(.*?)</h3>', article)
                    t = title_match.group(1) if title_match else "Sin Titulo"
                    
                    year_match = re.search(r'<p>\s*(\d{4})\s*</p>', article)
                    y = year_match.group(1) if year_match else "0"
                    
                    url_match = re.search(r'href=["\']([^"\']+)["\']', article)
                    u = url_match.group(1) if url_match else ""
                    
                    log(f"   #{i+1}: {t} ({y}) -> {u}")
                    
                    if u:
                        tipo = 'tv' if '/series/' in u or '/tvshows/' in u else 'movie'
                        img_match = re.search(r'src=["\']([^"\']+)["\']', article)
                        img = img_match.group(1) if img_match else ""
                        
                        results.append({
                            "title": t,
                            "url": u,
                            "img": img,
                            "year": y,
                            "type": tipo
                        })
                except Exception as e: error(f"Err parse #{i}: {e}")
            
            log(f"✅ Retornando {len(results)} resultados")
            return results

        except Exception as e:
            error(f"CRASH SEARCH: {e}")
            return []

    # --- EXTRACCIÓN (Misma lógica, mismos logs) ---
    def scrape_url(self, url):
        log(f"⛏️ EXTRAYENDO: {url}")
        found_links = []
        html = self.get_html(url)
        if not html: return []
        
        # 1. Embed.php
        iframe_match = re.search(r'src\s*=\s*["\']([^"\']*embed\.php\?id=\d+)[^"\']*["\']', html, re.IGNORECASE)
        if iframe_match:
            iframe_url = iframe_match.group(1)
            if iframe_url.startswith("//"): iframe_url = "https:" + iframe_url
            if "http" not in iframe_url: iframe_url = self.base_url + iframe_url
            log("   -> Encontrado embed.php")
            found_links.extend(self._scrape_double_hop(iframe_url))

        # 2. VAST
        if "go_to_playerVast" in html:
            log("   -> Encontrado sistema VAST")
            found_links.extend(self._scrape_vast(html))

        # 3. Embed69 JSON
        if "dataLink" in html:
            log("   -> Encontrado sistema Embed69")
            found_links.extend(self._scrape_embed69_json(html))
        
        # 4. Iframes
        frames = re.findall(r"src=['\"](https://embed69\.org/f/[^'\"]+)['\"]", html)
        if frames: log(f"   -> Encontrados {len(frames)} iframes externos")
        for f_url in frames:
            try:
                r = self.session.get(f_url, timeout=5, verify=False)
                if r.status_code == 200: found_links.extend(self._scrape_embed69_json(r.text))
            except: pass

        log(f"🏁 TOTAL ENLACES: {len(found_links)}")
        return found_links

    # --- HELPERS (Iguales) ---
    def _scrape_double_hop(self, url):
        links = []
        try:
            r = self.session.get(url, headers={'Referer': self.base_url}, timeout=10, verify=False)
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
                            b64 = params['link'][0] + ('=' * (-len(params['link'][0]) % 4))
                            decoded = base64.b64decode(b64).decode('utf-8')
                            links.append({'server': server_clean, 'url': decoded, 'quality': '720p', 'provider': 'SoloLatino'})
                    except: pass
                else:
                    links.append({'server': server_clean, 'url': clean_link, 'quality': '720p', 'provider': 'SoloLatino'})
        except: pass
        return links

    def _scrape_vast(self, html):
        links = []
        matches = re.findall(r"onclick=\"go_to_playerVast\('([^']+)'[^>]*data-lang=\"(\d+)\"[^>]*>.*?<span>(.*?)</span>", html, re.DOTALL)
        for url, lid, name in matches:
            links.append({'server': name.strip().title(), 'url': url, 'quality': '720p', 'provider': 'SoloLatino (Vast)'})
        return links

    def _scrape_embed69_json(self, html):
        links = []
        match = re.search(r'let\s+dataLink\s*=\s*(\[.*?\]);', html, re.DOTALL)
        if match:
            try:
                data = json.loads(match.group(1))
                for item in data:
                    lang = item.get('video_language', 'UNK')
                    for embed in item.get('sortedEmbeds', []):
                        if embed.get('servername') == 'download': continue
                        if embed.get('link'):
                             decoded = self._decode_jwt(embed.get('link'))
                             if decoded: links.append({'server': embed['servername'], 'url': decoded, 'quality': '1080p', 'lang': lang, 'provider': 'SoloLatino'})
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
    return json.dumps(scraper.scrape_url(url))
        
