# Conversation/Bluetooth source mapping — implementation in progress

Read-only reference: RTranslator-2.00/app/src/main/java/nie/translator/rtranslator. Upstream baseline tag2.0.0 at43cd934889302325d6071c9f35e69eaa5bd23df7; BluetoothCommunicator.java blob5777a91278a219a72d607adcff145058cdcdfc25 exactly matches the local source (shell495/496). Local customized WalkieTalkieService does not match upstream and is not overwritten.

## Actual original flow

- PairingFragment + fragment_pairing.xml: search/cancel, discovered peer rows, connection request/accept/reject.
- ConversationFragment + fragment_conversation.xml: original Conversation/Connection tabs and toolbar.
- ConversationMainFragment + fragment_conversation_main.xml: message list, keyboard, microphone and speaker controls.
- PeersInfoFragment: connected-peer information/disconnection.
- ConversationService: Recorder→Recognizer produces text; sends the final text and source-language suffix. This is not raw real-time audio streaming. Incoming text is translated and can be spoken.
- ConversationBluetoothCommunicator: m=text, d=device identity, i=profile image. The old implementation automatically exchanges identity/photo and automatically sends recognized text.
- BluetoothMessage:4 ASCII message-ID characters,3 sequence characters,1 final/nonfinal flag, then payload. Sequence alphabet is printable ASCII32–126. Message m payload ends in language tag and one-digit tag length.
- BluetoothConnectionServer: BLE GATT with ordinary READ/WRITE permissions; no encrypted/MITM requirement. Old adapter enable/disable/name manipulation is not suitable for unrestricted copying onto modern Android.

## Adaptation and intentional safety differences

ConversationProtocol preserves the source frame structure/text-language representation while rejecting gaps, malformed UTF8, unsupported languages, duplicate completed messages, oversized buffers and expired fragments. ConversationSession requires a bonded peer, explicit connection acceptance and single-use consent for the exact text/language/recipient. Disconnect clears approval; there is no automatic resend.

The transport must use a separate service UUID and authenticated/encrypted pairing rather than silently advertising compatibility with the old unsecured transport. Legacy peer interoperability is therefore not claimed. No profile image/device ID is automatically shared. No incoming text triggers OpenAI. Only four assessment languages remain supported.

Messages will remain bounded/in-memory; explicit sending is distinct from analysis consent. Leaving the foreground closes radio work. Reconnection must be an explicit action; missed health messages are not silently replayed.

## Normal UI validation and current repair

At checkpoint d033d25, the normal debug app on medium_phone and owner-signed release on small_phone discovered each other through actual Android BLE, requested/accepted a session, exchanged explicit reviewed text in both directions and displayed a real NLLB EN→TR received-message translation with both negated and affirmative sentences preserved. The Connection tab identified ReleaseTest. Evidence: `ui-comparison/product-release/normal-conversation-ui.json` and images07/08. Existing virtual-device bonds were reused; this is not physical RF evidence.

Two defects were exposed, not hidden: the discovery description overlapped the first peer row, and explicit server disconnection left the remote UI in chat. The first repair restores RTranslator PairingFragment466–479: top description hidden with nonempty list, bottom description visible; both receive status updates. The second adds a one-byte d session-close control to the distinct secured protocol (not legacy identity d compatibility), accepted only inside an authenticated connected session. The sender closes after confirmed delivery; failure still closes locally. Hardware loss/background cleanup remains Android callback-driven. These repairs are covered by the later layout and extended two-AVD disconnect results below; the earlier duplex success alone did not prove this fix. The first retry719/720 failed before transfer: Android delivered the outgoing client's shared physical-link callback to its local GATT server, whose duplicate-peer branch cancelled that same link. Restricted Bluetooth stack logs confirmed cancelConnection during the encrypted subscription handshake. The repair ignores that already-selected same peer and still rejects a different peer; no bonding/encryption requirement is weakened. The failed run is retained in duplex-disconnect-before.json. After build722, the unchanged exact-payload/order assertions plus the new authenticated disconnect assertion passed on both actual AVDs724/725 (server39.998s/client22.990s). Each peer sent/received two messages; the server's explicit close was observed by both app sessions within15s. Evidence: duplex-disconnect-after.json. The settled source-layout non-overlap check also passed717. Physical radio, multiparty and arbitrary background-link-loss behavior are not inferred from this success.

## Validation plan and current limits

ConversationProtocol6/6 local framing/state tests passed in shell487 app58/58. ConversationBluetooth and ConversationActivity are integrated with source XML tabs/pairing/peers/message controls, real Android BLE discovery/GATT/bonding, explicit accept/reject/send, offline optional translation and TTS. Shell465 UI3/4 passed; a wrong test title matcher was corrected and six updated UI checks await rerun.

Shell489/490 used real Android Bluetooth stacks in medium_phone API36 and small_phone API36.1: peers were discovered, but the client reached pairing and timed out; server did not complete an accepted session. No payload transfer was verified. Probe had no visible foreground Activity, so the next controlled attempt launches ConversationActivity and allows120s for the OS pairing prompt, without bypassing bonding. Discovery results are deduplicated. No physical radio or cloud hardware success is claimed.

Shell505/506 succeeded after bringing Android Bluetooth Settings foreground on the server: matching numeric codes were visibly compared and Pair approved on both AVDs, contacts/call sharing left disabled. The client established a bonded/accepted session and sent the exact synthetic English text; server asserted exact received payload/language. Client1/1 passed36.069s, server1/1 passed61.842s. This is actual two-AVD Android BLE/GATT-stack evidence, not mock transport or physical RF. Initial failed499/500 logs remain. At that stage reverse-direction/multi-message checks were pending; the later620/621 result below supersedes that part of the status. It does not establish a complete human-operated two-phone UI flow.

Shell566 eight current Conversation UI checks passed, including peer-scoped rotation retention, written draft/send boundaries, language/RTL and microphone stop before TTS. Shell572 ConversationTranslationTest passed two ordered synthetic incoming messages through the real NLLB engine, preserving originals/languages and never filling triage symptoms. That test injects decoded transport messages and is not a radio test. Shell620/621 now pass the separate actual two-AVD duplex BLE diagnostic: server23.710s/client18.676s, two ordered messages sent and two received by each peer, EN/TR/FR/AR tags and an English460-character fragmented payload checked exactly. Existing Android bonds were reused without bypassing pairing. This verifies the emulator Bluetooth stack, not physical radio. A SystemUI ANR was observed on the secondary emulator after the test; it is recorded separately from the passing application transport assertions.

Current limits: a single foreground peer, explicit reconnect rather than automatic health-text replay, no old unsecured-peer compatibility, no profile-picture transfer. Conversation has bounded100-message/100000-character in-memory history and serial translation queue capped at20; different-peer connection clears the in-memory case boundary. These are not reported as full legacy multi-peer equivalence.

Official emulator network simulation supports Bluetooth virtual radios; investigate two AVDs using installed images without changing security controls. This would be emulator radio-stack evidence, not physical RF/hardware certification.

References:
- https://developer.android.com/develop/connectivity/bluetooth/bt-permissions
- https://developer.android.com/studio/run/emulator-networking-advanced
- https://developer.android.com/reference/android/bluetooth/BluetoothGattCharacteristic

Android12+ scan/connect/advertise permissions must be requested at use. Older location permission is limited toAPI30. No location derivation is intended. Hardware absence/permission denial must leave written assessment usable.
