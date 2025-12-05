package org.example.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 模板数据传输对象
 */
@Data
public class TemplateDTO {
    private Long id;
    private String name;
    private String description;
    private String content;
    private String previewImage;
    private String category;
    private Boolean isSystem;
    private Integer usageCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

