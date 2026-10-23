package com.vitaltriage.core

import com.google.gson.*
import java.util.Locale

enum class Urgency(val color: String) {
    LOW("#176B47"), MEDIUM("#8A5700"), HIGH("#B34700"), CRITICAL("#B42335"), UNDETERMINED("#526277")
}
enum class Scenario { LOW, MEDIUM, HIGH, CRITICAL, UNDETERMINED, MALFORMED, OFFLINE }
data class TriageRequest(
    val symptoms: String, val language: String, val patientLanguages: List<String>,
    val confirmed: Boolean, val immediateDanger: Boolean = false, val scenario: String = "MEDIUM"
)
data class Assessment(
    val assessment: String, val urgency: Urgency, val criticality: Int?,
    val experimental: Boolean, val riskColor: String, val rationale: String,
    val redFlags: List<String>, val missingInformation: List<String>,
    val followUpQuestions: List<String>, val language: String
)
data class Evaluation(val result: Assessment, val mode: String, val failed: Boolean = false)

object Words {
    fun pick(lang: String, en: String, tr: String, fr: String, ar: String) = when(lang) {
        "tr" -> tr; "fr" -> fr; "ar" -> ar; else -> en
    }
    fun unknown(lang: String) = pick(lang, "Unable to determine urgency", "Aciliyet belirlenemedi", "Urgence indéterminée", "تعذر تحديد درجة الاستعجال")
    fun failure(lang: String) = pick(lang, "No reliable result was received. No clinical conclusion has been generated.", "Güvenilir sonuç alınamadı. Klinik sonuç üretilmedi.", "Aucun résultat fiable reçu. Aucune conclusion clinique produite.", "لم تصل نتيجة موثوقة. لم يتم إنشاء استنتاج سريري.")
    fun emergency(lang: String) = pick(lang, "Possible emergency warning. Seek urgent professional help; call your local emergency number if there is immediate danger. Do not wait for this app.", "Olası acil durum uyarısı. Acil profesyonel yardım alın; yakın tehlikede yerel acil numarayı arayın. Uygulamayı beklemeyin.", "Alerte potentiellement urgente. Demandez une aide médicale urgente ; en danger immédiat, appelez les secours locaux. N'attendez pas l'application.", "تحذير من حالة طارئة محتملة. اطلب مساعدة طبية عاجلة واتصل برقم الطوارئ المحلي عند وجود خطر فوري. لا تنتظر التطبيق.")
    fun missing(lang: String) = pick(lang, "Age, duration, severity and relevant medical history", "Yaş, süre, şiddet ve ilgili sağlık geçmişi", "Âge, durée, intensité et antécédents pertinents", "العمر والمدة والشدة والتاريخ الطبي ذي الصلة")
    fun question(lang: String) = pick(lang, "When did symptoms start, how severe are they, and are they getting worse?", "Belirtiler ne zaman başladı, ne kadar şiddetli ve kötüleşiyor mu?", "Quand les symptômes ont-ils commencé, quelle est leur intensité et s'aggravent-ils ?", "متى بدأت الأعراض وما شدتها وهل تزداد سوءًا؟")
}

object Safety {
    // Conservative prototype tripwires, not a diagnostic protocol. Negation may over-trigger.
    private val warnings = listOf("chest pain", "cannot breathe", "can't breathe", "difficulty breathing", "unconscious", "severe bleeding",
        "göğüs ağr", "nefes alam", "bilinç kay", "şiddetli kanama", "douleur thoracique", "ne peux pas respirer", "inconscient",
        "ألم في الصدر", "لا أستطيع التنفس", "فقدان الوعي", "نزيف شديد")
    /** A missing lexical match is never a negative medical finding. */
    fun localScreening(r:TriageRequest):String = if(emergency(r)) "WARNING_MATCH" else if(r.patientLanguages.all { it in setOf("en","tr","fr","ar") }) "NO_LOCAL_MATCH_NOT_ALL_CLEAR" else "LANGUAGE_COVERAGE_UNVERIFIED"
    fun emergency(r: TriageRequest) = r.immediateDanger || warnings.any { r.symptoms.lowercase(Locale.ROOT).contains(it) }
    fun valid(r: TriageRequest) = r.confirmed && r.symptoms.isNotBlank() && r.symptoms.length <= 4000 &&
        Language.entries.any { it.tag == r.language } && r.patientLanguages.isNotEmpty() &&
        r.patientLanguages.size <= Language.entries.size && r.patientLanguages.all { code -> Language.entries.any { it.tag == code } }
    fun insufficient(r: TriageRequest) = r.symptoms.trim().length < 15
    fun unknown(language: String) = Assessment(Words.unknown(language), Urgency.UNDETERMINED, null, true,
        Urgency.UNDETERMINED.color, Words.failure(language), emptyList(), listOf(Words.missing(language)), listOf(Words.question(language)), language)
    fun enforce(a: Assessment, r: TriageRequest): Assessment {
        if (!valid(r) || insufficient(r)) return unknown(r.language)
        if (emergency(r) && a.urgency in listOf(Urgency.LOW, Urgency.MEDIUM)) {
            return unknown(r.language).copy(redFlags = listOf(Words.emergency(r.language)))
        }
        if (a.missingInformation.isNotEmpty() && a.urgency == Urgency.LOW) {
            return unknown(r.language)
        }
        return a
    }
}

object Contract {
    val gson = GsonBuilder().serializeNulls().disableHtmlEscaping().create()
    private val keys = setOf("assessment", "urgency", "criticality", "experimental", "riskColor", "rationale", "redFlags", "missingInformation", "followUpQuestions", "language")
    fun encode(a: Assessment): String = gson.toJson(a)
    fun parse(raw: String, expectedLanguage: String): Assessment {
        require(Language.find(expectedLanguage)!=null)
        require(raw.length <= 32000)
        val o = JsonParser.parseString(raw).asJsonObject
        require(o.keySet() == keys)
        fun string(key: String): String {
            val v = o[key]; require(v.isJsonPrimitive && v.asJsonPrimitive.isString)
            return v.asString.also { require(it.isNotBlank() && it.length <= 2000) }
        }
        fun list(key: String): List<String> {
            require(o[key].isJsonArray)
            return o[key].asJsonArray.also { require(it.size() <= 12) }.map {
                require(it.isJsonPrimitive && it.asJsonPrimitive.isString)
                it.asString.also { s -> require(s.isNotBlank() && s.length <= 1000) }
            }
        }
        val urgency = Urgency.valueOf(string("urgency"))
        val score = o["criticality"].let {
            if(it.isJsonNull) null else {
                require(it.isJsonPrimitive && it.asJsonPrimitive.isNumber)
                require(it.toString().matches(Regex("[0-9]{1,3}")))
                it.asInt.also { n -> require(n in 0..100) }
            }
        }
        require(o["experimental"].isJsonPrimitive && o["experimental"].asJsonPrimitive.isBoolean && o["experimental"].asBoolean)
        require(string("language") == expectedLanguage)
        require(string("riskColor") == urgency.color)
        if(urgency == Urgency.UNDETERMINED) require(score == null)
        val flags = list("redFlags")
        if (urgency == Urgency.LOW) require(flags.isEmpty())
        return Assessment(string("assessment"), urgency, score, true, string("riskColor"), string("rationale"),
            flags, list("missingInformation"), list("followUpQuestions"), expectedLanguage)
    }
    fun request(raw: String): TriageRequest {
        require(raw.length <= 16000)
        val o = JsonParser.parseString(raw).asJsonObject
        require(o.keySet() == setOf("symptoms","language","patientLanguages","confirmed","immediateDanger","scenario"))
        fun str(k:String):String { require(o[k].isJsonPrimitive && o[k].asJsonPrimitive.isString); return o[k].asString }
        fun bool(k:String):Boolean { require(o[k].isJsonPrimitive && o[k].asJsonPrimitive.isBoolean); return o[k].asBoolean }
        val langs = o["patientLanguages"].asJsonArray.map { require(it.isJsonPrimitive && it.asJsonPrimitive.isString); it.asString }
        return TriageRequest(str("symptoms"), str("language"), langs, bool("confirmed"), bool("immediateDanger"), str("scenario")).also { require(Safety.valid(it)) }
    }
    fun schema(): JsonObject {
        val props = JsonObject()
        listOf("assessment","riskColor","rationale").forEach { props.add(it, gson.toJsonTree(mapOf("type" to "string"))) }
        props.add("urgency", gson.toJsonTree(mapOf("type" to "string", "enum" to Urgency.entries.map { it.name })))
        props.add("criticality", gson.toJsonTree(mapOf("type" to listOf("integer","null"), "minimum" to 0, "maximum" to 100)))
        props.add("experimental", gson.toJsonTree(mapOf("type" to "boolean", "enum" to listOf(true))))
        props.add("language", gson.toJsonTree(mapOf("type" to "string", "enum" to Language.entries.map { it.tag })))
        listOf("redFlags","missingInformation","followUpQuestions").forEach { props.add(it, gson.toJsonTree(mapOf("type" to "array", "items" to mapOf("type" to "string")))) }
        return JsonObject().apply {
            addProperty("type","object"); addProperty("additionalProperties",false)
            add("properties",props); add("required",gson.toJsonTree(keys.toList()))
        }
    }
}

object Demo {
    fun evaluate(r: TriageRequest): Evaluation {
        require(Safety.valid(r))
        val s = Scenario.valueOf(r.scenario)
        if(s == Scenario.OFFLINE) throw java.io.IOException("OFFLINE")
        if(s == Scenario.MALFORMED) return Evaluation(Safety.unknown(r.language), "DEMO", true)
        if(Safety.insufficient(r)) return Evaluation(Safety.unknown(r.language), "DEMO")
        val u = Urgency.valueOf(s.name)
        val description = when(u) {
            Urgency.LOW -> Words.pick(r.language, "Demo: mild localized irritation", "Demo: hafif yerel tahriş", "Démo : irritation locale légère", "عرض توضيحي: تهيج موضعي خفيف")
            Urgency.MEDIUM -> Words.pick(r.language, "Demo: symptoms needing medical review", "Demo: tıbbi değerlendirme gereken belirtiler", "Démo : symptômes nécessitant un avis médical", "عرض توضيحي: أعراض تحتاج مراجعة طبية")
            Urgency.HIGH -> Words.pick(r.language, "Demo: potentially urgent condition", "Demo: olası acil durum", "Démo : état potentiellement urgent", "عرض توضيحي: حالة قد تكون عاجلة")
            Urgency.CRITICAL -> Words.pick(r.language, "Demo: emergency warning scenario", "Demo: acil durum uyarı senaryosu", "Démo : scénario d'alerte d'urgence", "عرض توضيحي: سيناريو تحذير طارئ")
            Urgency.UNDETERMINED -> Words.unknown(r.language)
        }
        val a = Assessment(description, u, when(u) { Urgency.LOW -> 15; Urgency.MEDIUM -> 45; Urgency.HIGH -> 75; Urgency.CRITICAL -> 95; else -> null },
            true, u.color, Words.pick(r.language,
                "Synthetic fixture selected for software testing. It does not assess your symptoms.",
                "Yazılım testi için seçilmiş sentetik örnek. Belirtilerinizi değerlendirmez.",
                "Exemple synthétique choisi pour tester le logiciel. Il n'évalue pas vos symptômes.",
                "مثال اصطناعي لاختبار البرنامج. لا يقيّم أعراضك."),
            if(u in listOf(Urgency.HIGH,Urgency.CRITICAL)) listOf(Words.emergency(r.language)) else emptyList(),
            if(u == Urgency.UNDETERMINED) listOf(Words.missing(r.language)) else emptyList(),
            listOf(Words.question(r.language)), r.language)
        return Evaluation(Safety.enforce(Contract.parse(Contract.encode(a), r.language),r), "DEMO")
    }
}
