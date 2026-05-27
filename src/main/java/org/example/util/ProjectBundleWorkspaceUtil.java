package org.example.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 解析带 %TEMPLATE_SOURCE_PATH= 的项目内容，并定位 zip 解压后的 bundle 根目录（与 {@link LatexCompileUtil} 规则一致）。
 */
public final class ProjectBundleWorkspaceUtil {

    public static final String PREFIX = "%TEMPLATE_SOURCE_PATH=";

    public record ParsedBundle(String entryAbsolutePath, String entryBody) {
    }

    private ProjectBundleWorkspaceUtil() {
    }

    /**
     * 若首行为入口 .tex 绝对路径，则返回解析结果；否则返回 null。
     */
    public static ParsedBundle parseStoredContent(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String normalized = content.replace("\r\n", "\n");
        int firstLineEnd = normalized.indexOf('\n');
        String firstLine = firstLineEnd >= 0 ? normalized.substring(0, firstLineEnd) : normalized;
        if (!firstLine.startsWith(PREFIX)) {
            return null;
        }
        String sourcePathRaw = firstLine.substring(PREFIX.length()).trim();
        String remaining = firstLineEnd >= 0 ? normalized.substring(firstLineEnd + 1) : "";
        if (sourcePathRaw.isEmpty()) {
            return null;
        }
        try {
            Path entry = Paths.get(sourcePathRaw).toAbsolutePath().normalize();
            if (!Files.exists(entry) || !Files.isRegularFile(entry)) {
                return null;
            }
            return new ParsedBundle(entry.toString(), remaining);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 仅从首行解析入口 .tex 绝对路径，不校验文件是否存在（删除项目、孤儿清理等场景使用）。
     */
    public static Optional<Path> parseTemplateSourceEntryPathFromContent(String content) {
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }
        String normalized = content.replace("\r\n", "\n");
        int firstLineEnd = normalized.indexOf('\n');
        String firstLine = firstLineEnd >= 0 ? normalized.substring(0, firstLineEnd) : normalized;
        if (!firstLine.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String sourcePathRaw = firstLine.substring(PREFIX.length()).trim();
        if (sourcePathRaw.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Paths.get(sourcePathRaw).toAbsolutePath().normalize());
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public static Path locateBundleRoot(Path sourceTexPath) {
        if (sourceTexPath == null) {
            return null;
        }
        Path cursor = sourceTexPath.getParent();
        while (cursor != null) {
            Path name = cursor.getFileName();
            if (name != null && name.toString().startsWith("bundle_")) {
                return cursor;
            }
            cursor = cursor.getParent();
        }
        return sourceTexPath.getParent();
    }

    /**
     * 将相对路径解析为 bundle 内的绝对路径，并校验防逃逸。
     */
    public static Path resolveWithinBundle(Path bundleRoot, String relativePath) {
        if (bundleRoot == null || relativePath == null || relativePath.isBlank()) {
            throw new RuntimeException("路径无效");
        }
        Path normalizedRel = Paths.get(relativePath.replace("\\", "/")).normalize();
        if (normalizedRel.isAbsolute() || normalizedRel.startsWith("..")) {
            throw new RuntimeException("非法文件路径");
        }
        Path target = bundleRoot.resolve(normalizedRel).normalize();
        if (!target.startsWith(bundleRoot)) {
            throw new RuntimeException("非法文件路径");
        }
        return target;
    }

    /**
     * 列出 bundle 内可编辑的文本类文件（相对路径，使用 /）。
     */
    public static List<String> listEditableRelativeFiles(Path bundleRoot) throws Exception {
        if (bundleRoot == null || !Files.exists(bundleRoot) || !Files.isDirectory(bundleRoot)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(bundleRoot)) {
            List<Path> paths = stream.filter(Files::isRegularFile).collect(Collectors.toList());
            for (Path file : paths) {
                Path rel = bundleRoot.relativize(file);
                String relStr = rel.toString().replace('\\', '/');
                if (shouldSkip(relStr)) {
                    continue;
                }
                if (isEditableWorkspaceFile(relStr, file.getFileName().toString())) {
                    out.add(relStr);
                }
            }
        }
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    /**
     * 打包/列出时忽略的无关路径（与 zip 导入解压产物一致）。
     */
    public static boolean shouldSkipBundleArchiveEntry(String relStr) {
        String lower = relStr.toLowerCase(Locale.ROOT);
        return lower.contains("__macosx/")
                || lower.endsWith(".ds_store")
                || lower.endsWith("thumbs.db");
    }

    private static boolean shouldSkip(String relStr) {
        return shouldSkipBundleArchiveEntry(relStr);
    }

    private static boolean isEditableWorkspaceFile(String relStr, String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return "makefile".equalsIgnoreCase(fileName) || "latexmkrc".equalsIgnoreCase(fileName);
        }
        String ext = fileName.substring(dot).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case ".tex", ".bib", ".sty", ".cls", ".bst", ".clo", ".fd", ".def", ".cfg", ".ldf",
                 ".txt", ".md", ".csv", ".ins", ".dtx", ".ltx", ".mf", ".mpl", ".mk", ".ist" -> true;
            default -> false;
        };
    }

    public static void writeUtf8(Path file, String text) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(file, text == null ? "" : text, StandardCharsets.UTF_8);
    }
}
