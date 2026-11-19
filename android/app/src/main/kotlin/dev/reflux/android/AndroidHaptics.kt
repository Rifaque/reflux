package dev.reflux.android

import android.app.Activity
import android.content.Context
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import dev.reflux.ui.state.ControllerRumble
import dev.reflux.ui.state.DeviceHaptics
import dev.reflux.ui.state.HapticEvent

/**
 * The device's own haptics for interface feedback (F-001), reached only through `Haptics`, which keeps them off until
 * the viewer turns them on. Each event is the platform's own effect on the window, so the system's touch-feedback
 * setting is followed as well. A device without a vibrator does nothing.
 */
internal class AndroidDeviceHaptics(private val activity: Activity) : DeviceHaptics {
    override val available: Boolean
        get() = vibrator(activity)?.hasVibrator() == true

    override fun perform(event: HapticEvent) {
        val constant = when (event) {
            HapticEvent.CONFIRM -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
            HapticEvent.ERROR -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
            HapticEvent.TOGGLE, HapticEvent.BOUNDARY, HapticEvent.NAVIGATE -> HapticFeedbackConstants.CLOCK_TICK
        }
        activity.window?.decorView?.performHapticFeedback(constant)
    }

    private companion object {
        fun vibrator(context: Context): Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}

/**
 * The motors of the controller in use (F-002), reached only through `Haptics`. The controller is the last one whose
 * buttons or sticks were used; one without motors is not offered rumble, and one that is disconnected stops at once
 * and is forgotten.
 */
internal class AndroidControllerRumble(context: Context) : ControllerRumble, InputManager.InputDeviceListener {
    private val input = context.getSystemService(InputManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var current: Int? = null

    init {
        input?.registerInputDeviceListener(this, main)
    }

    /** The controller [deviceId] was just used. */
    fun used(deviceId: Int) {
        if (deviceId != current) stop()
        current = deviceId
    }

    override fun available(): Boolean = motors() != null

    override fun rumble(strength: Float, durationMs: Long) {
        val vibrator = motors() ?: return
        val amplitude = (strength.coerceIn(0f, 1f) * 255).toInt().coerceIn(1, 255)
        val effect = VibrationEffect.createOneShot(durationMs, if (vibrator.hasAmplitudeControl()) amplitude else VibrationEffect.DEFAULT_AMPLITUDE)
        runCatching { vibrator.vibrate(effect) }
    }

    override fun stop() {
        runCatching { motors()?.cancel() }
    }

    private fun motors(): Vibrator? {
        val id = current ?: return null
        val device = InputDevice.getDevice(id) ?: return null
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            device.vibratorManager.takeIf { it.vibratorIds.isNotEmpty() }?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            device.vibrator
        }
        return vibrator?.takeIf { it.hasVibrator() }
    }

    override fun onInputDeviceAdded(deviceId: Int) = Unit

    override fun onInputDeviceChanged(deviceId: Int) = Unit

    // A controller that goes away stops at once; what it was doing is not resumed on another.
    override fun onInputDeviceRemoved(deviceId: Int) {
        if (deviceId == current) {
            current = null
        }
    }

    fun close() {
        input?.unregisterInputDeviceListener(this)
    }
}
