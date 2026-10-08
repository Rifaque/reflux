package dev.reflux.core.model

enum class ArtworkKind { POSTER, BACKDROP, LOGO, THUMBNAIL, BANNER }

/** Where an artwork image comes from. */
sealed interface ArtworkLocator {
    /** An image file inside a media source (e.g. `poster.jpg` next to a movie). */
    data class SourceFile(val location: MediaLocation) : ArtworkLocator

    /** An image provided by a metadata provider. */
    data class Remote(val url: String) : ArtworkLocator
}

data class Artwork(
    val itemId: MediaId,
    val kind: ArtworkKind,
    val locator: ArtworkLocator,
    /** Provider or rule that produced the artwork, e.g. `local` or `tmdb`. */
    val origin: String,
)
