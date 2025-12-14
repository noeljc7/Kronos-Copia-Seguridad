import importlib
import sys
import os
from java import jclass

# Logger Bridge
try:
    AppLogger = jclass("com.kronos.tv.ui.AppLogger")
    def log(msg): AppLogger.log("PY_LOADER", str(msg))
except:
    def log(msg): print(f"PY_LOADER: {msg}")

def run_plugin_method(module_name, method_name, args_json=None):
    """
    Carga un módulo dinámicamente (incluso si se acaba de descargar)
    y ejecuta una función.
    """
    try:
        log(f"Intentando cargar módulo: {module_name}")
        
        # 1. Importar o recuperar el módulo
        if module_name in sys.modules:
            module = sys.modules[module_name]
            # 🔥 IMPORTANTE: Reload para tomar cambios si actualizamos el archivo sin reiniciar app
            importlib.reload(module)
        else:
            module = importlib.import_module(module_name)
            
        # 2. Obtener la función
        if not hasattr(module, method_name):
            return f'{{"error": "Método {method_name} no encontrado en {module_name}"}}'
            
        func = getattr(module, method_name)
        
        # 3. Ejecutar
        if args_json:
            return func(args_json)
        else:
            return func()
            
    except Exception as e:
        import traceback
        return f'{{"error": "Excepción en loader: {str(e)}", "trace": "{traceback.format_exc()}"}}'
