// SPDX-License-Identifier: LicenseRef-UAVF-1.0
// Copyright (c) 2026 U-AVF project owner (GitHub: dt0-imsyu).
// See LICENSE-UAVF.txt and LICENSE_SCOPE.md; earlier/third-party rights preserved.
package com.example.winavf;

import android.content.Context;
import android.view.Gravity;

/** Workspace home294:311, with actual state/configuration instead of prototype values. */
final class FigmaWorkspaceHome extends FigmaCanvas {
    FigmaWorkspaceHome(Context context,String state,String subtitle,String resources,String launchLabel,
            Runnable launch,Runnable dashboard,Runnable settings,Runnable diagnostics,Runnable recovery) {
        super(context,"home-background.png",true);
        header("Ubuntu 24.04 LTS",subtitle,"← Dashboard",dashboard);
        gradient(430,390,1700,360,0xe60f1720,0xe60f1720,0xbf293b4f,29);
        asset("ubuntu.png",500,465,200,208);
        text("Ubuntu 24.04 LTS",760,440,800,98,62,0xfaf4f7fa,true,Gravity.CENTER_VERTICAL);
        shape(756,555,300,64,0xfa18222d,0xbf2a3a4a,32);
        text(state,774,555,264,64,26,0xffa7b6c3,false,Gravity.CENTER);
        text(resources,760,623,1250,72,30,0xffabbacc,false,Gravity.CENTER_VERTICAL);
        button(launchLabel,1592,500,386,108,36,0xff05140f,0xf538d185,0xcc38d185,26,launch);
        tile(430,"Settings","Performance, display, input",settings,false);
        tile(1010,"Diagnostics","Compatibility and logs",diagnostics,false);
        tile(1590,"Install & recovery","Reinstall / Live / Setup status",recovery,true);
    }
    private void tile(int x,String title,String subtitle,Runnable action,boolean warning) {
        android.view.View card=shape(x,820,520,240,0xe60c121a,0xbf293b4f,24);card.setContentDescription(title);click(card,action);
        android.widget.TextView heading=text(title,x+60,858,410,64,warning?34:38,0xffb8c4d0,false,Gravity.CENTER_VERTICAL);click(heading,action);
        android.widget.TextView description=text(subtitle,x+60,926,390,80,warning?24:26,warning?0xfaffbc72:0xffa7b6c3,false,Gravity.TOP);click(description,action);
    }
}
