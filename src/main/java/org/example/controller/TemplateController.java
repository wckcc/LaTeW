package org.example.controller;

import org.example.dto.ResponseResult;
import org.example.dto.TemplateDTO;
import org.example.dto.UserDTO;
import org.example.mapper.UserMapper;
import org.example.service.TemplateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * 模板管理控制器
 * 提供模板的查询和管理功能
 */
@RestController
@RequestMapping("/api/templates")
@CrossOrigin(origins = "*")
public class TemplateController {

    @Autowired
    private TemplateService templateService;

    @Autowired
    private UserMapper userMapper;

    /**
     * 获取所有模板
     * GET /api/templates
     */
    @GetMapping
    public ResponseResult<List<TemplateDTO>> getAllTemplates() {
        List<TemplateDTO> templates = templateService.getAllTemplates();
        return ResponseResult.success(templates);
    }

    /**
     * 根据ID获取模板
     * GET /api/templates/{id}
     */
    @GetMapping("/{id}")
    public ResponseResult<TemplateDTO> getTemplateById(@PathVariable Long id) {
        TemplateDTO template = templateService.getTemplateById(id);
        if (template == null) {
            return ResponseResult.error(404, "模板不存在");
        }
        return ResponseResult.success(template);
    }

    /**
     * 根据分类获取模板
     * GET /api/templates/category/{category}
     */
    @GetMapping("/category/{category}")
    public ResponseResult<List<TemplateDTO>> getTemplatesByCategory(@PathVariable String category) {
        List<TemplateDTO> templates = templateService.getTemplatesByCategory(category);
        return ResponseResult.success(templates);
    }

    /**
     * 获取系统模板
     * GET /api/templates/system
     */
    @GetMapping("/system")
    public ResponseResult<List<TemplateDTO>> getSystemTemplates() {
        List<TemplateDTO> templates = templateService.getSystemTemplates();
        return ResponseResult.success(templates);
    }

    /**
     * 创建模板（管理员功能）
     * POST /api/templates
     */
    @PostMapping
    public ResponseResult<TemplateDTO> createTemplate(@RequestBody TemplateDTO templateDTO, HttpServletRequest request) {
        try {
            Long currentUserId = (Long) request.getAttribute("userId");
            if (currentUserId == null) {
                return ResponseResult.error(401, "用户未登录");
            }

            UserDTO currentUser = userMapper.selectById(currentUserId);
            if (currentUser == null) {
                return ResponseResult.error(401, "用户不存在");
            }
            if (!"admin".equalsIgnoreCase(currentUser.getRole())) {
                return ResponseResult.error(403, "仅管理员可保存模板");
            }

            TemplateDTO created = templateService.createTemplate(templateDTO);
            return ResponseResult.success("模板创建成功", created);
        } catch (RuntimeException e) {
            return ResponseResult.error(500, e.getMessage());
        }
    }

    /**
     * 导入 zip 模板包（管理员功能）
     * POST /api/templates/import-zip
     */
    @PostMapping("/import-zip")
    public ResponseResult<Integer> importTemplatesFromZip(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam("description") String description,
            HttpServletRequest request) {
        try {
            Long currentUserId = (Long) request.getAttribute("userId");
            if (currentUserId == null) {
                return ResponseResult.error(401, "用户未登录");
            }

            UserDTO currentUser = userMapper.selectById(currentUserId);
            if (currentUser == null) {
                return ResponseResult.error(401, "用户不存在");
            }
            if (!"admin".equalsIgnoreCase(currentUser.getRole())) {
                return ResponseResult.error(403, "仅管理员可导入模板");
            }
            if (file == null || file.isEmpty()) {
                return ResponseResult.error(400, "zip 文件不能为空");
            }
            if (name == null || name.trim().isEmpty()) {
                return ResponseResult.error(400, "模板名称不能为空");
            }
            if (description == null || description.trim().isEmpty()) {
                return ResponseResult.error(400, "模板描述不能为空");
            }

            int count = templateService.importTemplatesFromZip(file, name, description);
            return ResponseResult.success("模板导入成功", count);
        } catch (RuntimeException e) {
            return ResponseResult.error(500, e.getMessage());
        }
    }

    /**
     * 更新模板（管理员功能）
     * PUT /api/templates/{id}
     */
    @PutMapping("/{id}")
    public ResponseResult<TemplateDTO> updateTemplate(@PathVariable Long id, @RequestBody TemplateDTO templateDTO) {
        TemplateDTO existing = templateService.getTemplateById(id);
        if (existing == null) {
            return ResponseResult.error(404, "模板不存在");
        }
        templateDTO.setId(id);
        TemplateDTO updated = templateService.updateTemplate(id, templateDTO);
        return ResponseResult.success("模板更新成功", updated);
    }

    /**
     * 删除模板（管理员功能）
     * DELETE /api/templates/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseResult<Void> deleteTemplate(@PathVariable Long id) {
        TemplateDTO existing = templateService.getTemplateById(id);
        if (existing == null) {
            return ResponseResult.error(404, "模板不存在");
        }
        templateService.deleteTemplate(id);
        return ResponseResult.success("模板删除成功", null);
    }
}

