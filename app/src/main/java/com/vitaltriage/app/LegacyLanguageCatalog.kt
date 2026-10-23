package com.vitaltriage.app

import com.vitaltriage.core.Language

/**
 * Derived from RTranslator-2.00 Global.getLanguages: Recognizer ∩ Translator(NLLB).
 * Copyright 2016 Luca Martino, Apache License 2.0. Original notices: NOTICE.md and docs/ui-comparison/VITALVOICE-LICENSE.txt.
 * Data sources: raw/whisper_supported_languages.xml, raw/nllb_supported_languages{,_all}.xml
 * and Recognizer.LANGUAGES. Commented-out Indonesian is intentionally excluded.
 * This catalog does not install models or claim per-language quality.
 */
object LegacyLanguageCatalog {
    private val normal = setOf("ar","bg","ca","zh","cs","da","de","el","en","es","fi","fr","gl","hr","it","ja","ko","mk","nl","pl","pt","ro","ru","sk","sv","ta","th","tr","uk","ur","vi")
    private val extended = setOf("ar","af","am","as","eu","ba","be","bn","bs","bg","ca","zh","cs","da","de","et","el","en","es","fi","fo","gu","ha","he","hi","hu","ka","tl","fr","gl","hr","it","ja","ko","mk","nl","pl","pt","ro","ru","sk","sv","ta","th","tr","uk","ur","vi","kn","kk","lo","ln","lt","lb","bo","fa","km")
    fun tags(allowLow:Boolean):Set<String> = if(allowLow)Language.entries.map { it.tag }.toSet() else normal
    fun selectable(tag:String):Boolean = Language.entries.any { it.tag==tag }
}
