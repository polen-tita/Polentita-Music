# Guía de continuidad para Codex

Este documento resume el estado real de Polentita Music para que un chat nuevo
pueda continuar el trabajo sin volver a inspeccionar todo el repositorio. Debe
leerse junto con [`AGENTS.md`](../AGENTS.md), que contiene las reglas obligatorias
de contribución.

## Punto de partida actual

- Repositorio: `/home/polentita/Polentita-Music`.
- Aplicación Android: `Polentita Music`.
- Paquete: `com.polentita.music`.
- Módulo único: `app`.
- Versión actual: `0.5.9`, `versionCode = 14`.
- Base Room: versión 3.
- APK debug:
  `app/build/outputs/apk/debug/app-debug.apk`.
- SHA-256 de la APK 0.5.9:
  `c6538331aab6c836a8f5c099bd117dc940d7b5c1e03f84246a272506c4b328c4`.
- La APK 0.5.9 fue instalada el 24 de julio de 2026 mediante
  `adb install -r` en un Samsung Galaxy S20 FE.
- Dispositivo usado en la última instalación:
  `192.168.100.92:39129`. Esta dirección ADB por Wi-Fi puede cambiar.
- La última ejecución de `testDebugUnitTest` aprobó 84 pruebas.
- La última ejecución de `assembleDebug` terminó correctamente.
- No se ejecutó lint en la última iteración porque el usuario pidió ejecutar
  únicamente esas dos tareas.

La aplicación ya fue probada físicamente durante su desarrollo. Funcionan
importación, reproducción en segundo plano, controles multimedia, edición,
favoritos, álbumes, playlists, búsqueda local, descargas directas y con
`yt-dlp`, búsqueda de YouTube, TikTok mediante URL, persistencia de cola,
portadas descargadas y cierre opcional del servicio al quitar la app de
recientes.

## Instrucción importante para próximos cambios

La aplicación es funcional. Ante una petición puntual:

1. leer `AGENTS.md` y esta guía;
2. inspeccionar solamente los archivos relacionados;
3. preservar los datos existentes;
4. no cambiar Room salvo petición expresa y migración real;
5. no usar `fallbackToDestructiveMigration`;
6. no desinstalar la aplicación ni ejecutar `pm clear`;
7. instalar actualizaciones con `adb install -r`;
8. no reestructurar el proyecto para una corrección localizada;
9. ejecutar únicamente las tareas de validación pedidas por el usuario;
10. no afirmar que algo fue probado si solo compiló.

## Qué es el proyecto

Polentita Music es un reproductor Android personal, local-first y offline-first
para audio. No tiene cuentas, anuncios, analytics ni backend. La biblioteca
musical permanece en una carpeta elegida mediante Storage Access Framework.
Las referencias persistentes son `content://`; no se depende de rutas absolutas
y no se solicita `MANAGE_EXTERNAL_STORAGE`.

Tecnologías principales:

- Kotlin y JVM 17;
- Gradle Kotlin DSL;
- Jetpack Compose y Material 3;
- Navigation Compose;
- MVVM, Repository, Coroutines y Flow;
- Room;
- Hilt;
- Media3, ExoPlayer, `MediaSession` y `MediaSessionService`;
- DataStore;
- WorkManager;
- OkHttp;
- Coil 3;
- Chaquopy con Python 3.13 y `yt-dlp`;
- JUnit, Robolectric, MockK y Turbine.

Configuración Android:

- `compileSdk = 36`;
- `targetSdk = 36`;
- `minSdk = 26`;
- ABI empaquetada: `arm64-v8a`;
- dispositivo principal: Samsung Galaxy S20 FE.

Las versiones se centralizan en
[`gradle/libs.versions.toml`](../gradle/libs.versions.toml).

## Estructura de la raíz

```text
Polentita-Music/
├── AGENTS.md
├── README.md
├── LICENSE
├── app/
├── branding/
├── docs/
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── local.properties
├── local.properties.example
├── gradlew
├── gradlew.bat
└── serve-apk.sh
```

- `AGENTS.md`: reglas de seguridad, arquitectura y calidad.
- `README.md`: instrucciones de uso, compilación y limitaciones públicas.
- `app/`: módulo Android y todo el código funcional.
- `branding/`: fuentes maestras del icono de la aplicación.
- `docs/`: arquitectura, decisiones, backup, yt-dlp y diseño visual.
- `gradle/`: catálogo de versiones y Gradle Wrapper.
- `local.properties`: SDK y clave local de YouTube. Está ignorado y nunca debe
  publicarse.
- `serve-apk.sh`: servidor HTTP local que comparte exclusivamente la carpeta de
  la APK por el puerto 8000.

## Estructura del módulo Android

```text
app/
├── build.gradle.kts
├── schemas/
└── src/
    ├── main/
    │   ├── AndroidManifest.xml
    │   ├── assets/
    │   ├── java/com/polentita/music/
    │   ├── python/
    │   └── res/
    ├── test/
    └── androidTest/
```

### Entrada de la aplicación

- `MainActivity.kt`: raíz Compose, grafo de navegación, navegación inferior,
  mini reproductor y aplicación del tema global.
- `PolentitaApplication.kt`: `Application` de Hilt y configuración de
  WorkManager.
- `AndroidManifest.xml`: permisos, Activity `singleTop`, servicio Media3,
  WorkManager y FileProvider.

Rutas Compose principales:

- `home`;
- `library`;
- `search`;
- `playlists`;
- `settings`;
- `album/{albumId}`;
- `artist/{artist}`;
- `playlist/{playlistId}`;
- `player`;
- `queue`;
- `import`;
- `downloads/new`;
- `downloads/history`, conservada por compatibilidad aunque la interfaz actual
  ya no muestra una pestaña separada de historial;
- `song-editor/{songId}`;
- `technical/{songId}`;
- `about`.

## Paquetes Kotlin y responsabilidad

### `core/common`

- `FileChecksums.kt`: SHA-256 por streaming.
- `FileSafety.kt`: formatos permitidos, validación y sanitización de nombres.
- `TimeFormat.kt`: duración y tamaños legibles.

No cargar archivos de audio completos en memoria.

### `core/database`

- `Entities.kt`: entidades Room y enums persistidos.
- `SongDao.kt`: biblioteca, búsqueda, filtros y consultas observables.
- `AlbumPlaylistDaos.kt`: álbumes, playlists y transacciones de orden.
- `DownloadHistoryDaos.kt`: descargas e historial de reproducción.
- `RemoteReferenceDao.kt`: referencias externas guardadas.
- `BackupDao.kt`: exportación y restauración de conjuntos de datos.
- `PolentitaDatabase.kt`: base Room versión 3.
- `DatabaseMigrations.kt`: migraciones reales 1→2 y 2→3.

Entidades actuales:

- `SongEntity`;
- `AlbumEntity`;
- `PlaylistEntity`;
- `PlaylistSongCrossRef`;
- `DownloadEntity`;
- `PlaybackHistoryEntity`;
- `RemoteReferenceEntity`.

La base se llama `polentita.db`. `AppModule.kt` registra explícitamente
`MIGRATION_1_2` y `MIGRATION_2_3`. Si se cambia una entidad:

1. aumentar la versión;
2. crear migración preservando datos;
3. exportar el esquema nuevo en `app/schemas`;
4. añadir prueba de migración;
5. jamás usar una migración destructiva.

### `core/designsystem`

- `DesignTokens.kt`: espaciados, radios, portadas, opacidades y duraciones.
- `Theme.kt`: tema oscuro/claro/sistema y ambiente global de la portada.
- `ArtworkPalette.kt`: análisis, contraste y fallback determinista.
- `ArtworkPaletteExtractor.kt`: decodificación reducida fuera del hilo
  principal y caché LRU compartida.
- `ArtworkDynamicTheme.kt`: tema local para componentes destacados.
- `AdaptiveArtworkBackground.kt`: fondo desenfocado con overlay y fallback.
- `Components.kt`: portadas, filas, menús y mini reproductor reutilizables.

Desde la versión 0.5.8, la paleta de la canción actual se aplica a toda la aplicación. La
raíz observa `PlayerViewModel.visualState`, que excluye posición y duración para
evitar recomponer todo el árbol cada 500 ms. El color afecta fondos, superficies,
contenedores y acentos; `onBackground`, `onSurface` y otros colores de contenido
conservan contraste seguro.

El ajuste DataStore `adaptiveArtworkTheme`:

- se muestra como “Tema adaptativo según la canción”;
- está activado por defecto;
- es independiente de `dynamicColor`;
- vuelve al tema normal al desactivarse;
- se incluye en el backup.

No persistir colores de portada en Room ni crear un `ImageLoader` por pantalla.

### `core/di`

- `AppModule.kt`: Room, DAOs, OkHttp y enlace de `MusicRepository`.
- `ExtractorModule.kt`: enlace de la implementación de `yt-dlp`.

### `core/network`

- `DirectDownloadInspector.kt`: valida URL HTTPS, redirecciones, MIME, nombre,
  tamaño y soporte Range.
- `RemoteCoverDownloader.kt`: descarga y valida JPEG, PNG o WebP con límite de
  tamaño.

No habilitar HTTP en claro, no seguir redirecciones sin volver a validar y no
registrar URLs completas con tokens.

### `core/storage`

- `PreferencesStore.kt`: DataStore de preferencias y snapshot de reproducción.
- `LibraryStorage.kt`: SAF, carpetas, streaming, metadatos, portada y operaciones
  sobre documentos.
- `DeviceMusicScanner.kt`: escaneo opcional de MediaStore.
- `BackupManager.kt`: ZIP con JSON versionado, sin audio.

Estructura creada dentro del árbol SAF:

```text
Polentita Music/
├── Music/
├── Covers/
├── Imports/
└── Downloads/
```

Preferencias importantes:

- URI del árbol SAF;
- tema oscuro/claro/sistema;
- color dinámico del sistema;
- tema adaptativo según la portada;
- restaurar cola;
- detener reproducción al quitar la app de recientes;
- pausar al desconectar auriculares;
- descargas solo con Wi-Fi;
- comportamiento de borrado.

### `domain`

- `domain/model/Models.kt`: modelos usados fuera de Room y conversiones.
- `domain/repository/MusicRepository.kt`: contrato de biblioteca, importación,
  álbumes, playlists, escaneo e historial.
- `domain/provider/AuthorizedMusicProvider.kt`: contrato de proveedores
  externos, `RemoteTrack`, licencia, atribución y descarga autorizada.

Los contratos no deben depender de Compose.

### `data/repository`

- `DefaultMusicRepository.kt`: implementación principal contra Room y SAF.
- `RemoteReferenceRepository.kt`: referencias externas guardadas localmente.

Aquí se coordinan duplicados, transacciones, disponibilidad y actualización de
Room. Los ViewModels no deben acceder directamente al sistema de archivos.

### `data/downloader`

- `DownloadCoordinator.kt`: crea, cancela y reintenta trabajos WorkManager.
- `DirectDownloadWorker.kt`: descarga HTTPS directa con temporal, Range,
  validación y posterior importación.
- `YtDlpDownloadWorker.kt`: descarga/extracción local iniciada por el usuario.
- `DownloadedAudioPreparer.kt`: valida el audio y prepara su incorporación.
- `DownloadedCoverResolver.kt`: conserva miniatura en `Covers/`, pero nunca
  impide completar el audio si falla.
- `DownloadResumePolicy.kt`: decide reanudación o reinicio.
- `DownloadStateTransitions.kt`: transiciones válidas de estados.

Cerrar el servicio de reproducción no debe cancelar WorkManager ni las
descargas activas.

### `data/extractor` y `src/main/python`

- `YtDlpExtractor.kt`: contrato Kotlin, modelos e integración Chaquopy.
- `yt_dlp_bridge.py`: invoca `yt-dlp` para inspección, búsqueda y descarga.

El paquete fijado actualmente es `yt-dlp==2026.8.19`.

El flujo admite URLs HTTPS compatibles, incluidas YouTube y TikTok. Para YouTube se prueba primero el cliente
público `visionos`, seguido por `android`, `android_vr`, `web_embedded` y `tv` cuando el anterior es rechazado. La APK
fuerza IPv4 para mantener consistente la URL firmada entre extracción y reproducción, y no incluye cookies,
cuentas, scraping propio, `youtube-dl`, FFmpeg ni conversión a MP3. Se conserva una pista de audio compatible,
normalmente M4A o WebM.

La inspección y el preview aplican hasta cinco alternativas públicas por título y artista, con un límite global de
90 segundos en Search. Cuando el proveedor rechaza temporalmente una URL, `YtDlpSourceResolver` continúa con la
siguiente alternativa; si todas fallan, expone un mensaje recuperable para reintentar más tarde.

Si vuelve a aparecer “Sign in to confirm you're not a bot”, no se debe ocultar
el error ni incorporar cookies personales al repositorio. Revisar primero
`yt_dlp_bridge.py`, la versión fijada de `yt-dlp` y los clientes soportados.

### `data/provider`

- `AuthorizedProviderRegistry.kt`: lista los proveedores disponibles.
- `YouTubeAuthorizedMusicProvider.kt`: búsqueda paginada mediante `YtDlpExtractor`, referencias y
  planes `YtDlp` para descargas autorizadas desde Explorar.

Explorar no necesita una API key: reutiliza la búsqueda pública de yt-dlp y sus mismos perfiles de
recuperación que Descargas.

### `feature/app`

`AppViewModel.kt` observa DataStore y publica preferencias para la raíz Compose.

### `feature/home`

- `HomeViewModel.kt`: secciones observables no vacías.
- `HomeScreen.kt`: continuar escuchando, recientes, favoritas, álbumes,
  playlists y más reproducidas.

La tarjeta activa muestra progreso y continuar/pausar sin reiniciar
innecesariamente la canción.

### `feature/library`

- `LibraryViewModel.kt`: importación, escaneo, CRUD, álbumes y playlists.
- `LibraryScreen.kt`: configuración SAF, Biblioteca e Importación.

Biblioteca tiene Canciones, Álbumes y Artistas; permite lista/cuadrícula,
acciones de agregar/descargar y menú secundario. Eliminar un álbum solo elimina
`AlbumEntity`, deja las canciones sin álbum y no borra audios. Existe “Limpiar
álbumes vacíos”.

### `feature/search`

- `SearchViewModel.kt`: query local reactivo, debounce, filtros, orden y
  proveedores.
- `SearchScreen.kt`: pestañas “Mi biblioteca” y “Explorar”.

La búsqueda local usa Room, coincidencias parciales y no distingue mayúsculas.
Explorar mantiene el origen visible y no mezcla resultados.

### `feature/downloads`

- `DownloadsViewModel.kt`: inspección, búsqueda, paginación, trabajos y canción
  terminada.
- `DownloadsScreen.kt`: pantalla única de descargas.

Interfaz actual:

- pestaña “Buscar”: búsqueda de YouTube mediante `yt-dlp`, 10 resultados por
  página, reintento y carga adicional;
- pestaña “Pegar enlace”;
- método “Archivo directo”;
- método “Descargar con yt-dlp”;
- formulario de yt-dlp con el título compacto “Descargar audio”;
- selector/autocompletado de álbum existente;
- progreso, cancelación, reintento e importación automática.

La sección visual separada “Nueva / Historial” fue eliminada a petición del
usuario. Las entidades y funciones internas del historial se conservan porque
son útiles para estados de descargas y no dañan la interfaz.

### `feature/editor`

- `SongEditorViewModel.kt`: edición de metadatos Room.
- `EditorScreens.kt`: editor de canción, detalles técnicos, álbum y artista.

No escribir etiquetas ID3 en el archivo; la edición inicial es local en Room.

### `feature/playlist`

- `PlaylistViewModel.kt`: agregar, quitar, mover, editar y eliminar.
- `PlaylistScreens.kt`: playlists automáticas y manuales, detalle y
  reordenamiento.

El orden se conserva en `PlaylistSongCrossRef.position`.

### `feature/player`

- `PlayerViewModel.kt`: fachada de comandos y estado Media3.
- `PlayerScreens.kt`: reproductor completo y cola.

`PlayerViewModel.visualState` elimina progreso, duración y buffer para usos
visuales globales. `state` completo sí se usa en el progreso del reproductor y
mini reproductor.

### `feature/settings`

- `SettingsViewModel.kt`: modifica DataStore, SAF, backup y caché.
- `SettingsScreen.kt`: apariencia, biblioteca, reproducción, descargas, backup
  y acerca de.

### `playback`

- `queue/MediaItemMapper.kt`: convierte `Song` a `MediaItem`, incluido
  `artworkUri`.
- `session/PlaybackController.kt`: conexión singleton al `MediaController` y
  comandos desde UI.
- `session/PlaybackSessionActivity.kt`: `PendingIntent` que abre `MainActivity`.
- `service/PlaybackService.kt`: ExoPlayer, MediaSession, notificación oficial y
  persistencia.
- `service/PlaybackShutdownCoordinator.kt`: cierre ordenado al quitar la app de
  recientes.

El reproductor vive siempre en `MediaSessionService`, no en la Activity.
`PlaybackService` usa el proveedor oficial de notificación Media3 y una
`sessionActivity`. Al cerrar desde recientes, si el ajuste está activo:

1. persiste cola y posición;
2. detiene reproducción;
3. limpia la cola activa;
4. quita el foreground;
5. libera la sesión;
6. detiene el servicio.

Al iniciar otra vez, `PlaybackController.ensureConnected()` permite reconectar
sin necesitar abrir dos veces la aplicación. No usar `System.exit` ni
`killProcess`.

## Flujo resumido de datos

```text
Compose
  → ViewModel
    → contratos domain
      → repository / coordinator / provider
        → Room + DataStore + SAF + WorkManager
```

Reproducción:

```text
Compose
  → PlayerViewModel
    → PlaybackController
      → MediaController
        → PlaybackService
          → MediaSession + ExoPlayer
```

Importación:

```text
OpenMultipleDocuments
  → LibraryViewModel
    → DefaultMusicRepository
      → metadatos + SHA-256
        → temporal SAF
          → archivo final + portada
            → Room
              → Flow actualiza Compose
```

Descarga yt-dlp:

```text
URL HTTPS
  → DownloadsViewModel
    → YtDlpExtractor / inspección
      → DownloadCoordinator
        → WorkManager
          → Chaquopy + yt-dlp
            → temporal privado
              → validación + checksum + portada
                → SAF Downloads/Covers
                  → Room
```

## Pruebas

Pruebas locales: `app/src/test/java/com/polentita/music`.

Cobertura relevante:

- DAOs y migraciones;
- búsqueda parcial, filtros y query vacío;
- repositorio e importación descargada;
- sanitización, checksum y validación HTTPS;
- reanudación y estados de descarga;
- portadas descargadas;
- proveedor YouTube;
- seguridad de yt-dlp;
- ViewModels;
- mapeo `MediaItem`;
- cierre del servicio y reconexión;
- PendingIntent de sesión;
- backup;
- paleta, fallback, contraste y tema adaptativo global.

Prueba instrumental:

`app/src/androidTest/java/com/polentita/music/PolentitaFlowInstrumentedTest.kt`.

Comandos usuales:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  ./gradlew testDebugUnitTest --no-daemon --max-workers=2

JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  ./gradlew assembleDebug --no-daemon --max-workers=2
```

Solo ejecutar lint, pruebas instrumentales u otras tareas si el usuario lo pide
o si son necesarias y están dentro del alcance acordado. No lanzar varias
tareas Gradle simultáneamente.

## Instalación y distribución

ADB preservando datos:

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

ADB por Wi-Fi cambia de dirección después de reconexiones. Confirmar siempre con
`adb devices` antes de usar `-s`.

Compartir sin USB:

```bash
chmod +x serve-apk.sh
./serve-apk.sh
```

Abrir en el teléfono la URL que muestra el script. Detener con `Ctrl+C`. Android
debe permitir “Instalar aplicaciones desconocidas” para el navegador o gestor
que abre la APK.

Nunca desinstalar para actualizar: eso puede eliminar DataStore, Room y permisos
SAF. Usar `install -r`.

## Documentación complementaria

- [`ARCHITECTURE.md`](ARCHITECTURE.md): arquitectura general.
- [`DECISIONS.md`](DECISIONS.md): decisiones técnicas.
- [`BACKUP_FORMAT.md`](BACKUP_FORMAT.md): contrato del ZIP/JSON.
- [`YOUTUBE_YTDLP.md`](YOUTUBE_YTDLP.md): búsqueda, adelantos y descargas de YouTube mediante yt-dlp.
- [`UI_REDESIGN.md`](UI_REDESIGN.md): rediseño Hi-Fi y componentes visuales.

Esta guía es el documento más actualizado para estado, versión y continuidad.
Al existir una discrepancia histórica en otro documento, comprobar el código y
actualizar ambos.

## Limitaciones conocidas

- No se escriben etiquetas ID3 en los archivos.
- No se incluye FFmpeg ni conversión real a MP3.
- `yt-dlp` depende de cambios de los proveedores y puede dejar de resolver
  ciertos enlaces.
- No se incluyen cookies ni contenido que requiera iniciar sesión.
- Explorar no usa una API key; depende de la extracción pública de yt-dlp y puede verse afectado por
  cambios o bloqueos temporales de YouTube.
- Explorar puede preparar una descarga autorizada de YouTube con `yt-dlp` después
  de confirmar metadatos; Descargas y Explorar comparten el mismo worker.
- Solo se empaqueta `arm64-v8a`.
- El backup no incluye audio ni puede restaurar permisos SAF.
- Algunos proveedores SAF no permiten renombrar o borrar.
- No hay Paging 3; las listas son lazy y las consultas están indexadas.
- No hay selector MediaRouter embebido ni letras sincronizadas.

## Último cambio implementado

Versión 0.5.9:

- se eliminaron las transiciones del `NavHost` para que Inicio, Biblioteca,
  Buscar, Playlists y Ajustes cambien inmediatamente;
- el tema adaptativo conserva sus colores, pero ya no anima simultáneamente 19
  propiedades del `ColorScheme`;
- los esquemas Material base y adaptativo se memorizan y solo se reconstruyen
  cuando cambia realmente la portada o una preferencia;
- el mini reproductor observa `visualState`; su progreso de 500 ms se recoge en
  un subcomponente aislado y ya no recompone la navegación inferior;
- las portadas de las listas ya no dibujan sombras GPU; las portadas principales
  del reproductor y álbum conservan elevación;
- el desenfoque de fondos usa miniatura de 192 px, blur de 28 dp y transición
  corta;
- Biblioteca precalcula canciones por artista una sola vez en lugar de filtrar
  toda la biblioteca por cada elemento visible;
- se añadió una prueba que garantiza que actualizar solo posición, duración o
  buffer no emite un nuevo `PlayerViewModel.visualState`.

## Texto recomendado para iniciar un chat nuevo

Copiar este bloque y añadir la nueva petición:

```text
Trabaja en /home/polentita/Polentita-Music.
Lee primero AGENTS.md y docs/CODEX_HANDOFF.md. La versión actual es 0.5.9,
versionCode 14, Room v3 y ya funciona en un Samsung Galaxy S20 FE. No
reestructures el proyecto, no cambies Room, no borres datos y no desinstales la
app. Inspecciona solo los archivos relacionados con mi cambio. Para actualizar
el teléfono usa adb install -r.

Nueva petición:
[describir aquí el cambio]
```
