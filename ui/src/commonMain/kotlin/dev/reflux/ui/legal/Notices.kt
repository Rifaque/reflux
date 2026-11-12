package dev.reflux.ui.legal

/** Which build a third-party component is part of: the Android package, or the Windows and Linux one. */
enum class NoticeTarget { ANDROID, DESKTOP }

/**
 * Third-party software in a build, as `legal/components.txt` lists it: its name, its license (an SPDX
 * expression where one says it), the license texts shown for it, and where it is part of the build.
 */
data class ThirdPartyComponent(
    val name: String,
    val license: String,
    val texts: List<String>,
    val targets: Set<NoticeTarget>,
    /** One sentence about how it is part of Reflux, where that needs saying. */
    val note: String?,
)

/**
 * Something Reflux is built on or works with, as `legal/credits.txt` credits it: its name and what it does for
 * Reflux, in words. A credit, not a license notice (those are [ThirdPartyComponent]s), and never a logo.
 */
data class TechnologyCredit(
    val name: String,
    val role: String,
    val targets: Set<NoticeTarget>,
    /** One sentence shown with it: for a service Reflux only connects to, whose it is and that Reflux is independent of it. */
    val note: String?,
)

/** The third-party notices and license texts the About page shows. Generated from `legal/` at build time. */
object Notices {
    /** What [target]'s build is credited as built on and working with, in the order `legal/credits.txt` gives. */
    fun credits(target: NoticeTarget): List<TechnologyCredit> = NoticeData.credits.filter { target in it.targets }

    /** What [target]'s build contains, in the order `legal/components.txt` gives. */
    fun components(target: NoticeTarget): List<ThirdPartyComponent> = NoticeData.components.filter { target in it.targets }

    /** Reflux's own copyright notice, the repository's `LICENSE`: proprietary, all rights reserved; null without one. */
    val refluxLicense: String? get() = NoticeData.refluxLicense

    /** The notice's copyright line ("Copyright © ... All rights reserved."), shown with the build. */
    val refluxCopyright: String? get() = refluxLicense?.lines()?.firstOrNull { it.startsWith("Copyright") }?.trim()

    /** The license text in `legal/licenses/[file]`, or null if there is none by that name. */
    fun text(file: String): String? = NoticeData.text(file)

    /**
     * A license text as paragraphs to read, one block each: lines broken for an 80-column file are joined, so
     * the words reflow to the screen; the words themselves are not changed.
     */
    fun paragraphs(text: String): List<String> =
        text.replace("\r\n", "\n").split(Regex("\n[ \t]*\n"))
            .map { paragraph -> paragraph.lines().joinToString(" ") { it.trim() }.replace(Regex("[ \t]{2,}"), " ").trim() }
            .filter { it.isNotEmpty() }
}
