package org.example.service.impl;

import org.example.dto.CompileResult;
import org.example.dto.PdfWordFileDTO;
import org.example.dto.ProjectDTO;
import org.example.mapper.PdfWordFileMapper;
import org.example.mapper.ProjectMapper;
import org.example.service.ProjectService;
import org.example.util.LatexCompileUtil;
import org.example.util.WordExportUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    @Autowired
    private WordExportUtil wordExportUtil;

    @Autowired
    private PdfWordFileMapper pdfWordFileMapper;

    @Value("${latex.compile.output-dir:./static/pdf}")
    private String pdfOutputDir;

    private void recordFile(Long projectId, String filename, String absolutePath) {
        PdfWordFileDTO file = new PdfWordFileDTO();
        file.setProjectId(projectId);
        file.setFilename(filename);
        file.setFilePath(absolutePath);
        file.setUploadedAt(LocalDateTime.now());
        pdfWordFileMapper.insert(file);
    }

    private void trimBySuffix(Long projectId, String suffixPattern) {
        List<PdfWordFileDTO> files = pdfWordFileMapper.selectByProjectAndSuffix(projectId, suffixPattern);
        if (files == null || files.size() <= 10) {
            return;
        }
        for (int i = 10; i < files.size(); i++) {
            PdfWordFileDTO file = files.get(i);
            try {
                if (file.getFilePath() != null && !file.getFilePath().isBlank()) {
                    Files.deleteIfExists(Paths.get(file.getFilePath()));
                }
            } catch (Exception ignored) {
                // 删除物理文件失败时依然删除记录，避免无限膨胀
            }
            pdfWordFileMapper.deleteById(file.getId());
        }
    }

    private void enforceFileRetention(Long projectId) {
        trimBySuffix(projectId, "%.pdf");
        trimBySuffix(projectId, "%.docx");
    }

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
            if (result != null && result.getPdfPath() != null && !result.getPdfPath().isBlank()) {
                String pdfName = Paths.get(result.getPdfPath()).getFileName().toString();
                Path absolutePdf = Paths.get(pdfOutputDir).toAbsolutePath().normalize().resolve(pdfName);
                recordFile(projectId, pdfName, absolutePdf.toString());
            }
            enforceFileRetention(projectId);
            return result;
        } catch (Exception e) {
            CompileResult result = new CompileResult();
            result.setStatus("ERROR");
            result.setErrorMessage("编译失败: " + e.getMessage());
            result.setCreatedAt(LocalDateTime.now());
            return result;
        }
    }

    @Override
    public String exportProjectToWord(Long projectId) {
        ProjectDTO project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new RuntimeException("项目不存在");
        }
        if (project.getContent() == null || project.getContent().trim().isEmpty()) {
            throw new RuntimeException("项目内容为空，无法导出 Word");
        }
        try {
            Path outputPath = wordExportUtil.export(project.getContent(), projectId);
            String filename = outputPath.getFileName() == null ? ("project_" + projectId + ".docx") : outputPath.getFileName().toString();
            recordFile(projectId, filename, outputPath.toString());
            enforceFileRetention(projectId);
            return outputPath.toString();
        } catch (Exception e) {
            throw new RuntimeException("导出 Word 失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteProjectById(Long projectId) {
        ProjectDTO existing = projectMapper.selectById(projectId);
        if (existing == null) {
            throw new RuntimeException("项目不存在");
        }
        int result = projectMapper.deleteById(projectId);
        if (result <= 0) {
            throw new RuntimeException("删除项目失败");
        }
    }
}

