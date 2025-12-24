package org.example.service.impl;

import org.example.dto.ProjectDTO;
import org.example.mapper.ProjectMapper;
import org.example.service.ProjectService;
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
}

