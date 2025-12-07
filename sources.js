var KronosEngine = {
    providers: {
        "Ejemplo": {
            extract: function(html) {
                Android.log("Analizando HTML desde JS...");
                return "https://ejemplo.com/video.m3u8";
            }
        }
    }
};
