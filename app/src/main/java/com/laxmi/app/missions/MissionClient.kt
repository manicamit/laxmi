package com.laxmi.app.missions

import com.laxmi.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Thin client for the Gemini Interactions API (Managed Agents / Antigravity).
 * One POST per mission; cloud agents get ONLY the mission brief the user
 * approved on the consent sheet — never raw ledger data (PLAN.md §0).
 */
object MissionClient {

    private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/interactions"
    private const val AGENT = "antigravity-preview-05-2026"

    class MissionException(message: String) : Exception(message)

    /** Carries the interaction + environment ids so a follow-up can CONTINUE the
     *  same managed sandbox (Antigravity's persistent-session capability). */
    data class Result(val text: String, val interactionId: String?, val environmentId: String?)

    /**
     * @param previousInteractionId continue a prior interaction's reasoning context
     * @param environmentId reuse a prior sandbox (files/state); null starts a fresh one
     */
    suspend fun run(
        missionPrompt: String,
        previousInteractionId: String? = null,
        environmentId: String? = null,
    ): Result = withContext(Dispatchers.IO) {
        if (BuildConfig.GEMINI_API_KEY.isEmpty()) {
            throw MissionException("No API key configured (local.properties laxmi.geminiApiKey)")
        }
        val body = JSONObject()
            .put("agent", AGENT)
            .put("input", JSONArray().put(JSONObject().put("type", "text").put("text", missionPrompt)))
            .apply {
                if (previousInteractionId != null) put("previous_interaction_id", previousInteractionId)
                // Reuse the sandbox by id, else provision a fresh remote one.
                if (environmentId != null) put("environment", environmentId)
                else put("environment", JSONObject().put("type", "remote"))
            }
            .toString()

        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 180_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", BuildConfig.GEMINI_API_KEY)
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val response = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText().orEmpty()
            if (code !in 200..299) {
                throw MissionException("Mission API $code: ${response.take(300)}")
            }
            val root = JSONObject(response)
            Result(
                text = extractModelOutput(response),
                interactionId = root.optString("id").ifBlank { null },
                environmentId = root.optString("environment_id").ifBlank { null },
            )
        } finally {
            conn.disconnect()
        }
    }

    /** Pull the model_output step's text out of an interactions response. */
    private fun extractModelOutput(responseJson: String): String {
        val root = JSONObject(responseJson)
        val steps = root.optJSONArray("steps") ?: return ""
        for (i in steps.length() - 1 downTo 0) {
            val step = steps.getJSONObject(i)
            if (step.optString("type") == "model_output") {
                val content = step.optJSONArray("content") ?: continue
                val sb = StringBuilder()
                for (j in 0 until content.length()) {
                    val part = content.getJSONObject(j)
                    if (part.optString("type") == "text") sb.append(part.optString("text"))
                }
                return sb.toString()
            }
        }
        return ""
    }
}
