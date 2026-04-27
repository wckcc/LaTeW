package org.example.service.impl;

import org.example.dto.TemplateDTO;
import org.example.mapper.TemplateMapper;
import org.example.service.TemplateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 模板服务实现类
 */
@Service
@Transactional
public class TemplateServiceImpl implements TemplateService {

    @Autowired
    private TemplateMapper templateMapper;

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
        // 数据库存的是 template_path，尽量展开成可直接创建项目的 LaTeX 内容
        if (template.getContent() == null || template.getContent().isBlank()) {
            template.setContent("\\documentclass{article}\n\\begin{document}\n\n\\end{document}");
        } else {
            String candidatePath = template.getContent().trim();
            try {
                Path path = Paths.get(candidatePath);
                if (!path.isAbsolute()) {
                    path = Paths.get(".").resolve(candidatePath).normalize();
                }
                if (Files.exists(path) && Files.isRegularFile(path)) {
                    template.setSourcePath(path.toString());
                    String latexContent = Files.readString(path, StandardCharsets.UTF_8);
                    if (latexContent != null && !latexContent.isBlank()) {
                        template.setContent(latexContent);
                    }
                }
            } catch (Exception ignored) {
                // 路径不可读时保留原值，避免模板接口失败
            }
        }
        return template;
    }

    private String sanitizeTemplateName(String rawName) {
        String base = rawName == null ? "" : rawName.trim();
        if (base.isEmpty()) {
            return "导入模板";
        }
        return base.length() > 100 ? base.substring(0, 100) : base;
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
        if (zipFile == null || zipFile.isEmpty()) {
            throw new RuntimeException("zip 文件不能为空");
        }
        String normalizedName = sanitizeTemplateName(templateName);
        String normalizedDescription = templateDescription == null ? "" : templateDescription.trim();
        if (normalizedDescription.isEmpty()) {
            throw new RuntimeException("模板描述不能为空");
        }
        String originalName = zipFile.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw new RuntimeException("请上传 .zip 格式文件");
        }

        Path templateDir = Paths.get("./static/templates").toAbsolutePath().normalize();
        int importedCount = 0;
        int texFileIndex = 0;

        try {
            Files.createDirectories(templateDir);
            String bundleId = "bundle_" + UUID.randomUUID().toString().replace("-", "");
            Path bundleDir = templateDir.resolve(bundleId);
            Files.createDirectories(bundleDir);

            List<Path> texFiles = new ArrayList<>();
            try (InputStream rawInput = zipFile.getInputStream();
                 ZipInputStream zis = new ZipInputStream(rawInput, StandardCharsets.UTF_8)) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String entryName = entry.getName();
                    if (entryName == null || entryName.isBlank()) {
                        zis.closeEntry();
                        continue;
                    }

                    Path normalizedEntryPath = Paths.get(entryName).normalize();
                    if (normalizedEntryPath.isAbsolute() || normalizedEntryPath.startsWith("..")) {
                        zis.closeEntry();
                        continue;
                    }

                    Path target = bundleDir.resolve(normalizedEntryPath).normalize();
                    if (!target.startsWith(bundleDir)) {
                        zis.closeEntry();
                        continue;
                    }

                    if (entry.isDirectory()) {
                        Files.createDirectories(target);
                        zis.closeEntry();
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
                    zis.closeEntry();
                }
            }

            for (Path texPath : texFiles) {
                String latexContent = Files.readString(texPath, StandardCharsets.UTF_8);
                if (latexContent == null || latexContent.isBlank()) {
                    continue;
                }

                texFileIndex += 1;
                String finalName = texFileIndex == 1
                        ? normalizedName
                        : sanitizeTemplateName(normalizedName + "-" + texFileIndex);

                TemplateDTO dto = new TemplateDTO();
                dto.setName(finalName);
                dto.setDescription(normalizedDescription);
                dto.setContent(texPath.toString());
                dto.setPreviewImage("");
                dto.setCategory("general");
                dto.setIsSystem(false);
                dto.setUsageCount(0);
                LocalDateTime now = LocalDateTime.now();
                dto.setCreatedAt(now);
                dto.setUpdatedAt(now);

                int result = templateMapper.insert(dto);
                if (result > 0) {
                    importedCount += 1;
                }
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

