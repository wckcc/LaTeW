package org.example.service;

import org.example.dto.ProjectBundlesCleanupReport;
import org.example.mapper.ProjectMapper;
import org.example.mapper.TemplateMapper;
import org.example.util.ProjectBundleWorkspaceUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 对比数据库中项目与模板引用的 bundle 与 {@code static/templates} 下 {@code bundle_*} 目录，删除未被引用的孤儿目录。
 * <p>
 * 仅处理名为 {@code bundle_*} 的子目录；散落的 {@code template_*.tex} 等不在此删除范围内。
 */
@Service
public class TemplateBundlesCleanupService {

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private TemplateMapper templateMapper;

    public ProjectBundlesCleanupReport cleanupOrphanTemplateBundles(boolean dryRun) {
        Path templatesBase = Paths.get("./static/templates").toAbsolutePath().normalize();
        if (!Files.isDirectory(templatesBase)) {
            return new ProjectBundlesCleanupReport(dryRun, 0, 0);
        }

        Set<Path> referencedRoots = new HashSet<>();
        List<String> projectContents = projectMapper.selectAllLatexContents();
        if (projectContents != null) {
            for (String c : projectContents) {
                addReferencedBundleUnderTemplates(c, templatesBase, referencedRoots);
            }
        }
        List<String> templatePaths = templateMapper.selectAllTemplatePaths();
        if (templatePaths != null) {
            for (String tp : templatePaths) {
                resolveEntryPathFromTemplatePathColumn(tp).ifPresent(entry ->
                        addBundleRootIfUnderTemplates(entry, templatesBase, referencedRoots));
            }
        }

        Set<Path> onDiskBundleDirs = new HashSet<>();
        try (Stream<Path> stream = Files.list(templatesBase)) {
            stream.filter(Files::isDirectory)
                    .filter(p -> {
                        Path name = p.getFileName();
                        return name != null && name.toString().startsWith("bundle_");
                    })
                    .forEach(p -> onDiskBundleDirs.add(p.toAbsolutePath().normalize()));
        } catch (Exception e) {
            throw new RuntimeException("无法列出 templates 目录: " + e.getMessage(), e);
        }

        List<Path> orphans = onDiskBundleDirs.stream()
                .filter(p -> !referencedRoots.contains(p))
                .sorted(Comparator.comparing(Path::toString))
                .toList();

        ProjectBundlesCleanupReport report = new ProjectBundlesCleanupReport(
                dryRun, referencedRoots.size(), orphans.size());
        referencedRoots.stream().sorted(Comparator.comparing(Path::toString)).forEach(r -> report.addReferencedBundleDir(r.toString()));
        orphans.forEach(o -> report.addOrphanBundleDir(o.toString()));

        for (Path orphan : orphans) {
            if (dryRun) {
                report.addRemovedOrWouldRemove(orphan.toString());
            } else {
                deleteDirectoryTree(orphan);
                report.addRemovedOrWouldRemove(orphan.toString());
            }
        }
        return report;
    }

    private static void addReferencedBundleUnderTemplates(String content, Path templatesBase, Set<Path> referencedRoots) {
        Optional<Path> entryOpt = ProjectBundleWorkspaceUtil.parseTemplateSourceEntryPathFromContent(content);
        if (entryOpt.isEmpty()) {
            return;
        }
        addBundleRootIfUnderTemplates(entryOpt.get(), templatesBase, referencedRoots);
    }

    private static void addBundleRootIfUnderTemplates(Path entryPath, Path templatesBase, Set<Path> referencedRoots) {
        Path bundleRoot = ProjectBundleWorkspaceUtil.locateBundleRoot(entryPath);
        if (bundleRoot == null) {
            return;
        }
        Path norm = bundleRoot.toAbsolutePath().normalize();
        if (!norm.startsWith(templatesBase)) {
            return;
        }
        Path name = norm.getFileName();
        if (name != null && name.toString().startsWith("bundle_")) {
            referencedRoots.add(norm);
        }
    }

    /**
     * template_path 列：多为单行主 .tex 绝对路径；少数可能为首行 {@code %TEMPLATE_SOURCE_PATH=}。
     */
    private static Optional<Path> resolveEntryPathFromTemplatePathColumn(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String n = raw.replace("\r\n", "\n").trim();
        if (n.startsWith(ProjectBundleWorkspaceUtil.PREFIX)) {
            return ProjectBundleWorkspaceUtil.parseTemplateSourceEntryPathFromContent(n);
        }
        int nl = n.indexOf('\n');
        String first = nl >= 0 ? n.substring(0, nl).trim() : n;
        if (!first.toLowerCase(Locale.ROOT).endsWith(".tex")) {
            return Optional.empty();
        }
        try {
            Path p = Paths.get(first);
            if (!p.isAbsolute()) {
                p = Paths.get(".").resolve(first).normalize();
            }
            return Optional.of(p.toAbsolutePath().normalize());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private void deleteDirectoryTree(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(this::deletePathQuietly);
        } catch (Exception ignored) {
        }
    }

    private void deletePathQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
        }
    }
}
