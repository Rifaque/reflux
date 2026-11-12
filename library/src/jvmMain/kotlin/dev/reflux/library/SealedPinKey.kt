package dev.reflux.library

import dev.reflux.core.model.SourceId
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The key profile PINs are checked with (docs/WATCH_STATE.md, "Profiles"): 32 random bytes made once per
 * installation and kept the way server credentials are, through the platform's [ConfigSeal] (Windows Credential
 * Manager, the Secret Service on Linux, a key in the Android keystore). The database keeps only what the seal
 * returns. So a copy of the database alone cannot be used to try PINs; where the platform has no secure store
 * (a Linux desktop without a Secret Service) the key is kept as it is, and the PIN is a lock on the interface only,
 * which docs/PRIVACY.md says.
 */
class SealedPinKey(private val library: Library, private val seal: ConfigSeal) : PinKey {
    private val random = SecureRandom()

    @Volatile
    private var key: ByteArray? = null

    override fun mac(data: ByteArray): ByteArray = Mac.getInstance(ALGORITHM).run {
        init(SecretKeySpec(key(), ALGORITHM))
        doFinal(data)
    }

    override fun randomBytes(count: Int): ByteArray = ByteArray(count).also(random::nextBytes)

    private fun key(): ByteArray = key ?: synchronized(this) {
        key ?: (open() ?: create()).also { key = it }
    }

    private fun open(): ByteArray? {
        val stored = library.setting(SETTING) ?: return null
        val opened = runCatching { seal.open(ID, stored) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { Base64.getDecoder().decode(opened) }.getOrNull()?.takeIf { it.size == KEY_BYTES }
    }

    /**
     * A new key. A key that can no longer be opened (the credential store was reset) is replaced, and the PINs it
     * checked stop matching: the person removes the profile's PIN by deleting and recreating it, never by a way round.
     */
    private fun create(): ByteArray {
        val fresh = randomBytes(KEY_BYTES)
        library.setSetting(SETTING, seal.seal(ID, Base64.getEncoder().encodeToString(fresh)))
        return fresh
    }

    private companion object {
        const val ALGORITHM = "HmacSHA256"
        const val KEY_BYTES = 32
        const val SETTING = "profile.pinKey"

        /** Where the seal keeps it: beside the sources' connections, under a name no source can have. */
        val ID = SourceId("reflux-profile-pin-key")
    }
}
