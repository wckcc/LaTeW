package org.example.service;

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
}

