package dev.reflux.android

import android.annotation.SuppressLint
import android.app.Application
import android.Manifest
import android.app.PictureInPictureParams
import android.app.UiModeManager
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color.TRANSPARENT
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.text.Cue
import dev.reflux.android.engine.AndroidEnvironment
import dev.reflux.android.playback.Media3Player
import dev.reflux.core.input.SemanticAction
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.ui.RefluxApp
import dev.reflux.ui.player.PlayerOverlay
import dev.reflux.ui.state.AppState
import dev.reflux.ui.theme.RefluxColors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.plus

/** Holds the engine for the life of the process, so that it outlives any one screen. */
class RefluxApplication : Application() {
    val environment: AndroidEnvironment by lazy { AndroidEnvironment(this) }

    /** For what must not stop when a screen goes away: a copy being made for offline is one. */
    val scope = CoroutineScope(SupervisorJob() + CoroutineExceptionHandler { _, error -> environment.report("An unexpected error was not handled", error) })
}

/**
 * The Android shell: one activity that hosts the shared experience layer over a video surface, hands it
 * normalized input, and carries out what only the platform can (the folder picker, system bars,
 * orientation, keeping the screen on).
 */
class MainActivity : ComponentActivity() {
    private lateinit var platform: AndroidPlatform
    private lateinit var app: AppState
    private val sticks = StickDirections()

    /** Whether the activity is the small floating window: then it shows the picture and nothing else. */
    private var inPictureInPicture by mutableStateOf(false)

    private var pendingFolder: CompletableDeferred<Uri?>? = null
    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        pendingFolder?.complete(tree)
        pendingFolder = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(TRANSPARENT), SystemBarStyle.dark(TRANSPARENT))
        super.onCreate(savedInstanceState)
        val environment = (application as RefluxApplication).environment
        environment.startOffline((application as RefluxApplication).scope)
        platform = AndroidPlatform(environment, television()).also {
            it.pickFolder = ::pickFolder
            it.allowStorage = ::allowStorage
        }
        // The last line of defence: a failure nobody handled is written down instead of ending the app.
        val scope = lifecycleScope + CoroutineExceptionHandler { _, error -> platform.report("An unexpected error was not handled", error) }
        app = AppState(environment.library, environment.pipeline, environment.registry::resolve, platform, scope).also { it.start() }

        // Back is a semantic action like any other; with nowhere left to go back to, it leaves the app.
        onBackPressedDispatcher.addCallback(this) {
            if (!app.dispatch(SemanticAction.BACK)) moveTaskToBack(true)
        }
        setContent { Shell() }
    }

    /** Whether this device is a television: what the system says it is, not what size its screen happens to be. */
    private fun television(): Boolean =
        getSystemService(UiModeManager::class.java)?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    private var pendingStorage: CompletableDeferred<Boolean>? = null
    private val storagePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingStorage?.complete(granted)
        pendingStorage = null
    }

    /** The system's own question, asked when the user first goes to choose a folder on a television. */
    private suspend fun allowStorage(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) return true
        pendingStorage?.complete(false)
        val result = CompletableDeferred<Boolean>().also { pendingStorage = it }
        storagePermission.launch(permission)
        return result.await()
    }

    private suspend fun pickFolder(): Uri? {
        pendingFolder?.complete(null)
        val result = CompletableDeferred<Uri?>().also { pendingFolder = it }
        folderPicker.launch(null)
        return result.await()
    }

    @Composable
    private fun Shell() {
        val session = app.playback
        val player = platform.player
        Box(Modifier.fillMaxSize().background(RefluxColors.background)) {
            RefluxApp(app, Modifier.windowInsetsPadding(WindowInsets.safeDrawing))
            // Video is a platform surface beneath the controls: the engine keeps its direct path to the display.
            if (player != null) VideoLayer(player)
            // A picture-in-picture window is the picture; its controls are the system's own.
            if (session != null && !inPictureInPicture) PlayerOverlay(app, session, Modifier.windowInsetsPadding(WindowInsets.safeDrawing))
        }

        // The system's next and previous controls are the same semantic actions as everyone else's.
        DisposableEffect(player) {
            player?.onSkip = { forward -> runOnUiThread { app.dispatch(if (forward) SemanticAction.NEXT else SemanticAction.PREVIOUS) } }
            onDispose { player?.onSkip = null }
        }
        // Leaving the app while something plays keeps it on screen in a small window, shaped like the picture.
        val aspect = player?.videoAspect?.collectAsState()?.value
        val leavesToWindow = session != null && session.error == null && session.state.collectAsState().value.status.let { it == PlayerStatus.PLAYING || it == PlayerStatus.BUFFERING }
        LaunchedEffect(leavesToWindow, aspect) { continuesInWindow(leavesToWindow, aspect) }

        // While the player is up the system bars are away and the screen stays on while something plays.
        val playing = session?.state?.collectAsState()?.value?.status.let { it == PlayerStatus.PLAYING || it == PlayerStatus.BUFFERING || it == PlayerStatus.LOADING }
        DisposableEffect(session != null) {
            val bars = WindowCompat.getInsetsController(window, window.decorView)
            if (session != null) {
                bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                bars.hide(WindowInsetsCompat.Type.systemBars())
            }
            onDispose { bars.show(WindowInsetsCompat.Type.systemBars()) }
        }
        DisposableEffect(playing) {
            if (playing) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
        // Fullscreen on a handheld is the picture across the long side of the screen. A television has one side.
        if (!platform.television) LaunchedEffect(platform.fullscreen) {
            requestedOrientation = if (platform.fullscreen) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    @SuppressLint("RestrictedApi") // the toolkit marks this override as its own; it is how an activity sees every key
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val button = Input.gamepadButton(event)
        if (event.action == KeyEvent.ACTION_DOWN) {
            val handled = when {
                button != null -> app.onGamepadButton(button, Input.family(event.device))
                else -> Input.key(event)?.let(app::onKey) ?: false
            }
            if (handled) return true
        }
        // A controller's buttons are Reflux's; the system's fallbacks for them (B as Back) would act twice.
        if (button != null && (event.isFromSource(InputDevice.SOURCE_GAMEPAD) || event.keyCode in KeyEvent.KEYCODE_BUTTON_A..KeyEvent.KEYCODE_BUTTON_MODE)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(InputDevice.SOURCE_JOYSTICK) && event.action == MotionEvent.ACTION_MOVE) {
            val presses = sticks.presses(event)
            presses.forEach { app.onGamepadButton(it, Input.family(event.device)) }
            if (presses.isNotEmpty()) return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    private var windowOnLeave = false
    private var windowShape: Rational? = null

    /** Tells the system whether leaving the app should shrink it into a floating window, and of what shape. */
    private fun continuesInWindow(enabled: Boolean, aspect: Float?) {
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        windowOnLeave = enabled
        // The system accepts shapes between about 1:2.39 and 2.39:1.
        windowShape = aspect?.takeIf { it.isFinite() && it > 0f }?.coerceIn(0.42f, 2.39f)?.let { Rational((it * 1000).toInt(), 1000) }
        runCatching { setPictureInPictureParams(pictureInPictureParams()) }
    }

    private fun pictureInPictureParams(): PictureInPictureParams = PictureInPictureParams.Builder().apply {
        windowShape?.let(::setAspectRatio)
        if (Build.VERSION.SDK_INT >= 31) {
            setAutoEnterEnabled(windowOnLeave)
            setSeamlessResizeEnabled(true)
        }
    }.build()

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Newer systems enter by themselves (the gesture animates into the window); older ones are asked here.
        if (windowOnLeave && Build.VERSION.SDK_INT < 31) runCatching { enterPictureInPictureMode(pictureInPictureParams()) }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPictureInPicture = isInPictureInPictureMode
    }

    override fun onStop() {
        super.onStop()
        // Nothing keeps playing unseen: an activity in a floating window is not stopped, one that was
        // closed, covered, or left with the screen off is.
        platform.player?.pause()
    }

    override fun onDestroy() {
        if (isFinishing) app.playback?.close()
        super.onDestroy()
    }
}

/** The video, letterboxed to its own shape, with subtitles drawn over it. */
@Composable
private fun VideoLayer(player: Media3Player) {
    val aspect by player.videoAspect.collectAsState()
    val cues by player.cues.collectAsState()
    val clearance by player.subtitleClearance.collectAsState()
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        val container = maxWidth / maxHeight
        val shape = aspect ?: container
        val textSize = (minOf(maxWidth, maxHeight).value / 19f).coerceIn(14f, 30f)
        // While the controls show, text stays above them: their share of the window, less the band below the picture.
        val picture = if (shape < container) maxHeight else maxWidth / shape
        val lift = maxHeight * clearance - (maxHeight - picture) / 2
        Box(Modifier.aspectRatio(shape, matchHeightConstraintsFirst = shape < container)) {
            AndroidView(
                factory = { context -> SurfaceView(context).also(player::attach) },
                modifier = Modifier.fillMaxSize(),
                onRelease = { player.attach(null) },
            )
            Subtitles(cues, textSize, lift)
        }
    }
}

/** Subtitle text along the bottom of the picture, and image subtitles where their stream places them. */
@Composable
private fun Subtitles(cues: List<Cue>, textSize: Float, lift: Dp) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        for (cue in cues) {
            val image = cue.bitmap ?: continue
            val left = if (cue.position == Cue.DIMEN_UNSET) 0f else cue.position
            val top = if (cue.line == Cue.DIMEN_UNSET || cue.lineType != Cue.LINE_TYPE_FRACTION) 0f else cue.line
            val width = if (cue.size == Cue.DIMEN_UNSET) 1f else cue.size
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.padding(start = maxWidth * left, top = maxHeight * top).fillMaxWidth(width.coerceIn(0f, 1f - left)),
            )
        }
        val text = cues.mapNotNull { it.text?.toString()?.trim()?.takeIf(String::isNotEmpty) }.joinToString("\n")
        if (text.isNotEmpty()) {
            BasicText(
                text,
                Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp).padding(bottom = maxOf(maxHeight * 0.07f, lift)),
                style = TextStyle(
                    color = Color.White,
                    fontSize = textSize.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    shadow = Shadow(Color.Black, Offset(0f, 1.5f), blurRadius = 6f),
                ),
            )
        }
    }
}
