package org.example.dto;

import lombok.Data;

/**
 * 编译请求DTO
 */
@Data
public class CompileRequest {
    private Long documentId;
    private String compiler; // pdflatex, xelatex, lualatex
}

