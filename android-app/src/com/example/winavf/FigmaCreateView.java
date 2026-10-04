// SPDX-License-Identifier: LicenseRef-UAVF-1.0
// Copyright (c) 2026 U-AVF project owner (GitHub: dt0-imsyu).
// See LICENSE-UAVF.txt and LICENSE_SCOPE.md; earlier/third-party rights preserved.
package com.example.winavf;

import android.content.Context;
import android.view.Gravity;
import android.widget.TextView;

/** Create OS294:127 / Image294:162; retains real picker/download verification. */
final class FigmaCreateView extends FigmaCanvas {
    FigmaCreateView(Context context,boolean image,boolean verified,String selected,
            java.util.function.Consumer<String> choose,Runnable next,Runnable back,Runnable download,Runnable browse) {
        super(context,"create-background.png",false);
        header("Create workspace",image?"Choose where Ubuntu comes from.":"Choose the operating system.",image?"← Back":"← Dashboard",back);
        wizard(image?2:1);
        if(image) {
            gradient(430,500,1700,260,0xf7121e29,0xf50e1720,0xc72a4053,24);
            asset("ubuntu.png",500,540,150,156);
            text("Ubuntu Desktop 24.04.5 ARM64",700,524,900,84,50,0xfaf4f7fa,true,Gravity.CENTER_VERTICAL);
            text("Official Canonical image · Recommended",700,602,800,52,30,0xffb8c4d0,false,Gravity.CENTER_VERTICAL);
            shape(696,665,438,54,0xfa15372a,0xf23e8a62,27);
            text(verified?"✓ SHA256 verified":"SHA256 checked on import",714,665,402,54,24,0xff77edad,false,Gravity.CENTER);
            button("Ubuntu website ↗",1622,575,426,96,28,0xffe7eef5,0xfa15202b,0xe62a3b4d,24,download);
            gradient(430,820,1700,210,0xf7121e29,0xf50e1720,0xc72a4053,24);
            text("Choose local ISO",500,856,500,72,42,0xffb8c4d0,false,Gravity.CENTER_VERTICAL);
            text("After downloading Desktop ARM64, select the ISO here",500,923,970,65,28,0xffa7b6c3,false,Gravity.CENTER_VERTICAL);
            button("Browse files",1682,870,346,90,36,0xffe7eef5,0xfa15202b,0xe62a3b4d,24,browse);
            button("Back",412,1160,346,96,36,0xffe7eef5,0xfa15202b,0xe62a3b4d,24,back);
            TextView continueButton=button("Continue",1642,1160,506,96,36,0xff1a0c05,0xffff8a47,0xffff8a47,26,next);
            continueButton.setEnabled(verified);continueButton.setAlpha(verified?1:0.4f);
        } else {
            boolean windowsSelected="windows".equals(selected);
            gradient(430,500,780,420,windowsSelected?0xf7121e29:0xff24332b,0xf50e1720,windowsSelected?0xc72a4053:0xffff8a47,34);
            asset("ubuntu.png",500,575,170,178);text("Ubuntu",720,554,400,96,58,0xfaf4f7fa,true,Gravity.CENTER_VERTICAL);
            shape(716,660,258,54,0xfa15372a,0xf23e8a62,27);text("Recommended",734,660,222,54,24,0xff77edad,false,Gravity.CENTER);
            text("Official Ubuntu Desktop ARM64\nFull GNOME desktop",720,737,400,160,30,0xffb8c4d0,false,Gravity.TOP);
            gradient(1350,500,780,420,windowsSelected?0xff24332b:0xf7121e29,0xf50e1720,windowsSelected?0xffff8a47:0xc72a4053,34);
            asset("windows.png",1420,585,150,150);text("Windows ARM",1630,554,420,96,58,0xfaf4f7fa,true,Gravity.CENTER_VERTICAL);
            shape(1626,660,258,54,0xfa3a2a18,0xe69e6a2d,27);text("Experimental",1644,660,222,54,24,0xffffc174,false,Gravity.CENTER);
            text("Pre-alpha boot path\nNot recommended for daily use",1630,737,420,160,30,0xffb8c4d0,false,Gravity.TOP);
            text("✓ Selected",windowsSelected?1880:960,858,210,52,25,0xffff8a47,true,Gravity.CENTER);
            android.view.View ubuntuHit=shape(430,500,780,420,0,0,34);
            ubuntuHit.setSelected(!windowsSelected);ubuntuHit.setContentDescription("Ubuntu"+(!windowsSelected?", selected":""));
            click(ubuntuHit,()->choose.accept("ubuntu"));
            android.view.View windowsHit=shape(1350,500,780,420,0,0,34);
            windowsHit.setSelected(windowsSelected);windowsHit.setContentDescription("Windows ARM, experimental"+(windowsSelected?", selected":""));
            click(windowsHit,()->choose.accept("windows"));
            button("Continue",1642,1150,506,102,36,0xff1a0c05,0xffff8a47,0xffff8a47,26,next);
        }
    }
}
