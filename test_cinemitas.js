const axios = require('axios');
const cheerio = require('cheerio');
const qs = require('qs');

const BASE_URL = "https://cinemitas.org";
const UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";

async function runTest() {
    console.log("🕵️‍♂️ INICIANDO PRUEBA DE CINEMITAS...");

    try {
        // 1. PRUEBA DE BÚSQUEDA
        console.log("\n--- 1. BUSCANDO 'HARRY POTTER' ---");
        const searchUrl = `${BASE_URL}/?s=Harry+Potter`;
        let res = await axios.get(searchUrl, { headers: { 'User-Agent': UA } });
        let $ = cheerio.load(res.data);
        
        // AQUÍ ESTABA EL ERROR: Ahora sí se pegará el $ correctamente
        let primerResultado = $("article.item a").first().attr("href");
        
        if (!primerResultado) {
            console.error("❌ FALLO: No se encontraron resultados.");
            return;
        }
        console.log("✅ ENCONTRADO:", primerResultado);

        // 2. PRUEBA DE DETALLES (Sacar ID)
        console.log("\n--- 2. ANALIZANDO DETALLES ---");
        res = await axios.get(primerResultado, { headers: { 'User-Agent': UA } });
        $ = cheerio.load(res.data);
        
        const shortlink = $("link[rel=shortlink]").attr("href");
        const postId = shortlink.split("?p=")[1];
        console.log("✅ ID DEL POST:", postId);

        const opcion = $("#player-options-ul li").first();
        if (!opcion.length) {
             console.error("❌ FALLO: No hay servidores visibles.");
             return;
        }

        const type = opcion.attr("data-type");
        const post = opcion.attr("data-post");
        const nume = opcion.attr("data-nume");
        
        console.log(`✅ DATOS PARA AJAX -> Post: ${post}, Nume: ${nume}, Type: ${type}`);

        // 3. PRUEBA DE AJAX (EL POST CRÍTICO)
        console.log("\n--- 3. INTENTANDO SACAR LINK (POST) ---");
        
        const ajaxUrl = `${BASE_URL}/wp-admin/admin-ajax.php`;
        const data = qs.stringify({
            'action': 'doo_player_ajax',
            'post': post,
            'nume': nume,
            'type': type
        });

        res = await axios.post(ajaxUrl, data, {
            headers: {
                'User-Agent': UA,
                'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
                'X-Requested-With': 'XMLHttpRequest'
            }
        });

        console.log("📡 RESPUESTA DEL SERVIDOR:", res.data);

        if (res.data.embed_url) {
            console.log("\n🎉 ¡ÉXITO! LINK FINAL:", res.data.embed_url);
        } else {
            console.error("\n❌ FALLO: La respuesta no tiene link.");
        }

    } catch (e) {
        console.error("💥 ERROR FATAL:", e.message);
    }
}

runTest();
