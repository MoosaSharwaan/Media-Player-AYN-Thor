package com.msharwaan.exoplayer.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The API keys and the OpenSubtitles login, entered in Settings. */
enum class Secret { TMDB_KEY, FANART_KEY, OPENSUBTITLES_KEY, OPENSUBTITLES_USER, OPENSUBTITLES_PASSWORD }

/** Turns a value into stored text and back. */
interface Crypto {
    fun encrypt(plain: String): String
    fun decrypt(stored: String): String
}

/**
 * Keys and the password, stored on the device only, encrypted with a key that never
 * leaves Android's Keystore. App backup is off, so none of it is copied off the Thor.
 */
class Secrets(context: Context, private val crypto: Crypto = KeystoreCrypto()) {
    private val prefs = context.getSharedPreferences("secrets", Context.MODE_PRIVATE)

    /** The value, or null when not set (or no longer readable, e.g. after the app's data was moved). */
    operator fun get(secret: Secret): String? =
        prefs.getString(secret.name, null)?.let { runCatching { crypto.decrypt(it) }.getOrNull() }?.takeIf { it.isNotBlank() }

    /** Saves [value]; blank removes it. */
    operator fun set(secret: Secret, value: String?) {
        val v = value?.trim().orEmpty()
        prefs.edit { if (v.isEmpty()) remove(secret.name) else putString(secret.name, crypto.encrypt(v)) }
    }
}

/** AES-GCM with a key created in, and kept by, the Android Keystore. */
class KeystoreCrypto : Crypto {
    private fun key(): SecretKey {
        val store = KeyStore.getInstance(STORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, STORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    override fun decrypt(stored: String): String {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_BYTES))
        return String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES))
    }

    private companion object {
        const val STORE = "AndroidKeyStore"
        const val ALIAS = "exo_secrets"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}
