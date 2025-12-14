import json
import re
import requests
import base64
import urllib.parse
from java import jclass

# --- LOGS DETALLADOS ---
Log = jclass("android.util.Log")
TAG = "KRONOS_PY_DEBUG" # Tag nuevo para filtrar fácil

def log(msg):
    Log.d(TAG, f"ℹ️ {str(msg)}")

def warn(msg):
    Log.w(TAG, f"⚠️ {str(msg)}")

def error(msg):
    Log.e(TAG, f"❌ {str(msg)}")

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
            
            # DIAGNÓSTICO DE BLOQUEO
            if r.status_code != 200:
                error(f"Error HTTP: {r.status_code}")
                return None
                
            html = r.text
            log(f"📄 HTML Descargado: {len(html)} caracteres")
            
            # Verificamos si es Cloudflare o bloqueo
            if "Just a moment" in html or "cloudflare" in html.lower():
                error("🔥 ¡CLOUDFLARE DETECTADO! El HTML está encriptado.")
            elif len(html) < 1000:
                warn(f"⚠️ HTML sospechosamente corto: {html}")
                
            return html
        except Exception as e:
            error(f"Excepción de Red: {e}")
            return None

    def do_search(self, query):
        try:
            log(f"🔍 BÚSQUEDA INICIADA: '{query}'")
            
            # 1. URL
            search_url = f"{self.base_url}/?s={urllib.parse.quote(query)}"
            html = self.get_html(search_url)
            
            if not html: 
                error("No se obtuvo HTML para buscar.")
                return []

            results = []
            
            # 2. DEBUG DEL REGEX
            # Primero vemos si hay articles en general
            article_count = html.count("<article")
            log(f"🧩 Etiquetas <article> contadas en texto: {article_count}")
            
            articles = re.findall(r'<article(.*?)</article>', html, re.DOTALL)
            log(f"🧩 Bloques Regex extraídos: {len(articles)}")
            
            for i, article in enumerate(articles):
                try:
                    # Logs individuales por cada candidato
                    title_match = re.search(r'alt=["\']([^"\']+)["\']', article)
                    if not title_match: title_match = re.search(r'<h3>(.*?)</h3>', article)
                    
                    year_match = re.search(r'<p>\s*(\d{4})\s*</p>', article)
                    url_match = re.search(r'href=["\']([^"\']+)["\']', article)
                    
                    t = title_match.group(1) if title_match else "SIN TITULO"
                    y = year_match.group(1) if year_match else "0"
                    u = url_match.group(1) if url_match else "SIN URL"
                    
                    log(f"   Candidate #{i+1}: {t} | Año: {y} | Link: {u}")
                    
                    # Validación básica
                    if url_match:
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
                except Exception as e: 
                    error(f"Error parseando articulo #{i}: {e}")
            
            log(f"✅ Resultados finales retornados: {len(results)}")
            return results

        except Exception as e:
            error(f"CRASH EN SEARCH: {e}")
            import traceback
            error(traceback.format_exc())
            return []

    # --- EXTRACCIÓN (Mantenemos igual pero con logs) ---
    def scrape_url(self, url):
        log(f"⛏️ EXTRAYENDO URL: {url}")
        found_links = []
        html = self.get_html(url)
        if not html: return []
        
        # Log para ver qué estamos buscando
        if "embed.php" in html: log("👀 Detectado embed.php en HTML")
        if "go_to_playerVast" in html: log("👀 Detectado VAST en HTML")
        if "dataLink" in html: log("👀 Detectado dataLink (Embed69) en HTML")

        # 1. Doble Salto
        iframe_match = re.search(r'src\s*=\s*["\']([^"\']*embed\.php\?id=\d+)[^"\']*["\']', html, re.IGNORECASE)
        if iframe_match:
            iframe_url = iframe_match.group(1)
            if iframe_url.startswith("//"): iframe_url = "https:" + iframe_url
            if "http" not in iframe_url: iframe_url = self.base_url + iframe_url
            log(f"🦘 Saltando a iframe: {iframe_url}")
            found_links.extend(self._scrape_double_hop(iframe_url))

        # 2. VAST
        if "go_to_playerVast" in html:
            found_links.extend(self._scrape_vast(html))

        # 3. Embed69 JSON
        if "dataLink" in html:
            found_links.extend(self._scrape_embed69_json(html))
        
        # 4. Iframes
        found_links.extend(self._scrape_iframes(html))

        log(f"🏁 Total enlaces extraídos: {len(found_links)}")
        return found_links

    # --- HELPERS (Simplificados para brevedad, son los mismos) ---
    def _scrape_double_hop(self, url):
        links = []
        try:
            r = self.session.get(url, headers={'Referer': self.base_url}, timeout=10, verify=False)
            html = r.text
            matches = re.findall(r"onclick=\"go_to_player\('([^']+)'\)\"[^>]*>.*?<span>(.*?)</span>", html, re.DOTALL | re.IGNORECASE)
            log(f"   DobleHop encontró {len(matches)} botones")
            for link, server_name in matches:
                clean_link = link.strip()
                server_clean = server_name.strip().title()
                if "embed.php" in clean_link and "link=" in clean_link:
                    # Decode logic here
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
        except Exception as e: error(f"Error DoubleHop: {e}")
        return links

    def _scrape_vast(self, html):
        links = []
        matches = re.findall(r"onclick=\"go_to_playerVast\('([^']+)'[^>]*data-lang=\"(\d+)\"[^>]*>.*?<span>(.*?)</span>", html, re.DOTALL)
        log(f"   VAST encontró {len(matches)} opciones")
        for url, lid, name in matches:
            links.append({'server': name.strip().title(), 'url': url, 'quality': '720p', 'provider': 'SoloLatino (Vast)'})
        return links

    def _scrape_embed69_json(self, html):
        links = []
        match = re.search(r'let\s+dataLink\s*=\s*(\[.*?\]);', html, re.DOTALL)
        if match:
            log("   Embed69 JSON encontrado")
            try:
                data = json.loads(match.group(1))
                count = 0
                for item in data:
                    for embed in item.get('sortedEmbeds', []):
                        if embed.get('link'): count += 1
                        # Decode logic (omitted for brevity, assume same as before)
                        if embed.get('link'):
                             decoded = self._decode_jwt(embed.get('link'))
                             if decoded: links.append({'server': embed['servername'], 'url': decoded, 'quality': '1080p'})
                log(f"   Embed69 procesó {count} enlaces potenciales")
            except Exception as e: error(f"Error parseando JSON Embed69: {e}")
        return links

    def _scrape_iframes(self, html):
        links = []
        frames = re.findall(r"src=['\"](https://embed69\.org/f/[^'\"]+)['\"]", html)
        log(f"   Iframes Embed69 encontrados: {len(frames)}")
        for f_url in frames:
            try:
                r = self.session.get(f_url, timeout=5, verify=False)
                if r.status_code == 200: links.extend(self._scrape_embed69_json(r.text))
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
                    
