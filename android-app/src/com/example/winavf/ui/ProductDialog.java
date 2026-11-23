// SPDX-License-Identifier: LicenseRef-UAVF-1.0
// Copyright (c) 2026 U-AVF project owner (GitHub: dt0-imsyu).
// See LICENSE-UAVF.txt and LICENSE_SCOPE.md; earlier/third-party rights preserved.
package com.example.winavf.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.Window;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import java.util.function.IntConsumer;
import java.util.function.Consumer;

/** Shared product dialogs. Dismissal never performs a destructive action. */
public final class ProductDialog {
    private static final int TEXT=0xfff5f5f7, MUTED=0xffa6a7af, ACCENT=0xff0a84ff;
    private static final int CARD=0xff27282c, SECONDARY=0xff34363b, OUTLINE=0xff3a3c42;
    public static Dialog show(Context context,String title,String message,String confirm,boolean destructive,Runnable action) {
        Panel panel=new Panel(context,title);panel.description(message);
        LinearLayout actions=panel.actions();
        panel.action(actions,"Отмена",false,false,panel.dialog::dismiss);
        panel.action(actions,confirm,true,destructive,()->{panel.dialog.dismiss();action.run();});
        panel.open();
        return panel.dialog;
    }
    public static void notice(Context context,String title,String message) {
        Panel panel=new Panel(context,title);panel.description(message);
        panel.action(panel.actions(),"Понятно",true,false,panel.dialog::dismiss);panel.open();
    }

    public static void about(Context context, PackageInfo build) {
        String version=build==null || build.versionName==null?"":build.versionName;
        var parts=java.util.regex.Pattern.compile("^(\\d+\\.\\d+)[.-]?(.*)$").matcher(version);
        boolean parsed=parts.matches();
        String series=parsed?parts.group(1):"—";
        String identifier=parsed?parts.group(2):version;
        Panel panel=new Panel(context,"О приложении");
        panel.info("Серия",series);
        panel.info("Версия","Release");
        panel.info("Snapshot",identifier.isEmpty()?"—":identifier);
        panel.action(panel.actions(),"Закрыть",false,false,panel.dialog::dismiss);
        panel.open(400);
    }

    public static void privacy(Context context) {
        notice(context,"Политика конфиденциальности",PrivacyPolicy.current());
    }

    public static void contact(Context context,Runnable diagnostics,Runnable issues) {
        Panel panel=new Panel(context,"Связь с разработчиком");
        panel.description("Если что-то не работает, создайте issue в репозитории U-AVF и приложите экспорт журнала диагностики.");
        panel.link("Экспорт журнала диагностики",()->{panel.dialog.dismiss();diagnostics.run();});
        LinearLayout actions=panel.actions();
        panel.action(actions,"Закрыть",false,false,panel.dialog::dismiss);
        panel.action(actions,"Открыть Issues",true,false,()->{panel.dialog.dismiss();issues.run();});
        panel.open(440);
    }

    public static void permissions(Context context,String commands) {
        Panel panel=new Panel(context,"Доступ к виртуализации");
        panel.description("Подключите устройство к компьютеру с ADB и включённой отладкой по USB. Выполните команды ниже, затем повторите проверку.");
        TextView code=new TextView(context);
        code.setText(commands);
        code.setTextColor(TEXT);
        code.setTextSize(13);
        code.setTypeface(Typeface.MONOSPACE);
        code.setTextIsSelectable(true);
        int pad=panel.dp(10);
        code.setPadding(pad,pad,pad,pad);
        code.setBackground(panel.background(SECONDARY,OUTLINE,12));
        var size=new LinearLayout.LayoutParams(-1,-2);
        size.topMargin=panel.dp(12);
        panel.content.addView(code,size);
        panel.action(panel.actions(),"Понятно",true,false,panel.dialog::dismiss);
        panel.open(465);
    }
    public static void menu(Context context,String title,String[] options,IntConsumer action) {
        Panel panel=new Panel(context,title);
        for(int i=0;i<options.length;i++) {
            final int index=i;
            Button row=panel.button(options[i],CARD,TEXT);
            row.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
            row.setElevation(0);
            row.setStateListAnimator(null);
            panel.content.addView(row,new LinearLayout.LayoutParams(-1,-2));
            row.setOnClickListener(view->{panel.dialog.dismiss();action.accept(index);});
        }
        panel.action(panel.actions(),"Закрыть",false,false,panel.dialog::dismiss);
        panel.open();
    }

    public static void rename(Context context,String current,Consumer<String> save) {
        Panel panel=new Panel(context,"Название воркспейса");
        EditText input=new EditText(context);
        input.setSingleLine(true);
        input.setTextColor(TEXT);
        input.setText(current);
        input.setSelectAllOnFocus(true);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(42)});
        input.setContentDescription(UiLanguage.text("Название воркспейса"));
        panel.content.addView(input,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout actions=panel.actions();
        panel.action(actions,"Отмена",false,false,panel.dialog::dismiss);
        panel.action(actions,"Сохранить",true,false,()->{
            String next=input.getText().toString().strip();
            if(!next.isEmpty())save.accept(next);
            panel.dialog.dismiss();
        });
        panel.open();
    }
    public static void choose(Context context,String title,String message,String[] options,int selected,IntConsumer action) {
        Panel panel=new Panel(context,title);panel.description(message);
        for(int i=0;i<options.length;i++) {
            final int index=i;
            Button row=panel.button(UiLanguage.text(options[i])+(i==selected?"  ✓":""),i==selected?SECONDARY:CARD,TEXT);
            row.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
            row.setBackground(panel.background(i==selected?SECONDARY:CARD,i==selected?ACCENT:OUTLINE,14));
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,-2);size.topMargin=panel.dp(10);
            panel.content.addView(row,size);row.setOnClickListener(v->{panel.dialog.dismiss();action.accept(index);});
        }
        panel.action(panel.actions(),"Отмена",false,false,panel.dialog::dismiss);panel.open();
    }

    public static void choosePrimary(Context context, String title, String message,
                                     String[] labels, int primaryIndex, boolean decisionIcons, IntConsumer action) {
        Panel panel=new Panel(context,title);
        panel.description(message);
        for (int i=0;i<labels.length;i++) {
            int index=i;
            boolean primary=i==primaryIndex;
            Button button=panel.button(labels[i],primary?ACCENT:SECONDARY,primary?0xffffffff:TEXT);
            if (decisionIcons) {
                button.setCompoundDrawablesRelativeWithIntrinsicBounds(new ChoiceIcon(context, i==0),null,null,null);
                button.setCompoundDrawablePadding(panel.dp(12));
            }
            button.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
            var size=new LinearLayout.LayoutParams(-1,-2);
            size.topMargin=panel.dp(10);
            panel.content.addView(button,size);
            button.setOnClickListener(view->{panel.dialog.dismiss();action.accept(index);});
        }
        panel.action(panel.actions(),"Отмена",false,false,panel.dialog::dismiss);
        panel.open();
    }

    /** Two-action confirmation positioned next to the control that opened it. */
    public static PopupWindow confirmFrom(View anchor, View card, String title, Runnable stop) {
        Panel panel=new Panel(anchor.getContext(),title);
        panel.card.removeView(panel.scroll);
        int padding=panel.dp(12);
        panel.card.setPadding(padding,padding,padding,padding);
        panel.card.setBackground(panel.background(CARD,OUTLINE,16));
        panel.heading.setTextSize(14);
        panel.heading.setIncludeFontPadding(false);

        Rect visible=new Rect();
        anchor.getWindowVisibleDisplayFrame(visible);
        int width=Math.min(panel.dp(256),visible.width()-2*padding);
        int[] position=new int[2];
        card.getLocationOnScreen(position);
        int cardRight=position[0]+card.getWidth();
        boolean fitsBesideCard=cardRight+padding+width<=visible.right-padding;
        int[] anchorPosition=new int[2];
        anchor.getLocationOnScreen(anchorPosition);
        int left=fitsBesideCard ? cardRight+padding : Math.max(visible.left+padding,cardRight-width);
        int top=fitsBesideCard ? anchorPosition[1] : position[1]+Math.round(card.getHeight()*card.getScaleY())+padding;

        PopupWindow popup=new PopupWindow(panel.card,width,-2,true);
        popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(panel.dp(8));
        var actions=panel.actions();
        ((LinearLayout.LayoutParams) actions.getLayoutParams()).topMargin=panel.dp(10);
        panel.action(actions,"Отмена",false,false,popup::dismiss);
        panel.action(actions,"Остановить",true,true,()->{popup.dismiss();stop.run();});
        for(int i=0;i<actions.getChildCount();i++) {
            Button button=(Button)actions.getChildAt(i);
            button.setTextSize(13);
            button.setMinHeight(panel.dp(44));
            button.setPadding(panel.dp(4),panel.dp(6),panel.dp(4),panel.dp(6));
        }
        panel.card.setAlpha(0f);
        panel.card.setTranslationX(-panel.dp(18));
        popup.showAsDropDown(anchor,left-anchorPosition[0],top-anchorPosition[1]-anchor.getHeight(),Gravity.LEFT);
        panel.card.animate().alpha(1f).translationX(0f).setDuration(200)
                .setInterpolator(new DecelerateInterpolator()).start();
        return popup;
    }

    private static final class ChoiceIcon extends android.graphics.drawable.Drawable {
        private final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final int size;
        private final boolean check;

        ChoiceIcon(Context context, boolean check) {
            this.check=check;
            size=Math.round(22*context.getResources().getDisplayMetrics().density);
            paint.setColor(TEXT);
            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            paint.setStrokeWidth(1.6f);
        }

        @Override public int getIntrinsicWidth(){return size;}
        @Override public int getIntrinsicHeight(){return size;}
        @Override public void draw(android.graphics.Canvas canvas) {
            canvas.save();
            canvas.translate(getBounds().left,getBounds().top);
            canvas.scale(getBounds().width()/22f,getBounds().height()/22f);
            canvas.drawCircle(11,11,9,paint);
            if (check) {
                canvas.drawLine(7,11,10,14,paint);
                canvas.drawLine(10,14,15,8,paint);
            } else {
                canvas.drawLine(8,8,14,14,paint);
                canvas.drawLine(14,8,8,14,paint);
            }
            canvas.restore();
        }
        @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}
        @Override public void setColorFilter(android.graphics.ColorFilter filter){paint.setColorFilter(filter);}
        @Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
    }
    private static final class Panel {
        final Context context;final Dialog dialog;final LinearLayout card,content;final Typeface font;
        final TextView heading;
        final ScrollView scroll;
        Panel(Context context,String title) {
            this.context=context;font=Typeface.create("sans-serif",Typeface.NORMAL);
            dialog=new Dialog(context);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            card=new LinearLayout(context);card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(24),dp(24),dp(24),dp(24));
            card.setBackground(background(CARD,OUTLINE,24));card.setElevation(dp(16));
            heading=new TextView(context);heading.setText(UiLanguage.text(title));heading.setTextSize(23);
            heading.setTextColor(TEXT);heading.setTypeface(Typeface.create(font,600,false));card.addView(heading);
            content=new LinearLayout(context);content.setOrientation(LinearLayout.VERTICAL);
            scroll=new ScrollView(context) {
                @Override protected void onMeasure(int width,int height) {
                    int maximum=(int)(context.getResources().getDisplayMetrics().heightPixels*0.55f);
                    super.onMeasure(width,View.MeasureSpec.makeMeasureSpec(maximum,View.MeasureSpec.AT_MOST));
                }
            };
            scroll.setFillViewport(false);scroll.setVerticalScrollBarEnabled(false);scroll.addView(content);
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,-2);size.topMargin=dp(16);card.addView(scroll,size);
        }
        int dp(int value) {return Math.round(value*context.getResources().getDisplayMetrics().density);}
        GradientDrawable background(int fill,int stroke,int radius) {
            GradientDrawable shape=new GradientDrawable();shape.setColor(ProductAppearance.tint(context,fill));shape.setCornerRadius(dp(radius));
            shape.setStroke(dp(1),ProductAppearance.tint(context,stroke));return shape;
        }
        void description(String message) {
            if(message==null||message.isEmpty())return;
            TextView body=new TextView(context);body.setText(UiLanguage.text(message));body.setTextSize(15);
            body.setTextColor(MUTED);body.setTypeface(font);body.setLineSpacing(dp(3),1);
            content.addView(body,new LinearLayout.LayoutParams(-1,-2));
        }

        void info(String label,String value) {
            LinearLayout block=new LinearLayout(context);
            block.setOrientation(LinearLayout.VERTICAL);
            block.setPadding(dp(14),dp(10),dp(14),dp(10));
            block.setBackground(background(SECONDARY,OUTLINE,14));
            TextView caption=new TextView(context);
            caption.setText(UiLanguage.text(label));
            caption.setTextSize(13);
            caption.setTextColor(MUTED);
            block.addView(caption);
            TextView detail=new TextView(context);
            detail.setText(value);
            detail.setTextSize(16);
            detail.setTextColor(TEXT);
            detail.setTypeface(Typeface.create(font,600,false));
            block.addView(detail);
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,-2);
            if(content.getChildCount()>0)size.topMargin=dp(8);
            content.addView(block,size);
        }

        void link(String label,Runnable activate) {
            int accent=ProductAppearance.accent(context);
            TextView link=new TextView(context);
            link.setText(UiLanguage.text(label));
            link.setTextColor(accent);
            link.setTextSize(15);
            link.setGravity(Gravity.CENTER_VERTICAL);
            link.setMinHeight(dp(44));
            link.setTypeface(Typeface.create(font,600,false));
            var icon=new StrokeIcon(StrokeIcon.Kind.EXTERNAL,accent);
            icon.setBounds(0,0,dp(18),dp(18));
            link.setCompoundDrawablesRelative(null,null,icon,null);
            link.setCompoundDrawablePadding(dp(8));
            link.setFocusable(true);
            ScreenLayout.pressFeedback(link);
            link.setOnClickListener(view->activate.run());
            content.addView(link,new LinearLayout.LayoutParams(-2,-2));
        }
        Button button(String label,int fill,int color) {
            Button button=new Button(context);button.setText(UiLanguage.text(label));button.setAllCaps(false);
            button.setTypeface(font);button.setTextSize(15);button.setTextColor(color);
            button.setMinHeight(dp(52));button.setMinimumWidth(0);button.setMinWidth(0);
            button.setPadding(dp(18),dp(10),dp(18),dp(10));button.setBackground(background(fill,fill,14));
            button.setStateListAnimator(null);
            ScreenLayout.pressFeedback(button);
            return button;
        }
        LinearLayout actions() {
            LinearLayout buttons=new LinearLayout(context);buttons.setGravity(Gravity.END);
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,-2);size.topMargin=dp(24);card.addView(buttons,size);return buttons;
        }
        void action(LinearLayout buttons,String label,boolean primary,boolean destructive,Runnable action) {
            int fill=primary?(destructive?0xff522b2b:ACCENT):SECONDARY;
            Button button=button(label,fill,destructive?0xffffb3a5:TEXT);
            button.setBackground(background(fill,fill,28));
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,-2,1);
            if(buttons.getChildCount()>0)size.leftMargin=dp(12);
            buttons.addView(button,size);button.setOnClickListener(v->action.run());
        }
        void open() { open(560); }

        void open(int maxWidth) {
            dialog.setContentView(card);dialog.setCanceledOnTouchOutside(true);dialog.show();
            Window window=dialog.getWindow();
            if(window!=null) {
                window.setBackgroundDrawableResource(android.R.color.transparent);
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                WindowManager.LayoutParams params=window.getAttributes();params.dimAmount=0.62f;window.setAttributes(params);
                window.setLayout(Math.max(dp(200),Math.min(context.getResources().getDisplayMetrics().widthPixels-dp(32),dp(maxWidth))),-2);
            }
            PageMotion.showPanel(card);
        }
    }
}
