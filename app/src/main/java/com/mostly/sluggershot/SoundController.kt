package com.mostly.sluggershot

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import kotlin.random.Random

class SoundController(private val context: Context) {
    private val audioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

    private val soundPool =
        SoundPool.Builder()
            .setMaxStreams(12)
            .setAudioAttributes(audioAttributes)
            .build()

    private val normalShot = soundPool.load(context, R.raw.shot_normal, 1)
    private val spreadShot = soundPool.load(context, R.raw.shot_spread, 1)
    private val piercingShot = soundPool.load(context, R.raw.shot_piercing, 1)
    private val heavyShot = soundPool.load(context, R.raw.shot_heavy, 1)
    private val wallImpact = soundPool.load(context, R.raw.wall_impact, 1)
    private val projectileImpact = soundPool.load(context, R.raw.projectile_impact, 1)
    private val projectileBreak = soundPool.load(context, R.raw.projectile_break, 1)
    private val chargeReady = soundPool.load(context, R.raw.charge_ready, 1)
    private val modeSelect = soundPool.load(context, R.raw.mode_select, 1)

    private var beamPlayer: MediaPlayer? = null
    private var musicPlayer: MediaPlayer? = null
    private var musicVolume = 0f
    private var musicFadeStart = 0f
    private var musicFadeTarget = 0f
    private var musicFadeDuration = 0f
    private var musicFadeElapsed = 0f
    private var releaseMusicAfterFade = false

    fun startLevelMusic(fadeSeconds: Float = 0f) {
        startMusic(R.raw.impact_horizon, fadeSeconds)
    }

    fun fadeOutLevelMusic(seconds: Float = 1f) {
        beginMusicFade(0f, seconds, releaseAfter = true)
    }

    fun startBossMusic(fadeSeconds: Float = 2f) {
        startMusic(R.raw.impact_horizon_boss, fadeSeconds)
    }

    fun updateMusic(dt: Float) {
        if (musicFadeDuration <= 0f) return
        musicFadeElapsed = (musicFadeElapsed + dt).coerceAtMost(musicFadeDuration)
        val t = (musicFadeElapsed / musicFadeDuration).coerceIn(0f, 1f)
        musicVolume = musicFadeStart + (musicFadeTarget - musicFadeStart) * t
        musicPlayer?.setVolume(musicVolume, musicVolume)
        if (musicFadeElapsed >= musicFadeDuration) {
            musicFadeDuration = 0f
            if (releaseMusicAfterFade && musicFadeTarget <= 0f) {
                musicPlayer?.stop()
                musicPlayer?.release()
                musicPlayer = null
                releaseMusicAfterFade = false
            }
        }
    }

    private fun startMusic(resId: Int, fadeSeconds: Float) {
        musicPlayer?.stop()
        musicPlayer?.release()
        musicPlayer = MediaPlayer.create(context, resId)?.apply { isLooping = true }
        musicVolume = if (fadeSeconds > 0f) 0f else MUSIC_VOLUME
        musicPlayer?.setVolume(musicVolume, musicVolume)
        musicPlayer?.start()
        if (fadeSeconds > 0f) beginMusicFade(MUSIC_VOLUME, fadeSeconds, false)
        else musicFadeDuration = 0f
    }

    private fun beginMusicFade(target: Float, seconds: Float, releaseAfter: Boolean) {
        musicFadeStart = musicVolume
        musicFadeTarget = target.coerceIn(0f, 1f)
        musicFadeDuration = seconds.coerceAtLeast(0.001f)
        musicFadeElapsed = 0f
        releaseMusicAfterFade = releaseAfter
    }

    fun playShot(type: ShotType, heavy: Boolean) {
        if (heavy && type != ShotType.BEAM) {
            play(heavyShot, volume = 0.95f, pitchVariation = 0.025f)
            return
        }

        when (type) {
            ShotType.BOUNCE ->
                play(normalShot, volume = 0.84f, pitchVariation = 0.04f)
            ShotType.NONE ->
                play(normalShot, volume = 0.78f, pitchVariation = 0.055f)
            ShotType.SPREAD ->
                play(spreadShot, volume = 0.88f, pitchVariation = 0.035f)
            ShotType.AUTO ->
                play(normalShot, volume = 0.24f, pitchVariation = 0.14f)
            ShotType.PIERCING ->
                play(piercingShot, volume = 0.82f, pitchVariation = 0.025f)
            ShotType.BEAM -> Unit
                    ShotType.DOUBLE -> Unit
            ShotType.BOMB -> Unit
            ShotType.SHIELD -> Unit
            ShotType.FUEL -> Unit
}
    }

    fun playWallImpact() =
        play(wallImpact, volume = 0.38f, pitchVariation = 0.08f)

    fun playProjectileImpact() =
        play(projectileImpact, volume = 0.33f, pitchVariation = 0.09f)

    fun playProjectileBreak() =
        play(projectileBreak, volume = 0.60f, pitchVariation = 0.05f)

    fun playChargeReady() =
        play(chargeReady, volume = 0.52f, pitchVariation = 0f)

    fun playModeSelect() =
        play(modeSelect, volume = 0.30f, pitchVariation = 0.03f)

    fun startOrUpdateBeamLoop(tension: Float, heavy: Boolean) {
        val volume =
            (0.18f + tension.coerceIn(0f, 1f) * 0.46f) *
                if (heavy) 1.15f else 1f

        var player = beamPlayer

        if (player == null) {
            player = MediaPlayer.create(context, R.raw.beam_loop)?.apply {
                isLooping = true
            }
            beamPlayer = player
        }

        player ?: return
        val safeVolume = volume.coerceIn(0f, 1f)
        player.setVolume(safeVolume, safeVolume)

        if (!player.isPlaying) {
            player.start()
        }
    }

    fun stopBeamLoop() {
        beamPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                player.seekTo(0)
            }
        }
    }

    fun pauseAudio() {
        stopBeamLoop()
        musicPlayer?.let { if (it.isPlaying) it.pause() }
        soundPool.autoPause()
    }

    fun resumeAudio() {
        musicPlayer?.start()
        soundPool.autoResume()
    }

    fun release() {
        stopBeamLoop()
        beamPlayer?.release()
        beamPlayer = null
        musicPlayer?.stop()
        musicPlayer?.release()
        musicPlayer = null
        soundPool.release()
    }

    companion object {
        private const val MUSIC_VOLUME = 0.52f
    }

    private fun play(
        soundId: Int,
        volume: Float,
        pitchVariation: Float
    ) {
        val rate =
            if (pitchVariation <= 0f) {
                1f
            } else {
                1f + Random.nextFloat() * pitchVariation * 2f - pitchVariation
            }

        soundPool.play(
            soundId,
            volume.coerceIn(0f, 1f),
            volume.coerceIn(0f, 1f),
            1,
            0,
            rate.coerceIn(0.5f, 2f)
        )
    }
}
