package com.snaploop.app.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.snaploop.app.core.FirebaseErrorMapper
import com.snaploop.app.core.SnapLoopException
import com.snaploop.app.core.awaitResult
import com.snaploop.app.domain.SnapUser

class FirebaseUserDirectory(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val callable: FirebaseCallableClient = FirebaseCallableClient(),
) {
    suspend fun fetch(userId: String): SnapUser = try {
        val snapshot = db.collection("users").document(userId).get().awaitResult()
        val data = snapshot.data ?: throw SnapLoopException.InvalidData("User document does not exist")
        SnapUser(
            id = snapshot.id,
            phoneNumber = data["phoneNumber"] as? String ?: throw SnapLoopException.InvalidData("User is missing phoneNumber"),
            displayName = (data["displayName"] as? String)?.trim()?.takeIf { it.isNotBlank() },
            hasFaceProfile = data["hasFaceProfile"] as? Boolean ?: false,
            createdAtMillis = (data["createdAt"] as? Timestamp)?.toDate()?.time ?: throw SnapLoopException.InvalidData("User is missing createdAt"),
        )
    } catch (e: SnapLoopException) { throw e }
      catch (t: Throwable) { throw FirebaseErrorMapper.firestore(t) }

    suspend fun syncMyProfile(userId: String, displayName: String?) {
        callable.call("syncMyUserProfile", mapOf("userId" to userId, "displayName" to displayName?.trim()?.takeIf { it.isNotBlank() }))
    }

    suspend fun deleteMyAccount(userId: String) { callable.call("deleteMyAccount", mapOf("userId" to userId)) }
}
