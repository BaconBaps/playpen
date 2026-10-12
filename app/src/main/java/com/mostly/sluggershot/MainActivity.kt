package com.mostly.sluggershot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    private lateinit var game: GameView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Attach content first. Some physical devices can throw inside the
        // framework Window.insetsController getter if immersive mode is
        // requested before the decor view is fully attached.
        game = GameView(this)
        setContentView(game)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (game.isAtFlightDeck()) finish() else game.handleBack()
            }
        })

        // Defer immersive setup until the decor view has entered the UI queue.
        window.decorView.post {
            enterImmersiveMode()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        // Restore immersive mode after transient system bars, app switching,
        // permission UI, etc. This call is also deferred so fullscreen setup
        // can never race decor-view attachment.
        if (hasFocus) {
            window.decorView.post {
                enterImmersiveMode()
            }
        }
    }

    override fun onPause() {
        if (::game.isInitialized) game.pauseFlight()
        super.onPause()
    }

    private fun enterImmersiveMode() {
        // Use AndroidX compatibility APIs rather than the platform
        // Window.insetsController property that caused the hardware crash.
        runCatching {
            WindowCompat.setDecorFitsSystemWindows(window, false)

            WindowCompat.getInsetsController(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
        // Fullscreen is cosmetic. If a vendor-specific window implementation
        // rejects it, the game remains running instead of crashing.
    }
}
