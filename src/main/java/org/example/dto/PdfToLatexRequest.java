package org.example.dto;

import lombok.Data;

/**
 * PDF转LaTeX请求DTO
 */
@Data
public class PdfToLatexRequest {
    private String pdfContent; // PDF文件的Base64编码或文本内容
    private String fileName;   // PDF文件名
}

