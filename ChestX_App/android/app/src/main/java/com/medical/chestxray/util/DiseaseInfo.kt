package com.medical.chestxray.util

/**
 * Plain-language, educational descriptions of each finding the model can detect.
 * Shown via the "Learn more" affordance on the Diagnostic Report — not medical advice.
 */
object DiseaseInfo {

    private val descriptions = mapOf(
        "Atelectasis" to "Partial or complete collapse of a lung or a section of it, often " +
                "caused by a blocked airway, which prevents that area from filling normally with air.",
        "Consolidation" to "Lung tissue that has filled with fluid or other material instead of " +
                "air — commonly seen alongside pneumonia or bleeding into the lung.",
        "Infiltration" to "An abnormal buildup of fluid, cells, or other material within the lung " +
                "tissue, often an early radiographic sign of infection or inflammation.",
        "Pneumothorax" to "Air trapped in the space between the lung and the chest wall, which " +
                "can cause part or all of the lung to collapse.",
        "Edema" to "Fluid accumulation in the lung tissue, most often caused by the heart not " +
                "pumping efficiently (pulmonary edema).",
        "Emphysema" to "Long-term damage to the lungs' air sacs that reduces their ability to " +
                "exchange oxygen, commonly associated with long-term smoking.",
        "Fibrosis" to "Scarring and thickening of lung tissue, which makes the lungs stiffer and " +
                "less able to expand fully.",
        "Effusion" to "Excess fluid collecting in the space between the lung and the chest wall " +
                "(the pleural space).",
        "Pneumonia" to "An infection that inflames the air sacs in one or both lungs, which may " +
                "fill with fluid or pus.",
        "Pleural_Thickening" to "Thickening of the lining surrounding the lungs, often resulting " +
                "from earlier inflammation, infection, or exposure to irritants such as asbestos.",
        "Cardiomegaly" to "An enlarged heart silhouette on the X-ray, which can indicate the " +
                "heart is working harder than normal to pump blood.",
        "Nodule" to "A small, round spot in the lung tissue (under about 3 cm). Most are benign " +
                "scar tissue, but some warrant further evaluation.",
        "Mass" to "A larger abnormal growth in the lung tissue (3 cm or more), which typically " +
                "needs further imaging or biopsy to characterize.",
        "Hernia" to "Displacement of an abdominal organ or tissue through the diaphragm into the " +
                "chest cavity."
    )

    /** Plain-language description for a disease label, or a generic fallback if unknown. */
    fun describe(disease: String): String =
        descriptions[disease] ?: "No additional information is available for this finding."
}
