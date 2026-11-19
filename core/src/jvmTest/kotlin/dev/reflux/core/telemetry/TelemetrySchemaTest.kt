package dev.reflux.core.telemetry

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The schema itself cannot carry anything private: every field of every event is the build's context, a closed set,
 * or a bucket. A field of another type (a String, a number, a list) added later fails this test, and with it the
 * privacy review it would need (docs/PRIVACY.md, "Opt-in technical statistics").
 */
class TelemetrySchemaTest {
    @Test
    fun everyFieldOfEveryEventIsAClosedSetOrTheBuild() {
        val events = TelemetryEvent::class.java.permittedSubclasses
        assertTrue(events.size >= 6, "every kind of event is checked: ${events.map { it.simpleName }}")
        for (event in events) {
            for (field in event.declaredFields.filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }) {
                val type = field.type
                assertTrue(type.isEnum || type == TelemetryContext::class.java, "${event.simpleName}.${field.name} is ${type.simpleName}: only enums and the build's context")
            }
        }
        // The context: a platform, a channel (closed sets), and a version, which its constructor checks is one.
        val context = TelemetryContext::class.java.declaredFields.filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
        assertTrue(context.all { it.type.isEnum || it.name == "version" }, context.map { "${it.name}: ${it.type.simpleName}" }.toString())
    }
}
