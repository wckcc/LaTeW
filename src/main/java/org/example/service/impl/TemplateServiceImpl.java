package org.example.service.impl;

import org.example.dto.TemplateDTO;
import org.example.mapper.TemplateMapper;
import org.example.service.TemplateService;
import org.example.util.ZipLatexBundleUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 模板服务实现类
 */
@Service
@Transactional
public class TemplateServiceImpl implements TemplateService {

    /** 与 {@link org.example.util.ProjectBundleWorkspaceUtil#PREFIX} 一致；仅用于解析历史/少量首行标记数据 */
    private static final String TEMPLATE_SOURCE_PREFIX = "%TEMPLATE_SOURCE_PATH=";

    @Autowired
    private TemplateMapper templateMapper;

    /**
     * 按 UTF-8 宽松解码：非法字节替换为占位符，避免因 GBK 等编码导致 {@link java.nio.charset.MalformedInputException}。
     */
    private static String readLenientUtf8(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    private String persistTemplateContent(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return rawContent;
        }
        String trimmed = rawContent.trim();
        // 如果输入本身像一个 .tex 文件路径且存在，直接复用
        boolean maybePath = !trimmed.contains("\n") && !trimmed.contains("\r") && trimmed.toLowerCase().endsWith(".tex");
        if (maybePath) {
            try {
                Path existingPath = Paths.get(trimmed);
                if (Files.exists(existingPath) && Files.isRegularFile(existingPath)) {
                    return trimmed;
                }
            } catch (Exception ignored) {
                // 不是合法路径就按正文落盘
            }
        }

        // 否则按 LaTeX 内容落盘到模板目录，再存储路径
        try {
            Path templateDir = Paths.get("./static/templates").toAbsolutePath().normalize();
            Files.createDirectories(templateDir);
            String fileName = "template_" + UUID.randomUUID().toString().replace("-", "") + ".tex";
            Path target = templateDir.resolve(fileName);
            Files.writeString(target, rawContent, StandardCharsets.UTF_8);
            return target.toString();
        } catch (Exception e) {
            throw new RuntimeException("保存模板内容失败: " + e.getMessage(), e);
        }
    }

    private TemplateDTO normalizeTemplate(TemplateDTO template) {
        if (template == null) {
            return null;
        }
        if (template.getCategory() == null) {
            template.setCategory("general");
        }
        if (template.getIsSystem() == null) {
            template.setIsSystem(false);
        }
        if (template.getUsageCount() == null) {
            template.setUsageCount(0);
        }
        if (template.getUpdatedAt() == null) {
            template.setUpdatedAt(template.getCreatedAt());
        }
        // 数据库存的是 template_path（磁盘路径字符串），展开成正文并带上 sourcePath 供「从模板创建项目」使用
        if (template.getContent() == null || template.getContent().isBlank()) {
            template.setContent("\\documentclass{article}\n\\begin{document}\n\n\\end{document}");
        } else {
            String raw = template.getContent();
            String normalized = raw.replace("\r\n", "\n");
            // 历史或手工数据：仅首行标记；正文必须从磁盘读（库字段通常为 VARCHAR，不能存全文）
            if (normalized.trim().startsWith(TEMPLATE_SOURCE_PREFIX)) {
                int firstNl = normalized.indexOf('\n');
                String firstLine = firstNl >= 0 ? normalized.substring(0, firstNl) : normalized;
                String pathStr = firstLine.substring(TEMPLATE_SOURCE_PREFIX.length()).trim();
                resolveTemplateFromTexPath(template, pathStr);
            } else {
                String candidatePath = template.getContent().trim();
                if (!candidatePath.contains("\n") && !candidatePath.contains("\r")) {
                    resolveTemplateFromTexPath(template, candidatePath);
                }
            }
        }
        return template;
    }

    /**
     * template_path 存主 .tex 绝对路径时：始终设置 sourcePath；正文优先从该文件读取。
     */
    private void resolveTemplateFromTexPath(TemplateDTO template, String pathStr) {
        if (pathStr == null || pathStr.isBlank()) {
            return;
        }
        try {
            Path path = Paths.get(pathStr.trim());
            if (!path.isAbsolute()) {
                path = Paths.get(".").resolve(pathStr.trim()).normalize();
            }
            path = path.toAbsolutePath().normalize();
            if (pathStr.trim().toLowerCase(Locale.ROOT).endsWith(".tex")) {
                template.setSourcePath(path.toString());
            }
            if (Files.exists(path) && Files.isRegularFile(path)) {
                String latexContent = readLenientUtf8(path);
                if (latexContent != null && !latexContent.isBlank()) {
                    template.setContent(latexContent);
                }
            }
        } catch (Exception ignored) {
            // 路径不可解析时保留库中原值
        }
    }

    private String sanitizeTemplateName(String rawName) {
        String base = rawName == null ? "" : rawName.trim();
        if (base.isEmpty()) {
            return "导入模板";
        }
        return base.length() > 100 ? base.substring(0, 100) : base;
    }

    /**
     * 多个 .tex 时整包只对应一个模板，主文件固定为 main.tex（优先 bundle 根目录下的 main.tex）。
     */
    private Path resolvePrimaryTexPath(List<Path> texFiles, Path bundleDir) throws IOException {
        List<Path> nonEmpty = new ArrayList<>();
        for (Path p : texFiles) {
            if (Files.isRegularFile(p) && Files.size(p) > 0) {
                nonEmpty.add(p.toAbsolutePath().normalize());
            }
        }
        if (nonEmpty.isEmpty()) {
            return null;
        }
        if (nonEmpty.size() == 1) {
            return nonEmpty.get(0);
        }
        Path bundleAbs = bundleDir.toAbsolutePath().normalize();
        Path rootMain = bundleAbs.resolve("main.tex").normalize();
        if (nonEmpty.contains(rootMain)) {
            return rootMain;
        }
        Path anyMain = nonEmpty.stream()
                .filter(p -> p.getFileName().toString().equalsIgnoreCase("main.tex"))
                .min(Comparator.comparingInt(Path::getNameCount))
                .orElse(null);
        if (anyMain == null) {
            throw new RuntimeException(
                    "zip 中包含多个 .tex 文件时，请提供 main.tex 作为主文件（建议放在包根目录）");
        }
        return anyMain;
    }

    @Override
    public TemplateDTO createTemplate(TemplateDTO templateDTO) {
        // 兼容旧前端字段：当前数据库不存储这三项，返回层仍保留
        if (templateDTO.getCategory() == null) {
            templateDTO.setCategory("general");
        }
        if (templateDTO.getIsSystem() == null) {
            templateDTO.setIsSystem(false);
        }
        if (templateDTO.getUsageCount() == null) {
            templateDTO.setUsageCount(0);
        }

        // 设置创建时间
        LocalDateTime now = LocalDateTime.now();
        templateDTO.setCreatedAt(now);
        templateDTO.setUpdatedAt(now);
        templateDTO.setContent(persistTemplateContent(templateDTO.getContent()));

        // 插入模板
        int result = templateMapper.insert(templateDTO);
        if (result > 0) {
            return templateDTO;
        }
        throw new RuntimeException("创建模板失败");
    }

    @Override
    public TemplateDTO updateTemplate(Long id, TemplateDTO templateDTO) {
        // 检查模板是否存在
        TemplateDTO existing = templateMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("模板不存在");
        }

        // 设置ID和更新时间（updatedAt 仅供响应层使用）
        templateDTO.setId(id);
        templateDTO.setUpdatedAt(LocalDateTime.now());
        if (templateDTO.getContent() != null && !templateDTO.getContent().isBlank()) {
            templateDTO.setContent(persistTemplateContent(templateDTO.getContent()));
        }

        // 更新模板
        int result = templateMapper.updateById(templateDTO);
        if (result > 0) {
            return templateMapper.selectById(id);
        }
        throw new RuntimeException("更新模板失败");
    }

    @Override
    public void deleteTemplate(Long id) {
        // 检查模板是否存在
        TemplateDTO existing = templateMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("模板不存在");
        }

        // 删除模板
        int result = templateMapper.deleteById(id);
        if (result <= 0) {
            throw new RuntimeException("删除模板失败");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public TemplateDTO getTemplateById(Long id) {
        return normalizeTemplate(templateMapper.selectById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TemplateDTO> getAllTemplates() {
        List<TemplateDTO> templates = templateMapper.selectAll();
        templates.forEach(this::normalizeTemplate);
        return templates;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TemplateDTO> getTemplatesByCategory(String category) {
        List<TemplateDTO> templates = templateMapper.selectByCategory(category);
        // 当前数据库无分类字段，这里保持接口兼容：由服务层补齐 category 供前端筛选展示
        templates.forEach(this::normalizeTemplate);
        return templates;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TemplateDTO> getSystemTemplates() {
        List<TemplateDTO> templates = templateMapper.selectSystemTemplates();
        templates.forEach(t -> {
            normalizeTemplate(t);
            t.setIsSystem(true);
            t.setCategory("system");
        });
        return templates;
    }

    @Override
    public void incrementUsageCount(Long templateId) {
        // 检查模板是否存在
        TemplateDTO existing = templateMapper.selectById(templateId);
        if (existing == null) {
            throw new RuntimeException("模板不存在");
        }

        // 当前数据库无 usage_count 字段，保持接口幂等兼容，不抛错
        templateMapper.incrementUsageCount(templateId);
    }

    @Override
    public int importTemplatesFromZip(MultipartFile zipFile, String templateName, String templateDescription) {
        String normalizedName = sanitizeTemplateName(templateName);
        String normalizedDescription = templateDescription == null ? "" : templateDescription.trim();
        if (normalizedDescription.isEmpty()) {
            throw new RuntimeException("模板描述不能为空");
        }
        ZipLatexBundleUtil.requireZipExtension(zipFile);

        Path templateDir = Paths.get("./static/templates").toAbsolutePath().normalize();
        int importedCount = 0;

        try {
            ZipLatexBundleUtil.BundleResult bundleResult = ZipLatexBundleUtil.extractZipBundle(zipFile, templateDir);
            List<Path> texFiles = bundleResult.texFiles();
            Path primaryTex = resolvePrimaryTexPath(texFiles, bundleResult.bundleDir());
            if (primaryTex == null) {
                throw new RuntimeException("zip 中未找到可导入的 .tex 模板文件");
            }
            Path primaryAbs = primaryTex.toAbsolutePath().normalize();
            if (!Files.exists(primaryAbs) || !Files.isRegularFile(primaryAbs) || Files.size(primaryAbs) == 0L) {
                throw new RuntimeException("主 .tex 文件无效或为空");
            }
            // 仅保存主文件绝对路径（template_path 列为 VARCHAR 时常较短）；正文始终在磁盘 bundle 中，由 normalizeTemplate 读取
            TemplateDTO dto = new TemplateDTO();
            dto.setName(normalizedName);
            dto.setDescription(normalizedDescription);
            dto.setContent(primaryAbs.toString());
            dto.setPreviewImage("");
            dto.setCategory("general");
            dto.setIsSystem(false);
            dto.setUsageCount(0);
            LocalDateTime now = LocalDateTime.now();
            dto.setCreatedAt(now);
            dto.setUpdatedAt(now);

            int result = templateMapper.insert(dto);
            if (result > 0) {
                importedCount = 1;
            }
        } catch (Exception e) {
            throw new RuntimeException("导入模板失败: " + e.getMessage(), e);
        }

        if (importedCount == 0) {
            throw new RuntimeException("zip 中未找到可导入的 .tex 模板文件");
        }
        return importedCount;
    }
}

