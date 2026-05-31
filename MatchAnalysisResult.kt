package com.example.api

data class MatchAnalysisResult(
    val roleSummary: List<String>,
    val matchScore: Int,
    val fitRating: Double,
    val whyMatches: List<String>,
    val gapsRisks: List<String>,
    val transferableAdvantages: List<String>,
    val hiringManagerViewOption: String, // "Likely shortlist", "Borderline", "Low probability"
    val hiringManagerViewExplanation: String,
    val resumeOptimization: List<String>,
    val positioningPitch: String
) {
    companion object {
        fun createEmpty(): MatchAnalysisResult {
            return MatchAnalysisResult(
                roleSummary = emptyList(),
                matchScore = 0,
                fitRating = 0.0,
                whyMatches = emptyList(),
                gapsRisks = emptyList(),
                transferableAdvantages = emptyList(),
                hiringManagerViewOption = "Borderline",
                hiringManagerViewExplanation = "Provide a resume and job description to begin.",
                resumeOptimization = emptyList(),
                positioningPitch = ""
            )
        }
    }
}
