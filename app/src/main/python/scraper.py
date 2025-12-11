import json
import cloudscraper
import re
from bs4 import BeautifulSoup

# Instancia global del scraper (mantiene cookies y sesión activa)
scraper = cloudscraper.create_scraper(
    browser={
        'browser': 'chrome',
        'platform': 'windows',
        'desktop': True
    }
)

BASE_URL = "https://sololatino.net"

def log(msg):
    print(f"[PYTHON] {msg}")

def search(query):
    try:
        log(f"Buscando: {query}")
        
        # 1. Obtener Nonce de la home (Cloudscraper maneja el acceso)
        home = scraper.get(BASE_URL).text
        nonce_match = re.search(r'"nonce":"([^"]+)"', home)
        
        if not nonce_match:
            log("No se encontró nonce")
            return "[]"
            
        nonce = nonce_match.group(1)
        
        # 2. Consultar API Dooplay
        url = f"{BASE_URL}/wp-json/dooplay/search/?keyword={query}&nonce={nonce}"
        resp = scraper.get(url).json()
        
        results = []
        # Normalizar respuesta (puede ser dict o list)
        items = resp.values() if isinstance(resp, dict) else resp
        
        for item in items:
            if 'url' in item and 'title' in item:
                tipo = 'tv' if '/series/' in item['url'] or '/tvshows/' in item['url'] else 'movie'
                year = item.get('extra', {}).get('date', '0')
                
                results.append({
                    "title": item['title'],
                    "url": item['url'],
                    "img": item.get('img', ''),
                    "year": year,
                    "type": tipo
                })
                
        return json.dumps(results)
    except Exception as e:
        log(f"Error search: {str(e)}")
        return "[]"

def get_links(url):
    try:
        log(f"Analizando: {url}")
        html = scraper.get(url).text
        soup = BeautifulSoup(html, 'html.parser')
        
        final_links = []
        
        # BUSCAR IFRAMES DIRECTOS (Peliculas)
        iframes = soup.find_all('iframe', class_='metaframe')
        for iframe in iframes:
            src = iframe.get('src')
            if "embed69" in src or "xupalace" in src:
                extracted = extract_embed69(src)
                final_links.extend(extracted)

        # BUSCAR OPCIONES DE SERIES (API AJAX)
        # En Python BeautifulSoup hace esto facilísimo
        lis = soup.find_all('li', {'data-post': True, 'data-nume': True})
        
        for li in lis:
            post_id = li['data-post']
            tipo = li['data-type']
            nume = li['data-nume']
            
            if nume == "trailer": continue
            
            # Llamada a API interna para sacar el iframe
            api_url = f"{BASE_URL}/wp-json/dooplay/v2/{post_id}/{tipo}/{nume}"
            api_resp = scraper.get(api_url).json()
            
            target_url = api_resp.get('embed_url') or api_resp.get('u')
            if target_url:
                if "embed69" in target_url or "xupalace" in target_url:
                    extracted = extract_embed69(target_url)
                    final_links.extend(extracted)

        return json.dumps(final_links)
        
    except Exception as e:
        log(f"Error get_links: {str(e)}")
        return "[]"

def extract_embed69(url):
    try:
        log(f"Destripando Embed: {url}")
        
        # Extraer ID
        match = re.search(r'/(?:f|video)/([a-zA-Z0-9-]+)', url)
        if not match: return []
        
        video_id = match.group(1)
        domain = "https://xupalace.org" if "xupalace" in url else "https://embed69.org"
        decrypt_url = f"{domain}/api/decrypt"
        
        # Petición POST directa (Cloudscraper se encarga de headers/cookies)
        headers = {
            "Referer": url,
            "X-Requested-With": "XMLHttpRequest"
        }
        
        resp = scraper.post(decrypt_url, data={'id': video_id}, headers=headers).json()
        
        extracted = []
        if resp.get('success'):
            for link in resp.get('links', []):
                extracted.append({
                    "server": link['link'].split('/')[2], # Nombre del dominio como nombre server
                    "url": link['link'],
                    "quality": "HD",
                    "lang": "Multi"
                })
                
        return extracted
    except Exception as e:
        log(f"Error decrypt: {str(e)}")
        return []
