package org.example.util;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 将 LaTeX 项目/模板的 zip 解压到指定目录下的 bundle 子目录，并收集 .tex 文件路径。
 * 与模板 zip 导入使用相同的安全解压规则（防 Zip Slip）。
 */
public final class ZipLatexBundleUtil {

    private ZipLatexBundleUtil() {
    }

    /**
     * Servlet / Spring 的响应流由框架关闭；{@link ZipOutputStream#close} 会级联关闭底层流，
     * 可能导致 “response already committed” 等错误，故包装为不在 close 时关闭 delegate。
     */
    private static OutputStream nonClosing(OutputStream delegate) {
        return new FilterOutputStream(delegate) {
            @Override
            public void close() throws IOException {
                flush();
            }
        };
    }

    public record BundleResult(Path bundleDir, List<Path> texFiles) {
    }

    /**
     * @param parentDir 父目录（如 static/templates 或 static/project_bundles）
     * @param zipFile   上传的 zip
     * @return bundle 根目录及解压出的 .tex 文件绝对路径列表（顺序与 zip 遍历一致）
     */
    public static BundleResult extractZipBundle(MultipartFile zipFile, Path parentDir) throws IOException {
        Files.createDirectories(parentDir);
        String bundleId = "bundle_" + UUID.randomUUID().toString().replace("-", "");
        Path bundleDir = parentDir.resolve(bundleId);
        Files.createDirectories(bundleDir);

        List<Path> texFiles = new ArrayList<>();
        // JDK ZipInputStream 在 EFS(UTF-8) 位为 1 时用严格 UTF-8 解析文件名，易 MalformedInputException。
        // Commons Compress 对 UTF-8 条目名使用 REPLACE，且非 UTF-8 条目用 ISO-8859-1，解压更稳。
        try (InputStream rawInput = new BufferedInputStream(zipFile.getInputStream());
             ZipArchiveInputStream zis = new ZipArchiveInputStream(
                     rawInput,
                     StandardCharsets.ISO_8859_1.name(),
                     false,
                     true)) {
            ZipArchiveEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (entryName == null || entryName.isBlank()) {
                    continue;
                }
                Path normalizedEntryPath = Paths.get(entryName).normalize();
                if (normalizedEntryPath.isAbsolute() || normalizedEntryPath.startsWith("..")) {
                    continue;
                }
                Path target = bundleDir.resolve(normalizedEntryPath).normalize();
                if (!target.startsWith(bundleDir)) {
                    continue;
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                if (entryName.toLowerCase(Locale.ROOT).endsWith(".tex")) {
                    texFiles.add(target);
                }
            }
        }
        return new BundleResult(bundleDir, texFiles);
    }

    public static void requireZipExtension(MultipartFile zipFile) {
        if (zipFile == null || zipFile.isEmpty()) {
            throw new RuntimeException("zip 文件不能为空");
        }
        String originalName = zipFile.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new RuntimeException("请上传 .zip 格式文件");
        }
    }

    /**
     * 将目录下所有常规文件打成 zip（相对路径为条目名，跳过 __MACOSX 等）。
     */
    public static void zipDirectory(Path root, OutputStream rawOut) throws IOException {
        Path bundleRoot = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(bundleRoot)) {
            throw new IOException("导出目录不存在或不是文件夹");
        }
        try (ZipOutputStream zos = new ZipOutputStream(nonClosing(rawOut), StandardCharsets.UTF_8)) {
            try (Stream<Path> stream = Files.walk(bundleRoot)) {
                for (Path path : stream.filter(Files::isRegularFile).collect(Collectors.toList())) {
                    String rel = bundleRoot.relativize(path).toString().replace('\\', '/');
                    if (ProjectBundleWorkspaceUtil.shouldSkipBundleArchiveEntry(rel)) {
                        continue;
                    }
                    zos.putNextEntry(new ZipEntry(rel));
                    Files.copy(path, zos);
                    zos.closeEntry();
                }
            }
        }
    }

    /**
     * 单文件 tex 打成仅含一条目的 zip（用于非 bundle 项目）。
     */
    public static void zipSingleTexEntry(String entryName, String utf8Text, OutputStream rawOut) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(nonClosing(rawOut), StandardCharsets.UTF_8)) {
            zos.putNextEntry(new ZipEntry(entryName.replace('\\', '/')));
            byte[] bytes = (utf8Text == null ? "" : utf8Text).getBytes(StandardCharsets.UTF_8);
            zos.write(bytes);
            zos.closeEntry();
        }
    }
}
