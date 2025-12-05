package org.example.dto;

import lombok.Data;

/**
 * 导入请求DTO
 */
@Data
public class ImportRequest {
    private Long userId;
    private String fileName;
    private String fileContent; // LaTeX文件内容
    private Boolean useAIOptimize; // 是否使用AI优化
}

