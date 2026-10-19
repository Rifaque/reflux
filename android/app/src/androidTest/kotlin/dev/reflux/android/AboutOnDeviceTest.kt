package dev.reflux.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.reflux.android.engine.AndroidEnvironment
import dev.reflux.core.RefluxBuild
import dev.reflux.ui.legal.NoticeTarget
import dev.reflux.ui.legal.Notices
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What the About page shows, from the installed package: the version the package declares, the commit its
 * build recorded, and this build's third-party notices with every license text present.
 */
@RunWith(AndroidJUnit4::class)
class AboutOnDeviceTest {
    @Test
    fun thePackageSaysWhichBuildItIs() {
        val directory = File(context.filesDir, "reflux-about-${System.nanoTime()}").also { it.mkdirs() }
        try {
            val identity = AndroidEnvironment(context, directory, databaseName = null).build
            val versionName = context.packageManager.getPackageInfo(context.packageName, 0).versionName
            assertEquals(versionName, identity.version, "one version, everywhere")
            assertEquals(RefluxBuild.VERSION, identity.version)
            assertTrue(identity.commit?.matches(Regex("[0-9a-f]{7,40}")) == true, "the commit recorded by the build: $identity")
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun theNoticesAreThePackagesOwn() {
        val components = Notices.components(NoticeTarget.ANDROID)
        val names = components.map { it.name }
        assertTrue("AndroidX Media3" in names && "OkHttp and Okio" in names, "$names")
        assertFalse("Skia" in names || "mpv (libmpv) and FFmpeg" in names, "desktop-only software is not listed: $names")
        for (component in components) for (file in component.texts) {
            val text = assertNotNull(Notices.text(file), "${component.name}: $file")
            assertTrue(Notices.paragraphs(text).isNotEmpty())
        }
        // Reflux's own notice is in the package too: proprietary, and not among the third parties.
        val own = assertNotNull(Notices.refluxLicense, "Reflux's copyright notice is in the package")
        assertTrue("It is not open source" in own && "nothing in this notice changes or restricts them" in own)
        assertEquals("Copyright © 2026 RSX Develops. All rights reserved.", Notices.refluxCopyright)
        assertFalse("Reflux" in names)
    }
}
