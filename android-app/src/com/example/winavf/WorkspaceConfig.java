// SPDX-License-Identifier: LicenseRef-UAVF-1.0
// Copyright (c) 2026 U-AVF project owner (GitHub: dt0-imsyu).
// See LICENSE-UAVF.txt and LICENSE_SCOPE.md; earlier/third-party rights preserved.
package com.example.winavf;

/** Only configurations representable by the shipped AVF builders. */
final class WorkspaceConfig {
    final int ramGiB, width, height;
    final boolean oneCpu;
    WorkspaceConfig(int ramGiB, boolean oneCpu, String resolution) {
        if(ramGiB!=2 && ramGiB!=3 && ramGiB!=4 && ramGiB!=6)
            throw new IllegalArgumentException("Unsupported memory choice");
        switch(resolution) {
            case "1280x800": width=1280; height=800; break;
            case "1920x1080": width=1920; height=1080; break;
            case "1920x1200": width=1920; height=1200; break;
            default: throw new IllegalArgumentException("Unsupported display size");
        }
        this.ramGiB=ramGiB; this.oneCpu=oneCpu;
    }
    long memoryBytes() { return (long)ramGiB*1073741824L; }
    String topology() { return oneCpu?"CPU_TOPOLOGY_ONE_CPU":"CPU_TOPOLOGY_MATCH_HOST"; }
    String resolution() { return width+"x"+height; }
}
