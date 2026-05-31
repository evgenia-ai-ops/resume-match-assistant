package com.example.api

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

object GeminiClient {
    private const val TAG = "GeminiClient"
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"

    suspend fun analyzeJobMatch(resumeText: String, jobDescription: String): MatchAnalysisResult = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("Gemini API key is not configured. Please add it via the Secrets panel in AI Studio.")
        }

        val prompt = """
You are Job Match Analyzer, an experienced recruiter and expert hiring manager evaluating candidate fit against a job description. 
Analyze the match between the resume and job description. Provide realistic, non-flattering, evidence-based recruiter analysis.

RESUME CONTENT:
$resumeText

JOB DESCRIPTION:
$jobDescription

Please construct your response exactly as the following JSON structure:
{
  "roleSummary": ["A bullet point summarizing the role", "Another bullet point"],
  "matchScore": 82,
  "fitRating": 8.2,
  "whyMatches": ["Strength bullet 1", "Strength bullet 2"],
  "gapsRisks": ["Gap/concern bullet 1", "Gap/concern bullet 2"],
  "transferableAdvantages": ["Transferable advantage bullet 1", "Transferable advantage bullet 2"],
  "hiringManagerViewOption": "Likely shortlist", 
  "hiringManagerViewExplanation": "Hiring manager's explanation summary",
  "resumeOptimization": ["Optimization tip 1", "Optimization tip 2"],
  "positioningPitch": "A 3-5 line candidate elevator pitch positioning them for this specific role."
}

For "hiringManagerViewOption", choose exactly one of: "Likely shortlist", "Borderline", or "Low probability".
The "matchScore" is an integer from 0 to 100.
The "fitRating" is a decimal or double from 0.0 to 10.0.

Your return format must be ONLY a valid single JSON object matching the schema above. Do not wrap in markdown tags like ```json or similar. Just return pure JSON.
""".trimIndent()

        val contentObject = JSONObject().apply {
            put("parts", JSONArray().put(JSONObject().apply {
                put("text", prompt)
            }))
        }
        val contentsArray = JSONArray().put(contentObject)
        
        val generationConfig = JSONObject().apply {
            put("responseMimeType", "application/json")
            put("temperature", 0.1)
        }

        val postData = JSONObject().apply {
            put("contents", contentsArray)
            put("generationConfig", generationConfig)
        }

        val requestBody = postData.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val url = "$BASE_URL?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: "Empty body"
                Log.e(TAG, "Unsuccessful response: Code: ${response.code}, Msg: ${response.message}, Body: $errBody")
                throw IOException("API returned error code ${response.code}: $errBody")
            }

            val respString = response.body?.string() ?: throw IOException("Empty response from AI service")
            Log.d(TAG, "Raw Response: $respString")

            parseMatchResponse(respString)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to analyze match", e)
            throw e
        }
    }

    suspend fun generateCoverLetter(resumeText: String, jobDescription: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("API key is not configured. Please add it via the Secrets panel in AI Studio.")
        }

        val prompt = """
You are Job Match Analyzer. Write a highly tailored, polished, short, and persuasive cover letter on behalf of the candidate for this specific role.
Be professional, concise, and focused on why their specific skills (matching the job description) make them a strong candidate. Keep it short (2-3 paragraphs max).

RESUME:
$resumeText

JOB DESCRIPTION:
$jobDescription

Provide only the text of the cover letter with professional spacing. Keep it ready to be copied.
""".trimIndent()

        val contentObject = JSONObject().apply {
            put("parts", JSONArray().put(JSONObject().apply {
                put("text", prompt)
            }))
        }
        val contentsArray = JSONArray().put(contentObject)

        val postData = JSONObject().apply {
            put("contents", contentsArray)
        }

        val requestBody = postData.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val url = "$BASE_URL?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: "Empty body"
                throw IOException("API error ${response.code}: $errBody")
            }
            val respString = response.body?.string() ?: throw IOException("Empty response")
            val json = JSONObject(respString)
            val text = json.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
            text
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate cover letter", e)
            "Error generating cover letter: ${e.localizedMessage}"
        }
    }

    suspend fun extractTextFromDocument(mimeType: String, base64Data: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("API key is not configured. Please add it via the Secrets panel in AI Studio.")
        }

        val prompt = "Transcribe the visible text inside this document directly into structured plain text. Clean up formatting and fix typos if necessary. Do not add comments, only output the text contents of the resume or job description."

        val partText = JSONObject().apply { put("text", prompt) }
        val partDoc = JSONObject().apply {
            put("inlineData", JSONObject().apply {
                put("mimeType", mimeType)
                put("data", base64Data)
            })
        }
        val partsArray = JSONArray().apply {
            put(partText)
            put(partDoc)
        }
        val contentObject = JSONObject().apply { put("parts", partsArray) }
        val contentsArray = JSONArray().put(contentObject)

        val postData = JSONObject().apply {
            put("contents", contentsArray)
        }

        val requestBody = postData.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val url = "$BASE_URL?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: "Empty body"
                throw IOException("API returned error code ${response.code}: $errBody")
            }
            val respString = response.body?.string() ?: throw IOException("Empty response")
            val json = JSONObject(respString)
            val text = json.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
            text
        } catch (e: Exception) {
            Log.e(TAG, "Document extract failed for $mimeType", e)
            throw e
        }
    }

    suspend fun extractTextFromImage(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val base64Data = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        return extractTextFromDocument("image/jpeg", base64Data)
    }

    private fun parseMatchResponse(jsonResponse: String): MatchAnalysisResult {
        try {
            val json = JSONObject(jsonResponse)
            val candidates = json.getJSONArray("candidates")
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.getJSONObject("content")
            val parts = content.getJSONArray("parts")
            var text = parts.getJSONObject(0).getString("text").trim()

            if (text.startsWith("```json")) {
                text = text.removePrefix("```json")
            }
            if (text.startsWith("```")) {
                text = text.removePrefix("```")
            }
            if (text.endsWith("```")) {
                text = text.removeSuffix("```")
            }
            text = text.trim()

            val resultObj = JSONObject(text)

            fun jsonArrayToStreamList(arr: JSONArray?): List<String> {
                if (arr == null) return emptyList()
                val list = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    list.add(arr.getString(i))
                }
                return list
            }

            return MatchAnalysisResult(
                roleSummary = jsonArrayToStreamList(resultObj.optJSONArray("roleSummary")),
                matchScore = resultObj.optInt("matchScore", 0),
                fitRating = resultObj.optDouble("fitRating", 0.0),
                whyMatches = jsonArrayToStreamList(resultObj.optJSONArray("whyMatches")),
                gapsRisks = jsonArrayToStreamList(resultObj.optJSONArray("gapsRisks")),
                transferableAdvantages = jsonArrayToStreamList(resultObj.optJSONArray("transferableAdvantages")),
                hiringManagerViewOption = resultObj.optString("hiringManagerViewOption", "Borderline"),
                hiringManagerViewExplanation = resultObj.optString("hiringManagerViewExplanation", ""),
                resumeOptimization = jsonArrayToStreamList(resultObj.optJSONArray("resumeOptimization")),
                positioningPitch = resultObj.optString("positioningPitch", "")
            )
        } catch (e: Exception) {
            Log.e(TAG, "JSON Parsing breakdown: response: $jsonResponse", e)
            return MatchAnalysisResult(
                roleSummary = listOf("Failed to parse response successfully. Please verify your input and try again."),
                matchScore = 0,
                fitRating = 0.0,
                whyMatches = emptyList(),
                gapsRisks = listOf("Raw response could not be fully parsed into fields."),
                transferableAdvantages = emptyList(),
                hiringManagerViewOption = "Borderline",
                hiringManagerViewExplanation = "Analysis complete, but parsing failed. Raw: $jsonResponse",
                resumeOptimization = emptyList(),
                positioningPitch = ""
            )
        }
    }
}
