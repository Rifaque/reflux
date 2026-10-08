package dev.reflux.core.source

/** What a file found during a scan is to Reflux. */
enum class FileRole { VIDEO, SUBTITLE, IMAGE }

/**
 * Deterministic rules deciding which directories to walk and which files matter.
 *
 * Shared by all file-enumerating sources so local folders and network shares behave identically.
 */
object ScanRules {
    val videoExtensions = setOf(
        "mkv", "mk3d", "mp4", "m4v", "avi", "divx", "mov", "wmv", "asf", "ts", "m2ts", "mts",
        "mpg", "mpeg", "vob", "webm", "flv", "ogv", "ogm", "3gp",
    )
    val subtitleExtensions = setOf("srt", "ass", "ssa", "vtt", "sup", "sub", "idx", "ttml", "dfxp")
    val imageExtensions = setOf("jpg", "jpeg", "png", "webp")

    private val ignoredDirectories = setOf(
        "\$recycle.bin", "system volume information", "lost+found", "@eadir", "#recycle", "#snapshot",
        ".appledouble", ".trash", ".trashes", "@recycle", "recycler", ".snapshots",
    )

    /** Whether a directory should be descended into. Hidden and system directories are skipped. */
    fun shouldEnter(directoryName: String): Boolean {
        if (directoryName.startsWith('.')) return false
        val lower = directoryName.lowercase()
        if (lower in ignoredDirectories) return false
        return true
    }

    /** The role of a file, or null if Reflux does not care about it. */
    fun roleOf(fileName: String): FileRole? {
        if (fileName.startsWith('.')) return null
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            in videoExtensions -> FileRole.VIDEO
            in subtitleExtensions -> FileRole.SUBTITLE
            in imageExtensions -> FileRole.IMAGE
            else -> null
        }
    }
}
