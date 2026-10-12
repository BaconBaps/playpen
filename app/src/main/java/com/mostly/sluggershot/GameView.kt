package com.mostly.sluggershot

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class GameView(context: Context) : View(context) {
    private enum class FlightScreen { PLAYING, BRIEFING, PAUSED, RESULT, COMPLETE }
    private var flightScreen = FlightScreen.PLAYING
    private val flightUi = FlightUi()
    private val records = context.getSharedPreferences("flight_records", Context.MODE_PRIVATE)
    private var bestFlight = records.getInt("best_seconds", 0)
    private var volleysLaunched = 0
    private var titleTime = 0f
    private var pressedUiAction: String? = null

    private enum class InputMode {
        NONE,
        GESTURE_PENDING,
        AIM_SHOT,
        MOVE_SLINGSHOT,
        MODE_BUTTON
    }

    private enum class TouchRole {
        PENDING,
        SHOOT,
        MOVE,
        THRUST
    }

    private data class TouchTrack(
        val pointerId: Int,
        val startX: Float,
        val startY: Float,
        var lastX: Float,
        var lastY: Float,
        val downTimeMs: Long = System.currentTimeMillis(),
        var role: TouchRole = TouchRole.PENDING
    )

    private data class ContactPair(val lowId: Long, val highId: Long)

    private data class BeamPulse(
        val startX: Float,
        val startY: Float,
        val nx: Float,
        val ny: Float,
        val width: Float,
        val powerRatio: Float = 0f,
        var lifeSeconds: Float = BEAM_LIFETIME
    )

    private data class BossShot(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val radius: Float = 15f,
        var active: Boolean = true
    )

    private val slingshot = Slingshot()
    private val haptics = HapticController(context)
    private val sounds = SoundController(context)
    private val tiltThruster = TiltThrusterController(context)
    private val projectiles = mutableListOf<Projectile>()
    private val activeContacts = mutableSetOf<ContactPair>()
    private val enemies = mutableListOf<Enemy>()
    private val destructibleSteel = mutableListOf<DestructibleSteel>()
    private val powerUps = mutableListOf<PowerUpActor>()
    private val collectedPowerUps = linkedSetOf<ShotType>()
    private var runTimeSeconds = 0f
    private var powerUpSpawnTimer = 0f
    private var bombInventory = 0
    private var fuel = 1f
    private var verticalVelocity = 0f
    private var currentThrust = 0f
    private var bossBattle = false
    private var bossSpawnedThisRun = false
    private var bossMusicFadeStarted = false
    private var bossCheckpointAvailable = false
    private var checkpointPlayerMaxHealth = PLAYER_STARTING_MAX_HEALTH
    private var checkpointPlayerHealth = PLAYER_STARTING_MAX_HEALTH
    private var checkpointFuel = 1f
    private var checkpointBombInventory = 0
    private var checkpointChargeEnergy = 1f
    private var checkpointActiveShotType = ShotType.NONE
    private val checkpointCollectedPowerUps = linkedSetOf<ShotType>()
    private var bossIntroActive = false
    private var bossIntroSeconds = 0f
    private var bossVariant = 0
    private var bossFireTimer = 0f
    private var bossChargeTimer = 0f
    private var bossPowerUpTimer = 0f
    private val bossShots = mutableListOf<BossShot>()
    private var bossMessage = ""
    private var bossMessageSeconds = 0f
    private var tapSequenceCount = 0
    private var lastTapUpMs = 0L
    private var gameOver = false
    private var gameOverAtMs = 0L
    private var damageFlashSeconds = 0f
    private var damageShakeSeconds = 0f
    private var bombBlastSeconds = 0f
    private var bombBlastX = 0f
    private var bombBlastY = 0f
    private val starfield = Starfield()
    private val retroViewport = RetroViewport()
    private val atmosphere by lazy { SceneAtmosphere(resources) }
    private var bossTokens = 0
    private var bossesDefeated = 0
    private val arenaWalls = mutableListOf<ArenaWall>()
    private val modeButtons = linkedMapOf<ShotType, RectF>()
    private val gameModeButtons = linkedMapOf<GameMode, RectF>()

    private var nextProjectileId = 1L
    private var nextEnemyId = 1L
    private var lastEnemySpawnMs = 0L
    private var encounterTimer = 0.8f
    private var encounterIndex = 0
    private var playerMaxHealth = PLAYER_STARTING_MAX_HEALTH
    private var playerHealth = playerMaxHealth
    private val enemyPlayerContacts = mutableSetOf<Long>()
    private val solidWallPlayerContacts = mutableSetOf<Int>()
    private val destructiblePlayerContacts = mutableSetOf<Int>()
    private var solidWallSpawnTimer = 0f
    private var destructibleSpawnTimer = 0f
    private var activeShotType = ShotType.NONE
    private var gameMode = GameMode.ENEMY
    private var titleScreen = true
    private var puzzleCameraY = 0f
    private var puzzleWorldHeight = 0f
    private var puzzleLevel = 1
    private var puzzleSelection = false
    private var puzzleUnlocked = records.getInt("puzzle_unlocked", 1).coerceIn(1, PuzzleCampaign.LEVEL_COUNT)
    private var puzzleCompleted = records.getInt("puzzle_completed", 0).coerceIn(0, PuzzleCampaign.LEVEL_COUNT)
    private var puzzleLayout: PuzzleCampaign.Layout? = null
    private var guideCacheKey = ""
    private var guideCache = emptyList<PuzzleTrajectory.Point>()
    private var puzzleResetDelay = 0f
    private val titleShootingButton = RectF()
    private val titlePuzzleButton = RectF()
    private var inputMode = InputMode.NONE
    private val touchTracks = linkedMapOf<Int, TouchTrack>()

    private var shotPointerId = MotionEvent.INVALID_POINTER_ID
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var currentTouchX = 0f
    private var currentTouchY = 0f
    private var fullExtensionStartTimeMs = 0L
    private var heavyShotCharged = false
    private var chargeEnergy = 1f
    private var chargeRechargeDelay = 0f
    private var autoChargeActive = false
    private var heavyChargeHapticSent = false

    private var lastTwoFingerCenterX = 0f
    private var lastAutoFireMs = 0L
    private var autoHasFired = false

    private var beamPulse: BeamPulse? = null
    private val beamContactTime = mutableMapOf<Long, Float>()

    private var lastFrameTime = System.nanoTime()

    private val backgroundPaint = Paint().apply {
        color = Color.rgb(7, 15, 27)
    }

    private val trajectoryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x9970EBE2.toInt()
    }

    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttonTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 22f
        isFakeBoldText = true
    }

    private val beamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x08395B74
        strokeWidth = 2f
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF70EBE2.toInt()
        strokeWidth = 4f
    }
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xDD0D1B2A.toInt()
    }
    private val playerHealthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFB300.toInt()
        textSize = 30f
        typeface = Typeface.DEFAULT_BOLD
    }
    private val playerHealthLostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4A4D50.toInt()
        textSize = 30f
        typeface = Typeface.DEFAULT_BOLD
    }
    private val damageFlashPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lightCorePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        isFocusable = true
        keepScreenOn = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Music starts when a flight launches, rather than behind the menu.
    }



    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        slingshot.initialise(w.toFloat(), h.toFloat())
        buildArena(w.toFloat(), h.toFloat())
        buildFieldObjects(w.toFloat(), h.toFloat())
        starfield.resize(w.toFloat(), h.toFloat())
        buildModeButtons(w.toFloat(), h.toFloat())
        buildGameModeButtons(w.toFloat())
        titleShootingButton.set(w * 0.16f, h * 0.48f, w * 0.84f, h * 0.58f)
        titlePuzzleButton.set(w * 0.16f, h * 0.62f, w * 0.84f, h * 0.72f)
        initialiseEnemies(w.toFloat(), h.toFloat())
    }

    private fun buildArena(w: Float, h: Float) {
        arenaWalls.clear()
        solidWallSpawnTimer = 0.8f
    }

    private fun buildFieldObjects(w: Float, h: Float) {
        destructibleSteel.clear()
        destructibleSpawnTimer = 0.25f
    }

    private fun buildModeButtons(w: Float, h: Float) {
        modeButtons.clear()

        val types = ShotType.entries
        val margin = 12f
        val gap = 8f
        val totalGap = gap * (types.size - 1)
        val buttonWidth = (w - margin * 2f - totalGap) / types.size
        val top = h - MODE_BUTTON_BOTTOM_MARGIN - MODE_BUTTON_HEIGHT
        val bottom = h - MODE_BUTTON_BOTTOM_MARGIN

        types.forEachIndexed { index, type ->
            val left = margin + index * (buttonWidth + gap)
            modeButtons[type] = RectF(
                left,
                top,
                left + buttonWidth,
                bottom
            )
        }
    }

    private fun buildGameModeButtons(w: Float) {
        gameModeButtons.clear()

        val buttonWidth = 145f
        val buttonHeight = 54f
        val gap = 10f
        val top = 16f
        val totalWidth = buttonWidth * 2f + gap
        val startX = (w - totalWidth) * 0.5f

        gameModeButtons[GameMode.ENEMY] =
            RectF(startX, top, startX + buttonWidth, top + buttonHeight)

        gameModeButtons[GameMode.TARGET] =
            RectF(
                startX + buttonWidth + gap,
                top,
                startX + buttonWidth * 2f + gap,
                top + buttonHeight
            )
    }

    override fun onDraw(output: Canvas) {
        super.onDraw(output)

        val now = System.nanoTime()
        var dt = (now - lastFrameTime) / 1_000_000_000f
        dt = dt.coerceAtMost(0.033f)
        lastFrameTime = now

        if (titleScreen) {
            titleTime += dt
            if (puzzleSelection) flightUi.levelSelection(output, width, height, puzzleUnlocked, puzzleCompleted)
            else flightUi.title(output, width, height, bestFlight, titleTime, atmosphere)
            postInvalidateOnAnimation()
            return
        }

        val canvas = retroViewport.begin(width, height)

        if (flightScreen == FlightScreen.PLAYING && !gameOver) {
            updateChargeMeter(dt)
            if (gameMode == GameMode.ENEMY) updateChargeState()
            updateAutoFire(dt)
            updateGame(dt)
        }
        damageFlashSeconds = (damageFlashSeconds - dt).coerceAtLeast(0f)
        damageShakeSeconds = (damageShakeSeconds - dt).coerceAtLeast(0f)
        bossMessageSeconds = (bossMessageSeconds - dt).coerceAtLeast(0f)

        canvas.drawRect(
            0f, 0f,
            width.toFloat(), height.toFloat(),
            backgroundPaint
        )
        atmosphere.draw(canvas, width, height, if(gameMode == GameMode.TARGET) puzzleCameraY else runTimeSeconds*12f)
        starfield.draw(canvas, calm = gameMode == GameMode.TARGET)
        drawIndustrialBackdrop(canvas)

        canvas.save()
        if (gameMode == GameMode.TARGET) canvas.translate(0f, -puzzleCameraY)
        val damageShake =
            if (damageShakeSeconds > 0f)
                10f * (damageShakeSeconds / DAMAGE_SHAKE_DURATION).coerceIn(0f, 1f)
            else 0f
        val bombShake =
            if (bombBlastSeconds > 0f)
                BOMB_SHAKE_STRENGTH *
                    (bombBlastSeconds / BOMB_BLAST_DURATION).coerceIn(0f, 1f)
            else 0f
        val shakeStrength = damageShake + bombShake
        if (shakeStrength > 0f) {
            canvas.translate(
                randomRange(-shakeStrength, shakeStrength),
                randomRange(-shakeStrength, shakeStrength)
            )
        }

        for (wall in arenaWalls) {
            wall.draw(canvas)
        }

        if (activeShotType == ShotType.BEAM) {
            drawChargingBeam(canvas)
        } else {
            drawTrajectoryGuide(canvas)
        }

        val autoChargeVisualScale =
            if (autoChargeActive) chargeEnergy.coerceIn(0f, 1f) else 1f
        slingshot.draw(
            canvas,
            heavyShotCharged,
            currentThrust,
            autoChargeVisualScale
        )

        for (steel in destructibleSteel) {
            steel.draw(canvas)
        }

        for (powerUp in powerUps) {
            powerUp.draw(canvas)
        }

        for (enemy in enemies) {
            enemy.draw(canvas)
        }

        // Screen-space emissive pass. Drawn over hulls/terrain so bright weapons
        // actually cast coloured light into the scene rather than looking pasted on.
        drawDynamicLighting(canvas)

        for (projectile in projectiles) {
            projectile.draw(canvas)
        }

        drawBossShots(canvas)
        drawBeamPulse(canvas)
        drawBombBlast(canvas)
        canvas.restore()
        retroViewport.end(output, width, height)

        if (bossIntroActive) drawBossIntro(output)
        flightUi.hud(output, width, height, gameMode == GameMode.TARGET,
            playerHealth, playerMaxHealth, fuel, runTimeSeconds.toInt(),
            enemies.count { it.active }, puzzleLevel,
            if (activeShotType == ShotType.BEAM) "BEAM" else collectedPowerUps.joinToString(" + ") { it.label }.ifEmpty { "STANDARD / COLLECT UPGRADES" },
            bombInventory, puzzleLayout?.level, projectiles.any { it.active })
        drawDamageFeedback(output)
        val liveBoss = enemies.firstOrNull { it.active && it.boss }
        flightUi.encounter(output,
            if (bossMessageSeconds > 0f) bossMessage else "",
            if (bossBattle && !bossIntroActive && liveBoss != null) liveBoss.hitPoints / liveBoss.maxHitPoints else null)
        if (flightScreen != FlightScreen.PLAYING) {
            val kind = when (flightScreen) {
                FlightScreen.BRIEFING -> "briefing"
                FlightScreen.PAUSED -> "pause"
                FlightScreen.COMPLETE -> if (gameMode == GameMode.TARGET && puzzleLevel == PuzzleCampaign.LEVEL_COUNT) "campaign" else "complete"
                else -> "result"
            }
            flightUi.overlay(output, width, height, kind, gameMode == GameMode.TARGET,
                runTimeSeconds.toInt(), volleysLaunched, puzzleLevel, bossCheckpointAvailable, puzzleLayout?.level)
        }

        if (flightScreen == FlightScreen.PLAYING) postInvalidateOnAnimation()
    }

    private fun updateChargeMeter(dt: Float) {
        if (autoChargeActive) {
            chargeEnergy =
                (chargeEnergy - dt / AUTO_CHARGE_DRAIN_SECONDS).coerceAtLeast(0f)
            if (chargeEnergy <= 0f) {
                autoChargeActive = false
                heavyShotCharged = false
                chargeRechargeDelay = AUTO_CHARGE_RECHARGE_SECONDS
            }
            return
        }

        if (chargeRechargeDelay > 0f) {
            chargeRechargeDelay = (chargeRechargeDelay - dt).coerceAtLeast(0f)
            return
        }

        if (chargeEnergy < 1f) {
            chargeEnergy =
                (chargeEnergy + dt / NORMAL_CHARGE_RECHARGE_SECONDS).coerceAtMost(1f)
        }
    }

    private fun updateChargeState() {
        if (
            inputMode != InputMode.AIM_SHOT ||
            !slingshot.isPulled ||
            heavyShotCharged ||
            chargeEnergy < 0.999f
        ) {
            return
        }

        val fullyExtended =
            slingshot.rawPullDistance >= Slingshot.MAX_PULL_DISTANCE

        if (!fullyExtended) {
            // The two-second count requires uninterrupted full extension.
            fullExtensionStartTimeMs = 0L
            return
        }

        if (fullExtensionStartTimeMs == 0L) {
            fullExtensionStartTimeMs = System.currentTimeMillis()
            return
        }

        if (
            System.currentTimeMillis() - fullExtensionStartTimeMs >=
            HEAVY_CHARGE_TIME_MS
        ) {
            heavyShotCharged = true

            if (!heavyChargeHapticSent) {
                haptics.heavyCharged()
                sounds.playChargeReady()
                heavyChargeHapticSent = true
            }
        }
    }

    private fun updateAutoFire(dt: Float) {
        if (
            ShotType.AUTO !in collectedPowerUps ||
            inputMode != InputMode.AIM_SHOT ||
            !slingshot.isPulled
        ) {
            autoHasFired = false
            return
        }

        val tension = slingshot.pullRatio.coerceIn(0f, 1f)

        if (heavyShotCharged && chargeEnergy >= 0.999f && !autoChargeActive) {
            autoChargeActive = true
        }

        if (tension < AUTO_MIN_TENSION) {
            autoHasFired = false
            return
        }

        val normalized =
            ((tension - AUTO_MIN_TENSION) /
                (1f - AUTO_MIN_TENSION))
                .coerceIn(0f, 1f)

        // Slow at low tension, rapidly approaching maximum cadence at 90–100%.
        val interval =
            (AUTO_SLOW_INTERVAL_MS -
                normalized * (AUTO_SLOW_INTERVAL_MS - AUTO_FAST_INTERVAL_MS))
                .toLong()

        val now = System.currentTimeMillis()

        if (!autoHasFired || now - lastAutoFireMs >= interval) {
            firePoweredVolley(
                pullX = slingshot.pullX,
                pullY = slingshot.pullY,
                pullRatio = tension,
                heavy = heavyShotCharged && autoChargeActive && chargeEnergy > 0f
            )

            lastAutoFireMs = now
            autoHasFired = true
            haptics.autoShot()
            sounds.playShot(
                ShotType.AUTO,
                heavyShotCharged && autoChargeActive && chargeEnergy > 0f
            )
        }
    }

    private fun updateGame(dt: Float) {
        if (gameOver) return

        slingshot.update(dt)
        if (gameMode == GameMode.ENEMY) {
            updatePendingLongPresses()
            updateThruster(dt)
        } else {
            currentThrust = 0f
            verticalVelocity = 0f
        }
        starfield.update(dt * if(gameMode == GameMode.TARGET) 0.035f else 0.3f)
        sounds.updateMusic(dt)
        bombBlastSeconds = (bombBlastSeconds - dt).coerceAtLeast(0f)
        runTimeSeconds += dt
        if (gameMode == GameMode.ENEMY) {
            if (!bossBattle) updateScrollingField(dt)
            if (bossBattle) updateBossPowerUps(dt) else updatePowerUps(dt)
            resolvePlayerPowerUpCollisions()
            resolvePlayerFieldCollisions()
            resolvePlayerEnemyCollisions()
        }

        if (!bossIntroActive) updateEnemies(dt)
        updateBossBattleState(dt)
        updateBossCombat(dt)
        updateBossShots(dt)
        if (gameMode == GameMode.ENEMY && !bossBattle) {
            maybeSpawnEnemy(dt)
            if (!bossMusicFadeStarted && runTimeSeconds >= nextBossTimeSeconds() - 1f) {
                saveBossCheckpoint()
                bossMusicFadeStarted = true
                sounds.fadeOutLevelMusic(1f)
            }
            maybeStartBossBattle()
        }
        if (
            activeShotType == ShotType.BEAM &&
            inputMode == InputMode.AIM_SHOT &&
            slingshot.isPulled &&
            heavyShotCharged &&
            chargeEnergy >= 0.999f &&
            !autoChargeActive
        ) {
            // Charged Beam uses the same 1.5 s drain / 3.5 s recovery state as charged Auto.
            autoChargeActive = true
        }

        updateBeamDamage(dt)

        beamPulse?.let {
            it.lifeSeconds -= dt
            if (it.lifeSeconds <= 0f) beamPulse = null
        }

        val maxSpeed = projectiles
            .filter { it.active }
            .maxOfOrNull { sqrt(it.vx * it.vx + it.vy * it.vy) }
            ?: 0f

        val desiredSteps =
            if (maxSpeed <= 0f) 1
            else ceil((maxSpeed * dt) / MAX_TRAVEL_PER_SUBSTEP).toInt()

        val steps = desiredSteps.coerceIn(1, MAX_PHYSICS_SUBSTEPS)
        val subDt = dt / steps

        repeat(steps) {
            for (projectile in projectiles) {
                projectile.integrate(subDt)
            }

            for (projectile in projectiles) {
                projectile.resolveScreenWalls(
                    width.toFloat(),
                    if (gameMode == GameMode.TARGET) puzzleWorldHeight else height.toFloat(),
                    onBounce = {
                        haptics.bounce()
                        sounds.playWallImpact()
                    },
                    onBreak = {
                        haptics.projectileBreak()
                        sounds.playProjectileBreak()
                    }
                )
            }

            for (projectile in projectiles) {
                if (!projectile.active || projectile.piercing) continue

                for (wall in arenaWalls) {
                    if (wall.resolve(projectile)) {
                        projectile.registerArenaWallBounce(
                            onBounce = {
                                haptics.bounce()
                                sounds.playWallImpact()
                            },
                            onBreak = {
                                haptics.projectileBreak()
                                sounds.playProjectileBreak()
                            }
                        )
                        if (!projectile.active) break
                    }
                }
            }

            resolveProjectileCollisions()
            resolveProjectileDestructibleCollisions()
            resolveProjectileEnemyCollisions()
        }

        if (gameMode == GameMode.TARGET) updatePuzzleCameraAndCompletion(dt)
        projectiles.removeAll { !it.active }
        enemies.removeAll { !it.active }

        val liveIds = projectiles.mapTo(mutableSetOf()) { it.id }
        activeContacts.removeAll {
            it.lowId !in liveIds || it.highId !in liveIds
        }
    }

    private fun updatePendingLongPresses() {
        for (track in touchTracks.values) {
            if (track.role == TouchRole.PENDING) {
                maybeActivateLongPressThrust(track, track.lastX, track.lastY)
            }
        }
        syncInputModeFromTracks()
    }

    private fun updateThruster(dt: Float) {
        val thrustHeld = touchTracks.values.any { it.role == TouchRole.THRUST }
        val requested =
            if (thrustHeld && (bossBattle || fuel > 0f)) 1f
            else 0f
        currentThrust += (requested - currentThrust) * (1f - kotlin.math.exp(-8f * dt))

        if (!bossBattle && currentThrust > 0.02f && fuel > 0f) {
            fuel = (fuel - currentThrust * dt / FULL_THRUST_FUEL_SECONDS).coerceAtLeast(0f)
        }

        val thrustMultiplier = if (bossBattle) 2f else 1f
        val acceleration =
            THRUSTER_GRAVITY - THRUSTER_ACCELERATION * thrustMultiplier * currentThrust
        verticalVelocity += acceleration * dt
        verticalVelocity *= (1f - THRUSTER_DRAG * dt).coerceIn(0f, 1f)
        verticalVelocity = verticalVelocity.coerceIn(-THRUSTER_MAX_UP_SPEED, THRUSTER_MAX_DOWN_SPEED)

        val oldY = slingshot.y
        slingshot.moveVerticalBy(verticalVelocity * dt, height.toFloat())
        if (slingshot.y == oldY && kotlin.math.abs(verticalVelocity) > 1f) {
            verticalVelocity *= 0.25f
        }
    }

    private fun drawFuelGauge(canvas: Canvas) {
        val gaugeW = 180f
        val gaugeH = 22f
        val right = width.toFloat() - 20f
        val top = 82f
        val left = right - gaugeW

        buttonTextPaint.textAlign = Paint.Align.RIGHT
        buttonTextPaint.textSize = 20f
        buttonTextPaint.color = if (bossBattle) 0xFF69F0AE.toInt() else 0xFF7CE8FF.toInt()
        canvas.drawText(if (bossBattle) "FUEL SUPERCHARGED!!" else "FUEL", right, top - 8f, buttonTextPaint)

        buttonPaint.style = Paint.Style.FILL
        buttonPaint.color = 0xFF101820.toInt()
        canvas.drawRoundRect(left, top, right, top + gaugeH, 7f, 7f, buttonPaint)

        val inner = 3f
        val displayedFuel = if (bossBattle) 1f else fuel
        val fillRight = left + inner + (gaugeW - inner * 2f) * displayedFuel
        buttonPaint.color =
            if (fuel < 0.22f) 0xFFFF7043.toInt() else 0xFF29B6F6.toInt()
        if (fillRight > left + inner) {
            canvas.drawRoundRect(
                left + inner, top + inner,
                fillRight, top + gaugeH - inner,
                5f, 5f, buttonPaint
            )
        }

        buttonPaint.style = Paint.Style.STROKE
        buttonPaint.strokeWidth = 3f
        buttonPaint.color = 0xFF7CE8FF.toInt()
        canvas.drawRoundRect(left, top, right, top + gaugeH, 7f, 7f, buttonPaint)

        buttonTextPaint.textAlign = Paint.Align.RIGHT
        buttonTextPaint.textSize = 20f
        buttonTextPaint.color = 0xFFFFC857.toInt()
        canvas.drawText("TOKENS  $bossTokens", right, top + gaugeH + 27f, buttonTextPaint)

        buttonTextPaint.textAlign = Paint.Align.CENTER
        buttonTextPaint.textSize = 22f
        buttonTextPaint.color = Color.WHITE
    }

    private fun difficulty01(): Float =
        (runTimeSeconds / DIFFICULTY_RAMP_SECONDS).coerceIn(0f, 1f)

    private fun updateScrollingField(dt: Float) {
        val dy = FIELD_SCROLL_SPEED * dt
        for (wall in arenaWalls) wall.scrollBy(dy)
        for (steel in destructibleSteel) steel.scrollBy(dy)

        arenaWalls.removeAll { it.rect.top > height + 80f }
        destructibleSteel.removeAll { !it.active || it.rect.top > height + 80f }

        val difficulty = difficulty01()
        solidWallSpawnTimer -= dt
        destructibleSpawnTimer -= dt

        if (solidWallSpawnTimer <= 0f) {
            spawnSideWall()
            solidWallSpawnTimer = randomRange(
                lerp(7.2f, 3.4f, difficulty),
                lerp(10.0f, 5.0f, difficulty)
            )
        }

        if (destructibleSpawnTimer <= 0f) {
            spawnDestructibleWall()
            destructibleSpawnTimer = randomRange(
                lerp(2.8f, 1.15f, difficulty),
                lerp(4.0f, 1.9f, difficulty)
            )
        }
    }

    private fun spawnSideWall() {
        val w = width.toFloat()
        if (w <= 0f) return

        val onLeft = Random.nextBoolean()
        val wallWidth = randomRange(w * 0.12f, w * 0.22f)
        val wallHeight = randomRange(70f, 145f)
        val inset = randomRange(0f, w * 0.035f)
        val left =
            if (onLeft) inset
            else w - inset - wallWidth

        // Permanent steel is restricted to the side lanes. It creates an
        // occasional dodge without sealing the central planning corridor.
        arenaWalls += ArenaWall(
            RectF(left, -wallHeight - 12f, left + wallWidth, -12f)
        )
    }

    private fun spawnDestructibleWall() {
        val w = width.toFloat()
        if (w <= 0f) return

        val horizontal = Random.nextFloat() < 0.72f
        val wallWidth =
            if (horizontal) randomRange(w * 0.16f, w * 0.34f)
            else randomRange(w * 0.08f, w * 0.14f)
        val wallHeight =
            if (horizontal) randomRange(30f, 55f)
            else randomRange(70f, 125f)

        // Keep generous margins and never allow a single generated member to
        // dominate the screen width.
        val left = randomRange(w * 0.08f, w * 0.92f - wallWidth)
        destructibleSteel += DestructibleSteel(
            RectF(left, -wallHeight - 8f, left + wallWidth, -8f)
        )
    }

    private fun updatePowerUps(dt: Float) {
        for (powerUp in powerUps) {
            powerUp.update(dt, FIELD_SCROLL_SPEED, width.toFloat())
        }
        powerUps.removeAll { !it.active || it.y - it.radius > height + 50f }

        powerUpSpawnTimer -= dt
        if (powerUpSpawnTimer <= 0f) {
            spawnRandomPowerUp()
            val scarcity = 1f + collectedPowerUps.size * 0.28f
            powerUpSpawnTimer =
                randomRange(POWERUP_MIN_INTERVAL, POWERUP_MAX_INTERVAL) * scarcity
        }
    }

    private fun spawnRandomPowerUp() {
        val weighted = mutableListOf<Pair<ShotType, Float>>()
        weighted += ShotType.BOMB to BOMB_WEIGHT
        weighted += ShotType.FUEL to
            if (fuel < 0.35f) FUEL_LOW_WEIGHT
            else if (fuel < 0.70f) FUEL_MID_WEIGHT
            else FUEL_HIGH_WEIGHT
        if (playerHealth < 3) weighted += ShotType.SHIELD to SHIELD_LOW_HULL_WEIGHT

        if (ShotType.BOUNCE !in collectedPowerUps) {
            weighted += ShotType.BOUNCE to BASE_POWERUP_WEIGHT
        }

        val hasSpread = ShotType.SPREAD in collectedPowerUps
        val hasAuto = ShotType.AUTO in collectedPowerUps
        if (!hasSpread) weighted += ShotType.SPREAD to if (hasAuto) RIVAL_POWERUP_WEIGHT else BASE_POWERUP_WEIGHT
        if (!hasAuto) weighted += ShotType.AUTO to if (hasSpread) RIVAL_POWERUP_WEIGHT else BASE_POWERUP_WEIGHT
        if (ShotType.PIERCING !in collectedPowerUps) weighted += ShotType.PIERCING to BASE_POWERUP_WEIGHT
        if (ShotType.DOUBLE !in collectedPowerUps) weighted += ShotType.DOUBLE to BASE_POWERUP_WEIGHT
        if (weighted.isEmpty()) return

        var roll = Random.nextFloat() * weighted.sumOf { it.second.toDouble() }.toFloat()
        var selected = weighted.last().first
        for ((type, weight) in weighted) {
            roll -= weight
            if (roll <= 0f) { selected = type; break }
        }

        powerUps += PowerUpActor(
            type = selected,
            x = randomRange(POWERUP_MARGIN, width.toFloat() - POWERUP_MARGIN),
            y = -POWERUP_RADIUS - 10f
        )
    }

    private fun resolvePlayerPowerUpCollisions() {
        val px = slingshot.x
        val py = slingshot.y - PLAYER_COLLISION_Y_OFFSET
        for (powerUp in powerUps) {
            if (!powerUp.active || powerUp.collectDelay > 0f) continue
            val dx = powerUp.x - px
            val dy = powerUp.y - py
            val hit = powerUp.radius + PLAYER_COLLISION_RADIUS
            if (dx * dx + dy * dy <= hit * hit) {
                when (powerUp.type) {
                    ShotType.BOMB -> bombInventory++
                    ShotType.SHIELD ->
                        playerHealth = (playerHealth + 1).coerceAtMost(playerMaxHealth)
                    ShotType.FUEL ->
                        fuel = (fuel + FUEL_PICKUP_AMOUNT).coerceAtMost(1f)
                    ShotType.BEAM -> activeShotType = ShotType.BEAM
                    else -> collectedPowerUps += powerUp.type
                }
                powerUp.active = false
                haptics.modeSelected()
                sounds.playModeSelect()
            }
        }
    }

    private fun dropCollectedPowerUps() {
        if (collectedPowerUps.isEmpty()) return

        val types = collectedPowerUps.toList()
        collectedPowerUps.clear()

        val baseX = slingshot.x
        val baseY = slingshot.y - PLAYER_COLLISION_Y_OFFSET - 90f

        types.forEachIndexed { index, type ->
            val centered = index - (types.size - 1) * 0.5f
            val direction =
                if (types.size == 1) {
                    if (Random.nextBoolean()) -1f else 1f
                } else {
                    centered
                }

            powerUps += PowerUpActor(
                type = type,
                x = (baseX + direction * 58f).coerceIn(
                    POWERUP_MARGIN,
                    width.toFloat() - POWERUP_MARGIN
                ),
                y = baseY - kotlin.math.abs(centered) * 22f,
                burstVx = direction * 310f,
                burstVy = -360f - kotlin.math.abs(centered) * 55f
            ).also {
                it.collectDelay = EJECT_RECOLLECT_DELAY
            }
        }

        // The mode display is cleared immediately; the coloured actors now
        // visibly burst away from the launcher and can be chased down again.
        sounds.playProjectileBreak()
        haptics.projectileBreak()
    }

    private fun resolvePlayerEnemyCollisions() {
        val current = mutableSetOf<Long>()
        val px = slingshot.x
        val py = slingshot.y - PLAYER_COLLISION_Y_OFFSET

        for (enemy in enemies) {
            if (!enemy.active) continue
            val dx = enemy.x - px
            val dy = enemy.y - py
            val hitDistance = enemy.radius + PLAYER_COLLISION_RADIUS
            if (dx * dx + dy * dy <= hitDistance * hitDistance) {
                current += enemy.id
                if (enemy.id !in enemyPlayerContacts) {
                    damagePlayer()
                    if (!enemy.boss) {
                        // Standard attackers are consumed by the collision.
                        enemy.takeDamage(enemy.maxHitPoints)
                    } else {
                        // Boss contact still knocks the boss away; it is not disposable.
                        val length = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                        enemy.vx += dx / length * PLAYER_ENEMY_KNOCKBACK
                        enemy.vy += dy / length * PLAYER_ENEMY_KNOCKBACK
                    }
                }
            }
        }

        enemyPlayerContacts.retainAll(current)
        enemyPlayerContacts.addAll(current)
    }

    private fun resolvePlayerFieldCollisions() {
        val px = slingshot.x
        val py = slingshot.y - PLAYER_COLLISION_Y_OFFSET

        val solidNow = mutableSetOf<Int>()
        for (wall in arenaWalls) {
            if (circleTouchesRect(px, py, PLAYER_COLLISION_RADIUS, wall.rect)) {
                val key = System.identityHashCode(wall)
                solidNow += key
                if (key !in solidWallPlayerContacts) damagePlayer()
            }
        }
        solidWallPlayerContacts.retainAll(solidNow)
        solidWallPlayerContacts.addAll(solidNow)

        val destructibleNow = mutableSetOf<Int>()
        for (steel in destructibleSteel) {
            if (!steel.active) continue
            if (circleTouchesRect(px, py, PLAYER_COLLISION_RADIUS, steel.rect)) {
                val key = System.identityHashCode(steel)
                destructibleNow += key
                if (key !in destructiblePlayerContacts) damagePlayer()
            }
        }
        destructiblePlayerContacts.retainAll(destructibleNow)
        destructiblePlayerContacts.addAll(destructibleNow)
    }

    private fun circleTouchesRect(
        cx: Float,
        cy: Float,
        radius: Float,
        rect: RectF
    ): Boolean {
        val nearestX = cx.coerceIn(rect.left, rect.right)
        val nearestY = cy.coerceIn(rect.top, rect.bottom)
        val dx = cx - nearestX
        val dy = cy - nearestY
        return dx * dx + dy * dy <= radius * radius
    }

    private fun damagePlayer() {
        if (playerHealth <= 0 || gameOver) return

        playerHealth--
        dropCollectedPowerUps()

        damageFlashSeconds = DAMAGE_FLASH_DURATION
        damageShakeSeconds = DAMAGE_SHAKE_DURATION
        haptics.playerDamaged()
        sounds.playWallImpact()
        sounds.playProjectileBreak()

        if (playerHealth <= 0) {
            if (bossBattle && activeShotType == ShotType.BEAM) {
                activeShotType = ShotType.NONE
                beamPulse = null
                sounds.stopBeamLoop()
            }
            triggerGameOver()
        }
    }

    private fun saveBossCheckpoint() {
        bossCheckpointAvailable = true
        checkpointPlayerMaxHealth = playerMaxHealth
        checkpointPlayerHealth = playerHealth
        checkpointFuel = fuel
        checkpointBombInventory = bombInventory
        checkpointChargeEnergy = chargeEnergy
        checkpointActiveShotType =
            if (activeShotType == ShotType.BEAM) ShotType.NONE else activeShotType
        checkpointCollectedPowerUps.clear()
        checkpointCollectedPowerUps.addAll(collectedPowerUps)
    }

    private fun restoreBossCheckpoint() {
        gameOver = false
        bossBattle = false
        bossSpawnedThisRun = false
        bossMusicFadeStarted = false
        bossIntroActive = false
        bossIntroSeconds = 0f
        bossShots.clear()
        bossMessage = "CHECKPOINT RESTORED"
        bossMessageSeconds = BOSS_MESSAGE_DURATION
        damageFlashSeconds = 0f
        damageShakeSeconds = 0f
        bombBlastSeconds = 0f

        playerMaxHealth = checkpointPlayerMaxHealth
        playerHealth = checkpointPlayerHealth.coerceAtMost(playerMaxHealth)
        fuel = checkpointFuel
        bombInventory = checkpointBombInventory
        chargeEnergy = checkpointChargeEnergy
        chargeRechargeDelay = 0f
        autoChargeActive = false
        activeShotType = checkpointActiveShotType
        collectedPowerUps.clear()
        collectedPowerUps.addAll(checkpointCollectedPowerUps)

        powerUps.clear()
        projectiles.clear()
        beamPulse = null
        enemies.clear()
        arenaWalls.clear()
        destructibleSteel.clear()
        enemyPlayerContacts.clear()
        solidWallPlayerContacts.clear()
        destructiblePlayerContacts.clear()
        verticalVelocity = 0f
        currentThrust = 0f
        slingshot.cancelPull()
        resetCharge()

        // Return to the instant immediately before the 1-second boss warning lead-in.
        runTimeSeconds = nextBossTimeSeconds() - 1f
        powerUpSpawnTimer = 2.5f
        solidWallSpawnTimer = 0.8f
        destructibleSpawnTimer = 0.6f
        sounds.startLevelMusic()
    }

    private fun triggerGameOver() {
        gameOver = true
        flightScreen = FlightScreen.RESULT
        clearFlightInput()
        bestFlight = max(bestFlight, runTimeSeconds.toInt())
        records.edit().putInt("best_seconds", bestFlight).apply()
        sounds.pauseAudio()
        gameOverAtMs = System.currentTimeMillis()
        projectiles.clear()
        beamPulse = null
        sounds.stopBeamLoop()
        haptics.projectileBreak()
        sounds.playProjectileBreak()
    }

    private fun resetRun() {
        if (bossCheckpointAvailable) {
            restoreBossCheckpoint()
            return
        }

        gameOver = false
        bossBattle = false
        bossSpawnedThisRun = false
        bossMusicFadeStarted = false
        bossCheckpointAvailable = false
        bossIntroActive = false
        bossIntroSeconds = 0f
        bossShots.clear()
        bossMessage = ""
        bossMessageSeconds = 0f
        damageFlashSeconds = 0f
        damageShakeSeconds = 0f
        bombBlastSeconds = 0f
        playerMaxHealth = PLAYER_STARTING_MAX_HEALTH
        playerHealth = playerMaxHealth
        bombInventory = 0
        bossTokens = 0
        bossesDefeated = 0
        fuel = 1f
        verticalVelocity = 0f
        currentThrust = 0f
        chargeEnergy = 1f
        chargeRechargeDelay = 0f
        autoChargeActive = false
        collectedPowerUps.clear()
        powerUps.clear()
        projectiles.clear()
        beamPulse = null
        enemyPlayerContacts.clear()
        solidWallPlayerContacts.clear()
        destructiblePlayerContacts.clear()
        runTimeSeconds = 0f
        sounds.startLevelMusic(0.8f)
        buildArena(width.toFloat(), height.toFloat())
        buildFieldObjects(width.toFloat(), height.toFloat())
        initialiseEnemies(width.toFloat(), height.toFloat())
        slingshot.cancelPull()
        resetCharge()
    }

    private fun initialiseEnemies(w: Float, h: Float) {
        if (gameMode == GameMode.TARGET) {
            populateTargetMode(w, h)
        } else {
            populateEnemyMode(w, h)
        }
    }

    private fun populateEnemyMode(w: Float, h: Float) {
        enemies.clear()
        beamContactTime.clear()
        powerUps.clear()
        collectedPowerUps.clear()
        bombInventory = 0
        runTimeSeconds = 0f
        powerUpSpawnTimer = randomRange(8f, 14f)

        val openingCount = Random.nextInt(3, 6)
        repeat(openingCount) {
            enemies += createEnemy(
                x = randomRange(ENEMY_RADIUS, w - ENEMY_RADIUS),
                y = randomRange(ENEMY_RADIUS + 60f, h * 0.40f)
            )
        }

        lastEnemySpawnMs = System.currentTimeMillis()
        encounterTimer = 1.2f
        encounterIndex = 0
    }

    private fun populateTargetMode(w: Float, h: Float) {
        enemies.clear()
        arenaWalls.clear()
        destructibleSteel.clear()
        powerUps.clear()
        collectedPowerUps.clear()
        collectedPowerUps += ShotType.BOUNCE
        activeShotType = ShotType.NONE
        bossBattle = false
        bossIntroActive = false
        bossSpawnedThisRun = false
        bossMusicFadeStarted = false
        val layout = PuzzleCampaign.layout(puzzleLevel, w, h)
        puzzleLayout = layout
        guideCacheKey = ""
        puzzleWorldHeight = layout.height
        puzzleCameraY = (layout.height-h).coerceAtLeast(0f)
        puzzleResetDelay = 0f
        projectiles.clear()
        slingshot.x = layout.launcherX
        slingshot.y = layout.launcherY
        arenaWalls.addAll(layout.walls)
        layout.targets.forEach { target ->
            enemies += Enemy(nextEnemyId++, target.x, target.y, layout.targetRadius,
                maxHitPoints = 1f, spriteVariant = 0, sprite = null)
        }
    }

    private fun addBoss(x: Float, y: Float) {
        enemies += Enemy(
            id = nextEnemyId++,
            x = x,
            y = y,
            radius = BOSS_RADIUS,
            maxHitPoints = Enemy.BOSS_HIT_POINTS,
            boss = true
        )
    }

    private fun nextBossTimeSeconds(): Float =
        BOSS_BATTLE_START_SECONDS + bossesDefeated * BOSS_REPEAT_INTERVAL_SECONDS

    private fun maybeStartBossBattle() {
        if (runTimeSeconds < nextBossTimeSeconds()) return
        startBossBattle()
    }

    private fun startBossBattle() {
        bossBattle = true
        bossSpawnedThisRun = true
        bossIntroActive = true
        bossIntroSeconds = BOSS_INTRO_SECONDS
        sounds.startBossMusic(2f)

        enemies.clear()
        arenaWalls.clear()
        destructibleSteel.clear()
        projectiles.clear()
        powerUps.clear()
        enemyPlayerContacts.clear()
        solidWallPlayerContacts.clear()
        destructiblePlayerContacts.clear()

        sounds.stopBeamLoop()
        sounds.playChargeReady()
        haptics.heavyCharged()
    }

    private fun spawnBattleBoss() {
        bossVariant = Random.nextInt(BOSS_VARIANT_COUNT)
        bossFireTimer = randomRange(1.0f, 2.2f)
        bossChargeTimer = randomRange(4.2f, 7.0f)
        bossPowerUpTimer = 1.2f
        bossShots.clear()
        enemies += Enemy(
            id = nextEnemyId++,
            x = width * 0.5f,
            y = -BATTLE_BOSS_RADIUS,
            radius = BATTLE_BOSS_RADIUS,
            maxHitPoints = BATTLE_BOSS_HIT_POINTS,
            boss = true,
            bossVariant = bossVariant,
            sprite = null
        )
    }

    private fun updateBossBattleState(dt: Float) {
        if (!bossBattle) return

        if (bossIntroActive) {
            bossIntroSeconds = (bossIntroSeconds - dt).coerceAtLeast(0f)
            if (bossIntroSeconds <= 0f) {
                bossIntroActive = false
                spawnBattleBoss()
                sounds.playProjectileBreak()
                haptics.projectileBreak()
            }
            return
        }

        val boss = enemies.firstOrNull { it.active && it.boss }
        if (boss == null) {
            bossBattle = false
            bossSpawnedThisRun = false
            bossMusicFadeStarted = false
            bossShots.clear()
            // Any uncollected boss-battle weapon drops vanish with the boss.
            powerUps.removeAll { it.type in BOSS_WEAPON_POWERUPS }
            bossCheckpointAvailable = false
            sounds.startLevelMusic(1.2f)

            // Boss reward: permanently increase max HULL for the current run,
            // then restore every HULL unit.
            playerMaxHealth += 1
            playerHealth = playerMaxHealth
            bossTokens++
            bossesDefeated++
            if (activeShotType == ShotType.BEAM) {
                activeShotType = ShotType.NONE
                beamPulse = null
                sounds.stopBeamLoop()
                bossMessage = "BOSS DEFEATED  •  TOKEN +1  •  HULL +1  •  BEAM DISENGAGED"
            } else {
                bossMessage = "BOSS DEFEATED  •  TOKEN +1  •  HULL +1  •  FULL REPAIR"
            }
            bossMessageSeconds = BOSS_MESSAGE_DURATION
            solidWallSpawnTimer = 0.8f
            destructibleSpawnTimer = 0.6f
            powerUpSpawnTimer = 2.5f
            sounds.playProjectileBreak()
            haptics.projectileBreak()
        }
    }

    private fun drawBossIntro(canvas: Canvas) {
        canvas.drawColor(0xCC07101F.toInt())
        val scale=minOf(width/400f,height/800f)
        canvas.save();canvas.translate((width-400f*scale)/2f,(height-800f*scale)/2f);canvas.scale(scale,scale)
        PixelArt.rect(canvas,28f,260f,372f,264f,0xFFFF9A54.toInt())
        PixelFont.draw(canvas,"WARNING",28f,250f,18f,0xFFFFD980.toInt(),344f)
        PixelFont.draw(canvas,"BOSS INBOUND",28f,312f,38f,0xFFFF9A54.toInt(),344f)
        PixelFont.draw(canvas,"THRUSTERS SUPERCHARGED",28f,346f,12f,0xFF55EAD4.toInt(),344f)
        PixelArt.rect(canvas,28f,365f,372f,369f,0xFFFF9A54.toInt())
        canvas.restore()
    }

    private fun drawBossHealthBar(canvas: Canvas) {
        val boss = enemies.firstOrNull { it.active && it.boss } ?: return
        val margin = 34f
        val top = 125f
        val barH = 24f
        val left = margin
        val right = width.toFloat() - margin
        val ratio = (boss.hitPoints / boss.maxHitPoints).coerceIn(0f, 1f)

        buttonTextPaint.textAlign = Paint.Align.CENTER
        buttonTextPaint.textSize = 26f
        buttonTextPaint.color = 0xFFFFB300.toInt()
        canvas.drawText("BOSS", width * 0.5f, top - 12f, buttonTextPaint)

        buttonPaint.style = Paint.Style.FILL
        buttonPaint.color = 0xDD111111.toInt()
        canvas.drawRoundRect(left, top, right, top + barH, 8f, 8f, buttonPaint)
        buttonPaint.color = 0xFFFF6D00.toInt()
        canvas.drawRoundRect(
            left + 3f, top + 3f,
            left + 3f + (right - left - 6f) * ratio,
            top + barH - 3f, 6f, 6f, buttonPaint
        )
    }

    private fun enemyLoadoutPressure(): Float {
        var pressure = 1f
        if (ShotType.SPREAD in collectedPowerUps) pressure += 0.75f
        if (ShotType.DOUBLE in collectedPowerUps) pressure += 1.0f
        if (ShotType.PIERCING in collectedPowerUps) pressure += 0.55f
        if (ShotType.AUTO in collectedPowerUps) pressure += 0.95f
        return pressure
    }

    private fun maybeSpawnEnemy(dt: Float) {
        val difficulty = difficulty01()
        val pressure = enemyLoadoutPressure()
        val maxEnemies =
            (lerp(8f, MAX_ENEMY_COUNT.toFloat(), difficulty) * pressure)
                .toInt().coerceAtMost(LOADOUT_MAX_ENEMIES)
        if (enemies.count { it.active && !it.boss } >= maxEnemies) return

        encounterTimer -= dt
        if (encounterTimer > 0f) return

        spawnEncounter(encounterIndex % 6, difficulty)
        encounterIndex++

        // Every fourth encounter gets a deliberate breathing window.
        encounterTimer =
            if (encounterIndex % 4 == 0) randomRange(2.6f, 3.5f)
            else randomRange(1.25f, 2.15f) / pressure.coerceIn(1f, 2.2f)
    }

    private fun spawnEncounter(pattern: Int, difficulty: Float) {
        val w = width.toFloat()
        val top = -ENEMY_RADIUS - 12f
        when (pattern) {
            0 -> { // Scout line: clean horizontal formation
                val count = 3 + (difficulty * 2f).toInt()
                repeat(count) { i ->
                    val x = w * (i + 1f) / (count + 1f)
                    enemies += createEnemyOfVariant(x, top - i * 12f, 0)
                }
            }
            1 -> { // Raider pair enters from opposing lanes
                enemies += createEnemyOfVariant(w * 0.20f, top, 1)
                enemies += createEnemyOfVariant(w * 0.80f, top - 70f, 1)
            }
            2 -> { // Mine gate with a readable safe lane
                val gap = Random.nextInt(1, 4)
                for (lane in 0..4) {
                    if (lane == gap) continue
                    enemies += createEnemyOfVariant(w * (lane + 0.5f) / 5f, top - lane * 20f, 2)
                }
            }
            3 -> { // Guard convoy with scout escorts
                val center = randomRange(w * 0.32f, w * 0.68f)
                enemies += createEnemyOfVariant(center, top, 3)
                enemies += createEnemyOfVariant((center - 95f).coerceAtLeast(ENEMY_RADIUS), top - 55f, 0)
                enemies += createEnemyOfVariant((center + 95f).coerceAtMost(w - ENEMY_RADIUS), top - 55f, 0)
            }
            4 -> { // Interceptor pressure: only these actively home
                val count = if (difficulty > 0.55f) 3 else 2
                repeat(count) { i ->
                    enemies += createEnemyOfVariant(w * (i + 1f) / (count + 1f), top - i * 65f, 4)
                }
            }
            else -> { // Chevron: mixed readable formation
                val cx = w * 0.5f
                val spacing = 78f
                enemies += createEnemyOfVariant(cx, top, 3)
                for (i in 1..2) {
                    enemies += createEnemyOfVariant((cx - spacing * i).coerceAtLeast(ENEMY_RADIUS), top - 55f * i, 0)
                    enemies += createEnemyOfVariant((cx + spacing * i).coerceAtMost(w - ENEMY_RADIUS), top - 55f * i, 0)
                }
            }
        }
    }

    private fun spawnEnemyWave(difficulty: Float, bossChance: Float) {
        val base = 3 + (difficulty * 3f).toInt()
        val count = (base * if (ShotType.DOUBLE in collectedPowerUps) 2 else 1).coerceAtMost(12)
        val usable = width.toFloat() - ENEMY_RADIUS * 2f
        repeat(count) { index ->
            val fraction = (index + 1f) / (count + 1f)
            spawnOneEnemy(
                ENEMY_RADIUS + usable * fraction + randomRange(-18f, 18f),
                randomRange(ENEMY_RADIUS + 20f, height * 0.20f),
                bossChance * 0.55f
            )
        }
    }

    private fun spawnEnemyQueue(difficulty: Float, bossChance: Float) {
        val columns = if (ShotType.DOUBLE in collectedPowerUps) 2 else 1
        val queueLength = (3 + (difficulty * 3f).toInt()).coerceAtMost(6)
        repeat(columns) {
            val x = randomRange(width * 0.18f, width * 0.82f)
            repeat(queueLength) { index ->
                spawnOneEnemy(
                    x + randomRange(-8f, 8f),
                    -ENEMY_RADIUS - index * (ENEMY_RADIUS * 2.35f),
                    bossChance * 0.45f
                )
            }
        }
    }

    private fun spawnOneEnemy(x: Float, y: Float, bossChance: Float) {
        if (Random.nextFloat() < bossChance) addBoss(x, y)
        else enemies += createEnemy(x, y)
    }

    private fun createEnemy(x: Float, y: Float): Enemy =
        createEnemyOfVariant(x, y, Random.nextInt(PixelArt.ENEMY_VARIANTS))

    private fun createEnemyOfVariant(x: Float, y: Float, variant: Int): Enemy =
        Enemy(
            id = nextEnemyId++,
            x = x,
            y = y,
            radius = ENEMY_RADIUS,
            spriteVariant = variant,
            sprite = null
        )

    private fun updateBossPowerUps(dt: Float) {
        for (powerUp in powerUps) {
            powerUp.update(dt, BOSS_POWERUP_DRIFT_SPEED, width.toFloat())
        }
        powerUps.removeAll { !it.active || it.y - it.radius > height + 50f }

        if (bossIntroActive) return
        bossPowerUpTimer -= dt
        if (bossPowerUpTimer > 0f) return

        val choices = listOf(
            ShotType.SHIELD,
            ShotType.BOUNCE,
            ShotType.SPREAD,
            ShotType.AUTO,
            ShotType.PIERCING,
            ShotType.DOUBLE,
            ShotType.BEAM
        )
        val type = choices.random()
        powerUps += PowerUpActor(
            type = type,
            x = randomRange(POWERUP_MARGIN, width.toFloat() - POWERUP_MARGIN),
            y = -POWERUP_RADIUS - 10f
        )
        bossPowerUpTimer = randomRange(BOSS_POWERUP_MIN_INTERVAL, BOSS_POWERUP_MAX_INTERVAL)
    }

    private fun updateBossCombat(dt: Float) {
        if (!bossBattle || bossIntroActive) return
        val boss = enemies.firstOrNull { it.active && it.boss } ?: return

        bossFireTimer -= dt
        if (bossFireTimer <= 0f) {
            val dx = slingshot.x - boss.x
            val dy = (slingshot.y - PLAYER_COLLISION_Y_OFFSET) - boss.y
            val length = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            val speed =
                when (boss.bossVariant) {
                    1 -> 400f
                    2 -> 520f
                    3 -> 330f
                    4 -> 610f
                    5 -> 470f
                    else -> 450f
                }
            val spread =
                when (boss.bossVariant) {
                    2 -> listOf(-0.16f, 0f, 0.16f)
                    3 -> listOf(-0.30f, -0.15f, 0f, 0.15f, 0.30f)
                    5 -> listOf(-0.22f, 0f, 0.22f)
                    else -> listOf(0f)
                }
            for (angle in spread) {
                val c = kotlin.math.cos(angle)
                val sn = kotlin.math.sin(angle)
                val nx = dx / length
                val ny = dy / length
                bossShots += BossShot(
                    x = boss.x,
                    y = boss.y,
                    vx = (nx * c - ny * sn) * speed,
                    vy = (nx * sn + ny * c) * speed
                )
            }
            bossFireTimer =
                when (boss.bossVariant) {
                    1 -> randomRange(2.0f, 3.0f)
                    2 -> randomRange(1.6f, 2.5f)
                    3 -> randomRange(2.4f, 3.3f)
                    4 -> randomRange(0.75f, 1.25f)
                    5 -> randomRange(1.25f, 1.9f)
                    else -> randomRange(1.1f, 2.0f)
                }
        }

        // Charger and Forge variants periodically lunge at the player.
        if (boss.bossVariant == 0 || boss.bossVariant == 1 || boss.bossVariant == 4) {
            bossChargeTimer -= dt
            if (bossChargeTimer <= 0f) {
                val dx = slingshot.x - boss.x
                val dy = (slingshot.y - PLAYER_COLLISION_Y_OFFSET) - boss.y
                val length = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                val impulse = when (boss.bossVariant) {
                    1 -> 1150f
                    4 -> 1380f
                    else -> 820f
                }
                boss.vx += dx / length * impulse
                boss.vy += dy / length * impulse
                bossChargeTimer = when (boss.bossVariant) {
                    1 -> randomRange(3.8f, 5.5f)
                    4 -> randomRange(2.8f, 4.2f)
                    else -> randomRange(5.5f, 8.0f)
                }
            }
        }
    }

    private fun updateBossShots(dt: Float) {
        val px = slingshot.x
        val py = slingshot.y - PLAYER_COLLISION_Y_OFFSET

        for (shot in bossShots) {
            if (!shot.active) continue
            shot.x += shot.vx * dt
            shot.y += shot.vy * dt

            val dx = shot.x - px
            val dy = shot.y - py
            val hit = shot.radius + PLAYER_COLLISION_RADIUS
            if (dx * dx + dy * dy <= hit * hit) {
                shot.active = false
                damagePlayer()
                continue
            }

            if (
                shot.x < -80f || shot.x > width + 80f ||
                shot.y < -80f || shot.y > height + 80f
            ) {
                shot.active = false
            }
        }
        bossShots.removeAll { !it.active }
    }

    private fun drawBossShots(canvas: Canvas) {
        for(shot in bossShots) if(shot.active) PixelArt.draw(canvas,"shot:orange",shot.x,shot.y,shot.radius*2f)
    }

    private fun drawRadialLight(
        canvas: Canvas,
        x: Float,
        y: Float,
        radius: Float,
        rgb: Int,
        strength: Int
    ) {
        PixelArt.halo(canvas,x,y,radius,rgb or 0xFF000000.toInt(),strength)
    }

    private fun drawDynamicLighting(canvas: Canvas) {
        // Player weapon lights. Heavy rounds deliberately bloom far beyond their body.
        for (projectile in projectiles) {
            if (!projectile.active) continue
            val heavy = projectile.radius >= Slingshot.PROJECTILE_RADIUS * 1.4f
            val color = when {
                projectile.piercing -> 0x00C86BFF
                projectile.bouncy -> 0x0029B6F6
                ShotType.SPREAD in collectedPowerUps -> 0x003DFFB5
                ShotType.AUTO in collectedPowerUps -> 0x0040C4FF
                else -> 0x0064D8FF
            }
            val glowRadius = projectile.radius * if (heavy) 8.5f else 6.2f
            drawRadialLight(canvas, projectile.x, projectile.y, glowRadius, color, if (heavy) 112 else 86)
        }

        // Power-ups read as luminous objects rather than flat HUD tokens.
        for (powerUp in powerUps) {
            if (!powerUp.active) continue
            val color = when (powerUp.type) {
                ShotType.BOUNCE -> 0x0029B6F6
                ShotType.SPREAD -> 0x0042A5F5
                ShotType.AUTO -> 0x00FFC107
                ShotType.PIERCING -> 0x00AB47BC
                ShotType.DOUBLE -> 0x0026C6DA
                ShotType.BOMB -> 0x00EF5350
                ShotType.SHIELD -> 0x0066BB6A
                ShotType.FUEL -> 0x0029B6F6
                ShotType.BEAM -> 0x00D05CFF
                ShotType.NONE -> 0x0090A4AE
            }
            drawRadialLight(canvas, powerUp.x, powerUp.y, powerUp.radius * 3.2f, color, 44)
        }

        // Hostile plasma throws warm light over nearby ships and steel.
        for (shot in bossShots) {
            if (shot.active) drawRadialLight(canvas, shot.x, shot.y, shot.radius * 7.2f, 0x00FF3D00, 105)
        }

        // Thruster illumination follows the ship and gets much broader under full burn.
        if (currentThrust > 0.02f) {
            val intensity = (48 + currentThrust * 90f).toInt()
            drawRadialLight(
                canvas, slingshot.x, slingshot.y + 74f,
                95f + 125f * currentThrust, 0x0040E8FF, intensity
            )
        }

        // Bomb detonation floods the local scene before the expanding shock front arrives.
        if (bombBlastSeconds > 0f) {
            val life = (bombBlastSeconds / BOMB_BLAST_DURATION).coerceIn(0f, 1f)
            drawRadialLight(
                canvas, bombBlastX, bombBlastY,
                max(width, height).toFloat() * (0.35f + (1f - life) * 0.85f),
                0x00BDEFFF, (150f * life).toInt()
            )
        }

        // Bosses carry their own emissive reactor signature.
        for (enemy in enemies) {
            if (!enemy.active || !enemy.boss) continue
            val color = when (enemy.bossVariant) {
                0 -> 0x00FF4A22
                1 -> 0x00B84DFF
                2 -> 0x0028E7FF
                3 -> 0x00FF7A18
                4 -> 0x003A8DFF
                else -> 0x006BFF9A
            }
            val pulse = 0.88f + 0.12f * kotlin.math.sin(runTimeSeconds * 3.2f + enemy.bossVariant)
            drawRadialLight(canvas, enemy.x, enemy.y, enemy.radius * 2.9f * pulse, color, 42)
        }
    }

    private fun drawBossMessage(canvas: Canvas) {
        if (bossMessageSeconds <= 0f || bossMessage.isBlank()) return
        val alpha =
            (255f * (bossMessageSeconds / BOSS_MESSAGE_DURATION).coerceIn(0f, 1f))
                .toInt()
        buttonTextPaint.textAlign = Paint.Align.CENTER
        buttonTextPaint.typeface = Typeface.DEFAULT_BOLD
        buttonTextPaint.textSize = 30f
        buttonTextPaint.color = (alpha shl 24) or 0x00FFFFFF
        canvas.drawText(bossMessage, width * 0.5f, height * 0.22f, buttonTextPaint)
    }

    private fun updateEnemies(dt: Float) {
        if (gameMode == GameMode.TARGET) return

        for (enemy in enemies) {
            val targetX =
                if (!enemy.boss) slingshot.x
                else when (enemy.bossVariant) {
                    0 -> width * 0.5f + kotlin.math.sin(runTimeSeconds * 1.35f) * width * 0.30f
                    1 -> slingshot.x
                    2 -> width * 0.5f + kotlin.math.sin(runTimeSeconds * 0.70f) * width * 0.40f
                    3 -> width * 0.5f + kotlin.math.sin(runTimeSeconds * 0.42f) * width * 0.22f
                    4 -> slingshot.x + kotlin.math.sin(runTimeSeconds * 2.0f) * width * 0.12f
                    else -> width * 0.5f + kotlin.math.sin(runTimeSeconds * 1.05f) * width * 0.36f
                }
            val targetY =
                if (!enemy.boss) slingshot.y
                else when (enemy.bossVariant) {
                    0 -> height * 0.24f
                    1 -> height * 0.36f
                    2 -> height * 0.17f + kotlin.math.sin(runTimeSeconds) * height * 0.10f
                    3 -> height * 0.20f
                    4 -> height * 0.30f
                    else -> height * 0.16f + kotlin.math.sin(runTimeSeconds * 0.8f) * height * 0.08f
                }
            enemy.update(
                dt = dt,
                targetX = targetX,
                targetY = targetY,
                walls = arenaWalls,
                screenWidth = width.toFloat(),
                screenHeight = height.toFloat()
            )
        }

        resolveEnemySeparation()
    }

    private fun resolveEnemySeparation() {
        for (i in 0 until enemies.size) {
            val a = enemies[i]
            if (!a.active) continue

            for (j in i + 1 until enemies.size) {
                val b = enemies[j]
                if (!b.active) continue

                val dx = b.x - a.x
                val dy = b.y - a.y
                val minDistance = a.radius + b.radius
                val distanceSquared = dx * dx + dy * dy

                if (distanceSquared >= minDistance * minDistance) continue

                val distance = sqrt(distanceSquared)
                val nx = if (distance > 0.001f) dx / distance else 1f
                val ny = if (distance > 0.001f) dy / distance else 0f
                val correction =
                    (minDistance - if (distance > 0.001f) distance else 0f) * 0.5f

                a.x -= nx * correction
                a.y -= ny * correction
                b.x += nx * correction
                b.y += ny * correction
            }
        }
    }

    private fun resolveProjectileDestructibleCollisions() {
        for (projectile in projectiles) {
            if (!projectile.active) continue

            for (steel in destructibleSteel) {
                if (!steel.active) continue

                if (projectile.piercing) {
                    // Piercing rounds cut through steel rather than bouncing.
                    val nearestX = projectile.x.coerceIn(steel.rect.left, steel.rect.right)
                    val nearestY = projectile.y.coerceIn(steel.rect.top, steel.rect.bottom)
                    val dx = projectile.x - nearestX
                    val dy = projectile.y - nearestY
                    if (dx * dx + dy * dy < projectile.radius * projectile.radius) {
                        steel.takeBeamDamage(projectile.environmentDamage() * 1.7f)
                    }
                } else if (steel.resolve(projectile)) {
                    projectile.registerArenaWallBounce(
                        onBounce = {
                            haptics.bounce()
                            sounds.playWallImpact()
                        },
                        onBreak = {
                            haptics.projectileBreak()
                            sounds.playProjectileBreak()
                        }
                    )
                }
            }
        }
    }

    private fun resolveProjectileEnemyCollisions() {
        for (projectile in projectiles) {
            if (!projectile.active) continue

            for (enemy in enemies) {
                if (!enemy.active) continue

                val dx = enemy.x - projectile.x
                val dy = enemy.y - projectile.y
                val hitDistance = enemy.radius + projectile.radius

                if (dx * dx + dy * dy > hitDistance * hitDistance) continue

                enemy.takeDamage(projectile.damage())
                if (enemy.boss) {
                    val shotSpeed = projectile.speed().coerceAtLeast(1f)
                    enemy.vx += projectile.vx / shotSpeed * BOSS_HIT_KNOCKBACK
                    enemy.vy += projectile.vy / shotSpeed * BOSS_HIT_KNOCKBACK
                }
                sounds.playProjectileImpact()
                haptics.projectileCollision()

                if (!projectile.piercing) {
                    projectile.deactivate()
                    break
                }
            }
        }
    }

    private fun updateBeamDamage(dt: Float) {
        val geometry = currentBeamGeometry()

        if (geometry == null) {
            beamContactTime.clear()
            return
        }

        // Beam can progressively cut destructible steel.
        for (steel in destructibleSteel) {
            if (!steel.active) continue
            if (beamTouchesRect(geometry, steel.rect)) {
                steel.takeBeamDamage(BEAM_STEEL_DAMAGE_PER_SECOND * dt)
            }
        }

        val touchingNow = mutableSetOf<Long>()

        for (enemy in enemies) {
            if (!enemy.active) continue
            if (!beamTouchesEnemy(geometry, enemy)) continue

            touchingNow += enemy.id
            var accumulated = (beamContactTime[enemy.id] ?: 0f) + dt

            while (accumulated >= BEAM_DAMAGE_TICK_SECONDS && enemy.active) {
                val beamDamageMultiplier =
                    1f + geometry.powerRatio.coerceIn(0f, 1f) *
                        (MAX_BEAM_DAMAGE_MULTIPLIER - 1f)
                enemy.takeDamage(BEAM_DAMAGE_PER_TICK * beamDamageMultiplier)
                if (enemy.boss) {
                    enemy.vx += geometry.nx * BOSS_BEAM_KNOCKBACK
                    enemy.vy += geometry.ny * BOSS_BEAM_KNOCKBACK
                }
                accumulated -= BEAM_DAMAGE_TICK_SECONDS
                sounds.playProjectileImpact()
                haptics.projectileCollision()
            }

            beamContactTime[enemy.id] = accumulated
        }

        beamContactTime.keys.retainAll(touchingNow)
    }

    private fun currentBeamGeometry(): BeamPulse? {
        // While the player is actively aiming Beam, use exactly the same
        // direction and width that drawChargingBeam() renders.
        if (
            activeShotType == ShotType.BEAM &&
            inputMode == InputMode.AIM_SHOT &&
            slingshot.isPulled
        ) {
            val px = slingshot.pullX
            val py = slingshot.pullY
            val distance = sqrt(px * px + py * py)

            if (distance > 0.001f) {
                var beamWidth =
                    MIN_BEAM_WIDTH +
                        slingshot.pullRatio * (MAX_BEAM_WIDTH - MIN_BEAM_WIDTH)

                if (heavyShotCharged) {
                    beamWidth *= HEAVY_BEAM_WIDTH_MULTIPLIER
                }

                return BeamPulse(
                    startX = slingshot.x,
                    startY = slingshot.y - 55f,
                    nx = -px / distance,
                    ny = -py / distance,
                    width = beamWidth,
                    powerRatio = slingshot.pullRatio.coerceIn(0f, 1f),
                    lifeSeconds = BEAM_LIFETIME
                )
            }
        }

        // After release, the visible pulse remains damaging for its lifetime.
        return beamPulse
    }

    private fun beamTouchesRect(pulse: BeamPulse, rect: RectF): Boolean {
        val points = arrayOf(
            floatArrayOf(rect.left, rect.top),
            floatArrayOf(rect.right, rect.top),
            floatArrayOf(rect.left, rect.bottom),
            floatArrayOf(rect.right, rect.bottom),
            floatArrayOf(rect.centerX(), rect.centerY())
        )

        val threshold = pulse.width * 0.5f + 8f
        for (point in points) {
            val dx = point[0] - pulse.startX
            val dy = point[1] - pulse.startY
            val along = dx * pulse.nx + dy * pulse.ny
            if (along < 0f) continue
            val px = dx - pulse.nx * along
            val py = dy - pulse.ny * along
            if (px * px + py * py <= threshold * threshold) return true
        }
        return false
    }

    private fun beamTouchesEnemy(pulse: BeamPulse, enemy: Enemy): Boolean {
        val toEnemyX = enemy.x - pulse.startX
        val toEnemyY = enemy.y - pulse.startY

        val along = toEnemyX * pulse.nx + toEnemyY * pulse.ny
        if (along < 0f) return false

        val perpendicularX = toEnemyX - pulse.nx * along
        val perpendicularY = toEnemyY - pulse.ny * along
        val maxDistance = pulse.width * 0.5f + enemy.radius

        return perpendicularX * perpendicularX + perpendicularY * perpendicularY <=
            maxDistance * maxDistance
    }

    private fun lerp(a: Float, b: Float, t: Float): Float =
        a + (b - a) * t

    private fun randomRange(min: Float, max: Float): Float =
        min + kotlin.random.Random.nextFloat() * (max - min).coerceAtLeast(0f)

    private fun resolveProjectileCollisions() {
        val contactsThisPass = mutableSetOf<ContactPair>()

        for (i in 0 until projectiles.size) {
            val a = projectiles[i]
            if (!a.active || a.piercing) continue

            for (j in i + 1 until projectiles.size) {
                val b = projectiles[j]
                if (!b.active || b.piercing) continue

                val dx = b.x - a.x
                val dy = b.y - a.y
                val minDistance = a.radius + b.radius
                val distanceSquared = dx * dx + dy * dy

                if (distanceSquared >= minDistance * minDistance) continue

                val pair = contactPair(a.id, b.id)
                contactsThisPass.add(pair)

                val distance = sqrt(distanceSquared)

                val nx: Float
                val ny: Float

                if (distance < 0.0001f) {
                    nx = 1f
                    ny = 0f
                } else {
                    nx = dx / distance
                    ny = dy / distance
                }

                val penetration =
                    if (distance < 0.0001f) minDistance
                    else minDistance - distance

                if (penetration > 0f) {
                    val totalMass = a.mass + b.mass
                    val aShare = b.mass / totalMass
                    val bShare = a.mass / totalMass
                    val correction = penetration + 0.02f

                    a.x -= nx * correction * aShare
                    a.y -= ny * correction * aShare
                    b.x += nx * correction * bShare
                    b.y += ny * correction * bShare
                }

                val relativeVx = b.vx - a.vx
                val relativeVy = b.vy - a.vy
                val velocityAlongNormal =
                    relativeVx * nx + relativeVy * ny

                if (velocityAlongNormal < 0f) {
                    val inverseMassA = 1f / a.mass
                    val inverseMassB = 1f / b.mass

                    val impulseMagnitude =
                        -(1f + COLLISION_RESTITUTION) *
                            velocityAlongNormal /
                            (inverseMassA + inverseMassB)

                    val impulseX = impulseMagnitude * nx
                    val impulseY = impulseMagnitude * ny

                    a.vx -= impulseX * inverseMassA
                    a.vy -= impulseY * inverseMassA
                    b.vx += impulseX * inverseMassB
                    b.vy += impulseY * inverseMassB

                    if (pair !in activeContacts) {
                        haptics.projectileCollision()
                        sounds.playProjectileImpact()
                    }
                }
            }
        }

        activeContacts.clear()
        activeContacts.addAll(contactsThisPass)
    }

    private fun contactPair(a: Long, b: Long): ContactPair =
        if (a < b) ContactPair(a, b) else ContactPair(b, a)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Consume the entire gesture on UI controls. An UP must match its DOWN;
        // opening an overlay never turns the same gesture into a launch or shot.
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            pressedUiAction = flightUi.hit(event.x, event.y)
            flightUi.press(pressedUiAction)
            if (pressedUiAction != null || titleScreen || flightScreen != FlightScreen.PLAYING) {
                invalidate()
                return true
            }
        }
        if (pressedUiAction != null || titleScreen || flightScreen != FlightScreen.PLAYING) {
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                val action = pressedUiAction
                pressedUiAction = null
                flightUi.press(null)
                if (action != null && action == flightUi.hit(event.x, event.y)) handleFlightAction(action)
                performClick()
            } else if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                pressedUiAction = null
                flightUi.press(null)
            }
            invalidate()
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchTracks.clear()
                addTouchTrack(event, event.actionIndex)
                inputMode = InputMode.GESTURE_PENDING
                resetCharge()
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // Two independent fingers are supported. Once one finger has claimed
                // MOVE or SHOOT, the other is constrained to the complementary role.
                if (touchTracks.size < 2) {
                    addTouchTrack(event, event.actionIndex)
                }
                if (event.pointerCount >= 2 && gameMode == GameMode.ENEMY) {
                    lastTwoFingerCenterX = (event.getX(0) + event.getX(1)) * 0.5f
                    touchTracks.values.forEach { it.role = TouchRole.MOVE }
                    slingshot.cancelPull()
                    shotPointerId = MotionEvent.INVALID_POINTER_ID
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount >= 2 && touchTracks.size >= 2 && gameMode == GameMode.ENEMY) {
                    // Two-finger slide exclusively controls horizontal ship movement.
                    val x0 = event.getX(0)
                    val x1 = event.getX(1)
                    val centerX = (x0 + x1) * 0.5f
                    val tracks = touchTracks.values.take(2)
                    if (tracks.none { it.role == TouchRole.MOVE }) {
                        tracks.forEach { it.role = TouchRole.MOVE }
                        lastTwoFingerCenterX = centerX
                        slingshot.cancelPull()
                        shotPointerId = MotionEvent.INVALID_POINTER_ID
                    }
                    val dx = centerX - lastTwoFingerCenterX
                    if (kotlin.math.abs(dx) > 0.1f) slingshot.moveBy(dx, width.toFloat())
                    lastTwoFingerCenterX = centerX
                    tracks.forEachIndexed { index, track ->
                        track.lastX = event.getX(index)
                        track.lastY = event.getY(index)
                    }
                } else {
                    // A single finger is reserved for the slingshot firing gesture.
                    val id = event.getPointerId(0)
                    val track = touchTracks[id]
                    if (track != null) updateSingleFingerShot(
                        track, event.x,
                        if (gameMode == GameMode.TARGET) event.y + puzzleCameraY else event.y
                    )
                }
                syncInputModeFromTracks()
                return true
            }

            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_UP -> {
                val index = event.actionIndex
                val id = event.getPointerId(index)
                val track = touchTracks[id]

                if (track?.role == TouchRole.SHOOT) {
                    haptics.stop()
                    sounds.stopBeamLoop()
                    fireCurrentShot()
                } else if (
                    event.actionMasked == MotionEvent.ACTION_UP &&
                    track?.role == TouchRole.PENDING &&
                    touchTracks.size == 1
                ) {
                    registerTapForBomb()
                }

                touchTracks.remove(id)

                if (track?.role == TouchRole.SHOOT) {
                    shotPointerId = MotionEvent.INVALID_POINTER_ID
                }

                if (touchTracks.isEmpty()) {
                    finishGesture()
                } else {
                    // After a two-finger move ends, the remaining finger must begin a new
                    // drag before it can fire; this prevents accidental shots on lift-off.
                    val remaining = touchTracks.values.first()
                    remaining.role = TouchRole.PENDING
                    touchTracks[remaining.pointerId] = remaining.copy(
                        startX = remaining.lastX,
                        startY = remaining.lastY,
                        role = TouchRole.PENDING
                    )
                    syncInputModeFromTracks()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                touchTracks.clear()
                cancelCurrentShot()
                finishGesture()
                return true
            }
        }

        return true
    }

    private fun addTouchTrack(event: MotionEvent, index: Int) {
        val id = event.getPointerId(index)
        val x = event.getX(index)
        val rawY = event.getY(index)
        val y = if (gameMode == GameMode.TARGET) rawY + puzzleCameraY else rawY
        touchTracks[id] = TouchTrack(id, x, y, x, y)
    }

    private fun updateSingleFingerShot(track: TouchTrack, x: Float, y: Float) {
        val dx = x - track.startX
        val dy = y - track.startY
        if (track.role == TouchRole.PENDING && sqrt(dx * dx + dy * dy) >= GESTURE_CLASSIFY_DISTANCE) {
            track.role = TouchRole.SHOOT
            touchStartX = track.startX
            touchStartY = track.startY
            shotPointerId = track.pointerId
            resetCharge()
            autoHasFired = false
        }
        if (track.role == TouchRole.SHOOT) {
            touchStartX = track.startX
            touchStartY = track.startY
            updateShot(x, y)
        }
        track.lastX = x
        track.lastY = y
    }

    private fun updateTouchTrack(track: TouchTrack, x: Float, y: Float) {
        if (track.role == TouchRole.PENDING) {
            classifyTouchTrack(track, x, y)
            maybeActivateLongPressThrust(track, x, y)
        }

        when (track.role) {
            TouchRole.MOVE -> {
                // Action-control model: the movement finger is a horizontal track.
                // The shuttle follows the finger's X directly instead of accumulating swipes.
                slingshot.trackHorizontal(x, width.toFloat())
            }
            TouchRole.SHOOT -> {
                // The shooting finger owns the pull vector while the other finger may
                // translate the shuttle horizontally at the same time.
                touchStartX = track.startX
                touchStartY = track.startY
                shotPointerId = track.pointerId
                updateShot(x, y)
            }
            TouchRole.THRUST -> Unit
            TouchRole.PENDING -> Unit
        }

        track.lastX = x
        track.lastY = y
    }

    private fun maybeActivateLongPressThrust(track: TouchTrack, x: Float, y: Float) {
        if (track.role != TouchRole.PENDING) return
        val dx = x - track.startX
        val dy = y - track.startY
        val travelSquared = dx * dx + dy * dy
        if (travelSquared > LONG_PRESS_MOVE_TOLERANCE * LONG_PRESS_MOVE_TOLERANCE) return
        if (System.currentTimeMillis() - track.downTimeMs < THRUSTER_LONG_PRESS_MS) return

        track.role = TouchRole.THRUST
        haptics.modeSelected()
    }

    private fun classifyTouchTrack(track: TouchTrack, x: Float, y: Float) {
        val dx = x - track.startX
        val dy = y - track.startY
        val absX = kotlin.math.abs(dx)
        val absY = kotlin.math.abs(dy)

        if (absX < GESTURE_CLASSIFY_DISTANCE && absY < GESTURE_CLASSIFY_DISTANCE) return

        val occupiedRole =
            touchTracks.values
                .firstOrNull {
                    it.pointerId != track.pointerId &&
                        it.role != TouchRole.PENDING &&
                        it.role != TouchRole.THRUST
                }
                ?.role

        val requiredRole =
            when (occupiedRole) {
                TouchRole.MOVE -> TouchRole.SHOOT
                TouchRole.SHOOT -> TouchRole.MOVE
                TouchRole.THRUST,
                TouchRole.PENDING,
                null -> null
            }

        val vertical =
            absY > GESTURE_CLASSIFY_DISTANCE &&
                absY >= absX * FIRE_DIRECTION_BIAS
        val horizontal =
            absX > GESTURE_CLASSIFY_DISTANCE &&
                absX > absY * MOVE_DIRECTION_BIAS

        val selected =
            when (requiredRole) {
                TouchRole.SHOOT -> if (vertical) TouchRole.SHOOT else null
                TouchRole.MOVE -> if (horizontal) TouchRole.MOVE else null
                else -> when {
                    vertical -> TouchRole.SHOOT
                    horizontal -> TouchRole.MOVE
                    else -> null
                }
            } ?: return

        track.role = selected

        if (selected == TouchRole.SHOOT) {
            touchStartX = track.startX
            touchStartY = track.startY
            currentTouchX = x
            currentTouchY = y
            shotPointerId = track.pointerId
            resetCharge()
            autoHasFired = false
        } else {
            haptics.stop()
            if (touchTracks.values.none { it.role == TouchRole.SHOOT }) {
                sounds.stopBeamLoop()
            }
        }

        // If the other finger is still pending, its eventual classification is now
        // forced to the opposite role. This makes role assignment order-independent.
    }

    private fun syncInputModeFromTracks() {
        inputMode =
            when {
                touchTracks.values.any { it.role == TouchRole.SHOOT } -> InputMode.AIM_SHOT
                touchTracks.values.any { it.role == TouchRole.MOVE } -> InputMode.MOVE_SLINGSHOT
                touchTracks.isNotEmpty() -> InputMode.GESTURE_PENDING
                else -> InputMode.NONE
            }
    }

    private fun registerTapForBomb() {
        val now = System.currentTimeMillis()
        tapSequenceCount =
            if (now - lastTapUpMs <= TRIPLE_TAP_WINDOW_MS) tapSequenceCount + 1
            else 1
        lastTapUpMs = now

        if (tapSequenceCount >= 3) {
            tapSequenceCount = 0
            if (bombInventory > 0) detonateBomb()
        }
    }

    private fun detonateBomb() {
        if (bombInventory <= 0) return
        bombInventory--
        bombBlastX = slingshot.x
        bombBlastY = slingshot.y - PLAYER_COLLISION_Y_OFFSET
        bombBlastSeconds = BOMB_BLAST_DURATION

        // Bomb clears current-screen enemies and destructible structures.
        for (enemy in enemies) {
            if (enemy.active &&
                enemy.x + enemy.radius >= 0f &&
                enemy.x - enemy.radius <= width &&
                enemy.y + enemy.radius >= 0f &&
                enemy.y - enemy.radius <= height
            ) {
                enemy.takeDamage(BOMB_DAMAGE)
            }
        }

        for (steel in destructibleSteel) {
            if (steel.active &&
                steel.rect.right >= 0f &&
                steel.rect.left <= width &&
                steel.rect.bottom >= 0f &&
                steel.rect.top <= height
            ) {
                steel.takeBeamDamage(BOMB_DAMAGE)
            }
        }

        haptics.projectileBreak()
        sounds.playProjectileBreak()
    }

    private fun gameModeAt(x: Float, y: Float): GameMode? =
        gameModeButtons.entries.firstOrNull { it.value.contains(x, y) }?.key

    private fun selectGameMode(mode: GameMode) {
        if (gameMode == mode) return

        gameMode = mode
        gameOver = false
        playerMaxHealth = PLAYER_STARTING_MAX_HEALTH
        playerHealth = playerMaxHealth
        runTimeSeconds = 0f
        powerUps.clear()
        collectedPowerUps.clear()
        bombInventory = 0
        enemyPlayerContacts.clear()
        solidWallPlayerContacts.clear()
        destructiblePlayerContacts.clear()
        projectiles.clear()
        activeContacts.clear()
        beamPulse = null
        sounds.stopBeamLoop()

        initialiseEnemies(width.toFloat(), height.toFloat())
        haptics.modeSelected()
        sounds.playModeSelect()
    }

    private fun modeAt(x: Float, y: Float): ShotType? =
        modeButtons.entries.firstOrNull { it.value.contains(x, y) }?.key

    private fun selectMode(mode: ShotType) {
        when (mode) {
            ShotType.BOUNCE,
            ShotType.SPREAD,
            ShotType.AUTO,
            ShotType.PIERCING,
            ShotType.DOUBLE -> {
                if (!collectedPowerUps.add(mode)) {
                    collectedPowerUps.remove(mode)
                }
                activeShotType = ShotType.NONE
            }

            ShotType.BOMB -> {
                if (bombInventory > 0) bombInventory-- else bombInventory++
                activeShotType = ShotType.NONE
            }

            ShotType.SHIELD -> {
                playerHealth = (playerHealth + 1).coerceAtMost(playerMaxHealth)
                activeShotType = ShotType.NONE
            }

            ShotType.FUEL -> {
                fuel = 1f
                        activeShotType = ShotType.NONE
            }

            ShotType.BEAM -> {
                activeShotType =
                    if (activeShotType == ShotType.BEAM) ShotType.NONE else ShotType.BEAM
            }

            ShotType.NONE -> {
                collectedPowerUps.clear()
                activeShotType = ShotType.NONE
            }
        }

        sounds.stopBeamLoop()
        haptics.modeSelected()
        sounds.playModeSelect()
    }

    private fun beginShot(event: MotionEvent) {
        inputMode = InputMode.AIM_SHOT
        shotPointerId = event.getPointerId(0)

        touchStartX = event.x
        touchStartY = event.y
        currentTouchX = event.x
        currentTouchY = event.y

        resetCharge()
        autoHasFired = false
    }

    private fun updateShot(event: MotionEvent) {
        val pointerIndex = event.findPointerIndex(shotPointerId)
        if (pointerIndex < 0) return

        updateShot(
            event.getX(pointerIndex),
            event.getY(pointerIndex)
        )
    }

    private fun updateShot(x: Float, y: Float) {
        currentTouchX = x
        currentTouchY = y

        val dx = currentTouchX - touchStartX
        val dy = currentTouchY - touchStartY

        // Signed pull: down-drag fires upward; up-drag fires downward.
        slingshot.updatePull(dx, dy)

        haptics.updateTension(
            slingshot.pullRatio,
            slingshot.rawPullDistance >= Slingshot.MAX_PULL_DISTANCE
        )

        if (activeShotType == ShotType.BEAM && kotlin.math.abs(dy) >= MIN_FIRE_DISTANCE) {
            sounds.startOrUpdateBeamLoop(
                tension = slingshot.pullRatio,
                heavy = heavyShotCharged
            )
        } else {
            sounds.stopBeamLoop()
        }
    }

    private fun fireCurrentShot() {
        if (!slingshot.isPulled) return

        val pullX = slingshot.pullX
        val pullY = slingshot.pullY
        val pullRatio = slingshot.pullRatio
        val distance = sqrt(pullX * pullX + pullY * pullY)

        if (distance < MIN_FIRE_DISTANCE) {
            slingshot.cancelPull()
            return
        }

        if (activeShotType == ShotType.BEAM) {
            sounds.stopBeamLoop()
            val useCharge = gameMode == GameMode.ENEMY && heavyShotCharged && chargeEnergy >= 0.999f
            volleysLaunched++
            fireBeam(pullX, pullY, pullRatio)
            if (useCharge) {
                chargeEnergy = 0f
                chargeRechargeDelay = 0f
                heavyShotCharged = false
            }
            haptics.beamFire()
        } else if (ShotType.AUTO !in collectedPowerUps) {
            val useCharge = gameMode == GameMode.ENEMY && heavyShotCharged && chargeEnergy >= 0.999f
            firePoweredVolley(pullX, pullY, pullRatio, useCharge)
            if (useCharge) {
                chargeEnergy = 0f
                chargeRechargeDelay = 0f
                heavyShotCharged = false
            }
            haptics.release()
            sounds.playShot(
                when {
                    ShotType.SPREAD in collectedPowerUps -> ShotType.SPREAD
                    ShotType.PIERCING in collectedPowerUps -> ShotType.PIERCING
                    else -> ShotType.NONE
                },
                heavyShotCharged
            )
        }
        // AUTO fires while the pull is held and adds no extra release volley.

        slingshot.fireRelease()
        resetCharge()
    }

    private fun firePoweredVolley(
        pullX: Float,
        pullY: Float,
        pullRatio: Float,
        heavy: Boolean
    ) {
        if (gameMode == GameMode.TARGET && projectiles.any { it.active }) return
        volleysLaunched++
        val piercing = ShotType.PIERCING in collectedPowerUps
        val bouncy = ShotType.BOUNCE in collectedPowerUps
        val doubleShot = ShotType.DOUBLE in collectedPowerUps
        val angles =
            if (ShotType.SPREAD in collectedPowerUps) {
                floatArrayOf(-20f, -10f, 0f, 10f, 20f)
            } else {
                floatArrayOf(0f)
            }

        val distance = sqrt(pullX * pullX + pullY * pullY).coerceAtLeast(1f)
        val shotNx = -pullX / distance
        val shotNy = -pullY / distance
        val perpX = -shotNy
        val perpY = shotNx

        for (degrees in angles) {
            if (doubleShot) {
                spawnSingleProjectile(
                    pullX, pullY, pullRatio, heavy, piercing,
                    bouncy,
                    Math.toRadians(degrees.toDouble()).toFloat(),
                    perpX * DOUBLE_SHOT_HALF_SEPARATION,
                    perpY * DOUBLE_SHOT_HALF_SEPARATION
                )
                spawnSingleProjectile(
                    pullX, pullY, pullRatio, heavy, piercing,
                    bouncy,
                    Math.toRadians(degrees.toDouble()).toFloat(),
                    -perpX * DOUBLE_SHOT_HALF_SEPARATION,
                    -perpY * DOUBLE_SHOT_HALF_SEPARATION
                )
            } else {
                spawnSingleProjectile(
                    pullX = pullX,
                    pullY = pullY,
                    pullRatio = pullRatio,
                    heavy = heavy,
                    piercing = piercing,
                    angleOffsetRadians = Math.toRadians(degrees.toDouble()).toFloat()
                )
            }
        }
    }

    private fun spawnSingleProjectile(
        pullX: Float,
        pullY: Float,
        pullRatio: Float,
        heavy: Boolean,
        piercing: Boolean,
        bouncy: Boolean = ShotType.BOUNCE in collectedPowerUps,
        angleOffsetRadians: Float = 0f,
        spawnOffsetX: Float = 0f,
        spawnOffsetY: Float = 0f
    ) {
        if (projectiles.count { it.active } >= MAX_ACTIVE_PROJECTILES) return

        val distance = sqrt(pullX * pullX + pullY * pullY)
        if (distance <= 0f) return

        val baseNx = -pullX / distance
        val baseNy = -pullY / distance

        val c = cos(angleOffsetRadians)
        val s = sin(angleOffsetRadians)

        val nx = baseNx * c - baseNy * s
        val ny = baseNx * s + baseNy * c

        val speed =
            MIN_LAUNCH_SPEED +
                pullRatio.coerceIn(0f, 1f) *
                    (MAX_LAUNCH_SPEED - MIN_LAUNCH_SPEED)

        val normalRadius =
            if (bouncy) Slingshot.PROJECTILE_RADIUS
            else Slingshot.PROJECTILE_RADIUS * 0.5f
        val radius =
            if (gameMode == GameMode.TARGET) puzzleLayout?.shotRadius ?: normalRadius
            else if (heavy) normalRadius * Slingshot.HEAVY_RADIUS_MULTIPLIER
            else normalRadius

        val spawnOffset = radius + 18f

        projectiles.add(
            Projectile(
                id = nextProjectileId++,
                x = slingshot.x + nx * spawnOffset,
                y = slingshot.y - 50f + ny * spawnOffset,
                vx = nx * speed,
                vy = ny * speed,
                radius = radius,
                piercing = piercing,
                bouncy = bouncy,
                bounceLimit = if (gameMode == GameMode.TARGET) PuzzleCampaign.level(puzzleLevel).bounces else Projectile.MAX_BOUNCES - 1,
                maxLifetimeSeconds = if (gameMode == GameMode.TARGET)
                    maxOf(12f, (puzzleWorldHeight * 2f + width * PuzzleCampaign.level(puzzleLevel).bounces) / MIN_LAUNCH_SPEED + 2f)
                    else 5f
            )
        )
    }

    private fun fireSpread(
        pullX: Float,
        pullY: Float,
        pullRatio: Float,
        heavy: Boolean
    ) {
        val offsets = floatArrayOf(-20f, -10f, 0f, 10f, 20f)

        for (degrees in offsets) {
            spawnSingleProjectile(
                pullX = pullX,
                pullY = pullY,
                pullRatio = pullRatio,
                heavy = heavy,
                piercing = false,
                angleOffsetRadians = Math.toRadians(degrees.toDouble()).toFloat()
            )
        }
    }

    private fun fireBeam(
        pullX: Float,
        pullY: Float,
        pullRatio: Float
    ) {
        val distance = sqrt(pullX * pullX + pullY * pullY)
        if (distance <= 0f) return

        val nx = -pullX / distance
        val ny = -pullY / distance

        var width =
            MIN_BEAM_WIDTH +
                pullRatio * (MAX_BEAM_WIDTH - MIN_BEAM_WIDTH)

        if (heavyShotCharged) {
            width *= HEAVY_BEAM_WIDTH_MULTIPLIER
        }

        beamPulse = BeamPulse(
            startX = slingshot.x,
            startY = slingshot.y - 55f,
            nx = nx,
            ny = ny,
            width = width,
            powerRatio = pullRatio.coerceIn(0f, 1f)
        )
    }

    private fun resetCharge() {
        fullExtensionStartTimeMs = 0L
        heavyShotCharged = false
        heavyChargeHapticSent = false
        autoChargeActive = false
    }

    private fun cancelCurrentShot() {
        touchTracks.clear()
        slingshot.cancelPull()
        beamPulse = null
        sounds.stopBeamLoop()
        haptics.stop()
        autoHasFired = false
        resetCharge()
    }

    private fun finishGesture() {
        touchTracks.clear()
        haptics.stop()
        sounds.stopBeamLoop()
        inputMode = InputMode.NONE
        shotPointerId = MotionEvent.INVALID_POINTER_ID
        autoHasFired = false
        resetCharge()
    }

    override fun onDetachedFromWindow() {
        sounds.release()
        haptics.stop()
        super.onDetachedFromWindow()
    }

    private fun drawIndustrialBackdrop(canvas: Canvas) {
        val spacing = width / 5f
        var x = 0f
        while (x <= width) {
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
            x += spacing
        }

        var y = 0f
        while (y <= height) {
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
            y += spacing
        }

        // Bottom machine rail only; top and side field boundaries are open.
        canvas.drawRect(
            0f,
            height.toFloat() - 112f,
            width.toFloat(),
            height.toFloat(),
            panelPaint
        )
        canvas.drawLine(
            0f,
            height.toFloat() - 112f,
            width.toFloat(),
            height.toFloat() - 112f,
            accentPaint
        )
    }

    private fun drawTrajectoryGuide(canvas: Canvas) {
        if (inputMode != InputMode.AIM_SHOT || !slingshot.isPulled) return

        val distance = sqrt(
            slingshot.pullX * slingshot.pullX +
                slingshot.pullY * slingshot.pullY
        )
        if (distance <= 0f) return

        val nx = -slingshot.pullX / distance
        val ny = -slingshot.pullY / distance

        if (gameMode == GameMode.TARGET) {
            val layout = puzzleLayout ?: return
            val key = "$puzzleLevel/$nx/$ny/${enemies.count { it.active }}"
            if (key != guideCacheKey) {
                guideCache = PuzzleTrajectory.trace(slingshot.x, slingshot.y-50f, nx, ny,
                    layout.shotRadius, arenaWalls, height * layout.level.guideScreens,
                    layout.level.bounces, enemies)
                guideCacheKey = key
            }
            val points = guideCache
            trajectoryPaint.style = Paint.Style.STROKE
            trajectoryPaint.strokeWidth = 2f
            trajectoryPaint.color = 0x5570EBE2
            for (i in 1 until points.size) {
                val a=points[i-1]; val b=points[i]
                canvas.drawLine(a.x,a.y,b.x,b.y,trajectoryPaint)
            }
            trajectoryPaint.style = Paint.Style.FILL
            for (i in points.indices step 4) {
                val point=points[i]
                val fade=(1f-i.toFloat()/points.size).coerceAtLeast(0.2f)
                trajectoryPaint.color = ((fade*210f).toInt() shl 24) or 0x0070EBE2
                canvas.drawCircle(point.x,point.y,3.2f,trajectoryPaint)
            }
            for (i in 1 until points.size) if(points[i].bounces > points[i-1].bounces) {
                trajectoryPaint.color=0xFFFFBA66.toInt()
                trajectoryPaint.style=Paint.Style.STROKE
                canvas.drawCircle(points[i].x,points[i].y,8f,trajectoryPaint)
                trajectoryPaint.style=Paint.Style.FILL
            }
            points.lastOrNull()?.let {
                trajectoryPaint.color=0xCCFFFFFF.toInt()
                canvas.drawCircle(it.x,it.y,5f,trajectoryPaint)
            }
            return
        }
        trajectoryPaint.color=0x9970EBE2.toInt()
        val startX = slingshot.x
        val startY = slingshot.y - 60f

        val guideLength =
            MIN_GUIDE_LENGTH +
                slingshot.pullRatio *
                    (MAX_GUIDE_LENGTH - MIN_GUIDE_LENGTH)

        val spacing =
            MIN_DOT_SPACING +
                slingshot.pullRatio *
                    (MAX_DOT_SPACING - MIN_DOT_SPACING)

        val dotCount =
            max(2, ceil(guideLength / spacing).toInt())

        for (i in 1..dotCount) {
            val t = (i.toFloat() / dotCount) * guideLength
            val progress = i.toFloat() / dotCount
            val radius = 5.5f - progress * 2.5f

            canvas.drawCircle(
                startX + nx * t,
                startY + ny * t,
                radius,
                trajectoryPaint
            )
        }
    }

    private fun drawDamageFeedback(canvas: Canvas) {
        if (damageFlashSeconds <= 0f) return

        val ratio = (damageFlashSeconds / DAMAGE_FLASH_DURATION).coerceIn(0f, 1f)
        val alpha = (150f * ratio).toInt().coerceIn(0, 150)
        damageFlashPaint.color = (alpha shl 24) or 0x00E53935
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), damageFlashPaint)

        damageFlashPaint.style = Paint.Style.STROKE
        damageFlashPaint.strokeWidth = 18f
        damageFlashPaint.color = ((220f * ratio).toInt().coerceIn(0, 220) shl 24) or 0x00FF6F60
        canvas.drawRect(9f, 9f, width - 9f, height - 9f, damageFlashPaint)
        damageFlashPaint.style = Paint.Style.FILL
    }

    private fun drawGameOver(canvas: Canvas) {
        val overlay = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xB8000000.toInt()
        }
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFB300.toInt()
            textAlign = Paint.Align.CENTER
            textSize = 64f
            typeface = Typeface.DEFAULT_BOLD
        }
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE5E7E9.toInt()
            textAlign = Paint.Align.CENTER
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
        }

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), overlay)
        canvas.drawText("GAME OVER", width * 0.5f, height * 0.46f, title)
        canvas.drawText("RESETTING RUN", width * 0.5f, height * 0.46f + 48f, sub)
    }

    private fun drawBombBlast(canvas: Canvas) {
        if (bombBlastSeconds <= 0f) return

        val progress =
            (1f - bombBlastSeconds / BOMB_BLAST_DURATION).coerceIn(0f, 1f)
        // Fast ease-out expansion; radius is guaranteed to pass every screen corner.
        val eased = 1f - (1f - progress) * (1f - progress)
        val farX = maxOf(bombBlastX, width.toFloat() - bombBlastX)
        val farY = maxOf(bombBlastY, height.toFloat() - bombBlastY)
        val screenRadius = sqrt(farX * farX + farY * farY)
        val radius = screenRadius * eased

        val fade = (1f - progress).coerceIn(0f, 1f)
        buttonPaint.style = Paint.Style.FILL
        buttonPaint.color =
            ((95f * fade).toInt().coerceIn(0, 255) shl 24) or 0x00E8F7FF
        canvas.drawCircle(bombBlastX, bombBlastY, radius, buttonPaint)

        buttonPaint.style = Paint.Style.STROKE
        buttonPaint.strokeWidth = 18f * fade + 3f
        buttonPaint.color =
            ((175f * fade).toInt().coerceIn(0, 255) shl 24) or 0x00FFFFFF
        canvas.drawCircle(bombBlastX, bombBlastY, radius, buttonPaint)
        buttonPaint.style = Paint.Style.FILL
    }

    private fun drawPlayerHealth(canvas: Canvas) {
        val startX = 18f
        val y = 92f
        canvas.drawText("HULL", startX, y, playerHealthPaint)

        if (collectedPowerUps.isNotEmpty()) {
            val loadout = collectedPowerUps.joinToString(" + ") { it.label }
            canvas.drawText(loadout, startX, y + 32f, playerHealthPaint)
        }
        if (bombInventory > 0) {
            canvas.drawText("BOMB ×$bombInventory", startX, y + 64f, playerHealthPaint)
        }

        var x = startX + 82f
        for (i in 0 until playerMaxHealth) {
            val paint = if (i < playerHealth) playerHealthPaint else playerHealthLostPaint
            canvas.drawText("◆", x, y, paint)
            x += 31f
        }
    }

    private fun drawChargingBeam(canvas: Canvas) {
        if (inputMode != InputMode.AIM_SHOT || !slingshot.isPulled) return

        val distance = sqrt(
            slingshot.pullX * slingshot.pullX +
                slingshot.pullY * slingshot.pullY
        )
        if (distance <= 0f) return

        val nx = -slingshot.pullX / distance
        val ny = -slingshot.pullY / distance

        var beamWidth =
            MIN_BEAM_WIDTH +
                slingshot.pullRatio *
                    (MAX_BEAM_WIDTH - MIN_BEAM_WIDTH)

        if (heavyShotCharged) {
            beamWidth *= HEAVY_BEAM_WIDTH_MULTIPLIER
        }

        val startX = slingshot.x
        val startY = slingshot.y - 55f
        val length = max(width, height).toFloat() * 1.5f

        val chargeGlow = beamWidth.coerceAtLeast(16f) * 2.1f
        for (i in 0..8) {
            val d = length * i / 8f
            drawRadialLight(canvas, startX + nx * d, startY + ny * d,
                chargeGlow, 0x007CE8FF, 44)
        }
        drawRadialLight(canvas, startX, startY, chargeGlow * 1.7f, 0x00E9FDFF, 92)

        // Wide translucent halo underneath the beam makes it illuminate the corridor.
        beamPaint.color = 0x187CE8FF
        beamPaint.strokeWidth = beamWidth * 2.25f
        canvas.drawLine(startX, startY, startX + nx * length, startY + ny * length, beamPaint)

        beamPaint.color = 0x667CE8FF
        beamPaint.strokeWidth = beamWidth

        canvas.drawLine(
            startX,
            startY,
            startX + nx * length,
            startY + ny * length,
            beamPaint
        )

        beamPaint.color = 0xCCFFFFFF.toInt()
        beamPaint.strokeWidth = max(3f, beamWidth * 0.18f)

        canvas.drawLine(
            startX,
            startY,
            startX + nx * length,
            startY + ny * length,
            beamPaint
        )
    }

    private fun drawBeamPulse(canvas: Canvas) {
        val pulse = beamPulse ?: return

        val alpha =
            (255f * (pulse.lifeSeconds / BEAM_LIFETIME))
                .toInt()
                .coerceIn(0, 255)

        val length = max(width, height).toFloat() * 1.5f

        // Dense emissive lighting along the beam path: overlapping radial blooms make
        // the beam light nearby hulls and walls instead of reading as a flat line.
        val beamGlow = pulse.width.coerceAtLeast(18f) * 2.4f
        for (i in 0..10) {
            val d = length * i / 10f
            drawRadialLight(canvas, pulse.startX + pulse.nx * d, pulse.startY + pulse.ny * d,
                beamGlow, 0x007CE8FF, (72f * alpha / 255f).toInt())
        }
        drawRadialLight(canvas, pulse.startX, pulse.startY, beamGlow * 1.8f, 0x00E6FBFF,
            (120f * alpha / 255f).toInt())

        beamPaint.color =
            (((alpha * 0.16f).toInt().coerceIn(0, 255)) shl 24) or 0x007CE8FF
        beamPaint.strokeWidth = pulse.width * 2.6f
        canvas.drawLine(
            pulse.startX, pulse.startY,
            pulse.startX + pulse.nx * length, pulse.startY + pulse.ny * length,
            beamPaint
        )

        beamPaint.color =
            (alpha shl 24) or 0x007CE8FF
        beamPaint.strokeWidth = pulse.width

        canvas.drawLine(
            pulse.startX,
            pulse.startY,
            pulse.startX + pulse.nx * length,
            pulse.startY + pulse.ny * length,
            beamPaint
        )

        beamPaint.color =
            (alpha shl 24) or 0x00FFFFFF
        beamPaint.strokeWidth = max(4f, pulse.width * 0.16f)

        canvas.drawLine(
            pulse.startX,
            pulse.startY,
            pulse.startX + pulse.nx * length,
            pulse.startY + pulse.ny * length,
            beamPaint
        )
    }

    private fun startFromTitle(mode: GameMode) {
        clearFlightInput()
        titleScreen = false
        gameMode = mode
        bossCheckpointAvailable = false
        activeShotType = ShotType.NONE
        puzzleSelection = false
        if (mode == GameMode.ENEMY) puzzleLevel = 1
        volleysLaunched = 0
        slingshot.initialise(width.toFloat(), height.toFloat())
        resetRun()
        sounds.pauseAudio()
        flightScreen = FlightScreen.BRIEFING
        lastFrameTime = System.nanoTime()
        haptics.modeSelected()
        invalidate()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun clearFlightInput() {
        touchTracks.clear()
        shotPointerId = MotionEvent.INVALID_POINTER_ID
        inputMode = InputMode.NONE
        currentThrust = 0f
        autoHasFired = false
        slingshot.cancelPull()
        resetCharge()
        sounds.stopBeamLoop()
        haptics.stop()
    }

    fun pauseFlight() {
        if (!titleScreen && flightScreen == FlightScreen.PLAYING) {
            clearFlightInput()
            flightScreen = FlightScreen.PAUSED
        }
        sounds.pauseAudio()
        invalidate()
    }

    fun isAtFlightDeck(): Boolean = titleScreen && !puzzleSelection

    fun handleBack() {
        if (puzzleSelection) {
            puzzleSelection = false
            invalidate()
            return
        }
        if (titleScreen) return
        if (flightScreen == FlightScreen.PLAYING) pauseFlight() else handleFlightAction("home")
    }

    private fun handleFlightAction(action: String) {
        when (action) {
            "combat" -> startFromTitle(GameMode.ENEMY)
            "puzzle", "levels" -> {
                clearFlightInput()
                sounds.pauseAudio()
                titleScreen = true
                puzzleSelection = true
            }
            "recall" -> {
                clearFlightInput()
                projectiles.clear()
            }
            "restart" -> {
                clearFlightInput()
                volleysLaunched = 0
                populateTargetMode(width.toFloat(), height.toFloat())
                flightScreen = FlightScreen.BRIEFING
            }
            "pause" -> pauseFlight()
            "bomb" -> detonateBomb()
            "launch", "resume" -> {
                flightScreen = FlightScreen.PLAYING
                if (gameMode == GameMode.ENEMY) sounds.resumeAudio()
                lastFrameTime = System.nanoTime()
            }
            "retry" -> {
                clearFlightInput()
                slingshot.initialise(width.toFloat(), height.toFloat())
                activeShotType = ShotType.NONE
                resetRun()
                volleysLaunched = 0
                flightScreen = FlightScreen.PLAYING
                lastFrameTime = System.nanoTime()
            }
            "next" -> {
                if (puzzleLevel >= PuzzleCampaign.LEVEL_COUNT) {
                    titleScreen = true
                    puzzleSelection = true
                    invalidate()
                    return
                }
                clearFlightInput()
                puzzleLevel++
                volleysLaunched = 0
                populateTargetMode(width.toFloat(), height.toFloat())
                flightScreen = FlightScreen.BRIEFING
                lastFrameTime = System.nanoTime()
            }
            "home" -> {
                clearFlightInput()
                sounds.pauseAudio()
                titleScreen = true
                puzzleSelection = false
                flightScreen = FlightScreen.PLAYING
            }
            else -> if (action.startsWith("level:")) {
                val number = action.substringAfter(":").toIntOrNull() ?: return
                if (number !in 1..puzzleUnlocked) return
                puzzleLevel = number
                startFromTitle(GameMode.TARGET)
            }
        }
        haptics.modeSelected()
        invalidate()
    }

    private fun updatePuzzleCameraAndCompletion(dt: Float) {
        val followed = projectiles.lastOrNull { it.active }
        val desired = if (followed != null) {
            (followed.y - height * 0.50f).coerceIn(0f, (puzzleWorldHeight - height).coerceAtLeast(0f))
        } else {
            (slingshot.y - height * 0.78f).coerceIn(0f, (puzzleWorldHeight - height).coerceAtLeast(0f))
        }
        puzzleCameraY += (desired - puzzleCameraY) * (1f - kotlin.math.exp(-7f * dt))

        if (enemies.none { it.active }) {
            puzzleResetDelay += dt
            if (puzzleResetDelay >= 1.0f) {
                clearFlightInput()
                flightScreen = FlightScreen.COMPLETE
                puzzleCompleted = maxOf(puzzleCompleted, puzzleLevel)
                puzzleUnlocked = maxOf(puzzleUnlocked, (puzzleLevel+1).coerceAtMost(PuzzleCampaign.LEVEL_COUNT))
                records.edit().putInt("puzzle_unlocked", puzzleUnlocked).putInt("puzzle_completed", puzzleCompleted).apply()
                sounds.pauseAudio()
            }
        } else puzzleResetDelay = 0f
    }

    private fun drawPuzzleHud(canvas: Canvas) {
        buttonTextPaint.textAlign = Paint.Align.LEFT
        buttonTextPaint.typeface = Typeface.DEFAULT_BOLD
        buttonTextPaint.textSize = 24f
        buttonTextPaint.color = 0xFFEAFBFF.toInt()
        canvas.drawText("PUZZLE  $puzzleLevel", 20f, 42f, buttonTextPaint)
        buttonTextPaint.textSize = 18f
        buttonTextPaint.color = 0xFF64D8FF.toInt()
        canvas.drawText("BOUNCE ONLY   TARGETS ${enemies.count { it.active }}", 20f, 70f, buttonTextPaint)
        buttonTextPaint.textAlign = Paint.Align.CENTER
    }

    private fun drawTitleScreen(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        starfield.draw(canvas)
        val cx = width * 0.5f
        buttonTextPaint.textAlign = Paint.Align.CENTER
        buttonTextPaint.typeface = Typeface.DEFAULT_BOLD
        buttonTextPaint.textSize = 58f
        buttonTextPaint.color = 0xFFF4FBFF.toInt()
        canvas.drawText("SLUGGERSHOT", cx, height * 0.28f, buttonTextPaint)
        buttonTextPaint.textSize = 19f
        buttonTextPaint.color = 0xFF64D8FF.toInt()
        canvas.drawText("CHOOSE YOUR RUN", cx, height * 0.34f, buttonTextPaint)
        drawTitleButton(canvas, titleShootingButton, "PLAY SHOOTING", "combat • powerups • bosses")
        drawTitleButton(canvas, titlePuzzleButton, "PLAY PUZZLES", "bounce • bank shots • targets")
    }

    private fun drawTitleButton(canvas: Canvas, rect: RectF, title: String, subtitle: String) {
        buttonPaint.style = Paint.Style.FILL
        buttonPaint.color = 0xE817232D.toInt()
        canvas.drawRoundRect(rect, 18f, 18f, buttonPaint)
        buttonPaint.style = Paint.Style.STROKE
        buttonPaint.strokeWidth = 4f
        buttonPaint.color = 0xFF29B6F6.toInt()
        canvas.drawRoundRect(rect, 18f, 18f, buttonPaint)
        drawRadialLight(canvas, rect.centerX(), rect.centerY(), rect.width() * 0.42f, 0x0029B6F6, 28)
        buttonTextPaint.textSize = 28f
        buttonTextPaint.color = Color.WHITE
        canvas.drawText(title, rect.centerX(), rect.centerY() - 4f, buttonTextPaint)
        buttonTextPaint.textSize = 16f
        buttonTextPaint.color = 0xFF8EDFFF.toInt()
        canvas.drawText(subtitle, rect.centerX(), rect.centerY() + 25f, buttonTextPaint)
    }

    private fun drawModeButtons(canvas: Canvas) {
        for ((type, rect) in modeButtons) {
            val selected =
                when (type) {
                    ShotType.BOUNCE,
                    ShotType.SPREAD,
                    ShotType.AUTO,
                    ShotType.PIERCING,
                    ShotType.DOUBLE -> type in collectedPowerUps
                    ShotType.BOMB -> bombInventory > 0
                    ShotType.SHIELD -> playerHealth >= playerMaxHealth
                    ShotType.FUEL -> fuel >= 0.99f
                    ShotType.BEAM -> activeShotType == ShotType.BEAM
                    ShotType.NONE -> collectedPowerUps.isEmpty() && activeShotType == ShotType.NONE
                }

            buttonPaint.style = Paint.Style.FILL
            buttonPaint.color =
                if (selected) 0xFF5D4037.toInt()
                else 0xE0212325.toInt()

            canvas.drawRoundRect(rect, 10f, 10f, buttonPaint)

            buttonPaint.style = Paint.Style.STROKE
            buttonPaint.strokeWidth = if (selected) 5f else 2f
            buttonPaint.color =
                if (selected) 0xFFFFFFFF.toInt()
                else 0xFF757575.toInt()

            canvas.drawRoundRect(rect, 10f, 10f, buttonPaint)

            val baseline =
                rect.centerY() -
                    (buttonTextPaint.ascent() + buttonTextPaint.descent()) / 2f

            canvas.drawText(
                type.label,
                rect.centerX(),
                baseline,
                buttonTextPaint
            )
        }
    }

    private fun drawGameModeButtons(canvas: Canvas) {
        for ((mode, rect) in gameModeButtons) {
            val selected = mode == gameMode

            buttonPaint.style = Paint.Style.FILL
            buttonPaint.color =
                if (selected) 0xFF8D4B00.toInt()
                else 0xE0212325.toInt()

            canvas.drawRoundRect(rect, 10f, 10f, buttonPaint)

            buttonPaint.style = Paint.Style.STROKE
            buttonPaint.strokeWidth = if (selected) 4f else 2f
            buttonPaint.color =
                if (selected) 0xFFFFFFFF.toInt()
                else 0xFF757575.toInt()

            canvas.drawRoundRect(rect, 10f, 10f, buttonPaint)

            val baseline =
                rect.centerY() -
                    (buttonTextPaint.ascent() + buttonTextPaint.descent()) / 2f

            canvas.drawText(
                mode.label,
                rect.centerX(),
                baseline,
                buttonTextPaint
            )
        }
    }

    companion object {
        private const val MIN_FIRE_DISTANCE = 30f
        private const val MIN_CHARGE_PULL = 30f
        private const val MIN_LAUNCH_SPEED = 450f
        private const val MAX_LAUNCH_SPEED = 1900f

        private const val HEAVY_CHARGE_TIME_MS = 2000L
        private const val NORMAL_CHARGE_RECHARGE_SECONDS = 0.85f
        private const val AUTO_CHARGE_DRAIN_SECONDS = 1.5f
        private const val AUTO_CHARGE_RECHARGE_SECONDS = 3.5f

        private const val MIN_GUIDE_LENGTH = 90f
        private const val MAX_GUIDE_LENGTH = 680f
        private const val MIN_DOT_SPACING = 30f
        private const val MAX_DOT_SPACING = 54f

        private const val SLIDE_SENSITIVITY = 1f

        private const val MAX_TRAVEL_PER_SUBSTEP = 9f
        private const val MAX_PHYSICS_SUBSTEPS = 12
        private const val COLLISION_RESTITUTION = 1f

        private const val AUTO_MIN_TENSION = 0.18f
        private const val AUTO_SLOW_INTERVAL_MS = 620L
        private const val AUTO_FAST_INTERVAL_MS = 95L

        private const val PLAYER_STARTING_MAX_HEALTH = 5
        private const val DIFFICULTY_RAMP_SECONDS = 180f
        private const val POWERUP_RADIUS = 31f
        private const val POWERUP_MARGIN = 55f
        private const val POWERUP_MIN_INTERVAL = 15f
        private const val POWERUP_MAX_INTERVAL = 25f
        private const val BOMB_SPAWN_CHANCE = 0.055f
        private const val BOMB_WEIGHT = 0.16f
        private const val SHIELD_LOW_HULL_WEIGHT = 1.55f
        private const val BASE_POWERUP_WEIGHT = 1.0f
        private const val FUEL_HIGH_WEIGHT = 0.55f
        private const val FUEL_MID_WEIGHT = 1.25f
        private const val FUEL_LOW_WEIGHT = 2.20f
        private const val FUEL_PICKUP_AMOUNT = 0.34f
        private const val FULL_THRUST_FUEL_SECONDS = 7.5f
        private const val THRUSTER_ACCELERATION = 790f
        private const val THRUSTER_GRAVITY = 560f
        private const val THRUSTER_DRAG = 1.65f
        private const val THRUSTER_MAX_UP_SPEED = 660f
        private const val THRUSTER_MAX_DOWN_SPEED = 620f
        private const val RIVAL_POWERUP_WEIGHT = 0.18f
        private const val MAX_ACTIVE_PROJECTILES = 180
        private const val LOADOUT_MAX_ENEMIES = 95
        private const val LOADOUT_MIN_SPAWN_INTERVAL_MS = 280L
        private const val BOSS_BATTLE_START_SECONDS = 75f
        private const val BOSS_REPEAT_INTERVAL_SECONDS = 75f
        private const val BOSS_INTRO_SECONDS = 2.8f
        private const val BATTLE_BOSS_RADIUS = 92f
        private const val BATTLE_BOSS_HIT_POINTS = 1400f
        private const val BOSS_VARIANT_COUNT = 6
        private const val BOSS_HIT_KNOCKBACK = 330f
        private const val BOSS_BEAM_KNOCKBACK = 90f
        private const val BOSS_POWERUP_DRIFT_SPEED = 82f
        private const val BOSS_POWERUP_MIN_INTERVAL = 2.6f
        private const val BOSS_POWERUP_MAX_INTERVAL = 5.0f
        private const val BOSS_MESSAGE_DURATION = 2.4f
        private val BOSS_WEAPON_POWERUPS = setOf(
            ShotType.BOUNCE,
            ShotType.SPREAD,
            ShotType.AUTO,
            ShotType.PIERCING,
            ShotType.DOUBLE,
            ShotType.BEAM
        )
        private const val DOUBLE_SHOT_HALF_SEPARATION = 15f
        private const val EJECT_RECOLLECT_DELAY = 1.35f
        private const val TRIPLE_TAP_WINDOW_MS = 430L
        private const val BOMB_DAMAGE = 10000f
        private const val BOMB_BLAST_DURATION = 0.52f
        private const val BOMB_SHAKE_STRENGTH = 30f
        private const val GAME_OVER_RESET_DELAY_MS = 1800L
        private const val DAMAGE_FLASH_DURATION = 0.22f
        private const val DAMAGE_SHAKE_DURATION = 0.18f
        private const val PLAYER_COLLISION_RADIUS = 34f
        private const val PLAYER_COLLISION_Y_OFFSET = 48f
        private const val PLAYER_ENEMY_KNOCKBACK = 260f
        private const val FIELD_SCROLL_SPEED = 92f
        private const val SOLID_WALL_MIN_INTERVAL = 3.8f
        private const val SOLID_WALL_MAX_INTERVAL = 6.2f
        private const val DESTRUCTIBLE_MIN_INTERVAL = 1.45f
        private const val DESTRUCTIBLE_MAX_INTERVAL = 2.35f
        private const val ENEMY_RADIUS = 43.75f // +25% from 28f
        private const val GESTURE_CLASSIFY_DISTANCE = 28f
        private const val THRUSTER_LONG_PRESS_MS = 250L
        private const val LONG_PRESS_MOVE_TOLERANCE = 18f
        private const val FIRE_DIRECTION_BIAS = 0.72f
        private const val MOVE_DIRECTION_BIAS = 1.10f
        private const val HEAVY_OBJECT_DAMAGE_SPEED = 150f
        private const val BEAM_STEEL_DAMAGE_PER_SECOND = 5f
        private const val BOSS_RADIUS = 38f
        private const val INITIAL_ENEMY_COUNT = 12
        private const val MAX_ENEMY_COUNT = 28
        private const val ENEMY_SPAWN_INTERVAL_MS = 2100L

        private const val MIN_BEAM_WIDTH = 10f
        private const val MAX_BEAM_WIDTH = 360f
        private const val HEAVY_BEAM_WIDTH_MULTIPLIER = 1.35f
        private const val BEAM_LIFETIME = 1.0f
        private const val BEAM_DAMAGE_TICK_SECONDS = 0.5f
        private const val BEAM_DAMAGE_PER_TICK = 8f
        private const val MAX_BEAM_DAMAGE_MULTIPLIER = 6f

        private const val MODE_BUTTON_HEIGHT = 72f
        private const val MODE_BUTTON_BOTTOM_MARGIN = 18f
    }
}
