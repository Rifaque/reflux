package dev.reflux.ui.screens

import dev.reflux.ui.theme.States
import dev.reflux.ui.theme.Shapes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.reflux.core.metadata.ProviderAttribution
import dev.reflux.core.metadata.ProviderLogo
import dev.reflux.design.MaterialRole
import dev.reflux.ui.components.Icon
import dev.reflux.ui.components.Pressable
import dev.reflux.ui.components.initialFocus
import dev.reflux.ui.components.readable
import dev.reflux.ui.components.rememberFocusReturn
import dev.reflux.ui.legal.Notices
import dev.reflux.ui.legal.ThirdPartyComponent
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.SUPPORT_URL
import dev.reflux.ui.state.Destination
import dev.reflux.ui.theme.LocalTypography
import dev.reflux.ui.theme.RefluxColors
import dev.reflux.ui.theme.Text
import dev.reflux.ui.theme.glass
import kotlinx.coroutines.withContext

/** The name Reflux's own license is listed under, beside the third-party software. */
internal const val REFLUX_NOTICE = "Reflux"

/**
 * Reflux itself: which build this is, what it keeps and sends, who it credits, and the way to the licenses.
 * A short page; the legal texts are a step further in, never on it.
 */
@Composable
internal fun AboutScreen(app: AppState) {
    val build = app.platform.build
    val attribution = app.metadataAttribution
    val target = app.platform.notices
    Page {
        item(key = "identity") {
            // One stop for a remote or a screen reader: the name, what it is, and which build.
            Column(Modifier.fillMaxWidth().initialFocus().readable().semantics(mergeDescendants = true) {}.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Reflux", Modifier.semantics { heading() }, style = LocalTypography.current.headline)
                Text("A media player built around your media, not your media server.", color = RefluxColors.contentSecondary)
                Text("Version ${build.label}", Modifier.padding(top = 4.dp), style = LocalTypography.current.caption, color = RefluxColors.contentTertiary)
                Notices.refluxCopyright?.let { Text(it, style = LocalTypography.current.caption, color = RefluxColors.contentTertiary) }
                // The maker, beneath the product: RSX Develops' own wordmark where the shell has it.
                app.platform.publisherMark?.let { mark ->
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Made by", style = LocalTypography.current.caption, color = RefluxColors.contentTertiary)
                        androidx.compose.foundation.Image(
                            mark, "RSX Develops",
                            Modifier.height(14.dp).aspectRatio(mark.width.toFloat() / mark.height),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit, alpha = 0.85f,
                        )
                    }
                }
            }
        }
        attribution?.let { credit -> item(key = "credits") { Credit(credit) } }
        item(key = "privacy") {
            Column(Modifier.fillMaxWidth().readable().semantics(mergeDescendants = true) {}.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Privacy", Modifier.semantics { heading() }, style = LocalTypography.current.bodyStrong)
                Text(
                    "Reflux has no account and sends nothing to the people who make it. Your library, your progress, and your downloads are kept on this device.",
                    color = RefluxColors.contentSecondary,
                )
                Text(
                    listOfNotNull(
                        attribution?.let { "To recognize your movies and shows and fetch their artwork, Reflux looks up their titles on ${it.name}." },
                        "A server or share you add sees what you browse and play from it, and a server keeps your progress on what you play from it.",
                    ).joinToString(" "),
                    color = RefluxColors.contentSecondary,
                )
            }
        }
        // Reflux's own terms first, then the software it is built with: two rows, so neither reads as the other's.
        if (Notices.refluxLicense != null) {
            item(key = "license") {
                val back = rememberFocusReturn("about/license")
                Link("Copyright and ownership", "Reflux is proprietary software, not open source", back.modifier) {
                    back.leaving()
                    app.navigator.open(Destination.Notice(REFLUX_NOTICE))
                }
            }
        }
        if (target != null) {
            item(key = "notices") {
                val back = rememberFocusReturn("about/notices")
                val count = Notices.components(target).size
                Link("Third-party software", "$count components Reflux is built with, each under its own license", back.modifier) {
                    back.leaving()
                    app.navigator.open(Destination.Notices)
                }
            }
        }
        // Last, help: where to ask, and a report to attach that Reflux makes only when asked and never sends.
        item(key = "help") {
            val back = rememberFocusReturn("about/help")
            Link("Help and feedback", "Questions, problems, and ideas: ${SUPPORT_URL.removePrefix("https://")}", back.modifier) {
                back.leaving()
                app.openSupport()
            }
        }
        if (app.canSaveSupportReport) {
            item(key = "support-report") {
                Link(
                    "Save a support report",
                    "The version, this device, how the last playback went, and recent problems, to attach to a report. No passwords, addresses, file paths, or titles; Reflux sends nothing itself.",
                ) { app.saveSupportReport() }
            }
        }
    }
}

/**
 * The metadata provider's credit as its terms ask: its approved logo, smaller than Reflux's own name, and its
 * notice word for word. The glass card is the page's one surface; the words around it stand on the page.
 */
@Composable
private fun Credit(attribution: ProviderAttribution) {
    val shape = Shapes.large
    Column(
        Modifier.fillMaxWidth().readable(shape).glass(MaterialRole.ELEVATED, shape).semantics(mergeDescendants = true) {}.padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        attribution.logo?.let { ProviderMark(it, attribution.name, height = 14.dp) }
        Text("Titles, artwork, and details come from ${attribution.name} (${attribution.site}).", color = RefluxColors.contentPrimary)
        Text(attribution.notice, style = LocalTypography.current.caption, color = RefluxColors.contentSecondary)
    }
}

/** A provider's logo as it publishes it: its own shape, its own gradient, at [height] with its proportions. */
@Composable
internal fun ProviderMark(logo: ProviderLogo, name: String, height: Dp) {
    val path = remember(logo) { PathParser().parsePathString(logo.pathData).toPath() }
    val brush = remember(logo) {
        Brush.horizontalGradient(*logo.stops.map { (offset, argb) -> offset to Color(argb) }.toTypedArray(), startX = 0f, endX = logo.width)
    }
    Canvas(Modifier.height(height).aspectRatio(logo.width / logo.height).semantics { contentDescription = name; role = Role.Image }) {
        scale(size.width / logo.width, size.height / logo.height, pivot = Offset.Zero) { drawPath(path, brush) }
    }
}

/** A row that leads one step further in: what is there, a line about it, and the way in. */
@Composable
private fun Link(title: String, body: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = Shapes.large
    Pressable(onClick, modifier.fillMaxWidth(), shape, lift = 0f) { active ->
        Row(
            Modifier.fillMaxWidth().glass(MaterialRole.ELEVATED, shape).background(States.layer(if (active) 0.07f else 0f))
                .padding(horizontal = 18.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = LocalTypography.current.bodyStrong, maxLines = 1)
                Text(body, style = LocalTypography.current.caption, color = RefluxColors.contentSecondary, maxLines = 2)
            }
            Icon(Icon.CHEVRON_RIGHT, size = 16.dp, tint = RefluxColors.contentTertiary)
        }
    }
}

/** Every third-party component in this build, each leading to its notice and license texts. */
@Composable
internal fun NoticesScreen(app: AppState) {
    val target = app.platform.notices ?: return
    val components = remember(target) { Notices.components(target) }
    Page {
        item(key = "heading") {
            Heading("Third-party software", "Reflux is built with this software, each under its own license, which Reflux's own copyright does not change. Thank you to everyone who makes it.")
        }
        itemsIndexed(components, key = { _, it -> it.name }) { index, component ->
            val back = rememberFocusReturn("notice/${component.name}")
            NoticeRow(component, if (index == 0) back.modifier.initialFocus() else back.modifier) {
                back.leaving()
                app.navigator.open(Destination.Notice(component.name))
            }
        }
    }
}

@Composable
private fun NoticeRow(component: ThirdPartyComponent, modifier: Modifier, onClick: () -> Unit) {
    val shape = Shapes.large
    Pressable(onClick, modifier.fillMaxWidth(), shape, lift = 0f) { active ->
        Row(
            Modifier.fillMaxWidth().glass(MaterialRole.ELEVATED, shape).background(States.layer(if (active) 0.07f else 0f))
                .padding(horizontal = 18.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(component.name, style = LocalTypography.current.bodyStrong, maxLines = 1)
                Text(component.license, style = LocalTypography.current.caption, color = RefluxColors.contentTertiary, maxLines = 1)
            }
            Icon(Icon.CHEVRON_RIGHT, size = 16.dp, tint = RefluxColors.contentTertiary)
        }
    }
}

/**
 * One component's notice: its name, license, and how it is part of Reflux, then its license texts as they
 * are, a paragraph at a time. The texts are read off the main thread and laid out only as they scroll in.
 */
@Composable
internal fun NoticeScreen(app: AppState, name: String) {
    val target = app.platform.notices
    val component = remember(name, target) { target?.let { Notices.components(it) }?.firstOrNull { it.name == name } }
    val own = name == REFLUX_NOTICE && component == null
    if (component == null && !own) return
    val paragraphs by produceState<List<String>?>(null, name) {
        value = withContext(app.background) {
            val texts = if (own) listOfNotNull(Notices.refluxLicense) else component!!.texts.mapNotNull(Notices::text)
            texts.flatMap(Notices::paragraphs)
        }
    }
    Page {
        item(key = "heading") {
            Column(Modifier.padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(component?.name ?: "Reflux", Modifier.semantics { heading() }, style = LocalTypography.current.headline)
                component?.let { Text(it.license, color = RefluxColors.contentSecondary) }
                component?.note?.let { Text(it, style = LocalTypography.current.caption, color = RefluxColors.contentTertiary) }
            }
        }
        val loaded = paragraphs ?: return@Page
        if (loaded.isEmpty()) {
            item(key = "none") {
                Text("Reflux does not include it, so its license comes with the copy on this device.", Modifier.initialFocus().readable().padding(4.dp), color = RefluxColors.contentSecondary)
            }
        }
        itemsIndexed(loaded) { index, paragraph ->
            // Each paragraph is a stop for a remote, so a license longer than the screen is read by moving through it.
            Box(Modifier.fillMaxWidth().then(if (index == 0) Modifier.initialFocus() else Modifier).readable().semantics(mergeDescendants = true) {}.padding(vertical = 4.dp)) {
                Text(paragraph, style = LocalTypography.current.caption, color = RefluxColors.contentSecondary)
            }
        }
    }
}
