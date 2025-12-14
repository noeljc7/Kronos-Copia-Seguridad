import json
import re
import base64
import urllib.parse
from java import jclass

# Librerías potentes (definidas en build.gradle)
import cloudscraper
from bs4 import BeautifulSoup

# --- LOGGER BRIDGE ---
try:
    AppLogger = jclass("com.kronos.tv.ui.AppLogger")
    has_logger = True
except:
    has_logger = False
    print("⚠️ AppLogger no encontrado. Usando print estándar.")

def log(msg):
    if has_logger: AppLogger.log("PY_INFO", str(msg))
    else: print(f"[PY_INFO] {msg}")

def warn(msg):
    if has_logger: AppLogger.log("PY_WARN", str(msg))
    else: print(f"[PY_WARN] {msg}")

def error(msg):
    if has_logger: AppLogger.log("PY_ERR", str(msg))
    else: print(f"[PY_ERR] {msg}")

class SoloLatinoScraper:
    def __init__(self):
        # 🔥 AQUÍ ESTÁ LA MAGIA: Usamos cloudscraper en lugar de requests
        # Esto simula un navegador real y resuelve los captchas de fondo JS.
        self.scraper = cloudscraper.create_scraper(
            browser={
                'browser': 'chrome',
                'platform': 'android',
                'desktop': False
            }
        )
        self.base_url = "https://sololatino.net"

    def get_html(self, url):
        try:
            log(f"🌐 GET: {url}")
            # Cloudscraper maneja las cookies y headers por nosotros
            r = self.scraper.get(url, timeout=20)
            
            if r.status_code != 200:
                error(f"Error HTTP: {r.status_code}")
                return None
            
            return r.text
        except Exception as e:
            error(f"Excepción Red: {e}")
            return None

    def do_search(self, query):
        try:
            log(f"🔍 BUSCANDO (BS4): '{query}'")
            search_url = f"{self.base_url}/?s={urllib.parse.quote(query)}"
            html = self.get_html(search_url)
            
            if not html: return []

            # Usamos BeautifulSoup para parsear (Mucho más seguro que Regex)
            soup = BeautifulSoup(html, 'html.parser')
            results = []

            # Buscamos los artículos. La estructura suele ser <article class="post">
            articles = soup.find_all('article')
            
            if not articles:
                warn("⚠️ No se encontraron artículos con BS4.")

            for art in articles:
                try:
                    # 1. Título (Suele estar en alt de imagen o en h3)
                    title = "Sin Titulo"
                    img_tag = art.find('img')
                    if img_tag and img_tag.get('alt'):
                        title = img_tag.get('alt')
                    else:
                        h3 = art.find('h3')
                        if h3: title = h3.get_text(strip=True)

                    # 2. URL
                    link_tag = art.find('a', href=True)
                    url = link_tag['href'] if link_tag else ""
                    if not url: continue

                    # 3. Imagen
                    img = img_tag['src'] if img_tag and img_tag.get('src') else ""

                    # 4. Año (Suele estar en <span class="year"> o <p>)
                    year = "0"
                    year_tag = art.find('span', class_='year')
                    if not year_tag: year_tag = art.find('p') # Fallback
                    
                    if year_tag:
                        # Buscamos 4 dígitos en el texto
                        y_match = re.search(r'\d{4}', year_tag.get_text())
                        if y_match: year = y_match.group(0)

                    # 5. Tipo
                    tipo = 'tv' if '/series/' in url or '/tvshows/' in url else 'movie'

                    results.append({
                        "title": title,
                        "url": url,
                        "img": img,
                        "year": year,
                        "type": tipo
                    })
                except Exception as ex:
                    continue

            log(f"✅ Retornando {len(results)} resultados")
            return results

        except Exception as e:
            error(f"CRASH SEARCH: {e}")
            return []

    # --- LOGICA DE EXTRACCIÓN (Tu lógica original mejorada) ---
    def scrape_url(self, url):
        log(f"⛏️ EXTRAYENDO: {url}")
        found_links = []
        html = self.get_html(url)
        if not html: return []
        
        # 1. Embed.php (Tu Regex estaba bien aquí)
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
        
        # 4. Iframes Externos (Ej. fembed, streamtape)
        soup = BeautifulSoup(html, 'html.parser')
        iframes = soup.find_all('iframe')
        for iframe in iframes:
            src = iframe.get('src', '')
            if 'embed69' in src or '/f/' in src:
                try:
                    r = self.scraper.get(src, timeout=10)
                    if r.status_code == 200:
                        found_links.extend(self._scrape_embed69_json(r.text))
                except: pass

        return found_links

    # --- HELPERS ---
    def _scrape_double_hop(self, url):
        links = []
        try:
            # Importante: Headers para simular navegación real
            r = self.scraper.get(url, headers={'Referer': self.base_url}, timeout=10)
            html = r.text
            matches = re.findall(r"onclick=\"go_to_player\('([^']+)'\)\"[^>]*>.*?<span>(.*?)</span>", html, re.DOTALL | re.IGNORECASE)
            for link, server_name in matches:
                clean_link = link.strip()
                server_clean = server_name.strip().title()
                
                # Decodificación Base64 si es necesaria
                if "embed.php" in clean_link and "link=" in clean_link:
                    try:
                        parsed = urllib.parse.urlparse(clean_link)
                        params = urllib.parse.parse_qs(parsed.query)
                        if 'link' in params:
                            b64 = params['link'][0]
                            # Padding automático para base64
                            b64 += '=' * (-len(b64) % 4)
                            decoded = base64.b64decode(b64).decode('utf-8')
                            links.append({'server': server_clean, 'url': decoded, 'quality': '720p', 'provider': 'SoloLatino', 'lang': 'Latino'})
                    except: pass
                else:
                    links.append({'server': server_clean, 'url': clean_link, 'quality': '720p', 'provider': 'SoloLatino', 'lang': 'Latino'})
        except: pass
        return links

    def _scrape_vast(self, html):
        links = []
        matches = re.findall(r"onclick=\"go_to_playerVast\('([^']+)'[^>]*data-lang=\"(\d+)\"[^>]*>.*?<span>(.*?)</span>", html, re.DOTALL)
        for url, lid, name in matches:
            lang = "Latino"
            if lid == "1": lang = "Castellano"
            if lid == "2": lang = "Subtitulado"
            links.append({'server': name.strip().title(), 'url': url, 'quality': '720p', 'provider': 'SoloLatino (Vast)', 'lang': lang})
        return links

    def _scrape_embed69_json(self, html):
        links = []
        match = re.search(r'let\s+dataLink\s*=\s*(\[.*?\]);', html, re.DOTALL)
        if match:
            try:
                data = json.loads(match.group(1))
                for item in data:
                    lang = item.get('video_language', 'UNK')
                    # Normalizar idioma
                    if "LAT" in lang.upper(): lang = "Latino"
                    elif "SUB" in lang.upper(): lang = "Subtitulado"
                    elif "ESP" in lang.upper() or "CAST" in lang.upper(): lang = "Castellano"

                    for embed in item.get('sortedEmbeds', []):
                        if embed.get('servername') == 'download': continue
                        if embed.get('link'):
                             decoded = self._decode_jwt(embed.get('link'))
                             if decoded: 
                                 links.append({
                                     'server': embed['servername'], 
                                     'url': decoded, 
                                     'quality': '1080p', 
                                     'lang': lang, 
                                     'provider': 'SoloLatino'
                                 })
            except: pass
        return links

    def _decode_jwt(self, token):
        try:
            parts = token.split('.')
            if len(parts) < 2: return None
            payload = parts[1] + '=' * (-len(parts[1]) % 4)
            return json.loads(base64.urlsafe_b64decode(payload).decode('utf-8')).get('link')
        except: return None

# --- EXPORTACIONES ---
scraper = SoloLatinoScraper()

def search(query):
    return json.dumps(scraper.do_search(query))

def get_links(url):
    return json.dumps(scraper.scrape_url(url))
