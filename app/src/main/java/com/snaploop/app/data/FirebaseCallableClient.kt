package com.snaploop.app.data

import com.google.firebase.functions.FirebaseFunctions
import com.snaploop.app.core.FirebaseErrorMapper
import com.snaploop.app.core.awaitResult

class FirebaseCallableClient(private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()) {
    suspend fun call(name: String, data: Map<String, Any?> = emptyMap()): Any? = try {
        functions.getHttpsCallable(name).call(data).awaitResult().data
    } catch (t: Throwable) {
        throw FirebaseErrorMapper.functions(t)
    }
}
