# Security

## Known limits (deliberate, demo scope)

| Limit | Consequence | What production would need |
|---|---|---|
| Server has **no authentication** | Anyone who can reach the port can post or acknowledge alerts | Device certificates or tokens; dispatcher login |
| HTTP, not HTTPS | Traffic readable on the network | TLS; remove the cleartext exception |
| Cleartext allowed only for `10.0.2.2` / `localhost` in `network_security_config.xml` | Emulator-to-host only | — |
| Device owner set with `android:testOnly="true"` debug build | Removable for testing | Provisioned through managed enrolment |
| Server binds for local use | Do not expose the port to the internet | Reverse proxy, auth, rate limiting |

## Secrets

- The project needs no API keys or passwords.
- Never commit `local.properties`, keystores (`*.jks`, `*.keystore`), `.env`, or tokens. `.gitignore` covers these.
- Do not print machine paths, usernames or tokens in docs or saved evidence; redact before saving to `docs/evidence/`.

## Personal data

- An alert carries worker ID, device ID, location and battery level. Demo data only (`W-1042`, emulator serial, `geo fix` coordinates).
- `alerts.jsonl` and KPI CSVs are runtime output and are git-ignored.

## Input handling

- The server validates every POST body (`protocolVersion`, UUID `alertId`, known `type`, lat/lon range, battery 0–100) and returns 400 otherwise.
- The dispatcher console inserts alert fields with `textContent`, never `innerHTML`.
- Room queries use bound parameters only.

## Privileged permissions

- `READ_PRIVILEGED_PHONE_STATE` is requested only to read the device serial and is granted through a `privapp-permissions` allowlist on the AOSP emulator image.
- `SosKeyReceiver` and `SafeSignalAdminReceiver` are protected by system-only permissions so other apps cannot trigger them.

## Reporting

Portfolio project; open a GitHub issue for anything found.
