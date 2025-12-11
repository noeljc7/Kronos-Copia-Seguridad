import json
import cloudscraper
import re
from java import jclass # Importamos interfaz Java

# --- CONFIGURACIÓN DE LOGS ANDROID ---
Log = jclass("android.util.Log")
TAG = "KRONOS_PY"

def log(msg):
    Log.d(TAG, str(msg))

def error(msg):
    Log.e(TAG, str(msg))

# --- INICIALIZACIÓN ---
try:
    log("Iniciando Cloudscraper...")
    # Configuramos un navegador Chrome de PC para engañar a Cloudflare
    scraper = cloudscraper.create_scraper(
        browser={
            'browser': 'chrome',
            'platform': 'windows',
            'desktop': True
        }
    )
    log("Cloudscraper listo.")
except Exception as e:
    error(f"Error fatal iniciando Cloudscraper: {e}")

BASE_URL = "https://sololatino.net"

def search(query):
    try:
        log(f"--- SEARCH INICIO: {query} ---")
        
        # 1. Obtener HTML de la Home (Para robar el Nonce)
        log(f"Descargando Home: {BASE_URL}")
        response = scraper.get(BASE_URL)
        
        if response.status_code != 200:
            error(f"Error HTTP en Home: {response.status_code}")
            return "[]"
            
        home_html = response.text
        # log(f"HTML Home longitud: {len(home_html)}") # Descomentar si sospechas que llega vacío

        # 2. Buscar Nonce con Regex Flexible
        # Busca: "nonce":"xyz"  o  'nonce':'xyz'
        match = re.search(r'["\']nonce["\']\s*:\s*["\']([^"\']+)["\']', home_html)
        
        if not match:
            error("❌ NO SE ENCONTRÓ EL NONCE. Posible cambio de seguridad.")
            return "[]"
            
        nonce = match.group(1)
        log(f"🔑 Nonce encontrado: {nonce}")
        
        # 3. Consultar API Dooplay
        # Usamos requests params para que codifique bien los espacios (%20)
        api_url = f"{BASE_URL}/wp-json/dooplay/search/"
        params = {'keyword': query, 'nonce': nonce}
        
        log(f"Consultando API: {api_url} | params: {params}")
        
        api_resp = scraper.get(api_url, params=params)
        log(f"Respuesta API Code: {api_resp.status_code}")
        
        resp_json = api_resp.json()
        
        results = []
        # Normalizar respuesta (Dooplay devuelve diccionario o lista)
        items = resp_json.values() if isinstance(resp_json, dict) else resp_json
        
        for item in items:
            # Validamos que tenga lo mínimo necesario
            if isinstance(item, dict) and 'url' in item and 'title' in item:
                tipo = 'tv' if '/series/' in item['url'] or '/tvshows/' in item['url'] else 'movie'
                
                # Extraer año de forma segura
                year = "0"
                if 'extra' in item and isinstance(item['extra'], dict):
                    year = item['extra'].get('date', '0')
                
                results.append({
                    "title": item['title'],
                    "url": item['url'],
                    "img": item.get('img', ''),
                    "year": str(year),
                    "type": tipo
                })
        
        log(f"✅ Resultados parseados: {len(results)}")
        return json.dumps(results)

    except Exception as e:
        error(f"🔥 EXCEPCIÓN EN SEARCH: {str(e)}")
        import traceback
        error(traceback.format_exc())
        return "[]"

def get_links(url):
    try:
        log(f"--- GET LINKS INICIO: {url} ---")
        
        response = scraper.get(url)
        if response.status_code != 200:
            error(f"Error HTTP {response.status_code} en URL")
            return "[]"
            
        html = response.text
        final_links = []
        
        # A. BUSCAR IFRAMES DIRECTOS (Embed69/XuPalace)
        # Regex busca src="..." que contenga embed69 o xupalace
        log("Buscando iframes directos...")
        iframes = re.findall(r'<iframe[^>]*src=["\']([^"\']+)["\']', html)
        
        for src in iframes:
            if "embed69" in src or "xupalace" in src:
                log(f"Iframe encontrado: {src}")
                extracted = extract_embed(src)
                final_links.extend(extracted)

        # B. BUSCAR BOTONES DE SERIE (API AJAX)
        log("Buscando botones de serie (AJAX)...")
        lis = re.findall(r'<li[^>]+data-post=[\'"](\d+)[\'"][^>]*data-type=[\'"]([^"\']+)[\'"][^>]*data-nume=[\'"](\d+)[\'"]', html)
        
        log(f"Botones encontrados: {len(lis)}")
        
        for post_id, tipo, nume in lis:
            if str(nume) == "trailer": continue
            
            api_url = f"{BASE_URL}/wp-json/dooplay/v2/{post_id}/{tipo}/{nume}"
            # log(f"Consultando opción: {api_url}")
            
            try:
                # Simulamos ser navegador pidiendo JSON
                headers = {"X-Requested-With": "XMLHttpRequest"}
                api_data = scraper.get(api_url, headers=headers).json()
                
                target_url = api_data.get('embed_url') or api_data.get('u')
                
                if target_url and ("embed69" in target_url or "xupalace" in target_url):
                    # log(f"Target revelado: {target_url}")
                    extracted = extract_embed(target_url)
                    final_links.extend(extracted)
            except:
                pass # Ignorar fallos puntuales en botones

        log(f"✅ Total enlaces extraídos: {len(final_links)}")
        return json.dumps(final_links)
        
    except Exception as e:
        error(f"🔥 EXCEPCIÓN EN GET_LINKS: {str(e)}")
        return "[]"

def extract_embed(url):
    try:
        # Extraer ID
        match = re.search(r'/(?:f|video)/([a-zA-Z0-9-]+)', url)
        if not match: return []
        
        video_id = match.group(1)
        domain = "https://xupalace.org" if "xupalace" in url else "https://embed69.org"
        decrypt_url = f"{domain}/api/decrypt"
        
        # Petición POST
        headers = {
            "Referer": url,
            "X-Requested-With": "XMLHttpRequest"
        }
        
        resp = scraper.post(decrypt_url, data={'id': video_id}, headers=headers).json()
        
        extracted = []
        if resp.get('success') and resp.get('links'):
            for link in resp['links']:
                final_url = link['link'].replace('\\', '')
                
                # Detectar nombre del servidor
                host = "Server"
                if "voe" in final_url: host = "Voe"
                elif "dood" in final_url or "dintezuvio" in final_url: host = "Doodstream"
                elif "filemoon" in final_url: host = "Filemoon"
                elif "streamwish" in final_url: host = "Streamwish"
                elif "vidhide" in final_url: host = "Vidhide"
                
                extracted.append({
                    "server": host,
                    "url": final_url,
                    "quality": "HD",
                    "lang": "Multi",
                    "provider": "Python"
                })
                
        return extracted
    except Exception as e:
        error(f"Error Decrypt: {str(e)}")
        return []
