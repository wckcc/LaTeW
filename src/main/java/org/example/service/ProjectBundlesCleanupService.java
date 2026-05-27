package org.example.service;

import org.example.dto.ProjectBundlesCleanupReport;
import org.example.mapper.ProjectMapper;
import org.example.util.ProjectBundleWorkspaceUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 对比数据库中项目引用的 bundle 与 {@code static/project_bundles} 下目录，删除未被任何项目引用的孤儿目录。
 */
@Service
public class ProjectBundlesCleanupService {

    @Autowired
    private ProjectMapper projectMapper;

    public ProjectBundlesCleanupReport cleanupOrphanBundles(boolean dryRun) {
        Path bundlesBase = Paths.get("./static/project_bundles").toAbsolutePath().normalize();
        if (!Files.isDirectory(bundlesBase)) {
            return new ProjectBundlesCleanupReport(dryRun, 0, 0);
        }

        Set<Path> referencedRoots = new HashSet<>();
        List<String> contents = projectMapper.selectAllLatexContents();
        if (contents != null) {
            for (String c : contents) {
                Optional<Path> entryOpt = ProjectBundleWorkspaceUtil.parseTemplateSourceEntryPathFromContent(c);
                if (entryOpt.isEmpty()) {
                    continue;
                }
                Path bundleRoot = ProjectBundleWorkspaceUtil.locateBundleRoot(entryOpt.get());
                if (bundleRoot == null) {
                    continue;
                }
                Path norm = bundleRoot.toAbsolutePath().normalize();
                if (norm.startsWith(bundlesBase)) {
                    referencedRoots.add(norm);
                }
            }
        }

        Set<Path> onDiskBundleDirs = new HashSet<>();
        try (Stream<Path> stream = Files.list(bundlesBase)) {
            stream.filter(Files::isDirectory)
                    .filter(p -> {
                        Path name = p.getFileName();
                        return name != null && name.toString().startsWith("bundle_");
                    })
                    .forEach(p -> onDiskBundleDirs.add(p.toAbsolutePath().normalize()));
        } catch (Exception e) {
            throw new RuntimeException("无法列出 project_bundles 目录: " + e.getMessage(), e);
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
