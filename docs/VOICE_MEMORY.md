# Speech and translation memory limits

Earlier limited-memory emulator experiments recorded low-memory process exits
during Medium beam-search translation and a separate foreground translation
attempt. Foreground priority alone did not prevent termination. These are retained
historical observations; no performance profiling was run for this PR.

Pinned native source increases decoder cache allocation with multiple decoders,
which explains part of the pressure but is not a complete measured allocation
analysis. Production recognition uses greedy decoding. Native translation remains
a diagnostic path; user-facing text translation uses NLLB or ML Kit.

The app serializes native work, releases the previous model context on selection
changes, bounds audio and inference, and suppresses stale callbacks after
cancellation. The native abort adapter extends cancellation into CPU graph work.
Stopping the activity and critical-memory handling release native resources while
preserving available text; the operating system can still terminate a process
before a callback runs.

Whisper followed by NLLB has also recorded memory cancellation on a 3 GB emulator
and completion of a bounded synthetic flow on a 4 GB configuration. Those results
do not define a universal RAM minimum or guarantee reliability.

Use short utterances, consider Small, and fall back to typed input when needed.
Do not bypass Android memory/security controls or erase app data as a workaround.
Sampled process memory is not a measured device-wide peak. Model binaries and
private performance logs are not included.
