# Native speech memory investigation

2026-10-20; synthetic/licensed public audio only, zero OpenAI requests. Android-profiler instructions were consulted. This investigation uses Android ApplicationExitInfo, dumpsys memory accounting and the pinned native source; no Perfetto trace was recorded or analyzed, and no trace-level root-cause proof is claimed.

## Evidence

- medium_phone has2,018,820KB total RAM (~2GB). Host total RAM is a different resource and must not be used as the app's budget.
- medium-q5_0 greedy TR/AR/FR manual/auto waveform tests all passed in shell-223. Arabic WER0.20, French0.25; unchanged fixture threshold0.35.
- The next test used beam5 for French recognition/translation and the process died. Android reports LOW_MEMORY (pid22785), importance125, RSS659MB. This sampled RSS is not a measured peak allocation.
- A fresh foreground Activity translation-only test (shell-227) also died: LOW_MEMORY(pid25042), importance100, RSS701MB. Foreground priority alone did not solve the issue; no unrelated process was stopped.
- Pinned whisper.cpp/src/whisper.cpp, around7065–7082, reallocates decoder self-attention KV cache using factor=n_decoders_cur+2 when there are multiple decoders. beam5 therefore uses factor7; greedy at temperature0 uses one decoder. This is a source-supported explanation for increased pressure, not proof that no other allocation contributes.
- Production STT already used greedy. Experimental native translation was changed to greedy, and greedy.best_of=1 avoids unused decoder setup at temperature0. Production optional translation now uses ML Kit text translation instead; native speech translation remains only a comparison diagnostic. Beam remains an explicitly requested diagnostic comparison only, not a user setting. Historical failed tests are retained.

## Resource policy

Only one production model context is retained per SpeechController worker; a model selection change releases the old context before loading the new one. Native work uses two CPU threads, bounded29-second PCM and cooperative120-second work limits. Main-thread cancellation suppresses stale UI delivery. Upstream initial auto-language encoding can outlast cancellation before returning; that limitation remains.

Small remains the existing default. Medium is an optional, slower choice with higher RAM use; its successful short fixtures do not guarantee operation under all memory pressure or clinical accuracy. Shell-230 greedy native translation ran without a low-memory kill but failed meaning preservation. Shell-240 actual French PCM→Medium→ML Kit→UI review passed without an OS kill; this proves that bounded fixture's operation, not general memory safety or translation accuracy. MainActivity releases native weights when stopped and on critical-memory callbacks, while preserving text. OS termination before callback remains possible. No Android largeHeap, emulator RAM/security setting, OS kill policy, or background service privilege was changed. Test model weights and generated APKs are not committed.
