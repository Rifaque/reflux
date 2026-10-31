package dev.reflux.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.reflux.core.model.ArtworkLocator
import dev.reflux.design.ArtworkAtmosphere
import dev.reflux.design.Motion
import dev.reflux.ui.state.ArtworkChoice
import dev.reflux.ui.state.ArtworkLoader
import dev.reflux.ui.state.Formatting
import dev.reflux.ui.state.MediaCard
import dev.reflux.ui.theme.LocalLayout
import dev.reflux.ui.theme.LocalTypography
import dev.reflux.ui.theme.RefluxColors
import dev.reflux.ui.theme.Text
import dev.reflux.ui.theme.toColor
import dev.reflux.ui.theme.tween
import kotlin.math.absoluteValue
import kotlin.math.ceil

val LocalArtworkLoader = staticCompositionLocalOf<ArtworkLoader> { error("No artwork loader provided") }

/** Decoded sizes are bucketed so that layout animations (the controller morph) do not reload images. */
private const val SIZE_BUCKET_PX = 160

/** Loads [locator] at about [width]; null while loading, when there is no artwork, or when it is unavailable. */
@Composable
fun rememberArtwork(locator: ArtworkLocator?, width: Dp): ImageBitmap? = rememberArtwork(listOfNotNull(locator), width)

/**
 * The first of [choices] (best first, from [ArtworkChoice]) that can be had, loaded at about [width]: one that
 * cannot be read now (offline and not cached, or out of the platform's reach) gives way to the next.
 */
@Composable
fun rememberArtwork(choices: List<ArtworkLocator>, width: Dp): ImageBitmap? {
    val loader = LocalArtworkLoader.current
    val pixels = with(LocalDensity.current) { width.toPx() }
    val bucket = (ceil(pixels / SIZE_BUCKET_PX).toInt().coerceAtLeast(1)) * SIZE_BUCKET_PX
    var image by remember(choices, bucket) { mutableStateOf(choices.firstOrNull()?.let { loader.cached(it, bucket) }) }
    LaunchedEffect(choices, bucket) {
        if (image == null) image = choices.firstNotNullOfOrNull { loader.cached(it, bucket) ?: loader.load(it, bucket) }
    }
    return image
}

/** Artwork that fades in over [fallback], so a slow or missing image never leaves a hole. */
@Composable
fun ArtworkImage(
    locator: ArtworkLocator?,
    width: Dp,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    fallback: @Composable () -> Unit = {},
) = ArtworkImage(rememberArtwork(locator, width), modifier, alignment, fallback)

/** An already loaded [image], or [fallback] while there is none. */
@Composable
fun ArtworkImage(
    image: ImageBitmap?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    fallback: @Composable () -> Unit = {},
) {
    val alpha by animateFloatAsState(if (image != null) 1f else 0f, Motion.controlsShow.tween())
    Box(modifier) {
        if (alpha < 1f) fallback()
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha },
                alignment = alignment,
                contentScale = ContentScale.Crop,
            )
        }
    }
}

/** A stable hue for a title, so a work without artwork always gets the same color. */
fun titleHue(title: String): Float = (title.fold(7) { hash, char -> hash * 31 + char.code }.absoluteValue % 360).toFloat()

/** The two tones of a generated backdrop for [title]: deep and muted, like a dimly lit poster. */
fun titleTones(title: String): Pair<Color, Color> = backdropTones(titleHue(title))

/** The two tones of a generated backdrop of [hue]; without a hue (colorless artwork), graphite. */
fun backdropTones(hue: Float?): Pair<Color, Color> =
    if (hue == null) Color.hsl(0f, 0f, 0.2f) to Color.hsl(0f, 0f, 0.07f)
    else Color.hsl(hue, 0.34f, 0.25f) to Color.hsl((hue + 38f) % 360f, 0.40f, 0.085f)

/** How much of an artwork's atmosphere tints the page around it: a wash, never a color scheme. */
private const val ATMOSPHERE_STRENGTH = 0.22f

/** A sparse sample of [image]: rows spread over its height, a pixel in every few. Enough to know its color. */
private fun sample(image: ImageBitmap): LongArray {
    val width = image.width
    val height = image.height
    if (width <= 0 || height <= 0) return LongArray(0)
    val row = IntArray(width)
    val step = (width / 96).coerceAtLeast(1)
    val pixels = ArrayList<Long>(ATMOSPHERE_ROWS * (width / step + 1))
    for (i in 0 until ATMOSPHERE_ROWS) {
        val y = ((i + 0.5f) / ATMOSPHERE_ROWS * height).toInt().coerceIn(0, height - 1)
        image.readPixels(row, startX = 0, startY = y, width = width, height = 1)
        for (x in 0 until width step step) pixels += row[x].toLong() and 0xFFFFFFFFL
    }
    return pixels.toLongArray()
}

private const val ATMOSPHERE_ROWS = 24

/**
 * The tone of the page around a work: the background, washed with the atmosphere of its [image] or, while
 * there is none, of the backdrop generated for [title]. Grey artwork leaves the page neutral. Only ever a
 * background: controls, navigation, and focus stay neutral whatever the artwork (docs/DESIGN.md).
 */
@Composable
fun rememberPageTone(image: ImageBitmap?, title: String): Color {
    val tone = remember(image, title) {
        val atmosphere = if (image == null) {
            ArtworkAtmosphere.ofHue(titleHue(title).toDouble())
        } else {
            runCatching { ArtworkAtmosphere.from(sample(image)) }.getOrNull()
        }
        atmosphere?.toColor()?.copy(alpha = ATMOSPHERE_STRENGTH)?.compositeOver(RefluxColors.background) ?: RefluxColors.background
    }
    return animateColorAsState(tone, Motion.navigation.tween()).value
}

/**
 * What a work looks like before (or without) artwork: its title set on a color derived from the title.
 * Deliberately designed rather than a grey box, because a fresh library is all placeholders.
 */
/**
 * A full-bleed generated backdrop for [title], for heroes and headers of works without a backdrop. A work
 * that has a [poster] gets the poster's color, so the page agrees with the artwork on it; one without any
 * artwork gets its title's.
 */
@Composable
fun TitleBackdrop(title: String, modifier: Modifier = Modifier, poster: ImageBitmap? = null) {
    val hue = remember(title, poster) {
        if (poster == null) titleHue(title) else runCatching { ArtworkAtmosphere.dominantHue(sample(poster))?.toFloat() }.getOrNull()
    }
    val (light, dark) = backdropTones(hue)
    Box(
        modifier.fillMaxSize()
            .background(Brush.linearGradient(listOf(dark, light, dark), start = Offset.Zero, end = Offset.Infinite))
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(light.copy(alpha = 0.9f), Color.Transparent),
                        center = Offset(size.width * 0.78f, size.height * 0.2f),
                        radius = size.maxDimension * 0.55f,
                    ),
                )
            },
    )
}

@Composable
fun TitlePlaceholder(title: String, caption: String?, modifier: Modifier = Modifier) {
    val (light, dark) = titleTones(title)
    Box(
        modifier.fillMaxSize()
            .background(Brush.linearGradient(listOf(light, dark), start = Offset.Zero, end = Offset.Infinite))
            .drawBehind {
                // A soft top-left sheen, the same light that edges the glass materials.
                drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.14f), Color.Transparent), center = Offset.Zero, radius = size.maxDimension * 0.9f))
            }
            .padding(12.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = LocalTypography.current.bodyStrong, maxLines = 4)
            if (caption != null) Text(caption, style = LocalTypography.current.caption, color = RefluxColors.textSecondary, maxLines = 1)
        }
    }
}

@Composable
private fun StateBadges(card: MediaCard, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        if (card.offline) {
            Box(Modifier.fillMaxSize().background(RefluxColors.background.copy(alpha = 0.55f)))
            Chip("Not connected", Modifier.align(Alignment.TopStart).padding(8.dp))
        }
        if (card.watched) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(8.dp).size(22.dp).clip(CircleShape).background(RefluxColors.background.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icon.CHECK, size = 13.dp)
            }
        }
        card.progress?.let { ProgressBar(it, Modifier.align(Alignment.BottomCenter).padding(horizontal = 8.dp, vertical = 8.dp)) }
    }
}

private fun describe(card: MediaCard): String = listOfNotNull(
    card.title,
    card.caption,
    Formatting.remaining(card.entry.watchState),
    "watched".takeIf { card.watched },
    "not connected".takeIf { card.offline },
    "on this device".takeIf { card.downloaded },
    "some episodes on this device".takeIf { card.downloadedPartly },
).joinToString(", ")

/**
 * A portrait card: poster, then title and caption beneath. Its width follows the adaptive layout, or the
 * space it is given when [fill] is set (grids).
 */
@Composable
fun PosterCard(card: MediaCard, onClick: () -> Unit, modifier: Modifier = Modifier, fill: Boolean = false, focusKey: String = card.id.value) {
    val focusReturn = rememberFocusReturn(focusKey)
    val layout = LocalLayout.current
    val width = layout.posterWidthDp.dp
    val shape = RoundedCornerShape(12.dp)
    // A work with only a backdrop shows its backdrop, cropped to its middle, rather than a generated card.
    val image = rememberArtwork(ArtworkChoice.portrait(card.poster, card.wide), width)
    Column(if (fill) modifier.fillMaxWidth() else modifier.width(width), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Pressable(
            {
                focusReturn.leaving()
                onClick()
            },
            focusReturn.modifier.semantics { contentDescription = describe(card) },
            shape,
        ) { active ->
            // The card says all of it; words on the artwork would be said a second time.
            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clearAndSetSemantics { }) {
                ArtworkImage(image, Modifier.fillMaxSize()) { TitlePlaceholder(card.title, null) }
                if (active) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.06f)))
                // Progress sits on a short shadow, so it reads on the brightest poster.
                if (card.progress != null) {
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.78f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f))))
                }
                StateBadges(card)
                // A hairline edge defines the poster against the black without framing it.
                Box(Modifier.fillMaxSize().border(1.dp, Color.White.copy(alpha = 0.07f), shape))
            }
        }
        // Two lines are always reserved so rows line up; a placeholder already says the title, so only the caption follows it.
        // The card has said all of this, as one stop.
        Column(Modifier.padding(horizontal = 2.dp).clearAndSetSemantics { }) {
            if (image != null) Text(card.title, style = LocalTypography.current.captionStrong, maxLines = 1)
            Caption(card.caption, card.downloaded, card.downloadedPartly)
            if (image == null) Text(" ", style = LocalTypography.current.captionStrong, maxLines = 1)
        }
    }
}

/**
 * A card's line beneath its title, with a small mark when a copy is kept on this device: the filled mark when
 * all of it is, the outline when some episodes of a show are.
 */
@Composable
private fun Caption(text: String?, downloaded: Boolean, partly: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        if (downloaded) Icon(Icon.DOWNLOADED, size = 12.dp, tint = RefluxColors.textSecondary)
        else if (partly) Icon(Icon.DOWNLOAD, size = 12.dp, tint = RefluxColors.textTertiary)
        Text(text ?: " ", style = LocalTypography.current.caption, color = RefluxColors.textTertiary, maxLines = 1)
    }
}

/** A landscape card for things in progress: the title sits on the image with what is left to watch. */
@Composable
fun WideCard(card: MediaCard, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val layout = LocalLayout.current
    val width = (layout.posterWidthDp * 1.9f).dp
    val shape = RoundedCornerShape(14.dp)
    val image = rememberArtwork(ArtworkChoice.landscape(card.wide, card.poster, 16f / 9f), width)
    Column(modifier.width(width), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Pressable(onClick, Modifier.semantics { contentDescription = describe(card) }, shape, lift = 0.6f) { active ->
            // The card says all of it; the title on the image would be said a second time.
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clearAndSetSemantics { }) {
                ArtworkImage(image, Modifier.fillMaxSize(), alignment = Alignment.TopCenter) {
                    TitlePlaceholder(card.title, null)
                }
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0.45f to Color.Transparent, 1f to RefluxColors.background.copy(alpha = 0.88f)),
                    ),
                )
                if (active) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.06f)))
                Box(Modifier.fillMaxSize().border(1.dp, Color.White.copy(alpha = 0.07f), shape))
                // A placeholder already says the title; real artwork has it said on it.
                if (image != null) {
                    Text(
                        card.title,
                        Modifier.align(Alignment.BottomStart).padding(start = 12.dp, end = 12.dp, bottom = 20.dp),
                        style = LocalTypography.current.bodyStrong,
                        maxLines = 1,
                    )
                }
                StateBadges(card)
            }
        }
        Column(Modifier.padding(horizontal = 2.dp).clearAndSetSemantics { }) {
            Text(card.caption ?: card.title, style = LocalTypography.current.captionStrong, maxLines = 1)
            Text(
                Formatting.remaining(card.entry.watchState) ?: " ",
                style = LocalTypography.current.caption,
                color = RefluxColors.textTertiary,
                maxLines = 1,
            )
        }
    }
}
