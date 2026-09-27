package ameso.auditservice;

import ameso.shared.Ameso;
import ameso.shared.Contracts;
import ameso.shared.Contracts.AuditEvent;
import ameso.shared.Db;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication(scanBasePackages = {"ameso.shared", "ameso.auditservice"})
@RestController
public class Application {

    public static void main(String[] args) {
        Ameso.run(Application.class, "audit-service", args);
    }

    @Autowired Db db;
    @Autowired ObjectMapper json;

    record AuditRow(
            int id, String eventType, String service, String actor,
            String incidentId, String payload, OffsetDateTime occurredAt) {}

    @GetMapping("/api/audit")
    List<AuditRow> audit(@RequestParam(required = false) String incidentId, @RequestParam(required = false) Integer limit) {
        return db.query(AuditRow.class, """
                SELECT id, event_type AS eventType, service, actor, incident_id AS incidentId,
                       CAST(payload AS text) AS payload, occurred_at AS occurredAt
                FROM audit_log
                WHERE (CAST(:incidentId AS text) IS NULL OR incident_id = :incidentId)
                ORDER BY occurred_at DESC
                LIMIT :limit
                """, "incidentId", incidentId, "limit", Math.clamp(limit == null ? 100 : limit, 1, 1000));
    }

    /** Consumes audit-events and persists them to audit_log. Spring Kafka keeps retrying until the broker is up. */
    @KafkaListener(topics = Contracts.AUDIT_EVENTS, groupId = "audit-service")
    void consume(String message) throws Exception {
        var evt = json.readValue(message, AuditEvent.class);
        if (evt == null) return;

        db.exec("""
                INSERT INTO audit_log (event_type, service, actor, incident_id, payload, occurred_at)
                VALUES (:eventType, :service, :actor, :incidentId, CAST(:payloadJson AS jsonb), :occurredAt)
                """, "eventType", evt.eventType(), "service", evt.service(), "actor", evt.actor(),
                "incidentId", evt.incidentId(), "payloadJson", evt.payloadJson(), "occurredAt", evt.occurredAt());
    }
}
