package com.example.winavf;

import java.util.LinkedHashMap;
import java.util.Map;

/** Stateful key down/up relay; Xorg interprets shortcuts and repeat. */
final class GuestKeyboard {
    interface Sink { void key(int keysym, boolean down); }
    private final Sink sink;
    private final Map<Integer, Integer> pressed = new LinkedHashMap<>();
    private final Map<Integer, Boolean> modifiers = new LinkedHashMap<>();
    private static final int[][] MODIFIERS = {
        {113, 0xffe3, 0x2000, 0x1000}, {114, 0xffe4, 0x4000, 0x1000},
        {57, 0xffe9, 0x10, 0x2}, {58, 0xffea, 0x20, 0x2},
        {59, 0xffe1, 0x40, 0x1}, {60, 0xffe2, 0x80, 0x1},
        {117, 0xffeb, 0x20000, 0x10000}, {118, 0xffec, 0x40000, 0x10000}
    };
    GuestKeyboard(Sink sink) { this.sink = sink; }
    static boolean isModifier(int code) {
        for (int[] modifier : MODIFIERS) if (modifier[0] == code) return true;
        return false;
    }
    private void modifier(int sym, boolean down) {
        if (Boolean.TRUE.equals(modifiers.get(sym)) != down) {
            sink.key(sym, down);
            if (down) modifiers.put(sym, true); else modifiers.remove(sym);
        }
    }
    boolean handle(int code, boolean down, int meta, int unicode) {
        for (int[] m : MODIFIERS) {
            if (m[0] == code) { modifier(m[1], down); return true; }
        }
        // IMEs/shortcut dispatchers can provide meta state without separate
        // modifier events. Reconcile before forwarding the ordinary key.
        for (int i = 0; i < MODIFIERS.length; i += 2) {
            int[] left = MODIFIERS[i], right = MODIFIERS[i + 1];
            boolean l = (meta & left[2]) != 0, r = (meta & right[2]) != 0;
            if (!l && !r && (meta & left[3]) != 0) l = true;
            modifier(left[1], l); modifier(right[1], r);
        }
        Integer held = pressed.get(code);
        if (!down) {
            if (held != null) { sink.key(held, false); pressed.remove(code); return true; }
            return keysym(code, unicode) != 0;
        }
        if (held != null) return true;
        int sym = keysym(code, unicode);
        if (sym == 0) return false;
        pressed.put(code, sym); sink.key(sym, true); return true;
    }
    void releaseAll() {
        for (int sym : pressed.values()) sink.key(sym, false);
        pressed.clear();
        for (int sym : modifiers.keySet()) sink.key(sym, false);
        modifiers.clear();
    }
    static int keysym(int code, int unicode) {
        if (code >= 29 && code <= 54) return 'a' + code - 29;
        if (code >= 131 && code <= 142) return 0xffbe + code - 131;
        if (code >= 144 && code <= 153) return 0xffb0 + code - 144;
        switch (code) {
            // Only physical keyboard Back is routed here by the Activity.
            // Android navigation Back remains an app navigation operation.
            case 4: return 0x1008ff26; // XF86Back
            case 19: return 0xff52; case 20: return 0xff54;
            case 21: return 0xff51; case 22: return 0xff53;
            case 61: return 0xff09; case 66: return 0xff0d;
            case 67: return 0xff08; case 111: return 0xff1b;
            case 112: return 0xffff; case 122: return 0xff50;
            case 123: return 0xff57; case 124: return 0xff63;
            case 92: return 0xff55; case 93: return 0xff56;
            case 115: return 0xffe5; case 116: return 0xff14;
            case 120: return 0xff61; case 121: return 0xff13;
            case 143: return 0xff7f; case 154: return 0xffaf;
            case 155: return 0xffaa; case 156: return 0xffad;
            case 157: return 0xffab; case 158: return 0xffae;
            case 160: return 0xff8d;
            default: return unicode > 0 && unicode < 0x110000
                ? (unicode <= 0xff ? unicode : 0x01000000 | unicode) : 0;
        }
    }
}
