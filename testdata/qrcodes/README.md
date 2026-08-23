# QR codes for testing the scanner

Three codes for checking by hand what [URLTemplateTest] and
[TOTPValidationTest] check automatically: that a scanned URL is validated the
way a CSMS validates it. Display one on a screen and scan it with the app.

Scanning needs a camera, so this cannot be automated in the unit tests; the
codes exist so the manual check takes a minute rather than an afternoon.

## Setting up the app

Only `01-valid.png` needs preparation, because a valid token depends on the
moment and the secret:

1. Tap the **Timestamp** field. This stops the clock, which is what makes the
   check reproducible - validation uses the timestamp from the field.
2. Enter `2026-01-01 12:00:00`, in the Europe/Berlin time zone the code was
   generated for.
3. Set **Shared Secret** to `secure!Charging!`.

Leave validity time, TOTP length and alphabet at their defaults.

## What each code should produce

| File | Contents | Expected result |
|------|----------|-----------------|
| `01-valid.png` | `https://charging.cloud/DE*GEF*E12345*678*1/j0yccWm0nojS` | *TOTP valid — current time slot*, in green |
| `02-invalid-totp.png` | `https://charging.cloud/DE*GEF*E12345*678*1/000000000000` | *TOTP invalid!*, in the error colour |
| `03-foreign-url.png` | `https://evil.example.com/DE*GEF*E12345*678*1/j0yccWm0nojS` | *The scanned URL does not match the URL template!* |
| `04-other-evse.png` | `https://charging.cloud/DE*GEF*E99999*111*2/j0yccWm0nojS` | *TOTP valid — current time slot*, plus *EVSE ID: … (differs from the configured one)* |

The third one is the fraud OCPP 2.1 C25 exists to prevent: a sticker pasted
over the original, pointing at somebody else's payment page. Note that it
carries a perfectly valid TOTP - only the host differs.

The fourth shows why the EVSE ID is reported but does not decide the verdict.
The token is the same valid one as in the first code, because a TOTP does not
depend on the EVSE ID at all; only the charging point differs. C25 makes the
token the proof of authenticity and has the CSMS decode the URL parameters
afterwards, so this is a valid code of another charging point - not an invalid
one, and it is not coloured like a failure.

## Regenerating them

The codes are plain QR codes of the URLs above, so any generator will do. The
token in `01-valid.png` follows from the shared secret and the timestamp, and
`TOTP.generateTOTPs` produces it.
