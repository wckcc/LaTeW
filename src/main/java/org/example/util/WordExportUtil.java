package org.example.util;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHpsMeasure;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Word 导出工具类
 * 使用 pandoc 将 LaTeX 内容转换为 docx 文件
 */
@Component
public class WordExportUtil {

    @Value("${word.export.temp-dir:./temp/word}")
    private String tempDir;

    @Value("${word.export.output-dir:./static/word}")
    private String outputDir;

    @Value("${word.export.pandoc-path:pandoc}")
    private String pandocPath;

    @Value("${word.export.timeout:60}")
    private int timeoutSeconds;

    /**
     * 导出 LaTeX 内容为 Word 文件
     */
    public Path export(String latexContent, Long projectId) throws Exception {
        if (latexContent == null || latexContent.trim().isEmpty()) {
            throw new RuntimeException("项目内容为空，无法导出 Word");
        }

        String exportId = UUID.randomUUID().toString();
        Path baseTempDir = Paths.get(tempDir).toAbsolutePath().normalize();
        Path workDir = baseTempDir.resolve(exportId);
        Files.createDirectories(workDir);

        Path outDir = Paths.get(outputDir).toAbsolutePath().normalize();
        Files.createDirectories(outDir);

        Path texFile = workDir.resolve("main.tex");
        Path docxFile = workDir.resolve("main.docx");
        Files.write(texFile, latexContent.getBytes("UTF-8"));

        try {
            String pandocCommand = resolvePandocCommand();
            ProcessBuilder pb = new ProcessBuilder(
                    pandocCommand,
                    "-f", "latex",
                    "-t", "docx",
                    texFile.getFileName().toString(),
                    "-o", docxFile.getFileName().toString()
            );
            pb.directory(workDir.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            StringBuilder log = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("Word 导出超时（超过 " + timeoutSeconds + " 秒）");
            }

            if (process.exitValue() != 0 || !Files.exists(docxFile) || Files.size(docxFile) == 0) {
                String msg = log.length() > 0 ? log.toString().trim() : "pandoc 导出失败";
                throw new RuntimeException(msg);
            }

            // 按业务规范二次排版：正文中英文字体、小四；图名/表名宋体五号
            applyWordStyles(docxFile);

            String outputFileName = "project_" + projectId + "_" + exportId + ".docx";
            Path outputFile = outDir.resolve(outputFileName);
            Files.copy(docxFile, outputFile, StandardCopyOption.REPLACE_EXISTING);
            return outputFile;
        } finally {
            deleteDirectory(workDir);
        }
    }

    private void deleteDirectory(Path dir) {
        try {
            if (!Files.exists(dir)) {
                return;
            }
            Files.walk(dir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                        }
                    });
        } catch (Exception ignored) {
        }
    }

    private void applyWordStyles(Path docxPath) throws Exception {
        try (FileInputStream fis = new FileInputStream(docxPath.toFile());
             XWPFDocument document = new XWPFDocument(fis)) {

            for (XWPFParagraph paragraph : document.getParagraphs()) {
                applyParagraphStyle(paragraph);
            }
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for (XWPFParagraph paragraph : cell.getParagraphs()) {
                            applyParagraphStyle(paragraph);
                        }
                    }
                }
            }

            try (FileOutputStream fos = new FileOutputStream(docxPath.toFile())) {
                document.write(fos);
            }
        }
    }

    private void applyParagraphStyle(XWPFParagraph paragraph) {
        if (paragraph == null) {
            return;
        }
        String text = paragraph.getText() == null ? "" : paragraph.getText().trim();
        boolean isCaption = isFigureOrTableCaption(text);

        int halfPoints = isCaption ? 21 : 24; // 五号10.5pt=21 half-points；小四12pt=24 half-points
        for (XWPFRun run : paragraph.getRuns()) {
            applyRunFontsAndSize(run, isCaption, halfPoints);
        }
    }

    private boolean isFigureOrTableCaption(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return text.matches("^(图|表)\\s*\\d+.*")
                || text.matches("^(?i)(figure|table)\\s*\\d+.*");
    }

    private void applyRunFontsAndSize(XWPFRun run, boolean caption, int halfPoints) {
        if (run == null) {
            return;
        }
        CTRPr rPr = run.getCTR().isSetRPr() ? run.getCTR().getRPr() : run.getCTR().addNewRPr();
        CTFonts fonts = rPr.sizeOfRFontsArray() > 0 ? rPr.getRFontsArray(0) : rPr.addNewRFonts();

        if (caption) {
            fonts.setAscii("SimSun");
            fonts.setHAnsi("SimSun");
            fonts.setEastAsia("宋体");
            fonts.setCs("SimSun");
        } else {
            fonts.setAscii("Times New Roman");
            fonts.setHAnsi("Times New Roman");
            fonts.setEastAsia("宋体");
            fonts.setCs("Times New Roman");
        }

        CTHpsMeasure sz = rPr.sizeOfSzArray() > 0 ? rPr.getSzArray(0) : rPr.addNewSz();
        sz.setVal(BigInteger.valueOf(halfPoints));
        CTHpsMeasure szCs = rPr.sizeOfSzCsArray() > 0 ? rPr.getSzCsArray(0) : rPr.addNewSzCs();
        szCs.setVal(BigInteger.valueOf(halfPoints));
    }

    /**
     * 解析 pandoc 可执行命令路径。
     * Windows 下 Java 进程可能拿不到用户终端 PATH，因此增加常见安装位置兜底。
     */
    private String resolvePandocCommand() {
        List<String> candidates = new ArrayList<>();
        if (pandocPath != null && !pandocPath.trim().isEmpty()) {
            candidates.add(pandocPath.trim());
        }
        candidates.add("pandoc");
        candidates.add("pandoc.exe");

        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.trim().isEmpty()) {
            Path localPandoc = Paths.get(localAppData, "Pandoc", "pandoc.exe");
            candidates.add(localPandoc.toString());
        }

        for (String candidate : candidates) {
            try {
                ProcessBuilder checkPb = new ProcessBuilder(candidate, "--version");
                checkPb.redirectErrorStream(true);
                Process process = checkPb.start();
                boolean finished = process.waitFor(5, TimeUnit.SECONDS);
                if (finished && process.exitValue() == 0) {
                    return candidate;
                }
            } catch (Exception ignored) {
            }
        }

        throw new RuntimeException("未找到 pandoc 可执行文件，请在配置中设置 word.export.pandoc-path");
    }
}

