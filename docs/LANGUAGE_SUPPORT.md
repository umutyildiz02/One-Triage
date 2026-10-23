# Language capabilities — verified scope, 23 October 2026

Final polish results: unchanged 71-language registry; final Android regression 60/60 and normal offline model smoke 2/2 passed. The actual model suite retained three quality failures, including the original Japanese/Korean cases. A real DE→TR walkthrough also introduced female gender for neutral “person” and changed ongoing duration to past tense. Do not generalize a short passing German sample to every German translation. Original review and original-text assessment remain intact. Final budget is 22/100, USD 0.0749696 conservative. See current local handoff (excluded from this source backup) and local machine-readable results (excluded from this source backup).

Current policy update: the owner has authorized **100 total API attempts**, preserving all earlier attempts and unchanged USD 3/5 safeguards. Counts such as21/23 below are dated prior-run evidence, not the current maximum. See HANDOFF.md for the final ledger and latest run. The registry/model mappings and failed JA/KO quality expectations are unchanged in the polish pass.

The former four-element core Language enum controlled the catalog, utterance acceptance, Whisper allow-list, tokenizer mapping and OpenAI schema together. UiCopy also used enum ordinals. Those application restrictions have been replaced by one explicit processing registry, separate from the four available interface localizations. No new model or model-quality claim follows from adding a code.

## Independent roles

- Interface: EN/TR/FR/AR only, with explicit safe table indexing.
- Input: selected source/speech language, recorded per message. Only that original message is submitted for assessment after review and explicit consent.
- Translation target: independently persisted; used by WalkieTalkie and Conversation. Same-language messages bypass inference.
- Assessment result: independently persisted; strict schema requires the exact requested registered code. Migration retains the prior combined choice without deleting preferences.

Unknown codes are rejected. Only explicit speech locale aliases are mapped; arbitrary region/script subtags are never trimmed. Legacy simplified Chinese maps to zho_Hans; Hant voices/unknown variants are not silently accepted. Javanese jv maps explicitly to Whisper jw.

## Provenance and scope

Read-only RTranslator-2.00 raw/nllb_supported_languages_all.xml and translation/Tokenizer.java provide the 71 enabled entries and FLORES token order (256001 + index). Commented-out Indonesian is not treated as supported. Pinned whisper.cpp g_lang has 58 of these codes. The normal original 31-language filter is retained; the expanded catalog also permits typed-only languages without pretending Whisper supports them. Apache-2.0 source notices remain; NLLB weights remain separately CC-BY-NC-4.0.

TTS is conditional on the actual device voice, offline readiness and original quality preference. Lack of a voice does not disable typed input, translation or assessment. ML Kit support is its runtime catalog intersected with this registry; unsupported pairs direct the user to NLLB. Model download/preparation is explicit.

## Capability matrix (not runtime or quality proof)

Every row has a valid NLLB tokenizer mapping and strict OpenAI language-field plumbing. This does not establish provider quality, installed models or clinical accuracy. Actual translation/speech/TTS results are recorded separately below; mapping is not quality proof.

| Canonical tag | NLLB / token | Whisper code | Runtime/quality |
|---|---|---|---|
| ar | arb_Arab / 256011 | ar | Original native speech regressions passed; TTS installed; prior NLLB evidence retained |
| af | afr_Latn / 256006 | af | Mapping tested; actual language quality unverified |
| ak | aka_Latn / 256008 | No — typed input remains available | Mapping tested; actual language quality unverified |
| am | amh_Ethi / 256009 | am | Mapping tested; actual language quality unverified |
| as | asm_Beng / 256015 | as | Mapping tested; actual language quality unverified |
| eu | eus_Latn / 256050 | eu | Mapping tested; actual language quality unverified |
| ba | bak_Cyrl / 256021 | ba | Mapping tested; actual language quality unverified |
| bm | bam_Latn / 256022 | No — typed input remains available | Mapping tested; actual language quality unverified |
| be | bel_Cyrl / 256024 | be | Mapping tested; actual language quality unverified |
| bn | ben_Beng / 256026 | bn | Mapping tested; actual language quality unverified |
| bs | bos_Latn / 256031 | bs | Mapping tested; actual language quality unverified |
| bg | bul_Cyrl / 256033 | bg | One NLLB→EN meaning sample passed; reverse produced text; speech/TTS not run |
| ca | cat_Latn / 256034 | ca | One NLLB→EN meaning sample passed; reverse produced text; speech/TTS not run |
| zh | zho_Hans / 256200 | zh | Mapping tested; actual language quality unverified |
| cs | ces_Latn / 256036 | cs | Mapping tested; actual language quality unverified |
| da | dan_Latn / 256041 | da | One NLLB→EN meaning sample passed; reverse produced text; speech/TTS not run |
| dz | dzo_Tibt / 256045 | No — typed input remains available | Mapping tested; actual language quality unverified |
| de | deu_Latn / 256042 | de | One NLLB→EN meaning sample passed; reverse produced text; real manual/auto Whisper passed; TTS installed |
| eo | epo_Latn / 256048 | No — typed input remains available | Mapping tested; actual language quality unverified |
| et | est_Latn / 256049 | et | Mapping tested; actual language quality unverified |
| ee | ewe_Latn / 256051 | No — typed input remains available | Mapping tested; actual language quality unverified |
| el | ell_Grek / 256046 | el | Mapping tested; actual language quality unverified |
| en | eng_Latn / 256047 | en | Original native speech regressions passed; TTS installed; prior NLLB evidence retained |
| es | spa_Latn / 256161 | es | Mapping tested; actual language quality unverified |
| fi | fin_Latn / 256055 | fi | One NLLB→EN meaning sample passed; reverse produced text; speech/TTS not run |
| fo | fao_Latn / 256052 | fo | Mapping tested; actual language quality unverified |
| fj | fij_Latn / 256054 | No — typed input remains available | Mapping tested; actual language quality unverified |
| gu | guj_Gujr / 256064 | gu | Mapping tested; actual language quality unverified |
| ha | hau_Latn / 256066 | ha | Mapping tested; actual language quality unverified |
| he | heb_Hebr / 256067 | he | Mapping tested; actual language quality unverified |
| hi | hin_Deva / 256068 | hi | Mapping tested; actual language quality unverified |
| hu | hun_Latn / 256071 | hu | Mapping tested; actual language quality unverified |
| jv | jav_Latn / 256078 | jw | Mapping tested; actual language quality unverified |
| ka | kat_Geor / 256086 | ka | Mapping tested; actual language quality unverified |
| tl | tgl_Latn / 256174 | tl | Mapping tested; actual language quality unverified |
| fr | fra_Latn / 256057 | fr | Original native speech regressions passed; TTS installed; prior NLLB evidence retained |
| gl | glg_Latn / 256062 | gl | One NLLB→EN meaning sample passed; reverse produced text; speech/TTS not run |
| gn | grn_Latn / 256063 | No — typed input remains available | Mapping tested; actual language quality unverified |
| hr | hrv_Latn / 256070 | hr | One NLLB→EN meaning sample passed; reverse produced text; speech/TTS not run |
| it | ita_Latn / 256077 | it | Mapping tested; actual language quality unverified |
| ja | jpn_Jpan / 256079 | ja | Real manual/auto Whisper passed; TTS installed; NLLB fever meaning FAILED |
| ko | kor_Hang / 256098 | ko | NLLB original sample rejected U+D515; speech/TTS unverified |
| mk | mkd_Cyrl / 256118 | mk | Mapping tested; actual language quality unverified |
| nl | nld_Latn / 256127 | nl | One NLLB→EN meaning sample passed; reverse produced text; speech/TTS not run |
| pl | pol_Latn / 256140 | pl | Mapping tested; actual language quality unverified |
| pt | por_Latn / 256141 | pt | Mapping tested; actual language quality unverified |
| ro | ron_Latn / 256145 | ro | Mapping tested; actual language quality unverified |
| ru | rus_Cyrl / 256147 | ru | Mapping tested; actual language quality unverified |
| sk | slk_Latn / 256154 | sk | Mapping tested; actual language quality unverified |
| sv | swe_Latn / 256167 | sv | Mapping tested; actual language quality unverified |
| ta | tam_Taml / 256170 | ta | Mapping tested; actual language quality unverified |
| th | tha_Thai / 256175 | th | Mapping tested; actual language quality unverified |
| tr | tur_Latn / 256184 | tr | Original native speech regressions passed; TTS installed; prior NLLB evidence retained |
| uk | ukr_Cyrl / 256188 | uk | Mapping tested; actual language quality unverified |
| ur | urd_Arab / 256190 | ur | Mapping tested; actual language quality unverified |
| vi | vie_Latn / 256193 | vi | Mapping tested; actual language quality unverified |
| kn | kan_Knda / 256083 | kn | Mapping tested; actual language quality unverified |
| ks | kas_Arab / 256084 | No — typed input remains available | Mapping tested; actual language quality unverified |
| kk | kaz_Cyrl / 256089 | kk | Mapping tested; actual language quality unverified |
| ki | kik_Latn / 256093 | No — typed input remains available | Mapping tested; actual language quality unverified |
| ga | gle_Latn / 256061 | No — typed input remains available | Mapping tested; actual language quality unverified |
| rw | kin_Latn / 256094 | No — typed input remains available | Mapping tested; actual language quality unverified |
| ky | kir_Cyrl / 256095 | No — typed input remains available | Mapping tested; actual language quality unverified |
| lo | lao_Laoo / 256100 | lo | Mapping tested; actual language quality unverified |
| li | lim_Latn / 256103 | No — typed input remains available | Mapping tested; actual language quality unverified |
| ln | lin_Latn / 256104 | ln | Mapping tested; actual language quality unverified |
| lt | lit_Latn / 256105 | lt | Mapping tested; actual language quality unverified |
| lb | ltz_Latn / 256108 | lb | Mapping tested; actual language quality unverified |
| bo | bod_Tibt / 256030 | bo | Mapping tested; actual language quality unverified |
| fa | pes_Arab / 256053 | fa | Mapping tested; actual language quality unverified |
| km | khm_Khmr / 256092 | km | Mapping tested; actual language quality unverified |

The existing local emergency-word checks cover four languages only. A new explicit local_screening value distinguishes warning matches, absence of a local match (never all-clear), and unverified language coverage. The prompt still assesses the reviewed original and requires uncertainty when it cannot understand the text. No unsupported-language LOW default or Demo fallback is introduced.

## Verification status

- Core: 19/19 passed; backend: 30/30 passed.
- App unit tests: 108/108 passed99, including original four-language fixtures, all-registry/consent and localized failure tests. Core19/backend30 previously passed and remained cached in the final build.
- Shell99 device/isolated/runner builds and device lint passed6m34s. Test-only rebuilds100/111 passed.
- New clean API36 x86_64 isolated guest,12GB data; original application untouched. Initial role migration/persistence/recreation, preparation UI and voice-catalog tests3/3 passed70. Initial Pixel Launcher/SystemUI and instrumentation-startup ANRs retained; not treated as NLLB or application quality success.
- Real NLLB78: DE/BG/DA/NL/FI/GL/HR/CA each passed one synthetic source→EN negation/duration/severity/throat+fever check and produced nonempty EN→source output. Reverse fluency is not established (e.g. Finnish grammar is imperfect). Paired translations1.5–3.3s; model load7.5s. JA returned 'There's no heat' for the fever sentence, failing symptom meaning. KO input triggered unknown-token rejection; diagnosis pending. Original fixtures and thresholds retained. Full test remains FAILED, not9/10 or all-language success.
- Prepared offline TTS catalog: EN9/TR6/FR6/AR5/DE5/JA5 voices. The other65 registry codes have no installed offline voice on this guest; this does not gate typed translation/assessment. Official Google TTS packages added about73.2MB; no NLLB/Whisper weight download.
- Shell92:5/5 PASS,141.339s. All three formerly blocked NLLB/Conversation cases now pass: ordered actual peer translations, sequential actual TTS, and TR synthetic waveform→Whisper→NLLB→review→explicit mock approval with original text preserved. New DE original/EN single bubble/TR UI/JA result-selection recreation and voice catalog also pass. Combined pipeline58.506s, sampled peakPSS1,593,221KB, minimum available383,104KB; not a device-wide peak guarantee.
- Shell94:7/8 PASS,702.335s. Original six native Whisper tests all pass, as does additional DE/JA manual+automatic recognition. The remaining test FAILS its original JA/KO translation quality assertions: JA heat/fever meaning error; KO unknown SentencePiece token U+D515 (픕). These are not silently changed or accepted. Ten actual translations were attempted, eight met the specified meaning checks.
- DE manual/auto34.488/86.912s; JA38.952/95.309s on this CPU-constrained3GB x86_64 guest. Exact synthetic German sentence preserved; Japanese punctuation/numeral formatting differs, sentinel meaning words retained. Both detected languages match. This is offline TTS-generated PCM through the real model, not physical microphone evidence or broad language accuracy. Cancellation42/37ms, successful restart, sampledPSS980,835KB.
- Additional JA wording with explicit 発熱 correctly translated fever. Additional KO wording produced 'My neck hurts. There\'s no heat.' and FAILED throat/fever meaning checks. This diagnostic does not replace either original failing fixture. The limitation is model vocabulary/semantic quality, not a four-language UI gate. Translation remains opt-in; original-only assessment and review remain mandatory.
- Final local scope157: core19/backend30/app108; app108 rerun99, unchanged core/backend cached. Device/isolated builds+lint99 PASS6m34s. Full regression108 had52passes and3 outdated fixture failures; updated whitelist keeps privacy checks and adds independent result preference, catalog selection now scrolls by canonical code. All3 pass in112; no application code changed to hide failures. Targeted112 completed10/12: the3 repaired fixtures and branding/role/UI/Conversation tests pass; additional KO quality fails. TR combined flow also failed safely under3GB guest pressure (111,224KB available, speechMemory,61original characters retained,0translation). Its earlier92 success remains valid but does not establish low-memory reliability; unchanged test120 then PASS1/1 on4GB. Pipeline92.401s,61original/89translated characters,0lowMemory,sampledPSS1,588,317KB,minavailable984,144KB. Explicit consent/original retention passed. These CPU-constrained timings do not establish performance on physical ARM64.
- Final normal-device German direct API attempt21 VALIDATED, after attempt20 safely rejected OPENAI_SAFETY_CONFLICT. Backend stopped/reverse empty. Total21/23,14validated/7failed,conservativeUSD0.0742168,2remaining. Host history merged only after first19 exact equality; APK seed21. No extra allowance.
- Normal device133 real model smoke2/2 PASS. Final138 device/lint/app108 PASS;153 budget-seed rebuild PASS. Normal data/models retained. Only old .uitest duplicate Medium file removed after host/device SHA256 equality to free install space. Personal delivery APK updated; historical artifact metadata remains in older HANDOFF sections.
