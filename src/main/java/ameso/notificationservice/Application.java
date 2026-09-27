package ameso.notificationservice;

import ameso.shared.Ameso;
import ameso.shared.Contracts;
import ameso.shared.Contracts.IncidentEvent;
import ameso.shared.Db;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication(scanBasePackages = {"ameso.shared", "ameso.notificationservice"})
@RestController
public class Application {
    private static final Logger log = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) {
        Ameso.run(Application.class, "notification-service", args);
    }

    @Autowired Db db;
    @Autowired NotificationDispatcher dispatcher;
    @Autowired ObjectMapper json;

    record ManualNotifyRequest(String incidentId) {}
    record IncidentLocation(String patientId, double latitude, double longitude) {}
    record NotificationRow(
            int id, String incidentId, String recipientType, String recipientName,
            String channel, String destination, String subject, String status,
            OffsetDateTime sentAt, OffsetDateTime deliveredAt) {}

    /**
     * Consumes incident-events; IncidentAcknowledged triggers the notification fan-out
     * (hospital + emergency contacts). Spring Kafka keeps retrying until the broker is up.
     */
    @KafkaListener(topics = Contracts.INCIDENT_EVENTS, groupId = "notification-service")
    void consume(String message) throws Exception {
        var evt = json.readValue(message, IncidentEvent.class);
        if (evt == null || !"IncidentAcknowledged".equals(evt.eventType())) return;

        log.info("Processing ACK for incident {}", evt.incidentId());
        dispatcher.dispatchForIncident(evt.incidentId(), evt.patientId(), evt.latitude(), evt.longitude());
    }

    // Manual triggers (normal path is Kafka-driven on ACK)
    @PostMapping({"/api/notifications/hospital", "/api/notifications/emergency-contacts"})
    ResponseEntity<?> notify(@RequestBody ManualNotifyRequest req) {
        var incident = location(req.incidentId());
        if (incident == null) return ResponseEntity.notFound().build();

        dispatcher.dispatchForIncident(req.incidentId(), incident.patientId(), incident.latitude(), incident.longitude());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/api/notifications/ambulance")
    ResponseEntity<?> ambulance(@RequestBody ManualNotifyRequest req) {
        var incident = location(req.incidentId());
        if (incident == null) return ResponseEntity.notFound().build();

        dispatcher.dispatchAmbulance(req.incidentId(), incident.patientId(), incident.latitude(), incident.longitude());
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/api/notifications")
    List<NotificationRow> notifications(@RequestParam String incidentId) {
        return db.query(NotificationRow.class, """
                SELECT id, incident_id AS incidentId, recipient_type AS recipientType, recipient_name AS recipientName,
                       channel, destination, subject, status, sent_at AS sentAt, delivered_at AS deliveredAt
                FROM notification WHERE incident_id = :incidentId ORDER BY id
                """, "incidentId", incidentId);
    }

    private IncidentLocation location(String incidentId) {
        return db.one(IncidentLocation.class,
                "SELECT patient_id AS patientId, latitude, longitude FROM incident WHERE id = :id", "id", incidentId);
    }
}
