package io.github.khajamohammeddev.configadmin.web;

import io.github.khajamohammeddev.configadmin.client.ServiceCallException;
import io.github.khajamohammeddev.configadmin.client.ServiceClient;
import io.github.khajamohammeddev.configadmin.registry.InstanceRegistry;
import io.github.khajamohammeddev.configadmin.registry.ServiceSummary;
import io.github.khajamohammeddev.configcore.api.ConfigHistoryEntry;
import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;

/**
 * Write path of the UI. Every change takes two steps: an update is edited, then reviewed against the
 * current value before it is applied; a delete is shown with its current value before it is confirmed.
 * Writes go through the service's own internal endpoints, never to its database directly.
 *
 * <p>There is no login yet, so "changed by" is whatever the user types; it is remembered in the session
 * to save retyping. Phase 6 replaces it with the authenticated user.
 */
@Controller
@RequestMapping("/services/{serviceName}")
class ConfigEditController {

    static final String CHANGED_BY_SESSION_KEY = "config-admin.changedBy";

    private final InstanceRegistry registry;
    private final ServiceClient client;
    private final String basePath;

    /** {@code basePath} is the dashboard's path prefix ({@code ""} at the root), used for redirects. */
    ConfigEditController(InstanceRegistry registry, ServiceClient client, String basePath) {
        this.registry = registry;
        this.client = client;
        this.basePath = basePath;
    }

    /** The edit form. Pre-filled from the parameters, e.g. by the history page's "Restore" links. */
    @GetMapping("/edit")
    String edit(@PathVariable String serviceName, ChangeForm form, HttpSession session, Model model) {
        ServiceSummary service = findService(serviceName);
        ChangeForm filled = form.withChangedByDefault(rememberedChangedBy(session));
        if (filled.key() != null) {
            Optional<String> current = currentValue(serviceName, filled.key(), model);
            model.addAttribute("current", current.orElse(null));
            if (filled.value() == null) {
                filled = filled.withValue(current.orElse(null)); // editing: start from the current value
            }
        }
        return editPage(service, filled, model);
    }

    @PostMapping("/edit/review")
    String review(@PathVariable String serviceName, ChangeForm form, HttpSession session, Model model) {
        ServiceSummary service = findService(serviceName);
        List<String> errors = form.validate();
        if (!errors.isEmpty()) {
            model.addAttribute("errors", errors);
            return editPage(service, form, model);
        }
        remember(session, form.changedBy());
        Optional<String> current;
        try {
            current = lookUpCurrent(serviceName, form.key());
        } catch (ServiceCallException e) {
            model.addAttribute("errors", List.of(e.getMessage()));
            return editPage(service, form, model);
        }
        if (current.isPresent() && current.get().equals(form.value())) {
            model.addAttribute("errors", List.of("'" + form.key() + "' already has this value; nothing to change."));
            return editPage(service, form, model);
        }
        model.addAttribute("service", service);
        model.addAttribute("form", form);
        model.addAttribute("current", current.orElse(null));
        model.addAttribute("review", true);
        return "config-admin/edit";
    }

    @PostMapping("/update")
    String update(@PathVariable String serviceName, ChangeForm form, HttpSession session, Model model,
            RedirectAttributes redirect) {
        ServiceSummary service = findService(serviceName);
        List<String> errors = form.validate();
        if (!errors.isEmpty()) {
            model.addAttribute("errors", errors);
            return editPage(service, form, model);
        }
        remember(session, form.changedBy());
        Optional<ConfigHistoryEntry> entry;
        try {
            entry = client.update(serviceName, form.key(), form.value(), form.changedBy(), form.comment());
        } catch (ServiceCallException e) {
            model.addAttribute("errors", List.of("Update failed: " + e.getMessage()));
            return editPage(service, form, model);
        }
        redirect.addFlashAttribute("notice", entry
                .map(e -> (e.oldValue() == null ? "Created '" : "Updated '") + e.key() + "' (v" + e.version()
                        + "). All instances pick it up within about a second.")
                .orElse("'" + form.key() + "' already had this value; nothing changed."));
        return "redirect:" + basePath + "/services/{serviceName}";
    }

    @GetMapping("/delete")
    String confirmDelete(@PathVariable String serviceName, ChangeForm form, HttpSession session, Model model) {
        ServiceSummary service = findService(serviceName);
        if (ChangeForm.isBlank(form.key())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key is required");
        }
        ChangeForm filled = form.withChangedByDefault(rememberedChangedBy(session));
        model.addAttribute("current", currentValue(serviceName, filled.key(), model).orElse(null));
        return deletePage(service, filled, model);
    }

    @PostMapping("/delete")
    String delete(@PathVariable String serviceName, ChangeForm form, HttpSession session, Model model,
            RedirectAttributes redirect) {
        ServiceSummary service = findService(serviceName);
        List<String> errors = form.validateDeletion();
        if (!errors.isEmpty()) {
            model.addAttribute("errors", errors);
            return deletePage(service, form, model);
        }
        remember(session, form.changedBy());
        Optional<ConfigHistoryEntry> entry;
        try {
            entry = client.delete(serviceName, form.key(), form.changedBy(), form.comment());
        } catch (ServiceCallException e) {
            model.addAttribute("errors", List.of("Delete failed: " + e.getMessage()));
            return deletePage(service, form, model);
        }
        redirect.addFlashAttribute("notice", entry
                .map(e -> "Deleted '" + e.key() + "' (v" + e.version() + "). Its history is kept; restore it from there.")
                .orElse("'" + form.key() + "' was already deleted; nothing changed."));
        return "redirect:" + basePath + "/services/{serviceName}";
    }

    private String editPage(ServiceSummary service, ChangeForm form, Model model) {
        model.addAttribute("service", service);
        model.addAttribute("form", form);
        model.addAttribute("review", false);
        return "config-admin/edit";
    }

    private String deletePage(ServiceSummary service, ChangeForm form, Model model) {
        model.addAttribute("service", service);
        model.addAttribute("form", form);
        return "config-admin/delete";
    }

    /** The key's current value, or empty if it has none (or the lookup failed, which is shown as an error). */
    private Optional<String> currentValue(String serviceName, String key, Model model) {
        try {
            return lookUpCurrent(serviceName, key);
        } catch (ServiceCallException e) {
            model.addAttribute("errors", List.of(e.getMessage()));
            return Optional.empty();
        }
    }

    private Optional<String> lookUpCurrent(String serviceName, String key) {
        return Optional.ofNullable(client.currentConfig(serviceName).get(key));
    }

    private ServiceSummary findService(String serviceName) {
        return registry.service(serviceName).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "No service named '" + serviceName + "' is registered"));
    }

    private static String rememberedChangedBy(HttpSession session) {
        return session.getAttribute(CHANGED_BY_SESSION_KEY) instanceof String s ? s : null;
    }

    private static void remember(HttpSession session, String changedBy) {
        session.setAttribute(CHANGED_BY_SESSION_KEY, changedBy);
    }

    /**
     * Form fields shared by the update and delete flows. Blank comments count as none; values are kept
     * exactly as typed (an empty value is a valid value).
     */
    record ChangeForm(String key, String value, String changedBy, String comment) {

        ChangeForm {
            key = trimToNull(key);
            changedBy = trimToNull(changedBy);
            comment = trimToNull(comment);
        }

        List<String> validate() {
            List<String> errors = validateDeletion();
            if (value == null) {
                errors.add("Value is required (it may be empty).");
            }
            return errors;
        }

        List<String> validateDeletion() {
            List<String> errors = new ArrayList<>();
            if (key == null) {
                errors.add("Key is required.");
            }
            if (changedBy == null) {
                errors.add("Enter your name, so the change can be traced back to you.");
            }
            return errors;
        }

        ChangeForm withValue(String newValue) {
            return new ChangeForm(key, newValue, changedBy, comment);
        }

        ChangeForm withChangedByDefault(String remembered) {
            return new ChangeForm(key, value, changedBy != null ? changedBy : remembered, comment);
        }

        static boolean isBlank(String s) {
            return s == null || s.isBlank();
        }

        private static String trimToNull(String s) {
            return isBlank(s) ? null : s.trim();
        }
    }
}
