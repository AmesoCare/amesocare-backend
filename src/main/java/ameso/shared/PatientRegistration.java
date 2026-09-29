package ameso.shared;

import java.time.OffsetDateTime;

/** Registration shares the existing patient identity used by incidents. */
public record PatientRegistration(
        String id, String deviceId, String patientName, String age, String gender,
        String address, String phone, String bloodGroup, String medication, String allergies,
        String surgery, String remarks, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
