package ameso.patientservice;

import static ameso.shared.Ameso.obj;

import ameso.shared.Ameso;
import ameso.shared.Db;
import ameso.shared.Events;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication(scanBasePackages = {"ameso.shared", "ameso.patientservice"})
@RestController
public class Application {

    public static void main(String[] args) {
        Ameso.run(Application.class, "patient-service", args);
    }

    @Autowired Db db;
    @Autowired Events events;

    static final String PATIENT_SELECT = """
            SELECT p.id, p.name, p.age, p.blood_group AS bloodGroup, p.phone, p.address,
                   p.photo_url AS photoUrl, p.medical_conditions AS medicalConditions,
                   p.medications, p.preferred_hospital_id AS preferredHospitalId,
                   p.home_latitude AS homeLatitude, p.home_longitude AS homeLongitude
            FROM patient p
            """;

    record PatientRow(
            String id, String name, int age, String bloodGroup, String phone, String address,
            String photoUrl, String[] medicalConditions, String[] medications,
            String preferredHospitalId, double homeLatitude, double homeLongitude) {}
    record ContactRow(String name, String relation, String phone, String email, String preferredChannel) {}
    record HospitalRow(String id, String name, String address, String phone, String email, double latitude, double longitude) {}
    record PrevIncidentRow(String id, String status, String severity, OffsetDateTime createdAt, String closeReason) {}
    record PatientUpdate(String phone, String address) {}

    @GetMapping("/api/patient/{id}")
    ResponseEntity<?> get(@PathVariable String id) {
        var patient = db.one(PatientRow.class, PATIENT_SELECT + " WHERE p.id = :id", "id", id);
        if (patient == null) return ResponseEntity.notFound().build();

        var contacts = db.query(ContactRow.class,
                "SELECT name, relation, phone, email, preferred_channel AS preferredChannel FROM emergency_contact WHERE patient_id = :id",
                "id", id);

        var hospital = db.one(HospitalRow.class,
                "SELECT id, name, address, phone, email, latitude, longitude FROM hospital WHERE id = :hid",
                "hid", patient.preferredHospitalId());

        var previousIncidents = db.query(PrevIncidentRow.class,
                "SELECT id, status, severity, created_at AS createdAt, close_reason AS closeReason FROM incident WHERE patient_id = :id AND status IN ('Closed','Cancelled') ORDER BY created_at DESC LIMIT 10",
                "id", id);

        return ResponseEntity.ok(obj("patient", patient, "contacts", contacts, "hospital", hospital, "previousIncidents", previousIncidents));
    }

    @PutMapping("/api/patient/{id}")
    ResponseEntity<?> update(@PathVariable String id, @RequestBody PatientUpdate update) {
        var rows = db.exec(
                "UPDATE patient SET phone = COALESCE(CAST(:phone AS text), phone), address = COALESCE(CAST(:address AS text), address), updated_at = now() WHERE id = :id",
                "id", id, "phone", update.phone(), "address", update.address());
        if (rows == 0) return ResponseEntity.notFound().build();

        events.audit("PatientUpdated", null, null, obj("id", id, "update", update));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/hospitals")
    List<HospitalRow> hospitals() {
        return db.query(HospitalRow.class, "SELECT id, name, address, phone, email, latitude, longitude FROM hospital");
    }
}
