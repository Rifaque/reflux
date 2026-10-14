package dev.reflux.android.engine

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dev.reflux.core.model.SourceId
import dev.reflux.library.ConfigSeal
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Seals what a source connection stores (a server's token, a share's password) with a key that lives in
 * the Android keystore and never leaves it. The library database then holds only ciphertext, so a copy of
 * the database is not a copy of the user's passwords.
 *
 * A stored value that cannot be opened (the key is gone, which only happens when the app's keystore entries
 * are wiped) opens as nothing: the source then cannot be recreated, shows as not connected, and signing in
 * again replaces it. Nothing else is lost.
 */
class KeystoreSeal(private val alias: String = "reflux-sources") : ConfigSeal {
    override fun seal(id: SourceId, config: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.doFinal(config.encodeToByteArray())
        return PREFIX + encode(cipher.iv) + ":" + encode(sealed)
    }

    override fun open(id: SourceId, stored: String): String {
        if (!sealed(stored)) return stored
        return runCatching {
            val (iv, sealed) = stored.removePrefix(PREFIX).split(':', limit = 2).map { Base64.decode(it, Base64.NO_WRAP) }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
            cipher.doFinal(sealed).decodeToString()
        }.getOrDefault("")
    }

    override fun sealed(stored: String): Boolean = stored.startsWith(PREFIX)

    @Synchronized
    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PREFIX = "sealed:v1:"
        const val TAG_BITS = 128
    }
}
