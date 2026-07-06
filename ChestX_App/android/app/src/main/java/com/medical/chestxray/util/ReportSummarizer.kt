package com.medical.chestxray.util

import com.medical.chestxray.data.model.AnalysisResponse
import com.medical.chestxray.data.model.PatientInfo

/**
 * Deterministic, rule-based report summariser. It turns the CNN's probability
 * scores into a concise, cautious paragraph without any ML — so it is instant,
 * works on every device, and can never invent a finding the model didn't report.
 *
 * (Phase 1. A future phase may offer an optional on-device LLM to rephrase this.)
 */
object ReportSummarizer {

    // Probability bands (sigmoid output, in %).
    private const val HIGH = 70f
    private const val MODERATE = 40f

    fun generate(result: AnalysisResponse, patient: PatientInfo?): String {
        val subject = patient?.name
            ?.takeIf { it.isNotBlank() }
            ?.let { "$it's chest radiograph" }
            ?: "the chest radiograph"

        val demographic = buildDemographic(patient)
        val demoClause = if (demographic.isNotBlank()) " ($demographic)" else ""

        val significant = result.predictions
            .filter { it.confidence >= MODERATE }
            .sortedByDescending { it.confidence }

        val sb = StringBuilder()
        sb.append("Automated analysis of $subject$demoClause ")

        if (significant.isEmpty()) {
            sb.append("did not surface any thoracic abnormality at a significant probability. ")
            sb.append("All screened conditions returned low likelihood scores, which suggests the study is likely within normal limits according to the model. ")
        } else {
            val top = significant.first()
            sb.append(
                "indicates a ${band(top.confidence)} likelihood of ${prettify(top.disease)} " +
                        "(${top.confidence.toInt()}% confidence) as the primary finding. "
            )

            val others = significant.drop(1)
            if (others.isNotEmpty()) {
                val list = others.joinToString(", ") { "${prettify(it.disease)} (${it.confidence.toInt()}%)" }
                sb.append("Additional elevated probabilities were noted for $list. ")
            }

            if (top.confidence >= HIGH) {
                sb.append("This finding is pronounced and warrants careful clinical correlation. ")
            } else {
                sb.append("This finding is moderate and should be interpreted alongside clinical context. ")
            }
        }

        sb.append(
            "This summary is generated automatically from the model's probability scores for research " +
                    "purposes only and does not constitute a medical diagnosis."
        )

        return sb.toString()
    }

    private fun buildDemographic(patient: PatientInfo?): String {
        val age = patient?.age?.takeIf { it > 0 }?.let { "$it-year-old" }
        val sex = patient?.sex
            ?.takeIf { it.isNotBlank() && !it.equals("Other", ignoreCase = true) }
            ?.lowercase()
        return listOfNotNull(age, sex).joinToString(" ")
    }

    private fun band(confidence: Float): String = when {
        confidence >= HIGH -> "high"
        confidence >= MODERATE -> "moderate"
        else -> "low"
    }

    /** "Pleural_Thickening" -> "Pleural Thickening". */
    private fun prettify(disease: String): String = disease.replace('_', ' ')
}
