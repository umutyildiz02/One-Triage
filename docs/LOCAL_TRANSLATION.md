# Optional local translation

## Final observed meaning limits — 2026-10-23

The final normal-device walkthrough translated the synthetic German `Eine 30-jaehrige Person hat Seit 20 Minuten starke Brustschmerzen und schwere Atemnot.` into Turkish `30 yaşındaki bir kadın 20 dakika boyunca ciddi bir göğüs ağrısı ve ciddi bir nefes sıkıntısı yaşadı.` This is actual NLLB output, but it introduces female gender and shifts ongoing symptoms into past tense. It is not a quality pass. The reviewed German original, not that translation, was sent to OpenAI. This observation joins the retained Japanese fever/heat and Korean tokenizer/throat/fever failures. No alternate wording or weaker threshold replaces the failed corpus. See local final evidence (excluded from this source backup).

## Current personal integration — 2026-10-23

The provider is explicit: source-derived NLLB or the optional ML Kit adapter. Interface, source, translation target and assessment result languages are independent. The registry contains 71 processing languages; each engine has separate capability mappings. This is not proof of accuracy in all 71 languages. The main screen shows exactly one actual target-language bubble per completed turn; the original remains available in review and is the only assessment input after consent. Equal source/target skips translation. Missing output shows a truthful status. Optional translated-turn TTS finishes before a user-started automatic capture loop resumes. Peer playback never starts an assessment or shares text automatically.

[NLLB integration and real test evidence](NLLB_INTEGRATION.md) covers actual ONNX inference, prepared private models, offline four-target tests, the successful4GB Turkish speech/translation flow and remaining severity loss. local performance report (excluded from this source backup) records3GB cancellation versus4GB success. Translation staysOFF by default; neither provider has clinical semantic validation.

Normal-package real NLLB testing also found dropped second sentences in EN/TR/AR. The source-derived locale sentence loop now keeps segments separate; the same four fixtures pass offline after the change (shell682 baseline failure,684 success). Before/after outputs are retained in the product-release gallery folder. This repairs those omissions, not the separate known severity-loss case; original-only assessment and defaultOFF remain.

Fresh final-pass NLLB checks passed limited DE/BG/DA/NL/FI/GL/HR/CA meaning sentinels, four-target original-preservation checks, and the three previously blocked NLLB/Conversation cases. Japanese fever-to-heat and Korean vocabulary/throat/fever failures remain unchanged. See docs/final-showcase/measurements (relative to the project root) for actual outputs. No expected sentence or threshold was weakened.

The sections below retain the earlier ML Kit-only investigation and its failed fixtures. They are historical evidence, not a claim that the current product is English-only or that current opted-in TTS is absent.

## Historical ML Kit source and implementation

The inspected source <local-reference-root>/speechtranslator/app/src/main/java/com/example/speechtranslator/MainActivity.kt, translateText around156–190, uses ML Kit TranslatorOptions, downloadModelIfNeeded and translate. VitalTriage adapts that real pipeline with explicit preparation, bounded worker waits, deterministic close, no automatic TTS, no repeated-on-error recording loop and no automatic OpenAI call.

Whisper performs actual acoustic recognition and language detection. Optional Google Translate through ML Kit translates the recognized non-English text to English locally. The original transcript is always retained, remains editable, and is the only text sent to triage after explicit user approval. A missing model/translation failure does not discard the original. No identical second-language placeholder is fabricated.

Dependency: com.google.mlkit:translate:17.0.3, verified from official Android documentation on2026-10-20. APIs run on-device; Google may receive SDK use/performance metrics and model requests, but not input/output text. The app describes this in its preparation dialog, Settings and About. This is not a zero-network SDK. No Cloud Translation paid service or API key is used.

- Android integration: https://developers.google.com/ml-kit/language/translation/android
- Languages including en/tr/fr/ar: https://developers.google.com/ml-kit/language/translation/translation-language-support
- Terms/privacy: https://developers.google.com/ml-kit/terms
- Quality limitations: https://developers.google.com/ml-kit/language/translation
- Attribution: https://developers.google.com/ml-kit/language/translation/translation-terms and https://docs.cloud.google.com/translate/attribution

This SDK/model license is Google's ML Kit terms, not Whisper's MIT license. Smartphone/tablet/PC scope only; no embedded-device deployment was made. No model extraction or reverse engineering is performed. Google describes the translator as intended for casual/simple translations. Its output is not clinically validated and must not replace review of the original.

## Preparation and resource accounting

Settings → Prepare translation models explicitly prepares TR/FR/AR→English over Wi-Fi. Official documentation estimates approximately30MB per language. The UI shows actual pending-language/readiness or an error, never fictitious percent progress. A download managed by Android/ML Kit may finish after the screen closes; the app does not claim hard transport cancellation.

Ordinary recognition never initiates model downloads. Optional translation checks installed state and returns a visible failure if unprepared. Preparation checks400MB free app storage and uses a120-second wait per requested language, without an application retry loop. Existing installed models are reused.

The owner authorized5GB aggregate model downloads. scripts/Reserve-TranslationModels.mjs reserves100MB per language (300MB per authorized package) in the same persistent .runtime/model-downloads.json before controlled setup. This is a conservative model-size allowance, not independently measured SDK transport bytes. Never reset that file. The public Whisper downloads are tracked exactly and separately within the same total. SDK artifacts are authenticated/managed by Google's distribution; no independent published checksum comparison is claimed for those models.

## Attribution

The official attribution PNG is copied unchanged from https://docs.cloud.google.com/static/translate/images/text-attribution.png into drawable-nodpi. The first complete full-color badge is selected at render time; no mark is redrawn or generated. The official zip link returned an empty archive, so the published image sheet was used. Attribution appears adjacent translated text; the opt-in control is labeled Translate with Google. About includes the required provider disclaimer and information/privacy links. VitalVoice's original logo/header remain unchanged.

## Final emulator recheck — 2026-10-20

Shell-314 reran both real local semantic diagnostics without downloads or API calls. Both failed, and translation remains OFF. The original throat-loss (TR) and public legal double-negation (FR) failures persist. Added controlled symptom/severity/time sentinels passed all five checks for TR, but FR failed the no-chest-pain sentinel and AR failed the severe-breathing sentinel. These narrow keyword checks can also miss valid paraphrases; they are not a complete semantic audit or clinical validation. No claim that every failed keyword proves a particular clinical mistranslation. They provide no basis for automatically enabling the feature. Original transcript remains the only assessment payload.

Raw reports: ui-comparison/emulator-final/local-translation.json and translation-severity.json. Older quality failures and fixtures are retained unchanged.

## Evidence status

Native Whisper speech translation was tried first and retained as a failing diagnostic. The French public fixture lost its second negative clause with Small/q8/Medium. Medium beam5 also triggered LOW_MEMORY twice on this2GB emulator; greedy avoided the kill but not the meaning error. These are not successful translation claims. See WHISPER_QUALITY.md and VOICE_MEMORY.md.

Build passed shell-236. Actual model preparation and all three local translations ran shell-240. French and Arabic synthetic fixtures retained throat/fever/negation/duration checks; Turkish lost throat meaning and the quality test failed. The public French legal output also contains double negation; an explicit regression assertion now records that failure. No keyword-only success is treated as complete semantic fidelity.

VoiceTranslationFlowTest passed in shell-240: actual licensed French PCM→Medium Whisper→ML Kit→distinct original/English bubble→review, Analyze disabled before consent, no API call. It validates wiring and original retention, not the accuracy of the displayed English. Screenshots08–09 and mlkit-translation-flow-medium.json retain this distinction. Optional translation remains off by default, review-labeled, and is never sent in place of original text.

LocalTranslationTest requires explicit allowTranslationDownload=true to prepare missing models; ordinary runs require installed models and cannot silently download them. Existing failed assertions were retained/strengthened, not replaced with injected text or weaker thresholds. Normal-app explicit preparation was attempted: the first wait timed out on Turkish; a later check observed Turkish installed and advanced to French, whose wait also timed out. The final explicit check reused installed Turkish and French and advanced to Arabic, then timed out after120seconds. The normal-package SDK subsequently logged another completed model download at09:23:51. One explicit follow-up check then reused all installed models and displayed Ready: Turkish, French and Arabic→English (screenshot18). This confirms final normal-package model preparation; it does not resolve the semantic quality failures. Optional translation remains off. This does not block local Whisper or original-text LIVE evaluation.
