package org.example.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@code static/pdf} 下未被 {@code pdf_word_file} 引用的孤儿 PDF 清理报告。
 */
public class PdfOrphanCleanupReport {

    private final boolean dryRun;
    private final int trackedFileCount;
    private final int orphanFileCount;
    private final List<String> orphanFiles = new ArrayList<>();
    private final List<String> removedOrWouldRemove = new ArrayList<>();

    public PdfOrphanCleanupReport(boolean dryRun, int trackedFileCount, int orphanFileCount) {
        this.dryRun = dryRun;
        this.trackedFileCount = trackedFileCount;
        this.orphanFileCount = orphanFileCount;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public int getTrackedFileCount() {
        return trackedFileCount;
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
