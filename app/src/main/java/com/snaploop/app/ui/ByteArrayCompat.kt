package com.snaploop.app.ui

/** Primitive-array counterpart to Kotlin's nullable collection helper. */
internal fun ByteArray?.isNullOrEmpty(): Boolean = this == null || this.isEmpty()
