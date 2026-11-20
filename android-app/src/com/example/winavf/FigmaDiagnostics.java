// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf;

import com.example.winavf.diagnostics.DiagnosticJournal.Entry;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

/** Device overview and searchable journal from the final redesign. */
final class FigmaDiagnostics extends FigmaCanvas {
    private static final int SURFACE=0xff1c1d20;
    private static final int PRIMARY=0xfff5f5f7, SECONDARY=0xffa6a7af, ACCENT=0xff0a84ff;
    private static final int START=Gravity.START|Gravity.CENTER_VERTICAL;
    private static final int END=Gravity.END|Gravity.CENTER_VERTICAL;

    FigmaDiagnostics(Context context,String[] device,String[] guest,boolean running,
                     Runnable back,Runnable check,Runnable runs,Runnable log,Runnable export,Runnable resume) {
        super(context,true,1393);
        base("Проверки и диагностика",back);
        text("Устройство",160,460,1088,40,26,SECONDARY,false,START);
        shape(160,520,1088,480,SURFACE,0,32);
        String[] labels={"Android / ARM64","Android AVF","Разрешения Custom VM","Целостность платформы"};
        for(int i=0;i<labels.length;i++)row(160,520+i*120,labels[i],device[i],false);
        text("Последний запуск Ubuntu",1312,460,1088,40,26,SECONDARY,false,START);
        shape(1312,520,1088,360,SURFACE,0,32);
        row(1312,520,"Загрузка и vsock",guest[0],false);
        row(1312,640,"Графика",guest[1],false);
        row(1312,760,"Тестовые прогоны","Открыть",true);
        View open=new View(context);open.setContentDescription("Тестовые прогоны");
        widget(open,1312,760,1088,120);click(open,runs);
        shape(160,1240,492,96,ACCENT,0,48);
        asset("v12/63b38-svg.png",200,1268,40,40).setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        text("Проверить устройство",256,1240,380,96,32,PRIMARY,true,START);
        View checkTarget=new View(context);checkTarget.setContentDescription("Проверить устройство");
        widget(checkTarget,160,1240,492,96);click(checkTarget,check);
        button("Журнал",676,1240,200,96,32,PRIMARY,0,0,0,log);
        button("Экспорт отчёта",900,1240,340,96,32,PRIMARY,0,0,0,export);
        if(running)button("К рабочему столу",1930,1240,470,96,32,PRIMARY,SURFACE,0,48,resume);
    }

    FigmaDiagnostics(Context context,List<Entry> entries,Runnable back,
                     Consumer<String> copy,Runnable copyReport,Runnable export) {
        this(context,entries,null,back,copy,copyReport,export);
    }

    FigmaDiagnostics(Context context,Function<String,String> runSearch,Runnable back,
                     Consumer<String> copy,Runnable copyReport,Runnable export) {
        this(context,List.of(),runSearch,back,copy,copyReport,export);
    }

    private FigmaDiagnostics(Context context,List<Entry> entries,Function<String,String> runSearch,
                             Runnable back,Consumer<String> copy,Runnable copyReport,Runnable export) {
        super(context,true,1393);
        boolean runs=runSearch!=null;
        base(runs?"Тестовые прогоны":"Журнал",back);
        EditText query=new EditText(context);
        String searchLabel=runs?"Поиск по отчётам":"Поиск по событиям";
        query.setSingleLine();query.setHint(UiLanguage.text(searchLabel));
        query.setTextColor(PRIMARY);query.setHintTextColor(SECONDARY);query.setTextSize(15);
        query.setContentDescription(UiLanguage.text(searchLabel));
        widget(query,160,354,runs?2240:900,82);
        String[] components={"All components","AVF","Ubuntu","Windows","Check"};
        String[] levels={"All levels","Event","Error"};
        int[] selection={0,0};
        shape(160,460,2240,828,SURFACE,0,32);
        ScrollView scroll=new ScrollView(context);
        TextView output=new TextView(context);
        output.setTextColor(SECONDARY);output.setTextSize(15);output.setTextIsSelectable(true);
        scroll.addView(output,new ScrollView.LayoutParams(-1,-2));widget(scroll,208,508,2144,732);
        Runnable update=()->{
            String entered=query.getText().toString();
            String result=runs?runSearch.apply(entered):matchingEvents(entries,entered,components,levels,selection);
            output.setTag(result);
            output.setText(result.isEmpty()?UiLanguage.text(runs?"Нет подходящих отчётов":"Нет подходящих событий"):result);
        };
        query.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){update.run();}
            @Override public void afterTextChanged(Editable text) {}
        });
        if(!runs) {
            filter(components,selection,0,1108,update);
            filter(levels,selection,1,1770,update);
        }
        update.run();
        button("Копировать найденное",160,1312,530,84,30,PRIMARY,SURFACE,0,42,()->copy.accept((String)output.getTag()));
        button("Копировать отчёт",714,1312,460,84,30,PRIMARY,SURFACE,0,42,copyReport);
        button("Экспорт отчёта",1198,1312,430,84,30,PRIMARY,SURFACE,0,42,export);
    }

    private static String matchingEvents(List<Entry> entries,String query,String[] components,String[] levels,int[] selection) {
        String needle=query.strip().toLowerCase(Locale.ROOT);
        StringBuilder result=new StringBuilder();
        for(Entry entry:entries) {
            if(selection[0]>0&&!entry.component().equals(components[selection[0]]))continue;
            if(selection[1]>0&&!entry.level().equals(levels[selection[1]]))continue;
            String line=entry.line();
            if(line.toLowerCase(Locale.ROOT).contains(needle))result.append(line).append('\n');
        }
        return result.toString();
    }

    private void base(String title,Runnable back) {
        asset("v12/8b7c5-svg.png",168,83,26,34);
        text("Назад",220,56,380,88,32,ACCENT,false,START);
        View target=new View(getContext());target.setContentDescription("Назад");
        widget(target,160,56,450,88);click(target,back);
        text(title,160,224,2240,88,64,PRIMARY,true,START);
    }

    private void row(int x,int y,String title,String value,boolean arrow) {
        text(title,x+40,y,512,120,32,PRIMARY,false,START);
        text(value,x+520,y,arrow?470:528,120,30,SECONDARY,false,END);
        if(arrow)asset("v12/b9ac3-svg.png",x+1012,y+44,32,32);
    }

    private void filter(String[] options,int[] selection,int slot,int x,Runnable update) {
        TextView filter=button(options[0],x,354,630,82,30,PRIMARY,SURFACE,0,22,()->{});
        filter.setOnClickListener(view->FigmaDropdown.show(view,options,selection[slot],index->{
            selection[slot]=index;
            filter.setText(UiLanguage.text(options[index]));
            update.run();
        }));
    }
}
