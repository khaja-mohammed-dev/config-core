package io.github.configstream.admin.web;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Gives the dashboard templates the path prefix for their links, as {@code configStreamAdminBase}. A top-level
 * class, not nested in {@link ConfigStreamAdminWebConfiguration}, which would register it a second time.
 */
@ControllerAdvice(assignableTypes = {DashboardController.class, ConfigEditController.class})
class DashboardModel {

    private final String basePath;

    DashboardModel(String basePath) {
        this.basePath = basePath;
    }

    @ModelAttribute("configStreamAdminBase")
    String basePath() {
        return basePath;
    }
}
