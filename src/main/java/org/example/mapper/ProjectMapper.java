package org.example.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.example.dto.ProjectDTO;

import java.util.List;

/**
 * 项目Mapper接口
 */
@Mapper
public interface ProjectMapper {
    /**
     * 插入项目
     * @param projectDTO 项目数据
     * @return 影响行数
     */
    int insert(ProjectDTO projectDTO);

    /**
     * 根据用户ID查询项目列表
     * @param userId 用户ID
     * @return 项目列表
     */
    List<ProjectDTO> selectByUserId(Long userId);
    
    /**
     * 根据项目ID查询项目
     * @param id 项目ID
     * @return 项目信息
     */
    ProjectDTO selectById(Long id);

    /**
     * 根据项目ID更新项目
     * @param projectDTO 项目数据
     * @return 影响行数
     */
    int updateById(ProjectDTO projectDTO);

    /**
     * 根据项目ID删除项目
     * @param id 项目ID
     * @return 影响行数
     */
    int deleteById(Long id);
}

