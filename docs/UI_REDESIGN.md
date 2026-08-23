# Rediseño visual Hi‑Fi de Polentita Music

## Objetivo

Actualizar exclusivamente la capa visual de Polentita Music con una estética oscura,
cinematográfica y centrada en las portadas. Los flujos de reproducción, Room,
importación, descargas, búsqueda, playlists y Storage Access Framework se mantienen
sin cambios, salvo las conexiones de estado que necesiten los nuevos componentes
Compose.

No se modifica el esquema de Room ni las rutas de navegación existentes.

## Principios visuales

- Fondo base casi negro, optimizado para pantallas AMOLED.
- Portadas como elemento principal y fuente de color contextual.
- Superficies oscuras y translúcidas, con degradados y sombras discretas.
- Controles amplios, legibles y con áreas táctiles de al menos 48 dp.
- Tipografía clara, elipsis para textos largos y contraste verificable.
- Cuadrículas compactas y listas con información jerarquizada.
- Movimiento breve y funcional, respetando la reducción de animaciones del sistema.

## Componentes previstos

- `PolentitaSpacing`, `PolentitaRadii`, `PolentitaCoverSize`, `PolentitaOpacity` y
  `PolentitaMotion`: tokens reutilizables del sistema visual.
- `ArtworkPalette`: colores semánticos extraídos de una portada.
- `ArtworkPaletteExtractor`: análisis reducido, asíncrono y con caché por URI.
- `rememberArtworkPalette`: puente estable entre portadas y Compose.
- `ArtworkDynamicTheme`: transición animada del esquema cromático contextual.
- `AdaptiveArtworkBackground`: fondo desenfocado, overlay oscuro y degradados.
- `ArtworkOrFallback`: portada o gradiente determinista para canciones sin imagen.
- Componentes Hi‑Fi compartidos para botones, tarjetas, filas y encabezados.

## Rendimiento

- Las imágenes usadas para analizar color se decodifican como miniaturas.
- El análisis se ejecuta fuera del hilo principal.
- La paleta se almacena en una caché LRU en memoria y no se persiste en Room.
- Una misma `coverUri` no se vuelve a analizar durante recomposiciones.
- Los fondos no solicitan la portada a resolución completa.
- El progreso del reproductor se aísla en componentes pequeños.

## Accesibilidad

- Los estados activos se expresan con texto, iconografía o semántica además de color.
- Los controles conservan descripciones de contenido y tamaño táctil suficiente.
- Los colores de texto se seleccionan mediante contraste calculado.
- Las animaciones se reducen cuando el sistema deshabilita los animadores.
- La estructura conserva compatibilidad con TalkBack y escalado de fuente.

## Fases y estado

| Fase | Alcance | Estado |
| --- | --- | --- |
| 1 | Sistema de diseño, paleta, fondo adaptativo y mini reproductor | Implementada |
| 2 | Reproductor completo | Implementada |
| 3 | Detalle de álbum y artista | Implementada |
| 4 | Biblioteca, inicio y navegación inferior | Implementada |
| 5 | Movimiento, accesibilidad, pruebas y ajustes | Implementada |

## Validación prevista

Se ejecutarán, de forma secuencial:

```bash
./gradlew testDebugUnitTest --no-daemon --max-workers=2
./gradlew assembleDebug --no-daemon --max-workers=2
```

Pruebas visuales unitarias previstas:

- selección de colores desde una portada;
- fallback determinista cuando no existe portada;
- contraste mínimo de texto;
- estabilidad de color para una misma canción;
- estado visual y semántico de la canción activa.

## Registro de implementación

- 2026-07-24: se crea este documento antes de modificar componentes visuales.
- 2026-07-24: se implementan tokens, tipografía, formas, paleta dinámica, caché LRU,
  miniaturas de análisis de 128 px y fondos de 320 px.
- 2026-07-24: se rediseñan mini reproductor, reproductor completo, álbum, artista,
  Biblioteca, Inicio y navegación inferior sin cambiar rutas ni el esquema Room.
- 2026-07-24: el progreso del reproductor y el área inferior se aíslan para evitar
  recomposiciones del árbol global.
- 2026-07-24: `testDebugUnitTest` finaliza con 59 pruebas aprobadas y
  `assembleDebug` finaliza correctamente. No se ejecutó lint por indicación expresa.

## Estado final y límites

- En Android 12 o posterior el fondo utiliza desenfoque acelerado. En versiones
  anteriores conserva el overlay y los degradados, sin intentar un blur costoso.
- Los colores se conservan solo en memoria; se recalculan después de reiniciar el
  proceso, pero nunca durante recomposiciones de una misma sesión.
- No hay letras sincronizadas en el modelo actual, por lo que el reproductor no
  inventa contenido de letras.
- El acceso de salida abre los ajustes Bluetooth del sistema. Un selector de rutas
  embebido requeriría integrar MediaRouter en una iteración posterior.
- La revisión visual final en hardware real queda pendiente para ajustar densidad,
  blur y recortes con portadas reales del usuario.

## Correcciones 0.5.1

- El fondo del `Scaffold` vuelve a estar ligado al `ColorScheme`; ya no se combina
  un fondo negro hardcodeado con contenido de un tema claro.
- Los temas de portada solo aplican la paleta a fondos, superficies y acentos.
  `LocalContentColor`, `onBackground`, `onSurface`, `onSurfaceVariant` y colores
  deshabilitados usan tonos semánticos con contraste seguro.
- Las superficies Material 3 adicionales (`surfaceContainer*`) también se
  oscurecen dentro del tema de portada para mantener legibles menús y diálogos.
- Biblioteca conserva su título en una línea: Descargar y Agregar son iconos, y
  vista/refresco se agrupan en el menú de tres puntos.
- La búsqueda yt-dlp elimina la restricción heredada `playlist_items = "1"` y
  publica 10 resultados por página con deduplicación y paginación.
- Se sustituye el icono anterior por recursos legacy, round, adaptive y
  monocromático derivados de `branding/app_icon_master.png`.
- Validación final: 62 pruebas aprobadas y `assembleDebug` correcto.
