package io.github.khajamohammeddev.configcore.spring;

import io.github.khajamohammeddev.configcore.api.ConfigWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets the admin app change config through this service, using this service's own database
 * credentials, so the admin app never holds credentials for every service's database.
 *
 * <p>Guarded by a shared secret for now; OIDC replaces it once the admin app exists.
 */
@RestController
@RequestMapping("/internal/config")
class InternalConfigController {

    static final String SECRET_HEADER = "X-Config-Core-Secret";

    private static final Logger log = LoggerFactory.getLogger(InternalConfigController.class);

    private final ConfigWriter writer;
    private final byte[] secret;

    InternalConfigController(ConfigWriter writer, String secret) {
        this.writer = writer;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Writes the value to the store and returns 204 once it is persisted. Caches, including this
     * instance's, update shortly after through the change stream.
     */
    @PostMapping("/update")
    ResponseEntity<Void> update(
            @RequestHeader(name = SECRET_HEADER, required = false) String providedSecret,
            @RequestBody(required = false) UpdateRequest request) {
        if (!secretMatches(providedSecret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (request == null || request.key() == null || request.key().isBlank() || request.value() == null) {
            return ResponseEntity.badRequest().build();
        }
        writer.put(request.key(), request.value());
        log.info("Config '{}' updated via internal endpoint", request.key());
        return ResponseEntity.noContent().build();
    }

    private boolean secretMatches(String provided) {
        // Constant-time comparison so response timing doesn't reveal how much of the secret matched
        return provided != null && MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8), secret);
    }

    record UpdateRequest(String key, String value) {
    }
}
