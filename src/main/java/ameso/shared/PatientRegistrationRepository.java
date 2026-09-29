package ameso.shared;

import org.springframework.stereotype.Repository;

@Repository
public class PatientRegistrationRepository {
    private final Db db;

    public PatientRegistrationRepository(Db db) { this.db = db; }

    private static final String SELECT = """
            SELECT id, device_id::text AS deviceId, name AS patientName, age::text AS age,
                   gender, address, phone, blood_group AS bloodGroup, medication, allergies, surgery, remarks,
                   created_at AS createdAt, updated_at AS updatedAt
            FROM patient WHERE device_id = CAST(:deviceId AS uuid)
            """;

    public PatientRegistration find(String deviceId) {
        return db.one(PatientRegistration.class, SELECT, "deviceId", deviceId);
    }

    public PatientRegistration insert(PatientRegistration p) {
        // Atomic conflict handling makes retries and concurrent registration idempotent.
        // An existing installation's details are never overwritten by a retry.
        db.exec("""
                INSERT INTO patient (id, device_id, name, age, gender, address, phone, blood_group,
                                     medication, allergies, surgery, remarks)
                VALUES (:id, CAST(:deviceId AS uuid), :name, :age, :gender, :address, :phone, :bloodGroup,
                        :medication, :allergies, :surgery, :remarks)
                ON CONFLICT (device_id) DO NOTHING
                """, "id", p.id(), "deviceId", p.deviceId(), "name", p.patientName(),
                "age", Integer.parseInt(p.age()), "gender", p.gender(), "address", p.address(), "phone", p.phone(),
                "bloodGroup", p.bloodGroup(), "medication", p.medication(), "allergies", p.allergies(),
                "surgery", p.surgery(), "remarks", p.remarks());
        return find(p.deviceId());
    }
}
