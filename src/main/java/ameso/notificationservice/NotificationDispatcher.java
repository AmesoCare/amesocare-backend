package ameso.notificationservice;

import static ameso.shared.Ameso.obj;

import ameso.shared.Db;
import ameso.shared.Events;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NotificationDispatcher {
    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    /**
     * Channel provider abstraction. POC ships mock providers that log and simulate
     * delivery; swap for Twilio / WhatsApp Business API / SMTP in Phase 2.
     */
    public interface ChannelProvider {
        boolean send(String destination, String subject, String body);
    }

    public record PatientInfo(String name, int age, String bloodGroup, String[] medicalConditions,
                              String hospitalName, String hospitalEmail, String hospitalPhone) {}
    public record ContactInfo(String name, String phone, String email, String preferredChannel) {}

    private static final String PATIENT_SELECT = """
            SELECT p.name, p.age, p.blood_group AS bloodGroup, p.medical_conditions AS medicalConditions,
                   h.name AS hospitalName, h.email AS hospitalEmail, h.phone AS hospitalPhone
            FROM patient p JOIN hospital h ON h.id = p.preferred_hospital_id
            WHERE p.id = :patientId
            """;
    private static final String CONTACTS_SELECT =
            "SELECT name, phone, email, preferred_channel AS preferredChannel FROM emergency_contact WHERE patient_id = :patientId";

    private final Db db;
    private final Events events;
    private final WhatsAppCloudApiProvider whatsApp;

    public NotificationDispatcher(Db db, Events events, WhatsAppCloudApiProvider whatsApp) {
        this.db = db;
        this.events = events;
        this.whatsApp = whatsApp;
    }

    private ChannelProvider provider(String channel) {
        if (channel.equals("WhatsApp")) return whatsApp;
        return (destination, subject, body) -> {
            sleep(150); // simulate provider latency
            log.info("[MOCK {}] to {} | {}\n{}", channel, destination, subject == null ? "(no subject)" : subject, body);
            return true;
        };
    }

    /** Notify hospital + all emergency contacts for an acknowledged incident. */
    public void dispatchForIncident(String incidentId, String patientId, Double lat, Double lon) {
        var patient = db.one(PatientInfo.class, PATIENT_SELECT, "patientId", patientId);
        if (patient == null) {
            log.warn("Patient {} not found; skipping notifications", patientId);
            return;
        }

        var contacts = db.query(ContactInfo.class, CONTACTS_SELECT, "patientId", patientId);

        // 1. Hospital (email)
        var primaryContactPhone = contacts.isEmpty() ? "n/a" : contacts.get(0).phone();
        var body = Templates.hospital(patient.name(), patient.age(), patient.bloodGroup(), lat, lon,
                patient.medicalConditions(), primaryContactPhone, incidentId);
        sendAndRecord(incidentId, "Hospital", patient.hospitalName(), "Email", patient.hospitalEmail(), Templates.HOSPITAL_SUBJECT, body);

        // 2. Emergency contacts on their preferred channel
        for (var c : contacts) {
            switch (c.preferredChannel()) {
                case "Email" -> sendAndRecord(incidentId, "EmergencyContact", c.name(), "Email",
                        c.email() != null ? c.email() : c.phone(), "Emergency Alert",
                        Templates.emergencyContact(patient.name(), lat, lon, incidentId));
                case "WhatsApp" -> sendAndRecord(incidentId, "EmergencyContact", c.name(), "WhatsApp", c.phone(), null,
                        Templates.emergencyContactAck(patient.name(), patient.hospitalName()));
                default -> sendAndRecord(incidentId, "EmergencyContact", c.name(), "SMS", c.phone(), null,
                        Templates.sms(patient.name(), lat, lon));
            }
        }

        db.exec("""
                INSERT INTO incident_history (incident_id, event_type, actor, details) VALUES
                (:incidentId, 'HospitalNotified', 'system', :hospital),
                (:incidentId, 'ContactsNotified', 'system', :contactSummary)
                """, "incidentId", incidentId, "hospital", patient.hospitalName(),
                "contactSummary", contacts.size() + " contact(s) notified");
    }

    /** Call Ambulance — WhatsApp the preferred hospital with patient, coordinates, and emergency contacts. */
    public void dispatchAmbulance(String incidentId, String patientId, Double lat, Double lon) {
        var patient = db.one(PatientInfo.class, PATIENT_SELECT, "patientId", patientId);
        if (patient == null) {
            log.warn("Patient {} not found; skipping ambulance dispatch", patientId);
            return;
        }

        var contacts = db.query(ContactInfo.class, CONTACTS_SELECT, "patientId", patientId);

        var body = Templates.ambulanceDispatch(patient.name(), lat, lon, incidentId, contacts);
        sendAndRecord(incidentId, "Hospital", patient.hospitalName(), "WhatsApp", patient.hospitalPhone(), "Ambulance Dispatch Request", body);

        db.exec("INSERT INTO incident_history (incident_id, event_type, actor, details) VALUES (:incidentId, 'AmbulanceRequested', 'system', :hospital)",
                "incidentId", incidentId, "hospital", patient.hospitalName());
    }

    public void sendAndRecord(String incidentId, String recipientType, String recipientName,
                              String channel, String destination, String subject, String body) {
        var id = db.scalar(Integer.class, """
                INSERT INTO notification (incident_id, recipient_type, recipient_name, channel, destination, subject, body, status)
                VALUES (:incidentId, :recipientType, :recipientName, :channel, :destination, :subject, :body, 'Pending')
                RETURNING id
                """, "incidentId", incidentId, "recipientType", recipientType, "recipientName", recipientName,
                "channel", channel, "destination", destination, "subject", subject, "body", body);

        var ok = provider(channel).send(destination, subject, body);

        db.exec(ok
                        ? "UPDATE notification SET status = 'Delivered', sent_at = now(), delivered_at = now() WHERE id = :id"
                        : "UPDATE notification SET status = 'Failed', sent_at = now() WHERE id = :id",
                "id", id);

        events.audit(ok ? "NotificationDelivered" : "NotificationFailed", "system", incidentId,
                obj("channel", channel, "recipientType", recipientType, "recipientName", recipientName));
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
