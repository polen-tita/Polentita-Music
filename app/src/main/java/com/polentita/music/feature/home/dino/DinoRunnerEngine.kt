package com.polentita.music.feature.home.dino

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

internal enum class DinoRunnerPhase {
    IDLE,
    RUNNING,
    PAUSED,
    GAME_OVER,
}

enum class DinoRunnerAmbientPhase {
    DAY,
    SUNSET,
    NIGHT,
    AURORA,
    ECLIPSE,
    DEEP_SPACE,
    NEBULA,
    HYPERSPACE,
    SINGULARITY,
    SUPERNOVA,
}

data class DinoRunnerAmbientState(
    val active: Boolean = false,
    val phase: DinoRunnerAmbientPhase = DinoRunnerAmbientPhase.DAY,
    val globalChromeActive: Boolean = false,
)

internal enum class DinoObstacleKind {
    SMALL_CACTUS,
    LARGE_CACTUS,
    BIRD,
}

internal enum class DinoBirdLane {
    HIGH,
    MIDDLE,
    LOW,
}

internal data class DinoWorldRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun overlaps(other: DinoWorldRect): Boolean =
        left < other.right &&
            right > other.left &&
            top < other.bottom &&
            bottom > other.top
}

internal data class DinoPoseMetrics(
    val width: Float,
    val height: Float,
    val footOverlap: Float,
)

internal object DinoRunnerGeometry {
    const val WORLD_WIDTH = 600f
    const val WORLD_HEIGHT = 180f
    const val GROUND_Y = 146f
    const val DINO_X = 100f
    const val GROUND_FRACTION = 0.85f
    const val TRACK_VISIBLE_LINE_OFFSET_Y = 8f
    const val TRACK_TOP_Y = GROUND_Y - TRACK_VISIBLE_LINE_OFFSET_Y
    const val CACTUS_BASE_Y = GROUND_Y + 4f
    const val OBSTACLE_SPAWN_X = WORLD_WIDTH + 120f

    val standingPose = DinoPoseMetrics(
        width = 68f,
        height = 73f,
        footOverlap = 3f,
    )
    val duckingPose = DinoPoseMetrics(
        width = 75f,
        height = 38f,
        footOverlap = 3f,
    )

    fun dinoDrawBounds(
        dinoVerticalOffset: Float,
        ducking: Boolean,
    ): DinoWorldRect {
        val pose = if (ducking) duckingPose else standingPose
        val bottom = GROUND_Y + pose.footOverlap - dinoVerticalOffset
        return DinoWorldRect(
            left = DINO_X,
            top = bottom - pose.height,
            right = DINO_X + pose.width,
            bottom = bottom,
        )
    }

    fun dinoHitBoxes(
        dinoVerticalOffset: Float,
        ducking: Boolean,
    ): List<DinoWorldRect> {
        val drawBounds = dinoDrawBounds(dinoVerticalOffset, ducking)
        return if (ducking) {
            listOf(
                DinoWorldRect(
                    left = drawBounds.left + 7f,
                    top = drawBounds.top + 4f,
                    right = drawBounds.right - 2f,
                    bottom = drawBounds.top + 27f,
                ),
                DinoWorldRect(
                    left = drawBounds.left + 21f,
                    top = drawBounds.top + 21f,
                    right = drawBounds.left + 55f,
                    bottom = drawBounds.bottom - 2f,
                ),
            )
        } else {
            listOf(
                DinoWorldRect(
                    left = drawBounds.left + 19f,
                    top = drawBounds.top + 3f,
                    right = drawBounds.right - 2f,
                    bottom = drawBounds.top + 47f,
                ),
                DinoWorldRect(
                    left = drawBounds.left + 12f,
                    top = drawBounds.top + 36f,
                    right = drawBounds.left + 51f,
                    bottom = drawBounds.bottom - 2f,
                ),
            )
        }
    }

    fun birdTop(lane: DinoBirdLane): Float = when (lane) {
        DinoBirdLane.HIGH -> 8f
        DinoBirdLane.MIDDLE -> 48f
        DinoBirdLane.LOW -> 84f
    }

    fun obstacleHitBoxes(obstacle: DinoRunnerObstacle): List<DinoWorldRect> =
        when (obstacle.kind) {
            DinoObstacleKind.BIRD -> listOf(
                DinoWorldRect(
                    left = obstacle.x + 22f,
                    top = obstacle.y + 22f,
                    right = obstacle.x + obstacle.width - 8f,
                    bottom = obstacle.y + 48f,
                ),
            )

            DinoObstacleKind.SMALL_CACTUS,
            DinoObstacleKind.LARGE_CACTUS,
            -> cactusHitBoxes(obstacle)
        }

    fun collides(
        obstacle: DinoRunnerObstacle,
        dinoVerticalOffset: Float,
        ducking: Boolean,
    ): Boolean {
        val dinoBoxes = dinoHitBoxes(dinoVerticalOffset, ducking)
        val obstacleBoxes = obstacleHitBoxes(obstacle)
        return dinoBoxes.any { dino -> obstacleBoxes.any(dino::overlaps) }
    }

    private fun cactusHitBoxes(obstacle: DinoRunnerObstacle): List<DinoWorldRect> {
        val cactusCount = (obstacle.variant + 1).coerceIn(1, 3)
        val segmentWidth = obstacle.width / cactusCount
        return List(cactusCount) { index ->
            val segmentLeft = obstacle.x + segmentWidth * index
            DinoWorldRect(
                left = segmentLeft + min(4f, segmentWidth * 0.10f),
                top = obstacle.y + 7f,
                right = segmentLeft + segmentWidth - min(4f, segmentWidth * 0.10f),
                bottom = obstacle.y + obstacle.height - 2f,
            )
        }
    }
}

internal data class DinoRunnerObstacle(
    val kind: DinoObstacleKind,
    val variant: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val frame: Int = 0,
    val birdLane: DinoBirdLane? = null,
)

internal data class DinoRunnerCloud(
    val x: Float,
    val y: Float,
)

internal data class DinoSpawnHistory(
    val totalObstacles: Int = 0,
    val groundObstaclesSinceBird: Int = 0,
    val birdIntroduced: Boolean = false,
)

internal data class DinoSpawnDecision(
    val obstacle: DinoRunnerObstacle,
    val history: DinoSpawnHistory,
)

internal class DinoObstacleDirector(
    private val random: Random,
) {
    fun create(
        score: Int,
        history: DinoSpawnHistory,
    ): DinoSpawnDecision {
        val birdUnlocked = score >= DinoRunnerEngine.BIRD_UNLOCK_SCORE
        val introduceBird = birdUnlocked && !history.birdIntroduced
        val birdAllowed = birdUnlocked && history.groundObstaclesSinceBird >= 2
        val forceBird = birdAllowed && history.groundObstaclesSinceBird >= 5
        val chooseBird = introduceBird || forceBird || (birdAllowed && random.nextFloat() < 0.30f)

        val obstacle = if (chooseBird) {
            createBird(firstBird = introduceBird)
        } else {
            createCactus(score, history.totalObstacles)
        }
        val spawnedBird = obstacle.kind == DinoObstacleKind.BIRD
        return DinoSpawnDecision(
            obstacle = obstacle,
            history = DinoSpawnHistory(
                totalObstacles = history.totalObstacles + 1,
                groundObstaclesSinceBird = if (spawnedBird) {
                    0
                } else {
                    history.groundObstaclesSinceBird + 1
                },
                birdIntroduced = history.birdIntroduced || spawnedBird,
            ),
        )
    }

    fun nextDelayMs(): Float = random.nextInt(
        DinoRunnerEngine.MIN_SPAWN_MS.toInt(),
        DinoRunnerEngine.MAX_SPAWN_MS.toInt() + 1,
    ).toFloat()

    private fun createBird(firstBird: Boolean): DinoRunnerObstacle {
        val lane = if (firstBird) {
            DinoBirdLane.MIDDLE
        } else {
            DinoBirdLane.entries[random.nextInt(DinoBirdLane.entries.size)]
        }
        return DinoRunnerObstacle(
            kind = DinoObstacleKind.BIRD,
            variant = 0,
            x = DinoRunnerGeometry.OBSTACLE_SPAWN_X,
            y = DinoRunnerGeometry.birdTop(lane),
            width = BIRD_WIDTH,
            height = BIRD_HEIGHT,
            birdLane = lane,
        )
    }

    private fun createCactus(
        score: Int,
        obstacleCount: Int,
    ): DinoRunnerObstacle {
        val kind = when (obstacleCount) {
            0 -> DinoObstacleKind.SMALL_CACTUS
            1 -> DinoObstacleKind.LARGE_CACTUS
            else -> if (random.nextBoolean()) {
                DinoObstacleKind.SMALL_CACTUS
            } else {
                DinoObstacleKind.LARGE_CACTUS
            }
        }
        val sizes = if (kind == DinoObstacleKind.SMALL_CACTUS) {
            SMALL_CACTUS_SIZES
        } else {
            LARGE_CACTUS_SIZES
        }
        val variant = if (obstacleCount < 2 || score < MULTI_CACTUS_UNLOCK_SCORE) {
            0
        } else {
            random.nextInt(sizes.size)
        }
        val (width, height) = sizes[variant]
        return DinoRunnerObstacle(
            kind = kind,
            variant = variant,
            x = DinoRunnerGeometry.OBSTACLE_SPAWN_X,
            y = DinoRunnerGeometry.CACTUS_BASE_Y - height,
            width = width,
            height = height,
        )
    }

    private companion object {
        const val MULTI_CACTUS_UNLOCK_SCORE = 60
        const val BIRD_WIDTH = 96f
        const val BIRD_HEIGHT = 66f

        val SMALL_CACTUS_SIZES = arrayOf(
            40f to 71f,
            68f to 71f,
            105f to 71f,
        )
        val LARGE_CACTUS_SIZES = arrayOf(
            48f to 95f,
            99f to 95f,
            102f to 95f,
        )
    }
}

internal data class DinoRunnerState(
    val phase: DinoRunnerPhase = DinoRunnerPhase.IDLE,
    val dinoVerticalOffset: Float = 0f,
    val dinoVelocity: Float = 0f,
    val ducking: Boolean = false,
    val runFrame: Int = 0,
    val obstacles: List<DinoRunnerObstacle> = emptyList(),
    val clouds: List<DinoRunnerCloud> = DinoRunnerEngine.initialClouds(),
    val spawnInMs: Float = DinoRunnerEngine.FIRST_SPAWN_MS,
    val spawnHistory: DinoSpawnHistory = DinoSpawnHistory(),
    val distance: Float = 0f,
    val score: Int = 0,
    val highScore: Int = 0,
    val highScoreAtRunStart: Int = highScore,
    val speed: Float = DinoRunnerEngine.INITIAL_SPEED,
    val elapsedMs: Long = 0L,
) {
    val isNewHighScore: Boolean
        get() = phase == DinoRunnerPhase.GAME_OVER && score > highScoreAtRunStart
}

internal class DinoRunnerEngine(
    random: Random = Random.Default,
    initialHighScore: Int = 0,
    private val invincible: Boolean = false,
) {
    companion object {
        const val WORLD_WIDTH = DinoRunnerGeometry.WORLD_WIDTH
        const val WORLD_HEIGHT = DinoRunnerGeometry.WORLD_HEIGHT
        const val GROUND_Y = DinoRunnerGeometry.GROUND_Y
        const val DINO_X = DinoRunnerGeometry.DINO_X
        const val INITIAL_SPEED = 210f
        const val PRE_EPIC_MAX_SPEED = 680f
        const val MAX_SPEED = 900f
        const val SPEED_GAIN_PER_SCORE = 0.11f
        const val EPIC_SPEED_GAIN_PER_SCORE = 0.022f
        const val FIRST_SPAWN_MS = 1_200f
        const val MIN_SPAWN_MS = 1_150f
        const val MAX_SPAWN_MS = 1_550f
        const val MIN_OBSTACLE_GAP = 300f
        const val MIN_CLEAR_TIME_SECONDS = 1.20f
        const val SCORE_SOUND_STEP = 1_000
        const val SUNSET_SCORE = 500
        const val BIRD_UNLOCK_SCORE = 120
        const val NIGHT_SCORE = 1_500
        const val AURORA_SCORE = 3_000
        const val ECLIPSE_SCORE = 4_500
        const val EPIC_MODE_SCORE = 5_000
        const val DEEP_SPACE_SCORE = 7_000
        const val NEBULA_SCORE = 9_000
        const val HYPERSPACE_SCORE = 11_000
        const val SINGULARITY_SCORE = 13_000
        const val SUPERNOVA_SCORE = 15_000
        const val ASCENT_GRAVITY = 1_100f
        const val DESCENT_GRAVITY = 2_300f
        const val JUMP_VELOCITY = 680f
        const val FIXED_STEP_MS = 16L

        private const val SCORE_DIVISOR = 11f
        private const val MAX_FRAME_DELTA_MS = 96L

        fun initialClouds(): List<DinoRunnerCloud> = listOf(
            DinoRunnerCloud(x = 54f, y = -112f),
            DinoRunnerCloud(x = 216f, y = -78f),
            DinoRunnerCloud(x = 382f, y = -130f),
            DinoRunnerCloud(x = 548f, y = -92f),
        )

        fun shouldPlayScoreSound(previousScore: Int, currentScore: Int): Boolean =
            currentScore / SCORE_SOUND_STEP > previousScore / SCORE_SOUND_STEP

        fun ambientPhaseForScore(score: Int): DinoRunnerAmbientPhase = when {
            score >= SUPERNOVA_SCORE -> DinoRunnerAmbientPhase.SUPERNOVA
            score >= SINGULARITY_SCORE -> DinoRunnerAmbientPhase.SINGULARITY
            score >= HYPERSPACE_SCORE -> DinoRunnerAmbientPhase.HYPERSPACE
            score >= NEBULA_SCORE -> DinoRunnerAmbientPhase.NEBULA
            score >= DEEP_SPACE_SCORE -> DinoRunnerAmbientPhase.DEEP_SPACE
            score >= ECLIPSE_SCORE -> DinoRunnerAmbientPhase.ECLIPSE
            score >= AURORA_SCORE -> DinoRunnerAmbientPhase.AURORA
            score >= NIGHT_SCORE -> DinoRunnerAmbientPhase.NIGHT
            score >= SUNSET_SCORE -> DinoRunnerAmbientPhase.SUNSET
            else -> DinoRunnerAmbientPhase.DAY
        }

        fun ambientStateFor(state: DinoRunnerState): DinoRunnerAmbientState {
            val active = state.phase == DinoRunnerPhase.RUNNING ||
                state.phase == DinoRunnerPhase.PAUSED
            return DinoRunnerAmbientState(
                active = active,
                phase = ambientPhaseForScore(state.score),
                globalChromeActive = active && state.score >= EPIC_MODE_SCORE,
            )
        }

        fun speedForScore(score: Int): Float {
            val safeScore = score.coerceAtLeast(0)
            if (safeScore <= EPIC_MODE_SCORE) {
                return min(
                    PRE_EPIC_MAX_SPEED,
                    INITIAL_SPEED + safeScore * SPEED_GAIN_PER_SCORE,
                )
            }
            return min(
                MAX_SPEED,
                PRE_EPIC_MAX_SPEED +
                    (safeScore - EPIC_MODE_SCORE) * EPIC_SPEED_GAIN_PER_SCORE,
            )
        }
    }

    private val random = random
    private val obstacleDirector = DinoObstacleDirector(random)
    private var accumulatedMs = 0L

    var state: DinoRunnerState = DinoRunnerState(
        highScore = initialHighScore.coerceAtLeast(0),
        highScoreAtRunStart = initialHighScore.coerceAtLeast(0),
    )
        private set

    fun start(): Boolean {
        if (state.phase == DinoRunnerPhase.RUNNING) return false

        val best = max(state.highScore, state.score)
        accumulatedMs = 0L
        state = DinoRunnerState(
            phase = DinoRunnerPhase.RUNNING,
            highScore = best,
            highScoreAtRunStart = best,
        )
        return true
    }

    fun updateHighScore(candidate: Int) {
        val best = max(state.highScore, candidate.coerceAtLeast(0))
        state = if (state.phase == DinoRunnerPhase.IDLE) {
            state.copy(
                highScore = best,
                highScoreAtRunStart = best,
            )
        } else {
            state.copy(highScore = best)
        }
    }

    fun pause() {
        if (state.phase == DinoRunnerPhase.RUNNING) {
            accumulatedMs = 0L
            state = state.copy(phase = DinoRunnerPhase.PAUSED, ducking = false)
        }
    }

    fun resume() {
        if (state.phase == DinoRunnerPhase.PAUSED) {
            accumulatedMs = 0L
            state = state.copy(phase = DinoRunnerPhase.RUNNING)
        }
    }

    fun jump(): Boolean {
        if (state.phase != DinoRunnerPhase.RUNNING || state.dinoVerticalOffset > 0f) {
            return false
        }

        state = state.copy(
            dinoVelocity = JUMP_VELOCITY,
            ducking = false,
        )
        return true
    }

    fun setDucking(ducking: Boolean) {
        if (
            state.phase != DinoRunnerPhase.RUNNING ||
            state.dinoVerticalOffset > 1f ||
            state.dinoVelocity != 0f
        ) {
            return
        }
        state = state.copy(ducking = ducking)
    }

    fun tick(deltaMs: Long) {
        if (state.phase != DinoRunnerPhase.RUNNING || deltaMs <= 0L) return

        accumulatedMs += deltaMs.coerceAtMost(MAX_FRAME_DELTA_MS)
        while (accumulatedMs >= FIXED_STEP_MS && state.phase == DinoRunnerPhase.RUNNING) {
            step(FIXED_STEP_MS / 1_000f)
            accumulatedMs -= FIXED_STEP_MS
        }
    }

    private fun step(deltaSeconds: Float) {
        val gravity = if (state.dinoVelocity > 0f) ASCENT_GRAVITY else DESCENT_GRAVITY
        var verticalOffset = state.dinoVerticalOffset +
            state.dinoVelocity * deltaSeconds -
            0.5f * gravity * deltaSeconds * deltaSeconds
        var velocity = state.dinoVelocity - gravity * deltaSeconds
        if (verticalOffset <= 0f) {
            verticalOffset = 0f
            velocity = 0f
        }

        val movementSpeed = speedForScore(state.score)
        val distance = state.distance + movementSpeed * deltaSeconds / SCORE_DIVISOR
        val score = distance.toInt()
        val nextSpeed = speedForScore(score)
        val stepMs = (deltaSeconds * 1_000f).toLong()
        val elapsedMs = state.elapsedMs + stepMs
        val runFrame = ((elapsedMs / 100L) % 2L).toInt()

        val obstacles = state.obstacles
            .map { obstacle ->
                obstacle.copy(
                    x = obstacle.x - movementSpeed * deltaSeconds,
                    frame = if (obstacle.kind == DinoObstacleKind.BIRD) runFrame else obstacle.frame,
                )
            }
            .filter { it.x + it.width > -24f }
            .toMutableList()

        var spawnInMs = state.spawnInMs - stepMs.toFloat()
        var spawnHistory = state.spawnHistory
        val newestObstacle = obstacles.maxByOrNull(DinoRunnerObstacle::x)
        val requiredGap = max(MIN_OBSTACLE_GAP, movementSpeed * MIN_CLEAR_TIME_SECONDS)
        val availableGap = if (newestObstacle == null) {
            Float.POSITIVE_INFINITY
        } else {
            DinoRunnerGeometry.OBSTACLE_SPAWN_X - (newestObstacle.x + newestObstacle.width)
        }
        if (spawnInMs <= 0f && availableGap >= requiredGap) {
            val decision = obstacleDirector.create(score, spawnHistory)
            obstacles += decision.obstacle
            spawnHistory = decision.history
            spawnInMs = obstacleDirector.nextDelayMs()
        } else if (spawnInMs <= 0f) {
            spawnInMs = 0f
        }

        val clouds = state.clouds.map { cloud ->
            val nextX = cloud.x - movementSpeed * deltaSeconds * 0.12f
            if (nextX < -100f) {
                DinoRunnerCloud(
                    x = WORLD_WIDTH + random.nextInt(30, 180),
                    y = random.nextInt(-134, -61).toFloat(),
                )
            } else {
                cloud.copy(x = nextX)
            }
        }

        val nextState = state.copy(
            dinoVerticalOffset = verticalOffset,
            dinoVelocity = velocity,
            obstacles = obstacles,
            clouds = clouds,
            spawnInMs = spawnInMs,
            spawnHistory = spawnHistory,
            distance = distance,
            score = score,
            highScore = max(state.highScore, score),
            speed = nextSpeed,
            elapsedMs = elapsedMs,
            runFrame = runFrame,
        )

        state = if (!invincible && nextState.obstacles.any { obstacle ->
            DinoRunnerGeometry.collides(
                obstacle = obstacle,
                dinoVerticalOffset = nextState.dinoVerticalOffset,
                ducking = nextState.ducking,
            )
        }) {
            accumulatedMs = 0L
            nextState.copy(
                phase = DinoRunnerPhase.GAME_OVER,
                ducking = false,
            )
        } else {
            nextState
        }
    }
}
