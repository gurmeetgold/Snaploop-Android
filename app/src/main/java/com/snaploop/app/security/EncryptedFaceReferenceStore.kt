package com.snaploop.app.security

import android.content.Context
import java.io.File
import java.security.MessageDigest

/** Guided/gallery face reference JPEGs remain encrypted, device-local and outside backups. */
class EncryptedFaceReferenceStore(context: Context) {
    enum class Kind(val suffix: String) { GUIDED("guided"), GALLERY("gallery") }

    private val folder = File(context.noBackupFilesDir, "SnapLoop/FaceReferences").apply { mkdirs() }
    private val crypto = KeystoreAes("snaploop.face.references.aes.v1")

    fun save(userId: String, kind: Kind, jpeg: ByteArray) {
        require(jpeg.isNotEmpty())
        val destination = file(userId, kind)
        val temp = File(destination.parentFile, destination.name + ".tmp")
        temp.writeBytes(crypto.encrypt(jpeg))
        if (!temp.renameTo(destination)) {
            destination.writeBytes(temp.readBytes())
            temp.delete()
        }
    }

    fun load(userId: String, kind: Kind): ByteArray? = runCatching {
        val f = file(userId, kind)
        if (!f.exists()) null else crypto.decrypt(f.readBytes())
    }.getOrNull()

    fun loadPreferred(userId: String): ByteArray? = load(userId, Kind.GUIDED) ?: load(userId, Kind.GALLERY)

    fun delete(userId: String) { Kind.entries.forEach { file(userId, it).delete() } }

    private fun file(userId: String, kind: Kind): File = File(folder, "${accountKey(userId)}.${kind.suffix}.bin")
    private fun accountKey(userId: String): String = MessageDigest.getInstance("SHA-256")
        .digest(userId.trim().toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
