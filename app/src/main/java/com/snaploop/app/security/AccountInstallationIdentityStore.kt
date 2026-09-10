package com.snaploop.app.security

import android.content.Context
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/**
 * Pseudonymous account+installation identity. Never use this value as authorization.
 *
 * Android 8+ scopes ANDROID_ID to the app-signing key, device and OS user. Hashing that local value
 * with the SnapLoop account gives us a 64-hex pseudonym that survives a normal uninstall/reinstall
 * with the same signing key, preventing the same MediaStore photo from being republished under a
 * fresh source identity. The raw device-scoped value never leaves the device.
 *
 * The previous Keystore-backed random identity remains only as a defensive fallback for devices
 * that do not expose a usable ANDROID_ID.
 */
class AccountInstallationIdentityStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("snaploop_installation_identity", Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    @Synchronized fun idFor(userId: String): String {
        val normalizedUserId = userId.trim()
        if (normalizedUserId.isEmpty()) return ""

        val androidId = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        if (androidId != null) return stableId(androidId, normalizedUserId)

        return fallbackId(normalizedUserId)
    }

    /** Clears only the exceptional fallback identity; the platform-scoped ID is not app storage. */
    @Synchronized fun resetInstallation() {
        val marker = prefs.getString(MARKER_KEY, null)
        if (marker != null) keyStore.deleteEntry(alias(marker))
        prefs.edit().clear().apply()
    }

    private fun fallbackId(normalizedUserId: String): String {
        val marker = prefs.getString(MARKER_KEY, null) ?: java.util.UUID.randomUUID().toString().lowercase().also {
            prefs.edit().putString(MARKER_KEY, it).apply()
        }
        val key = loadOrCreateKey(marker)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key)
        val digest = mac.doFinal("snaploop-account-installation-fallback-v2:$normalizedUserId".toByteArray(StandardCharsets.UTF_8))
        return digest.toHex()
    }

    private fun loadOrCreateKey(marker: String): SecretKey {
        val alias = alias(marker)
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN).setDigests(KeyProperties.DIGEST_SHA256).build())
        return generator.generateKey()
    }

    private fun alias(marker: String) = "snaploop.installation.hmac.v2.$marker"

    companion object {
        private const val MARKER_KEY = "marker.v2"

        internal fun stableId(androidId: String, userId: String): String {
            val normalizedAndroidId = androidId.trim()
            val normalizedUserId = userId.trim()
            require(normalizedAndroidId.isNotEmpty()) { "Android installation identity is unavailable" }
            require(normalizedUserId.isNotEmpty()) { "Account identity is unavailable" }
            val material = "snaploop-account-installation-v2:$normalizedAndroidId:$normalizedUserId"
            return MessageDigest.getInstance("SHA-256")
                .digest(material.toByteArray(StandardCharsets.UTF_8))
                .toHex()
        }
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
