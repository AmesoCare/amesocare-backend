package ameso.shared;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.HashMap;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** Shared wiring for every service: DB, JWT, CORS, JSON format, /health. */
@Configuration
@RestController
public class AmesoConfig {

    @Value("${ameso.service-name}")
    private String serviceName;

    record Health(String status, String service) {}

    @GetMapping("/health")
    Health health() {
        return new Health("healthy", serviceName);
    }

    /** Accepts the same Npgsql-style ConnectionStrings__Default the compose files already set. */
    @Bean
    DataSource dataSource(@Value("${ConnectionStrings__Default:Host=localhost;Port=5432;Database=amesohermes;Username=ameso;Password=ameso}") String cs) {
        var parts = new HashMap<String, String>();
        for (var part : cs.split(";")) {
            var kv = part.split("=", 2);
            if (kv.length == 2) parts.put(kv[0].trim().toLowerCase(), kv[1].trim());
        }
        var ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:postgresql://" + parts.getOrDefault("host", "localhost") + ":"
                + parts.getOrDefault("port", "5432") + "/" + parts.get("database"));
        ds.setUsername(parts.get("username"));
        ds.setPassword(parts.get("password"));
        return ds;
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http
                .csrf(c -> c.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/health", "/api/auth/login", "/error", "/swagger/**", "/swagger-ui/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()))
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(@Value(Ameso.JWT_KEY) String jwtKey) {
        var decoder = NimbusJwtDecoder
                .withSecretKey(new SecretKeySpec(jwtKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ofMinutes(1)),
                new JwtIssuerValidator(Ameso.JWT_ISSUER)));
        return decoder;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of("*"));
        cors.setAllowedMethods(List.of("*"));
        cors.setAllowedHeaders(List.of("*"));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    /** Timestamps as "2026-09-25T10:00:05.123+00:00" — same shape the .NET services emitted. */
    @Bean
    Jackson2ObjectMapperBuilderCustomizer offsetDateTimeFormat() {
        DateTimeFormatter format = new DateTimeFormatterBuilder()
                .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .appendOffset("+HH:MM", "+00:00")
                .toFormatter();
        return b -> b.serializerByType(OffsetDateTime.class, new JsonSerializer<OffsetDateTime>() {
            @Override
            public void serialize(OffsetDateTime value, JsonGenerator gen, SerializerProvider p) throws IOException {
                gen.writeString(format.format(value));
            }
        });
    }
}
