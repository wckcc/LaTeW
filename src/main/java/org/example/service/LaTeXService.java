package org.example.service;

import org.example.dto.CompileRequest;
import org.example.dto.CompileResult;
import org.example.dto.ImportRequest;
import org.example.dto.DocumentDTO;

import java.io.File;

/**
 * LaTeX编译和导出服务接口
 */
public interface LaTeXService {
    /**
     * 编译LaTeX文档为PDF
     */
    CompileResult compileToPdf(CompileRequest request);

    /**
     * 导出为LaTeX格式
     */
    String exportToLaTeX(Long documentId);

    /**
     * 导出为Word格式
     */
    File exportToWord(Long documentId);

    /**
     * 导出为PDF格式
     */
    File exportToPdf(Long documentId);

    /**
     * 导入LaTeX文件
     */
    DocumentDTO importFromLaTeX(ImportRequest request);

    /**
     * 验证LaTeX语法
     */
    CompileResult validateLaTeX(String latexContent);

    /**
     * 获取编译日志
     */
    CompileResult getCompileLog(Long documentId);
}

