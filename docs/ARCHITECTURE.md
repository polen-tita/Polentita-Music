# Arquitectura

Polentita Music usa un único módulo Android para mantener simple el MVP, con límites de paquete explícitos.

```text
Compose UI
  └─ ViewModels (estado inmutable)
      ├─ MusicRepository
      │   ├─ Room DAOs
      │   └─ LibraryStorage (SAF / MediaStore)
      ├─ PlaybackController
      │   └─ MediaController → MediaSessionService → ExoPlayer
      ├─ DownloadCoordinator
      │   ├─ WorkManager → OkHttp → temporal → SAF
      │   └─ WorkManager → Chaquopy → yt-dlp → temporal → SAF
      ├─ AuthorizedMusicProvider
      │   └─ yt-dlp → metadatos/referencia externa → Room
      │       └─ autorización explícita → yt-dlp/WorkManager → SAF + Room
      └─ BackupManager
          └─ Room + DataStore → ZIP/JSON
```

## Paquetes

- `core/database`: entidades, índices, claves foráneas, DAOs y base Room.
- `core/storage`: preferencias DataStore, SAF, escaneo MediaStore y backup.
- `core/network`: inspección segura de enlaces directos.
- `core/designsystem`: tema Material 3 y componentes reutilizables.
- `data/repository`: implementación del repositorio musical.
- `data/downloader`: coordinación y workers recuperables de descarga directa y `yt-dlp`.
- `data/extractor`: puente Kotlin/Python y validación de resultados de `yt-dlp`.
- `data/provider`: registro del proveedor autorizado y búsqueda de YouTube mediante yt-dlp.
- `domain/model`, `domain/repository` y `domain/provider`: modelos y contratos independientes de la UI.
- `feature/*`: pantallas y ViewModels por función.
- `playback/*`: conversión de cola, controlador de sesión y servicio.

## Flujo de importación

1. El selector devuelve uno o varios `content://`.
2. Se inspecciona MIME/extensión y se extraen metadatos.
3. Se calcula SHA-256 por streaming.
4. Room detecta el checksum existente.
5. El audio se copia a un documento `.part` dentro de `Imports/`.
6. Se renombra al nombre final, se extrae la portada y se crea la entidad.
7. La consulta `Flow` actualiza Compose.

## Reproducción

La Activity nunca posee ExoPlayer. `PlaybackService` mantiene ExoPlayer y `MediaSession`; la UI usa un `MediaController`. Esto permite notificación multimedia, pantalla bloqueada, botones de auriculares/Bluetooth, audio focus y reproducción con pantalla apagada. DataStore conserva IDs, índice, posición, repetición y aleatorio.

## Datos y escalabilidad

Las columnas usadas para búsqueda, joins y orden tienen índices. Las listas visuales usan `LazyColumn`, `LazyRow` y `LazyVerticalGrid`; ninguna copia o descarga carga el audio completo en memoria. Las portadas se cargan y redimensionan mediante Coil.

## Búsqueda

`LibraryViewModel` mantiene el texto inmediato en un `MutableStateFlow`; para canciones aplica `trim`, `debounce` y `distinctUntilChanged` solo antes de consultar Room, y la misma consulta filtra álbumes y artistas. La pantalla **Buscar** contiene únicamente **Explorar**, que muestra directamente el proveedor YouTube mediante `AuthorizedMusicProvider`. Los resultados de Explorar quedan en estado del ViewModel y no se vuelven a consultar por cambios de Room, navegación o finalización de una descarga: solo cambian al modificar la búsqueda o pulsar **Otra mezcla**.

`YouTubeAuthorizedMusicProvider` usa `YtDlpExtractor.search` para obtener páginas de resultados de
20 elementos. La página siguiente se representa como un índice numérico interno y el puente aplica
los mismos perfiles públicos de recuperación que las descargas. El proveedor publica metadatos,
referencias guardadas localmente y un plan `YtDlp` para resultados descargables bajo autorización
explícita. Explorar usa ese plan para inspeccionar y confirmar metadatos antes de delegar en el mismo
worker recuperable de Descargas. Los adelantos resuelven una pista HTTPS temporal y la reproducción
no se registra en Room ni en la cola persistente.

## Extracción local con yt-dlp

`YtDlpDownloadWorker` solicita a `ChaquopyYtDlpExtractor` que ejecute
`main/python/yt_dlp_bridge.py`. La inspección no descarga. La transferencia selecciona audio
preseparado, conserva `.part`, limita tamaño/reintentos y reporta progreso a WorkManager. Antes de
copiar a SAF se verifica reproducción, extensión, MIME y checksum. Room solo recibe la canción
cuando la copia final existe.
