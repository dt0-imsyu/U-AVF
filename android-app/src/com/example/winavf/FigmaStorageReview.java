// SPDX-License-Identifier: LicenseRef-UAVF-1.0
// Copyright (c) 2026 U-AVF project owner (GitHub: dt0-imsyu).
// See LICENSE-UAVF.txt and LICENSE_SCOPE.md; earlier/third-party rights preserved.
package com.example.winavf;

import android.content.Context;
import android.view.Gravity;

/** Storage294:200 and Review294:248, with supported configuration only. */
final class FigmaStorageReview extends FigmaCanvas {
    FigmaStorageReview(Context context,boolean review,String disk,String cpu,int ram,boolean verified,String display,
            String primary,Runnable next,Runnable back,Runnable settings,String rootSize,Runnable storageSettings) {
        super(context,"create-background.png",false);
        header(review?"Review workspace":"Create workspace",review?"Check your settings.":"Storage and performance", "← Back",back);
        wizard(review?4:3);
        if(review) {
            gradient(430,500,1700,560,0xf7121e29,0xf50e1720,0xc72a4053,38);
            row(545,"Operating system","Ubuntu 24.04.5 LTS",false);
            row(640,"Image",verified?"Official ARM64 ISO · verified":"Official ISO · missing / unverified",verified);
            row(735,"Storage","Internal storage · "+disk,false);
            row(830,"Performance",cpu+" · "+ram+" GiB RAM",false);
            row(925,"Display",display+" · Hardware graphics",false);
            button(primary,1492,1160,656,96,36,0xff1a0c05,0xffff8a47,0xffff8a47,26,next);
        } else {
            gradient(430,500,810,520,0xf7121e29,0xf50e1720,0xc72a4053,38);
            text("Storage",490,540,480,75,44,0xfaf4f7fa,false,Gravity.CENTER_VERTICAL);
            text("Location",490,614,300,50,28,0xffa7b6c3,false,Gravity.CENTER_VERTICAL);
            text("Internal storage / U-AVF",490,675,670,65,31,0xffb8c4d0,false,Gravity.CENTER_VERTICAL);
            text("Virtual disk",490,782,360,55,28,0xffa7b6c3,false,Gravity.CENTER_VERTICAL);
            text(disk,860,782,300,55,32,0xffb8c4d0,false,Gravity.RIGHT|Gravity.CENTER_VERTICAL);
            text("Ubuntu storage: "+rootSize,490,850,670,55,28,0xffb8c4d0,false,Gravity.CENTER_VERTICAL);
            if(storageSettings!=null)button("Choose size",490,920,330,66,27,0xffe7eef5,0xfa15202b,0xe62a3b4d,18,storageSettings);
            gradient(1320,500,810,520,0xf7121e29,0xf50e1720,0xc72a4053,38);
            text("Performance",1380,540,480,75,44,0xfaf4f7fa,false,Gravity.CENTER_VERTICAL);
            text(cpu,1380,675,360,60,32,0xffb8c4d0,false,Gravity.CENTER_VERTICAL);
            text(ram+" GiB RAM",1750,675,300,60,36,0xffb8c4d0,false,Gravity.CENTER_VERTICAL);
            text("Hardware graphics",1380,758,430,65,34,0xffb8c4d0,false,Gravity.CENTER_VERTICAL);
            shape(1831,760,228,54,0xff34e38b,0xbf2a3a4a,27);text("VirGL",1849,760,192,54,24,0xff05140f,false,Gravity.CENTER);
            text("Resolution",1380,832,600,52,28,0xffa7b6c3,false,Gravity.CENTER_VERTICAL);
            text(display,1380,884,660,52,32,0xffb8c4d0,false,Gravity.CENTER_VERTICAL);
            android.widget.TextView customize=text("Customize CPU / memory settings →",1380,946,640,58,25,0xffa7b6c3,false,Gravity.CENTER_VERTICAL);click(customize,settings);
            button("Review",1642,1160,506,96,36,0xffe7eef5,0xfa15202b,0xe62a3b4d,24,next);
        }
        button("Back",412,1160,346,96,36,0xffe7eef5,0xfa15202b,0xe62a3b4d,24,back);
    }
    private void row(int y,String key,String value,boolean green) {
        text(key,500,y,430,58,29,0xffb8c4d0,false,Gravity.CENTER_VERTICAL);
        text(value,1020,y,980,58,31,green?0xff55eba0:0xffb8c4d0,false,Gravity.CENTER_VERTICAL);
    }
}
