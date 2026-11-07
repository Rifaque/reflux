package dev.reflux.library

import dev.reflux.core.model.SourceId
import dev.reflux.core.source.SectionKind
import dev.reflux.core.source.SourceSection

/**
 * One library of a source (a server's), as last read, with the viewer's choice for it.
 *
 * @param included the library's own switch, kept as chosen whatever the source's switch says.
 * @param effective whether it takes part in the unified library: its own switch, and its source's.
 */
data class SourceLibrary(
    val id: String,
    val name: String,
    val kind: SectionKind,
    val included: Boolean,
    val effective: Boolean,
    val contents: SourceContents,
)

/**
 * Whether a source, and each of its libraries, take part in the unified library (docs/NAVIGATION.md, section 5).
 * Nothing here says whether the source is connected or reachable: those are separate questions.
 */
data class SourceInclusion(
    val sourceId: SourceId,
    /** The source's own switch. */
    val included: Boolean,
    /** Its libraries as last read; empty for a source that has none (a folder, a share). */
    val libraries: List<SourceLibrary>,
) {
    /** Whether anything of the source takes part: its switch is on and, where it has libraries, at least one of theirs. */
    val contributes: Boolean get() = included && (libraries.isEmpty() || libraries.any { it.included })

    /** On, with libraries that are all off: it contributes nothing, and that is said rather than left to be wondered about. */
    val noLibraryIncluded: Boolean get() = included && libraries.isNotEmpty() && libraries.none { it.included }
}

/** What the unified library is made of, per source: its switch, its libraries, and which of them are in. */
fun Library.inclusion(id: SourceId): SourceInclusion? {
    val record = source(id) ?: return null
    val excluded = queries.excludedSectionsOf(id.value).executeAsList().toSet()
    val libraries = queries.sourceSectionsOf(id.value).executeAsList().map { row ->
        val on = row.section_id !in excluded
        val counts = queries.sectionContents(id.value, row.section_id).executeAsOne()
        SourceLibrary(
            id = row.section_id,
            name = row.name,
            kind = enumOrNull<SectionKind>(row.kind) ?: SectionKind.MIXED,
            included = on,
            effective = on && record.included,
            contents = SourceContents(counts.movies.toInt(), counts.shows.toInt()),
        )
    }
    return SourceInclusion(id, record.included, libraries)
}

/** The libraries a source has, as last read, without asking it. */
fun Library.sourceLibraries(id: SourceId): List<SourceLibrary> = inclusion(id)?.libraries.orEmpty()

/**
 * The source's own switch. Off takes every one of its libraries out of the unified library and keeps each
 * library's switch as it was, so on again restores exactly what was chosen. Nothing else changes: no
 * connection, credential, cached metadata, watch state, file, or copy kept on the device.
 */
fun Library.setSourceIncluded(id: SourceId, included: Boolean) = database.transaction {
    queries.setSourceIncluded(if (included) 1L else 0L, id.value)
    writer.recomputeLibraries()
}

/** One library's switch; it counts only while its source's switch is on. */
fun Library.setLibraryIncluded(id: SourceId, section: String, included: Boolean) = database.transaction {
    if (included) queries.includeSection(id.value, section) else queries.excludeSection(id.value, section)
    writer.recomputeLibraries()
}

/**
 * Libraries a connection left out before inclusion belonged to the library (docs/NAVIGATION.md): kept as
 * excluded libraries, so the viewer's choice holds, while every library is read again to be browsed.
 */
fun Library.excludeLibraries(id: SourceId, sections: Set<String>) = database.transaction {
    sections.forEach { queries.excludeSection(id.value, it) }
    writer.recomputeLibraries()
}

/**
 * Remembers the libraries a source has now, in its order, so they can be shown while it is away. A library that
 * is gone leaves the list; the choice made for it stays, should it come back. A new one is included.
 */
internal fun Library.rememberSections(id: SourceId, sections: List<SourceSection>) = database.transaction {
    if (!queries.sourceById(id.value).executeAsOneOrNull().let { it != null }) return@transaction
    queries.deleteSourceSectionsOf(id.value)
    sections.forEachIndexed { index, section ->
        queries.insertSourceSection(id.value, section.id, section.name, section.kind.name, index.toLong())
    }
}

/** How many movies and shows one library of a source holds. */
fun Library.libraryContents(id: SourceId, section: String): SourceContents =
    queries.sectionContents(id.value, section).executeAsOne().let { SourceContents(it.movies.toInt(), it.shows.toInt()) }
