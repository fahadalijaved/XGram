/*
 * This file is a part of XGram, a fork of Telegram X
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.fahadali.xgram.xdrive

import com.fahadali.xgram.XgramSettings
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.thunderdog.challegram.R
import org.thunderdog.challegram.core.Lang
import java.util.concurrent.TimeUnit

/** The cloud AIs Organize can use with the user's own API key. */
enum class XDriveCloudProvider(val id: String, val title: Int, val defaultModel: String, val keyHelp: Int) {
  CLAUDE("claude", R.string.XgramXDriveCloudClaude, "claude-opus-5-5", R.string.XgramXDriveCloudClaudeHelp),
  GEMINI("gemini", R.string.XgramXDriveCloudGemini, "gemini-flash-latest", R.string.XgramXDriveCloudGeminiHelp),
  OPENAI("openai", R.string.XgramXDriveCloudOpenAi, "", R.string.XgramXDriveCloudOpenAiHelp);

  companion object {
    fun of(id: String?): XDriveCloudProvider? = entries.firstOrNull { it.id == id }
  }
}

/** A provider set up with a key (and model, base URL); null from [load]: none is. */
class XDriveCloudConfig(val provider: XDriveCloudProvider, val key: String, val model: String, val baseUrl: String) {
  val name: String get() = Lang.getString(provider.title)

  companion object {
    const val OPENAI_BASE_URL = "https://api.openai.com/v1"

    fun load(): XDriveCloudConfig? {
      val settings = XgramSettings.instance()
      val provider = XDriveCloudProvider.of(settings.xdriveAiProvider()) ?: return null
      val key = settings.xdriveAiKey(provider.id).trim()
      val model = settings.xdriveAiModel(provider.id).trim().ifEmpty { provider.defaultModel }
      if (key.isEmpty() || model.isEmpty()) return null
      val base = settings.xdriveAiBaseUrl().trim().ifEmpty { OPENAI_BASE_URL }.trimEnd('/')
      return XDriveCloudConfig(provider, key, model, base)
    }
  }
}

/** Why a cloud answer didn't come (shown to the user as it is). */
class XDriveCloudException(message: String) : Exception(message)

/** One answer from the cloud AI over HTTPS (blocking: call it off the main thread). */
object XDriveCloud {
  private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

  /** Claude models that take output_config.effort, and those that take the server-side refusal fallback. */
  private val CLAUDE_EFFORT = Regex("^claude-(opus-5|opus-4-[5-8]|sonnet-5|sonnet-4-6|haiku-5|fable|mythos)")
  private val CLAUDE_FALLBACK = setOf("claude-fable-5-1", "claude-opus-5-5", "claude-opus-5", "claude-sonnet-5-5")

  private val client: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout(20, TimeUnit.SECONDS)
      .readTimeout(150, TimeUnit.SECONDS)
      .writeTimeout(30, TimeUnit.SECONDS)
      .callTimeout(180, TimeUnit.SECONDS)
      .build()
  }

  fun generate(config: XDriveCloudConfig, prompt: String): String = when (config.provider) {
    XDriveCloudProvider.CLAUDE -> claude(config, prompt)
    XDriveCloudProvider.GEMINI -> gemini(config, prompt)
    XDriveCloudProvider.OPENAI -> openAi(config, prompt)
  }

  /** The Messages API: a short classification, so low effort; the refusal fallback where the model has one. */
  private fun claude(config: XDriveCloudConfig, prompt: String): String {
    val body = JSONObject()
      .put("model", config.model)
      .put("max_tokens", 16000)
      .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
    if (CLAUDE_EFFORT.containsMatchIn(config.model)) {
      body.put("output_config", JSONObject().put("effort", "low"))
    }
    val request = Request.Builder()
      .url("https://api.anthropic.com/v1/messages")
      .header("x-api-key", config.key)
      .header("anthropic-version", "2023-06-01")
    if (config.model in CLAUDE_FALLBACK) {
      body.put("fallbacks", "default")
      request.header("anthropic-beta", "server-side-fallback-2026-07-01")
    }
    val json = call(config, request.post(body.toString().toRequestBody(JSON_TYPE)).build())
    if (json.optString("stop_reason") == "refusal") {
      throw XDriveCloudException(Lang.getString(R.string.XgramXDriveCloudDeclined, config.name))
    }
    val content = json.optJSONArray("content") ?: JSONArray()
    val text = StringBuilder()
    for (i in 0 until content.length()) {
      val block = content.optJSONObject(i) ?: continue
      if (block.optString("type") == "text") text.append(block.optString("text"))
    }
    return text.toString()
  }

  private fun gemini(config: XDriveCloudConfig, prompt: String): String {
    val body = JSONObject()
      .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
      .put("generationConfig", JSONObject().put("temperature", 0.2).put("maxOutputTokens", 8192))
    val request = Request.Builder()
      .url("https://generativelanguage.googleapis.com/v1beta/models/" + config.model + ":generateContent")
      .header("x-goog-api-key", config.key)
      .post(body.toString().toRequestBody(JSON_TYPE))
      .build()
    val json = call(config, request)
    val blocked = json.optJSONObject("promptFeedback")?.optString("blockReason")
    if (!blocked.isNullOrEmpty()) {
      throw XDriveCloudException(Lang.getString(R.string.XgramXDriveCloudDeclined, config.name))
    }
    val parts = json.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
    val text = StringBuilder()
    for (i in 0 until parts.length()) {
      val part = parts.optJSONObject(i) ?: continue
      if (!part.optBoolean("thought", false)) text.append(part.optString("text"))
    }
    return text.toString()
  }

  /** Chat Completions, which OpenAI, OpenRouter, Groq, DeepSeek and local servers all speak. */
  private fun openAi(config: XDriveCloudConfig, prompt: String): String {
    val body = JSONObject()
      .put("model", config.model)
      .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
    val request = Request.Builder()
      .url(config.baseUrl + "/chat/completions")
      .header("Authorization", "Bearer " + config.key)
      .post(body.toString().toRequestBody(JSON_TYPE))
      .build()
    val json = call(config, request)
    return json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content") ?: ""
  }

  /** Runs the request; an error status (or an unreadable answer) becomes an exception with the service's own words. */
  private fun call(config: XDriveCloudConfig, request: Request): JSONObject {
    try {
      client.newCall(request).execute().use { response ->
        val text = response.body?.string() ?: ""
        val json = try {
          JSONObject(text)
        } catch (t: Throwable) {
          null
        }
        if (!response.isSuccessful) {
          val error = json?.optJSONObject("error")
          val message = error?.optString("message")?.takeIf { it.isNotEmpty() } ?: json?.optString("message")?.takeIf { it.isNotEmpty() } ?: text.take(160)
          throw XDriveCloudException(config.name + ": " + message + " (HTTP " + response.code + ")")
        }
        return json ?: throw XDriveCloudException(config.name + ": " + Lang.getString(R.string.XgramXDriveCloudBadAnswer))
      }
    } catch (e: XDriveCloudException) {
      throw e
    } catch (e: java.io.IOException) {
      throw XDriveCloudException(config.name + ": " + (e.message ?: e.javaClass.simpleName))
    }
  }

  /** What leaves the phone, made safer: long numbers, e-mail addresses and code-like tokens are masked. */
  fun redact(text: String): String = text
    .replace(Regex("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+"), "[email]")
    .replace(Regex("\\d[\\d -]{5,}\\d"), "[number]")
    .replace(Regex("\\b(?=[A-Za-z0-9_-]*\\d)(?=[A-Za-z0-9_-]*[A-Za-z])[A-Za-z0-9_-]{20,}\\b"), "[code]")
}
