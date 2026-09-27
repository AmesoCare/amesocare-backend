package ameso.authservice;

import ameso.shared.Ameso;
import ameso.shared.Contracts.LoginRequest;
import ameso.shared.Db;
import ameso.shared.Events;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication(scanBasePackages = {"ameso.shared", "ameso.authservice"})
@RestController
public class Application {

    public static void main(String[] args) {
        Ameso.run(Application.class, "auth-service", args);
    }

    @Autowired Db db;
    @Autowired Events events;
    @Value(Ameso.JWT_KEY) String jwtKey;

    record UserRow(String username, String displayName, String role, String patientId) {}
    record LoginResponse(String token, String username, String displayName, String role, String patientId) {}

    @PostMapping("/api/auth/login")
    ResponseEntity<?> login(@RequestBody LoginRequest req) throws Exception {
        var hash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(req.password().getBytes(StandardCharsets.UTF_8)));

        var user = db.one(UserRow.class,
                "SELECT username, display_name AS displayName, role, patient_id AS patientId FROM app_user WHERE username = :username AND password_hash = :hash",
                "username", req.username(), "hash", hash);

        if (user == null) {
            events.audit("LoginFailed", req.username(), null, null);
            return ResponseEntity.status(401).build();
        }

        var claims = new JWTClaimsSet.Builder()
                .claim(Ameso.NAME_CLAIM, user.username())
                .claim("displayName", user.displayName())
                .claim(Ameso.ROLE_CLAIM, user.role());
        if (user.patientId() != null) claims.claim("patientId", user.patientId());
        claims.expirationTime(Date.from(Instant.now().plus(Duration.ofHours(8)))).issuer(Ameso.JWT_ISSUER);

        var token = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(), claims.build());
        token.sign(new MACSigner(jwtKey.getBytes(StandardCharsets.UTF_8)));

        events.audit("LoginSucceeded", user.username(), null, null);

        return ResponseEntity.ok(new LoginResponse(
                token.serialize(), user.username(), user.displayName(), user.role(), user.patientId()));
    }
}
