package com.vitaltriage.core

import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** One Responses contract for the Windows gateway and the owner's personal Android client. */
object OpenAiProtocol {
    fun request(r: TriageRequest, model: String, prompt: String): JsonObject {
        if (!Safety.valid(r)) throw LiveFailure("INSUFFICIENT_INFORMATION")
        require(model == LiveBudget.MODEL)
        return JsonObject().apply {
            addProperty("model", model); addProperty("store", false)
            addProperty("instructions", prompt)
            addProperty("input", Contract.gson.toJson(mapOf("symptoms" to r.symptoms, "response_language" to r.language,
                "patient_languages" to r.patientLanguages, "reported_immediate_danger" to r.immediateDanger,
                "local_screening" to Safety.localScreening(r))))
            add("text", JsonObject().apply { add("format", JsonObject().apply {
                addProperty("type", "json_schema"); addProperty("name", "triage_assessment")
                addProperty("strict", true); add("schema", Contract.schema().apply {
                    getAsJsonObject("properties").getAsJsonObject("language").add("enum", Contract.gson.toJsonTree(listOf(r.language)))
                    getAsJsonObject("properties").getAsJsonObject("riskColor").add("enum", Contract.gson.toJsonTree(Urgency.entries.map { it.color }))
                })
            }) })
            addProperty("max_output_tokens", LiveBudget.OUTPUT_LIMIT)
            addProperty("service_tier", "default")
        }.also {
            if (it.toString().toByteArray(Charsets.UTF_8).size + 4096 > LiveBudget.INPUT_LIMIT) throw LiveFailure("INPUT_LIMIT")
        }
    }
    fun httpError(status: Int, root: JsonObject?): String {
        val code = runCatching { root?.getAsJsonObject("error")?.get("code")?.asString }.getOrNull()
        return when {
            status == 401 -> "OPENAI_AUTH"
            code == "insufficient_quota" -> "OPENAI_CREDIT"
            status == 403 -> "OPENAI_PERMISSION"
            status == 404 || code == "model_not_found" -> "OPENAI_MODEL"
            status == 429 -> "OPENAI_RATE_LIMIT"
            else -> "OPENAI_REQUEST"
        }
    }
    fun decode(raw: String, language: String): Assessment {
        val root = JsonParser.parseString(raw).asJsonObject
        check(root["status"]?.asString == "completed")
        val texts = root["output"].asJsonArray.flatMap { item ->
            val o = item.asJsonObject
            if (o["type"]?.asString != "message") emptyList() else o["content"].asJsonArray.map { c ->
                val part = c.asJsonObject
                check(part["type"].asString == "output_text")
                part["text"].asString
            }
        }
        check(texts.size == 1)
        val value = JsonParser.parseString(texts.single()).asJsonObject
        fun reject(condition: Boolean, code: String) { if (!condition) throw LiveFailure(code) }
        reject(value["language"]?.asString == language, "OPENAI_WRONG_LANGUAGE")
        val urgency = runCatching { Urgency.valueOf(value["urgency"].asString) }.getOrNull()
        reject(urgency != null, "OPENAI_INVALID_URGENCY")
        reject(value["riskColor"]?.asString == urgency!!.color, "OPENAI_INVALID_COLOR")
        if (urgency == Urgency.UNDETERMINED) reject(value["criticality"]?.isJsonNull == true, "OPENAI_INVALID_SCORE")
        if (urgency == Urgency.LOW) reject(value["redFlags"]?.asJsonArray?.size() == 0, "OPENAI_CONTRADICTORY_FLAGS")
        return Contract.parse(texts.single(), language)
    }
    fun validate(raw: String, request: TriageRequest): Assessment {
        val result = decode(raw, request.language)
        if (Safety.enforce(result, request) != result) throw LiveFailure("OPENAI_SAFETY_CONFLICT")
        return result
    }
}
