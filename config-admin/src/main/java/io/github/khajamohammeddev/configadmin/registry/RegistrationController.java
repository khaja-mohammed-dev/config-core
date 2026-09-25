package io.github.khajamohammeddev.configadmin.registry;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The API config-core instances call to register, heartbeat and deregister. Unauthenticated until
 * Phase 6 adds security.
 */
@RestController
@RequestMapping("/api/instances")
class RegistrationController {

    private final InstanceRegistry registry;

    RegistrationController(InstanceRegistry registry) {
        this.registry = registry;
    }

    @PostMapping
    ResponseEntity<Void> register(@RequestBody(required = false) InstanceRegistration registration) {
        if (registration == null || !registration.isValid()) {
            return ResponseEntity.badRequest().build();
        }
        registry.register(registration);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /** 404 tells the instance this registry doesn't know it (e.g. after a restart), so it re-registers. */
    @PutMapping("/{instanceId}/heartbeat")
    ResponseEntity<Void> heartbeat(@PathVariable String instanceId) {
        return registry.heartbeat(instanceId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{instanceId}")
    ResponseEntity<Void> deregister(@PathVariable String instanceId) {
        registry.deregister(instanceId);
        return ResponseEntity.noContent().build();
    }
}
