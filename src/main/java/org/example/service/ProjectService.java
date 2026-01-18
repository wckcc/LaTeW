package org.example.service;

import org.example.dto.CompileResult;
import org.example.dto.ProjectDTO;

import java.util.List;

/**
 * 项目服务接口
 */
public interface ProjectService {
    /**
     * 创建项目
     * @param projectDTO 项目数据
     * @return 创建的项目
     */
    ProjectDTO createProject(ProjectDTO projectDTO);

    /**
     * 根据用户ID查询项目列表
     * @param userId 用户ID
     * @return 项目列表
     */
    List<ProjectDTO> getProjectsByUserId(Long userId);
    
    /**
     * 根据项目ID查询项目
     * @param id 项目ID
     * @return 项目信息
     */
    ProjectDTO getProjectById(Long id);
    
    /**
     * 更新项目
     * @param projectDTO 项目数据（包含ID）
     * @return 更新后的项目信息
     */
    ProjectDTO updateProjectById(ProjectDTO projectDTO);
    
    /**
     * 编译项目内容为PDF
     * @param projectId 项目ID
     * @param compiler 编译器类型（pdflatex, xelatex, lualatex）
     * @return 编译结果
     */
    CompileResult compileProject(Long projectId, String compiler);
}

