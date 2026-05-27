package org.example.dto;

import lombok.Data;

@Data
public class WorkspaceFilePayloadDTO {
    private String path;
    private String content;
}
