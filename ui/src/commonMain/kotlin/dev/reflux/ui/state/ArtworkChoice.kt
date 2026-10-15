package dev.reflux.ui.state

import dev.reflux.core.model.ArtworkLocator

/**
 * Which of a work's images picture it in a space of a given shape, best first.
 *
 * A work may have a poster, a wide image (a backdrop, or an episode's still), both, or neither, and a platform
 * that cannot read some of its artwork simply has fewer. The rule is the same on every platform: the image made
 * for the shape; failing that, or failing to load, the work's other real image, cropped, where the crop is still
 * a picture; and only when none is left, a generated title treatment (an empty list here).
 */
object ArtworkChoice {
    /**
     * The widest frame a poster fills in place of wide artwork: 16:9, the shape of what it stands in for.
     * Cropped any wider, a poster is a band of its lettering rather than a picture.
     */
    const val POSTER_FILLS_UP_TO = 16f / 9f

    /** For a portrait frame (a poster card): the poster, then the wide image cropped to its middle. */
    fun portrait(poster: ArtworkLocator?, wide: ArtworkLocator?): List<ArtworkLocator> = listOfNotNull(poster, wide)

    /** For a landscape frame [aspect] times as wide as it is tall: the wide image, then the poster if the frame is not too wide for it. */
    fun landscape(wide: ArtworkLocator?, poster: ArtworkLocator?, aspect: Float): List<ArtworkLocator> =
        listOfNotNull(wide, poster?.takeIf { aspect <= POSTER_FILLS_UP_TO })

    /**
     * What a work's header shows, [aspect] being its shape: images across it, and whether a poster stands
     * beside the title where the layout has [room] for one. The poster stands there when there is one, or when
     * there is no real artwork at all (its generated card then names the work); a work with only a backdrop
     * shows its backdrop rather than a generated card beside it. A poster that does not stand beside the title
     * fills the header instead, if the header is not too wide for it.
     */
    fun header(poster: ArtworkLocator?, backdrop: ArtworkLocator?, aspect: Float, room: Boolean): Header {
        val beside = room && (poster != null || backdrop == null)
        return Header(fill = landscape(backdrop, poster.takeUnless { beside }, aspect), posterBeside = beside)
    }

    data class Header(val fill: List<ArtworkLocator>, val posterBeside: Boolean)
}
