# Firebase and ONNX ship consumer rules, but ONNX Runtime's native JNI layer
# constructs Java API objects (for example NodeInfo/ValueInfo) by exact class
# and method signature. R8 can otherwise remove or rename members that are only
# referenced from native code, causing release-only NoSuchMethodError crashes.
-keep class ai.onnxruntime.** { *; }
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Preserve attributes needed for reflection/serialization.
-keepattributes Signature,*Annotation*

# Do not enable verbose logging or preserve debug-only biometric diagnostics in release.
