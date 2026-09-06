package com.snaploop.app.scanner

import android.content.Context
import com.snaploop.app.core.FaceModelPolicy
import com.snaploop.app.security.KeystoreAes
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest

/** Encrypted, no-backup persistence for candidate embeddings and recipient cursors. */
class EncryptedScanStateStore(context: Context) {
    private val folder = File(context.noBackupFilesDir, "SnapLoop/ScanState").apply { mkdirs() }
    private val crypto = KeystoreAes("snaploop.scan.state.aes.v1")

    fun load(eventId: String): ScanState {
        val file = file(eventId)
        if (!file.exists()) return ScanState(eventId)
        return runCatching { decode(crypto.decrypt(file.readBytes()), eventId) }.getOrElse {
            file.delete()
            ScanState(eventId)
        }
    }

    fun save(state: ScanState) {
        val destination = file(state.eventId)
        val temp = File(destination.parentFile, destination.name + ".tmp")
        temp.writeBytes(crypto.encrypt(encode(state)))
        if (!temp.renameTo(destination)) {
            destination.writeBytes(temp.readBytes())
            temp.delete()
        }
    }

    fun delete(eventId: String) { file(eventId).delete() }

    private fun encode(state: ScanState): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(ScanState.CURRENT_SCHEMA_VERSION)
            out.writeUTF(state.eventId)
            writeNullable(out, state.sourceMembershipEpoch); writeNullable(out, state.sourceSharingRevision)
            writeNullable(out, state.sourceOwnMatchesRevision); writeNullable(out, state.rosterAmbiguityRevision)
            out.writeLong(state.lastSyncedAtMillis ?: Long.MIN_VALUE)
            out.writeInt(state.photoCorpus.size)
            state.photoCorpus.toSortedMap().values.forEach { record ->
                out.writeUTF(record.assetId); out.writeLong(record.creationDateMillis); out.writeLong(record.processedAtMillis)
                out.writeInt(record.faces.size)
                record.faces.forEach { face ->
                    out.writeDouble(face.sizeFraction); out.writeInt(face.embedding.size); face.embedding.forEach(out::writeFloat)
                }
            }
            out.writeInt(state.recipientCursors.size)
            state.recipientCursors.toSortedMap().values.forEach { cursor ->
                out.writeUTF(cursor.userId); out.writeUTF(cursor.membershipEpoch); out.writeUTF(cursor.faceIdentityId); out.writeUTF(cursor.faceProfileRevision)
                writeSet(out, cursor.positiveAssetIds); writeSet(out, cursor.negativeAssetIds); writeSet(out, cursor.staleAssetIds)
            }
        }
        return bytes.toByteArray()
    }

    private fun decode(bytes: ByteArray, expectedEventId: String): ScanState {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val version = input.readInt(); require(version == ScanState.CURRENT_SCHEMA_VERSION)
            val eventId = input.readUTF(); require(eventId == expectedEventId)
            val state = ScanState(
                eventId = eventId,
                schemaVersion = version,
                sourceMembershipEpoch = readNullable(input),
                sourceSharingRevision = readNullable(input),
                sourceOwnMatchesRevision = readNullable(input),
                rosterAmbiguityRevision = readNullable(input),
                lastSyncedAtMillis = input.readLong().takeUnless { it == Long.MIN_VALUE },
            )
            repeat(input.readInt()) {
                val assetId = input.readUTF(); val creation = input.readLong(); val processed = input.readLong()
                val faces = List(input.readInt()) {
                    val sizeFraction = input.readDouble(); val dimension = input.readInt(); require(dimension == FaceModelPolicy.EMBEDDING_DIMENSION)
                    CachedPhotoFace(FloatArray(dimension) { input.readFloat() }, sizeFraction)
                }
                state.photoCorpus[assetId] = PhotoCorpusRecord(assetId, creation, faces, processed)
            }
            repeat(input.readInt()) {
                val userId = input.readUTF()
                state.recipientCursors[userId] = RecipientMatchCursor(
                    userId, input.readUTF(), input.readUTF(), input.readUTF(),
                    readSet(input), readSet(input), readSet(input),
                )
            }
            return state
        }
    }

    private fun writeNullable(out: DataOutputStream, value: String?) { out.writeBoolean(value != null); if (value != null) out.writeUTF(value) }
    private fun readNullable(input: DataInputStream): String? = if (input.readBoolean()) input.readUTF() else null
    private fun writeSet(out: DataOutputStream, values: Set<String>) { out.writeInt(values.size); values.sorted().forEach(out::writeUTF) }
    private fun readSet(input: DataInputStream): MutableSet<String> = MutableList(input.readInt()) { input.readUTF() }.toMutableSet()
    private fun file(eventId: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(eventId.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(folder, "$digest.bin")
    }
}
