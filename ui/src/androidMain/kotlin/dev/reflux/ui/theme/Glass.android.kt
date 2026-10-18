package dev.reflux.ui.theme

import android.os.Build

// Render effects, blur among them, arrived in Android 12; before it they are silently ignored.
internal actual val platformBlursBackdrop: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
