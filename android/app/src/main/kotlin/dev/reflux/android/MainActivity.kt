package dev.reflux.android

import android.annotation.SuppressLint
import android.app.Application
import android.Manifest
import android.app.PictureInPictureParams
import android.app.PictureInPictureUiState
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.Lifecycle
import android.app.UiModeManager
import android.content.Intent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.ui.platform.LocalLayoutDirection
import dev.reflux.ui.SystemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.text.Cue
import dev.reflux.android.engine.AndroidEnvironment
import dev.reflux.android.launcher.WatchNextLink
import dev.reflux.android.launcher.WatchNextPublisher
import dev.reflux.android.playback.Media3Player
import dev.reflux.core.input.InputMapping
import dev.reflux.core.input.SemanticAction
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.ui.RefluxApp
import dev.reflux.ui.player.MiniPlayerSurface
import dev.reflux.ui.player.PlayerOverlay
import dev.reflux.ui.player.SystemWindowOverlay
import dev.reflux.ui.player.VideoStage
import dev.reflux.ui.player.rememberVideoPlacement
import dev.reflux.ui.state.BackRoute
import dev.reflux.ui.state.PlayerPresentation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import dev.reflux.ui.state.AppState
import dev.reflux.ui.theme.RefluxColors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

/** Holds the engine for the life of the process, so that it outlives any one screen. */
class RefluxApplication : Application() {
    val environment: AndroidEnvironment by lazy { AndroidEnvironment(this) }

    /** For what must not stop when a screen goes away: a copy being made for offline is one. */
    val scope = CoroutineScope(SupervisorJob() + CoroutineExceptionHandler { _, error -> environment.report("An unexpected error was not handled", error) })

    /** The launcher's Watch Next row, where the device has one. */
    val watchNext: WatchNextPublisher by lazy { WatchNextPublisher(this, environment.library, environment.artwork, environment::report) }
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

    /**
     * What the app's work runs in. Not the activity's lifecycle scope: that is cancelled before [onDestroy] runs, and what the
     * playback has still to do when it ends (record its place, let go of the engine and the stream) would be cancelled with
     * it. It ends when the playback has been let go (see [onDestroy]).
     */
    private lateinit var work: CoroutineScope

    /** Where the picture is in the window: the system's window animates out of and into it. */
    private var pictureBounds by mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)

    private var pendingFolder: CompletableDeferred<Uri?>? = null
    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        pendingFolder?.complete(tree)
        pendingFolder = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The platform's launch screen hands over to the first frame as soon as the app is ready: nothing holds it.
        installSplashScreen()
        enableEdgeToEdge(SystemBarStyle.dark(TRANSPARENT), SystemBarStyle.dark(TRANSPARENT))
        super.onCreate(savedInstanceState)
        val environment = (application as RefluxApplication).environment
        environment.startOffline((application as RefluxApplication).scope)
        platform = AndroidPlatform(environment, television()).also {
            it.pickFolder = ::pickFolder
            it.allowStorage = ::allowStorage
            it.share = ::share
            it.browse = ::browse
        }
        // The last line of defence: a failure nobody handled is written down instead of ending the app.
        work = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, error -> platform.report("An unexpected error was not handled", error) })
        app = AppState(environment.library, environment.pipeline, environment.registry::resolve, platform, work).also { it.start() }
        platform.enterSystem = ::enterPictureInPicture

        // Back is handled where the state says so, by handlers that are enabled only while Reflux has something to do with it
        // (see Shell): nothing is enabled at the root, so the system's own Back runs there, with its animation.
        setContent { Shell() }
        keepWatchNextCurrent()
        // Launched from a Watch Next card: the first time only, not again when the activity is recreated.
        if (savedInstanceState == null) continueFromLauncher(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        continueFromLauncher(intent)
    }

    /**
     * A Watch Next card names a work and the profile whose Continue Watching it was; Reflux opens it in that profile
     * and plays it from where that viewer is (docs/WATCH_STATE.md, "Profiles").
     */
    private fun continueFromLauncher(intent: Intent?) {
        val target = WatchNextLink.target(intent?.dataString) ?: return
        app.continueFromOutside(target.itemId, target.profile)
    }

    /**
     * The launcher's row follows the user's state: whenever it may have changed (a playback stopped, a
     * work was marked, a scan brought news), shortly after, once things are quiet; and when the user leaves.
     */
    @OptIn(FlowPreview::class)
    private fun keepWatchNextCurrent() {
        val watchNext = (application as RefluxApplication).watchNext
        if (!watchNext.supported) return
        lifecycleScope.launch {
            snapshotFlow { app.revision }.debounce(WATCH_NEXT_QUIET_MS).collectLatest { watchNext.publish() }
        }
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
    /** The system's share sheet with [text]: the person chooses where it goes. */
    private fun share(subject: String, text: String): Boolean = runCatching {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, text)
        startActivity(Intent.createChooser(send, "Send the support report"))
        true
    }.getOrDefault(false)

    /** A browser on [url], where the device has one. */
    private fun browse(url: String): Boolean = runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
        true
    }.getOrDefault(false)

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
        val presentation = session?.presentation
        // The session's state is read here so that what Back does (it depends on whether something is playing) is current.
        val status = session?.state?.collectAsState()?.value?.status
        val placement = rememberVideoPlacement(app, travels = true)
        val route = app.backRoute
        val reduceMotion = platform.accessibility.reduceMotion

        Box(Modifier.fillMaxSize().background(RefluxColors.background)) {
            // Edge to edge: Reflux draws under the system's bars and keeps its controls clear of them itself. Only the
            // keyboard, beyond the bars it covers, still makes room.
            val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
            val direction = LocalLayoutDirection.current
            val bars = SystemBars(
                top = insets.calculateTopPadding(),
                bottom = insets.calculateBottomPadding(),
                start = insets.calculateStartPadding(direction),
                end = insets.calculateEndPadding(direction),
            )
            // In the system's own window there is no page to read: the window is the picture.
            val page = Modifier.windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.systemBars))
                .then(if (presentation == PlayerPresentation.SYSTEM) Modifier.clearAndSetSemantics { } else Modifier)
            RefluxApp(app, page, bars = bars)
            // Video is a platform surface beneath the controls: the engine keeps its direct path to the display. One surface
            // for the whole of a playback, put where the experience layer says (the window, the dock, out of sight) by
            // laying it out, never by making another (docs/PLAYBACK.md, "One session, several presentations").
            if (player != null) {
                VideoStage(placement, Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        VideoLayer(player, subtitles = presentation != PlayerPresentation.MINI, radius = placement.pictureRadius, onPicture = { pictureBounds = it })
                        // In the dock, and on the way there and back, the picture is only the way back to the player.
                        if (session != null && presentation != PlayerPresentation.SYSTEM && !(presentation == PlayerPresentation.FULL && placement.full)) {
                            MiniPlayerSurface(app, session, silent = false)
                        }
                    }
                }
            }
            if (session != null && presentation == PlayerPresentation.FULL && placement.full) {
                PlayerOverlay(app, session, Modifier.windowInsetsPadding(WindowInsets.safeDrawing))
            }
            // The system's window shows the picture, and what the viewer must not miss: its controls are the system's own.
            if (session != null && presentation == PlayerPresentation.SYSTEM) SystemWindowOverlay(app, session)
        }

        // Back, from the state alone. At most one of these is enabled, and none at the root: a callback that is always
        // enabled takes Back from the system, back-to-home animation and all. Android 16 sends Back only here.
        PredictiveBackHandler(enabled = route == BackRoute.COLLAPSE && !reduceMotion) { progress ->
            try {
                progress.collect { event -> app.backPreview = event.progress }
                app.backPreview = null
                app.dispatch(SemanticAction.BACK)
            } catch (cancelled: CancellationException) {
                // The gesture was taken back: nothing but the preview moved, so letting it go is all there is to restore.
                app.backPreview = null
                throw cancelled
            }
        }
        BackHandler(enabled = route == BackRoute.APP || route == BackRoute.SYSTEM_PRESENTATION || (route == BackRoute.COLLAPSE && reduceMotion)) {
            if (!app.dispatch(SemanticAction.BACK)) moveTaskToBack(true)
        }
        // Before Android 12 there is no system animation to keep, and the root activity would otherwise be finished.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) BackHandler(enabled = route == BackRoute.SYSTEM) { moveTaskToBack(true) }

        // The system's next and previous controls are the same semantic actions as everyone else's.
        DisposableEffect(player) {
            player?.onSkip = { forward -> runOnUiThread { app.dispatch(if (forward) SemanticAction.NEXT else SemanticAction.PREVIOUS) } }
            onDispose { player?.onSkip = null }
        }
        // Leaving the app while something plays keeps it on screen in a small window, shaped like the picture.
        val aspect = player?.videoAspect?.collectAsState()?.value
        val eligible = PictureInPicture.eligible(presentation, status, session?.error != null, session?.upNext != null)
        val bounds = pictureBounds
        LaunchedEffect(eligible, aspect, bounds) { continuesInWindow(eligible, aspect, bounds) }

        // While the full player has the screen the system bars are away; browsing with a mini-player has them, and the screen
        // stays on while something plays, in whichever window.
        val watching = presentation == PlayerPresentation.FULL
        val playing = status.let { it == PlayerStatus.PLAYING || it == PlayerStatus.BUFFERING || it == PlayerStatus.LOADING }
        DisposableEffect(watching) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (watching) {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }
            onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
        }
        DisposableEffect(playing && session != null) {
            if (playing && session != null) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
                // A held key repeats as presses with a count: a held seek is one seek (docs/INPUT_MODEL.md).
                else -> Input.key(event)?.let { app.onKey(it, repeat = event.repeatCount > 0) } ?: false
            }
            if (handled) return true
            // A controller's cancel button where Reflux has no use for Back is the system's Back, as a remote's is: the
            // system's own handling of it is suppressed below, so it is given on here.
            if (button != null && button == InputMapping.cancelButton(Input.family(event.device)) && app.backRoute == BackRoute.SYSTEM) {
                if (event.repeatCount == 0) onBackPressedDispatcher.onBackPressed()
                return true
            }
        } else if (event.action == KeyEvent.ACTION_UP && button == null) {
            Input.key(event)?.let(app::onKeyReleased)
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
    private var windowSource: android.graphics.Rect? = null

    /** Tells the system whether leaving the app should shrink it into a floating window, of what shape, and out of what part of the screen. */
    private fun continuesInWindow(enabled: Boolean, aspect: Float?, picture: androidx.compose.ui.geometry.Rect?) {
        if (!leavesIntoWindow(platform.television, packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE))) return
        windowOnLeave = enabled
        windowShape = PictureInPicture.shape(aspect)?.let { (width, height) -> Rational(width, height) }
        // The picture, not the letterbox around it: the window grows out of what the viewer sees.
        windowSource = picture?.takeIf { it.width > 0f && it.height > 0f }?.let {
            android.graphics.Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt())
        }
        runCatching { setPictureInPictureParams(pictureInPictureParams()) }
    }

    private fun pictureInPictureParams(): PictureInPictureParams = PictureInPictureParams.Builder().apply {
        windowShape?.let(::setAspectRatio)
        windowSource?.let(::setSourceRectHint)
        if (Build.VERSION.SDK_INT >= 31) {
            setAutoEnterEnabled(windowOnLeave)
            setSeamlessResizeEnabled(true)
        }
    }.build()

    /** The experience layer asked for the system's window (Back at Home with something playing); the window is reported by [onPictureInPictureModeChanged]. */
    private fun enterPictureInPicture(): Boolean =
        leavesIntoWindow(platform.television, packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) &&
            runCatching { enterPictureInPictureMode(pictureInPictureParams()) }.getOrDefault(false)

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Newer systems enter by themselves (the gesture animates into the window); older ones are asked here.
        if (windowOnLeave && Build.VERSION.SDK_INT < 31) runCatching { enterPictureInPictureMode(pictureInPictureParams()) }
    }

    /** Android 15 and later say the window is on its way: the controls go before the animation starts, so it shows only the picture. */
    @RequiresApi(31)
    override fun onPictureInPictureUiStateChanged(pipState: PictureInPictureUiState) {
        super.onPictureInPictureUiStateChanged(pipState)
        if (Build.VERSION.SDK_INT >= 35) app.systemTransition = pipState.isTransitioningToPip
        Log.i(TAG, "pip ui state: transitioning=${if (Build.VERSION.SDK_INT >= 35) pipState.isTransitioningToPip else null} stashed=${pipState.isStashed}")
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        app.systemTransition = false
        Log.i(TAG, "pip mode changed: in=$isInPictureInPictureMode lifecycle=${lifecycle.currentState}")
        if (isInPictureInPictureMode) {
            app.playback?.enterSystem()
            return
        }
        // Brought back, or closed: a closed window stops the activity, and an expanded one keeps it going.
        val expanded = PictureInPicture.expanded(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        app.playback?.leaveSystem(expanded)
    }

    override fun onStart() {
        super.onStart()
        platform.player?.wake()
    }

    override fun onStop() {
        super.onStop()
        // Nothing keeps playing unseen: an activity in a floating window is not stopped, one that was
        // closed, covered, or left with the screen off is. Unseen, it holds no decoder either.
        platform.player?.let {
            it.pause()
            it.standBy()
        }
        // Leaving Reflux is when the launcher should know where the user is; the work playing records its place first.
        val application = application as RefluxApplication
        if (application.watchNext.supported) application.scope.launch {
            // Pausing reports the position; it is written before the row is.
            delay(WATCH_NEXT_QUIET_MS)
            app.playback?.recorded()
            application.watchNext.publish()
        }
    }

    override fun onDestroy() {
        // However the activity ends (finished, recreated for a change it does not handle, taken by the system), what plays is
        // ended with it: its place is recorded and its engine, surface, and audio focus are let go. A new activity starts with
        // nothing playing; a playback left running with no activity to show it would be a second one beside the next.
        val release = app.playback?.close()
        if (release == null) work.cancel() else release.invokeOnCompletion { work.cancel() }
        super.onDestroy()
    }
}

private const val TAG = "RefluxPip"

/**
 * Whether leaving the app while something plays shrinks it into a floating window: on a handheld whose system
 * offers one. A television's remote cannot reach such a window, and Fire OS pauses the playing session as Home
 * is pressed, which left a frozen picture over its home screen; there, leaving pauses and the place is kept.
 */
internal fun leavesIntoWindow(television: Boolean, systemOffersIt: Boolean): Boolean = systemOffersIt && !television

/** How long the user's state is quiet before the launcher's row is brought up to date (the platform asks for under 5 s). */
private const val WATCH_NEXT_QUIET_MS = 2_000L

/** The video, letterboxed to its own shape, with subtitles drawn over it. */
@Composable
private fun VideoLayer(player: Media3Player, subtitles: Boolean, radius: androidx.compose.ui.unit.Dp, onPicture: (androidx.compose.ui.geometry.Rect) -> Unit) {
    val aspect by player.videoAspect.collectAsState()
    val size by player.videoSize.collectAsState()
    val cues by player.cues.collectAsState()
    val clearance by player.subtitleClearance.collectAsState()
    var view by remember { mutableStateOf<SurfaceView?>(null) }
    // A buffer of the picture's own size, so that putting the surface in the dock and back is the compositor scaling it, not a
    // buffer made again (VERIFICATION.md, M10 probe P1: no jank counted under it, 95% of frames legacy-janky without).
    LaunchedEffect(view, size) { size?.let { (width, height) -> view?.holder?.setFixedSize(width, height) } }
    val radiusPx = with(androidx.compose.ui.platform.LocalDensity.current) { radius.toPx() }
    BoxWithConstraints(Modifier.fillMaxSize().clip(androidx.compose.foundation.shape.RoundedCornerShape(radius)).background(Color.Black), contentAlignment = Alignment.Center) {
        val container = maxWidth / maxHeight
        val containerPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth.toPx() to maxHeight.toPx() }
        val shape = aspect ?: container
        val textSize = (minOf(maxWidth, maxHeight).value / 19f).coerceIn(14f, 30f)
        // While the controls show, text stays above them: their share of the window, less the band below the picture.
        val picture = if (shape < container) maxHeight else maxWidth / shape
        val lift = maxHeight * clearance - (maxHeight - picture) / 2
        Box(Modifier.aspectRatio(shape, matchHeightConstraintsFirst = shape < container).onGloballyPositioned { onPicture(it.boundsInWindow()) }) {
            AndroidView(
                factory = { context -> SurfaceView(context).also { surface -> view = surface; player.attach(surface) } },
                modifier = Modifier.fillMaxSize(),
                // The picture says nothing to a screen reader, and the view holding it must not count as something over the
                // dock's picture box, which is the stop that speaks and acts: a node covered by another is left out of what
                // can be reached (PlayerAccessibilityOnDeviceTest).
                update = { surface ->
                    surface.roundTo(containerPx.first, containerPx.second, radiusPx)
                    surface.importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    (surface.parent as? android.view.View)?.importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                },
                onRelease = {
                    view = null
                    player.attach(null)
                },
            )
            // The dock's picture is too small to read lines on; they are on in the full player and in the system's window.
            if (subtitles) Subtitles(cues, textSize, lift)
        }
    }
}

/**
 * Rounds what a surface puts on screen. A compositor layer ignores the toolkit's clip, so the dock's rounded corners are
 * given to the surface itself as its outline: the corners of the box it sits in (which may be larger than the picture, a
 * film of another shape being letterboxed in it), meeting the picture's own edges. Without a radius there is no outline.
 */
private fun SurfaceView.roundTo(containerWidth: Float, containerHeight: Float, radius: Float) {
    if (radius <= 0.5f) {
        if (clipToOutline) { clipToOutline = false; outlineProvider = android.view.ViewOutlineProvider.BACKGROUND }
        return
    }
    outlineProvider = object : android.view.ViewOutlineProvider() {
        override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
            val dx = (containerWidth - view.width) / 2f
            val dy = (containerHeight - view.height) / 2f
            val picture = android.graphics.Path().apply { addRect(0f, 0f, view.width.toFloat(), view.height.toFloat(), android.graphics.Path.Direction.CW) }
            val box = android.graphics.Path().apply { addRoundRect(-dx, -dy, view.width + dx, view.height + dy, radius, radius, android.graphics.Path.Direction.CW) }
            // The meeting of two convex shapes is convex, which every Android version this runs on can clip to.
            if (picture.op(box, android.graphics.Path.Op.INTERSECT)) outline.setConvexPath(picture) else outline.setRect(0, 0, view.width, view.height)
        }
    }
    clipToOutline = true
    invalidateOutline()
}

/** Where an image subtitle goes on a picture of [Placement]'s box, in the box's units. */
internal data class Placement(val x: Float, val y: Float, val width: Float, val height: Float)

/**
 * Image subtitles are placed on the canvas they were authored for, which is not always the picture: a 1920x818 film
 * carries PGS for 1920x1080, and placed by the picture their last line falls off it. The fractions a cue gives say
 * how tall that canvas is, and it is centred on the picture, as the film was on it.
 *
 * @param bitmapHeight the image's height as a share of its canvas; unset, the canvas is taken to be the picture.
 */
internal fun imagePlacement(left: Float, top: Float, size: Float, bitmapHeight: Float, imageWidth: Int, imageHeight: Int, boxWidth: Float, boxHeight: Float): Placement {
    val width = boxWidth * size.coerceIn(0f, 1f)
    val height = width * imageHeight / imageWidth.coerceAtLeast(1)
    val canvas = if (bitmapHeight == Cue.DIMEN_UNSET || bitmapHeight <= 0f) boxHeight else height / bitmapHeight
    return Placement(boxWidth * left, (boxHeight - canvas) / 2 + canvas * top, width, height)
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
            val place = imagePlacement(left, top, width, cue.bitmapHeight, image.width, image.height, maxWidth.value, maxHeight.value)
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                // Not limited to the box: a cropped film's subtitles sit on a taller canvas than its picture.
                modifier = Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)
                    .offset(place.x.dp, place.y.dp).requiredSize(place.width.dp, place.height.dp),
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
