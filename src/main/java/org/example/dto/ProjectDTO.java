package org.example.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 项目数据传输对象
 */
@Data
public class ProjectDTO {
    /**
     * 项目ID
     */
    private Long id;

    /**
     * 项目名称
     */
    private String name;

    /**
     * 项目内容（LaTeX格式）
     */
    private String content;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 修改时间
     */
    private LocalDateTime updatedAt;
}

