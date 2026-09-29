package ameso.patientservice;

import ameso.shared.PatientRegistration;
import ameso.shared.PatientRegistrationRepository;
import ameso.shared.RegistrationValidation;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RegistrationService {
    public record Request(String deviceId, String patientName, String age, String gender, String address,
                          String phone, String bloodGroup, String medication, String allergies, String surgery, String remarks) {}

    private final PatientRegistrationRepository repository;
    public RegistrationService(PatientRegistrationRepository repository) { this.repository = repository; }

    public PatientRegistration register(Request r) {
        var deviceId = RegistrationValidation.deviceId(r.deviceId());
        var name = RegistrationValidation.text(r.patientName(), "Patient name", true);
        var phone = r.phone();
        if (phone == null || !phone.matches("[0-9]{10}"))
            throw RegistrationValidation.invalid("Phone number must contain exactly 10 digits, without a country code");
        var age = RegistrationValidation.text(r.age(), "Age", true);
        if (!age.matches("[0-9]{1,3}") || Integer.parseInt(age) < 1 || Integer.parseInt(age) > 120)
            throw RegistrationValidation.invalid("Age must be a whole number from 1 to 120");
        var gender = RegistrationValidation.text(r.gender(), "Gender", true);
        if (!java.util.Set.of("Male", "Female").contains(gender))
            throw RegistrationValidation.invalid("Select Male or Female");
        var blood = RegistrationValidation.text(r.bloodGroup(), "Blood group", true);
        if (!java.util.Set.of("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-").contains(blood))
            throw RegistrationValidation.invalid("Select a valid blood group");
        return repository.insert(new PatientRegistration("PAT-" + UUID.randomUUID(), deviceId,
                name, Integer.toString(Integer.parseInt(age)), gender,
                RegistrationValidation.text(r.address(), "Address", true), phone, blood,
                RegistrationValidation.text(r.medication(), "Medication", false),
                RegistrationValidation.text(r.allergies(), "Allergies", false),
                RegistrationValidation.text(r.surgery(), "Surgery", false),
                RegistrationValidation.text(r.remarks(), "Remarks", false), null, null));
    }

    public PatientRegistration find(String deviceId) {
        return repository.find(RegistrationValidation.deviceId(deviceId));
    }
}
