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
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileInputStream;
import java.net.URLEncoder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

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



