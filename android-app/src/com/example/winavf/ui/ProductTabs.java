// SPDX-License-Identifier: LicenseRef-UAVF-1.0
package com.example.winavf.ui;

import android.content.Context;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.example.winavf.navigation.ProductScreen;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/** Keeps the three main pages attached while the pager slides between them. */
public final class ProductTabs {
    private static final ProductScreen[] SCREENS = {
            ProductScreen.MODE_SELECTION, ProductScreen.SYSTEMS, ProductScreen.APP_SETTINGS
    };
    private FrameLayout container;
    private DashboardScreen.Pager pager;
    private DashboardScreen.Navigation indicator;
    private int language = -1;
    private ProductAppearance.Color theme;

    public TextView show(FrameLayout parent, int previous, int selected, Consumer<ProductScreen> navigate,
                  Supplier<IntFunction<DashboardScreen>> pageFactory) {
        boolean reusable = container != null && container.getParent() == parent
                && language == UiLanguage.selected() && theme == ProductAppearance.theme(parent.getContext());
        if (!reusable) attach(parent, selected, navigate);
        else if (previous != selected && pager.page(selected) != null) {
            return select(selected, true);
        }

        IntFunction<DashboardScreen> createPage = pageFactory.get();
        if (reusable) pager.page(selected, createPage.apply(selected));
        else for (int tab = 0; tab < SCREENS.length; tab++) pager.page(tab, createPage.apply(tab));
        return select(selected, false);
    }

    private void attach(FrameLayout parent, int selected, Consumer<ProductScreen> navigate) {
        Context context = parent.getContext();
        IntConsumer select = index -> navigate.accept(SCREENS[index]);
        parent.removeAllViews();
        container = new FrameLayout(context);
        indicator = new DashboardScreen.Navigation(context, selected, select);
        pager = new DashboardScreen.Pager(context, selected, indicator, select);
        container.addView(pager, fill());
        container.addView(indicator, fill());
        parent.addView(container, fill());
        language = UiLanguage.selected();
        theme = ProductAppearance.theme(context);
    }

    private TextView select(int tab, boolean animate) {
        TextView notice = pager.page(tab).notice();
        indicator.select(tab);
        pager.show(tab, animate);
        return notice;
    }

    private static FrameLayout.LayoutParams fill() {
        return new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
    }
}
