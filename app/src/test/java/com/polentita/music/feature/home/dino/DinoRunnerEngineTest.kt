package com.polentita.music.feature.home.dino

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DinoRunnerEngineTest {
    @Test
    fun `el sonido de puntuacion se reproduce solamente cada mil puntos`() {
        assertFalse(DinoRunnerEngine.shouldPlayScoreSound(0, 999))
        assertTrue(DinoRunnerEngine.shouldPlayScoreSound(999, 1_000))
        assertFalse(DinoRunnerEngine.shouldPlayScoreSound(1_000, 1_999))
        assertTrue(DinoRunnerEngine.shouldPlayScoreSound(1_999, 2_000))
        assertEquals(1_000, DinoRunnerEngine.SCORE_SOUND_STEP)
    }

    @Test
    fun `el cielo comienza con cuatro nubes separadas y fuera del corredor de salto`() {
        val clouds = DinoRunnerEngine.initialClouds()

        assertEquals(4, clouds.size)
        assertEquals(4, clouds.map(DinoRunnerCloud::x).distinct().size)
        assertTrue(clouds.all { it.y < -60f })
    }

    @Test
    fun `el ambiente progresa por todas sus etapas`() {
        assertEquals(DinoRunnerAmbientPhase.DAY, DinoRunnerEngine.ambientPhaseForScore(0))
        assertEquals(
            DinoRunnerAmbientPhase.DAY,
            DinoRunnerEngine.ambientPhaseForScore(DinoRunnerEngine.SUNSET_SCORE - 1),
        )
        assertEquals(
            DinoRunnerAmbientPhase.SUNSET,
            DinoRunnerEngine.ambientPhaseForScore(DinoRunnerEngine.SUNSET_SCORE),
        )
        assertEquals(
            DinoRunnerAmbientPhase.NIGHT,
            DinoRunnerEngine.ambientPhaseForScore(DinoRunnerEngine.NIGHT_SCORE),
        )
        assertEquals(
            DinoRunnerAmbientPhase.AURORA,
            DinoRunnerEngine.ambientPhaseForScore(DinoRunnerEngine.AURORA_SCORE),
        )
        assertEquals(
            DinoRunnerAmbientPhase.ECLIPSE,
            DinoRunnerEngine.ambientPhaseForScore(DinoRunnerEngine.ECLIPSE_SCORE),
        )
        assertEquals(
            DinoRunnerAmbientPhase.DEEP_SPACE,
            DinoRunnerEngine.ambientPhaseForScore(DinoRunnerEngine.DEEP_SPACE_SCORE),
        )
        assertEquals(
            DinoRunnerAmbientPhase.NEBULA,
            DinoRunnerEngine.ambientPhaseForScore(DinoRunnerEngine.NEBULA_SCORE),
        )
        assertEquals(
            DinoRunnerAmbientPhase.HYPERSPACE,
            DinoRunnerEngine.ambientPhaseForScore(DinoRunnerEngine.HYPERSPACE_SCORE),
        )
        assertEquals(
            DinoRunnerAmbientPhase.SINGULARITY,
            DinoRunnerEngine.ambientPhaseForScore(DinoRunnerEngine.SINGULARITY_SCORE),
        )
        assertEquals(
            DinoRunnerAmbientPhase.SUPERNOVA,
            DinoRunnerEngine.ambientPhaseForScore(DinoRunnerEngine.SUPERNOVA_SCORE),
        )
    }

    @Test
    fun `el chrome global se activa en cinco mil y vuelve a normal al morir`() {
        val runningBeforeEpic = DinoRunnerState(
            phase = DinoRunnerPhase.RUNNING,
            score = DinoRunnerEngine.EPIC_MODE_SCORE - 1,
        )
        val runningEpic = runningBeforeEpic.copy(score = DinoRunnerEngine.EPIC_MODE_SCORE)
        val gameOverEpic = runningEpic.copy(phase = DinoRunnerPhase.GAME_OVER)

        assertFalse(DinoRunnerEngine.ambientStateFor(runningBeforeEpic).globalChromeActive)
        assertTrue(DinoRunnerEngine.ambientStateFor(runningEpic).globalChromeActive)
        assertFalse(DinoRunnerEngine.ambientStateFor(gameOverEpic).active)
        assertFalse(DinoRunnerEngine.ambientStateFor(gameOverEpic).globalChromeActive)
    }

    @Test
    fun `el runner permanece detenido hasta iniciar`() {
        val engine = DinoRunnerEngine(Random(1))

        engine.tick(1_000L)

        assertEquals(DinoRunnerPhase.IDLE, engine.state.phase)
        assertEquals(0, engine.state.score)
        assertTrue(engine.start())
        assertEquals(DinoRunnerPhase.RUNNING, engine.state.phase)
        assertFalse(engine.start())
    }

    @Test
    fun `todas las posturas comparten el suelo y una posicion estable`() {
        val standing = DinoRunnerGeometry.dinoDrawBounds(
            dinoVerticalOffset = 0f,
            ducking = false,
        )
        val ducking = DinoRunnerGeometry.dinoDrawBounds(
            dinoVerticalOffset = 0f,
            ducking = true,
        )

        assertEquals(100f, standing.left, 0.001f)
        assertEquals(100f, ducking.left, 0.001f)
        assertEquals(DinoRunnerEngine.GROUND_Y + 3f, standing.bottom, 0.001f)
        assertEquals(standing.bottom, ducking.bottom, 0.001f)
        assertEquals(68f, standing.width, 0.001f)
        assertEquals(73f, standing.height, 0.001f)
        assertEquals(75f, ducking.width, 0.001f)
        assertEquals(38f, ducking.height, 0.001f)
        assertEquals(
            DinoRunnerEngine.GROUND_Y,
            DinoRunnerGeometry.TRACK_TOP_Y + DinoRunnerGeometry.TRACK_VISIBLE_LINE_OFFSET_Y,
            0.001f,
        )
        assertEquals(
            DinoRunnerEngine.GROUND_Y + 4f,
            DinoRunnerGeometry.CACTUS_BASE_Y,
            0.001f,
        )
    }

    @Test
    fun `el salto tiene apice alto y una caida mas rapida`() {
        val engine = DinoRunnerEngine(Random(2))
        engine.start()
        assertTrue(engine.jump())

        var maxOffset = 0f
        var apexAtMs = 0L
        var landingAtMs = 0L
        while (landingAtMs == 0L && engine.state.elapsedMs < 1_200L) {
            engine.tick(DinoRunnerEngine.FIXED_STEP_MS)
            if (engine.state.dinoVerticalOffset > maxOffset) {
                maxOffset = engine.state.dinoVerticalOffset
                apexAtMs = engine.state.elapsedMs
            }
            if (
                engine.state.elapsedMs > 0L &&
                engine.state.dinoVerticalOffset == 0f &&
                engine.state.dinoVelocity == 0f
            ) {
                landingAtMs = engine.state.elapsedMs
            }
        }

        assertTrue(maxOffset in 207f..213f)
        assertTrue(apexAtMs in 590L..660L)
        assertTrue(landingAtMs in 1_010L..1_100L)
        assertTrue(landingAtMs - apexAtMs < apexAtMs)
        assertTrue(
            DinoRunnerEngine.INITIAL_SPEED * landingAtMs / 1_000f >
                102f + standingCollisionWidth() + 40f,
        )
    }

    @Test
    fun `un salto al borde supera por completo tres cactus grandes`() {
        val engine = DinoRunnerEngine(Random(23))
        engine.start()
        assertTrue(engine.jump())

        var cactus = DinoRunnerObstacle(
            kind = DinoObstacleKind.LARGE_CACTUS,
            variant = 2,
            x = DinoRunnerGeometry.dinoDrawBounds(0f, ducking = false).right + 16f,
            y = DinoRunnerGeometry.CACTUS_BASE_Y - 95f,
            width = 102f,
            height = 95f,
        )
        var crossedCompletely = false

        while (engine.state.elapsedMs < 1_200L && !crossedCompletely) {
            engine.tick(DinoRunnerEngine.FIXED_STEP_MS)
            cactus = cactus.copy(
                x = cactus.x - engine.state.speed * DinoRunnerEngine.FIXED_STEP_MS / 1_000f,
            )
            assertFalse(
                DinoRunnerGeometry.collides(
                    obstacle = cactus,
                    dinoVerticalOffset = engine.state.dinoVerticalOffset,
                    ducking = false,
                ),
            )
            crossedCompletely = cactus.x + cactus.width <
                DinoRunnerGeometry.dinoHitBoxes(
                    dinoVerticalOffset = engine.state.dinoVerticalOffset,
                    ducking = false,
                ).minOf(DinoWorldRect::left)
        }

        assertTrue(crossedCompletely)
        assertTrue(engine.state.dinoVerticalOffset > 0f)
    }

    @Test
    fun `el salto despeja un cactus alto y colisiona al estar en el suelo`() {
        val cactus = DinoRunnerObstacle(
            kind = DinoObstacleKind.LARGE_CACTUS,
            variant = 0,
            x = 150f,
            y = DinoRunnerGeometry.CACTUS_BASE_Y - 95f,
            width = 48f,
            height = 95f,
        )

        assertTrue(
            DinoRunnerGeometry.collides(
                obstacle = cactus,
                dinoVerticalOffset = 0f,
                ducking = false,
            ),
        )
        assertFalse(
            DinoRunnerGeometry.collides(
                obstacle = cactus,
                dinoVerticalOffset = 136f,
                ducking = false,
            ),
        )
    }

    @Test
    fun `la hitbox excluye la cola y detecta el contacto del cuerpo`() {
        val besideTail = DinoRunnerObstacle(
            kind = DinoObstacleKind.SMALL_CACTUS,
            variant = 0,
            x = 70f,
            y = DinoRunnerGeometry.CACTUS_BASE_Y - 71f,
            width = 40f,
            height = 71f,
        )
        val touchingBody = besideTail.copy(x = 160f)

        assertTrue(
            DinoRunnerGeometry.dinoDrawBounds(0f, ducking = false).overlaps(
                DinoWorldRect(
                    besideTail.x,
                    besideTail.y,
                    besideTail.x + besideTail.width,
                    besideTail.y + besideTail.height,
                ),
            ),
        )
        assertFalse(DinoRunnerGeometry.collides(besideTail, 0f, ducking = false))
        assertTrue(DinoRunnerGeometry.collides(touchingBody, 0f, ducking = false))
    }

    @Test
    fun `los carriles de pajaro exigen la accion correcta`() {
        fun bird(lane: DinoBirdLane) = DinoRunnerObstacle(
            kind = DinoObstacleKind.BIRD,
            variant = 0,
            x = 100f,
            y = DinoRunnerGeometry.birdTop(lane),
            width = 96f,
            height = 66f,
            birdLane = lane,
        )

        assertFalse(DinoRunnerGeometry.collides(bird(DinoBirdLane.HIGH), 0f, ducking = false))
        assertTrue(DinoRunnerGeometry.collides(bird(DinoBirdLane.MIDDLE), 0f, ducking = false))
        assertFalse(DinoRunnerGeometry.collides(bird(DinoBirdLane.MIDDLE), 0f, ducking = true))
        assertTrue(DinoRunnerGeometry.collides(bird(DinoBirdLane.LOW), 0f, ducking = true))
    }

    @Test
    fun `agacharse solo se activa en el suelo`() {
        val engine = DinoRunnerEngine(Random(3))
        engine.start()

        engine.setDucking(true)
        assertTrue(engine.state.ducking)

        assertTrue(engine.jump())
        engine.setDucking(true)
        assertFalse(engine.state.ducking)
    }

    @Test
    fun `la puntuacion y velocidad avanzan sin superar el limite`() {
        val engine = DinoRunnerEngine(Random(4))
        engine.start()

        repeat(20) { engine.tick(16L) }

        assertTrue(engine.state.score > 0)
        assertTrue(engine.state.speed > DinoRunnerEngine.INITIAL_SPEED)
        assertTrue(engine.state.speed <= DinoRunnerEngine.MAX_SPEED)
        assertTrue(engine.state.highScore >= engine.state.score)
    }

    @Test
    fun `la dificultad progresa hasta quince mil y despues conserva un tope justo`() {
        val initial = DinoRunnerEngine.speedForScore(0)
        val atOneThousand = DinoRunnerEngine.speedForScore(1_000)
        val atThreeThousand = DinoRunnerEngine.speedForScore(3_000)
        val atFourThousand = DinoRunnerEngine.speedForScore(4_000)
        val atFiveThousand = DinoRunnerEngine.speedForScore(5_000)
        val atSevenThousand = DinoRunnerEngine.speedForScore(7_000)
        val atNineThousand = DinoRunnerEngine.speedForScore(9_000)
        val atElevenThousand = DinoRunnerEngine.speedForScore(11_000)
        val atThirteenThousand = DinoRunnerEngine.speedForScore(13_000)
        val atFifteenThousand = DinoRunnerEngine.speedForScore(15_000)
        val capped = DinoRunnerEngine.speedForScore(Int.MAX_VALUE)

        assertEquals(DinoRunnerEngine.INITIAL_SPEED, initial, 0.001f)
        assertEquals(320f, atOneThousand, 0.001f)
        assertEquals(540f, atThreeThousand, 0.001f)
        assertEquals(650f, atFourThousand, 0.001f)
        assertTrue(atThreeThousand > initial * 2.5f)
        assertTrue(atThreeThousand > atOneThousand)
        assertTrue(atFourThousand > atThreeThousand)
        assertEquals(680f, atFiveThousand, 0.001f)
        assertEquals(724f, atSevenThousand, 0.001f)
        assertEquals(768f, atNineThousand, 0.001f)
        assertEquals(812f, atElevenThousand, 0.001f)
        assertEquals(856f, atThirteenThousand, 0.001f)
        assertEquals(900f, atFifteenThousand, 0.001f)
        assertEquals(DinoRunnerEngine.MAX_SPEED, capped, 0.001f)
    }

    @Test
    fun `el record inicial se conserva y una partida nueva identifica si lo supera`() {
        val engine = DinoRunnerEngine(Random(29), initialHighScore = 4_000)

        assertEquals(4_000, engine.state.highScore)
        assertTrue(engine.start())
        assertEquals(4_000, engine.state.highScoreAtRunStart)

        engine.updateHighScore(3_000)
        assertEquals(4_000, engine.state.highScore)
        engine.updateHighScore(4_500)
        assertEquals(4_500, engine.state.highScore)

        val previousRecord = DinoRunnerState(
            phase = DinoRunnerPhase.GAME_OVER,
            score = 3_999,
            highScore = 4_000,
            highScoreAtRunStart = 4_000,
        )
        assertFalse(previousRecord.isNewHighScore)
        assertTrue(
            previousRecord.copy(
                score = 4_500,
                highScore = 4_500,
            ).isNewHighScore,
        )
    }

    @Test
    fun `los obstaculos nacen completamente fuera del campo`() {
        val engine = DinoRunnerEngine(Random(7))
        engine.start()

        repeat(75) { engine.tick(16L) }

        val firstObstacle = requireNotNull(engine.state.obstacles.firstOrNull())
        assertEquals(DinoRunnerGeometry.OBSTACLE_SPAWN_X, firstObstacle.x, 0.001f)
        assertTrue(firstObstacle.x > DinoRunnerEngine.WORLD_WIDTH)
    }

    @Test
    fun `dos obstaculos mantienen una separacion visible minima`() {
        val engine = DinoRunnerEngine(Random(11))
        engine.start()

        while (engine.state.obstacles.size < 2 && engine.state.elapsedMs < 3_600L) {
            engine.tick(16L)
        }

        val ordered = engine.state.obstacles.sortedBy(DinoRunnerObstacle::x)
        assertTrue(ordered.size >= 2)
        val older = ordered[0]
        val newer = ordered[1]
        val clearGap = newer.x - (older.x + older.width)
        assertTrue(clearGap >= DinoRunnerEngine.MIN_OBSTACLE_GAP - 1f)
    }

    @Test
    fun `los dos primeros cactus son individuales y el primer pajaro ensena a agacharse`() {
        val director = DinoObstacleDirector(Random(13))
        var history = DinoSpawnHistory()

        val first = director.create(score = 0, history = history)
        history = first.history
        val second = director.create(score = 40, history = history)
        history = second.history
        val bird = director.create(score = DinoRunnerEngine.BIRD_UNLOCK_SCORE, history = history)

        assertEquals(DinoObstacleKind.SMALL_CACTUS, first.obstacle.kind)
        assertEquals(0, first.obstacle.variant)
        assertEquals(DinoObstacleKind.LARGE_CACTUS, second.obstacle.kind)
        assertEquals(0, second.obstacle.variant)
        assertEquals(DinoObstacleKind.BIRD, bird.obstacle.kind)
        assertEquals(DinoBirdLane.MIDDLE, bird.obstacle.birdLane)
    }

    @Test
    fun `despues de un pajaro hay dos cactus y otro aparece en un plazo acotado`() {
        val director = DinoObstacleDirector(Random(17))
        var history = DinoSpawnHistory(
            totalObstacles = 3,
            groundObstaclesSinceBird = 0,
            birdIntroduced = true,
        )
        val followingKinds = mutableListOf<DinoObstacleKind>()

        repeat(6) {
            val decision = director.create(score = 300, history = history)
            history = decision.history
            followingKinds += decision.obstacle.kind
            if (decision.obstacle.kind == DinoObstacleKind.BIRD) return@repeat
        }

        assertTrue(followingKinds.take(2).all { it != DinoObstacleKind.BIRD })
        assertTrue(followingKinds.any { it == DinoObstacleKind.BIRD })
    }

    @Test
    fun `el paso fijo produce el mismo estado con frecuencias distintas`() {
        fun engineAdvancedBy(chunks: List<Long>): DinoRunnerState {
            val engine = DinoRunnerEngine(Random(19))
            engine.start()
            chunks.forEach(engine::tick)
            return engine.state
        }

        val at8Ms = engineAdvancedBy(List(200) { 8L })
        val at16Ms = engineAdvancedBy(List(100) { 16L })
        val at33Ms = engineAdvancedBy(List(48) { 33L } + 16L)
        val at50Ms = engineAdvancedBy(List(32) { 50L })

        listOf(at16Ms, at33Ms, at50Ms).forEach { state ->
            assertEquals(at8Ms.elapsedMs, state.elapsedMs)
            assertEquals(at8Ms.score, state.score)
            assertEquals(at8Ms.speed, state.speed, 0.001f)
            assertEquals(at8Ms.obstacles.size, state.obstacles.size)
            assertEquals(at8Ms.obstacles.first().x, state.obstacles.first().x, 0.001f)
        }
    }

    @Test
    fun `pausar congela el motor y reanudar continua`() {
        val engine = DinoRunnerEngine(Random(5))
        engine.start()
        engine.tick(256L)
        val beforePause = engine.state

        engine.pause()
        engine.tick(1_000L)
        assertEquals(DinoRunnerPhase.PAUSED, engine.state.phase)
        assertEquals(beforePause.score, engine.state.score)
        assertEquals(beforePause.elapsedMs, engine.state.elapsedMs)

        engine.resume()
        engine.tick(256L)
        assertEquals(DinoRunnerPhase.RUNNING, engine.state.phase)
        assertTrue(engine.state.elapsedMs > beforePause.elapsedMs)
    }

    @Test
    fun `la sesion conserva la partida mientras la interfaz no esta visible`() {
        val session = DinoRunnerSession()
        assertTrue(session.start())
        session.tick(512L)
        val beforeLeaving = session.state

        session.pause()
        session.tick(2_000L)

        assertEquals(DinoRunnerPhase.PAUSED, session.state.phase)
        assertEquals(beforeLeaving.score, session.state.score)
        assertEquals(beforeLeaving.elapsedMs, session.state.elapsedMs)
        assertEquals(beforeLeaving.obstacles, session.state.obstacles)

        session.resume()
        session.tick(256L)

        assertEquals(DinoRunnerPhase.RUNNING, session.state.phase)
        assertTrue(session.state.elapsedMs > beforeLeaving.elapsedMs)
    }

    @Test
    fun `una partida sin saltar termina por colision y se puede reiniciar`() {
        val engine = DinoRunnerEngine(Random(6))
        engine.start()

        repeat(200) {
            engine.tick(50L)
            if (engine.state.phase == DinoRunnerPhase.GAME_OVER) return@repeat
        }

        assertEquals(DinoRunnerPhase.GAME_OVER, engine.state.phase)
        val previousHighScore = engine.state.highScore

        assertTrue(engine.start())
        assertEquals(DinoRunnerPhase.RUNNING, engine.state.phase)
        assertEquals(0, engine.state.score)
        assertTrue(engine.state.highScore >= previousHighScore)
    }

    @Test
    fun `el modo inmortal atraviesa obstaculos y conserva la progresion`() {
        val engine = DinoRunnerEngine(
            random = Random(31),
            invincible = true,
        )
        engine.start()

        repeat(400) { engine.tick(50L) }

        assertEquals(DinoRunnerPhase.RUNNING, engine.state.phase)
        assertTrue(engine.state.spawnHistory.totalObstacles >= 5)
        assertTrue(engine.state.score > 300)
        assertTrue(engine.state.speed > DinoRunnerEngine.INITIAL_SPEED)
    }

    private fun standingCollisionWidth(): Float {
        val boxes = DinoRunnerGeometry.dinoHitBoxes(
            dinoVerticalOffset = 0f,
            ducking = false,
        )
        return boxes.maxOf(DinoWorldRect::right) - boxes.minOf(DinoWorldRect::left)
    }
}
