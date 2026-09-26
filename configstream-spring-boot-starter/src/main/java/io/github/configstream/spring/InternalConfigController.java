package io.github.configstream.spring;

import io.github.configstream.api.ConfigDeletion;
import io.github.configstream.api.ConfigHistory;
import io.github.configstream.api.ConfigHistoryEntry;
import io.github.configstream.api.ConfigUpdate;
import io.github.configstream.api.ConfigWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    static final String SECRET_HEADER = "X-ConfigStream-Secret";
    static final int DEFAULT_HISTORY_LIMIT = 50;
    static final int MAX_HISTORY_LIMIT = 500;

    private static final Logger log = LoggerFactory.getLogger(InternalConfigController.class);

    private final ConfigService config;
    private final ConfigWriter writer;
    private final ConfigHistory history;
    private final byte[] secret;

    InternalConfigController(ConfigService config, ConfigWriter writer, ConfigHistory history, String secret) {
        this.config = config;
        this.writer = writer;
        this.history = history;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    /** Every config value as this instance currently sees it, sorted by key. */
    @GetMapping
    ResponseEntity<Map<String, String>> current(
            @RequestHeader(name = SECRET_HEADER, required = false) String providedSecret) {
        if (!secretMatches(providedSecret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(new TreeMap<>(config.getAll()));
    }

    /**
     * Writes the value and its history entry. Returns 200 with the recorded {@link ConfigHistoryEntry},
     * or 204 if the key already had this value. Caches, including this instance's, update shortly
     * after through the change stream.
     *
     * <p>To roll back, send the historical value again with a comment such as {@code "Reverted to v3"}.
     */
    @PostMapping("/update")
    ResponseEntity<ConfigHistoryEntry> update(
            @RequestHeader(name = SECRET_HEADER, required = false) String providedSecret,
            @RequestBody(required = false) UpdateRequest request) {
        if (!secretMatches(providedSecret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (request == null || isBlank(request.key()) || request.value() == null || isBlank(request.changedBy())) {
            return ResponseEntity.badRequest().build();
        }
        return writer.write(new ConfigUpdate(request.key(), request.value(), request.changedBy(), request.comment()))
                .map(entry -> {
                    log.info("Config '{}' updated to v{} by {} via internal endpoint",
                            entry.key(), entry.version(), entry.changedBy());
                    return ResponseEntity.ok(entry);
                })
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * Soft-deletes the key: it leaves every cache but keeps its history. Returns 200 with the recorded
     * {@link ConfigHistoryEntry}, or 204 if the key has no value (never existed or already deleted).
     * To restore it, write a value again.
     */
    @PostMapping("/delete")
    ResponseEntity<ConfigHistoryEntry> delete(
            @RequestHeader(name = SECRET_HEADER, required = false) String providedSecret,
            @RequestBody(required = false) DeleteRequest request) {
        if (!secretMatches(providedSecret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (request == null || isBlank(request.key()) || isBlank(request.changedBy())) {
            return ResponseEntity.badRequest().build();
        }
        return writer.delete(new ConfigDeletion(request.key(), request.changedBy(), request.comment()))
                .map(entry -> {
                    log.info("Config '{}' deleted (v{}) by {} via internal endpoint",
                            entry.key(), entry.version(), entry.changedBy());
                    return ResponseEntity.ok(entry);
                })
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Changes to one key, newest first. {@code limit} defaults to 50 and is capped at 500. */
    @GetMapping("/history")
    ResponseEntity<List<ConfigHistoryEntry>> history(
            @RequestHeader(name = SECRET_HEADER, required = false) String providedSecret,
            @RequestParam(required = false) String key,
            @RequestParam(required = false) Integer limit) {
        if (!secretMatches(providedSecret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (isBlank(key) || (limit != null && limit <= 0)) {
            return ResponseEntity.badRequest().build();
        }
        int effectiveLimit = limit == null ? DEFAULT_HISTORY_LIMIT : Math.min(limit, MAX_HISTORY_LIMIT);
        return ResponseEntity.ok(history.history(key, effectiveLimit));
    }

    private boolean secretMatches(String provided) {
        // Constant-time comparison so response timing doesn't reveal how much of the secret matched
        return provided != null && MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8), secret);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** {@code changedBy} is the admin app's authenticated user; {@code comment} is optional. */
    record UpdateRequest(String key, String value, String changedBy, String comment) {
    }

    record DeleteRequest(String key, String changedBy, String comment) {
    }
}
