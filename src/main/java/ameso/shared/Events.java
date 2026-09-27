package ameso.shared;

import ameso.shared.Contracts.AuditEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Kafka event publisher + audit helper. */
@Component
public class Events {
    private static final Logger log = LoggerFactory.getLogger(Events.class);

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;
    private final String service;

    public Events(KafkaTemplate<String, String> kafka, ObjectMapper json, @Value("${ameso.service-name}") String service) {
        this.kafka = kafka;
        this.json = json;
        this.service = service;
    }

    public void publish(String topic, Object event) {
        try {
            kafka.send(topic, toJson(event)).get();
        } catch (Exception e) {
            // POC: log and continue — do not fail the API call because the broker is down
            log.error("Kafka publish failed for topic {}", topic, e);
        }
    }

    public void audit(String eventType, String actor, String incidentId, Object payload) {
        publish(Contracts.AUDIT_EVENTS, new AuditEvent(
                eventType, service, actor, incidentId,
                payload == null ? null : toJson(payload),
                OffsetDateTime.now(ZoneOffset.UTC)));
    }

    public String toJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
