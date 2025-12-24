package org.example.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.dto.ProjectDTO;
import org.example.dto.ResponseResult;
import org.example.service.ProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
    public ResponseResult<List<ProjectDTO>> getProjectsByUser(@PathVariable Long userId) {
        List<ProjectDTO> projects = projectService.getProjectsByUserId(userId);
        return ResponseResult.success("查询成功", projects);
    }
}

