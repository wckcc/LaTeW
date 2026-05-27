package org.example.service;

import org.example.dto.PdfOrphanCleanupReport;
import org.example.mapper.PdfWordFileMapper;
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
import java.util.stream.Stream;

/**
 * 对比 {@code pdf_word_file} 与 {@code static/pdf} 目录：删除磁盘上未被任何导出记录引用的 PDF。
 */
@Service
public class ExportPdfCleanupService {

    @Autowired
    private PdfWordFileMapper pdfWordFileMapper;

    @Value("${latex.compile.output-dir:./static/pdf}")
    private String pdfOutputDir;

    private static String pathKey(Path p) {
        return p.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
    }

    private static String pathKeyFromDb(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return pathKey(Paths.get(raw.trim()));
        } catch (Exception e) {
            return null;
        }
    }

    public PdfOrphanCleanupReport cleanupOrphanPdfs(boolean dryRun) {
        Path pdfBase = Paths.get(pdfOutputDir).toAbsolutePath().normalize();
        if (!Files.isDirectory(pdfBase)) {
            return new PdfOrphanCleanupReport(dryRun, 0, 0);
        }

        Set<String> trackedKeys = new HashSet<>();
        List<String> rows = pdfWordFileMapper.selectAllTrackedPdfPaths();
        if (rows != null) {
            for (String row : rows) {
                String k = pathKeyFromDb(row);
                if (k != null) {
                    trackedKeys.add(k);
                }
            }
        }

        List<Path> onDisk = new ArrayList<>();
        try (Stream<Path> stream = Files.list(pdfBase)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> {
                        Path name = p.getFileName();
                        return name != null
                                && name.toString().toLowerCase(Locale.ROOT).endsWith(".pdf");
                    })
                    .forEach(p -> onDisk.add(p.toAbsolutePath().normalize()));
        } catch (Exception e) {
            throw new RuntimeException("无法列出 PDF 输出目录: " + e.getMessage(), e);
        }

        List<Path> orphans = onDisk.stream()
                .filter(p -> !trackedKeys.contains(pathKey(p)))
                .sorted(Comparator.comparing(Path::toString))
                .toList();

        PdfOrphanCleanupReport report = new PdfOrphanCleanupReport(dryRun, trackedKeys.size(), orphans.size());
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
