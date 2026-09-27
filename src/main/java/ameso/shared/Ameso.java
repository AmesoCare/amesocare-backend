package ameso.shared;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.builder.SpringApplicationBuilder;

public final class Ameso {
    public static final String JWT_KEY = "${Jwt__Key:ameso-hermes-poc-dev-signing-key-0123456789}";
    public static final String JWT_ISSUER = "ameso-auth";
    // Claim names kept identical to the tokens the .NET auth-service issued
    public static final String NAME_CLAIM = "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name";
    public static final String ROLE_CLAIM = "http://schemas.microsoft.com/ws/2008/06/identity/claims/role";

    /** Starts a service; scans ameso.shared plus the app's own package. */
    public static void run(Class<?> app, String serviceName, String[] args) {
        new SpringApplicationBuilder(app).properties("ameso.service-name=" + serviceName).run(args);
    }

    /** Ordered JSON object (anonymous-object equivalent). Allows null values. */
    public static Map<String, Object> obj(Object... kv) {
        var map = new LinkedHashMap<String, Object>();
        for (int i = 0; i < kv.length; i += 2) map.put((String) kv[i], kv[i + 1]);
        return map;
    }

    private Ameso() {}
}
