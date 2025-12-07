var KronosEngine = KronosEngine || { providers: {} };

KronosEngine.providers['Voe'] = {
    extract: function(url) {
        try {
            bridge.log("Iniciando Voe Script para: " + url);

            var html = bridge.fetchHtml(url);
            if (!html) return null;

            // Patrón estándar Voe
            var regex = /"hls":\s*"([^"]+)"/i;
            var match = html.match(regex);

            if (match && match[1]) {
                return match[1];
            }

            // Patrón MP4
            var regexMp4 = /"mp4":\s*"([^"]+)"/i;
            var matchMp4 = html.match(regexMp4);
            if (matchMp4 && matchMp4[1]) {
                return matchMp4[1];
            }

        } catch (e) {
            bridge.log("Error en Voe Script: " + e.toString());
        }
        return null;
    }
};

