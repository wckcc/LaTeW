package org.example.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ProjectWorkspaceInfoDTO {
    private boolean bundleMode;
    private String entryRelativePath;
    private List<String> files = new ArrayList<>();
}
