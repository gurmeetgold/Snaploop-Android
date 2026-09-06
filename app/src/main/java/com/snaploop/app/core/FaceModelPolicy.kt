package com.snaploop.app.core

object FaceModelPolicy {
    const val CURRENT_VERSION = 5
    const val MODEL_IDENTIFIER = "auraface-v1-coreml-fp16"
    const val SOURCE_MODEL = "fal/AuraFace-v1 (glintr100.onnx)"
    const val SOURCE_MODEL_SHA256 = "a7933ea5330113b01c9b60351d8f4c33003f145d8470ac5f0e52ee2effe25c60"
    const val SCAN_GENERATION = "face-v5.2"
    const val EMBEDDING_DIMENSION = 512
    const val TARGET_TEMPLATE_COUNT = 5
    const val EVALUATION_MATCH_THRESHOLD = 0.52
    const val EVALUATION_AMBIGUITY_MARGIN = 0.08
    const val CORROBORATED_BEST_TEMPLATE_SLACK = 0.04
    const val SUPPORTING_TEMPLATE_SLACK = 0.06
    const val STRONG_SINGLE_TEMPLATE_BONUS = 0.10
    const val MINIMUM_RECOGNITION_FACE_PIXELS = 42
    const val MAXIMUM_RECOGNITION_YAW_DEGREES = 55.0
    const val MINIMUM_CAPTURE_QUALITY = 0.20
    const val IS_PRODUCTION_VALIDATED = false
}
