# Registro de decisiones técnicas

## ADR-001 — Storage Access Framework

Se eligió SAF con permisos persistentes porque preserva control del usuario, funciona con proveedores de documentos y evita `MANAGE_EXTERNAL_STORAGE`. Todas las referencias persistidas son `content://`.

## ADR-002 — Ediciones en Room

Los cambios de metadatos se guardan en Room. No se incorporó una biblioteca de escritura ID3 antigua o insegura. El archivo de audio permanece intacto salvo renombrado o borrado explícito.

## ADR-003 — Servicio Media3

ExoPlayer vive en un `MediaSessionService`. Un controlador singleton conecta la UI. Cerrar la Activity no detiene la reproducción; audio focus y desconexión de auriculares se delegan a Media3/ExoPlayer.

## ADR-004 — Descarga directa

OkHttp no sigue redirecciones automáticamente. La aplicación sigue como máximo cinco, vuelve a validar HTTPS y solo acepta MIME/extensiones de audio reconocidas. WorkManager aporta restricciones de red, cancelación, reintentos y notificación foreground.

## ADR-005 — Proveedores externos explícitos

La exploración externa depende de `AuthorizedMusicProvider`, separado de la búsqueda Room. Cada
pista declara proveedor, licencia, permiso de descarga y atribución. YouTube obtiene sus metadatos
públicos mediante yt-dlp y entrega un plan `YtDlp` que exige una acción explícita, confirmación de
metadatos y autorización del usuario.

## ADR-006 — Backup sin audio

El backup es un ZIP con un JSON versionado. Conserva referencias y metadatos, pero no audio ni permisos SAF, porque esos permisos deben volver a concederse mediante el selector oficial después de reinstalar.

## ADR-007 — Texto inmediato y consulta debounced

El texto del buscador no se deriva del resultado de Room. Se publica inmediatamente para mantener estable el `TextField`; `trim` y el debounce de 300 ms se aplican únicamente al flujo que llega al repositorio. Esto evita que Compose restaure un valor anterior mientras el usuario escribe.

## ADR-008 — Búsqueda de YouTube mediante yt-dlp

Para un uso personal y una distribución limitada se evita incluir una API key en el APK. El proveedor
de Explorar usa el mismo `YtDlpExtractor` local que Descargas, con páginas de 20 resultados, perfiles
públicos de recuperación y validación de URLs HTTPS. La búsqueda puede verse afectada por cambios,
verificaciones anti-bot o bloqueos públicos de YouTube; no se incorporan cookies, sesiones ni un
backend para eludir esas restricciones.

## ADR-009 — Descarga y adelantos mediante el flujo compartido de yt-dlp

`resolveDownload` devuelve un plan `YtDlp`; la URL HTTPS se inspecciona y la descarga solo se encola
después de confirmar los metadatos. Chaquopy evita un backend y WorkManager mantiene progreso,
cancelación y reintento. Se eligió audio preexistente sin FFmpeg para no empaquetar binarios
adicionales. El adelanto usa una pista HTTPS temporal en Media3 y no altera la biblioteca.
