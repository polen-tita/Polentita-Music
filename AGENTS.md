# Polentita Music — reglas de contribución

## Contexto rápido para futuras sesiones

- La navegación inferior vigente es `home`, `library`, `search`, `playlists` y `settings`.
- `SearchScreen` muestra solo `Explore`; `LibraryScreen` concentra la búsqueda de canciones, álbumes y artistas.
- Las favoritas se sincronizan en la playlist reservada `Tus me gusta`; el acceso Favoritas de Inicio abre esa playlist.
- `Recientes` y `Más reproducidas` son colecciones dinámicas. Las playlists de usuario permiten reordenar canciones con pulsación larga y arrastre.
- Las descargas yt-dlp desde Buscar dejan el álbum vacío por defecto; no restablecer el valor `YouTube` automáticamente.
- `NetworkAccessPolicy` centraliza modo offline, conectividad y descargas solo con Wi‑Fi; no reemplazarlo por comprobaciones aisladas en Compose.
- `SquareArtworkProcessor` conserva la portada original y genera la representación cuadrada adaptativa usada por Compose y Media3.
- Playlists incluye la acción `Importar playlist`; el flujo funcional actual es la importación desde JSON, CSV o TXT estructurado.
- Para entregar: preservar datos del usuario, actualizar pruebas de lógica, compilar `app-debug.apk` e instalarlo con `adb install -r` si hay un dispositivo conectado.

### Control de versiones y punto de restauración

- El repositorio remoto privado del proyecto es `https://github.com/polen-tita/Polentita-Music.git`.
- La rama principal es `main` y contiene un punto de restauración funcional anterior al rediseño de interfaz.
- El commit base de restauración es `1b0101a` (`Snapshot funcional antes del rediseño de interfaz`).
- Antes de cambios visuales mayores, conservar este commit y crear nuevos commits descriptivos; no reescribir ni eliminar el historial publicado.
- El repositorio remoto no autoriza incluir credenciales: mantener fuera del control de versiones `local.properties`, tokens, claves API, logs del dispositivo, datos personales, cachés y artefactos generados no necesarios.
- Para recuperar el estado base, verificar primero los cambios locales y usar el commit `1b0101a` de forma explícita, preservando cualquier trabajo posterior del usuario.

## Alcance

- Aplicación Android local-first y offline-first escrita en Kotlin y Jetpack Compose.
- El paquete raíz es `com.polentita.music`.
- La biblioteca del usuario se accede exclusivamente mediante `content://` y Storage Access Framework.
- No solicitar `MANAGE_EXTERNAL_STORAGE`, no habilitar tráfico HTTP en claro y no incluir analytics.
- Nunca incorporar claves, tokens, canciones comerciales ni datos personales al repositorio.

## Arquitectura

- Un módulo `app` con MVVM, Repository, Room, Coroutines/Flow, Hilt y Media3.
- Mantener IO fuera del hilo principal.
- Las consultas observables de Room deben devolver `Flow`.
- Los ViewModels publican estados inmutables.
- Toda operación destructiva sobre archivos requiere una decisión explícita en UI.
- La reproducción vive en `MediaSessionService`, no en una Activity.

## Estado funcional y visual vigente

### Biblioteca, búsqueda y descargas

- `LibraryViewModel` y `SearchViewModel` persisten el criterio de orden y la dirección mediante `PreferencesStore`.
- `LibraryScreen` muestra una barra de búsqueda compacta en el encabezado, una fila secundaria con cantidad y orden entre el buscador y las pestañas, y las pestañas `Canciones`, `Álbumes` y `Artistas` reutilizan ese filtro.
- Al cambiar el orden en `LibraryScreen`, primero se captura el índice y desplazamiento visibles y luego `LazyListState`/`LazyGridState` los conserva mediante `requestScrollToItem`; no se debe seguir la posición de la canción activa ni usar `animateScrollToItem` para este cambio.
- Los filtros de búsqueda son independientes del orden y la consulta visible se actualiza inmediatamente; el flujo hacia Room/proveedores usa debounce.
- `SearchScreen` contiene únicamente `Explore`: conserva el buscador superior y muestra recomendaciones, referencias guardadas y resultados relacionados con la biblioteca; no reintroducir una pestaña o sección `Mi biblioteca`.
- Los selectores de artista y álbum de `DownloadsScreen` muestran sugerencias por prefijo, mantienen el teclado al escribir y limitan el menú a una altura desplazable.
- El flujo de descarga conserva la confirmación de metadatos, permite elegir artistas/álbumes existentes o escribir nuevos y no modifica Room, SAF, yt-dlp ni Media3 fuera de sus callbacks actuales.
- En el flujo de descarga yt-dlp iniciado desde `SearchScreen`, el álbum queda vacío por defecto aunque el proveedor devuelva `YouTube`/`YouTube Music`; la selección manual de un álbum existente o la escritura de uno nuevo sí se conserva. `YtDlpDownloadWorker` debe mantener `useMetadataAlbumWhenBlank = false`.
- `Explore` usa `AuthorizedMusicProvider`; cuando la consulta está vacía intenta relacionarse con la biblioteca local y respeta estado offline, configuración, licencia y permisos de descarga del proveedor.
- Los proveedores que implementan `PaginatedAuthorizedMusicProvider` deben devolver `RemoteSearchPage` con su `nextPageToken`; `SearchViewModel` conserva las páginas ya cargadas, las agrega con claves estables y expone `Cargar más resultados` sin repetir pistas.
- La implementación de YouTube usa el `YtDlpExtractor` compartido, realiza las solicitudes de red fuera del hilo principal y conserva la paginación por páginas; no sustituirla por un límite fijo de resultados.
- Explore oculta canciones que ya están disponibles localmente comparando URL, título y artista normalizados; debe contemplar títulos editados por el usuario, prefijos de artista quitados del título y canciones que pasan a estar disponibles mientras la pantalla está abierta.
- Al modificar la lógica de paginación, filtrado de biblioteca o estados de Explore, actualizar las pruebas del proveedor y de `SearchViewModel`.
- Biblioteca permite editar y renombrar artistas; al renombrar se actualizan las canciones relacionadas y al eliminar se conserva cada archivo y canción, dejando el artista vacío. La eliminación de álbumes sigue el mismo principio.
- Biblioteca permite editar álbumes desde la cuadrícula de Álbumes o desde el detalle: nombre, artista, año y portada. `Cambiar portada` reutiliza el selector público de Internet, TIDAL, YouTube y la relación verificable de Spotify, además de archivos mediante SAF; la elección es un borrador hasta guardar y también existe la acción explícita para quitarla.
- `ArtworkEditingRepository.saveAlbum` debe actualizar `AlbumEntity` y el nombre de álbum de todas sus canciones dentro de una transacción, pero la portada del álbum es independiente de las portadas de sus canciones. Eliminar un álbum conserva canciones y archivos, dejándolos sin álbum; quitar una portada solo borra un archivo administrado por Polentita cuando ya no tiene referencias.
- El editor de canciones ofrece listas desplazables de artistas y álbumes existentes; no reintroducir la sección redundante de álbumes creados cuando la lista de selección ya cumple esa función.
- Las portadas administradas por la aplicación deben permanecer fuera de la galería del dispositivo mediante `.nomedia`, incluyendo la protección al iniciar y al crear la carpeta de portadas.

### Inicio, reproducción y navegación

- `HomeScreen`, `LibraryScreen` y `SearchScreen` mantienen el rediseño compacto y premium: usar `LazyColumn`/`LazyRow` con claves estables, tarjetas y portadas proporcionadas, y evitar espacios verticales innecesarios.
- `PlaylistNames.TUS_ME_GUSTA` (`"Tus me gusta"`) es una playlist reservada. Al marcar la primera canción como favorita, `DefaultMusicRepository` la crea y sincroniza; al quitar el corazón se elimina solo la relación con esa playlist, no la canción. `AppViewModel` también sincroniza las favoritas existentes al iniciar.
- El acceso `Favoritas` de Inicio navega directamente a `Tus me gusta` para que el usuario elija la canción; no debe reproducir una canción aleatoria desde ese acceso.
- La reproducción y el progreso siguen siendo responsabilidad de `PlayerViewModel`, `PlaybackController` y `MediaSessionService`; los componentes Compose solo reciben callbacks elevados.
- `PlaybackContext` y `PlaybackQueueOrigin` describen una única línea de tiempo Media3: actual, cola manual, continuación del contexto y respaldo de Biblioteca. La cola manual siempre queda antes de los elementos automáticos.
- Al reproducir desde Biblioteca se sigue su lista visible y los cambios de consulta/orden reconstruyen solo la cola automática. Al terminar Inicio, álbum, artista, playlist o colección inteligente se continúa con Biblioteca; al final absoluto `PlaybackService` vuelve al comienzo aun con repetición desactivada.
- La procedencia, tipo/clave/etiqueta y ancla del contexto se persisten con la cola. No volver a reducir la restauración a una lista de IDs sin procedencia ni crear una segunda sesión Media3.
- La tarjeta grande de reproducción de Inicio (`FeaturedNowPlayingCard` en `HomeScreen.kt`) debe conservar una geometría estable al cambiar de canción: título y artista se limitan a una línea con elipsis y el bloque de progreso/tiempos reserva su altura aunque Media3 todavía no haya informado la duración. No eliminar y volver a insertar ese bloque según `durationMs`, porque la tarjeta se contrae y se expande visiblemente.
- `MiniPlayer` conserva sus callbacks de abrir, reproducir/pausar, siguiente y progreso; no debe crear una segunda conexión con Media3.
- El `MiniPlayer` inferior y la tarjeta grande de Inicio son componentes distintos: el MiniPlayer inferior está validado visualmente y no debe modificarse al corregir la tarjeta grande.
- `FullPlayerScreen` muestra directamente el botón de lápiz para editar información y portada; no reintroducir el menú superior con acciones duplicadas de cola y salida de audio, que ya están disponibles en los controles inferiores.
- Las rutas inferiores son `home`, `library`, `search`, `playlists` y `settings`. Volver a Inicio desde otra pestaña usa `popBackStack("home", inclusive = false)`; las demás pestañas usan `popUpTo("home")` con `saveState`, `launchSingleTop` y `restoreState`.
- Los destinos inferiores se envuelven en una superficie opaca para que una pantalla anterior no se vea durante el cambio de ruta.

### Juego Chrome Dino en Inicio — estado de continuidad

El minijuego tipo Chrome Dino está integrado directamente al final de Inicio, sin tarjeta propia y respetando el fondo claro u oscuro de Polentita Music. Se inicia únicamente con el icono de reproducir; durante la partida un toque corto salta y mantener pulsado aproximadamente 170 ms activa el agacharse. La esquina superior izquierda muestra una acción de pausa y el estado pausado muestra el icono central de reanudar. Al perder, el icono de reintentar continúa siendo la única acción y aparece el récord debajo; no mostrar el récord durante la carrera. No agregar flechas, texto de “Jugar” ni música: únicamente los efectos sonoros del juego.

#### Archivos y ubicación

- Lógica de estado y física: `app/src/main/java/com/polentita/music/feature/home/dino/DinoRunnerEngine.kt`.
- Especificación visual y paleta compartida por escena/chrome: `app/src/main/java/com/polentita/music/feature/home/dino/DinoBiomeVisuals.kt`.
- UI, Canvas, controles, sonidos y ambientación: `app/src/main/java/com/polentita/music/feature/home/dino/DinoRunnerGame.kt`.
- Pruebas deterministas: `app/src/test/java/com/polentita/music/feature/home/dino/DinoRunnerEngineTest.kt` y `DinoBiomeVisualsTest.kt`.
- Integración en Inicio: `HomeScreen.kt`, después de `MostPlayedRanking`, con `Spacer(height = 96.dp)` y el elemento estable `home-dino-runner`.
- Sprites y pista: recursos `dino_*` en `app/src/main/res/drawable-nodpi/`; efectos `dino_button_press`, `dino_hit` y `dino_score` en `app/src/main/res/raw/`.
- Los archivos del juego continúan como no rastreados junto con otros cambios del usuario. No borrar, mover ni hacer `reset`; comprobar `git status` antes de tocar este apartado.

#### Controles e integración vigente

- `DINO_QA_INVINCIBILITY_ENABLED` debe permanecer en `false` en `DinoRunnerGame.kt` para entregas jugables. Puede activarse temporalmente para recorrer ambientaciones, pero la APK final debe recompilarse e instalarse con colisiones normales.
- `DinoRunnerSession` se recuerda en `MainNavigation`, por encima del `NavHost`: al desplazar el campo hasta ocultar más del 20 %, abrir otra ruta, el reproductor completo o sacar la app de primer plano, pausa motor y reloj ambiental. Al volver conserva exactamente puntuación, obstáculos y fotograma, pero nunca se reanuda automáticamente: el usuario debe tocar el icono central. Mientras el campo está fuera de foco, la ambientación global se retira para que la interfaz vuelva a ser legible; al reaparecer vuelve congelada junto con el control de reanudar.
- El `pointerInput` pertenece a la columna completa del juego: incluye el campo visual 16:9 y los `168.dp` transparentes que llegan hasta el MiniPlayer. Por eso tocar o mantener pulsado en el espacio inferior también salta o agacha sin mover ni modificar MiniPlayer/navegación.
- Un desplazamiento que supera `touchSlop` cancela el gesto del juego y conserva el scroll vertical de Inicio.
- No reducir esta superficie interactiva a la relación 16:9 ni volver a convertir los `168.dp` en `contentPadding` pasivo.
- El marcador usa `Alignment.TopEnd` con margen útil de `4.dp`; conservarlo pegado al extremo derecho sin recortarlo.

#### Geometría, física y dificultad vigentes

- Mundo lógico: `WORLD_WIDTH = 600f`, `WORLD_HEIGHT = 180f`, `GROUND_Y = 146f`; dinosaurio en `DINO_X = 100f`.
- Pose normal `68 × 73`, agachada `75 × 38` y solapamiento de pies de `3f`. Dino, pista, obstáculos y hitboxes usan el mismo `DinoViewport` y la misma referencia de suelo.
- La pista comienza en `TRACK_TOP_Y = 138f`; el borde visible interno del PNG está desplazado `8f` y coincide con `GROUND_Y`. Los cactus apoyan en `CACTUS_BASE_Y = GROUND_Y + 4f`, como la referencia original de Chrome.
- El salto usa velocidad `680f`, gravedad de subida `1100f` y gravedad de bajada `2300f`: alcanza margen suficiente sobre el cactus triple y cae de forma más rápida y legible.
- La velocidad conserva `min(680f, 210f + score * 0.11f)` hasta 5000 puntos y después usa `min(900f, 680f + (score - 5000) * 0.022f)`. Referencias: `680` a los 5000, `724` a los 7000, `768` a los 9000, `812` a los 11000, `856` a los 13000 y `900` a los 15000; no modificar física, hitboxes ni spawns para simular dificultad.
- El primer obstáculo aparece a los `1200 ms`, nace fuera del campo en `x = 720f`, espera entre `1150` y `1550 ms` y respeta una separación mínima `max(300f, speed * 1.20f)`.
- Los dos primeros cactus son individuales; los grupos se desbloquean a los 60 puntos. Los pájaros se desbloquean a los 120: el primero usa carril medio, después hay al menos dos cactus entre pájaros y se fuerza uno tras cinco obstáculos de suelo para que existan sin encadenarse injustamente.
- Las cuatro nubes usan coordenadas lógicas altas, incluso negativas, para conservar un cielo despejado sobre el salto.

#### Render y ambientación

- Los sprites originales se dibujan directamente dentro del Canvas compartido. En tema oscuro no usan filtro; en tema claro se invierten. No restaurar `BRIGHT_DINO_FILTER`, una capa `Image` independiente ni filtros que aplanen la cabeza: el ojo debe seguir visible.
- El atardecer no dibuja una línea de horizonte adicional: se eliminó porque atravesaba visualmente el campo bajo el marcador y las nubes. La única línea horizontal del juego debe ser la pista real junto a los pies.
- Las fases por puntuación son día, atardecer (`500`), noche estrellada (`1500`), aurora (`3000`), aproximación del eclipse (`4500`), eclipse total con meteoritos (`5000`), espacio profundo (`7000`), nebulosa (`9000`), hiperespacio (`11000`), singularidad (`13000`) y supernova estable (`15000+`).
- Desde el primer bioma, el contenido previo de Inicio —incluido `Más reproducidas`— usa `12.dp` de desenfoque y `0.10f` de alpha mientras el campo está visible, de modo que sol, luna y cuerpos celestes prevalecen sin eliminar la estructura. Al desplazar el juego fuera de foco se pausa y ese contenido vuelve a ser plenamente legible. El campo conserva siempre 16:9: no volver a expandirlo a 3:2 en 5000 porque mueve el suelo, relocaliza la lista y provoca un tirón. Desde DAY la ambientación retinta progresivamente MiniPlayer, navegación inferior y barras del sistema sin modificar el layout; a partir de 5000 la inmersión se intensifica.
- `DinoBiomeVisuals.kt` es la única fuente de cielo, horizonte, acentos, superficie y fuerza del chrome para cada fase. `HomeScreen`, el Canvas del juego y `MainActivity` deben reutilizarla para no volver a separar visualmente el campo de Inicio.
- El campo dibuja terreno procedural determinista: desierto con parallax en día/atardecer/noche, picos en aurora, yermo durante el eclipse y polvo/estelas cósmicas desde espacio profundo. No desplazar la pista ni agregar una segunda línea de suelo al modificar estas capas.
- El contenedor exterior de `PolentitaBottomArea` pinta el fondo ambiental a todo el ancho detrás de MiniPlayer y navegación. No quitar ese fondo ni intentar cubrir los gutters agrandando el MiniPlayer, porque reaparecen franjas negras laterales.
- El antiguo resplandor radial limitado al rectángulo del Canvas fue eliminado porque formaba una banda celeste con bordes visibles. El ambiente debe provenir únicamente de la capa de Inicio a pantalla completa.
- El color base de cada bioma es una superficie unificada casi opaca obtenida al mezclar sus dos acentos. No recuperar un degradado vertical de alto contraste ni dejar visible el bitmap ambiental cuadrado de la portada: toda la pantalla debe percibirse como un único cielo, y la profundidad debe venir de partículas y cuerpos celestes.
- Estrellas, auroras, nebulosas, meteoros, nieve, hiperespacio y pulsos obtienen su fase del `elapsedMs` persistente de la partida. Las curvas son periódicas en sus extremos y los reinicios individuales ocurren fuera del campo o desfasados; no reintroducir `rememberInfiniteTransition` local que vuelva a cero al recomponer o navegar. La densidad base es de 72 estrellas, el conjunto tardío ofrece hasta 18 meteoros y la nebulosa de 9000 añade 42 copos deterministas.
- Las transiciones de bioma mantienen una sola escena Canvas compleja activa y dejan la continuidad al cambio animado de paleta. No restaurar el `AnimatedContent` entre dos escenas de pantalla completa ni el anillo circular expansivo: ambos coincidían en el umbral y producían un tirón perceptible.
- La singularidad de 13000 aparece más cerca del centro, con mayor escala, cinco órbitas elípticas contrarrotantes y veinte partículas con estela alrededor del disco de acreción. La supernova de 15000 usa tres ondas de choque desfasadas, treinta y dos partículas radiales y rayos pulsantes; sus ciclos comienzan y terminan transparentes para evitar saltos visibles.
- `GAME_OVER` desactiva el estado ambiental y toda la interfaz vuelve mediante animación a su tema normal.
- El récord se persiste en `PreferencesStore`, se incluye en backup y nunca disminuye al restaurar. En UI se muestra únicamente en `GAME_OVER`; usar `NUEVO RÉCORD` solo si la partida supera el valor existente al comenzarla.
- Los efectos OGG están normalizados para oírse sobre la música sin solicitar foco ni atenuar Media3: picos medidos aproximados de `-2.3 dB` para salto, `-1.6 dB` para muerte y `-3.4 dB` para hito. El sonido de hito se reproduce únicamente al cruzar cada múltiplo de 1000 puntos.
- Mantener el tamaño del sprite muerto coherente con el vivo y la caja de colisión compuesta alineada con cabeza/torso/patas visibles.

#### Validación del último estado

- El 15 de agosto de 2026 pasaron 31 pruebas dirigidas: las 24 de `DinoRunnerEngineTest`, las 5 de `DinoBiomeVisualsTest`, `PreferencesStoreDinoHighScoreTest` y el roundtrip de `BackupManagerTest`. Cubren geometría, hitboxes, física, spawns, diez fases, progresión de chrome, densidad tardía de meteoros/nieve, sonido cada 1000, cuatro nubes, récord, backup, modo inmortal aislado, pausa/reinicio y dificultad hasta 15000 puntos.
- `:app:compileDebugKotlin`, `git diff --check`, `:app:lintDebug --no-daemon --max-workers=1`, el validador de `polentita-compose-ui` y `:app:assembleDebug --no-daemon --max-workers=1` pasaron. La APK jugable final mide `58496803` bytes y su SHA-256 es `b38af9e8645bd89fd30e05c1c49db6e73302cab09247d29833c54111241274a7`; `DINO_QA_INVINCIBILITY_ENABLED` permanece en `false`.
- La APK jugable final se instaló mediante `adb install -r` en `192.168.100.93:42119` (Samsung Galaxy S20 FE, `SM-G781B`) conservando los datos y se verificó su arranque. El paquete instalado es `0.6.0` (`versionCode 15`). Las once capturas del usuario verificaron los biomas, campo/Inicio unificados, chrome coordinado, pausa y ausencia de franjas negras alrededor del MiniPlayer en la iteración anterior; queda pendiente su revisión física de los nuevos meteoros, nieve, singularidad y transición ligera.
- En una sesión debug de 26916 frames en el S20 FE, `dumpsys gfxinfo` informó 2,18 % de frames fuera de deadline, mediana de 14 ms, percentil 90 de 23 ms y GPU percentil 95 de 10 ms. Es una medición acumulada de QA, no un benchmark comparativo aislado.
- La comprobación física cubrió `IDLE`, carrera, salto, caída, postura agachada desde el área inferior, cactus individual/triple, pájaro, puntuación, `GAME_OVER` y reintento. El usuario confirmó que el toque en la zona inferior funciona correctamente.
- El dinosaurio era visible en la validación física anterior, conservaba el ojo, apoyaba los pies en la línea, los cactus quedaban levemente debajo y el marcador llegaba al borde útil derecho. Esos contratos visuales no fueron modificados.
- La curva hasta `900f` a los 15000 está probada de forma determinista y queda abierta únicamente la calibración subjetiva después de jugar la compilación cinematográfica.

### Playlists y reordenamiento

- `PlaylistsScreen` no muestra una fila separada de Favoritas con cantidad/reproducción. `Recientes` y `Más reproducidas` abren detalles dinámicos basados en Room; no son playlists persistidas ni deben reproducir automáticamente una canción al tocarlas.
- `PlaylistDetailScreen` permite mantener pulsada una canción y arrastrarla: la fila se eleva visualmente, se escala y puede auto-desplazar la lista cerca de los bordes. `PlaylistViewModel` serializa los cambios de orden y conserva el orden pendiente para no perder movimientos rápidos.
- El `DragHandle` es una indicación visual; la interacción real debe seguir funcionando mediante pulsación larga sobre la fila. Las listas usan claves estables y no deben crear una segunda conexión con Media3.

### Importación de playlists mediante metadatos

- La entrada visible está en `PlaylistsScreen`: `Importar playlist` navega a la ruta `playlist-import` de `MainActivity` y no debe iniciar descargas al pegar o analizar un enlace.
- Archivos canónicos del flujo: `domain/playlistimport/PlaylistImportModels.kt`, `data/playlistimport/PlaylistImportProviders.kt`, `PlaylistImportMatcher.kt`, `PlaylistImportRepository.kt`, `PlaylistImportCoordinator.kt`, `feature/playlistimport/PlaylistImportViewModel.kt` y `PlaylistImportScreen.kt`.
- `FilePlaylistImportProvider`, `SpotifyPlaylistImportProvider`, `TidalPlaylistImportProvider` y `YouTubePlaylistImportProvider` convierten sus metadatos públicos a `ImportedCollection`, conservando `sourceId`, posiciones, ISRC, título, artistas, álbum, duración, portada y tipo de colección. Los enlaces públicos no deben mostrar `Próximamente` antes de intentar el análisis.
- Spotify y TIDAL inspeccionan primero el HTML público, JSON-LD o estado JSON embebido y, cuando la página lo expone, usan únicamente la lectura pública necesaria para completar la colección; YouTube/YouTube Music usa la extracción plana de playlist de yt-dlp con `skip_download`. No usar cookies, inicio de sesión, endpoints privados ni extraer audio durante el análisis.
- Cuando el proveedor no expone la lista completa, devolver exactamente `Este proveedor no permite leer esta playlist públicamente. Impórtala mediante JSON, CSV o TXT`; no simular metadatos ni volver a mostrar `Próximamente`.
- Las canciones faltantes se resuelven usando el `AuthorizedProviderRegistry` y el proveedor de búsqueda existente basado en yt-dlp; la descarga debe pasar exclusivamente por `YtDlpExtractor` y `DownloadCoordinator`/`YtDlpDownloadWorker`. No crear una segunda implementación de descarga ni ejecutar yt-dlp en paralelo.
- `PlaylistImportMatcher` compara primero ISRC, después título/artistas normalizados, luego álbum y duración. Normaliza mayúsculas, diacríticos, espacios, puntuación, feat./ft., artistas múltiples y sufijos de versión. Las coincidencias aproximadas solo son sugerencias; las ambiguas requieren revisión.
- `PlaylistImportMatcher.selectCandidates` selecciona automáticamente la coincidencia de mayor puntuación aunque existan alternativas cercanas; conserva `ambiguous` solo como información. Ofrece hasta 6 candidatos con puntuación mínima de 0,30 para permitir cambiar manualmente la coincidencia desde el diálogo.
- `PlaylistImportRepository` crea la playlist local con las canciones disponibles, conserva el orden original, evita relaciones duplicadas y guarda la procedencia internamente. Las canciones descargadas después se adjuntan a la misma playlist; los fallos no eliminan lo completado. `Tus me gusta` sigue siendo un nombre reservado.
- `PlaylistImportCoordinator` mantiene una cola persistente de una descarga por vez, recupera estados tras reinicio, respeta `NetworkAccessPolicy`, permite pausar/reanudar/cancelar/reintentar/omitir y emite la notificación final. La inicialización es perezosa: solo restaura una cola activa o se inicia explícitamente desde una acción.
- Al procesar una canción importada, `PlaylistImportCoordinator` usa `YtDlpSourceResolver` para inspeccionar primero la URL autorizada y, si falla, buscar hasta 5 alternativas públicas mediante yt-dlp. Tras agotar los reintentos internos de WorkManager, `PlaylistImportRecoveryPolicy` permite un segundo ciclo automático para fallos temporales del mismo candidato; luego avanza al siguiente. Los fallos permanentes o de configuración requieren revisión y no saltan autenticación ni restricciones del proveedor.
- `PlaylistImportItemEntity.artworkUrl` es la portada pública de la pista analizada. Debe pasarse como `thumbnailUrl` a `DownloadCoordinator.enqueueYtDlp`; ese valor tiene prioridad sobre la miniatura de yt-dlp y `YtDlpDownloadWorker` lo entrega al `DownloadedCoverResolver`. No reemplazarla por una portada descargada desde Spotify/TIDAL/YouTube ni iniciar audio durante el análisis.
- `YtDlpSourceResolver` es compartido por `PlaylistImportCoordinator` y `SearchViewModel`; no duplicar la lógica de fallback ni crear otro flujo de descarga. La selección, inspección y resolución de alternativas ocurren después de la confirmación explícita del usuario.
- La notificación de progreso de `YtDlpDownloadWorker` usa el recurso `extracting_audio` con el texto visible `Extrayendo canción`; no volver a mostrar `Extrayendo audio autorizado`.
- La persistencia está en `PlaylistImportEntities.kt` y `PlaylistImportDao.kt`. La base Room es versión 4; `DatabaseMigrations.MIGRATION_3_4` es aditiva y no debe eliminar datos existentes. La versión 4 añade ISRC a canciones/descargas y tablas de colecciones, elementos y candidatos.
- No guardar tokens, cabeceras, URLs temporales ni credenciales en las entidades de importación. Las listas grandes deben usar `LazyColumn` con claves estables.
- Al modificar este flujo, actualizar como mínimo `PlaylistImportProvidersTest`, `PlaylistImportMatcherTest`, `PlaylistImportRepositoryTest`, `PlaylistImportQueuePolicyTest` y `DatabaseMigration3To4RoomTest`, además de las pruebas afectadas de descarga/red.

### Paleta adaptativa y barras del sistema

- `ArtworkPaletteExtractor`/`rememberArtworkPalette` es la única fuente de paleta de portada. Usa miniaturas, trabajo fuera del hilo principal y caché LRU; no crear extractores paralelos ni recalcular bitmaps en recomposición.
- `ArtworkDynamicTheme` y `AdaptiveArtworkBackground` animan la paleta, mantienen superficies grandes oscuras, aplican overlay de contraste y usan fallback determinista cuando no hay portada.
- En Inicio, el fondo ambiental se basa en la canción actual del reproductor; no usar temporalmente la portada de otra sección como sustituto durante la carga.
- `AdaptiveSystemBars` mantiene edge-to-edge y sincroniza color e iconos de las barras de estado y navegación con `MaterialTheme.colorScheme`; debe conservar contraste legible en temas claros, oscuros y adaptativos.
- El encabezado expandido de Inicio es compacto y no debe reintroducir una barra fija opaca que persiga al usuario durante el desplazamiento.
- Las animaciones deben respetar la escala del sistema, evitar flashes y conservar claves estables en listas.
- `PolentitaStatusPill`, `PolentitaMetricCard` y `PolentitaSectionHeader` son los componentes compartidos para comunicar estado, resumen y jerarquía. Usar texto además de color y reservar reintentos visibles para errores que realmente requieran intervención.

### Portadas adaptativas y metadata multimedia

- El URI guardado en `Song.coverUri` continúa apuntando a la portada original. Nunca sobrescribir una portada horizontal o vertical con una copia recortada; debe seguir disponible para edición futura.
- `SquareArtworkProcessor`, `SquareArtworkTransformation` y `SquareArtworkBitmapLoader`, en `core/artwork/SquareArtwork.kt`, forman la única política de encuadre cuadrado. No crear otra transformación o procesador paralelo.
- Las relaciones de aspecto aproximadamente cuadradas conservan el comportamiento normal. Las portadas claramente horizontales o verticales se presentan dentro de un cuadrado con fondo suavizado derivado de la misma imagen, overlay oscuro y original centrada con escala `Fit`, sin deformación ni barras negras planas.
- `Artwork` construye una `ImageRequest` con `SquareArtworkTransformation`; Coil realiza y cachea la transformación fuera de recomposición. No decodificar ni transformar bitmaps directamente desde un Composable.
- `ArtworkPaletteExtractor` sigue recibiendo el URI original y continúa siendo la única fuente de color adaptativo; el encuadre cuadrado no sustituye al extractor de paleta.
- `Song.toMediaItem()` mantiene título, artista, álbum, duración y artwork, y agrega `ARTWORK_REVISION` basado en `dateModified` para invalidar metadata cuando se edita la portada.
- `PlaybackService` observa en Room únicamente la canción actual para refrescar su `MediaItem` cuando cambian título, artista, álbum o portada. El reemplazo conserva media id y URI de audio; no debe reiniciar la reproducción ni observar toda la biblioteca desde el servicio.
- Media3 permanece en una sola `MediaSession` dentro de `PlaybackService`. Se usa `DefaultMediaNotificationProvider`, `SquareArtworkBitmapLoader`, el icono monocromático `ic_notification`, el canal `polentita_media_playback` y preferencias estándar para anterior, reproducir/pausar y siguiente. No introducir `RemoteViews`, otra sesión ni otro servicio.
- `PlaybackSessionActivity` crea el `ContentIntent`; `MainActivity` consume `ACTION_OPEN_PLAYER` y navega a `player`. Tocar la notificación debe abrir el reproductor completo, incluso si la Activity ya existe con `launchMode="singleTop"`.
- La notificación y pantalla bloqueada deben seguir delegando estados de reproducción, pausa, buffering y disponibilidad de comandos a Media3/SystemUI para conservar compatibilidad con One UI, Bluetooth y Android Auto.

### Conectividad, ahorro de datos y modo offline

- `AppPreferences` persiste por separado `offlineMode` y `wifiOnlyDownloads`. Desactivar el modo offline no debe borrar ni modificar el valor de Wi‑Fi solamente. Backup importa y exporta ambas preferencias.
- `NetworkAccessPolicy`, en `core/network/NetworkAccessPolicy.kt`, combina `PreferencesStore` con `ConnectivityManager`/`NetworkCapabilities` y publica un `StateFlow<NetworkAccessState>` inmutable.
- La política distingue explícitamente `OFFLINE_MODE`, `NO_CONNECTION` y `WIFI_REQUIRED`. No mostrar “Modo sin conexión activo” cuando simplemente falta Internet.
- La descarga es apta con `wifiOnlyDownloads` activado únicamente si la red validada usa transporte Wi‑Fi y no está marcada como medida. Ethernet, datos móviles y redes medidas no deben considerarse Wi‑Fi apta.
- La búsqueda remota y los adelantos siguen permitidos con datos móviles aunque `wifiOnlyDownloads` esté activo; solamente la descarga queda bloqueada. `offlineMode` bloquea búsqueda, recomendaciones, inspección/resolución de URL, adelantos y nuevas descargas independientemente del transporte.
- La protección no es solo visual: `SearchViewModel` y `DownloadsViewModel` comprueban la política antes de llamar proveedores o `YtDlpExtractor`; `DownloadCoordinator` vuelve a comprobarla antes de crear el registro y encolar; los workers vuelven a comprobarla antes y durante el tráfico.
- WorkManager usa `NetworkType.UNMETERED` para trabajos creados con Wi‑Fi solamente. `DirectDownloadWorker` y `YtDlpDownloadWorker` hacen además la validación exacta de transporte mediante la misma política; una restricción de WorkManager por sí sola no reemplaza esa comprobación.
- Si la política deja de permitir una descarga, el worker usa `PAUSED`/reintento o cancelación segura según el origen del cambio. Conservar `polentita-download-<id>.part` y `cache/yt-dlp/<id>` cuando sean recuperables; no borrar parciales al activar offline.
- Al activar `offlineMode`, `DownloadCoordinator` cancela trabajos etiquetados `polentita-download` sin borrar canciones completadas ni archivos del usuario. `PlayerViewModel` detiene inmediatamente cualquier adelanto remoto usando la conexión Media3 existente.
- `SearchScreen` conserva Explore como única función, deshabilita el buscador en offline, mantiene las referencias locales visibles y evita cargar sus miniaturas remotas. Muestra una acción para ir a Biblioteca.
- `DownloadsScreen` conserva las secciones `Buscar` y `Pegar enlace`; en offline ambas permanecen visibles pero no pueden iniciar teclado/acciones remotas. Con Wi‑Fi solamente bloqueado por datos móviles, buscar y escuchar adelantos siguen disponibles.
- Ajustes muestra la sección `Conectividad` antes de `Descargas`. El switch “Descargas solo con Wi‑Fi” se deshabilita visualmente mientras offline está activo, pero conserva su valor persistido.

### Ajustes responsive vigentes

- En tarjetas remotas estrechas usar el texto corto `Adelanto`; no volver a forzar “Escuchar un adelanto” en dos botones de igual ancho si se trunca en el Samsung Galaxy S20 FE.
- El botón principal de detalle de playlist mantiene `Reproducir` en una sola línea con padding compacto; conservar las acciones de aleatorio, agregar, editar, borrar y reordenar.
- El panel de título/artista/progreso del reproductor completo es deliberadamente ligero y compacto. Mantener sus áreas táctiles y callbacks aunque se reduzca padding visual.
- `MiniPlayer` y navegación inferior usan padding vertical reducido, pero sus controles conservan al menos 48 dp y separación flotante. No recuperar espacio reduciendo los blancos táctiles.
- La búsqueda de Descargas muestra un estado inicial informativo y no ejecuta búsquedas automáticas con la consulta vacía.

## Calidad

- No agregar `TODO()` ni implementaciones simuladas en flujos centrales.
- Los textos visibles deben estar en español y en `strings.xml` cuando sean estáticos.
- Agregar o actualizar pruebas al modificar DAOs, repositorios, validadores, backups o ViewModels.
- En este entorno, la suite completa de Gradle puede congelar el equipo porque combina
  Robolectric, Chaquopy y varios executors de pruebas. Para cambios futuros ejecutar pruebas
  dirigidas —preferentemente un método individual para Robolectric/Room— siempre con
  `--no-daemon --max-workers=1` y sin lanzar comandos de Gradle en paralelo. Preferir fixtures
  locales, fakes y respuestas simuladas; no hacer que las pruebas unitarias dependan de Internet.
  Si el daemon de Kotlin se cae, añadir `-Dkotlin.compiler.execution.strategy=in-process` a esa
  ejecución aislada. Si una prueba pesada falla por memoria, timeout o caída del executor, no
  repetir automáticamente `test`, `testDebugUnitTest` ni varias clases juntas: dividirla en
  ejecuciones aisladas y registrar la limitación real.
- Para cambios exclusivamente visuales, no tomar capturas ni recorrer la aplicación con ADB como
  paso rutinario: ralentiza la iteración y no sustituye la referencia visual del usuario.
- La revisión visual con capturas debe hacerse solo si el usuario la solicita expresamente o si el
  usuario proporciona las imágenes; en ese caso, usar esas imágenes como referencia principal.
- Antes de entregar ejecutar, cuando el entorno lo permita:

```bash
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

- Registrar limitaciones reales en `README.md`; no afirmar que una prueba no ejecutada pasó.
- Instalar un APK por ADB no requiere ejecutar tests. Para cambios exclusivamente visuales o de navegación, la validación mínima es `assembleDebug`; si se modifica lógica de ViewModel, repositorio, DAO o validadores, aplicar las pruebas correspondientes.
- Después de una compilación exitosa, una actualización conservadora puede instalarse con `adb install -r app/build/outputs/apk/debug/app-debug.apk`; no desinstalar ni borrar datos de la aplicación para actualizar.
- Antes de instalar, comprobar `adb devices -l` y usar el serial del dispositivo conectado (puede ser una conexión ADB por Wi‑Fi). Tras instalar, se puede abrir con `adb -s <serial> shell monkey -p com.polentita.music 1`.

### Última validación integral conocida

- La validación de la importación de playlists quedó en 145 pruebas unitarias, `lintDebug`, `assembleDebug` y `git diff --check` sin fallos; la migración Room 3→4 se probó con una base v3 real y datos centinela.
- El APK `0.6.0` (`versionCode 15`) se instaló con `adb install -r` en un Samsung Galaxy S20 FE (`SM-G781B`) conservando los datos, y la aplicación arrancó correctamente después de la migración.
- Las pruebas relevantes incluyen `PlaylistImportProvidersTest`, `PlaylistImportMatcherTest`, `PlaylistImportRepositoryTest`, `PlaylistImportQueuePolicyTest`, `DatabaseMigration3To4RoomTest`, `SquareArtworkTest`, `NetworkAccessPolicyTest`, `PreferencesStoreConnectivityTest`, `DownloadsViewModelTest`, `ViewModelTest` y `MediaItemMapperTest`.
- Esta nota describe el último estado conocido, no sustituye volver a ejecutar las verificaciones después de cambios futuros.
- En la actualización del 13 de agosto de 2026, la tarjeta grande de Inicio reservó el espacio de progreso/tiempos durante la carga de duración; `:app:compileDebugKotlin`, `:app:assembleDebug` y `git diff --check` pasaron, y el APK se instaló con `adb install -r` en el Samsung Galaxy S20 FE conectado.

## Estilo y seguridad

- Kotlin/JVM 17, formato oficial de Kotlin, nombres expresivos y funciones pequeñas.
- Sanitizar nombres de archivo y validar URLs remotas antes de cualquier acceso de red.
- No registrar URLs completas, cabeceras, tokens ni parámetros de consulta.
- Compartir archivos solamente mediante `content://` con permisos temporales.
- Preservar los cambios del usuario y evitar comandos destructivos sobre el repositorio.

### Actualizaciones fuera de Google Play y enlaces externos

- `AGENTS.md`, `docs/`, las pruebas locales y cualquier material de trabajo privado no se
  publican en GitHub. Antes de preparar un commit revisar siempre la lista exacta de archivos
  que se van a incluir.
- El enlace de Colaborar abre primero `https://github.com/polen-tita/Polentita-Music` mediante la
  aplicación oficial de GitHub (`com.github.android`) cuando está instalada y, si no, usa el
  navegador. La resolución del paquete ocurre únicamente después de tocar el enlace, no durante
  la composición ni en segundo plano.
- El subtítulo de Inicio rota entre cinco recursos localizados al volver a primer plano:
  `home_tagline_offline`, `home_tagline_smile`, `home_tagline_today`, `home_tagline_dino` y
  `home_tagline_aura_farmer`. `Aura Farmer` se conserva deliberadamente en inglés.
- Los APK públicos viven en `downloads/`, el aviso de terceros en `THIRD_PARTY_NOTICES.md` y el
  manifiesto de actualizaciones en `updates/latest.json`. `website/` permanece local hasta que
  esté listo para producción y `serve-apk.sh` es una herramienta local; ninguno se publica.
  `config/local.properties.example` es solo una plantilla pública: nunca publicar
  `local.properties` ni credenciales.
- El comprobador de actualizaciones vive en `core/update/AppUpdate.kt` y
  `feature/update/AppUpdateViewModel.kt`. Lee solo el manifiesto HTTPS público
  `https://raw.githubusercontent.com/polen-tita/Polentita-Music/main/updates/latest.json`, respeta
  `NetworkAccessPolicy`, limita la frecuencia a una comprobación cada 30 minutos y compara
  `versionCode` contra `BuildConfig.VERSION_CODE`.
- `latest.json` es público y debe contener `versionCode`, `versionName`, `releaseUrl` y un objeto
  `downloads` con entradas `es-arm64-v8a`, `es-x86_64`, `en-arm64-v8a` y `en-x86_64`. Cada entrada
  necesita una URL HTTPS permitida, terminada en `.apk`, y un SHA-256 de 64 caracteres. Los APK de
  la versión `v1.0.0` viven en los assets del GitHub Release; al publicar una versión nueva hay que
  compilar los cuatro APK, calcular sus hashes y actualizar el manifiesto con las URLs de su release
  antes de publicar o retirar los assets de la versión anterior.
- El README enlaza a `releases/latest/download/<nombre>.apk` para que sus botones sigan funcionando
  entre versiones mientras se conserven los nombres de los cuatro assets.
- El aviso redondo de Inicio se puede cerrar con `X`; al cerrarlo permanece visible en Ajustes,
  debajo de Descargas. El botón descarga el APK correspondiente, verifica el SHA-256 y abre el
  instalador del sistema. Android siempre puede pedir confirmación y autorización de “instalar
  aplicaciones desconocidas”; no existe instalación silenciosa.
- La actualización solo puede funcionar si el APK nuevo conserva `applicationId`
  `com.polentita.music`, tiene un `versionCode` mayor y está firmado con la misma clave de firma
  que la instalación existente. La clave privada de firma nunca se guarda en el repositorio. Si
  la instalación automática falla, conservar el botón de la página de descarga de GitHub como
  alternativa.
- No reemplazar la verificación SHA-256 por una descarga directa sin validación, no aceptar URLs
  fuera de los hosts de GitHub permitidos y no quitar `REQUEST_INSTALL_PACKAGES` sin cambiar el
  flujo a una descarga manual.
