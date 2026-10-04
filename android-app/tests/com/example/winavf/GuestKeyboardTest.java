package com.example.winavf;
import java.util.ArrayList;
import java.util.List;

public final class GuestKeyboardTest {
    public static void main(String[] args) {
        List<String> events = new ArrayList<>();
        GuestKeyboard keyboard = new GuestKeyboard((s, d) -> events.add(s + ":" + d));
        keyboard.handle(113, true, 0x3000, 0);
        keyboard.handle(31, true, 0x3000, 3);
        keyboard.handle(31, false, 0x3000, 3);
        keyboard.handle(113, false, 0, 0);
        check(events, "65507:true", "99:true", "99:false", "65507:false");
        events.clear();
        // No modifier down events: synthesize Ctrl+Alt+Shift from meta state.
        keyboard.handle(48, true, 0x1003, 'T');
        keyboard.releaseAll();
        check(events, "65507:true", "65513:true", "65505:true", "116:true",
            "116:false", "65507:false", "65513:false", "65505:false");
        events.clear();
        keyboard.handle(118, true, 0x50000, 0);
        keyboard.handle(21, true, 0x50000, 0);
        keyboard.handle(21, false, 0x50000, 0);
        keyboard.releaseAll();
        check(events, "65516:true", "65361:true", "65361:false", "65516:false");
        events.clear();
        keyboard.handle(8, true, 0x41, '!');
        keyboard.handle(59, false, 0, 0);
        keyboard.handle(8, false, 0, '1');
        check(events, "65505:true", "33:true", "65505:false", "33:false");
        events.clear();
        // Samsung Cmd uses Android META_LEFT, which Xorg names Super_L.
        keyboard.handle(117, true, 0x30000, 0);
        keyboard.handle(117, false, 0, 0);
        check(events, "65515:true", "65515:false");
        for (int[] pair : new int[][]{{113,65507},{114,65508},{57,65513},
                                     {58,65514},{59,65505},{60,65506},
                                     {117,65515},{118,65516}}) {
            events.clear();
            keyboard.handle(pair[0], true, 0, 0);
            keyboard.handle(pair[0], true, 0, 0); // repeat must not stick
            keyboard.handle(pair[0], false, 0, 0);
            check(events, pair[1]+":true", pair[1]+":false");
        }
        events.clear();
        keyboard.handle(4, true, 0, 0);
        keyboard.handle(4, false, 0, 0);
        check(events, "269025062:true", "269025062:false");
        if (GuestKeyboard.keysym(131, 0) != 65470 ||
            GuestKeyboard.keysym(142, 0) != 65481 ||
            GuestKeyboard.keysym(122, 0) != 65360) throw new AssertionError("Navigation/F keys");
        System.out.println("GUEST_KEYBOARD_STATE_TESTS=PASS");
    }
    private static void check(List<String> actual, String... expected) {
        if (!actual.equals(List.of(expected))) throw new AssertionError(actual);
    }
}
