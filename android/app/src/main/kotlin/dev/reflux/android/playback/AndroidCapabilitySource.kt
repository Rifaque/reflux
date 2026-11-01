package dev.reflux.android.playback

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import dev.reflux.core.playback.DeviceCapabilities

/**
 * Keeps one reading until something it read from can have changed. Decoders are for the life of the process; a
 * receiver or a television is not, so [invalidate] drops the reading, and the next [current] takes another.
 */
class CapabilityReading<T : Any>(private val read: () -> T) {
    @Volatile private var kept: T? = null

    fun current(): T = kept ?: read().also { kept = it }

    fun invalidate() {
        kept = null
    }
}

/** This device's capabilities as they are now, for the life of the application: nothing here is ever released. */
class AndroidCapabilitySource(context: Context) {
    private val application = context.applicationContext
    private val decoders by lazy { AndroidCapabilities.decoders() }
    private val reading = CapabilityReading { AndroidCapabilities.of(decoders, application) }

    init {
        val main = Handler(Looper.getMainLooper())
        application.getSystemService(AudioManager::class.java)?.registerAudioDeviceCallback(
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = reading.invalidate()
                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = reading.invalidate()
            },
            main,
        )
        application.getSystemService(DisplayManager::class.java)?.registerDisplayListener(
            object : DisplayManager.DisplayListener {
                override fun onDisplayAdded(displayId: Int) = reading.invalidate()
                override fun onDisplayRemoved(displayId: Int) = reading.invalidate()
                override fun onDisplayChanged(displayId: Int) = reading.invalidate()
            },
            main,
        )
    }

    fun current(): DeviceCapabilities = reading.current()
}
