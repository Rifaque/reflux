package dev.reflux.desktop

import java.nio.file.Files
import java.nio.file.Path

/**
 * What a Linux machine without libmpv is told to install, for its own distribution (Phase 1, workstream 12;
 * docs/RELEASE.md, "libmpv on Linux"). Reflux for Linux cannot carry a libmpv of its own: mpv's X11 support, which
 * Reflux's video window uses, is GPL-only (mpv's meson.build: `x11` requires `gpl`), and Reflux ships only LGPL code
 * (DECISIONS.md, 2026-11-18). The distribution's own package is the user's install, under the distribution's terms.
 *
 * Each command was run in a clean container of that distribution and the packaged tarball then played
 * (docs/VERIFICATION.md, "Phase 1"); a distribution not listed gets the general sentence.
 */
internal object LinuxPlaybackSetup {
    /** The hint for the distribution [osRelease] (the text of `/etc/os-release`) describes. */
    fun hint(osRelease: String?): String {
        val fields = osRelease.orEmpty().lines().mapNotNull { line ->
            line.substringBefore('=', "").trim().takeIf { it.isNotEmpty() }?.let { it to line.substringAfter('=').trim().trim('"', '\'') }
        }.toMap()
        val family = (listOfNotNull(fields["ID"]) + fields["ID_LIKE"].orEmpty().split(' ')).map { it.lowercase() }.filter { it.isNotBlank() }
        val version = fields["VERSION_ID"].orEmpty()
        // A derivative's own version number says nothing of its base (Linux Mint 21 is Ubuntu 22.04): the codename
        // of the Ubuntu or Debian release beneath it does. Before Ubuntu 23.04 and Debian 12 there is no libmpv2.
        val base = (fields["UBUNTU_CODENAME"] ?: fields["VERSION_CODENAME"].takeIf { "debian" in family }).orEmpty().lowercase()
        val command = when {
            base in OLD_BASES -> "sudo apt install libmpv1"
            "ubuntu" == fields["ID"] && version.majorAtMost(22) -> "sudo apt install libmpv1"
            "debian" == fields["ID"] && version.majorAtMost(11) -> "sudo apt install libmpv1"
            "debian" in family || "ubuntu" in family -> "sudo apt install libmpv2"
            "fedora" in family -> "sudo dnf install mpv-libs"
            "arch" in family -> "sudo pacman -S mpv"
            "opensuse" in family || "suse" in family -> "sudo zypper install libmpv2"
            else -> null
        }
        return if (command != null) {
            "Playback needs libmpv from your system. Install it with: $command — then restart Reflux."
        } else {
            "Playback needs libmpv from your system: install your distribution's libmpv package (it is part of mpv), then restart Reflux."
        }
    }

    /** The hint for this machine. */
    fun hint(): String = hint(runCatching { Files.readString(Path.of("/etc/os-release")) }.getOrNull())

    private fun String.majorAtMost(major: Int): Boolean = substringBefore('.').toIntOrNull()?.let { it <= major } == true

    /** Ubuntu 20.04 and 22.04, Debian 10 and 11: packaged as libmpv1, never libmpv2. */
    private val OLD_BASES = setOf("focal", "jammy", "buster", "bullseye")
}
