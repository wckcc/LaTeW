package org.example.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ProjectBundlesCleanupReport {

    private final boolean dryRun;
    private final int referencedBundleDirCount;
    private final int orphanBundleDirCount;
    private final List<String> referencedBundleDirs = new ArrayList<>();
    private final List<String> orphanBundleDirs = new ArrayList<>();
    private final List<String> removedOrWouldRemove = new ArrayList<>();

    public ProjectBundlesCleanupReport(boolean dryRun, int referencedBundleDirCount, int orphanBundleDirCount) {
        this.dryRun = dryRun;
        this.referencedBundleDirCount = referencedBundleDirCount;
        this.orphanBundleDirCount = orphanBundleDirCount;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public int getReferencedBundleDirCount() {
        return referencedBundleDirCount;
    }

    public int getOrphanBundleDirCount() {
        return orphanBundleDirCount;
    }

    public List<String> getReferencedBundleDirs() {
        return Collections.unmodifiableList(referencedBundleDirs);
    }

    public List<String> getOrphanBundleDirs() {
        return Collections.unmodifiableList(orphanBundleDirs);
    }

    public List<String> getRemovedOrWouldRemove() {
        return Collections.unmodifiableList(removedOrWouldRemove);
    }

    public void addReferencedBundleDir(String path) {
        referencedBundleDirs.add(path);
    }

    public void addOrphanBundleDir(String path) {
        orphanBundleDirs.add(path);
    }

    public void addRemovedOrWouldRemove(String path) {
        removedOrWouldRemove.add(path);
    }
}
