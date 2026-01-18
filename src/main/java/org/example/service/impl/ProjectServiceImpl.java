package org.example.service.impl;

import org.example.dto.CompileResult;
import org.example.dto.ProjectDTO;
import org.example.mapper.ProjectMapper;
import org.example.service.ProjectService;
import org.example.util.LatexCompileUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 项目服务实现类
 */
@Service
@Transactional
public class ProjectServiceImpl implements ProjectService {

    @Autowired
    private ProjectMapper projectMapper;
    
    @Autowired
    private LatexCompileUtil latexCompileUtil;

    @Override
    public ProjectDTO createProject(ProjectDTO projectDTO) {
        // 设置创建时间和修改时间
        LocalDateTime now = LocalDateTime.now();
        projectDTO.setCreatedAt(now);
        projectDTO.setUpdatedAt(now);

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
        
        // 保留原有项目的重要字段
        projectDTO.setName(existingProject.getName());
        projectDTO.setUserId(existingProject.getUserId());
        projectDTO.setCreatedAt(existingProject.getCreatedAt());

        // 更新项目
        int result = projectMapper.updateById(projectDTO);
        if (result > 0) {
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
            return result;
        } catch (Exception e) {
            CompileResult result = new CompileResult();
            result.setStatus("ERROR");
            result.setErrorMessage("编译失败: " + e.getMessage());
            result.setCreatedAt(LocalDateTime.now());
            return result;
        }
    }
}

