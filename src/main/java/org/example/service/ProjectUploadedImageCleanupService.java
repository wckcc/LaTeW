package org.example.service;

import org.example.dto.ImageOrphanCleanupReport;
import org.example.mapper.ProjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 对比 {@code project.latex_content} 与 {@code static/images}：删除未被任何项目正文引用的
 * {@code project_{id}_{8位hex}.(jpg|jpeg|png|gif|webp)} 上传图文件（与 {@link ExportPdfCleanupService} 同类维护任务）。
 */
@Service
public class ProjectUploadedImageCleanupService {

    /**
     * 与 {@link org.example.controller.ProjectController} 上传命名一致：{@code project_数字_uuid前8位.扩展名}。
     */
    private static final Pattern UPLOADED_IMAGE_BASENAME_IN_CONTENT = Pattern.compile(
            "\\b(project_\\d+_[a-fA-F0-9]{8}\\.(?:jpg|jpeg|png|gif|webp))\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern UPLOADED_IMAGE_FILENAME_ON_DISK = Pattern.compile(
            "^project_\\d+_[a-fA-F0-9]{8}\\.(?:jpg|jpeg|png|gif|webp)$",
            Pattern.CASE_INSENSITIVE);

    @Autowired
    private ProjectMapper projectMapper;

    @Value("${image.upload.path:./static/images}")
    private String imageUploadPath;

    public ImageOrphanCleanupReport cleanupOrphanUploadedImages(boolean dryRun) {
        Path imageBase = Paths.get(imageUploadPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(imageBase)) {
            return new ImageOrphanCleanupReport(dryRun, 0, 0);
        }

        Set<String> referenced = new HashSet<>();
        List<String> contents = projectMapper.selectAllLatexContents();
        if (contents != null) {
            for (String c : contents) {
                if (c == null || c.isBlank()) {
                    continue;
                }
                Matcher m = UPLOADED_IMAGE_BASENAME_IN_CONTENT.matcher(c);
                while (m.find()) {
                    referenced.add(m.group(1).toLowerCase(Locale.ROOT));
                }
            }
        }

        List<Path> onDisk = new ArrayList<>();
        try (Stream<Path> stream = Files.list(imageBase)) {
            stream.filter(Files::isRegularFile)
                    .forEach(p -> {
                        Path name = p.getFileName();
                        if (name != null
                                && UPLOADED_IMAGE_FILENAME_ON_DISK.matcher(name.toString()).matches()) {
                            onDisk.add(p.toAbsolutePath().normalize());
                        }
                    });
        } catch (Exception e) {
            throw new RuntimeException("无法列出图片上传目录: " + e.getMessage(), e);
        }

        List<Path> orphans = onDisk.stream()
                .filter(p -> {
                    String fn = p.getFileName().toString().toLowerCase(Locale.ROOT);
                    return !referenced.contains(fn);
                })
                .sorted(Comparator.comparing(Path::toString))
                .toList();

        ImageOrphanCleanupReport report = new ImageOrphanCleanupReport(dryRun, referenced.size(), orphans.size());
        orphans.forEach(o -> report.addOrphanFile(o.toString()));

        for (Path orphan : orphans) {
            if (dryRun) {
                report.addRemovedOrWouldRemove(orphan.toString());
            } else {
                deleteFileQuietly(orphan);
                report.addRemovedOrWouldRemove(orphan.toString());
            }
        }
        return report;
    }

    private void deleteFileQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
        }
    }
}
