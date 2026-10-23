# Bluetooth Conversation

Conversation exchanges text and language metadata with one nearby foreground
peer. It does not stream live microphone audio. Both users must enable Bluetooth,
grant permissions, complete Android bonding, and explicitly accept the connection.

## Consent and transport

`ConversationProtocol` uses bounded fragmented messages, validates UTF-8 and
language tags, and rejects malformed, expired, duplicate, or out-of-order data.
`ConversationSession` requires approval for the selected text, language, and
recipient. Disconnect clears approval; reconnect and resend are explicit.

The service uses a separate secured UUID with authenticated/encrypted pairing.
Interoperability with older unsecured peers is not supported. A nickname is
exchanged during the explicitly accepted connection; profile photos are not shared.
See [source attribution](THIRD_PARTY.md) for framing and UI provenance.

Android 12+ uses nearby-device scan/connect/advertise permissions. Older location
permission is limited to API 30. The application does not intend to derive location.
Permission denial or unavailable Bluetooth leaves typed input usable.

## Message processing

Received originals remain separate from optional local translations. Installed
TTS can read messages when enabled; peer playback never automatically starts
capture, shares text, or submits an OpenAI assessment. Sending and assessment
approval remain independent.

History is in memory and bounded to 100 messages / 100,000 characters. Translation
is serialized with a queue capped at 20. Changing peers clears the conversation
boundary. Leaving the foreground closes radio work. The explicit disconnect
control requests remote session closure, but unexpected hardware/link loss still
depends on Android callbacks.

## Evidence limits and troubleshooting

Earlier integration notes recorded two-AVD Android BLE discovery, bonding,
bidirectional fragmented messages, and explicit disconnect tests. Earlier pairing
timeouts and a duplicate-link callback defect were also recorded. These are
historical integration observations, not physical radio certification, and were
not rerun for this documentation PR. Private logs and screenshots are not included.

If discovery or connection fails, keep both apps foreground, check permissions and
bonding, then explicitly reconnect. Do not weaken pairing security or assume a
missed message was delivered. Multiparty chat, background replay, and profile-photo
transfer are not supported.

A recipient can copy or otherwise retain received text. Obtain the person's
permission before sharing sensitive information.

References:
- [Android Bluetooth permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions)
- [Android GATT characteristic API](https://developer.android.com/reference/android/bluetooth/BluetoothGattCharacteristic)
