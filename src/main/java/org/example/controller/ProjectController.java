package org.example.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.dto.CompileRequest;
import org.example.dto.CompileResult;
import org.example.dto.PdfToLatexResponse;
import org.example.dto.ProjectDTO;
import org.example.dto.ResponseResult;
import org.example.service.AIService;
import org.example.service.ProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.net.URL;
import java.net.URLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 项目管理控制器
 * 提供项目的创建和查询功能
 */
@RestController
@RequestMapping("/api/projects")
@CrossOrigin(origins = "*")
public class ProjectController {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private AIService aiService;

    @Value("${image.upload.path:./static/images}")
    private String imageUploadPath;

    @Value("${image.upload.allowed-types:image/jpeg,image/png,image/gif,image/webp}")
    private String imageAllowedTypes;

    /**
     * 创建项目
     * POST /api/projects
     */
    @PostMapping
    public ResponseResult<ProjectDTO> createProject(@RequestBody ProjectDTO projectDTO, HttpServletRequest request) {
        // 从请求中获取当前登录用户的ID（由JWT拦截器设置）
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseResult.error(401, "用户未登录");
        }
        // 设置用户ID，确保使用当前登录用户的ID
        projectDTO.setUserId(userId);
        
        ProjectDTO created = projectService.createProject(projectDTO);
        return ResponseResult.success("项目创建成功", created);
    }

    /**
     * 根据用户ID查询项目列表
     * GET /api/projects/user/{userId}
     */
    @GetMapping("/user/{userId}")
    public ResponseResult<List<ProjectDTO>> getProjectsByUser(@PathVariable Long userId, HttpServletRequest request) {
        // 从请求中获取当前登录用户的ID（由JWT拦截器设置）
        Long currentUserId = (Long) request.getAttribute("userId");
        if (currentUserId == null) {
            return ResponseResult.error(401, "用户未登录");
        }
        
        // 确保用户只能查询自己的项目列表
        if (!currentUserId.equals(userId)) {
            return ResponseResult.error(403, "没有权限访问该项目列表");
        }
        
        List<ProjectDTO> projects = projectService.getProjectsByUserId(userId);
        return ResponseResult.success("查询成功", projects);
    }
    
    /**
     * 根据项目ID查询项目
     * GET /api/projects/{id}
     */
    @GetMapping("/{id}")
    public ResponseResult<ProjectDTO> getProjectById(@PathVariable Long id, HttpServletRequest request) {
        // 从请求中获取当前登录用户的ID（由JWT拦截器设置）
        Long currentUserId = (Long) request.getAttribute("userId");
        if (currentUserId == null) {
            return ResponseResult.error(401, "用户未登录");
        }
        
        ProjectDTO project = projectService.getProjectById(id);
        if (project == null) {
            return ResponseResult.error(404, "项目不存在");
        }
        
        // 检查用户权限，确保用户只能访问自己创建的项目
        if (!project.getUserId().equals(currentUserId)) {
            return ResponseResult.error(403, "没有权限访问该项目");
        }
        
        return ResponseResult.success("查询成功", project);
    }
    
    /**
     * 更新项目内容
     * PUT /api/projects
     */
    @PutMapping
    public ResponseResult<ProjectDTO> updateProjectContent(@RequestBody ProjectDTO projectDTO, HttpServletRequest request) {
        // 从请求中获取当前登录用户的ID（由JWT拦截器设置）
        Long currentUserId = (Long) request.getAttribute("userId");
        if (currentUserId == null) {
            return ResponseResult.error(401, "用户未登录");
        }
        
        // 检查项目ID是否存在
        if (projectDTO.getId() == null) {
            return ResponseResult.error(400, "项目ID不能为空");
        }
        
        // 检查项目是否存在
        ProjectDTO existingProject = projectService.getProjectById(projectDTO.getId());
        if (existingProject == null) {
            return ResponseResult.error(404, "项目不存在");
        }
        
        // 检查用户权限，确保用户只能更新自己创建的项目
        if (!existingProject.getUserId().equals(currentUserId)) {
            return ResponseResult.error(403, "没有权限更新该项目");
        }
        
        // 更新项目内容
        projectDTO.setUserId(currentUserId); // 确保使用当前用户ID
        ProjectDTO updatedProject = projectService.updateProjectById(projectDTO);
        
        return ResponseResult.success("项目内容更新成功", updatedProject);
    }

    /**
     * 删除项目
     * DELETE /api/projects/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseResult<Void> deleteProject(@PathVariable Long id, HttpServletRequest request) {
        Long currentUserId = (Long) request.getAttribute("userId");
        if (currentUserId == null) {
            return ResponseResult.error(401, "用户未登录");
        }

        ProjectDTO project = projectService.getProjectById(id);
        if (project == null) {
            return ResponseResult.error(404, "项目不存在");
        }
        if (!project.getUserId().equals(currentUserId)) {
            return ResponseResult.error(403, "没有权限删除该项目");
        }

        projectService.deleteProjectById(id);
        return ResponseResult.success("项目删除成功", null);
    }

    /**
     * 上传项目图片资源（用于 LaTeX includegraphics）
     * POST /api/projects/{id}/images
     */
    @PostMapping("/{id}/images")
    public ResponseResult<String> uploadProjectImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        Long currentUserId = (Long) request.getAttribute("userId");
        if (currentUserId == null) {
            return ResponseResult.error(401, "用户未登录");
        }

        ProjectDTO project = projectService.getProjectById(id);
        if (project == null) {
            return ResponseResult.error(404, "项目不存在");
        }
        if (!project.getUserId().equals(currentUserId)) {
            return ResponseResult.error(403, "没有权限上传该项目图片");
        }
        if (file == null || file.isEmpty()) {
            return ResponseResult.error(400, "图片文件不能为空");
        }
        String contentType = file.getContentType();
        if (contentType == null || !("," + imageAllowedTypes + ",").contains("," + contentType + ",")) {
            return ResponseResult.error(400, "不支持的图片类型");
        }

        try {
            Path uploadDir = Paths.get(imageUploadPath).toAbsolutePath().normalize();
            Files.createDirectories(uploadDir);
            String original = file.getOriginalFilename();
            if (original == null || original.isBlank()) {
                original = "image.png";
            }
            String ext = original.contains(".") ? original.substring(original.lastIndexOf('.')) : ".png";
            String fileName = "project_" + id + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;
            Path target = uploadDir.resolve(fileName);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return ResponseResult.success("上传成功", "/api/images/" + fileName);
        } catch (Exception e) {
            return ResponseResult.error(500, "图片上传失败: " + e.getMessage());
        }
    }

    /**
     * 通过 URL 下载图片并保存为项目资源
     * POST /api/projects/{id}/images/from-url
     */
    @PostMapping("/{id}/images/from-url")
    public ResponseResult<String> uploadProjectImageFromUrl(
            @PathVariable Long id,
            @RequestParam("imageUrl") String imageUrl,
            HttpServletRequest request) {
        Long currentUserId = (Long) request.getAttribute("userId");
        if (currentUserId == null) {
            return ResponseResult.error(401, "用户未登录");
        }

        ProjectDTO project = projectService.getProjectById(id);
        if (project == null) {
            return ResponseResult.error(404, "项目不存在");
        }
        if (!project.getUserId().equals(currentUserId)) {
            return ResponseResult.error(403, "没有权限上传该项目图片");
        }
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            return ResponseResult.error(400, "图片URL不能为空");
        }

        String normalizedUrl = imageUrl.trim();
        if (!(normalizedUrl.startsWith("http://") || normalizedUrl.startsWith("https://"))) {
            return ResponseResult.error(400, "仅支持 http/https 图片URL");
        }

        try {
            URL url = URI.create(normalizedUrl).toURL();
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            connection.setRequestProperty("Accept", "image/*,*/*;q=0.8");

            String contentType = connection.getContentType();
            String normalizedType = contentType == null ? "" : contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
            String lowerUrl = normalizedUrl.toLowerCase(Locale.ROOT);
            String extByUrl = lowerUrl.contains(".jpg") || lowerUrl.contains(".jpeg") ? ".jpg"
                    : lowerUrl.contains(".png") ? ".png"
                    : lowerUrl.contains(".gif") ? ".gif"
                    : lowerUrl.contains(".webp") ? ".webp"
                    : lowerUrl.contains(".avif") ? ".avif"
                    : "";
            boolean typeAllowed = !normalizedType.isEmpty() && ("," + imageAllowedTypes + ",").contains("," + normalizedType + ",");
            boolean isAvif = extByUrl.equals(".avif") || normalizedType.equals("image/avif");
            if (isAvif) {
                return ResponseResult.error(400, "AVIF 图片暂不支持 LaTeX 编译，请使用 JPG/PNG/GIF/WEBP");
            }
            boolean extAllowed = extByUrl.equals(".jpg") || extByUrl.equals(".png") || extByUrl.equals(".gif") || extByUrl.equals(".webp");
            if (!typeAllowed && !extAllowed) {
                return ResponseResult.error(400, "URL资源不是受支持的图片类型");
            }

            Path uploadDir = Paths.get(imageUploadPath).toAbsolutePath().normalize();
            Files.createDirectories(uploadDir);
            String ext = switch (normalizedType) {
                case "image/jpeg" -> ".jpg";
                case "image/png" -> ".png";
                case "image/gif" -> ".gif";
                case "image/webp" -> ".webp";
                default -> extAllowed ? extByUrl : ".png";
            };
            String fileName = "project_" + id + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;
            Path target = uploadDir.resolve(fileName);

            try (InputStream in = connection.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return ResponseResult.success("上传成功", "/api/images/" + fileName);
        } catch (Exception e) {
            return ResponseResult.error(500, "URL图片下载失败: " + e.getMessage());
        }
    }
    
    /**
     * 编译项目为PDF
     * POST /api/projects/{id}/compile
     */
    @PostMapping("/{id}/compile")
    public ResponseResult<CompileResult> compileProject(
            @PathVariable Long id,
            @RequestBody(required = false) CompileRequest compileRequest,
            HttpServletRequest request) {
        // 从请求中获取当前登录用户的ID（由JWT拦截器设置）
        Long currentUserId = (Long) request.getAttribute("userId");
        if (currentUserId == null) {
            return ResponseResult.error(401, "用户未登录");
        }
        
        // 检查项目是否存在
        ProjectDTO project = projectService.getProjectById(id);
        if (project == null) {
            return ResponseResult.error(404, "项目不存在");
        }
        
        // 检查用户权限，确保用户只能编译自己创建的项目
        if (!project.getUserId().equals(currentUserId)) {
            return ResponseResult.error(403, "没有权限编译该项目");
        }
        
        // 获取编译器类型（默认为pdflatex）
        String compiler = compileRequest != null && compileRequest.getCompiler() != null 
            ? compileRequest.getCompiler() 
            : "pdflatex";
        
        try {
            CompileResult result = projectService.compileProject(id, compiler);
            // 统一返回成功，编译结果状态在 CompileResult 中体现
            String message = "SUCCESS".equals(result.getStatus()) ? "编译成功" : "编译完成";
            return ResponseResult.success(message, result);
        } catch (Exception e) {
            // 捕获异常时，创建错误结果
            CompileResult errorResult = new CompileResult();
            errorResult.setStatus("ERROR");
            errorResult.setErrorMessage("编译失败: " + e.getMessage());
            errorResult.setCreatedAt(LocalDateTime.now());
            return ResponseResult.success("编译失败", errorResult);
        }
    }

    /**
     * 导出项目为 Word
     * GET /api/projects/{id}/export-word
     */
    @GetMapping("/{id}/export-word")
    public ResponseEntity<?> exportWord(@PathVariable Long id, HttpServletRequest request) {
        Long currentUserId = (Long) request.getAttribute("userId");
        if (currentUserId == null) {
            return ResponseEntity.status(401).body("用户未登录");
        }

        ProjectDTO project = projectService.getProjectById(id);
        if (project == null) {
            return ResponseEntity.status(404).body("项目不存在");
        }
        if (!project.getUserId().equals(currentUserId)) {
            return ResponseEntity.status(403).body("没有权限导出该项目");
        }

        try {
            String outputPath = projectService.exportProjectToWord(id);
            Path file = Paths.get(outputPath);
            String fileName = (project.getName() == null || project.getName().trim().isEmpty() ? "project_" + id : project.getName().trim()) + ".docx";
            String encodedFileName = URLEncoder.encode(fileName, "UTF-8").replaceAll("\\+", "%20");

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .body(new InputStreamResource(new FileInputStream(file.toFile())));
        } catch (Exception e) {
            return ResponseEntity.status(500).body("导出 Word 失败: " + e.getMessage());
        }
    }

    /**
     * 导出项目为 LaTeX 源文件
     * GET /api/projects/{id}/export-latex
     */
    @GetMapping("/{id}/export-latex")
    public ResponseEntity<?> exportLatex(@PathVariable Long id, HttpServletRequest request) {
        Long currentUserId = (Long) request.getAttribute("userId");
        if (currentUserId == null) {
            return ResponseEntity.status(401).body("用户未登录");
        }

        ProjectDTO project = projectService.getProjectById(id);
        if (project == null) {
            return ResponseEntity.status(404).body("项目不存在");
        }
        if (!project.getUserId().equals(currentUserId)) {
            return ResponseEntity.status(403).body("没有权限导出该项目");
        }

        try {
            String fileName = (project.getName() == null || project.getName().trim().isEmpty() ? "project_" + id : project.getName().trim()) + ".tex";
            String encodedFileName = URLEncoder.encode(fileName, "UTF-8").replaceAll("\\+", "%20");
            String content = project.getContent() == null ? "" : project.getContent();
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                    .contentType(MediaType.parseMediaType("application/x-tex; charset=UTF-8"))
                    .body(bytes);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("导出 LaTeX 失败: " + e.getMessage());
        }
    }

    /**
     * 从PDF文件创建项目
     * POST /api/projects/from-pdf
     */
    @PostMapping("/from-pdf")
    public ResponseResult<ProjectDTO> createProjectFromPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String projectName,
            HttpServletRequest request) {
        
        // 从请求中获取当前登录用户的ID（由JWT拦截器设置）
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            return ResponseResult.error(401, "用户未登录");
        }

        // 验证文件
        if (file == null || file.isEmpty()) {
            return ResponseResult.error(400, "PDF文件不能为空");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals("application/pdf")) {
            return ResponseResult.error(400, "请上传PDF格式的文件");
        }

        // 验证项目名称
        if (projectName == null || projectName.trim().isEmpty()) {
            // 如果没有提供项目名称，使用文件名
            String fileName = file.getOriginalFilename();
            if (fileName != null && fileName.endsWith(".pdf")) {
                projectName = fileName.substring(0, fileName.length() - 4);
            } else {
                projectName = "未命名项目";
            }
        }

        try {
            // 调用AI服务将PDF转换为LaTeX
            PdfToLatexResponse pdfResponse = aiService.convertPdfToLatex(file);
            
            if (pdfResponse.getErrorMessage() != null && !pdfResponse.getErrorMessage().isEmpty()) {
                return ResponseResult.error(500, "PDF转换失败: " + pdfResponse.getErrorMessage());
            }

            String latexContent = pdfResponse.getLatexContent();
            if (latexContent == null || latexContent.trim().isEmpty()) {
                return ResponseResult.error(500, "PDF转换失败：未生成LaTeX内容");
            }

            // 创建项目
            ProjectDTO projectDTO = new ProjectDTO();
            projectDTO.setUserId(userId);
            projectDTO.setName(projectName.trim());
            projectDTO.setContent(latexContent);

            ProjectDTO created = projectService.createProject(projectDTO);
            return ResponseResult.success("项目创建成功", created);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseResult.error(500, "创建项目失败: " + e.getMessage());
        }
    }
}



