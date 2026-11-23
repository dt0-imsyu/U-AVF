// SPDX-License-Identifier: LicenseRef-UAVF-1.0
// Copyright (c) 2026 U-AVF project owner (GitHub: dt0-imsyu).
package com.example.winavf.input;

import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.text.InputType;
import java.util.function.IntConsumer;

/** Routes Android keyboard commits to the existing guest key channel. */
public final class GuestIme extends View {
    private final IntConsumer key;

    public GuestIme(Context context, IntConsumer key) {
        super(context);
        this.key = key;
        setFocusableInTouchMode(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void show() {
        requestFocus();
        post(() -> {
            if (hasFocus()) getContext().getSystemService(InputMethodManager.class)
                    .showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    public void dismiss() {
        if (!hasFocus()) return;
        getContext().getSystemService(InputMethodManager.class)
                .hideSoftInputFromWindow(getWindowToken(), 0);
        clearFocus();
    }

    @Override public boolean onCheckIsTextEditor() { return true; }

    @Override public InputConnection onCreateInputConnection(EditorInfo info) {
        info.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        info.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                | EditorInfo.IME_ACTION_NONE;
        return new BaseInputConnection(this, true) {
            @Override public boolean commitText(CharSequence value, int cursor) {
                value.toString().codePoints().forEach(code -> key.accept(
                        code == '\n' ? 0xff0d : GuestKeyboard.keysym(KeyEvent.KEYCODE_UNKNOWN, code)));
                getEditable().clear();
                return true;
            }

            @Override public boolean deleteSurroundingText(int before, int after) {
                for (int i = 0; i < before; i++) key.accept(0xff08);
                for (int i = 0; i < after; i++) key.accept(0xffff);
                return true;
            }

            @Override public boolean deleteSurroundingTextInCodePoints(int before, int after) {
                return deleteSurroundingText(before, after);
            }

            @Override public boolean performEditorAction(int action) {
                key.accept(0xff0d);
                return true;
            }
        };
    }
}
