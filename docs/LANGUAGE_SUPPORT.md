# Language support

The source registry contains 71 processing languages. Interface localization is
separate and supports English, Turkish, French, and Arabic. The table below
retains earlier diagnostic observations; no language tests were rerun during
the publication review.

## Independent choices

- Interface language controls menus.
- Input/speech language describes the original message.
- Translation target controls optional display translation.
- Assessment language controls the requested result language.

Unknown codes are rejected. Explicit speech aliases are mapped without arbitrary
region/script trimming. Simplified Chinese uses zho_Hans; Javanese jv maps to
Whisper jw. Catalog provenance is recorded in [THIRD_PARTY.md](THIRD_PARTY.md).

Whisper has mappings for 58 registry codes; other entries can still accept typed
input. The normal catalog filters some entries, and expanded-language settings
expose additional unverified choices. TTS depends on actual installed offline
voices and quality settings. ML Kit has its own supported-language intersection.

## Capability matrix

A tokenizer mapping and response schema entry do not prove recognition,
translation, TTS availability, clinical accuracy, or provider performance.
Rows marked passed refer only to historical bounded fixtures, not broad validation.

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


## Known limits

Japanese fever/heat errors and Korean vocabulary/throat/fever failures remain
unresolved. A German-to-Turkish example introduced gender and changed ongoing
symptoms to past tense. Turkish severity loss and other provider-specific failures
are recorded in [Local translation](LOCAL_TRANSLATION.md).

Local emergency-word checks cover only four languages; other entries explicitly
have unverified local-screening coverage. The absence of a warning match is not evidence of
safety. Assessment requires the reviewed original and separate approval; mapping
a new language never licenses a LOW default or simulated fallback.

Earlier workflow checks included short synthetic/licensed examples and emulator
tests. Private reports and usage histories are excluded. Language availability is
not a claim that all 71 languages were tested.
