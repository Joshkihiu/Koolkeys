package com.koolkeys.app.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class TypingAction {
    enum Type {
        CHARACTER,
        ESCAPE,
        ENTER,
        TAB,
        BACKSPACE
    }

    final Type type;
    final char character;
    final int positionAfter;

    private TypingAction(Type type, char character, int positionAfter) {
        this.type = type;
        this.character = character;
        this.positionAfter = positionAfter;
    }

    static TypingAction escape(int positionAfter) {
        return new TypingAction(Type.ESCAPE, '\0', positionAfter);
    }

    static List<TypingAction> build(String text) {
        if (text == null || text.isEmpty()) return Collections.emptyList();
        ArrayList<TypingAction> actions = new ArrayList<>(text.length());
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '\r') {
                if (index + 1 < text.length() && text.charAt(index + 1) == '\n') index++;
                actions.add(new TypingAction(Type.ENTER, '\n', index + 1));
            } else if (character == '\n') {
                actions.add(new TypingAction(Type.ENTER, '\n', index + 1));
            } else if (character == '\t') {
                actions.add(new TypingAction(Type.TAB, '\t', index + 1));
            } else if (character == '\b') {
                actions.add(new TypingAction(Type.BACKSPACE, '\b', index + 1));
            } else {
                actions.add(new TypingAction(Type.CHARACTER, character, index + 1));
            }
        }
        return actions;
    }
}
