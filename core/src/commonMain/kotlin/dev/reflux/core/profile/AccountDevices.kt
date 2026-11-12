package dev.reflux.core.profile

import dev.reflux.core.model.StableIds

/**
 * The device identity Reflux presents to a server that tells its clients apart by device (Jellyfin's
 * `DeviceId`, Plex's `X-Plex-Client-Identifier`): one per account on this installation, never one shared by every
 * account (docs/WATCH_STATE.md, "Profiles"). A server keeps its sessions per device, and Reflux finds its own
 * session by the identity it sent; two accounts sharing one identity could each be taken for the other there.
 *
 * Derived, not drawn: the same account on the same server from the same installation always has the same identity,
 * so signing in again does not leave a new device behind on the server each time. It carries nothing secret and
 * nothing that identifies the person beyond what the server already knows.
 */
object AccountDevices {
    /**
     * @param installation this installation's own identifier (the one identity the shell keeps for itself).
     * @param type the adapter type ([dev.reflux.core.source.SourceDescriptor.type]).
     * @param address the server's address as normalized by the adapter.
     * @param account who signs in there, as typed (compared without case); null for a connection with no account.
     */
    fun id(installation: String, type: String, address: String, account: String?): String {
        val who = account?.trim()?.lowercase().orEmpty()
        val server = address.trim().trimEnd('/').lowercase()
        return installation + "-" + StableIds.hash("$type\u0000$server\u0000$who")
    }
}
