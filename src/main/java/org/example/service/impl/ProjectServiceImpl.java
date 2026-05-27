package org.example.service.impl;

import org.example.dto.CompileResult;
import org.example.dto.PdfWordFileDTO;
import org.example.dto.ProjectDTO;
import org.example.dto.ProjectWorkspaceInfoDTO;
import org.example.dto.WorkspaceFilePayloadDTO;
import org.example.mapper.AILogMapper;
import org.example.mapper.PdfWordFileMapper;
import org.example.mapper.ProjectMapper;
import org.example.service.ProjectService;
import org.example.util.LatexCompileUtil;
import org.example.util.ProjectBundleWorkspaceUtil;
import org.example.util.ProjectBundleWorkspaceUtil.ParsedBundle;
import org.example.util.WordExportUtil;
import org.example.util.ZipLatexBundleUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 项目服务实现类
 */
@Service
@Transactional
public class ProjectServiceImpl implements ProjectService {

    private record BundleContext(Path bundleRoot, Path entryPath, String entryRelative) {
    }

    @Autowired
    private ProjectMapper projectMapper;
    
    @Autowired
    private LatexCompileUtil latexCompileUtil;

    @Autowired
    private WordExportUtil wordExportUtil;

    @Autowired
    private PdfWordFileMapper pdfWordFileMapper;

    @Autowired
    private AILogMapper aiLogMapper;

    @Value("${latex.compile.output-dir:./static/pdf}")
    private String pdfOutputDir;

    @Value("${latex.compile.max-pdf-versions-per-project:3}")
    private int maxPdfVersionsPerProject;

    @Value("${word.export.output-dir:./static/word}")
    private String wordOutputDir;

    /** 同一项目保留的 Word 导出版本上限（与 PDF 独立） */
    private static final int MAX_WORD_VERSIONS_PER_PROJECT = 10;

    @Value("${image.upload.path:./static/images}")
    private String imageUploadPath;

    private static final String TEMPLATE_SOURCE_LINE_PREFIX = "%TEMPLATE_SOURCE_PATH=";

    private Path templatesStaticDir() {
        return Paths.get("./static/templates").toAbsolutePath().normalize();
    }

    private Path projectBundlesStaticDir() {
        return Paths.get("./static/project_bundles").toAbsolutePath().normalize();
    }

    /**
     * 将 sourceRoot 下整棵目录树复制到 destRoot（含空子目录）。
     */
    private void copyDirectoryRecursive(Path sourceRoot, Path destRoot) throws IOException {
        Path src = sourceRoot.toAbsolutePath().normalize();
        Path dst = destRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(src)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(src)) {
            for (Path source : (Iterable<Path>) walk::iterator) {
                Path relative = src.relativize(source);
                Path target = dst.resolve(relative);
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Path parent = target.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /**
     * 若 {@code %TEMPLATE_SOURCE_PATH=} 指向 static/templates 下的文件，则整包复制到 static/project_bundles，
     * 并返回更新后的首行路径，避免保存/编译时写回模板目录。
     */
    private String materializeTemplateBundleIfReferenced(String content) throws IOException {
        ParsedBundle parsed = ProjectBundleWorkspaceUtil.parseStoredContent(content);
        if (parsed == null) {
            return content;
        }
        Path entry = Paths.get(parsed.entryAbsolutePath()).normalize().toAbsolutePath();
        if (!Files.exists(entry) || !Files.isRegularFile(entry)) {
            return content;
        }
        Path projectBundlesBase = projectBundlesStaticDir();
        if (entry.startsWith(projectBundlesBase)) {
            return content;
        }
        Path templatesBase = templatesStaticDir();
        if (!entry.startsWith(templatesBase)) {
            return content;
        }
        Path oldBundleRoot = ProjectBundleWorkspaceUtil.locateBundleRoot(entry);
        if (oldBundleRoot == null) {
            return content;
        }
        oldBundleRoot = oldBundleRoot.normalize().toAbsolutePath();
        if (!Files.isDirectory(oldBundleRoot) || !oldBundleRoot.startsWith(templatesBase)) {
            return content;
        }

        Files.createDirectories(projectBundlesBase);
        String newBundleId = "bundle_" + UUID.randomUUID().toString().replace("-", "");
        Path newBundleRoot = projectBundlesBase.resolve(newBundleId);
        Files.createDirectories(newBundleRoot);

        Path newEntry;
        if (Files.isSameFile(oldBundleRoot, templatesBase)) {
            Path destFile = newBundleRoot.resolve(entry.getFileName());
            Files.copy(entry, destFile, StandardCopyOption.REPLACE_EXISTING);
            newEntry = destFile.normalize().toAbsolutePath();
        } else {
            copyDirectoryRecursive(oldBundleRoot, newBundleRoot);
            newEntry = newBundleRoot.resolve(oldBundleRoot.relativize(entry)).normalize().toAbsolutePath();
            if (!newEntry.startsWith(newBundleRoot)) {
                throw new IOException("入口路径解析异常");
            }
        }
        ProjectBundleWorkspaceUtil.writeUtf8(newEntry, parsed.entryBody());
        return TEMPLATE_SOURCE_LINE_PREFIX + newEntry + "\n" + parsed.entryBody();
    }

    /**
     * 无有效 {@code %TEMPLATE_SOURCE_PATH=}（或指向的文件已不存在）时，在 {@code project_bundles} 下新建 bundle 并写入 {@code main.tex}，
     * 使项目内容首行始终绑定磁盘入口，孤儿 bundle 清理不会误删。
     */
    private String materializePlainTexToPrivateBundleIfNeeded(String content) throws IOException {
        if (content == null || content.isBlank()) {
            return content;
        }
        // 已有有效首行且入口文件存在（parseStoredContent 会校验）
        if (ProjectBundleWorkspaceUtil.parseStoredContent(content) != null) {
            return content;
        }

        String normalized = content.replace("\r\n", "\n");
        String body = normalized;
        String trim = normalized.trim();
        if (trim.startsWith(TEMPLATE_SOURCE_LINE_PREFIX)) {
            int nl = normalized.indexOf('\n');
            body = nl >= 0 ? normalized.substring(nl + 1) : "";
        }

        Path projectBundlesBase = projectBundlesStaticDir();
        Files.createDirectories(projectBundlesBase);
        String newBundleId = "bundle_" + UUID.randomUUID().toString().replace("-", "");
        Path newBundleRoot = projectBundlesBase.resolve(newBundleId);
        Files.createDirectories(newBundleRoot);
        Path newEntry = newBundleRoot.resolve("main.tex").normalize().toAbsolutePath();
        ProjectBundleWorkspaceUtil.writeUtf8(newEntry, body);
        return TEMPLATE_SOURCE_LINE_PREFIX + newEntry + "\n" + body;
    }

    private ProjectDTO ensureProjectContentUsesPrivateBundle(ProjectDTO project) {
        if (project == null || project.getContent() == null) {
            return project;
        }
        try {
            String isolated = materializeTemplateBundleIfReferenced(project.getContent());
            if (isolated.equals(project.getContent())) {
                return project;
            }
            project.setContent(isolated);
            project.setUpdatedAt(LocalDateTime.now());
            projectMapper.updateById(project);
            return projectMapper.selectById(project.getId());
        } catch (IOException e) {
            throw new RuntimeException("复制模板到项目工作区失败: " + e.getMessage(), e);
        }
    }

    private BundleContext resolveBundleContext(ProjectDTO project) {
        if (project == null || project.getContent() == null) {
            return null;
        }
        ParsedBundle parsed = ProjectBundleWorkspaceUtil.parseStoredContent(project.getContent());
        if (parsed == null) {
            return null;
        }
        try {
            Path entry = Paths.get(parsed.entryAbsolutePath()).normalize();
            if (!Files.exists(entry) || !Files.isRegularFile(entry)) {
                return null;
            }
            Path bundleRoot = ProjectBundleWorkspaceUtil.locateBundleRoot(entry);
            if (bundleRoot == null || !Files.isDirectory(bundleRoot)) {
                return null;
            }
            String entryRel = bundleRoot.relativize(entry).toString().replace("\\", "/");
            return new BundleContext(bundleRoot, entry, entryRel);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void syncBundleEntryTexToDisk(String fullContent) {
        ParsedBundle parsed = ProjectBundleWorkspaceUtil.parseStoredContent(fullContent);
        if (parsed == null) {
            return;
        }
        try {
            Path texPath = Paths.get(parsed.entryAbsolutePath()).normalize();
            if (!Files.exists(texPath) || !Files.isRegularFile(texPath)) {
                return;
            }
            ProjectBundleWorkspaceUtil.writeUtf8(texPath, parsed.entryBody());
        } catch (Exception ignored) {
            // 磁盘与 DB 同步失败不阻塞保存
        }
    }

    private Path pickPrimaryTexFile(List<Path> texFiles) {
        if (texFiles == null || texFiles.isEmpty()) {
            throw new RuntimeException("zip 中未找到 .tex 文件");
        }
        Path bestMain = null;
        Path bestDoc = null;
        for (Path p : texFiles) {
            if (p.getFileName() == null) {
                continue;
            }
            String fn = p.getFileName().toString();
            if ("main.tex".equalsIgnoreCase(fn)) {
                if (bestMain == null || p.getNameCount() < bestMain.getNameCount()) {
                    bestMain = p;
                }
            }
            if ("document.tex".equalsIgnoreCase(fn)) {
                if (bestDoc == null || p.getNameCount() < bestDoc.getNameCount()) {
                    bestDoc = p;
                }
            }
        }
        if (bestMain != null) {
            return bestMain;
        }
        if (bestDoc != null) {
            return bestDoc;
        }
        return texFiles.get(0);
    }

    private void recordFile(Long projectId, String filename, String absolutePath) {
        PdfWordFileDTO file = new PdfWordFileDTO();
        file.setProjectId(projectId);
        file.setFilename(filename);
        file.setFilePath(absolutePath);
        file.setUploadedAt(LocalDateTime.now());
        pdfWordFileMapper.insert(file);
    }

    private void trimBySuffix(Long projectId, String suffixPattern, int maxKeep) {
        int cap = Math.max(1, maxKeep);
        List<PdfWordFileDTO> files = pdfWordFileMapper.selectByProjectAndSuffix(projectId, suffixPattern);
        if (files == null || files.size() <= cap) {
            return;
        }
        for (int i = cap; i < files.size(); i++) {
            PdfWordFileDTO file = files.get(i);
            try {
                if (file.getFilePath() != null && !file.getFilePath().isBlank()) {
                    Files.deleteIfExists(Paths.get(file.getFilePath()));
                }
            } catch (Exception ignored) {
                // 删除物理文件失败时依然删除记录，避免无限膨胀
            }
            pdfWordFileMapper.deleteById(file.getId());
        }
    }

    private void enforceFileRetention(Long projectId) {
        trimBySuffix(projectId, "%.pdf", maxPdfVersionsPerProject);
        trimBySuffix(projectId, "%.docx", MAX_WORD_VERSIONS_PER_PROJECT);
    }

    @Override
    public ProjectDTO createProject(ProjectDTO projectDTO) {
        // 设置创建时间和修改时间
        LocalDateTime now = LocalDateTime.now();
        projectDTO.setCreatedAt(now);
        projectDTO.setUpdatedAt(now);

        if (projectDTO.getContent() != null) {
            try {
                String c = materializeTemplateBundleIfReferenced(projectDTO.getContent());
                c = materializePlainTexToPrivateBundleIfNeeded(c);
                projectDTO.setContent(c);
            } catch (IOException e) {
                throw new RuntimeException("复制模板到项目工作区失败: " + e.getMessage(), e);
            }
        }

        // 插入项目
        int result = projectMapper.insert(projectDTO);
        if (result > 0) {
            return projectDTO;
        }
        throw new RuntimeException("创建项目失败");
    }

    @Override
    public List<ProjectDTO> getProjectsByUserId(Long userId) {
        return projectMapper.selectByUserId(userId);
    }
    
    @Override
    public ProjectDTO getProjectById(Long id) {
        return projectMapper.selectById(id);
    }

    @Override
    public ProjectDTO updateProjectById(ProjectDTO projectDTO) {
        // 获取现有项目信息
        ProjectDTO existingProject = projectMapper.selectById(projectDTO.getId());
        if (existingProject == null) {
            throw new RuntimeException("项目不存在");
        }
        
        // 设置更新时间
        LocalDateTime now = LocalDateTime.now();
        projectDTO.setUpdatedAt(now);

        // 未在请求中携带的字段保留原值（支持仅改名、仅改内容或同时修改）
        if (projectDTO.getContent() == null) {
            projectDTO.setContent(existingProject.getContent());
        }
        if (projectDTO.getName() == null || projectDTO.getName().trim().isEmpty()) {
            projectDTO.setName(existingProject.getName());
        } else {
            String trimmedName = projectDTO.getName().trim();
            projectDTO.setName(trimmedName.length() > 200 ? trimmedName.substring(0, 200) : trimmedName);
        }
        projectDTO.setUserId(existingProject.getUserId());
        projectDTO.setCreatedAt(existingProject.getCreatedAt());

        try {
            projectDTO.setContent(materializeTemplateBundleIfReferenced(projectDTO.getContent()));
        } catch (IOException e) {
            throw new RuntimeException("复制模板到项目工作区失败: " + e.getMessage(), e);
        }

        // 更新项目
        int result = projectMapper.updateById(projectDTO);
        if (result > 0) {
            if (projectDTO.getContent() != null) {
                syncBundleEntryTexToDisk(projectDTO.getContent());
            }
            return projectDTO;
        }
        throw new RuntimeException("更新项目失败");
    }
    
    @Override
    public CompileResult compileProject(Long projectId, String compiler) {
        // 获取项目内容
        ProjectDTO project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new RuntimeException("项目不存在");
        }
        project = ensureProjectContentUsesPrivateBundle(project);
        // 如果项目内容为空
        if (project.getContent() == null || project.getContent().trim().isEmpty()) {
            CompileResult result = new CompileResult();
            result.setStatus("ERROR");
            result.setErrorMessage("项目内容为空，无法编译");
            result.setCreatedAt(LocalDateTime.now());
            return result;
        }
        // 使用编译工具编译
        try {
            CompileResult result = latexCompileUtil.compile(
                project.getContent(), 
                projectId, 
                compiler != null ? compiler : "pdflatex"
            );
            if (result != null && result.getPdfPath() != null && !result.getPdfPath().isBlank()) {
                String pdfName = Paths.get(result.getPdfPath()).getFileName().toString();
                Path absolutePdf = Paths.get(pdfOutputDir).toAbsolutePath().normalize().resolve(pdfName);
                recordFile(projectId, pdfName, absolutePdf.toString());
            }
            enforceFileRetention(projectId);
            return result;
        } catch (Exception e) {
            CompileResult result = new CompileResult();
            result.setStatus("ERROR");
            result.setErrorMessage("编译失败: " + e.getMessage());
            result.setCreatedAt(LocalDateTime.now());
            return result;
        }
    }

    @Override
    public String exportProjectToWord(Long projectId) {
        ProjectDTO project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new RuntimeException("项目不存在");
        }
        project = ensureProjectContentUsesPrivateBundle(project);
        if (project.getContent() == null || project.getContent().trim().isEmpty()) {
            throw new RuntimeException("项目内容为空，无法导出 Word");
        }
        try {
            Path outputPath = wordExportUtil.export(project.getContent(), projectId);
            String filename = outputPath.getFileName() == null ? ("project_" + projectId + ".docx") : outputPath.getFileName().toString();
            recordFile(projectId, filename, outputPath.toString());
            enforceFileRetention(projectId);
            return outputPath.toString();
        } catch (Exception e) {
            throw new RuntimeException("导出 Word 失败: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] exportProjectZipArchive(Long projectId) {
        ProjectDTO project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new RuntimeException("项目不存在");
        }
        project = ensureProjectContentUsesPrivateBundle(project);
        String content = project.getContent() == null ? "" : project.getContent();
        try {
            syncBundleEntryTexToDisk(content);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            BundleContext ctx = resolveBundleContext(project);
            if (ctx != null) {
                ZipLatexBundleUtil.zipDirectory(ctx.bundleRoot(), baos);
            } else {
                ZipLatexBundleUtil.zipSingleTexEntry("main.tex", content, baos);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("导出 zip 失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteProjectById(Long projectId) {
        ProjectDTO existing = projectMapper.selectById(projectId);
        if (existing == null) {
            throw new RuntimeException("项目不存在");
        }
        final List<PdfWordFileDTO> trackedExports = pdfWordFileMapper.selectAllByProjectId(projectId);
        aiLogMapper.deleteByProjectId(projectId);
        pdfWordFileMapper.deleteByProjectId(projectId);
        int result = projectMapper.deleteById(projectId);
        if (result <= 0) {
            throw new RuntimeException("删除项目失败");
        }
        final ProjectDTO projectSnapshot = existing;
        final Long pid = projectId;
        Runnable deleteArtifacts = () -> {
            deletePdfWordPhysicalFiles(trackedExports);
            cleanupProjectFilesystem(projectSnapshot, pid);
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteArtifacts.run();
                }
            });
        } else {
            deleteArtifacts.run();
        }
    }

    private void deletePdfWordPhysicalFiles(List<PdfWordFileDTO> trackedExports) {
        if (trackedExports == null) {
            return;
        }
        for (PdfWordFileDTO f : trackedExports) {
            if (f.getFilePath() != null && !f.getFilePath().isBlank()) {
                deletePathQuietly(Paths.get(f.getFilePath()));
            }
        }
    }

    private void cleanupProjectFilesystem(ProjectDTO projectSnapshot, Long projectId) {
        try {
            deleteGeneratedFilesByPrefix(pdfOutputDir, projectId, ".pdf");
            deleteGeneratedFilesByPrefix(wordOutputDir, projectId, ".docx");
            deleteBundleWorkspaceIfPresent(projectSnapshot);
            deleteUploadedImagesForProject(projectId);
        } catch (Exception ignored) {
        }
    }

    private void deleteGeneratedFilesByPrefix(String dirConfig, Long projectId, String suffix) {
        Path dir = Paths.get(dirConfig).toAbsolutePath().normalize();
        if (!Files.isDirectory(dir)) {
            return;
        }
        String prefix = "project_" + projectId + "_";
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        return n.startsWith(prefix) && n.endsWith(suffix);
                    })
                    .forEach(this::deletePathQuietly);
        } catch (Exception ignored) {
        }
    }

    private void deleteBundleWorkspaceIfPresent(ProjectDTO project) {
        if (project == null) {
            return;
        }
        Path bundlesBase = Paths.get("./static/project_bundles").toAbsolutePath().normalize();

        BundleContext ctx = resolveBundleContext(project);
        if (ctx != null && ctx.bundleRoot() != null) {
            tryDeleteBundleUnderProjectBundles(ctx.bundleRoot(), bundlesBase);
            return;
        }

        Optional<Path> entryOpt = ProjectBundleWorkspaceUtil.parseTemplateSourceEntryPathFromContent(project.getContent());
        if (entryOpt.isEmpty()) {
            return;
        }
        Path bundleRoot = ProjectBundleWorkspaceUtil.locateBundleRoot(entryOpt.get());
        if (bundleRoot == null) {
            return;
        }
        tryDeleteBundleUnderProjectBundles(bundleRoot, bundlesBase);
    }

    /**
     * 仅删除位于 {@code static/project_bundles} 下的 bundle 根目录（防误删其它路径）。
     */
    private void tryDeleteBundleUnderProjectBundles(Path bundleRoot, Path bundlesBase) {
        if (bundleRoot == null || bundlesBase == null) {
            return;
        }
        Path root = bundleRoot.toAbsolutePath().normalize();
        if (!root.startsWith(bundlesBase)) {
            return;
        }
        deleteDirectoryTree(root);
    }

    private void deleteUploadedImagesForProject(Long projectId) {
        Path dir = Paths.get(imageUploadPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(dir)) {
            return;
        }
        String prefix = "project_" + projectId + "_";
        try (Stream<Path> s = Files.list(dir)) {
            s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith(prefix))
                    .forEach(this::deletePathQuietly);
        } catch (Exception ignored) {
        }
    }

    private void deletePathQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
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

    @Override
    public ProjectDTO importProjectFromZip(MultipartFile zipFile, String projectName, Long userId) {
        ZipLatexBundleUtil.requireZipExtension(zipFile);
        if (userId == null) {
            throw new RuntimeException("用户未登录");
        }
        String normalizedName = projectName == null ? "" : projectName.trim();
        if (normalizedName.isEmpty()) {
            throw new RuntimeException("项目名称不能为空");
        }
        if (normalizedName.length() > 200) {
            normalizedName = normalizedName.substring(0, 200);
        }

        Path projectBundlesDir = Paths.get("./static/project_bundles").toAbsolutePath().normalize();
        Path extractedBundleDir = null;
        try {
            ZipLatexBundleUtil.BundleResult bundleResult = ZipLatexBundleUtil.extractZipBundle(zipFile, projectBundlesDir);
            extractedBundleDir = bundleResult.bundleDir();
            List<Path> texFiles = bundleResult.texFiles();
            if (texFiles.isEmpty()) {
                throw new RuntimeException("zip 中未找到 .tex 文件");
            }

            Path primary = pickPrimaryTexFile(texFiles);
            String latexContent = Files.readString(primary, StandardCharsets.UTF_8);
            if (latexContent == null || latexContent.isBlank()) {
                for (Path p : texFiles) {
                    String c = Files.readString(p, StandardCharsets.UTF_8);
                    if (c != null && !c.isBlank()) {
                        primary = p;
                        latexContent = c;
                        break;
                    }
                }
            }
            if (latexContent == null || latexContent.isBlank()) {
                throw new RuntimeException("zip 中的 .tex 文件内容为空");
            }

            String sourcePath = primary.toAbsolutePath().normalize().toString();
            String storedContent = TEMPLATE_SOURCE_LINE_PREFIX + sourcePath + "\n" + latexContent;

            ProjectDTO projectDTO = new ProjectDTO();
            projectDTO.setUserId(userId);
            projectDTO.setName(normalizedName);
            projectDTO.setContent(storedContent);
            ProjectDTO created = createProject(projectDTO);
            extractedBundleDir = null;
            return created;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("导入项目失败: " + e.getMessage(), e);
        } finally {
            if (extractedBundleDir != null) {
                tryDeleteBundleUnderProjectBundles(extractedBundleDir, projectBundlesDir);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectWorkspaceInfoDTO getProjectWorkspaceInfo(Long projectId) {
        ProjectWorkspaceInfoDTO dto = new ProjectWorkspaceInfoDTO();
        ProjectDTO project = projectMapper.selectById(projectId);
        if (project == null) {
            return dto;
        }
        BundleContext ctx = resolveBundleContext(project);
        if (ctx == null) {
            return dto;
        }
        try {
            List<String> files = new ArrayList<>(
                    ProjectBundleWorkspaceUtil.listEditableRelativeFiles(ctx.bundleRoot()));
            if (files.isEmpty() && Files.isRegularFile(ctx.entryPath())) {
                files.add(ctx.entryRelative());
            }
            dto.setBundleMode(true);
            dto.setEntryRelativePath(ctx.entryRelative());
            dto.setFiles(files);
        } catch (Exception e) {
            throw new RuntimeException("读取项目文件列表失败: " + e.getMessage(), e);
        }
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspaceFilePayloadDTO readProjectWorkspaceFile(Long projectId, String relativePath) {
        ProjectDTO project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new RuntimeException("项目不存在");
        }
        BundleContext ctx = resolveBundleContext(project);
        if (ctx == null) {
            throw new RuntimeException("当前项目不是多文件工作区");
        }
        if (relativePath == null || relativePath.isBlank()) {
            throw new RuntimeException("文件路径不能为空");
        }
        try {
            Path target = ProjectBundleWorkspaceUtil.resolveWithinBundle(ctx.bundleRoot(), relativePath);
            if (!Files.exists(target) || !Files.isRegularFile(target)) {
                throw new RuntimeException("文件不存在");
            }
            String text = Files.readString(target, StandardCharsets.UTF_8);
            WorkspaceFilePayloadDTO out = new WorkspaceFilePayloadDTO();
            out.setPath(relativePath.replace("\\", "/"));
            out.setContent(text);
            return out;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("读取文件失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void writeProjectWorkspaceFile(Long projectId, WorkspaceFilePayloadDTO payload) {
        if (payload == null || payload.getPath() == null || payload.getPath().isBlank()) {
            throw new RuntimeException("文件路径不能为空");
        }
        ProjectDTO project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new RuntimeException("项目不存在");
        }
        project = ensureProjectContentUsesPrivateBundle(project);
        BundleContext ctx = resolveBundleContext(project);
        if (ctx == null) {
            throw new RuntimeException("当前项目不是多文件工作区");
        }
        try {
            Path target = ProjectBundleWorkspaceUtil.resolveWithinBundle(ctx.bundleRoot(), payload.getPath());
            if (target.equals(ctx.entryPath())) {
                throw new RuntimeException("主入口文件请使用「保存」更新，勿通过工作区接口覆盖");
            }
            ProjectBundleWorkspaceUtil.writeUtf8(target, payload.getContent() == null ? "" : payload.getContent());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("写入文件失败: " + e.getMessage(), e);
        }
    }
}

