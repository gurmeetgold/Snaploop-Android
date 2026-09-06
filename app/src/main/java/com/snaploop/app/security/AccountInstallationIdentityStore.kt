package com.snaploop.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/** Pseudonymous account+installation identity. Never use this value as authorization. */
class AccountInstallationIdentityStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("snaploop_installation_identity", Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    @Synchronized fun idFor(userId: String): String {
        val normalized = userId.trim()
        if (normalized.isEmpty()) return ""
        val marker = prefs.getString(MARKER_KEY, null) ?: java.util.UUID.randomUUID().toString().lowercase().also {
            prefs.edit().putString(MARKER_KEY, it).apply()
        }
        val key = loadOrCreateKey(marker)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key)
        val digest = mac.doFinal("snaploop-account-installation-v1:$normalized".toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    @Synchronized fun resetInstallation() {
        val marker = prefs.getString(MARKER_KEY, null)
        if (marker != null) keyStore.deleteEntry(alias(marker))
        prefs.edit().clear().apply()
    }

    private fun loadOrCreateKey(marker: String): SecretKey {
        val alias = alias(marker)
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN).setDigests(KeyProperties.DIGEST_SHA256).build())
        return generator.generateKey()
    }

    private fun alias(marker: String) = "snaploop.installation.hmac.v1.$marker"

    companion object { private const val MARKER_KEY = "marker.v1" }
}
