package ameso.incidentservice;

import static ameso.shared.Ameso.obj;

import ameso.shared.Ameso;
import ameso.shared.Contracts;
import ameso.shared.Contracts.AckRequest;
import ameso.shared.Contracts.CloseRequest;
import ameso.shared.Contracts.IncidentEvent;
import ameso.shared.Contracts.SosRequest;
import ameso.shared.Db;
import ameso.shared.Events;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication(scanBasePackages = {"ameso.shared", "ameso.incidentservice"})
@RestController
public class Application {

    public static void main(String[] args) {
        Ameso.run(Application.class, "incident-service", args);
    }

    @Autowired Db db;
    @Autowired Events events;
    @Autowired ameso.shared.PatientRegistrationRepository registrations;

    record SosCancelledRequest(String deviceId) {}
    record IncidentRow(
            String id, String patientId, String patientName, String status, String severity,
            Double latitude, Double longitude, OffsetDateTime createdAt,
            OffsetDateTime ackedAt, String ackedBy,
            OffsetDateTime closedAt, String closedBy, String closeReason) {}
    record HistoryRow(String eventType, String actor, String details, OffsetDateTime occurredAt) {}
    record AckInfoRow(String patientId, String patientName, String severity, Double latitude, Double longitude) {}

    // ---------- SOS ----------
    @org.springframework.transaction.annotation.Transactional
    @PostMapping("/api/sos")
    public ResponseEntity<?> sos(@RequestBody SosRequest req) {
        var patient = registrations.find(ameso.shared.RegistrationValidation.deviceId(req.deviceId()));
        if (patient == null) return ResponseEntity.status(409).body(obj("error", "Register this installation before sending SOS"));
        var timestamp = OffsetDateTime.now(ZoneOffset.UTC);
        // Generate incident id: INC-YYYYMMDDNNNN (atomic per-day counter)
        var day = OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        var counter = db.scalar(Integer.class, """
                INSERT INTO incident_counter (day, counter) VALUES (:day, 1)
                ON CONFLICT (day) DO UPDATE SET counter = incident_counter.counter + 1
                RETURNING counter
                """, "day", day);
        var incidentId = "INC-%s%04d".formatted(day, counter);

        db.exec("""
                INSERT INTO incident (id, patient_id, status, severity, latitude, longitude, created_at)
                VALUES (:incidentId, :patientId, 'Open', 'Critical', :latitude, :longitude, :timestamp)
                """, "incidentId", incidentId, "patientId", patient.id(),
                "latitude", null, "longitude", null, "timestamp", timestamp);

        db.exec("""
                INSERT INTO incident_history (incident_id, event_type, actor, details)
                VALUES (:incidentId, 'Created', :patientId, 'SOS activated by patient')
                """, "incidentId", incidentId, "patientId", patient.id());

        events.publish(Contracts.INCIDENT_EVENTS, new IncidentEvent(
                "IncidentCreated", incidentId, patient.id(), patient.patientName(), "Critical",
                null, null, patient.id(), OffsetDateTime.now(ZoneOffset.UTC)));
        events.audit("SosActivated", patient.id(), incidentId, req);
        events.audit("IncidentCreated", patient.id(), incidentId, null);

        return ResponseEntity.created(URI.create("/api/incidents/" + incidentId))
                .body(obj("incidentId", incidentId, "status", "Open"));
    }

    @PostMapping("/api/sos/cancelled")
    ResponseEntity<?> sosCancelled(@RequestBody SosCancelledRequest req) {
        var patient = registrations.find(ameso.shared.RegistrationValidation.deviceId(req.deviceId()));
        if (patient == null) return ResponseEntity.status(409).body(obj("error", "Installation is not registered"));
        // Patient cancelled during the 10-second window — no incident created, audit only
        events.audit("SosCancelled", patient.id(), null, req);
        return ResponseEntity.accepted().build();
    }

    // ---------- Incidents ----------
    static final String INCIDENT_SELECT = """
            SELECT i.id, i.patient_id AS patientId,
                   CASE WHEN p.device_id IS NOT NULL THEN p.name ELSE 'Registration unavailable' END AS patientName,
                   i.status, i.severity,
                   i.latitude, i.longitude, i.created_at AS createdAt,
                   i.acked_at AS ackedAt, i.acked_by AS ackedBy,
                   i.closed_at AS closedAt, i.closed_by AS closedBy, i.close_reason AS closeReason
            FROM incident i JOIN patient p ON p.id = i.patient_id
            """;

    static final String ACK_INFO_SELECT = """
            SELECT i.patient_id AS patientId, p.name AS patientName, i.severity, i.latitude, i.longitude
            FROM incident i JOIN patient p ON p.id = i.patient_id WHERE i.id = :id
            """;

    @GetMapping("/api/incidents")
    List<IncidentRow> incidents(@RequestParam(required = false) String status) {
        var sql = INCIDENT_SELECT + (status == null ? "" : " WHERE i.status = :status") + " ORDER BY i.created_at DESC LIMIT 200";
        return db.query(IncidentRow.class, sql, "status", status);
    }

    @GetMapping("/api/incidents/{id}")
    ResponseEntity<?> incident(@PathVariable String id) {
        var incident = db.one(IncidentRow.class, INCIDENT_SELECT + " WHERE i.id = :id", "id", id);
        if (incident == null) return ResponseEntity.notFound().build();

        var history = db.query(HistoryRow.class,
                "SELECT event_type AS eventType, actor, details, occurred_at AS occurredAt FROM incident_history WHERE incident_id = :id ORDER BY occurred_at",
                "id", id);

        var registration = db.one(ameso.shared.PatientRegistration.class, """
                SELECT id, device_id::text AS deviceId, name AS patientName, age::text AS age,
                       gender, address, phone, blood_group AS bloodGroup, medication, allergies, surgery, remarks,
                       created_at AS createdAt, updated_at AS updatedAt
                FROM patient WHERE id = :id AND device_id IS NOT NULL
                """, "id", incident.patientId());
        return ResponseEntity.ok(obj("incident", incident, "history", history, "patient", registration));
    }

    @PostMapping("/api/incidents/{id}/ack")
    ResponseEntity<?> ack(@PathVariable String id, @RequestBody AckRequest req, @AuthenticationPrincipal Jwt user) {
        var actor = actor(req.ackedBy(), user);

        var rows = db.exec(
                "UPDATE incident SET status = 'Acknowledged', acked_at = now(), acked_by = :actor WHERE id = :id AND status = 'Open'",
                "id", id, "actor", actor);
        if (rows == 0) return ResponseEntity.status(409).body(obj("error", "Incident not found or not in Open state"));

        db.exec("INSERT INTO incident_history (incident_id, event_type, actor) VALUES (:id, 'Acknowledged', :actor)",
                "id", id, "actor", actor);

        var info = db.one(AckInfoRow.class, ACK_INFO_SELECT, "id", id);

        // Async fan-out: notification-service consumes this and notifies hospital + contacts
        events.publish(Contracts.INCIDENT_EVENTS, new IncidentEvent(
                "IncidentAcknowledged", id, info.patientId(), info.patientName(), info.severity(),
                info.latitude(), info.longitude(), actor, OffsetDateTime.now(ZoneOffset.UTC)));
        events.audit("IncidentAcknowledged", actor, id, null);

        return ResponseEntity.ok(obj("incidentId", id, "status", "Acknowledged", "ackedBy", actor));
    }

    @PostMapping("/api/incidents/{id}/close")
    ResponseEntity<?> close(@PathVariable String id, @RequestBody CloseRequest req, @AuthenticationPrincipal Jwt user) {
        var actor = actor(req.closedBy(), user);

        var rows = db.exec(
                "UPDATE incident SET status = 'Closed', closed_at = now(), closed_by = :actor, close_reason = :reason WHERE id = :id AND status IN ('Open','Acknowledged')",
                "id", id, "actor", actor, "reason", req.reason());
        if (rows == 0) return ResponseEntity.status(409).body(obj("error", "Incident not found or already closed"));

        db.exec("INSERT INTO incident_history (incident_id, event_type, actor, details) VALUES (:id, 'Closed', :actor, :reason)",
                "id", id, "actor", actor, "reason", req.reason());

        var info = db.one(AckInfoRow.class, ACK_INFO_SELECT, "id", id);

        events.publish(Contracts.INCIDENT_EVENTS, new IncidentEvent(
                "IncidentClosed", id, info.patientId(), info.patientName(), info.severity(),
                info.latitude(), info.longitude(), actor, OffsetDateTime.now(ZoneOffset.UTC)));
        events.audit("IncidentClosed", actor, id, obj("reason", req.reason()));

        return ResponseEntity.ok(obj("incidentId", id, "status", "Closed"));
    }

    private static String actor(String explicit, Jwt user) {
        return Stream.of(explicit, user.getClaimAsString("displayName"), user.getClaimAsString(Ameso.NAME_CLAIM), "care-executive")
                .filter(s -> s != null).findFirst().get();
    }
}
