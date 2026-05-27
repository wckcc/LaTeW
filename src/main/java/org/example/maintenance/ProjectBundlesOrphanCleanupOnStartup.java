package org.example.maintenance;

import org.example.dto.ProjectBundlesCleanupReport;
import org.example.service.ProjectBundlesCleanupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 应用启动后删除未被任何项目引用的 {@code static/project_bundles} 目录。
 * 默认关闭，避免多实例或误配时意外删盘。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "latew", name = "cleanup-orphan-bundles-on-startup", havingValue = "true")
public class ProjectBundlesOrphanCleanupOnStartup {

    @Autowired
    private ProjectBundlesCleanupService cleanupService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            ProjectBundlesCleanupReport report = cleanupService.cleanupOrphanBundles(false);
            log.info(
                    "Startup orphan bundle cleanup: referencedDirs={}, orphanDirsRemoved={}",
                    report.getReferencedBundleDirCount(),
                    report.getRemovedOrWouldRemove().size());
        } catch (Exception e) {
            log.warn("Startup orphan bundle cleanup skipped or failed: {}", e.getMessage());
        }
    }
}
