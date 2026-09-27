package ameso.notificationservice;

import static ameso.shared.Ameso.obj;

import ameso.shared.Events;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Real WhatsApp Business (Meta Cloud API) sender. Configure via env vars:
 *   WhatsApp__AccessToken     — permanent or temporary token from Meta App dashboard
 *   WhatsApp__PhoneNumberId   — the sending number's Phone Number ID
 *   WhatsApp__ApiVersion      — optional, defaults to v21.0
 * Without those two required values, falls back to logging + simulated delivery
 * so the POC runs end-to-end without a live Meta Business account.
 */
@Component
public class WhatsAppCloudApiProvider implements NotificationDispatcher.ChannelProvider {
    private static final Logger log = LoggerFactory.getLogger(WhatsAppCloudApiProvider.class);

    private final HttpClient http = HttpClient.newHttpClient();
    private final Environment env;
    private final Events events;

    public WhatsAppCloudApiProvider(Environment env, Events events) {
        this.env = env;
        this.events = events;
    }

    @Override
    public boolean send(String destination, String subject, String body) {
        var accessToken = env.getProperty("WhatsApp__AccessToken");
        var phoneNumberId = env.getProperty("WhatsApp__PhoneNumberId");
        var apiVersion = env.getProperty("WhatsApp__ApiVersion", "v21.0");

        if (accessToken == null || accessToken.isBlank() || phoneNumberId == null || phoneNumberId.isBlank()) {
            NotificationDispatcher.sleep(150); // simulate provider latency
            log.info("[MOCK WhatsApp — set WhatsApp__AccessToken / WhatsApp__PhoneNumberId for real delivery] to {}\n{}",
                    destination, body);
            return true;
        }

        var to = destination.replace(" ", "").replace("+", "");
        var payload = obj("messaging_product", "whatsapp", "to", to, "type", "text", "text", obj("body", body));

        try {
            var request = HttpRequest.newBuilder(URI.create(
                            "https://graph.facebook.com/" + apiVersion + "/" + phoneNumberId + "/messages"))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(events.toJson(payload)))
                    .build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.error("WhatsApp Cloud API send failed ({}) to {}: {}", response.statusCode(), destination, response.body());
                return false;
            }
            log.info("[WhatsApp] delivered to {}: {}", destination, response.body());
            return true;
        } catch (Exception e) {
            log.error("WhatsApp Cloud API send threw for {}", destination, e);
            return false;
        }
    }
}
