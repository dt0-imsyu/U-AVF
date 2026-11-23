// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf.ui;

import android.content.Context;
import android.animation.ValueAnimator;
import android.graphics.Outline;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewOutlineProvider;
import android.view.HapticFeedbackConstants;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;
import android.widget.FrameLayout;
import java.util.function.IntConsumer;
import java.util.function.Consumer;

/** The Home, Systems and Settings pages. Runtime actions belong to the activity. */
public final class DashboardScreen extends ScreenLayout {
    public static final int HOME = 0, SYSTEMS = 1, SETTINGS = 2;
    private static final int PRIMARY = 0xfff5f5f7, SECONDARY = 0xffa6a7af;
    private static final int SURFACE = 0xff1c1d20, LINE = 0xff3a3c42, ACCENT = 0xff0a84ff;
    private static final int START = Gravity.START | Gravity.CENTER_VERTICAL;

    public record Workspace(String title, String subtitle, String status, String ram, String disk,
                     String display, String recent, boolean windows, Runnable open) {}

    public DashboardScreen(Context context, int tab, boolean firstHome, boolean hasUbuntu,
                  Workspace ubuntu, Workspace windows, String storage, String launchLabel,
                  Runnable launch, Runnable create, Runnable ubuntuCreate, Runnable windowsCreate, Runnable settings, Runnable disks,
                  Runnable liveUbuntu, boolean liveRunning,
                  Runnable diagnostics,
                  boolean verify, Runnable changeVerify, Runnable about, Runnable contact, Runnable privacy,
                  IntConsumer language, Runnable refreshAppearance) {
        super(context);
        String[] titles={"Главная","Системы","Настройки"};
        label(titles[tab],112,208,1100,80,64,PRIMARY,true);
        if(tab==HOME) {
            if(firstHome) firstHome(hasUbuntu,windows,ubuntuCreate,windowsCreate);
            else if (hasUbuntu) home(ubuntu,windows,storage,launchLabel,launch,settings,disks,diagnostics);
            else home(windows,null,windows.disk(),"Открыть воркспейс",windows.open(),
                    windows.open(),windows.open(),diagnostics);
        } else if(tab==SYSTEMS) {
            systems(ubuntu,windows,hasUbuntu,create,liveUbuntu,liveRunning);
        } else {
            appSettings(diagnostics,verify,changeVerify,about,contact,privacy,language,refreshAppearance);
        }
    }

    static final class Navigation extends ScreenLayout {
        private final View indicator;
        private final View[] targets=new View[3], activeIcons=new View[3], inactiveIcons=new View[3];
        private final TextView[] labels=new TextView[3];
        private float position;
        private int selected=-1;

        Navigation(Context context,int initial,IntConsumer select) {
            super(context, Surface.OVERLAY);
            brandIcon(112,74,52);
            text("U-AVF",184,60,220,80,36,PRIMARY,true,START);
            shape(744,1432,1072,128,0xf2282b31,OUTLINE_STROKE,64);
            indicator=shape(760,1448,340,96,0xff34363b,0,48);
            indicator.addOnLayoutChangeListener((view,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom)
                    ->position(position));
            String[] titles={"Главная","Системы","Настройки"};
            String[] on={"f14ab","2b047","334f1"},off={"5c010","317c5","a3639"};
            for(int i=0;i<titles.length;i++) {
                final int index=i;
                int left=760+348*i;
                inactiveIcons[i]=asset("v12/"+off[i]+"-svg.png",left+78,1476,40,40);
                activeIcons[i]=asset("v12/"+on[i]+"-svg.png",left+78,1476,40,40);
                labels[i]=text(titles[i],left+136,1448,202,96,30,SECONDARY,true,START);
                View target=new View(context);
                target.setContentDescription(titles[i]);
                widget(target,left,1448,340,96);
                targets[i]=target;
                click(target,()->{
                    if(index==selected)return;
                    target.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    select.accept(index);
                });
                roundedPress(target,48,96);
            }
            select(initial);
            position(initial);
        }

        void select(int index) {
            if(index==selected)return;
            selected=index;
            for(int i=0;i<targets.length;i++) {
                boolean active=i==index;
                targets[i].setSelected(active);
                activeIcons[i].setVisibility(active?VISIBLE:INVISIBLE);
                inactiveIcons[i].setVisibility(active?INVISIBLE:VISIBLE);
                labels[i].setTextColor(active?PRIMARY:SECONDARY);
            }
        }

        private void position(float position) {
            this.position=position;
            indicator.setTranslationX(position*348*indicator.getWidth()/340f);
        }

        void follow(float offset) { position(offset); }

    }

    /** Full-width pages share one viewport for both finger and tab navigation. */
    static final class Pager extends FrameLayout {
        private final DashboardScreen[] pages=new DashboardScreen[3];
        private final IntConsumer select;
        private final Navigation navigation;
        private final int slop;
        private int selected, startScroll;
        private float downX,downY;
        private boolean dragging;
        private ValueAnimator movement;

        Pager(Context context,int selected,Navigation navigation,IntConsumer select) {
            super(context);
            this.selected=selected;
            this.navigation=navigation;
            this.select=select;
            slop=ViewConfiguration.get(context).getScaledTouchSlop();
        }

        void page(int index,DashboardScreen page) {
            if(pages[index]!=null)removeView(pages[index]);
            pages[index]=page;
            page.setOnTouchListener((view,event)->true);
            addView(page,new LayoutParams(-1,-1));
        }

        DashboardScreen page(int index) {return pages[index];}

        void show(int index,boolean animate) {
            selected=index;
            for(int i=0;i<pages.length;i++)if(pages[i]!=null)pages[i].setImportantForAccessibility(
                    i==index?IMPORTANT_FOR_ACCESSIBILITY_AUTO:IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            if(movement!=null)movement.cancel();
            int target=index*getWidth();
            if(!animate || target==getScrollX() || !ValueAnimator.areAnimatorsEnabled()) {
                scrollTo(target,0);
                return;
            }
            movement=ValueAnimator.ofInt(getScrollX(),target);
            movement.setDuration(260);
            movement.setInterpolator(new DecelerateInterpolator(1.5f));
            movement.addUpdateListener(value->scrollTo((int)value.getAnimatedValue(),0));
            movement.start();
        }

        @Override protected void onSizeChanged(int w,int h,int oldW,int oldH) {
            super.onSizeChanged(w,h,oldW,oldH);
            if(movement!=null)movement.cancel();
            scrollTo(selected*w,0);
        }

        @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom) {
            for(int i=0;i<pages.length;i++)if(pages[i]!=null)
                pages[i].layout(i*getWidth(),0,(i+1)*getWidth(),getHeight());
        }

        @Override protected void onScrollChanged(int x,int y,int oldX,int oldY) {
            super.onScrollChanged(x,y,oldX,oldY);
            if(getWidth()>0)navigation.follow(x/(float)getWidth());
        }

        @Override public boolean onInterceptTouchEvent(MotionEvent event) {
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN) {
                downX=event.getX();
                downY=event.getY();
                dragging=false;
            } else if(event.getActionMasked()==MotionEvent.ACTION_MOVE &&
                    Math.abs(event.getX()-downX)>slop && Math.abs(event.getX()-downX)>Math.abs(event.getY()-downY)) {
                if(movement!=null)movement.cancel();
                startScroll=getScrollX();
                dragging=true;
                return true;
            }
            return super.onInterceptTouchEvent(event);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if(!dragging)return super.onTouchEvent(event);
            float delta=downX-event.getX();
            switch(event.getActionMasked()) {
                case MotionEvent.ACTION_MOVE -> scrollTo(Math.max(0,Math.min(2*getWidth(),Math.round(startScroll+delta))),0);
                case MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL -> {
                    dragging=false;
                    int next=selected;
                    if(event.getActionMasked()==MotionEvent.ACTION_UP && Math.abs(delta)>=getWidth()*.16f)
                        next=Math.max(0,Math.min(2,selected+(delta>0?1:-1)));
                    if(next==selected)show(selected,true);
                    else select.accept(next);
                }
            }
            return true;
        }

        @Override protected void onDetachedFromWindow() {
            if(movement!=null)movement.cancel();
            super.onDetachedFromWindow();
        }
    }

    private void home(Workspace ubuntu,Workspace windows,String storage,String launchLabel,Runnable launch,
                      Runnable settings,Runnable disks,Runnable diagnostics) {
        panel(112,352,1504,876);
        preview(ubuntu.windows()?"windows-workspace.webp":"ubuntu-workspace.webp",113,353,1502,632,40,true);
        icon(ubuntu.windows()?"b8c15":"a23a7",160,1055,96,96);
        label(ubuntu.title(),316,1038,850,68,46,PRIMARY,true);
        label(ubuntu.subtitle(),316,1113,850,47,28,SECONDARY,false);
        action(launchLabel,"910d2",1216,1058,352,96,launch);
        panel(112,1264,1504,128);
        value(ubuntu.ram(),"Память",160);value(ubuntu.disk(),"Диск",586);value(ubuntu.display(),"Дисплей",1013);
        shape(1480,1284,88,88,0xff27282c,0,44);
        icon("f79ec",1503,1307,42,42);
        hit(ubuntu.windows()?"Открыть воркспейс":"Ресурсы Ubuntu",1480,1284,88,88,settings);

        if(windows!=null) {
            panel(1664,352,784,436);
            preview("windows-workspace.webp",1665,353,782,292,40,true);
            icon("b8c15",1704,686,64,64);
            label(windows.title(),1792,666,496,56,34,PRIMARY,true);
            label(windows.subtitle(),1792,724,500,36,24,SECONDARY,false);
            icon("915f1",2318,700,34,34);
            hit(windows.title(),1664,352,784,436,windows.open());
        } else {
            panel(1664,352,784,436);
            icon("2cba8",1704,404,64,64);
            label("Проверки и диагностика",1704,508,696,68,40,PRIMARY,true);
            label("Состояние устройства и последнего запуска",1704,581,696,82,28,SECONDARY,false);
            label("Открыть диагностику",1704,682,626,56,28,ACCENT,true);
            icon("915f1",2318,692,34,34);
            hit("Открыть диагностику",1664,352,784,436,diagnostics);
        }

        panel(1664,828,784,312);
        label("Недавние сессии",1704,858,700,52,34,PRIMARY,true);
        if(ubuntu.recent().isEmpty() && (windows==null || windows.recent().isEmpty())) {
            label("История появится после запуска",1704,930,704,52,28,SECONDARY,false);
        } else {
            int top=930;
            if(!ubuntu.recent().isEmpty()){recent(ubuntu,top);top+=80;}
            if(windows!=null && !windows.recent().isEmpty())recent(windows,top);
        }
        panel(1664,1180,784,212);
        icon("8c047",1704,1258,56,56);
        label("Образы и диски",1788,1229,520,52,32,PRIMARY,true);
        label(storage,1788,1285,520,48,26,SECONDARY,false);
        icon("d76bf",2340,1270,32,32);
        hit("Образы и диски",1664,1180,784,212,disks);
    }

    private void recent(Workspace workspace,int top) {
        label(workspace.title(),1704,top,382,52,28,PRIMARY,false);
        TextView time=label(workspace.recent(),2086,top,322,52,24,SECONDARY,false);
        time.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
    }

    private void value(String value,String name,int left) {
        label(value,left,1279,350,48,36,PRIMARY,true);
        label(name,left,1330,350,36,24,SECONDARY,false);
    }

    private void firstHome(boolean ubuntuPending,Workspace windows,Runnable ubuntu,Runnable windowsCreate) {
        label(windows!=null||ubuntuPending?"Воркспейсы":"Создать воркспейс",112,338,1000,74,54,PRIMARY,true);
        firstCard("Ubuntu",ubuntuPending?"Продолжить настройку":"24.04 LTS ARM64",
                "ubuntu-workspace.webp","d6129",ubuntuPending?"bd3c2":"2209b",112,
                ubuntuPending?"Продолжить настройку":"Создать Ubuntu",ubuntu);
        firstCard(windows==null?"Windows":windows.title(),
                windows==null?"Экспериментальный запуск Windows":windows.subtitle(),
                "windows-workspace.webp","efef3",windows==null?"c7402":"bd3c2",1304,
                windows==null?"Экспериментальный запуск Windows":windows.title(),
                windows==null?windowsCreate:windows.open());
    }

    private void firstCard(String title,String subtitle,String photo,String os,String glyph,int left,String action,Runnable open) {
        panel(left,472,1144,824);
        preview(photo,left+1,473,1142,615,40,true);
        icon(os,left+48,1144,96,96);
        label(title,left+176,1136,740,60,46,PRIMARY,true);
        label(subtitle,left+176,1204,740,36,30,SECONDARY,false);
        icon(glyph,left+948,1170,44,44);
        hit(action,left,472,1144,824,open);
    }

    private void systems(Workspace ubuntu,Workspace windows,boolean hasUbuntu,Runnable create,Runnable liveUbuntu,boolean liveRunning) {
        action("Создать воркспейс","b73ab",2008,208,440,96,create);
        panel(112,368,2336,656);
        label("Воркспейсы",160,376,175,92,28,PRIMARY,true);
        label(Integer.toString((hasUbuntu?1:0)+(windows==null?0:1)),340,376,500,92,28,SECONDARY,false);
        if(hasUbuntu) systemRow(ubuntu,472);
        if(windows!=null)systemRow(windows,hasUbuntu?736:472);
        else if(!hasUbuntu)label("Здесь появятся созданные воркспейсы",160,492,1000,70,32,SECONDARY,false);
        panel(112,1060,2336,144);
        icon("d6129",160,1104,56,56);
        label(liveRunning?"Вернуться в Ubuntu Live":"Временная Live-сессия",248,1078,2000,50,36,PRIMARY,true);
        label(liveRunning?"Работает":"Запуск Ubuntu с ISO без установки",248,1130,2000,44,28,SECONDARY,false);
        icon("bd3c2",2334,1112,40,40);
        hit("Временная Live-сессия",112,1060,2336,144,liveUbuntu);
    }

    private void systemRow(Workspace workspace,int top) {
        preview(workspace.windows()?"windows-workspace.webp":"ubuntu-workspace.webp",160,top+32,328,192,24,false);
        label(workspace.title(),532,top+66,876,50,40,PRIMARY,true);
        label(workspace.subtitle(),532,top+130,876,32,26,SECONDARY,false);
        icon("23d66",1452,top+89,10,10);
        label(workspace.status(),1474,top+70,506,48,24,SECONDARY,false);
        if(!workspace.windows())label(workspace.ram()+" ОЗУ · "+workspace.disk(),1452,top+130,528,32,26,SECONDARY,false);
        icon("bd3c2",2048,top+108,40,40);
        hit(workspace.title(),160,top,2240,256,workspace.open());
    }

    private void appSettings(Runnable diagnostics,
                             boolean verify,Runnable changeVerify,Runnable about,Runnable contact,Runnable privacy,
                             IntConsumer language,Runnable refreshAppearance) {
        panel(112,368,1144,384);panel(1304,368,1144,384);
        row(112,368,"Проверки и тесты",StrokeIcon.Kind.CHECKLIST,null,true,diagnostics);
        row(112,496,"О приложении",StrokeIcon.Kind.INFO,null,true,about);
        row(112,624,"Связь с разработчиком",StrokeIcon.Kind.MESSAGE,null,true,contact);
        row(1304,368,"Проверять образ перед запуском",StrokeIcon.Kind.FILE_CHECK,verify,false,changeVerify);
        row(1304,496,"Приватность",StrokeIcon.Kind.LOCK,null,true,privacy);
        icon("f79ec",1344,667,42,42);
        label("Язык",1410,624,600,128,32,PRIMARY,false);
        label(UiLanguage.NAMES[UiLanguage.selected()],2030,624,300,128,30,SECONDARY,false);
        icon("6c14a",2378,673,30,30);
        View languageRow=hit("Язык",1304,624,1144,128,()->{});
        languageRow.setOnClickListener(view->DropdownMenu.show(view,UiLanguage.NAMES,UiLanguage.selected(),language));
        appearanceChoices(112,784,"Цветовая тема",false,ProductAppearance.theme(getContext()),color->{
            ProductAppearance.setTheme(getContext(),color);
            // Let the selection ring settle before rebuilding the themed pages.
            removeCallbacks(refreshAppearance);
            postDelayed(refreshAppearance,ValueAnimator.areAnimatorsEnabled()?180:0);
        });
        appearanceChoices(1304,784,"Иконка приложения",true,ProductAppearance.launcherIcon(getContext()),
                color->ProductAppearance.setLauncherIcon(getContext(),color));
    }

    private void appearanceChoices(int column,int top,String title,boolean icons,ProductAppearance.Color initial,
                                   Consumer<ProductAppearance.Color> select) {
        panel(column,top,1144,248);
        label(title,column+48,top+20,1048,52,34,PRIMARY,true);
        ProductAppearance.Color[] colors=ProductAppearance.Color.values();
        View[] rings=new View[colors.length];
        View[] targets=new View[colors.length];
        for(int i=0;i<colors.length;i++) {
            var color=colors[i];
            int left=column+50+i*358, center=left+164;
            int radius=icons?25:52;
            rings[i]=shape(center-53,top+80,106,106,0,0,0);
            var ring=new android.graphics.drawable.GradientDrawable();
            ring.setColor(android.graphics.Color.TRANSPARENT);
            ring.setCornerRadius(radius*getResources().getDisplayMetrics().density/2f);
            ring.setStroke(Math.max(1,Math.round(2*getResources().getDisplayMetrics().density)),PRIMARY);
            if(!icons) ring.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            rings[i].setBackground(ring);
            if(icons) asset(ProductAppearance.iconAsset(color),center-42,top+91,84,84)
                    .setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            else {
                var swatch=new View(getContext());
                var fill=new android.graphics.drawable.GradientDrawable();
                fill.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                fill.setColor(color.accent);
                swatch.setBackground(fill);
                widget(swatch,center-38,top+95,76,76);
            }
            text(color.label,left,top+190,328,40,26,SECONDARY,false,Gravity.CENTER);
            int index=i;
            View target=hit(UiLanguage.text(title)+": "+UiLanguage.text(color.label),left,top+76,328,156,()->{
                if(targets[index].isSelected()) return;
                try { select.accept(color); }
                catch(IllegalArgumentException | SecurityException failure) {
                    android.util.Log.w("UAVF-Appearance","Launcher icon change failed",failure);
                    ProductDialog.notice(getContext(),"Иконка приложения","Не удалось изменить иконку приложения.");
                    return;
                }
                for(int choice=0;choice<colors.length;choice++) {
                    targets[choice].setSelected(choice==index);
                    showSelectionRing(rings[choice],choice==index,true);
                }
                targets[index].performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            });
            roundedPress(target,30,156);
            targets[i]=target;
            target.setSelected(initial==color);
            showSelectionRing(rings[i],initial==color,false);
        }
    }

    private static void showSelectionRing(View ring,boolean selected,boolean animate) {
        float alpha=selected?1f:0f, scale=selected?1f:.83f;
        ring.animate().cancel();
        if(animate && ValueAnimator.areAnimatorsEnabled()) {
            ring.animate().alpha(alpha).scaleX(scale).scaleY(scale).setDuration(170)
                    .setInterpolator(new DecelerateInterpolator()).start();
        } else {
            ring.setAlpha(alpha);
            ring.setScaleX(scale);
            ring.setScaleY(scale);
        }
    }

    private void row(int left,int top,String title,StrokeIcon.Kind glyph,Boolean on,boolean chevron,Runnable click) {
        var image=new android.widget.ImageView(getContext());
        image.setImageDrawable(new StrokeIcon(glyph,SECONDARY));
        image.setScaleType(android.widget.ImageView.ScaleType.FIT_XY);
        image.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        widget(image,left+40,top+43,42,42);
        label(title,left+106,top,828,128,32,PRIMARY,false);
        if(chevron)icon("6c14a",left+1074,top+49,30,30);
        ToggleView toggle=on==null?null:new ToggleView(getContext(),on);
        if(toggle!=null)widget(toggle,left+(chevron?958:1012),top+36,92,56);
        View target=hit(title,left,top,1144,128,click);
        if(toggle!=null) {
            target.setSelected(on);
            target.setStateDescription(UiLanguage.text(on?"Включено":"Выключено"));
            target.setOnClickListener(view->{
                boolean next=!view.isSelected();
                click.run();
                view.setSelected(next);
                view.setStateDescription(UiLanguage.text(next?"Включено":"Выключено"));
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                toggle.checked(next);
            });
        }
    }

    private void panel(int x,int y,int w,int h) { shape(x,y,w,h,SURFACE,LINE,40); }
    private void preview(String name,int x,int y,int w,int h,int corner,boolean topOnly) {
        var image=asset("v12/"+name,x,y,w,h);
        image.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        image.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view,Outline outline) {
                float radius=corner*view.getWidth()/(float)w;
                int bottom=view.getHeight()+(topOnly?Math.round(radius):0);
                outline.setRoundRect(0,0,view.getWidth(),bottom,radius);
            }
        });
        image.setClipToOutline(true);
    }
    private void icon(String name,int x,int y,int w,int h) {
        asset("v12/"+name+"-svg.png",x,y,w,h).setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
    }
    private TextView label(String s,int x,int y,int w,int h,int font,int color,boolean bold) {
        return text(s,x,y,w,h,font,color,bold,START);
    }
    private void action(String title,String glyph,int x,int y,int w,int h,Runnable click) {
        shape(x,y,w,h,ACCENT,0,48);
        icon(glyph,x+52,y+28,40,40);
        label(title,x+109,y,w-130,h,32,PRIMARY,true);
        hit(title,x,y,w,h,click);
    }
    private View hit(String title,int x,int y,int w,int h,Runnable action) {
        View target=new View(getContext());
        target.setContentDescription(title);
        widget(target,x,y,w,h);
        click(target,action);
        return target;
    }
}
