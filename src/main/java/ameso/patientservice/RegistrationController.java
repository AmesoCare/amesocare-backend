package ameso.patientservice;

import static ameso.shared.Ameso.obj;
import org.springframework.web.bind.annotation.*;

@RestController
public class RegistrationController {
    private final RegistrationService service;
    public RegistrationController(RegistrationService service) { this.service = service; }

    @PostMapping("/api/patients/register")
    public Object register(@RequestBody RegistrationService.Request request) {
        return obj("registered", true, "patient", service.register(request));
    }

    @GetMapping("/api/patients/device/{deviceId}")
    public Object status(@PathVariable String deviceId) {
        var patient = service.find(deviceId);
        return obj("registered", patient != null, "patient", patient);
    }
}
