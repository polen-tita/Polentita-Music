package com.polentita.music.feature.home.dino

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.polentita.music.R
import com.polentita.music.core.designsystem.PolentitaSpacing
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

private const val DUCK_HOLD_MS = 170L
private const val GAME_ASPECT_RATIO = 16f / 9f
private const val DINO_QA_INVINCIBILITY_ENABLED = false
private const val CLOUD_RENDER_HEIGHT = 27f
private const val GAME_OVER_TOP_Y = -20f
private val DINO_ACTION_OFFSET_Y = (-8).dp

private enum class DinoQuickGestureResult {
    RELEASED,
    CANCELED,
}

private data class DinoRunnerAssets(
    val start: ImageBitmap,
    val run: List<ImageBitmap>,
    val jump: ImageBitmap,
    val duck: List<ImageBitmap>,
    val dead: ImageBitmap,
    val smallCactus: List<ImageBitmap>,
    val largeCactus: List<ImageBitmap>,
    val birds: List<ImageBitmap>,
    val cloud: ImageBitmap,
    val track: ImageBitmap,
    val gameOver: ImageBitmap,
)

private data class DinoViewport(
    val scale: Float,
    val top: Float,
) {
    fun mapX(worldX: Float): Float = worldX * scale

    fun mapY(worldY: Float): Float = top + worldY * scale

    fun map(bounds: DinoWorldRect): DinoPixelRect = DinoPixelRect(
        left = mapX(bounds.left),
        top = mapY(bounds.top),
        right = mapX(bounds.right),
        bottom = mapY(bounds.bottom),
    )

    companion object {
        fun from(size: Size): DinoViewport {
            val scale = size.width / DinoRunnerGeometry.WORLD_WIDTH
            val groundInPixels = size.height * DinoRunnerGeometry.GROUND_FRACTION
            return DinoViewport(
                scale = scale,
                top = groundInPixels - DinoRunnerGeometry.GROUND_Y * scale,
            )
        }
    }
}

private data class DinoPixelRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

private data class DinoSourceCrop(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
) {
    fun offset(): IntOffset = IntOffset(left, top)

    fun sizeFor(image: ImageBitmap): IntSize = IntSize(
        width = (image.width - left - right).coerceAtLeast(1),
        height = (image.height - top - bottom).coerceAtLeast(1),
    )
}

private data class DinoRenderedFrame(
    val image: ImageBitmap,
    val crop: DinoSourceCrop = DinoSourceCrop(),
)

private val START_SOURCE_CROP = DinoSourceCrop(left = 1, top = 6, bottom = 5)
private val DEAD_SOURCE_CROP = DinoSourceCrop(top = 6, bottom = 9)
private val CACTUS_SOURCE_CROP = DinoSourceCrop(top = 2)
private val CLOUD_SOURCE_CROP = DinoSourceCrop(top = 2, bottom = 72)
private val TRACK_SOURCE_CROP = DinoSourceCrop(top = 2, bottom = 2)

@Stable
class DinoRunnerSession internal constructor(
    initialHighScore: Int = 0,
) {
    private val engine = DinoRunnerEngine(
        initialHighScore = initialHighScore,
        invincible = DINO_QA_INVINCIBILITY_ENABLED,
    )

    internal var state by mutableStateOf(engine.state)
        private set

    internal fun updateHighScore(score: Int) {
        engine.updateHighScore(score)
        syncState()
    }

    internal fun start(): Boolean = engine.start().also { syncState() }

    internal fun pause() {
        engine.pause()
        syncState()
    }

    internal fun resume() {
        engine.resume()
        syncState()
    }

    internal fun jump(): Boolean = engine.jump().also { syncState() }

    internal fun setDucking(ducking: Boolean) {
        engine.setDucking(ducking)
        syncState()
    }

    internal fun tick(deltaMs: Long) {
        engine.tick(deltaMs)
        syncState()
    }

    private fun syncState() {
        state = engine.state
    }
}

@Composable
internal fun DinoRunnerGame(
    session: DinoRunnerSession,
    gameplayVisible: Boolean,
    modifier: Modifier = Modifier,
    bottomControlAreaHeight: Dp = 0.dp,
    onAmbientChanged: (DinoRunnerAmbientState) -> Unit = {},
    onNewHighScore: (Int) -> Unit = {},
) {
    val assets = rememberDinoRunnerAssets()
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val soundController = remember { DinoRunnerSoundController(context.applicationContext) }
    val state = session.state
    val currentOnAmbientChanged by rememberUpdatedState(onAmbientChanged)
    val currentOnNewHighScore by rememberUpdatedState(onNewHighScore)
    val ambientState = if (gameplayVisible) {
        DinoRunnerEngine.ambientStateFor(state)
    } else {
        DinoRunnerAmbientState()
    }
    val biomeSpec = dinoBiomeSpec(ambientState, MaterialTheme.colorScheme)

    LaunchedEffect(session, gameplayVisible, state.phase) {
        if (!gameplayVisible && state.phase == DinoRunnerPhase.RUNNING) {
            session.pause()
        }
    }

    LaunchedEffect(ambientState) {
        currentOnAmbientChanged(ambientState)
    }

    DisposableEffect(Unit) {
        onDispose {
            currentOnAmbientChanged(DinoRunnerAmbientState())
        }
    }

    DisposableEffect(soundController) {
        onDispose { soundController.release() }
    }

    DisposableEffect(lifecycleOwner, session) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    session.pause()
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            session.pause()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(session, soundController, state.phase, gameplayVisible) {
        if (!gameplayVisible || state.phase != DinoRunnerPhase.RUNNING) return@LaunchedEffect

        var previousFrameNanos = 0L
        while (isActive && session.state.phase == DinoRunnerPhase.RUNNING) {
            val frameNanos = withFrameNanos { it }
            if (previousFrameNanos != 0L) {
                val before = session.state
                session.tick((frameNanos - previousFrameNanos) / 1_000_000L)
                val after = session.state
                if (
                    before.phase != DinoRunnerPhase.GAME_OVER &&
                    after.phase == DinoRunnerPhase.GAME_OVER
                ) {
                    soundController.playHit()
                    if (after.isNewHighScore) currentOnNewHighScore(after.score)
                }
                if (DinoRunnerEngine.shouldPlayScoreSound(before.score, after.score)) {
                    soundController.playScore()
                }
            }
            previousFrameNanos = frameNanos
        }
    }

    val sceneBackground = if (biomeSpec.active) {
        androidx.compose.ui.graphics.lerp(biomeSpec.sky, biomeSpec.horizon, 0.52f)
    } else {
        MaterialTheme.colorScheme.background
    }
    val isLightScene = sceneBackground.luminance() > 0.5f
    val spriteFilter = if (isLightScene) INVERTED_SPRITE_FILTER else null
    val actionDescription = stringResource(
        when (state.phase) {
            DinoRunnerPhase.GAME_OVER -> R.string.dino_game_restart
            DinoRunnerPhase.PAUSED -> R.string.dino_game_resume
            else -> R.string.dino_game_start
        },
    )
    val pauseDescription = stringResource(R.string.dino_game_pause)
    val gameDescription = stringResource(R.string.dino_game_description)
    val gameplayInputModifier = if (state.phase == DinoRunnerPhase.RUNNING) {
        Modifier.pointerInput(state.phase) {
            val touchSlop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val quickResult = withTimeoutOrNull(DUCK_HOLD_MS) {
                    awaitQuickGestureResult(
                        pointerId = down.id,
                        startX = down.position.x,
                        startY = down.position.y,
                        touchSlop = touchSlop,
                    )
                }
                when (quickResult) {
                    DinoQuickGestureResult.RELEASED -> {
                        if (session.jump()) soundController.playPress()
                    }

                    DinoQuickGestureResult.CANCELED -> Unit
                    null -> {
                        session.setDucking(true)
                        try {
                            awaitDuckingRelease(
                                pointerId = down.id,
                                startX = down.position.x,
                                startY = down.position.y,
                                touchSlop = touchSlop,
                            )
                        } finally {
                            session.setDucking(false)
                        }
                    }
                }
            }
        }
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(gameplayInputModifier)
            .semantics { contentDescription = gameDescription },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(GAME_ASPECT_RATIO),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawDinoScene(
                    state = state,
                    assets = assets,
                    spriteFilter = spriteFilter,
                    biomeSpec = biomeSpec,
                )
            }

            if (state.phase != DinoRunnerPhase.IDLE) {
                Text(
                    text = state.score.toString().padStart(5, '0'),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(
                            top = PolentitaSpacing.small,
                            end = PolentitaSpacing.xs,
                        ),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.phase == DinoRunnerPhase.RUNNING) {
                IconButton(
                    onClick = {
                        session.pause()
                        soundController.playPress()
                    },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(
                            top = PolentitaSpacing.xs,
                            start = PolentitaSpacing.xs,
                        )
                        .semantics { contentDescription = pauseDescription },
                ) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f),
                    )
                }
            }

            if (
                state.phase == DinoRunnerPhase.IDLE ||
                state.phase == DinoRunnerPhase.PAUSED ||
                state.phase == DinoRunnerPhase.GAME_OVER
            ) {
                IconButton(
                    onClick = {
                        val changed = if (state.phase == DinoRunnerPhase.PAUSED) {
                            session.resume()
                            true
                        } else {
                            session.start()
                        }
                        if (changed) soundController.playPress()
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = DINO_ACTION_OFFSET_Y)
                        .semantics { contentDescription = actionDescription },
                ) {
                    Icon(
                        imageVector = when (state.phase) {
                            DinoRunnerPhase.GAME_OVER -> Icons.Default.Replay
                            else -> Icons.Default.PlayArrow
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (state.phase == DinoRunnerPhase.GAME_OVER) {
                Text(
                    text = stringResource(
                        if (state.isNewHighScore) {
                            R.string.dino_new_high_score
                        } else {
                            R.string.dino_high_score
                        },
                        state.highScore.toString().padStart(5, '0'),
                    ),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = 40.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                    ),
                    color = if (state.isNewHighScore) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                )
            }
        }

        if (bottomControlAreaHeight > 0.dp) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bottomControlAreaHeight),
            )
        }
    }
}

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.awaitQuickGestureResult(
    pointerId: PointerId,
    startX: Float,
    startY: Float,
    touchSlop: Float,
): DinoQuickGestureResult {
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == pointerId }
            ?: return DinoQuickGestureResult.CANCELED
        if (!change.pressed) return DinoQuickGestureResult.RELEASED
        val deltaX = change.position.x - startX
        val deltaY = change.position.y - startY
        if (deltaX * deltaX + deltaY * deltaY > touchSlop * touchSlop) {
            return DinoQuickGestureResult.CANCELED
        }
    }
}

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.awaitDuckingRelease(
    pointerId: PointerId,
    startX: Float,
    startY: Float,
    touchSlop: Float,
) {
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == pointerId } ?: return
        if (!change.pressed) return
        val deltaX = change.position.x - startX
        val deltaY = change.position.y - startY
        if (deltaX * deltaX + deltaY * deltaY > touchSlop * touchSlop) return
        change.consume()
    }
}

@Composable
private fun rememberDinoRunnerAssets(): DinoRunnerAssets {
    val start = ImageBitmap.imageResource(R.drawable.dino_start)
    val run1 = ImageBitmap.imageResource(R.drawable.dino_run_1)
    val run2 = ImageBitmap.imageResource(R.drawable.dino_run_2)
    val jump = ImageBitmap.imageResource(R.drawable.dino_jump)
    val duck1 = ImageBitmap.imageResource(R.drawable.dino_duck_1)
    val duck2 = ImageBitmap.imageResource(R.drawable.dino_duck_2)
    val dead = ImageBitmap.imageResource(R.drawable.dino_dead)
    val small1 = ImageBitmap.imageResource(R.drawable.dino_cactus_small_1)
    val small2 = ImageBitmap.imageResource(R.drawable.dino_cactus_small_2)
    val small3 = ImageBitmap.imageResource(R.drawable.dino_cactus_small_3)
    val large1 = ImageBitmap.imageResource(R.drawable.dino_cactus_large_1)
    val large2 = ImageBitmap.imageResource(R.drawable.dino_cactus_large_2)
    val large3 = ImageBitmap.imageResource(R.drawable.dino_cactus_large_3)
    val bird1 = ImageBitmap.imageResource(R.drawable.dino_bird_1)
    val bird2 = ImageBitmap.imageResource(R.drawable.dino_bird_2)
    val cloud = ImageBitmap.imageResource(R.drawable.dino_cloud)
    val track = ImageBitmap.imageResource(R.drawable.dino_track)
    val gameOver = ImageBitmap.imageResource(R.drawable.dino_game_over)
    return remember(
        start,
        run1,
        run2,
        jump,
        duck1,
        duck2,
        dead,
        small1,
        small2,
        small3,
        large1,
        large2,
        large3,
        bird1,
        bird2,
        cloud,
        track,
        gameOver,
    ) {
        DinoRunnerAssets(
            start = start,
            run = listOf(run1, run2),
            jump = jump,
            duck = listOf(duck1, duck2),
            dead = dead,
            smallCactus = listOf(small1, small2, small3),
            largeCactus = listOf(large1, large2, large3),
            birds = listOf(bird1, bird2),
            cloud = cloud,
            track = track,
            gameOver = gameOver,
        )
    }
}

private fun DrawScope.drawDinoScene(
    state: DinoRunnerState,
    assets: DinoRunnerAssets,
    spriteFilter: ColorFilter?,
    biomeSpec: DinoBiomeSpec,
) {
    val viewport = DinoViewport.from(size)

    if (biomeSpec.active) {
        drawDinoBiomeField(
            state = state,
            spec = biomeSpec,
            viewport = viewport,
        )
    }

    if (biomeSpec.phase <= DinoRunnerAmbientPhase.ECLIPSE) {
        state.clouds.forEach { cloud ->
            drawWorldSprite(
                image = assets.cloud,
                bounds = DinoWorldRect(
                    left = cloud.x,
                    top = cloud.y,
                    right = cloud.x + assets.cloud.width,
                    bottom = cloud.y + CLOUD_RENDER_HEIGHT,
                ),
                viewport = viewport,
                colorFilter = spriteFilter,
                sourceCrop = CLOUD_SOURCE_CROP,
            )
        }
    }

    val trackWidth = assets.track.width.toFloat()
    val trackOffset = -(state.distance * 2.1f % trackWidth)
    var trackX = trackOffset
    while (trackX < DinoRunnerGeometry.WORLD_WIDTH) {
        drawWorldSprite(
            image = assets.track,
            bounds = DinoWorldRect(
                left = trackX,
                top = DinoRunnerGeometry.TRACK_TOP_Y,
                right = trackX + trackWidth,
                bottom = DinoRunnerGeometry.TRACK_TOP_Y +
                    TRACK_SOURCE_CROP.sizeFor(assets.track).height,
            ),
            viewport = viewport,
            colorFilter = spriteFilter,
            sourceCrop = TRACK_SOURCE_CROP,
        )
        trackX += trackWidth
    }

    state.obstacles.forEach { obstacle ->
        val image = when (obstacle.kind) {
            DinoObstacleKind.SMALL_CACTUS -> assets.smallCactus[obstacle.variant]
            DinoObstacleKind.LARGE_CACTUS -> assets.largeCactus[obstacle.variant]
            DinoObstacleKind.BIRD -> assets.birds[obstacle.frame]
        }
        drawWorldSprite(
            image = image,
            bounds = DinoWorldRect(
                left = obstacle.x,
                top = obstacle.y,
                right = obstacle.x + obstacle.width,
                bottom = obstacle.y + obstacle.height,
            ),
            viewport = viewport,
            colorFilter = spriteFilter,
            sourceCrop = if (obstacle.kind == DinoObstacleKind.BIRD) {
                DinoSourceCrop()
            } else {
                CACTUS_SOURCE_CROP
            },
        )
    }

    if (state.phase == DinoRunnerPhase.IDLE) {
        val previewCactus = assets.smallCactus[0]
        drawWorldSprite(
            image = previewCactus,
            bounds = DinoWorldRect(
                left = 430f,
                top = DinoRunnerGeometry.CACTUS_BASE_Y - previewCactus.height,
                right = 430f + previewCactus.width,
                bottom = DinoRunnerGeometry.CACTUS_BASE_Y,
            ),
            viewport = viewport,
            colorFilter = spriteFilter,
            sourceCrop = CACTUS_SOURCE_CROP,
        )
    }

    val dino = dinoFrameForState(state, assets)
    drawWorldSprite(
        image = dino.image,
        bounds = DinoRunnerGeometry.dinoDrawBounds(
            dinoVerticalOffset = state.dinoVerticalOffset,
            ducking = state.ducking,
        ),
        viewport = viewport,
        colorFilter = spriteFilter,
        sourceCrop = dino.crop,
    )

    if (state.phase == DinoRunnerPhase.GAME_OVER) {
        drawWorldSprite(
            image = assets.gameOver,
            bounds = DinoWorldRect(
                left = (DinoRunnerGeometry.WORLD_WIDTH - assets.gameOver.width) / 2f,
                top = GAME_OVER_TOP_Y,
                right = (DinoRunnerGeometry.WORLD_WIDTH + assets.gameOver.width) / 2f,
                bottom = GAME_OVER_TOP_Y + assets.gameOver.height,
            ),
            viewport = viewport,
            colorFilter = spriteFilter,
        )
    }
}

private data class DinoFieldParticle(
    val x: Float,
    val y: Float,
    val depth: Float,
    val radius: Float,
)

private val DINO_FIELD_PARTICLES = List(28) { index ->
    DinoFieldParticle(
        x = ((index * 83 + 41) % 613).toFloat(),
        y = 14f + ((index * 47 + 19) % 116),
        depth = 0.18f + (index % 6) * 0.11f,
        radius = 0.8f + (index % 4) * 0.45f,
    )
}

private fun DrawScope.drawDinoBiomeField(
    state: DinoRunnerState,
    spec: DinoBiomeSpec,
    viewport: DinoViewport,
) {
    when (spec.phase) {
        DinoRunnerAmbientPhase.DAY -> {
            drawDinoDesertLayers(state, spec, viewport, warmth = 0.36f)
            drawDinoFieldParticles(state, spec.accent, viewport, alpha = 0.25f)
        }
        DinoRunnerAmbientPhase.SUNSET -> {
            drawDinoDesertLayers(state, spec, viewport, warmth = 0.62f)
            drawDinoFieldParticles(state, spec.accent, viewport, alpha = 0.34f)
        }
        DinoRunnerAmbientPhase.NIGHT -> {
            drawDinoDesertLayers(state, spec, viewport, warmth = 0.18f)
            drawDinoFieldParticles(state, spec.accent, viewport, alpha = 0.30f)
        }
        DinoRunnerAmbientPhase.AURORA -> {
            drawDinoFrozenPeaks(state, spec, viewport)
            drawDinoFieldParticles(state, spec.accent, viewport, alpha = 0.38f)
        }
        DinoRunnerAmbientPhase.ECLIPSE -> {
            drawDinoWasteland(state, spec, viewport)
            drawDinoFieldParticles(
                state,
                spec.accent,
                viewport,
                alpha = if (state.score >= DinoRunnerEngine.EPIC_MODE_SCORE) 0.54f else 0.34f,
            )
        }
        DinoRunnerAmbientPhase.DEEP_SPACE -> {
            drawDinoCosmicField(state, spec, viewport, streak = 0.10f)
        }
        DinoRunnerAmbientPhase.NEBULA -> {
            drawDinoCosmicField(state, spec, viewport, streak = 0.18f)
        }
        DinoRunnerAmbientPhase.HYPERSPACE -> {
            drawDinoCosmicField(state, spec, viewport, streak = 0.70f)
        }
        DinoRunnerAmbientPhase.SINGULARITY -> {
            drawDinoCosmicField(state, spec, viewport, streak = 0.28f)
        }
        DinoRunnerAmbientPhase.SUPERNOVA -> {
            drawDinoCosmicField(state, spec, viewport, streak = 0.42f)
        }
    }
}

private fun DrawScope.drawDinoDesertLayers(
    state: DinoRunnerState,
    spec: DinoBiomeSpec,
    viewport: DinoViewport,
    warmth: Float,
) {
    drawDinoMesaLayer(
        distance = state.distance,
        viewport = viewport,
        height = 31f,
        span = 214f,
        parallax = 0.055f,
        color = spec.secondary.copy(alpha = 0.08f + warmth * 0.05f),
        phase = 0.18f,
    )
    drawDinoMesaLayer(
        distance = state.distance,
        viewport = viewport,
        height = 21f,
        span = 168f,
        parallax = 0.10f,
        color = spec.fieldSilhouette.copy(alpha = 0.14f + warmth * 0.05f),
        phase = 0.52f,
    )
    drawDinoMesaLayer(
        distance = state.distance,
        viewport = viewport,
        height = 12f,
        span = 126f,
        parallax = 0.16f,
        color = spec.accent.copy(alpha = 0.07f + warmth * 0.035f),
        phase = 0.81f,
    )
}

private fun DrawScope.drawDinoMesaLayer(
    distance: Float,
    viewport: DinoViewport,
    height: Float,
    span: Float,
    parallax: Float,
    color: Color,
    phase: Float,
) {
    val baseY = viewport.mapY(DinoRunnerGeometry.GROUND_Y + 5f)
    val spanPx = viewport.mapX(span)
    val offsetPx = -((viewport.mapX(distance * parallax) + spanPx * phase) % spanPx)
    val path = Path().apply {
        moveTo(0f, size.height)
        lineTo(0f, baseY)
        var x = offsetPx - spanPx
        while (x <= size.width + spanPx) {
            lineTo(x, baseY)
            lineTo(x + spanPx * 0.16f, baseY - viewport.mapX(height * 0.34f))
            lineTo(x + spanPx * 0.31f, baseY - viewport.mapX(height * 0.92f))
            lineTo(x + spanPx * 0.55f, baseY - viewport.mapX(height))
            lineTo(x + spanPx * 0.68f, baseY - viewport.mapX(height * 0.46f))
            lineTo(x + spanPx, baseY)
            x += spanPx
        }
        lineTo(size.width, size.height)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawDinoFrozenPeaks(
    state: DinoRunnerState,
    spec: DinoBiomeSpec,
    viewport: DinoViewport,
) {
    drawDinoMesaLayer(
        distance = state.distance,
        viewport = viewport,
        height = 38f,
        span = 176f,
        parallax = 0.045f,
        color = spec.secondary.copy(alpha = 0.13f),
        phase = 0.33f,
    )
    drawDinoMesaLayer(
        distance = state.distance,
        viewport = viewport,
        height = 24f,
        span = 134f,
        parallax = 0.09f,
        color = spec.accent.copy(alpha = 0.11f),
        phase = 0.71f,
    )
}

private fun DrawScope.drawDinoWasteland(
    state: DinoRunnerState,
    spec: DinoBiomeSpec,
    viewport: DinoViewport,
) {
    drawDinoMesaLayer(
        distance = state.distance,
        viewport = viewport,
        height = 34f,
        span = 190f,
        parallax = 0.07f,
        color = spec.secondary.copy(alpha = 0.22f),
        phase = 0.44f,
    )
    drawDinoMesaLayer(
        distance = state.distance,
        viewport = viewport,
        height = 15f,
        span = 112f,
        parallax = 0.15f,
        color = spec.accent.copy(alpha = 0.12f),
        phase = 0.78f,
    )
}

private fun DrawScope.drawDinoFieldParticles(
    state: DinoRunnerState,
    color: Color,
    viewport: DinoViewport,
    alpha: Float,
) {
    DINO_FIELD_PARTICLES.forEachIndexed { index, particle ->
        val travel = state.distance * (0.22f + particle.depth * 0.48f)
        val worldX = ((particle.x - travel) % DinoRunnerGeometry.WORLD_WIDTH +
            DinoRunnerGeometry.WORLD_WIDTH) % DinoRunnerGeometry.WORLD_WIDTH
        val pulse = 0.62f + 0.38f * kotlin.math.sin(
            state.elapsedMs / 780f + index * 0.91f,
        )
        drawCircle(
            color = color.copy(alpha = (alpha * pulse).coerceIn(0.04f, 0.56f)),
            radius = viewport.mapX(particle.radius),
            center = Offset(
                viewport.mapX(worldX),
                viewport.mapY(particle.y),
            ),
        )
    }
}

private fun DrawScope.drawDinoCosmicField(
    state: DinoRunnerState,
    spec: DinoBiomeSpec,
    viewport: DinoViewport,
    streak: Float,
) {
    DINO_FIELD_PARTICLES.forEachIndexed { index, particle ->
        if (particle.y > DinoRunnerGeometry.GROUND_Y - 16f) return@forEachIndexed
        val travel = state.distance * (0.08f + particle.depth * (0.22f + streak * 0.28f))
        val worldX = ((particle.x - travel) % DinoRunnerGeometry.WORLD_WIDTH +
            DinoRunnerGeometry.WORLD_WIDTH) % DinoRunnerGeometry.WORLD_WIDTH
        val head = Offset(viewport.mapX(worldX), viewport.mapY(particle.y))
        val length = viewport.mapX((3f + particle.depth * 13f) * streak)
        val particleColor = if (index % 2 == 0) spec.accent else spec.secondary
        if (length > 1f) {
            drawLine(
                color = particleColor.copy(alpha = 0.16f + streak * 0.18f),
                start = Offset(head.x - length, head.y - length * 0.16f),
                end = head,
                strokeWidth = viewport.mapX(particle.radius * 0.58f).coerceAtLeast(1f),
                cap = StrokeCap.Round,
            )
        }
        drawCircle(
            color = particleColor.copy(alpha = 0.26f + streak * 0.20f),
            radius = viewport.mapX(particle.radius * 0.78f),
            center = head,
        )
    }
}

private fun dinoFrameForState(
    state: DinoRunnerState,
    assets: DinoRunnerAssets,
): DinoRenderedFrame = when {
    state.phase == DinoRunnerPhase.GAME_OVER -> DinoRenderedFrame(
        image = assets.dead,
        crop = DEAD_SOURCE_CROP,
    )
    state.ducking -> DinoRenderedFrame(assets.duck[state.runFrame])
    state.dinoVerticalOffset > 0f -> DinoRenderedFrame(assets.jump)
    state.phase == DinoRunnerPhase.IDLE -> DinoRenderedFrame(
        image = assets.start,
        crop = START_SOURCE_CROP,
    )
    else -> DinoRenderedFrame(assets.run[state.runFrame])
}

private fun DrawScope.drawWorldSprite(
    image: ImageBitmap,
    bounds: DinoWorldRect,
    viewport: DinoViewport,
    colorFilter: ColorFilter?,
    sourceCrop: DinoSourceCrop = DinoSourceCrop(),
) {
    val pixels = viewport.map(bounds)
    drawImage(
        image = image,
        srcOffset = sourceCrop.offset(),
        srcSize = sourceCrop.sizeFor(image),
        dstOffset = IntOffset(
            x = pixels.left.roundToInt(),
            y = pixels.top.roundToInt(),
        ),
        dstSize = IntSize(
            width = (pixels.right - pixels.left).roundToInt().coerceAtLeast(1),
            height = (pixels.bottom - pixels.top).roundToInt().coerceAtLeast(1),
        ),
        colorFilter = colorFilter,
        filterQuality = FilterQuality.None,
    )
}

private class DinoRunnerSoundController(context: Context) {
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val press = soundPool.load(context, R.raw.dino_button_press, 1)
    private val hit = soundPool.load(context, R.raw.dino_hit, 1)
    private val score = soundPool.load(context, R.raw.dino_score, 1)

    fun playPress() = play(press)

    fun playHit() = play(hit)

    fun playScore() = play(score)

    fun release() {
        soundPool.release()
    }

    private fun play(soundId: Int) {
        if (soundId != 0) {
            soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
        }
    }
}

private val INVERTED_SPRITE_FILTER = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)
