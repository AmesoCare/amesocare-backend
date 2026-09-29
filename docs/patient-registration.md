# Patient registration

Registration extends the existing JDBC-backed `patient` table. There is no new database,
ORM, authentication system, or migration framework. Android owns one installation UUID
in private SharedPreferences; the backend is authoritative for registration status.

## Database upgrade

For an existing local database, run **once**, from `amesocare-backend`, before starting
updated services:

```sh
docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U ameso -d amesohermes < db/migrations/001-patient-registration.sql
docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U ameso -d amesohermes < db/migrations/002-patient-phone.sql
docker compose up -d --build patient-service incident-service notification-service
```

Use the corresponding compose file and database credentials for your deployment.
Do not remove the PostgreSQL volume. The migration preserves existing patients and incidents.
If migration 001 is already applied, run only migration 002 above. It reuses the existing
`phone` column and enforces exactly ten digits for new/updated registered patient rows.
Existing registrations without a phone are preserved and show “Not provided” in Hermes;
no phone numbers are fabricated.

Fresh databases automatically apply `db/init/03-patient-registration.sql` and
`db/init/04-patient-phone.sql` after the existing
schema and seed scripts; do not apply the migration again to a fresh database.

The migration adds a unique nullable UUID `device_id`, gender, medication, allergies,
surgery and remarks, plus validation constraints for registered patients. Existing name,
age, address, blood group and timestamps are reused. Preferred hospital and home/incident coordinates allow null: registration does not
collect them. Phone is required for new registrations, stored as a string to preserve
leading zeros, and must contain exactly ten digits with no country code. Existing seed patients are not registered installations.

## API changes

- `POST /api/patients/register` (patient service): accepts `deviceId`, `patientName`,
  string `age`, `gender`, `address`, `bloodGroup`, required string `phone`, and optional `medication`, `allergies`,
  `surgery`, `remarks`. Returns `{registered: true, patient: {...}}` with ID and timestamps.
  A repeated/concurrent request for the same UUID returns the original patient without
  updating it or creating a duplicate.
- `GET /api/patients/device/{deviceId}` (patient service): returns registered patient
  information, or `{registered: false, patient: null}`. Malformed UUIDs return HTTP 400.
- `POST /api/sos` and `POST /api/sos/cancelled` (incident service): now accept only
  `{"deviceId":"<installation UUID>"}`. No demo login or patient details are needed.
  Invalid UUIDs return 400; unregistered installations return 409. The server resolves
  patient identity and assigns the SOS timestamp. SOS returns 201 with incident ID.
- `GET /api/incidents/{id}`: adds `patient`, the complete stored registration, or null
  for legacy patients. Hermes uses this field for its patient card. Incident coordinates
  are nullable.
- Existing authenticated patient detail responses also include the new medical fields.

Registration/status and the two SOS submission routes allow anonymous installation access.
Hospital login and protected incident/patient/notification endpoints retain existing JWT
requirements. A UUID identifies an installation; it is not a new user authentication scheme.
The old browser patient demo's patientId-based SOS request is no longer accepted; this
change implements the native Android registration flow.

Validation is applied on Android and the backend: required name/address, age as a whole
number from 1 through 120, Male/Female, one of the eight standard blood groups, and 200
characters maximum per free-form input other than phone, which requires exactly ten
ASCII digits. No country-code field is shown. HTTP 400 responses include an `error` message.
Blank optional fields are stored as null and rendered as “Not provided”.

## Android

Build with the configured deployment URLs, or point an emulator at the local backend:

```sh
cd ../amesocare-android
./gradlew :app:assembleDebug \
  -PAMESO_PATIENT_URL=http://10.0.2.2:5002/ \
  -PAMESO_INCIDENT_URL=http://10.0.2.2:5003/
```

Install `app/build/outputs/apk/debug/app-debug.apk`. Use the computer's LAN IP for a
physical device. Release builds require HTTPS. URLs are compiled into the APK.

On startup, the app persists/loads its UUID and checks registration before showing SOS.
A failed status check shows Retry, never an empty registration form or SOS. An unregistered
installation sees the form. Successful backend confirmation opens SOS. Failed submissions
retain the input for retry. The original ten-second countdown/cancellation behavior remains.
Normal app closes, restarts and updates retain the UUID; clearing app data/uninstalling can
create a new installation. Existing Android demo installations will need to register once.

## Hermes and routing

From `amesocare-ui`, rebuild the existing dashboard:

```sh
docker compose up -d --build hermes
```

For production, use your existing production compose configuration and rebuild Hermes with
the correct `NEXT_PUBLIC_*_URL` values. If using Caddy, reload/restart it after updating
`Caddyfile`; `/api/patients/*` now routes to patient-service alongside `/api/patient/*`.

Hermes retains polling, alert sound, queue styling, acknowledgement, closure, notification
history and timeline. The patient card displays all ten actual registration fields.
Simulated vitals, wearable readings and simulated ambulance names/ETAs are removed.
Legacy incidents without registration display an explicit unavailable label and message rather
than a fabricated profile. No GPS location, hospital preference or emergency contacts
are collected by this form, so new registrations have no map pin or ambulance dispatch.
Hermes still receives their incidents. Existing external notification routing requires a
configured hospital/contact and remains separate from the Hermes polling alert.

## Manual verification

No automated test files were added. Build and manual API verification cover validation,
idempotent concurrent registration, PostgreSQL persistence, status lookup, rejecting an
unregistered SOS, server-side patient resolution and the incident detail payload. The
manual data and service containers used for verification are isolated from the running
application database. Android emulator checks confirmed first-launch registration,
required-field and invalid-age errors, successful submission, relaunch directly to SOS,
and successful SOS submission with Kafka available. A Chrome browser check confirmed
Hermes renders the original nine stored registration fields without simulated readings. Fresh initialization and migration
of the original seeded schema both succeeded. Device restarts/app updates should preserve SharedPreferences;
clearing app data is the way to exercise a new installation.

The phone extension passed Android/backend/Hermes builds and manual API/PostgreSQL
checks: missing, short, long, prefixed and non-digit values were rejected; a valid
10-digit phone was persisted and returned unchanged, including its leading zero.
Duplicate registration remained idempotent.

## Files changed

- Backend: new `RegistrationController`, `RegistrationService`, `PatientRegistration`,
  `PatientRegistrationRepository`, `RegistrationValidation`, and `ApiErrors`; updated
  patient, incident and notification applications, `NotificationDispatcher`, `Templates`,
  `Contracts`, and `AmesoConfig`.
- Database: `db/init/03-patient-registration.sql` and `04-patient-phone.sql` for fresh
  databases; `db/migrations/001-patient-registration.sql` and `002-patient-phone.sql`
  for existing databases.
- Routing/docs: `Caddyfile`, `docs/api-spec.yaml`, and this guide.
- Android: `MainActivity.kt`, `SosViewModel.kt`, `SosApi.kt`, new `RegistrationScreen.kt`,
  `app/build.gradle.kts`, `gradle.properties`, and `README.md`.
- Hermes: `hermes/components/Dashboard.tsx` and `hermes/lib/api.ts`.
