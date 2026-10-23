package com.vitaltriage.core

import java.util.Locale

/**
 * Data from RTranslator-2.00 raw/nllb_supported_languages_all.xml and
 * Tokenizer.languagesNLLB (Copyright 2016 Luca Martino, Apache-2.0; see NOTICE.md).
 * Whisper capability is checked against the pinned whisper.cpp g_lang table.
 * Capability is not installed-model readiness or per-language quality validation.
 * NLLB weights have their own CC-BY-NC-4.0 license.
 */
enum class Language(val tag:String, val speechTag:String, val nllbCode:String,
    val nllbId:Long, val whisperTag:String?) {
    EN("en", "en-US", "eng_Latn", 256047L, "en"),
    TR("tr", "tr-TR", "tur_Latn", 256184L, "tr"),
    FR("fr", "fr-FR", "fra_Latn", 256057L, "fr"),
    AR("ar", "ar-SA", "arb_Arab", 256011L, "ar"),
    AF("af", "af", "afr_Latn", 256006L, "af"),
    AK("ak", "ak", "aka_Latn", 256008L, null),
    AM("am", "am", "amh_Ethi", 256009L, "am"),
    AS("as", "as", "asm_Beng", 256015L, "as"),
    BA("ba", "ba", "bak_Cyrl", 256021L, "ba"),
    BE("be", "be", "bel_Cyrl", 256024L, "be"),
    BG("bg", "bg-BG", "bul_Cyrl", 256033L, "bg"),
    BM("bm", "bm", "bam_Latn", 256022L, null),
    BN("bn", "bn", "ben_Beng", 256026L, "bn"),
    BO("bo", "bo", "bod_Tibt", 256030L, "bo"),
    BS("bs", "bs", "bos_Latn", 256031L, "bs"),
    CA("ca", "ca-ES", "cat_Latn", 256034L, "ca"),
    CS("cs", "cs", "ces_Latn", 256036L, "cs"),
    DA("da", "da-DK", "dan_Latn", 256041L, "da"),
    DE("de", "de-DE", "deu_Latn", 256042L, "de"),
    DZ("dz", "dz", "dzo_Tibt", 256045L, null),
    EE("ee", "ee", "ewe_Latn", 256051L, null),
    EL("el", "el", "ell_Grek", 256046L, "el"),
    EO("eo", "eo", "epo_Latn", 256048L, null),
    ES("es", "es", "spa_Latn", 256161L, "es"),
    ET("et", "et", "est_Latn", 256049L, "et"),
    EU("eu", "eu", "eus_Latn", 256050L, "eu"),
    FA("fa", "fa", "pes_Arab", 256053L, "fa"),
    FI("fi", "fi-FI", "fin_Latn", 256055L, "fi"),
    FJ("fj", "fj", "fij_Latn", 256054L, null),
    FO("fo", "fo", "fao_Latn", 256052L, "fo"),
    GA("ga", "ga", "gle_Latn", 256061L, null),
    GL("gl", "gl-ES", "glg_Latn", 256062L, "gl"),
    GN("gn", "gn", "grn_Latn", 256063L, null),
    GU("gu", "gu", "guj_Gujr", 256064L, "gu"),
    HA("ha", "ha", "hau_Latn", 256066L, "ha"),
    HE("he", "he", "heb_Hebr", 256067L, "he"),
    HI("hi", "hi", "hin_Deva", 256068L, "hi"),
    HR("hr", "hr-HR", "hrv_Latn", 256070L, "hr"),
    HU("hu", "hu", "hun_Latn", 256071L, "hu"),
    IT("it", "it", "ita_Latn", 256077L, "it"),
    JA("ja", "ja-JP", "jpn_Jpan", 256079L, "ja"),
    JV("jv", "jv", "jav_Latn", 256078L, "jw"),
    KA("ka", "ka", "kat_Geor", 256086L, "ka"),
    KI("ki", "ki", "kik_Latn", 256093L, null),
    KK("kk", "kk", "kaz_Cyrl", 256089L, "kk"),
    KM("km", "km", "khm_Khmr", 256092L, "km"),
    KN("kn", "kn", "kan_Knda", 256083L, "kn"),
    KO("ko", "ko-KR", "kor_Hang", 256098L, "ko"),
    KS("ks", "ks", "kas_Arab", 256084L, null),
    KY("ky", "ky", "kir_Cyrl", 256095L, null),
    LB("lb", "lb", "ltz_Latn", 256108L, "lb"),
    LI("li", "li", "lim_Latn", 256103L, null),
    LN("ln", "ln", "lin_Latn", 256104L, "ln"),
    LO("lo", "lo", "lao_Laoo", 256100L, "lo"),
    LT("lt", "lt", "lit_Latn", 256105L, "lt"),
    MK("mk", "mk", "mkd_Cyrl", 256118L, "mk"),
    NL("nl", "nl-NL", "nld_Latn", 256127L, "nl"),
    PL("pl", "pl", "pol_Latn", 256140L, "pl"),
    PT("pt", "pt-PT", "por_Latn", 256141L, "pt"),
    RO("ro", "ro", "ron_Latn", 256145L, "ro"),
    RU("ru", "ru", "rus_Cyrl", 256147L, "ru"),
    RW("rw", "rw", "kin_Latn", 256094L, null),
    SK("sk", "sk", "slk_Latn", 256154L, "sk"),
    SV("sv", "sv", "swe_Latn", 256167L, "sv"),
    TA("ta", "ta", "tam_Taml", 256170L, "ta"),
    TH("th", "th", "tha_Thai", 256175L, "th"),
    TL("tl", "tl", "tgl_Latn", 256174L, "tl"),
    UK("uk", "uk", "ukr_Cyrl", 256188L, "uk"),
    UR("ur", "ur", "urd_Arab", 256190L, "ur"),
    VI("vi", "vi", "vie_Latn", 256193L, "vi"),
    ZH("zh", "zh-CN", "zho_Hans", 256200L, "zh");
    val label:String get() = when(this) {
        EN -> "English"; TR -> "Türkçe"; FR -> "Français"; AR -> "العربية"
        ZH -> "中文（简体）"
        else -> Locale.forLanguageTag(tag).getDisplayLanguage(Locale.forLanguageTag(tag))
    }
    /** Interface localization is deliberately separate from processing languages. */
    val uiIndex:Int get() = when(this) { EN->0;TR->1;FR->2;AR->3;else->0 }
    companion object {
        val uiLanguages = listOf(EN,TR,FR,AR)
        fun find(tag:String?):Language? = entries.firstOrNull { it.tag.equals(tag,ignoreCase=true) }
        fun from(tag:String):Language = find(tag) ?: throw IllegalArgumentException("Unknown language code")
        /** Explicit aliases only. Never discard an unknown region or script subtag. */
        fun fromSpeechTag(tag:String?):Language? = find(tag)
            ?: entries.firstOrNull { it.speechTag.equals(tag,ignoreCase=true) }
            ?: when(tag?.lowercase(Locale.ROOT)) { "en-gb"->EN; "fr-ca"->FR; "pt-br"->PT; "iw-il"->HE; else->null }
        fun fromWhisper(tag:String):Language? = entries.firstOrNull { it.whisperTag==tag }
        fun matchesVoice(language:Language,locale:Locale):Boolean {
            if(locale.language!=language.tag)return false
            // The legacy zh mapping explicitly targets simplified Chinese, not Hant.
            return language!=ZH || (locale.script!="Hant" && locale.country !in setOf("TW","HK","MO"))
        }
    }
}
