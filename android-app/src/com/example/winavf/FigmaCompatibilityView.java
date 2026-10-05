// SPDX-License-Identifier: LicenseRef-UAVF-1.0
// Copyright (c) 2026 U-AVF project owner (GitHub: dt0-imsyu).
// See LICENSE-UAVF.txt and LICENSE_SCOPE.md; earlier/third-party rights preserved.
package com.example.winavf;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.widget.ProgressBar;

/** Figma294:41/80. Each result comes from the caller's actual probe evidence. */
final class FigmaCompatibilityView extends FigmaCanvas {
    FigmaCompatibilityView(Context context,boolean running,boolean passed,int completed,String[] results,
            String boot,String vsock,String graphics,Runnable repeat,Runnable proceed,Runnable back,Runnable share,Runnable skip) {
        super(context,"create-background.png",false,1460);
        header(running?"Checking this device":"Compatibility test",running?"Testing the capabilities needed to run U-AVF.":passed?"Launch prerequisites verified. Runtime checks are shown separately.":"Some requirements need attention.","← Back",back);
        if(running || !passed) {
            String[] labels={"Android / ARM64","Android Virtualization Framework","Custom VM permissions","Platform integrity"};
            for(int i=0;i<labels.length;i++) {
                int y=390+i*115;
                shape(420,y,1720,100,0xf5111a23,0xc72b4154,20);
                boolean active=running && i==completed;
                boolean ok=results[i].startsWith("Passed"),failed=results[i].startsWith("Failed");
                if(ok)asset("compat-dot.png",458,y+33,34,34);
                else shape(458,y+33,34,34,failed?0xffff8f82:0xff4a5866,0,17);
                text(labels[i],520,y,760,100,33,0xe6b8c4d0,false,Gravity.CENTER_VERTICAL);
                text(i<3?"System":"Integrity",1280,y,230,100,25,0xffa7b6c3,false,Gravity.CENTER_VERTICAL);
                text(active?"Checking…":results[i],1520,y,570,100,27,ok?0xff55eba0:failed?0xffff8f82:0xffa7b6c3,false,Gravity.CENTER_VERTICAL|Gravity.RIGHT);
                if(active) {
                    ProgressBar spinner=new ProgressBar(context);spinner.setIndeterminateTintList(ColorStateList.valueOf(0xff55eba0));widget(spinner,1455,y+30,40,40);
                }
            }
            ProgressBar progress=new ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal);
            progress.setIndeterminate(running);progress.setMax(labels.length);progress.setProgress(completed);
            progress.setIndeterminateTintList(ColorStateList.valueOf(0xff38d185));progress.setProgressTintList(ColorStateList.valueOf(0xff38d185));widget(progress,420,1120,1720,24);
            text(running?(completed<labels.length?"Checking: "+labels[Math.min(completed,labels.length-1)]:"Saving results…"):"Read the results above, then retry.",420,1180,1720,65,28,0xffa7b6c3,false,Gravity.CENTER_VERTICAL);
            if(!running) {
                button("Run again",1120,1290,970,100,30,0xff05140f,0xff38d185,0xff38d185,26,repeat);
                button("Skip test…",420,1290,650,100,28,0xffa7b6c3,0xfa15202b,0xe62a3b4d,26,skip);
            }
        } else {
            asset("setup-glow-depth.png",759,249,950,950);
            asset("compat-circle.png",500,440,410,410);
            text("✓",535,494,340,313,250,0xfff5fafd,true,Gravity.CENTER);
            text("Prerequisites ready",410,894,590,140,50,0xff6becaf,true,Gravity.CENTER);
            shape(1120,410,970,700,0xe60e1720,0xcc2b4052,38);
            text("LOCAL CHECKS COMPLETE",1190,465,760,45,24,0xff45e79c,true,Gravity.CENTER_VERTICAL);
            text("Your device results",1190,510,760,65,42,0xffeff5f9,true,Gravity.CENTER_VERTICAL);
            text("No boot or GPU success is inferred from file checks.",1190,575,760,70,27,0xffa7b6c3,false,Gravity.CENTER_VERTICAL);
            String[] names={"AVF / custom VM permissions","Guest boot","vsock communication","OpenGL renderer"};
            String[] values={"Verified",boot,vsock,graphics};
            for(int i=0;i<4;i++) {
                int y=665+i*100;shape(1190,y,800,86,0xf00f1720,0xe6293b4f,22);
                text(names[i],1225,y,370,86,26,0xfff4f7fa,false,Gravity.CENTER_VERTICAL);
                text(values[i],1605,y,350,86,23,values[i].startsWith("Verified")?0xff5ce8a5:0xffa7b6c3,true,Gravity.CENTER_VERTICAL|Gravity.RIGHT);
            }
            button("Continue",1120,1150,970,110,28,0xff05140f,0xf538d185,0xcc38d185,26,proceed);
            button("Run again",1120,1285,450,80,26,0xffa7b6c3,0xfa15202b,0xe62a3b4d,22,repeat);
            button("Share report",1600,1285,490,80,26,0xffa7b6c3,0xfa15202b,0xe62a3b4d,22,share);
        }
    }
}
