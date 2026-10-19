package dev.reflux.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** How a build names itself (docs/VERSIONING.md, "Build metadata"). */
class BuildIdentityTest {
    @Test
    fun theLabelIsTheVersionWithTheCommitAndWhetherTheTreeHadChanges() {
        assertEquals("0.9.0-beta.1 (abc1234)", BuildIdentity("0.9.0-beta.1", "abc1234").label)
        assertEquals("0.9.0-beta.1 (abc1234, modified)", BuildIdentity("0.9.0-beta.1", "abc1234", modified = true).label)
        assertEquals("0.9.0-beta.1", BuildIdentity("0.9.0-beta.1").label)
    }

    @Test
    fun theVersionIsAlwaysTheDeclaredOne() {
        assertEquals(RefluxBuild.VERSION, BuildIdentity().version)
        // What the build wrote down never supplies a version of its own.
        assertEquals(RefluxBuild.VERSION, BuildIdentity.parse("version=9.9.9\ncommit=abc1234\nmodified=false\n").version)
    }

    @Test
    fun whatTheBuildRecordedIsRead() {
        assertEquals(BuildIdentity(commit = "8bd4bba", modified = true), BuildIdentity.parse("commit=8bd4bba\nmodified=true\n"))
        assertEquals(BuildIdentity(commit = "8bd4bba"), BuildIdentity.parse("commit=8bd4bba\r\nmodified=false\r\n"))
    }

    @Test
    fun nothingOrSomethingElseRecordedGivesTheVersionAlone() {
        for (text in listOf(null, "", "garbage", "commit=\nmodified=true", "commit=not a hash\nmodified=true", "commit=\$(rm -rf)")) {
            val identity = BuildIdentity.parse(text)
            assertNull(identity.commit, "from $text")
            assertEquals(RefluxBuild.VERSION, identity.label, "from $text")
        }
    }
}
