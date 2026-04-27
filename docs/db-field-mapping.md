# Database Field Mapping

This document records how backend DTO fields map to the current live database schema.

## Tables

- `user`
- `project`
- `template`
- `compile_task`
- `ai_log`
- `email_verification`
- `pdf_file`

## UserDTO -> user

- `UserDTO.id` -> `user.user_id`
- `UserDTO.username` -> `user.username`
- `UserDTO.email` -> `user.email`
- `UserDTO.password` -> `user.password_hash`
- `UserDTO.avatar` -> `user.avatar_url`
- `UserDTO.code` -> runtime-only verification code (not stored in `user`)

## ProjectDTO -> project

- `ProjectDTO.id` -> `project.project_id`
- `ProjectDTO.userId` -> `project.user_id`
- `ProjectDTO.name` -> `project.name`
- `ProjectDTO.content` -> `project.latex_content`
- `ProjectDTO.createdAt` -> `project.created_at`
- `ProjectDTO.updatedAt` -> `project.updated_at`

## TemplateDTO -> template

- `TemplateDTO.id` -> `template.template_id`
- `TemplateDTO.name` -> `template.name`
- `TemplateDTO.description` -> `template.description`
- `TemplateDTO.previewImage` -> `template.preview_url`
- `TemplateDTO.content` -> `template.template_path` (service may resolve it to LaTeX file content when possible)
- `TemplateDTO.createdAt` -> `template.created_at`

Compatibility fields currently synthesized in service layer (not persisted in `template`):

- `TemplateDTO.category`
- `TemplateDTO.isSystem`
- `TemplateDTO.usageCount`
- `TemplateDTO.updatedAt`

## CompileResult -> compile_task

- `CompileResult.id` -> `compile_task.task_id`
- `CompileResult.documentId` -> `compile_task.project_id`
- `CompileResult.status` -> `compile_task.status`
- `CompileResult.logContent` -> `compile_task.log`
- `CompileResult.compilerVersion` -> `compile_task.engine`
- `CompileResult.pdfPath` -> `compile_task.result_pdf_path`
- `CompileResult.createdAt` -> `compile_task.created_at`

Not present in `compile_task` and therefore not persisted directly:

- `CompileResult.errorMessage`
- `CompileResult.compileTimeMs`

