package org.example.maintenance;

import org.example.LaTeWapplication;
import org.example.dto.ImageOrphanCleanupReport;
import org.example.dto.PdfOrphanCleanupReport;
import org.example.dto.ProjectBundlesCleanupReport;
import org.example.service.ExportPdfCleanupService;
import org.example.service.ProjectBundlesCleanupService;
import org.example.service.ProjectUploadedImageCleanupService;
import org.example.service.TemplateBundlesCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 命令行一次性清理：{@code mvn spring-boot:run -Dspring-boot.run.profiles=cleanup-bundles}
 * <p>
 * 真正删除：追加 {@code -Dspring-boot.run.arguments=--latew.cleanup-bundles.dry-run=false}。
 * 同一次运行会清理：① {@code project_bundles} 孤儿目录；② {@code static/templates} 下未被项目/模板表引用的 {@code bundle_*} 孤儿目录；
 * ③ {@code static/pdf} 中未在 {@code pdf_word_file} 登记的孤儿 PDF；
 * ④ {@code static/images} 中未被任何项目 {@code latex_content} 引用的上传图（{@code project_*_xxxxxxxx.ext}）。
 */
@Component
@Profile("cleanup-bundles")
@Order(Integer.MAX_VALUE)
public class ProjectBundlesCleanupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProjectBundlesCleanupRunner.class);

    @Value("${latew.cleanup-bundles.dry-run:true}")
    private boolean dryRun;

    @Autowired
    private ProjectBundlesCleanupService cleanupService;

    @Autowired
    private ExportPdfCleanupService exportPdfCleanupService;

    @Autowired
    private ProjectUploadedImageCleanupService projectUploadedImageCleanupService;

    @Autowired
    private TemplateBundlesCleanupService templateBundlesCleanupService;

    @Autowired
    private ConfigurableApplicationContext applicationContext;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Project bundle cleanup starting (dryRun={})", dryRun);
        ProjectBundlesCleanupReport report = cleanupService.cleanupOrphanBundles(dryRun);
        log.info("Referenced bundle dirs: {}", report.getReferencedBundleDirCount());
        log.info("Orphan bundle dirs: {}", report.getOrphanBundleDirCount());
        for (String line : report.getOrphanBundleDirs()) {
            log.info("Orphan: {}", line);
        }
        if (dryRun) {
            log.info("Dry run — no project bundle directories deleted. Pass --latew.cleanup-bundles.dry-run=false to delete.");
        } else {
            log.info("Deleted (or attempted) {} orphan project bundle director(ies).", report.getRemovedOrWouldRemove().size());
        }

        log.info("Orphan template bundle cleanup starting (dryRun={})", dryRun);
        ProjectBundlesCleanupReport tmplReport = templateBundlesCleanupService.cleanupOrphanTemplateBundles(dryRun);
        log.info("Referenced template bundle dirs: {}", tmplReport.getReferencedBundleDirCount());
        log.info("Orphan template bundle dirs: {}", tmplReport.getOrphanBundleDirCount());
        for (String line : tmplReport.getOrphanBundleDirs()) {
            log.info("Orphan template bundle: {}", line);
        }
        if (dryRun) {
            log.info("Dry run — no template bundle directories deleted.");
        } else {
            log.info("Deleted (or attempted) {} orphan template bundle director(ies).", tmplReport.getRemovedOrWouldRemove().size());
        }

        log.info("Orphan PDF cleanup starting (dryRun={})", dryRun);
        PdfOrphanCleanupReport pdfReport = exportPdfCleanupService.cleanupOrphanPdfs(dryRun);
        log.info("Tracked PDF paths in DB: {}", pdfReport.getTrackedFileCount());
        log.info("Orphan PDF files on disk: {}", pdfReport.getOrphanFileCount());
        for (String line : pdfReport.getOrphanFiles()) {
            log.info("Orphan PDF: {}", line);
        }
        if (dryRun) {
            log.info("Dry run — no PDF files deleted (same dry-run flag as bundles).");
        } else {
            log.info("Deleted (or attempted) {} orphan PDF file(s).", pdfReport.getRemovedOrWouldRemove().size());
        }

        log.info("Orphan uploaded-image cleanup starting (dryRun={})", dryRun);
        ImageOrphanCleanupReport imgReport = projectUploadedImageCleanupService.cleanupOrphanUploadedImages(dryRun);
        log.info("Referenced uploaded image basenames (in project latex_content): {}", imgReport.getReferencedBasenameCount());
        log.info("Orphan uploaded image files on disk: {}", imgReport.getOrphanFileCount());
        for (String line : imgReport.getOrphanFiles()) {
            log.info("Orphan image: {}", line);
        }
        if (dryRun) {
            log.info("Dry run — no image files deleted (same dry-run flag as bundles).");
        } else {
            log.info("Deleted (or attempted) {} orphan uploaded image file(s).", imgReport.getRemovedOrWouldRemove().size());
        }

        int code = SpringApplication.exit(applicationContext, () -> 0);
        System.exit(code);
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(LaTeWapplication.class);
        app.setAdditionalProfiles("cleanup-bundles");
        app.run(args);
    }
}
