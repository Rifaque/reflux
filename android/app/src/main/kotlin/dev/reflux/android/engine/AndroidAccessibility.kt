package dev.reflux.android.engine

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.reflux.ui.state.AccessibilityPreferences

/**
 * The system's accessibility settings as observable state. Lives as long as the process (AndroidEnvironment), so
 * what it registers is never unregistered. Text size is not here: Android applies it to every text by itself.
 */
class AndroidAccessibility(context: Context) {
    private val application = context.applicationContext
    private val manager = application.getSystemService(AccessibilityManager::class.java)

    var preferences by mutableStateOf(read())
        private set

    init {
        val refresh = { preferences = read() }
        manager.addAccessibilityStateChangeListener { refresh() }
        // Explore by touch is what TalkBack turns on, and it changes without the manager's own state changing
        // when another service was already running.
        manager.addTouchExplorationStateChangeListener { refresh() }
        if (Build.VERSION.SDK_INT >= 33) manager.addAccessibilityServicesStateChangeListener { refresh() }
        application.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) = refresh()
            },
        )
    }

    private fun read() = AccessibilityPreferences(
        // Spoken feedback, not touch exploration alone: a television's TalkBack has no touch to explore with.
        screenReader = manager.isEnabled && (manager.isTouchExplorationEnabled || manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_SPOKEN).isNotEmpty()),
        // "Remove animations" sets this to 0. Android has no other reduce-motion setting.
        reduceMotion = Settings.Global.getFloat(application.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f,
    )
}
