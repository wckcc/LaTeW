package org.example.service;

import org.example.dto.CompileResult;
import org.example.dto.ProjectDTO;
import org.example.dto.ProjectWorkspaceInfoDTO;
import org.example.dto.WorkspaceFilePayloadDTO;
import org.springframework.web.multipart.MultipartFile;

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

    /**
     * 导出项目内容为 Word 文档
     * @param projectId 项目ID
     * @return 导出的 Word 文件绝对路径
     */
    String exportProjectToWord(Long projectId);

    /**
     * 将项目打成 zip 字节数组（多文件项目为解压目录全量；单文件为仅含 main.tex）。
     */
    byte[] exportProjectZipArchive(Long projectId);

    /**
     * 删除项目
     * @param projectId 项目ID
     */
    void deleteProjectById(Long projectId);

    /**
     * 从 zip 包创建项目（解压方式与模板 zip 导入一致，主入口优先 main.tex / document.tex）
     */
    ProjectDTO importProjectFromZip(MultipartFile zipFile, String projectName, Long userId);

    ProjectWorkspaceInfoDTO getProjectWorkspaceInfo(Long projectId);

    WorkspaceFilePayloadDTO readProjectWorkspaceFile(Long projectId, String relativePath);

    void writeProjectWorkspaceFile(Long projectId, WorkspaceFilePayloadDTO payload);
}

