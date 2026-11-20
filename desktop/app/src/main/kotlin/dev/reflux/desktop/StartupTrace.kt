package dev.reflux.desktop

import java.lang.management.ManagementFactory

/**
 * Where a desktop start's time goes (B-022, B-041): checkpoints from the JVM's own start, printed once the first frame is
 * drawn, only when `REFLUX_STARTUP_TRACE=1` (or `-Dreflux.startupTrace=true`) asks for them, and appended to the file
 * `REFLUX_STARTUP_TRACE_FILE` names, if any (a packaged app's launcher does not pass its child's output on). It changes
 * nothing else, and costs a clock read per checkpoint.
 */
internal object StartupTrace {
    private val file: String? = System.getenv("REFLUX_STARTUP_TRACE_FILE")
    private val enabled = System.getenv("REFLUX_STARTUP_TRACE") == "1" || System.getProperty("reflux.startupTrace") == "true" || file != null
    private val jvmStart = runCatching { ManagementFactory.getRuntimeMXBean().startTime }.getOrDefault(System.currentTimeMillis())
    private val marks = mutableListOf<Pair<String, Long>>()
    private var printed = false

    fun mark(name: String) {
        if (!enabled) return
        synchronized(marks) { marks += name to System.currentTimeMillis() }
    }

    /** The first frame: the checkpoints are printed, each as milliseconds since the JVM started. */
    fun firstFrame() {
        if (!enabled || printed) return
        printed = true
        mark("first frame")
        val line = synchronized(marks) { marks.joinToString(" ") { (name, at) -> "${name.replace(' ', '_')}=${at - jvmStart}" } }
        System.err.println("reflux-startup $line")
        file?.let { runCatching { java.io.File(it).appendText("reflux-startup $line" + System.lineSeparator()) } }
    }
}
