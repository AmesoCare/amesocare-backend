package ameso.notificationservice;

import ameso.notificationservice.NotificationDispatcher.ContactInfo;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

public final class Templates {

    /** Formats a double the way .NET does (59.3293, 13 — never "13.0"). */
    static String num(Double value) {
        if (value == null) return "Not provided";
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    public static String mapsLink(Double lat, Double lon) {
        if (lat == null || lon == null) return "Location not provided";
        return "https://maps.google.com/?q=" + num(lat) + "," + num(lon);
    }

    public static final String HOSPITAL_SUBJECT = "Emergency Patient Alert";

    public static String hospital(
            String patientName, int age, String bloodGroup, Double lat, Double lon,
            String[] conditions, String emergencyContactPhone, String incidentId) {
        return """
                EMERGENCY ALERT

                Patient:
                %s

                Age:
                %d

                Blood Group:
                %s

                Current Location:
                %s

                Medical Conditions:
                %s

                Emergency Contact:
                %s

                Incident ID:
                %s

                Please dispatch emergency assistance immediately.""".formatted(
                patientName, age, bloodGroup, mapsLink(lat, lon), String.join("\n", conditions),
                emergencyContactPhone, incidentId);
    }

    /** WhatsApp message sent to emergency contacts the moment Hermes ACKs the incident. */
    public static String emergencyContactAck(String patientName, String hospitalName) {
        return """
                AMESO-CARE EMERGENCY ALERT
                ---------------------------
                %s has activated an emergency SOS.

                %s has acknowledged the emergency and is coordinating assistance.

                An emergency assistance vehicle will be dispatched shortly.""".formatted(patientName, hospitalName);
    }

    public static String ambulanceDispatch(
            String patientName, Double lat, Double lon, String incidentId, List<ContactInfo> contacts) {
        return """
                AMBULANCE DISPATCH REQUEST

                Patient:
                %s

                Location (lat, long):
                %s, %s
                %s

                Emergency Contacts:
                %s

                Incident ID:
                %s

                Please dispatch an ambulance immediately.""".formatted(
                patientName, num(lat), num(lon), mapsLink(lat, lon),
                contacts.stream().map(c -> c.name() + " — " + c.phone()).collect(Collectors.joining("\n")),
                incidentId);
    }

    public static String emergencyContact(String patientName, Double lat, Double lon, String incidentId) {
        return """
                EMERGENCY ALERT

                %s has activated an emergency SOS.

                Location:
                %s

                Hermes Customer Care has acknowledged the emergency and is coordinating assistance.

                Incident ID:
                %s""".formatted(patientName, mapsLink(lat, lon), incidentId);
    }

    public static String sms(String patientName, Double lat, Double lon) {
        return """
                Emergency Alert

                %s activated SOS.

                Location:
                %s

                Hermes is coordinating assistance.""".formatted(patientName, mapsLink(lat, lon));
    }

    private Templates() {}
}
