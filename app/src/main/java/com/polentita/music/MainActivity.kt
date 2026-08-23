package com.polentita.music

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.polentita.music.core.designsystem.MiniPlayer
import com.polentita.music.core.designsystem.AmbientChromeStyle
import com.polentita.music.core.designsystem.ArtworkPalette
import com.polentita.music.core.designsystem.ArtworkColorAnalyzer
import com.polentita.music.core.designsystem.PolentitaContentColors
import com.polentita.music.core.designsystem.PolentitaMotion
import com.polentita.music.core.designsystem.PolentitaOpacity
import com.polentita.music.core.designsystem.PolentitaRadii
import com.polentita.music.core.designsystem.PolentitaSpacing
import com.polentita.music.core.designsystem.PolentitaTheme
import com.polentita.music.core.designsystem.animationDuration
import com.polentita.music.core.designsystem.rememberAnimationsEnabled
import com.polentita.music.core.designsystem.rememberArtworkPalette
import com.polentita.music.core.localization.withAppLanguage
import com.polentita.music.feature.app.AppViewModel
import com.polentita.music.feature.downloads.DownloadsScreen
import com.polentita.music.feature.editor.AlbumDetailScreen
import com.polentita.music.feature.editor.ArtistDetailScreen
import com.polentita.music.feature.editor.SongEditorScreen
import com.polentita.music.feature.editor.TechnicalDetailsScreen
import com.polentita.music.feature.home.HomeScreen
import com.polentita.music.feature.home.dino.DinoRunnerAmbientState
import com.polentita.music.feature.home.dino.DinoRunnerSession
import com.polentita.music.feature.home.dino.dinoBiomeSpec
import com.polentita.music.feature.library.FolderSetupScreen
import com.polentita.music.feature.library.ImportScreen
import com.polentita.music.feature.library.LibraryScreen
import com.polentita.music.feature.library.LibraryViewModel
import com.polentita.music.feature.player.FullPlayerScreen
import com.polentita.music.feature.player.PlayerViewModel
import com.polentita.music.feature.player.QueueScreen
import com.polentita.music.feature.playlist.PlaylistDetailScreen
import com.polentita.music.feature.playlist.PlaylistsScreen
import com.polentita.music.feature.playlist.SmartPlaylistDetailScreen
import com.polentita.music.feature.playlist.SmartPlaylistKind
import com.polentita.music.feature.playlistimport.PlaylistImportScreen
import com.polentita.music.feature.search.SearchScreen
import com.polentita.music.feature.settings.AboutScreen
import com.polentita.music.feature.settings.SettingsScreen
import com.polentita.music.feature.update.AppUpdateUiState
import com.polentita.music.feature.update.AppUpdateViewModel
import com.polentita.music.playback.session.PlaybackController
import com.polentita.music.playback.session.PlaybackSessionActivity
import dagger.hilt.android.AndroidEntryPoint
import coil3.compose.AsyncImage
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var playbackController: PlaybackController

    private val openPlayerRequest = mutableIntStateOf(0)
    private val homeTaglineIndex = mutableIntStateOf(0)
    private val updateCheckRequest = mutableIntStateOf(0)
    private var hasStartedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == PlaybackSessionActivity.ACTION_OPEN_PLAYER) {
            openPlayerRequest.intValue++
        }
        enableEdgeToEdge()
        setContent {
            PolentitaApp(
                openPlayerRequest = openPlayerRequest.intValue,
                homeTaglineIndex = homeTaglineIndex.intValue,
                updateCheckRequest = updateCheckRequest.intValue,
            )
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == PlaybackSessionActivity.ACTION_OPEN_PLAYER) {
            openPlayerRequest.intValue++
        }
    }

    override fun onStart() {
        super.onStart()
        if (hasStartedOnce) {
            homeTaglineIndex.intValue++
        }
        hasStartedOnce = true
        updateCheckRequest.intValue++
        playbackController.ensureConnected()
    }
}

private data class BottomDestination(
    val route: String,
    val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val bottomDestinations = listOf(
    BottomDestination("home", R.string.nav_home, Icons.Default.Home),
    BottomDestination("library", R.string.nav_library, Icons.Default.LibraryMusic),
    BottomDestination("search", R.string.nav_search, Icons.Default.Search),
    BottomDestination("playlists", R.string.nav_playlists, Icons.Default.PlaylistPlay),
    BottomDestination("settings", R.string.nav_settings, Icons.Default.Settings),
)

private data class DinoChromeVisuals(
    val active: Boolean,
    val backdrop: Color,
    val surface: Color,
    val accent: Color,
    val secondary: Color,
    val strength: Float,
) {
    fun asAmbientStyle(): AmbientChromeStyle = AmbientChromeStyle(
        backdrop = backdrop,
        surface = surface,
        accent = accent,
        secondary = secondary,
        strength = strength,
    )
}

@Composable
private fun rememberDinoChromeVisuals(ambient: DinoRunnerAmbientState): DinoChromeVisuals {
    val colorScheme = MaterialTheme.colorScheme
    val animationsEnabled = rememberAnimationsEnabled()
    val duration = animationDuration(animationsEnabled, PolentitaMotion.artwork)
    val spec = dinoBiomeSpec(ambient, colorScheme)
    val backdrop by animateColorAsState(
        targetValue = spec.chromeBackdrop,
        animationSpec = tween(duration),
        label = "Fondo ambiental global",
    )
    val surface by animateColorAsState(
        targetValue = spec.chromeSurface,
        animationSpec = tween(duration),
        label = "Superficie ambiental global",
    )
    val accent by animateColorAsState(
        targetValue = spec.accent,
        animationSpec = tween(duration),
        label = "Acento ambiental global",
    )
    val secondary by animateColorAsState(
        targetValue = spec.secondary,
        animationSpec = tween(duration),
        label = "Acento ambiental secundario",
    )
    val strength by androidx.compose.animation.core.animateFloatAsState(
        targetValue = spec.chromeStrength,
        animationSpec = tween(duration),
        label = "Intensidad ambiental global",
    )
    return DinoChromeVisuals(
        active = ambient.active || strength > 0.001f,
        backdrop = backdrop,
        surface = surface,
        accent = accent,
        secondary = secondary,
        strength = strength,
    )
}

@Composable
private fun DinoGlobalChromeTheme(
    visuals: DinoChromeVisuals,
    content: @Composable () -> Unit,
) {
    val base = MaterialTheme.colorScheme
    val strength = visuals.strength.coerceIn(0f, 1f)
    val background = lerp(base.background, visuals.backdrop, strength)
    val surface = lerp(base.surface, visuals.surface, strength)
    val surfaceVariant = lerp(
        lerp(base.surfaceVariant, visuals.surface, 0.72f * strength),
        visuals.secondary,
        0.12f * strength,
    )
    val primary = lerp(base.primary, visuals.accent, strength)
    val secondary = lerp(base.secondary, visuals.secondary, strength)
    val onBackground = ArtworkColorAnalyzer.safeContentColor(background)
    val onSurface = ArtworkColorAnalyzer.safeContentColor(surface)

    MaterialTheme(
        colorScheme = base.copy(
            primary = primary,
            onPrimary = ArtworkColorAnalyzer.safeContentColor(primary),
            primaryContainer = lerp(base.primaryContainer, primary, 0.28f * strength),
            onPrimaryContainer = lerp(
                base.onPrimaryContainer,
                PolentitaContentColors.PrimaryOnDark,
                strength,
            ),
            secondary = secondary,
            onSecondary = ArtworkColorAnalyzer.safeContentColor(secondary),
            tertiary = lerp(base.tertiary, visuals.accent, 0.72f * strength),
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = lerp(
                base.onSurfaceVariant,
                PolentitaContentColors.SecondaryOnDark,
                strength,
            ),
            surfaceDim = lerp(base.surfaceDim, visuals.backdrop, 0.82f * strength),
            surfaceBright = lerp(base.surfaceBright, visuals.secondary, 0.12f * strength),
            surfaceContainerLowest = background,
            surfaceContainerLow = lerp(background, surface, 0.42f),
            surfaceContainer = surface,
            surfaceContainerHigh = surfaceVariant,
            surfaceContainerHighest = lerp(surfaceVariant, visuals.accent, 0.08f * strength),
            outline = lerp(base.outline, visuals.secondary, 0.46f * strength),
            outlineVariant = lerp(base.outlineVariant, visuals.accent, 0.24f * strength),
        ),
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}

internal val splashIconResourceId: Int = R.mipmap.ic_launcher_foreground

@Composable
private fun PolentitaApp(
    openPlayerRequest: Int,
    homeTaglineIndex: Int,
    updateCheckRequest: Int,
    appViewModel: AppViewModel = hiltViewModel(),
    player: PlayerViewModel = hiltViewModel(),
    updateViewModel: AppUpdateViewModel = hiltViewModel(),
) {
    val appState by appViewModel.state.collectAsStateWithLifecycle()
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    val playbackVisual by player.visualState.collectAsStateWithLifecycle()
    val adaptiveThemeActive =
        appState.preferences.adaptiveArtworkTheme && playbackVisual.currentSongId != null
    val artworkPalette = rememberArtworkPalette(
        coverUri = playbackVisual.artworkUri.takeIf { adaptiveThemeActive },
        seed = if (adaptiveThemeActive) {
            "${playbackVisual.title}|${playbackVisual.artist}"
        } else {
            "Polentita Music"
        },
    )
    var dinoAmbient by remember { mutableStateOf(DinoRunnerAmbientState()) }
    val context = LocalContext.current
    val openUpdatePage: () -> Unit = {
        updateState.info?.releaseUrl?.let { releaseUrl ->
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl)))
            }
        }
        Unit
    }
    val startUpdate: () -> Unit = {
        if (updateState.info?.downloadUrl == null) {
            openUpdatePage()
        } else if (updateViewModel.canRequestInstallation()) {
            updateViewModel.startDownload()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    ),
                )
            }
        }
        Unit
    }
    LaunchedEffect(updateCheckRequest) {
        updateViewModel.checkForUpdate()
    }
    LaunchedEffect(updateState.downloadedUri) {
        val uri = updateState.downloadedUri ?: return@LaunchedEffect
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(installIntent) }
            .onFailure { openUpdatePage() }
        updateViewModel.consumeDownloadedUri(uri)
    }
    val localizedContext = remember(context, appState.preferences.language) {
        context.withAppLanguage(appState.preferences.language)
    }
    val localizedConfiguration = remember(localizedContext) {
        Configuration(localizedContext.resources.configuration)
    }
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedConfiguration,
    ) {
        PolentitaTheme(
            themeMode = appState.preferences.themeMode,
            dynamicColor = appState.preferences.dynamicColor,
            artworkPalette = artworkPalette.takeIf { adaptiveThemeActive },
            adaptiveArtworkTheme = appState.preferences.adaptiveArtworkTheme,
        ) {
            val dinoChrome = rememberDinoChromeVisuals(dinoAmbient)
            DinoGlobalChromeTheme(dinoChrome) {
                AdaptiveSystemBars()
                when {
                    appState.loading -> SplashScreen()
                    appState.preferences.libraryTreeUri == null -> {
                        val libraryViewModel: LibraryViewModel = hiltViewModel()
                        FolderSetupScreen(libraryViewModel, onLinked = {})
                    }
                    else -> MainNavigation(
                        player = player,
                        homeTaglineIndex = homeTaglineIndex,
                        updateState = updateState,
                        artworkPalette = artworkPalette.takeIf { adaptiveThemeActive },
                        openPlayerRequest = openPlayerRequest,
                        dinoAmbient = dinoAmbient,
                        dinoChrome = dinoChrome,
                        dinoHighScore = appState.preferences.dinoHighScore,
                        onDinoAmbientChanged = { dinoAmbient = it },
                        onDinoHighScore = appViewModel::recordDinoHighScore,
                        onUpdate = startUpdate,
                        onDismissUpdate = updateViewModel::dismiss,
                        onOpenUpdatePage = openUpdatePage,
                    )
                }
            }
        }
    }
}

@Composable
private fun SplashScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val fallbackPainter = painterResource(R.drawable.ic_notification)
            AsyncImage(
                model = splashIconResourceId,
                contentDescription = null,
                error = fallbackPainter,
                fallback = fallbackPainter,
                modifier = Modifier.size(112.dp),
            )
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
        }
    }
}

@Composable
private fun MainNavigation(
    player: PlayerViewModel = hiltViewModel(),
    artworkPalette: ArtworkPalette? = null,
    openPlayerRequest: Int = 0,
    homeTaglineIndex: Int = 0,
    updateState: AppUpdateUiState = AppUpdateUiState(),
    dinoAmbient: DinoRunnerAmbientState = DinoRunnerAmbientState(),
    dinoChrome: DinoChromeVisuals = DinoChromeVisuals(
        active = false,
        backdrop = Color.Unspecified,
        surface = Color.Unspecified,
        accent = Color.Unspecified,
        secondary = Color.Unspecified,
        strength = 0f,
    ),
    dinoHighScore: Int = 0,
    onDinoAmbientChanged: (DinoRunnerAmbientState) -> Unit = {},
    onDinoHighScore: (Int) -> Unit = {},
    onUpdate: () -> Unit = {},
    onDismissUpdate: () -> Unit = {},
    onOpenUpdatePage: () -> Unit = {},
) {
    val navController = rememberNavController()
    val dinoSession = remember { DinoRunnerSession(dinoHighScore) }
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottom = currentRoute in bottomDestinations.map(BottomDestination::route)
    val animationsEnabled = rememberAnimationsEnabled()
    val destinationDuration = animationDuration(animationsEnabled, PolentitaMotion.standard)

    LaunchedEffect(dinoSession, dinoHighScore) {
        dinoSession.updateHighScore(dinoHighScore)
    }

    LaunchedEffect(dinoSession, currentRoute) {
        if (currentRoute != "home") {
            dinoSession.pause()
            onDinoAmbientChanged(DinoRunnerAmbientState())
        }
    }

    LaunchedEffect(openPlayerRequest) {
        if (openPlayerRequest > 0 && currentRoute != "player") {
            navController.navigate("player") { launchSingleTop = true }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        bottomBar = {
            PolentitaBottomArea(
                player = player,
                showMini = currentRoute != "player",
                showNavigation = showBottom,
                selectedRoute = currentRoute,
                onOpenPlayer = { navController.navigate("player") },
                artworkPalette = artworkPalette,
                dinoChrome = dinoChrome,
                onNavigate = { destination ->
                    if (currentRoute != destination.route) {
                        if (destination.route == "home") {
                            navController.popBackStack("home", inclusive = false)
                        } else {
                            navController.navigate(destination.route) {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding),
            enterTransition = {
                fadeIn(tween(destinationDuration)) + slideInVertically(
                    animationSpec = tween(destinationDuration),
                    initialOffsetY = { it / 28 },
                )
            },
            exitTransition = {
                fadeOut(tween(destinationDuration)) + slideOutVertically(
                    animationSpec = tween(destinationDuration),
                    targetOffsetY = { -it / 28 },
                )
            },
            popEnterTransition = {
                fadeIn(tween(destinationDuration)) + slideInVertically(
                    animationSpec = tween(destinationDuration),
                    initialOffsetY = { -it / 28 },
                )
            },
            popExitTransition = {
                fadeOut(tween(destinationDuration)) + slideOutVertically(
                    animationSpec = tween(destinationDuration),
                    targetOffsetY = { it / 28 },
                )
            },
        ) {
            composable("home") {
                OpaqueDestination {
                    HomeScreen(
                        homeTaglineIndex = homeTaglineIndex,
                        updateState = updateState,
                        onPlaylist = { navController.navigate("playlist/$it") },
                        onAlbum = { navController.navigate("album/$it") },
                        onLibrary = { navController.navigate("library") },
                        onDownloads = { navController.navigate("downloads/new") },
                        playerViewModel = player,
                        dinoAmbient = dinoAmbient,
                        dinoSession = dinoSession,
                        onDinoAmbientChanged = onDinoAmbientChanged,
                        onDinoHighScore = onDinoHighScore,
                        onUpdate = onUpdate,
                        onDismissUpdate = onDismissUpdate,
                    )
                }
            }
            composable("library") {
                OpaqueDestination {
                    LibraryScreen(
                        playerViewModel = player,
                        onImport = { navController.navigate("import") },
                        onDownload = { navController.navigate("downloads/new") },
                        onAlbum = { navController.navigate("album/$it") },
                        onArtist = { navController.navigate("artist/${Uri.encode(it)}") },
                        onEditSong = { navController.navigate("song-editor/$it") },
                        onDetails = { navController.navigate("technical/$it") },
                    )
                }
            }
            composable("search") {
                OpaqueDestination {
                    SearchScreen(
                        player = player,
                        onOpenLibrary = {
                            navController.navigate("library") {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
            composable("playlists") {
                OpaqueDestination {
                    PlaylistsScreen(
                        onPlaylist = { navController.navigate("playlist/$it") },
                        onAutomaticPlaylist = { kind ->
                            navController.navigate("smart-playlist/${kind.routeValue}")
                        },
                        onImportPlaylist = { navController.navigate("playlist-import") },
                    )
                }
            }
            composable("settings") {
                OpaqueDestination {
                    SettingsScreen(
                        onImport = { navController.navigate("import") },
                        onDownloads = { navController.navigate("downloads/new") },
                        onAbout = { navController.navigate("about") },
                        updateState = updateState,
                        onUpdate = onUpdate,
                        onOpenUpdatePage = onOpenUpdatePage,
                    )
                }
            }
            composable("album/{albumId}") { entry ->
                val id = entry.arguments?.getString("albumId")?.toLongOrNull() ?: -1
                AlbumDetailScreen(
                    albumId = id,
                    player = player,
                    onEditSong = { navController.navigate("song-editor/$it") },
                    onDetails = { navController.navigate("technical/$it") },
                    onBack = navController::popBackStack,
                )
            }
            composable("artist/{artist}") { entry ->
                val artist = Uri.decode(entry.arguments?.getString("artist").orEmpty())
                ArtistDetailScreen(
                    artist = artist,
                    player = player,
                    onEditSong = { navController.navigate("song-editor/$it") },
                    onDetails = { navController.navigate("technical/$it") },
                    onBack = navController::popBackStack,
                )
            }
            composable("playlist/{playlistId}") {
                PlaylistDetailScreen(
                    player = player,
                    onEditSong = { navController.navigate("song-editor/$it") },
                    onDetails = { navController.navigate("technical/$it") },
                    onBack = navController::popBackStack,
                )
            }
            composable("playlist-import") {
                PlaylistImportScreen(
                    onBack = navController::popBackStack,
                    onOpenPlaylist = { navController.navigate("playlist/$it") },
                )
            }
            composable("smart-playlist/{kind}") { entry ->
                val kind = when (entry.arguments?.getString("kind")) {
                    SmartPlaylistKind.MOST_PLAYED.routeValue -> SmartPlaylistKind.MOST_PLAYED
                    else -> SmartPlaylistKind.RECENT
                }
                SmartPlaylistDetailScreen(
                    kind = kind,
                    player = player,
                    onEditSong = { navController.navigate("song-editor/$it") },
                    onDetails = { navController.navigate("technical/$it") },
                    onBack = navController::popBackStack,
                )
            }
            composable("player") {
                FullPlayerScreen(
                    viewModel = player,
                    onQueue = { navController.navigate("queue") },
                    onEditSong = { navController.navigate("song-editor/$it") },
                    onBack = navController::popBackStack,
                )
            }
            composable("queue") { QueueScreen(player, navController::popBackStack) }
            composable("import") { ImportScreen(navController::popBackStack) }
            composable("downloads/new") {
                DownloadsScreen(
                    onBack = navController::popBackStack,
                    onOpenSong = {
                        player.playSong(it)
                        navController.navigate("player")
                    },
                    player = player,
                )
            }
            composable("downloads/history") {
                DownloadsScreen(
                    onBack = navController::popBackStack,
                    onOpenSong = {
                        player.playSong(it)
                        navController.navigate("player")
                    },
                    player = player,
                )
            }
            composable("song-editor/{songId}") { SongEditorScreen(navController::popBackStack) }
            composable("technical/{songId}") { entry ->
                TechnicalDetailsScreen(
                    songId = entry.arguments?.getString("songId")?.toLongOrNull() ?: -1,
                    onBack = navController::popBackStack,
                )
            }
            composable("about") { AboutScreen(navController::popBackStack) }
        }
    }
}

@Composable
private fun OpaqueDestination(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.055f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.035f),
                    ),
                ),
            ),
    ) {
        content()
    }
}

@Composable
private fun AdaptiveSystemBars() {
    val view = LocalView.current
    val activity = view.context as? Activity ?: return
    val animationsEnabled = rememberAnimationsEnabled()
    val systemBarDuration = animationDuration(animationsEnabled, PolentitaMotion.standard)
    val statusBarColor by androidx.compose.animation.animateColorAsState(
        targetValue = MaterialTheme.colorScheme.background,
        animationSpec = tween(systemBarDuration),
        label = "Color de barra de estado",
    )
    val navigationBarColor by androidx.compose.animation.animateColorAsState(
        targetValue = MaterialTheme.colorScheme.surface,
        animationSpec = tween(systemBarDuration),
        label = "Color de barra de navegación del sistema",
    )
    val lightStatusIcons = statusBarColor.luminance() > 0.5f
    val lightNavigationIcons = navigationBarColor.luminance() > 0.5f

    SideEffect {
        activity.window.statusBarColor = statusBarColor.toArgb()
        activity.window.navigationBarColor = navigationBarColor.toArgb()
        WindowCompat.getInsetsController(activity.window, view).apply {
            isAppearanceLightStatusBars = lightStatusIcons
            isAppearanceLightNavigationBars = lightNavigationIcons
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activity.window.isStatusBarContrastEnforced = false
            activity.window.isNavigationBarContrastEnforced = false
        }
    }
}

@Composable
private fun PolentitaBottomArea(
    player: PlayerViewModel,
    showMini: Boolean,
    showNavigation: Boolean,
    selectedRoute: String?,
    onOpenPlayer: () -> Unit,
    artworkPalette: ArtworkPalette?,
    dinoChrome: DinoChromeVisuals,
    onNavigate: (BottomDestination) -> Unit,
) {
    val animationsEnabled = rememberAnimationsEnabled()
    val bottomBackdrop by animateColorAsState(
        targetValue = if (dinoChrome.active && dinoChrome.backdrop != Color.Unspecified) {
            dinoChrome.backdrop
        } else {
            MaterialTheme.colorScheme.background
        },
        animationSpec = tween(animationDuration(animationsEnabled, PolentitaMotion.artwork)),
        label = "Continuidad detrás del MiniPlayer",
    )
    val navigationAccent by animateColorAsState(
        targetValue = if (dinoChrome.strength > 0f && dinoChrome.accent != Color.Unspecified) {
            lerp(MaterialTheme.colorScheme.primary, dinoChrome.accent, dinoChrome.strength)
        } else {
            MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(animationDuration(animationsEnabled, PolentitaMotion.artwork)),
        label = "Acento de navegación",
    )
    val navigationSurface by animateColorAsState(
        targetValue = run {
            val base = artworkPalette?.background?.copy(alpha = 0.96f)
                ?: MaterialTheme.colorScheme.surface
            if (
                dinoChrome.active &&
                dinoChrome.surface != Color.Unspecified &&
                dinoChrome.secondary != Color.Unspecified
            ) {
                lerp(
                    lerp(base, dinoChrome.surface, 0.72f * dinoChrome.strength),
                    dinoChrome.secondary,
                    0.10f * dinoChrome.strength,
                )
            } else {
                base
            }
        },
        animationSpec = tween(animationDuration(animationsEnabled, PolentitaMotion.artwork)),
        label = "Superficie de navegación",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .background(bottomBackdrop),
    ) {
        PolentitaMiniPlayerArea(player, showMini, onOpenPlayer, dinoChrome)
        if (showNavigation) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PolentitaSpacing.small, vertical = 2.dp)
                    .clip(RoundedCornerShape(PolentitaRadii.large))
                    .background(navigationSurface.copy(alpha = 0.96f))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = PolentitaOpacity.border),
                        RoundedCornerShape(PolentitaRadii.large),
                    ),
            ) {
                NavigationBar(
                    modifier = Modifier.height(72.dp),
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 0.dp,
                ) {
                    bottomDestinations.forEach { destination ->
                        val selected = selectedRoute == destination.route
                        val iconScale by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = if (selected) 1.06f else 1f,
                            animationSpec = tween(animationDuration(animationsEnabled, PolentitaMotion.quick)),
                            label = "Escala de navegación",
                        )
                        NavigationBarItem(
                            selected = selected,
                            onClick = { onNavigate(destination) },
                            icon = {
                                Icon(
                                    destination.icon,
                                    stringResource(destination.labelRes),
                                    Modifier.graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    },
                                )
                            },
                            label = { Text(stringResource(destination.labelRes), maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = navigationAccent,
                                selectedTextColor = navigationAccent,
                                indicatorColor = navigationAccent.copy(alpha = 0.14f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PolentitaMiniPlayerArea(
    player: PlayerViewModel,
    showMini: Boolean,
    onOpenPlayer: () -> Unit,
    dinoChrome: DinoChromeVisuals,
) {
    val playback by player.visualState.collectAsStateWithLifecycle()
    AnimatedVisibility(
        visible = showMini && (playback.currentSongId != null || playback.isPreview),
        enter = fadeIn() + slideInVertically { it / 3 },
        exit = fadeOut() + slideOutVertically { it / 3 },
    ) {
        MiniPlayer(
            state = playback,
            onOpen = onOpenPlayer,
            onToggle = player::togglePlayPause,
            onNext = player::next,
            progressContent = { MiniPlayerProgress(player) },
            ambientStyle = dinoChrome.asAmbientStyle().takeIf { dinoChrome.active },
        )
    }
}

private data class MiniProgressState(
    val positionMs: Long = 0,
    val durationMs: Long = 0,
)

@Composable
private fun MiniPlayerProgress(player: PlayerViewModel) {
    val progress by remember(player) {
        player.state
            .map { MiniProgressState(it.positionMs, it.durationMs) }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = MiniProgressState())
    if (progress.durationMs <= 0) return
    LinearProgressIndicator(
        progress = {
            progress.positionMs.toFloat() / progress.durationMs.coerceAtLeast(1)
        },
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth().height(2.dp),
    )
}
