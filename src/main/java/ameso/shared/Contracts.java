package ameso.shared;

import java.time.OffsetDateTime;

public final class Contracts {
    // ---------- Kafka topics ----------
    public static final String INCIDENT_EVENTS = "incident-events";
    public static final String AUDIT_EVENTS = "audit-events";

    // ---------- Event contracts ----------
    public record IncidentEvent(
            String eventType,        // IncidentCreated | IncidentAcknowledged | IncidentClosed
            String incidentId,
            String patientId,
            String patientName,
            String severity,
            Double latitude,
            Double longitude,
            String actor,
            OffsetDateTime occurredAt) {}

    public record AuditEvent(
            String eventType,
            String service,
            String actor,
            String incidentId,
            String payloadJson,
            OffsetDateTime occurredAt) {}

    // ---------- API DTOs ----------
    public record SosRequest(String deviceId) {}

    public record LoginRequest(String username, String password) {}

    public record AckRequest(String ackedBy) {}
    public record CloseRequest(String closedBy, String reason) {}

    private Contracts() {}
}
