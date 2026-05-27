package org.example.mapper;

import org.apache.ibatis.annotations.*;
import org.example.dto.PdfWordFileDTO;

import java.util.List;

/**
 * PDF/Word 文件记录 Mapper
 */
@Mapper
public interface PdfWordFileMapper {
    @Insert("insert into pdf_word_file (project_id, filename, file_path, uploaded_at) values (#{projectId}, #{filename}, #{filePath}, #{uploadedAt})")
    int insert(PdfWordFileDTO file);

    @Select("select pdf_id as id, project_id, filename, file_path, uploaded_at " +
            "from pdf_word_file " +
            "where project_id = #{projectId} and filename like #{suffixPattern} " +
            "order by uploaded_at desc, pdf_id desc")
    List<PdfWordFileDTO> selectByProjectAndSuffix(@Param("projectId") Long projectId, @Param("suffixPattern") String suffixPattern);

    @Select("select pdf_id as id, project_id, filename, file_path, uploaded_at " +
            "from pdf_word_file where project_id = #{projectId} " +
            "order by uploaded_at desc, pdf_id desc")
    List<PdfWordFileDTO> selectAllByProjectId(@Param("projectId") Long projectId);

    @Delete("delete from pdf_word_file where project_id = #{projectId}")
    int deleteByProjectId(@Param("projectId") Long projectId);

    @Delete("delete from pdf_word_file where pdf_id = #{id}")
    int deleteById(@Param("id") Long id);

    /**
     * 导出 PDF 在库中的物理路径（用于孤儿 PDF 清理）。
     */
    @Select("select file_path from pdf_word_file " +
            "where file_path is not null and file_path <> '' " +
            "and lower(filename) like '%.pdf'")
    List<String> selectAllTrackedPdfPaths();
}

