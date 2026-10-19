package dev.reflux.core

/**
 * Which build of Reflux this is (docs/VERSIONING.md, "Build metadata"): the product version, the commit it was
 * built from, and whether the tree had changes. Shown on the About page and at the top of the diagnostics log.
 */
data class BuildIdentity(
    val version: String = RefluxBuild.VERSION,
    /** The short commit hash, or null where the build could not tell (no Git checkout). */
    val commit: String? = null,
    val modified: Boolean = false,
) {
    /** `0.9.0-beta.1 (abc1234)`, `0.9.0-beta.1 (abc1234, modified)`, or the version alone without a commit. */
    val label: String
        get() = when {
            commit == null -> version
            modified -> "$version ($commit, modified)"
            else -> "$version ($commit)"
        }

    companion object {
        /**
         * Reads what the build wrote down about itself (`commit=` and `modified=` lines); null or unreadable
         * text gives the version alone. The version is never read from there: it has one source.
         */
        fun parse(text: String?): BuildIdentity {
            val values = text.orEmpty().lineSequence().mapNotNull { line ->
                line.split('=', limit = 2).takeIf { it.size == 2 }?.let { (key, value) -> key.trim() to value.trim() }
            }.toMap()
            val commit = values["commit"]?.takeIf { it.matches(COMMIT) }
            return BuildIdentity(commit = commit, modified = commit != null && values["modified"] == "true")
        }

        private val COMMIT = Regex("[0-9a-f]{7,40}")
    }
}
