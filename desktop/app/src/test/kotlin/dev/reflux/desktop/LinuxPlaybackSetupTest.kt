package dev.reflux.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A Linux machine without libmpv is told the one command for its own distribution (Phase 1, workstream 12). */
class LinuxPlaybackSetupTest {
    private fun command(osRelease: String) = LinuxPlaybackSetup.hint(osRelease).substringAfter("with: ", "").substringBefore(" —")

    @Test
    fun eachDistributionGetsItsOwnCommand() {
        assertEquals("sudo apt install libmpv2", command("ID=ubuntu\nVERSION_ID=\"24.04\"\nID_LIKE=debian"))
        assertEquals("sudo apt install libmpv1", command("ID=ubuntu\nVERSION_ID=\"22.04\"\nID_LIKE=debian"), "22.04 has no libmpv2")
        assertEquals("sudo apt install libmpv2", command("ID=debian\nVERSION_ID=\"12\""))
        assertEquals("sudo apt install libmpv1", command("ID=debian\nVERSION_ID=\"11\""))
        assertEquals("sudo apt install libmpv2", command("ID=linuxmint\nID_LIKE=\"ubuntu debian\"\nVERSION_ID=\"22\""), "a derivative, by its family")
        assertEquals("sudo apt install libmpv2", command("ID=pop\nID_LIKE=\"ubuntu debian\"\nVERSION_ID=\"24.04\""))
        // A derivative's own version is not its base's: Linux Mint 21 is Ubuntu 22.04 (jammy), which has no libmpv2.
        assertEquals("sudo apt install libmpv1", command("ID=linuxmint\nID_LIKE=\"ubuntu debian\"\nVERSION_ID=\"21.3\"\nUBUNTU_CODENAME=jammy"))
        assertEquals("sudo apt install libmpv2", command("ID=linuxmint\nID_LIKE=\"ubuntu debian\"\nVERSION_ID=\"22\"\nUBUNTU_CODENAME=noble"))
        assertEquals("sudo apt install libmpv1", command("ID=pop\nID_LIKE=\"ubuntu debian\"\nVERSION_ID=\"22.04\"\nUBUNTU_CODENAME=jammy"))
        assertEquals("sudo apt install libmpv1", command("ID=raspbian\nID_LIKE=debian\nVERSION_ID=\"11\"\nVERSION_CODENAME=bullseye"))
        assertEquals("sudo dnf install mpv-libs", command("ID=fedora\nVERSION_ID=41"))
        assertEquals("sudo pacman -S mpv", command("ID=arch"))
        assertEquals("sudo pacman -S mpv", command("ID=manjaro\nID_LIKE=arch"))
        assertEquals("sudo zypper install libmpv2", command("ID=\"opensuse-tumbleweed\"\nID_LIKE=\"opensuse suse\""))
    }

    @Test
    fun anUnknownOrUnreadableSystemGetsTheGeneralSentence() {
        for (unknown in listOf(null, "", "ID=nixos", "garbage")) {
            val hint = LinuxPlaybackSetup.hint(unknown)
            assertTrue("libmpv package" in hint && "sudo" !in hint, hint)
        }
    }
}
