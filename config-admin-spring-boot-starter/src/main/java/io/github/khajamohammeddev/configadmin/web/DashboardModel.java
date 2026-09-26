package io.github.khajamohammeddev.configadmin.web;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Gives the dashboard templates the path prefix for their links, as {@code configAdminBase}. A top-level
 * class, not nested in {@link ConfigAdminWebConfiguration}, which would register it a second time.
 */
@ControllerAdvice(assignableTypes = {DashboardController.class, ConfigEditController.class})
class DashboardModel {

    private final String basePath;

    DashboardModel(String basePath) {
        this.basePath = basePath;
    }

    @ModelAttribute("configAdminBase")
    String basePath() {
        return basePath;
    }
}
