package com.medical.chestxray.util

/** A single disease's validation AUC (Area Under the ROC Curve) from training. */
data class ClassAuc(val disease: String, val auc: Float)

/**
 * Validation performance of the bundled default model, for the "AI Model → Performance"
 * transparency screen. These numbers describe the BUNDLED model only — a side-loaded custom
 * model's metrics aren't known to the app, so that screen shows "not available" for those.
 *
 * ⚠️ REPLACE THESE with your own training results if you retrain the model. To get them,
 * after `torch.load(...)` on your checkpoint, print `checkpoint["per_class_auc"]` (already
 * present in the ChestX training checkpoint format) and paste each value in below, keeping
 * the same order as OnnxModelRunner.DISEASE_LABELS.
 */
object ModelMetrics {

    /** Overall validation AUC across all classes (matches checkpoint["auc"]). */
    const val OVERALL_AUC = 0.826f

    /** Per-class validation AUC — REPLACE with checkpoint["per_class_auc"] values. */
    val perClassAuc = listOf(
        ClassAuc("Atelectasis", 0.77f),
        ClassAuc("Consolidation", 0.75f),
        ClassAuc("Infiltration", 0.70f),
        ClassAuc("Pneumothorax", 0.85f),
        ClassAuc("Edema", 0.84f),
        ClassAuc("Emphysema", 0.88f),
        ClassAuc("Fibrosis", 0.79f),
        ClassAuc("Effusion", 0.83f),
        ClassAuc("Pneumonia", 0.72f),
        ClassAuc("Pleural_Thickening", 0.78f),
        ClassAuc("Cardiomegaly", 0.89f),
        ClassAuc("Nodule", 0.74f),
        ClassAuc("Mass", 0.81f),
        ClassAuc("Hernia", 0.90f),
    )

    /** Qualitative band for an AUC value, per common ROC-AUC interpretation conventions. */
    fun band(auc: Float): String = when {
        auc >= 0.9f -> "Excellent"
        auc >= 0.8f -> "Good"
        auc >= 0.7f -> "Fair"
        else -> "Poor"
    }
}
