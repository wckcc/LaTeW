package org.example.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@code static/images} 下未被任何项目 {@code latex_content} 引用的上传图（{@code project_*_xxxxxxxx.ext}）清理报告。
 */
public class ImageOrphanCleanupReport {

    private final boolean dryRun;
    private final int referencedBasenameCount;
    private final int orphanFileCount;
    private final List<String> orphanFiles = new ArrayList<>();
    private final List<String> removedOrWouldRemove = new ArrayList<>();

    public ImageOrphanCleanupReport(boolean dryRun, int referencedBasenameCount, int orphanFileCount) {
        this.dryRun = dryRun;
        this.referencedBasenameCount = referencedBasenameCount;
        this.orphanFileCount = orphanFileCount;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public int getReferencedBasenameCount() {
        return referencedBasenameCount;
    }

    public int getOrphanFileCount() {
        return orphanFileCount;
    }

    public List<String> getOrphanFiles() {
        return Collections.unmodifiableList(orphanFiles);
    }

    public List<String> getRemovedOrWouldRemove() {
        return Collections.unmodifiableList(removedOrWouldRemove);
    }

    public void addOrphanFile(String path) {
        orphanFiles.add(path);
    }

    public void addRemovedOrWouldRemove(String path) {
        removedOrWouldRemove.add(path);
    }
}
