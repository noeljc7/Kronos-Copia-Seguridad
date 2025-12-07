# KRONOS-TV Project Structure

## Estructura del Proyecto

```
KRONOS-TV/
├── app/                           # Módulo principal de la aplicación
│   ├── build.gradle               # Configuración de build del app
│   ├── proguard-rules.pro          # Reglas de ofuscación
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml # Manifiesto de la aplicación
│       │   ├── java/
│       │   │   └── com/kronos/tv/
│       │   │       └── MainActivity.kt
│       │   └── res/
│       │       ├── drawable/      # Recursos gráficos
│       │       ├── layout/        # Layouts XML
│       │       └── values/        # Strings, colors, styles
│       └── test/                  # Tests unitarios
├── build.gradle                   # Build principal
├── settings.gradle                # Configuración Gradle
├── gradle.properties              # Propiedades Gradle
├── local.properties.example       # Plantilla configuración local
├── build.sh                       # Script de compilación
└── README.md                      # Documentación

```

## Requisitos

- **Android SDK**: API 21 (Android 5.0) mínimo
- **Gradle**: 8.0+
- **Java/Kotlin**: Java 11+
- **Android Studio** (opcional pero recomendado)

## Instalación

1. **Configurar SDK local:**
   ```bash
   cp local.properties.example local.properties
   # Editar local.properties con la ruta a tu Android SDK
   ```

2. **Instalar dependencias:**
   ```bash
   ./gradlew tasks
   ```

## Compilación

### Debug APK (para desarrollo/testing)
```bash
./gradlew assembleDebug
# o
bash build.sh build
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

### Release APK (para producción)
```bash
./gradlew assembleRelease
# o
bash build.sh release
```
Output: `app/build/outputs/apk/release/app-release.apk`

### Comandos útiles
```bash
bash build.sh clean   # Limpiar build
bash build.sh test    # Ejecutar tests
bash build.sh install # Instalar en dispositivo
```

## Compilación desde línea de comandos

```bash
# Solo compilar (sin instalar)
./gradlew assembleDebug

# Compilar e instalar
./gradlew installDebug

# Compilar sin recursos, solo código
./gradlew compileDebugKotlin

# Ver tareas disponibles
./gradlew tasks
```

## Instalación en dispositivo/emulador

```bash
# Dispositivo USB conectado
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Desde Gradle
./gradlew installDebug

# Lista de dispositivos
adb devices

# Ejecutar app específica
adb shell am start -n com.kronos.tv/.MainActivity
```

## Configuración de Signing (Release)

1. Crear keystore:
```bash
keytool -genkey -v -keystore my-release-key.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias my-key-alias
```

2. Editar `app/build.gradle` (en la sección `buildTypes.release`)

## Notas para Google TV

- ✅ Configurado con `leanback_launcher` category
- ✅ Soporte para navegación con control remoto
- ✅ Mínimo SDK 21 (requerido por Google TV)
- ✅ Librerías de Google TV incluidas

## Próximos pasos

1. Personalizar la app en `MainActivity.kt`
2. Agregar más Activities/Fragments según necesites
3. Implementar lógica de navegación con controles remotos
4. Agregar reproducción de video si es necesario
5. Configurar signing para release

## Solución de problemas

### "SDK not found"
```bash
export ANDROID_SDK_ROOT=/ruta/a/tu/sdk
export PATH=$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$PATH
```

### "gradle command not found"
```bash
# Usar el wrapper incluido
./gradlew en lugar de gradle
```

### Build falla
```bash
./gradlew clean
./gradlew assembleDebug --stacktrace
```
