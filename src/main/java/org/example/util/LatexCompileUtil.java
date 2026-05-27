package org.example.util;

import org.example.dto.CompileResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * LaTeX编译工具类
 * 使用 Tex Live 编译 LaTeX 代码
 */
@Component
public class LatexCompileUtil {
    private static final String TEMPLATE_SOURCE_PREFIX = "%TEMPLATE_SOURCE_PATH=";
    private static final Set<String> COMMON_BUILTIN_CLASSES = Set.of(
            "article", "report", "book", "letter", "proc", "slides",
            "minimal", "beamer", "memoir", "scrartcl", "scrreprt", "scrbook"
    );

    @Value("${latex.compile.temp-dir:./temp/compile}")
    private String tempDir;

    @Value("${latex.compile.output-dir:./static/pdf}")
    private String outputDir;

    @Value("${latex.compile.pdflatex-path:pdflatex}")
    private String pdflatexPath;

    @Value("${latex.compile.xelatex-path:xelatex}")
    private String xelatexPath;

    @Value("${latex.compile.lualatex-path:lualatex}")
    private String lualatexPath;

    @Value("${latex.compile.latexmk-path:latexmk}")
    private String latexmkPath;

    @Value("${latex.compile.timeout:300}")
    private int timeoutSeconds;

    @Value("${image.upload.path:./static/images}")
    private String imageUploadDir;

    private Path resolveImageSource(String rawFileName) {
        if (rawFileName == null || rawFileName.isBlank()) {
            return null;
        }
        try {
            String cleaned = rawFileName.trim();
            int queryIdx = cleaned.indexOf('?');
            if (queryIdx >= 0) {
                cleaned = cleaned.substring(0, queryIdx);
            }
            cleaned = URLDecoder.decode(cleaned, StandardCharsets.UTF_8);
            int slashIdx = cleaned.lastIndexOf('/');
            if (slashIdx >= 0 && slashIdx < cleaned.length() - 1) {
                cleaned = cleaned.substring(slashIdx + 1);
            }
            if (cleaned.isBlank()) {
                return null;
            }
            // 兼容从通用 LaTeX 模板导入的占位符路径（如 <eps-file>）
            if (cleaned.contains("<") || cleaned.contains(">")) {
                return null;
            }

            Path configured = Paths.get(imageUploadDir).toAbsolutePath().normalize().resolve(cleaned);
            if (Files.exists(configured) && Files.isRegularFile(configured)) {
                return configured;
            }

            Path fallback1 = Paths.get("static", "images").toAbsolutePath().normalize().resolve(cleaned);
            if (Files.exists(fallback1) && Files.isRegularFile(fallback1)) {
                return fallback1;
            }

            Path fallback2 = Paths.get(System.getProperty("user.dir", "."), "static", "images")
                    .toAbsolutePath().normalize().resolve(cleaned);
            if (Files.exists(fallback2) && Files.isRegularFile(fallback2)) {
                return fallback2;
            }

            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String nfc(String s) {
        if (s == null) {
            return "";
        }
        return Normalizer.normalize(s, Normalizer.Form.NFC);
    }

    private static boolean isGraphicsExtension(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".pdf") || lower.endsWith(".eps") || lower.endsWith(".mps")
                || lower.endsWith(".bmp") || lower.endsWith(".gif");
    }

    /**
     * 在临时工作区（zip 工程副本）中按相对路径、或按 Unicode 规范化后的文件名匹配图片。
     * 解决 Windows 下 .tex 与磁盘 NFC/NFD 不一致、以及 xelatex/xdvipdfmx 找不到含中文/空格文件名的问题。
     */
    private Path resolveGraphicsInTempBundle(Path bundleRoot, Path jobDir, String originalPath) {
        if (bundleRoot == null || jobDir == null || originalPath == null || originalPath.isBlank()) {
            return null;
        }
        try {
            Path b = bundleRoot.toAbsolutePath().normalize();
            Path job = jobDir.toAbsolutePath().normalize();
            if (!job.startsWith(b)) {
                return null;
            }
            String ref = originalPath.trim().replace('\\', '/');
            int q = ref.indexOf('?');
            if (q >= 0) {
                ref = ref.substring(0, q);
            }
            ref = URLDecoder.decode(ref, StandardCharsets.UTF_8);
            if (ref.isBlank()) {
                return null;
            }
            Path directJob = job.resolve(ref).normalize();
            if (directJob.startsWith(b) && Files.isRegularFile(directJob)) {
                return directJob;
            }
            Path directBundle = b.resolve(ref).normalize();
            if (directBundle.startsWith(b) && Files.isRegularFile(directBundle)) {
                return directBundle;
            }
            int slash = ref.lastIndexOf('/');
            String base = slash >= 0 ? ref.substring(slash + 1) : ref;
            if (base.isBlank()) {
                return null;
            }
            String baseNfc = nfc(base);
            try (Stream<Path> walk = Files.walk(b)) {
                return walk.filter(Files::isRegularFile)
                        .filter(p -> isGraphicsExtension(p.getFileName().toString()))
                        .filter(p -> {
                            String fn = p.getFileName().toString();
                            return nfc(fn).equals(baseNfc) || fn.equals(base);
                        })
                        .findFirst()
                        .orElse(null);
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * @param bundleRoot  临时目录下整包根（与 copyTemplateDependencies 输出一致）
     * @param jobTexDir     主 .tex 所在目录（latex 解析相对路径的基准）
     */
    private String prepareImagesForCompile(String latexContent, Path bundleRoot, Path jobTexDir) {
        if (latexContent == null || latexContent.isBlank()) {
            return latexContent;
        }
        Pattern p = Pattern.compile("\\\\includegraphics\\s*(\\[[^\\]]*\\])?\\s*\\{([^}]+)\\}");
        Matcher m = p.matcher(latexContent);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String opts = m.group(1) == null ? "" : m.group(1);
            String originalPath = m.group(2).trim();
            if (originalPath.contains("<") || originalPath.contains(">")) {
                String placeholder = "% Removed invalid image placeholder: " + originalPath + "\n\\fbox{Image Placeholder}";
                m.appendReplacement(sb, Matcher.quoteReplacement(placeholder));
                continue;
            }
            String replacementPath = originalPath;
            String rawFileName = originalPath.startsWith("/api/images/")
                    ? originalPath.substring("/api/images/".length())
                    : originalPath;
            String fileName = rawFileName;
            int queryIdx = fileName.indexOf('?');
            if (queryIdx >= 0) {
                fileName = fileName.substring(0, queryIdx);
            }
            int slashIdx = fileName.lastIndexOf('/');
            if (slashIdx >= 0 && slashIdx < fileName.length() - 1) {
                fileName = fileName.substring(slashIdx + 1);
            }
            Path source = resolveImageSource(rawFileName);
            Path target = jobTexDir.resolve(fileName);
            try {
                if (source != null && Files.exists(source) && Files.isRegularFile(source)) {
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                    replacementPath = fileName;
                } else {
                    Path onDisk = resolveGraphicsInTempBundle(bundleRoot, jobTexDir, originalPath);
                    if (onDisk != null && Files.isRegularFile(onDisk)) {
                        Path rel = jobTexDir.relativize(onDisk).normalize();
                        String relStr = rel.toString().replace('\\', '/');
                        if (!relStr.startsWith("..")) {
                            replacementPath = relStr;
                        }
                    }
                }
            } catch (Exception ignored) {
                // 保持原路径，后续编译日志会给出错误信息
            }
            String replacement = "\\includegraphics" + opts + "{" + replacementPath + "}";
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String ensureGraphicxPackage(String latexContent) {
        if (latexContent == null || latexContent.isBlank()) {
            return latexContent;
        }
        if (!latexContent.contains("\\includegraphics")) {
            return latexContent;
        }
        String content = disableDraftMode(latexContent);

        // 先移除已有 graphicx 引入，避免选项冲突（draft/final/重复加载）
        content = content.replaceAll(
                "(?m)^\\s*\\\\usepackage\\s*(\\[[^\\]]*\\])?\\s*\\{[^}]*graphicx[^}]*\\}\\s*$\\n?",
                ""
        );

        // 仅在明确找到 documentclass 时才注入，避免包声明出现在文档类之前
        Pattern docClassPattern = Pattern.compile("(?m)^\\s*\\\\documentclass\\s*(\\[[^\\]]*\\])?\\s*\\{[^}]+\\}");
        Matcher docClassMatcher = docClassPattern.matcher(content);
        if (docClassMatcher.find()) {
            int insertPos = docClassMatcher.end();
            String graphicxBlock = "\n\\usepackage{graphicx}\n\\setkeys{Gin}{draft=false}\n";
            content = content.substring(0, insertPos)
                    + graphicxBlock
                    + content.substring(insertPos);
        } else {
            // 无 documentclass 的片段内容不强制注入，避免破坏模板原始结构
            return content;
        }

        // 强制关闭图像 draft 渲染，避免只显示文件名而不显示真实图片
        if (!content.contains("\\setkeys{Gin}{draft=false}")) {
            Pattern beginDocPattern = Pattern.compile("\\\\begin\\{document\\}");
            Matcher beginDocMatcher = beginDocPattern.matcher(content);
            if (beginDocMatcher.find()) {
                int insertPos = beginDocMatcher.start();
                content = content.substring(0, insertPos)
                        + "\\setkeys{Gin}{draft=false}\n"
                        + content.substring(insertPos);
            } else {
                content = content + "\n\\setkeys{Gin}{draft=false}\n";
            }
        }
        return content;
    }

    private String disableDraftMode(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        // 1) 处理 documentclass 中的 draft 选项
        Pattern docClassPattern = Pattern.compile("\\\\documentclass\\s*\\[([^\\]]*)\\]\\s*\\{([^}]+)\\}");
        Matcher docMatcher = docClassPattern.matcher(content);
        StringBuffer docSb = new StringBuffer();
        while (docMatcher.find()) {
            String options = docMatcher.group(1);
            String clazz = docMatcher.group(2);
            String cleaned = options
                    .replaceAll("(^|,)\\s*draft\\s*(,|$)", "$1$2")
                    .replaceAll(",,+", ",")
                    .replaceAll("^,|,$", "")
                    .trim();
            if (cleaned.isEmpty()) {
                docMatcher.appendReplacement(docSb, Matcher.quoteReplacement("\\documentclass{" + clazz + "}"));
            } else {
                docMatcher.appendReplacement(docSb, Matcher.quoteReplacement("\\documentclass[" + cleaned + "]{" + clazz + "}"));
            }
        }
        docMatcher.appendTail(docSb);
        String updated = docSb.toString();

        // 2) 处理 graphicx 的 draft 选项
        updated = updated.replaceAll("\\\\usepackage\\s*\\[([^\\]]*?)\\bdraft\\b([^\\]]*?)\\]\\s*\\{graphicx\\}",
                "\\\\usepackage[$1$2]{graphicx}");
        updated = updated.replaceAll("\\\\usepackage\\s*\\[\\s*,", "\\\\usepackage[");
        updated = updated.replaceAll(",\\s*\\]", "]");
        return updated;
    }

    private String fallbackMissingDocumentClass(String latexContent, Path tempWorkDir, boolean allowFallback) {
        if (latexContent == null || latexContent.isBlank()) {
            return latexContent;
        }
        Pattern docClassPattern = Pattern.compile("(?m)^\\s*\\\\documentclass\\s*(\\[[^\\]]*\\])?\\s*\\{([^}]+)\\}");
        Matcher matcher = docClassPattern.matcher(latexContent);
        if (!matcher.find()) {
            return latexContent;
        }

        String className = matcher.group(2) == null ? "" : matcher.group(2).trim();
        if (className.isEmpty()) {
            return latexContent;
        }
        if (className.toLowerCase(Locale.ROOT).startsWith("sn-")) {
            return latexContent;
        }
        if (COMMON_BUILTIN_CLASSES.contains(className.toLowerCase(Locale.ROOT))) {
            return latexContent;
        }

        Path classFile = tempWorkDir.resolve(className + ".cls");
        if (Files.exists(classFile) && Files.isRegularFile(classFile)) {
            return latexContent;
        }

        if (!allowFallback) {
            return latexContent;
        }

        return matcher.replaceFirst(Matcher.quoteReplacement("\\documentclass{article}"));
    }

    private boolean hasSpringerFrontmatterCommands(String latexContent) {
        if (latexContent == null || latexContent.isBlank()) {
            return false;
        }
        return latexContent.contains("\\affil")
                || latexContent.contains("\\email")
                || latexContent.contains("\\author[")
                || latexContent.contains("\\author*[");
    }

    private String enforceSpringerClassWhenAvailable(String latexContent, Path compileTexDir) {
        if (latexContent == null || latexContent.isBlank()) {
            return latexContent;
        }
        if (!hasSpringerFrontmatterCommands(latexContent)) {
            return latexContent;
        }
        Path snClass = compileTexDir.resolve("sn-jnl.cls");
        if (!Files.exists(snClass) || !Files.isRegularFile(snClass)) {
            return latexContent;
        }

        Pattern docClassPattern = Pattern.compile("(?m)^\\s*\\\\documentclass\\s*(\\[[^\\]]*\\])?\\s*\\{([^}]+)\\}");
        Matcher matcher = docClassPattern.matcher(latexContent);
        if (!matcher.find()) {
            return latexContent;
        }
        String className = matcher.group(2) == null ? "" : matcher.group(2).trim().toLowerCase(Locale.ROOT);
        if ("sn-jnl".equals(className)) {
            return latexContent;
        }
        return matcher.replaceFirst(Matcher.quoteReplacement("\\documentclass[pdflatex,sn-mathphys-num]{sn-jnl}"));
    }

    private static class TemplateContentContext {
        private final String latexContent;
        private final Path sourceTexPath;

        private TemplateContentContext(String latexContent, Path sourceTexPath) {
            this.latexContent = latexContent;
            this.sourceTexPath = sourceTexPath;
        }
    }

    private TemplateContentContext extractTemplateSourceContext(String latexContent) {
        if (latexContent == null || latexContent.isBlank()) {
            return new TemplateContentContext(latexContent, null);
        }
        String normalized = latexContent.replace("\r\n", "\n");
        int firstLineEnd = normalized.indexOf('\n');
        String firstLine = firstLineEnd >= 0 ? normalized.substring(0, firstLineEnd) : normalized;
        if (!firstLine.startsWith(TEMPLATE_SOURCE_PREFIX)) {
            return new TemplateContentContext(latexContent, null);
        }

        String sourcePathRaw = firstLine.substring(TEMPLATE_SOURCE_PREFIX.length()).trim();
        String remaining = firstLineEnd >= 0 ? normalized.substring(firstLineEnd + 1) : "";
        if (sourcePathRaw.isEmpty()) {
            return new TemplateContentContext(remaining, null);
        }
        try {
            Path sourcePath = Paths.get(sourcePathRaw).toAbsolutePath().normalize();
            return new TemplateContentContext(remaining, sourcePath);
        } catch (Exception ignored) {
            return new TemplateContentContext(remaining, null);
        }
    }

    private Path locateTemplateBundleRoot(Path sourceTexPath) {
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

    private String copyTemplateDependencies(Path sourceTexPath, Path tempWorkDir) {
        if (sourceTexPath == null || !Files.exists(sourceTexPath) || !Files.isRegularFile(sourceTexPath)) {
            return "main.tex";
        }
        try {
            Path bundleRoot = locateTemplateBundleRoot(sourceTexPath);
            if (bundleRoot == null || !Files.exists(bundleRoot) || !Files.isDirectory(bundleRoot)) {
                return "main.tex";
            }
            try (Stream<Path> stream = Files.walk(bundleRoot)) {
                stream.forEach(path -> {
                    try {
                        Path relative = bundleRoot.relativize(path);
                        Path target = tempWorkDir.resolve(relative).normalize();
                        if (!target.startsWith(tempWorkDir)) {
                            return;
                        }
                        if (Files.isDirectory(path)) {
                            Files.createDirectories(target);
                        } else if (Files.isRegularFile(path)) {
                            Path parent = target.getParent();
                            if (parent != null) {
                                Files.createDirectories(parent);
                            }
                            Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (Exception ignored) {
                        // 单个依赖复制失败不阻塞整体编译
                    }
                });
            }
            Path relativeTex = bundleRoot.relativize(sourceTexPath);
            return relativeTex.toString().replace("\\", "/");
        } catch (Exception ignored) {
            return "main.tex";
        }
    }

    private boolean shouldRunBibtex(Path workDir, String texFileName) {
        try {
            String baseName = getBaseName(texFileName);
            Path jobDir = resolveJobDirectory(workDir, texFileName);
            Path auxFile = jobDir.resolve(baseName + ".aux");
            if (!Files.exists(auxFile) || !Files.isRegularFile(auxFile)) {
                return false;
            }
            String auxContent = Files.readString(auxFile, StandardCharsets.UTF_8);
            if (auxContent == null || auxContent.isBlank()) {
                return false;
            }
            return auxContent.contains("\\citation")
                    || auxContent.contains("\\bibdata")
                    || auxContent.contains("\\bibstyle");
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * 主 .tex 所在目录（biber/bibtex 需在此 cwd 下执行）
     */
    private Path resolveJobDirectory(Path workDir, String texFileName) {
        int slash = Math.max(texFileName.lastIndexOf('/'), texFileName.lastIndexOf('\\'));
        if (slash < 0) {
            return workDir.toAbsolutePath().normalize();
        }
        String relDir = texFileName.substring(0, slash).replace("\\", "/");
        return workDir.resolve(relDir).normalize();
    }

    /**
     * 从模板 bundle 复制到临时目录时可能带入旧的 .toc/.aux 等；\@starttoc 会优先读这些文件导致目录页一直异常。
     * 仅在每轮完整编译开始前调用一次（各次引擎之间仍保留 .aux/.toc 供多轮修正）。
     */
    private void purgeStaleAuxiliaryFiles(Path workDir, String texFileName) {
        if (texFileName == null || texFileName.isBlank()) {
            return;
        }
        String norm = texFileName.replace('\\', '/');
        String baseName = getBaseName(norm);
        Path jobDir = resolveJobDirectory(workDir, norm);
        String[] suffixes = {
                ".aux", ".toc", ".out", ".lof", ".lot",
                ".bcf", ".bbl", ".blg", ".run.xml",
                ".fdb_latexmk", ".fls", ".synctex.gz", ".synctex"
        };
        for (String suf : suffixes) {
            try {
                Files.deleteIfExists(jobDir.resolve(baseName + suf));
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * biblatex + biber：首轮 xelatex/pdf 后生成 .bcf
     */
    private boolean shouldRunBiber(Path workDir, String texFileName, String baseName) {
        try {
            Path jobDir = resolveJobDirectory(workDir, texFileName);
            Path bcf = jobDir.resolve(baseName + ".bcf");
            return Files.exists(bcf) && Files.isRegularFile(bcf) && Files.size(bcf) > 32L;
        } catch (Exception ignored) {
            return false;
        }
    }

    private Path locateGeneratedPdf(Path workDir, String texFileName) {
        String baseName = getBaseName(texFileName);
        Path jobDir = resolveJobDirectory(workDir, texFileName);
        Path beside = jobDir.resolve(baseName + ".pdf").normalize();
        if (beside.startsWith(workDir.normalize()) && Files.exists(beside) && Files.isRegularFile(beside)) {
            return beside;
        }
        Path root = workDir.resolve(baseName + ".pdf").normalize();
        if (Files.exists(root) && Files.isRegularFile(root)) {
            return root;
        }
        return beside;
    }

    private Path locateGeneratedXdv(Path workDir, String texFileName) {
        String baseName = getBaseName(texFileName);
        Path jobDir = resolveJobDirectory(workDir, texFileName);
        Path beside = jobDir.resolve(baseName + ".xdv").normalize();
        if (beside.startsWith(workDir.normalize()) && Files.exists(beside)) {
            return beside;
        }
        return workDir.resolve(baseName + ".xdv").normalize();
    }

    private boolean isLatexmkAvailable() {
        if (latexmkPath == null || latexmkPath.isBlank()) {
            return false;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(latexmkPath.trim(), "-v");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                while (reader.readLine() != null) {
                    // drain
                }
            }
            boolean finished = p.waitFor(8, TimeUnit.SECONDS);
            return finished && p.exitValue() == 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private CompileExecutionResult runBibtexProcess(String baseName, Path jobDirectory) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("bibtex", baseName);
        pb.directory(jobDirectory.toAbsolutePath().toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        StringBuilder logOutput = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logOutput.append(line).append("\n");
            }
        }
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("bibtex 执行超时（超过 " + timeoutSeconds + " 秒）");
        }
        return new CompileExecutionResult(process.exitValue(), logOutput.toString());
    }

    /**
     * 必须为 {@code biber main} 形式（jobname，无 .tex 后缀）
     */
    private CompileExecutionResult runBiberProcess(String baseName, Path jobDirectory) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("biber", baseName);
        pb.directory(jobDirectory.toAbsolutePath().toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        StringBuilder logOutput = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logOutput.append(line).append("\n");
            }
        }
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("biber 执行超时（超过 " + timeoutSeconds + " 秒）");
        }
        return new CompileExecutionResult(process.exitValue(), logOutput.toString());
    }

    private CompileExecutionResult runLatexmk(Path workDir, String texFileName, String normalizedCompiler) throws Exception {
        String norm = texFileName.replace('\\', '/');
        int slash = norm.lastIndexOf('/');
        Path cwd = workDir.toAbsolutePath().normalize();
        String mkTarget = norm;
        if (slash >= 0) {
            cwd = resolveJobDirectory(workDir, norm);
            mkTarget = norm.substring(slash + 1);
        }
        List<String> cmd = new ArrayList<>();
        cmd.add(latexmkPath.trim());
        switch (normalizedCompiler) {
            case "xelatex" -> cmd.add("-xelatex");
            case "lualatex" -> cmd.add("-lualatex");
            default -> cmd.add("-pdf");
        }
        cmd.add("-interaction=nonstopmode");
        cmd.add("-file-line-error");
        cmd.add(mkTarget);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(cwd.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        StringBuilder logOutput = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logOutput.append(line).append("\n");
            }
        }
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("latexmk 执行超时（超过 " + timeoutSeconds + " 秒）");
        }
        return new CompileExecutionResult(process.exitValue(), logOutput.toString());
    }

    /**
     * 手动多轮：含参考文献时为 引擎 + biber/bibtex + 引擎 + 引擎 + 第5次收尾；
     * 无文献时为 4 次引擎。子目录入口由 {@link #runCompileCommand} 在 .tex 所在目录执行。
     */
    private int runManualMultiPass(Path workDir, String texFileName, String normalizedCompiler,
                                   String compilerCommand, StringBuilder logOutput) throws Exception {
        String baseName = getBaseName(texFileName);
        String texArg = texFileName.replace("\\", "/");
        Path jobDir = resolveJobDirectory(workDir, texArg);

        CompileExecutionResult p1 = runCompileCommand(compilerCommand, texArg, workDir);
        logOutput.append("\n=== ").append(normalizedCompiler).append(" [第 1 次] ===\n")
                .append(p1.logContent).append("\n[退出码 ").append(p1.exitCode).append("]\n");

        boolean needsBiber = shouldRunBiber(workDir, texFileName, baseName);
        boolean needsBibtex = !needsBiber && shouldRunBibtex(workDir, texFileName);
        int lastExit = p1.exitCode;

        if (needsBiber) {
            CompileExecutionResult br = runBiberProcess(baseName, jobDir);
            logOutput.append("\n=== biber ").append(baseName).append(" ===\n")
                    .append(br.logContent).append("\n[退出码 ").append(br.exitCode).append("]\n");
            lastExit = br.exitCode;
        } else if (needsBibtex) {
            CompileExecutionResult bt = runBibtexProcess(baseName, jobDir);
            logOutput.append("\n=== bibtex ").append(baseName).append(" ===\n")
                    .append(bt.logContent).append("\n[退出码 ").append(bt.exitCode).append("]\n");
            lastExit = bt.exitCode;
        }

        CompileExecutionResult p2 = runCompileCommand(compilerCommand, texArg, workDir);
        logOutput.append("\n=== ").append(normalizedCompiler).append(" [第 2 次] ===\n")
                .append(p2.logContent).append("\n[退出码 ").append(p2.exitCode).append("]\n");
        lastExit = p2.exitCode;

        if (needsBiber || needsBibtex) {
            CompileExecutionResult p3 = runCompileCommand(compilerCommand, texArg, workDir);
            logOutput.append("\n=== ").append(normalizedCompiler).append(" [第 3 次] ===\n")
                    .append(p3.logContent).append("\n[退出码 ").append(p3.exitCode).append("]\n");
            lastExit = p3.exitCode;
        } else {
            CompileExecutionResult p3 = runCompileCommand(compilerCommand, texArg, workDir);
            logOutput.append("\n=== ").append(normalizedCompiler).append(" [第 3 次：目录/页码/交叉引用] ===\n")
                    .append(p3.logContent).append("\n[退出码 ").append(p3.exitCode).append("]\n");
            lastExit = p3.exitCode;
        }

        CompileExecutionResult p4 = runCompileCommand(compilerCommand, texArg, workDir);
        logOutput.append("\n=== ").append(normalizedCompiler).append(" [第 4 次：目录/页码定稿] ===\n")
                .append(p4.logContent).append("\n[退出码 ").append(p4.exitCode).append("]\n");
        return p4.exitCode != 0 ? p4.exitCode : lastExit;
    }

    private String getBaseName(String texFileName) {
        if (texFileName == null || texFileName.isBlank()) {
            return "main";
        }
        int slash = Math.max(texFileName.lastIndexOf('/'), texFileName.lastIndexOf('\\'));
        String name = slash >= 0 ? texFileName.substring(slash + 1) : texFileName;
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /**
     * 编译 LaTeX 代码为 PDF
     * 
     * @param latexContent LaTeX 代码内容
     * @param projectId 项目ID（用于生成文件名）
     * @param compiler 编译器类型（pdflatex, xelatex, lualatex）
     * @return 编译结果对象，包含PDF路径、日志等信息
     * @throws Exception 编译异常
     */
    public CompileResult compile(String latexContent, Long projectId, String compiler) throws Exception {
        long startTime = System.currentTimeMillis();
        
        // 创建临时目录（使用绝对路径）
        String compileId = UUID.randomUUID().toString();
        Path baseTempDir = Paths.get(tempDir).toAbsolutePath().normalize();
        Path tempWorkDir = baseTempDir.resolve(compileId);
        Files.createDirectories(tempWorkDir);
        
        // 创建输出目录（使用绝对路径）
        Path pdfOutputDir = Paths.get(outputDir).toAbsolutePath().normalize();
        Files.createDirectories(pdfOutputDir);
        
        String texFileName = "main.tex";
        Path texFile = tempWorkDir.resolve(texFileName);
        
        try {
            TemplateContentContext templateContext = extractTemplateSourceContext(latexContent);
            latexContent = templateContext.latexContent;
            if (templateContext.sourceTexPath != null) {
                texFileName = copyTemplateDependencies(templateContext.sourceTexPath, tempWorkDir);
                texFile = tempWorkDir.resolve(texFileName).normalize();
            }
            Path compileTexDir = texFile.getParent() == null ? tempWorkDir : texFile.getParent();
            latexContent = prepareImagesForCompile(latexContent, tempWorkDir, compileTexDir);
            latexContent = enforceSpringerClassWhenAvailable(latexContent, compileTexDir);
            latexContent = ensureGraphicxPackage(latexContent);
            boolean allowClassFallback = templateContext.sourceTexPath == null;
            latexContent = fallbackMissingDocumentClass(latexContent, compileTexDir, allowClassFallback);
            Path texParent = texFile.getParent();
            if (texParent != null) {
                Files.createDirectories(texParent);
            }
            // 写入 LaTeX 内容到文件
            Files.write(texFile, latexContent.getBytes("UTF-8"));
            purgeStaleAuxiliaryFiles(tempWorkDir, texFileName);

            String normalizedCompiler = normalizeCompiler(compiler);
            String compilerCommand = getCompilerCommand(normalizedCompiler);
            String texArg = texFileName.replace("\\", "/");

            StringBuilder logOutput = new StringBuilder();
            int exitCode;
            if (isLatexmkAvailable()) {
                logOutput.append("=== latexmk（优先；子目录工程在 .tex 所在目录执行） ===\n");
                CompileExecutionResult mk = runLatexmk(tempWorkDir, texFileName, normalizedCompiler);
                logOutput.append(mk.logContent).append("\n[退出码 ").append(mk.exitCode).append("]\n");
                exitCode = mk.exitCode;
                if (mk.exitCode == 0) {
                    Path pdfProbe = locateGeneratedPdf(tempWorkDir, texFileName);
                    if (Files.exists(pdfProbe)) {
                        for (int pass = 1; pass <= 3; pass++) {
                            try {
                                logOutput.append("\n=== ").append(normalizedCompiler)
                                        .append(" [补 ").append(pass).append("/3：目录/页码/交叉引用] ===\n");
                                CompileExecutionResult extra = runCompileCommand(compilerCommand, texArg, tempWorkDir);
                                logOutput.append(extra.logContent).append("\n[退出码 ").append(extra.exitCode).append("]\n");
                                if (extra.exitCode != 0) {
                                    logOutput.append("[提示] 补编译退出码非零，仍保留 latexmk 生成的 PDF；请查看日志。\n");
                                }
                            } catch (Exception e) {
                                logOutput.append("\n[补编译异常] ").append(e.getMessage()).append("\n");
                            }
                        }
                    }
                }
            } else {
                logOutput.append("[提示] 未检测到 latexmk（请配置 latex.compile.latexmk-path 或安装 latexmk），")
                        .append("使用手动多轮编译（含第 4 次定稿目录）；若存在 biber/bibtex 则插入文献处理后再编译。\n");
                exitCode = runManualMultiPass(tempWorkDir, texFileName, normalizedCompiler, compilerCommand, logOutput);
            }

            long compileTime = System.currentTimeMillis() - startTime;

            Path pdfFile = locateGeneratedPdf(tempWorkDir, texFileName);
            if (!Files.exists(pdfFile) && "xelatex".equals(normalizedCompiler)) {
                // 某些 XeLaTeX 环境会产出 XDV，需要额外转换为 PDF
                Path xdvFile = locateGeneratedXdv(tempWorkDir, texFileName);
                if (Files.exists(xdvFile)) {
                    String xdvLog = convertXdvToPdf(xdvFile);
                    if (xdvLog != null && !xdvLog.isEmpty()) {
                        logOutput.append("\n[xdvipdfmx]\n").append(xdvLog).append("\n");
                    }
                }
            }
            CompileResult result = new CompileResult();
            result.setCompileTimeMs(compileTime);
            result.setLogContent(logOutput.toString());
            result.setCreatedAt(LocalDateTime.now());
            
            // 检查PDF是否生成
            String logContent = logOutput.toString();
            
            if (Files.exists(pdfFile)) {
                // PDF 文件已生成，认为编译成功
                String pdfFileName = "project_" + projectId + "_" + compileId + ".pdf";
                Path outputPdf = pdfOutputDir.resolve(pdfFileName);
                Files.copy(pdfFile, outputPdf, StandardCopyOption.REPLACE_EXISTING);
                
                // 校验生成文件是否为有效PDF，避免前端出现“无法打开此文件”
                if (!isValidPdf(outputPdf)) {
                    if (!isValidPdf(outputPdf)) {
                        result.setStatus("ERROR");
                        String logHint = extractErrorMessage(logContent);
                        if (logHint == null || logHint.isEmpty()) {
                            logHint = "编译产物不是有效的 PDF 文件";
                        }
                        result.setErrorMessage(logHint);
                        result.setPdfPath(null);
                        return result;
                    }
                }
                
                // 检查是否有警告或非致命错误（如图片文件缺失等）
                // 如果 PDF 生成了，即使有错误消息，也视为成功或警告
                boolean hasNonFatalErrors = logContent.contains("File") && logContent.contains("not found");
                boolean hasWarnings = logContent.contains("Warning") || logContent.contains("warning");
                boolean hasErrors = logContent.contains("!") && !hasNonFatalErrors;
                
                if (hasWarnings || hasNonFatalErrors) {
                    // 有警告或非致命错误（如图片缺失），但 PDF 已生成
                    result.setStatus("WARNING");
                    // 提取警告信息作为提示
                    String warningMsg = extractWarningMessage(logContent);
                    if (warningMsg != null && !warningMsg.isEmpty()) {
                        result.setErrorMessage(warningMsg);
                    } else if (hasNonFatalErrors) {
                        // 提取文件缺失的错误信息
                        result.setErrorMessage(extractFileNotFoundMessage(logContent));
                    }
                } else if (hasErrors && exitCode != 0) {
                    // 有错误且退出码不为0，但 PDF 仍然生成了（少见情况）
                    result.setStatus("WARNING");
                    result.setErrorMessage(extractErrorMessage(logContent));
                } else {
                    // 完全成功
                    result.setStatus("SUCCESS");
                }
                
                result.setPdfPath("/api/pdf/" + pdfFileName); // 相对路径，用于前端访问
                
                // 获取编译器版本
                String version = getCompilerVersion(compilerCommand);
                result.setCompilerVersion(version);
            } else {
                // 编译失败，没有生成PDF
                result.setStatus("ERROR");
                String hint = extractErrorMessage(logContent);
                result.setErrorMessage("未生成 PDF（最后记录退出码 " + exitCode + "）。"
                        + (hint.isBlank() ? "" : "\n" + hint));
                result.setPdfPath(null);
            }
            
            return result;
            
        } finally {
            // 清理临时文件（可选：保留日志时注释掉）
            deleteDirectory(tempWorkDir.toFile());
        }
    }

    private CompileExecutionResult runCompileCommand(String compilerCommand, String texFileName, Path workDir) throws Exception {
        String norm = texFileName.replace('\\', '/');
        int slash = norm.lastIndexOf('/');
        Path cwd = workDir.toAbsolutePath().normalize();
        String texArg = norm;
        if (slash >= 0) {
            cwd = resolveJobDirectory(workDir, norm);
            texArg = norm.substring(slash + 1);
        }
        ProcessBuilder processBuilder = new ProcessBuilder(
                compilerCommand,
                "-interaction=nonstopmode",
                "-file-line-error",
                texArg
        );
        processBuilder.directory(cwd.toFile());
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        StringBuilder logOutput = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logOutput.append(line).append("\n");
            }
        }

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("编译超时（超过 " + timeoutSeconds + " 秒）");
        }
        return new CompileExecutionResult(process.exitValue(), logOutput.toString());
    }

    private static class CompileExecutionResult {
        private final int exitCode;
        private final String logContent;

        private CompileExecutionResult(int exitCode, String logContent) {
            this.exitCode = exitCode;
            this.logContent = logContent;
        }
    }

    /**
     * 将 XeLaTeX 产出的 xdv 转换为 PDF
     */
    private String convertXdvToPdf(Path xdvFile) {
        StringBuilder logOutput = new StringBuilder();
        try {
            Path workDirForXdv = xdvFile.getParent();
            if (workDirForXdv == null) {
                return "xdvipdfmx: 无效的 xdv 路径";
            }
            ProcessBuilder pb = new ProcessBuilder(
                    "xdvipdfmx",
                    xdvFile.getFileName().toString()
            );
            pb.directory(workDirForXdv.toAbsolutePath().toFile());
            pb.redirectErrorStream(true);

            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logOutput.append(line).append("\n");
                }
            }
            boolean finished = p.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                logOutput.append("xdvipdfmx 执行超时");
            }
        } catch (Exception e) {
            logOutput.append("xdvipdfmx 转换失败: ").append(e.getMessage());
        }
        return logOutput.toString().trim();
    }

    /**
     * 校验文件是否为有效PDF（最小校验：文件头 + 基本大小）
     */
    private boolean isValidPdf(Path pdfPath) {
        try {
            if (!Files.exists(pdfPath)) {
                return false;
            }
            long size = Files.size(pdfPath);
            if (size < 100) {
                return false;
            }
            try (InputStream in = Files.newInputStream(pdfPath)) {
                byte[] header = new byte[1024];
                int read = in.read(header);
                if (read <= 0) {
                    return false;
                }
                // PDF 规范允许 header 不一定出现在第 0 字节，通常在前 1KB 内
                for (int i = 0; i <= read - 5; i++) {
                    if (header[i] == '%' && header[i + 1] == 'P' && header[i + 2] == 'D' && header[i + 3] == 'F' && header[i + 4] == '-') {
                        return true;
                    }
                }
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 获取编译器命令
     */
    private String getCompilerCommand(String compiler) {
        String normalizedCompiler = normalizeCompiler(compiler);
        switch (normalizedCompiler) {
            case "pdflatex":
                return pdflatexPath;
            case "xelatex":
                return resolveCompilerPath("xelatex", xelatexPath);
            case "lualatex":
                return resolveCompilerPath("lualatex", lualatexPath);
            default:
                return pdflatexPath;
        }
    }

    /**
     * 解析编译器命令路径：
     * 1) 优先使用显式配置的路径；
     * 2) 若为默认命令名，且 pdflatex-path 为绝对路径，则推导同目录下的引擎可执行文件。
     */
    private String resolveCompilerPath(String compilerName, String configuredPath) {
        if (configuredPath != null && !configuredPath.trim().isEmpty() && !compilerName.equalsIgnoreCase(configuredPath.trim())) {
            return configuredPath.trim();
        }

        if (pdflatexPath != null) {
            String pdflatex = pdflatexPath.trim();
            // 当 pdflatex-path 配的是绝对路径时，推导兄弟可执行文件（Windows/Linux/macOS 均兼容）
            if (pdflatex.contains("/") || pdflatex.contains("\\")) {
                Path pdflatexFile = Paths.get(pdflatex);
                Path parent = pdflatexFile.getParent();
                String fileName = pdflatexFile.getFileName() != null ? pdflatexFile.getFileName().toString() : "";
                if (parent != null && !fileName.isEmpty()) {
                    String suffix = fileName.toLowerCase(Locale.ROOT).endsWith(".exe") ? ".exe" : "";
                    Path siblingCompiler = parent.resolve(compilerName + suffix);
                    if (Files.exists(siblingCompiler)) {
                        return siblingCompiler.toString();
                    }
                }
            }
        }

        return compilerName;
    }

    /**
     * 规范化编译器参数，仅允许三种受支持引擎，非法值回退到 pdflatex
     */
    private String normalizeCompiler(String compiler) {
        if (compiler == null) {
            return "pdflatex";
        }
        String normalized = compiler.trim().toLowerCase();
        if ("xelatex".equals(normalized) || "lualatex".equals(normalized) || "pdflatex".equals(normalized)) {
            return normalized;
        }
        return "pdflatex";
    }
    
    /**
     * 获取编译器版本信息
     */
    private String getCompilerVersion(String compilerCommand) {
        try {
            ProcessBuilder pb = new ProcessBuilder(compilerCommand, "--version");
            Process process = pb.start();
            
            StringBuilder versionOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), "UTF-8"))) {
                String line;
                int lineCount = 0;
                while ((line = reader.readLine()) != null && lineCount < 3) {
                    versionOutput.append(line).append(" ");
                    lineCount++;
                }
            }
            
            process.waitFor(5, TimeUnit.SECONDS);
            return versionOutput.toString().trim();
        } catch (Exception e) {
            return "Unknown";
        }
    }
    
    /**
     * 从日志中提取错误信息
     */
    private String extractErrorMessage(String logContent) {
        String[] lines = logContent.split("\n");
        StringBuilder errorMsg = new StringBuilder();
        
        boolean inError = false;
        for (String line : lines) {
            if (line.contains("!") || line.contains("Error") || line.contains("error")) {
                inError = true;
                errorMsg.append(line).append("\n");
            } else if (inError && (line.trim().isEmpty() || line.startsWith("l."))) {
                if (line.startsWith("l.")) {
                    errorMsg.append(line).append("\n");
                }
                if (line.trim().isEmpty() && errorMsg.length() > 0) {
                    break;
                }
            }
        }
        
        if (errorMsg.length() == 0) {
            errorMsg.append("编译失败，请查看完整日志");
        }
        
        return errorMsg.toString().trim();
    }
    
    /**
     * 从日志中提取警告信息
     */
    private String extractWarningMessage(String logContent) {
        String[] lines = logContent.split("\n");
        StringBuilder warningMsg = new StringBuilder();
        
        for (String line : lines) {
            // 提取常见的警告信息
            if (line.contains("Warning") || line.contains("warning")) {
                warningMsg.append(line).append("\n");
            }
        }
        
        return warningMsg.toString().trim();
    }
    
    /**
     * 从日志中提取文件缺失的错误信息（这类错误通常是非致命的）
     */
    private String extractFileNotFoundMessage(String logContent) {
        String[] lines = logContent.split("\n");
        StringBuilder msg = new StringBuilder();
        
        for (String line : lines) {
            // 查找文件缺失相关的错误
            if (line.contains("File") && line.contains("not found")) {
                // 找到包含文件名的那一行
                if (line.contains("!")) {
                    msg.append(line.trim()).append(" ");
                } else {
                    // 继续查找相关的错误信息
                    msg.append(line.trim()).append(" ");
                }
            }
            // 限制消息长度
            if (msg.length() > 500) {
                break;
            }
        }
        
        String result = msg.toString().trim();
        return result.isEmpty() ? "某些文件未找到（如图片文件），但 PDF 已生成" : result;
    }
    
    /**
     * 递归删除目录
     */
    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }
}

