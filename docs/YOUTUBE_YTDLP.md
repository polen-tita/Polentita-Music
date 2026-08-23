# YouTube y yt-dlp: guía educativa

Explorar, Descargas y los adelantos comparten el extractor local de `yt-dlp`.

## 1. Búsqueda de YouTube

Archivos principales:

- `data/provider/YouTubeAuthorizedMusicProvider.kt`: convierte resultados de yt-dlp a `RemoteTrack`.
- `data/provider/AuthorizedProviderRegistry.kt`: publica el proveedor a la pestaña Explorar.
- `data/extractor/YtDlpExtractor.kt`: búsqueda paginada y validación de URLs.
- `main/python/yt_dlp_bridge.py`: búsqueda pública mediante `ytsearch`, sin descargar audio.
- `feature/search/SearchViewModel.kt`: debounce, estados y referencias.
- `feature/search/SearchScreen.kt`: búsqueda directa, licencia, adelanto y referencias guardadas.
- `core/database/RemoteReferenceDao.kt` y `RemoteReferenceEntity`: persistencia local.

No hay backend ni API key. La búsqueda usa `ytsearch` con extracción plana y `skip_download`; cada
página contiene hasta 20 resultados y el siguiente índice se solicita solo al pulsar **Cargar más
resultados**. Los resultados se validan como URLs HTTPS antes de llegar a Compose.

## 2. Semántica de allowsDownload

Hay dos comprobaciones:

```text
provider.allowsDownload
        AND
track.externalUrl válido
        ↓
track.allowsDownload / plan de descarga autorizado
```

Si el resultado es `false`, la UI informa que no se permite descargar. Si es `true`, pulsar el botón
llama a `AuthorizedMusicProvider.resolveDownload(track)`. Solo un
`Result.success(AuthorizedDownload)` inicia el worker `yt-dlp`. El resultado de
YouTube devuelve un plan `YtDlp`; Explorar inspecciona sus metadatos, solicita confirmación y luego
usa el mismo flujo de Descargas.

El booleano por sí solo nunca descarga un video: el plan de YouTube siempre pasa por inspección,
confirmación de metadatos y el worker recuperable.

## 3. URL autorizada con yt-dlp

Archivos principales:

- `gradle/libs.versions.toml`, `build.gradle.kts` y `app/build.gradle.kts`: Chaquopy y versión fijada
  de `yt-dlp`.
- `main/python/yt_dlp_bridge.py`: inspección y extracción real con la API Python de `yt-dlp`.
- `data/extractor/YtDlpExtractor.kt`: puente tipado, HTTPS, confinamiento de ruta y errores
  redactados.
- `data/downloader/YtDlpDownloadWorker.kt`: foreground, progreso, validación, checksum, SAF y Room.
- `data/downloader/DownloadCoordinator.kt`: alta, cancelación y reintento.
- `feature/downloads/DownloadsViewModel.kt` y `DownloadsScreen.kt`: análisis y
  metadatos editables.

Secuencia para una URL de YouTube u otro sitio compatible:

```text
URL HTTPS
  → inspect_media (sin descargar)
  → usuario revisa metadatos
  → WorkManager
  → yt-dlp selecciona bestaudio M4A/WebM
  → temporal privado / .part
  → validación de audio + SHA-256
  → SAF: Downloads/
  → SongEntity(sourceType = DOWNLOADED)
  → Biblioteca / reproductor
```

Para escuchar un adelanto, `YtDlpExtractor` resuelve una pista de audio HTTPS sin descargarla al
almacenamiento. Media3 la reproduce durante 30 segundos y después restaura la cola local; el adelanto
no se registra en Room ni en el historial. La misma acción está disponible en los resultados de
Descargas y no abre una aplicación externa. Las referencias se guardan en `remote_references` y se
muestran en Explorar hasta que el usuario las quite.

En YouTube, el puente fija `yt-dlp==2026.8.19` y comienza por el cliente público `visionos`. Si no ofrece un
formato reproducible, prueba `android`, `android_vr`, `web_embedded` y `tv`. Son perfiles sin cookies ni sesión; `web_embedded` solo
funciona con contenido que permita reproducción incrustada. Esto mejora la compatibilidad, pero no
puede eliminar un bloqueo temporal de la red ni una verificación que exija cuenta, cookies o un
token de origen.

La inspección y el adelanto conservan el fallback de hasta cinco URLs públicas alternativas por título y artista,
además de los perfiles de cliente de yt-dlp. El límite global de 90 segundos evita que la pantalla quede esperando
indefinidamente cuando la red está bloqueada; si se agotan los intentos, se muestra un error recuperable.

Esta capacidad técnica no concede derechos sobre el contenido ni anula las condiciones del
servicio. Debe utilizarse con material propio, de dominio público o con autorización explícita.

## 4. Lo que no se implementó

- scraping HTML propio;
- `youtube-dl`;
- cookies, autenticación o elusión de restricciones;
- FFmpeg y conversión a MP3;
- un runtime JavaScript ejecutable dentro de Android: la APK no incorpora Deno, Node ni QuickJS;
  por eso el puente no selecciona clientes que dependan de EJS y conserva el fallback sin runtime;
- API key ni llamadas a la YouTube Data API.

Referencias:

- [Políticas para desarrolladores de YouTube](https://developers.google.com/youtube/terms/developer-policies)
- [README de yt-dlp](https://github.com/yt-dlp/yt-dlp/blob/master/README.md)
- [Chaquopy para Android](https://chaquo.com/chaquopy/doc/current/android.html)
