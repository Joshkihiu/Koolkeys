package com.koolkeys.app.session;

final class LiveTypingCursor {
    enum ActionType {
        CHARACTER,
        ENTER,
        TAB,
        BACKSPACE
    }

    static final class Action {
        final ActionType type;
        final char character;

        Action(ActionType type, char character) {
            this.type = type;
            this.character = character;
        }
    }

    private final String text;
    private int position;

    LiveTypingCursor(String text) {
        this.text = text == null ? "" : text;
    }

    boolean hasNext() {
        return position < text.length();
    }

    Action next() {
        Action action = peekNext();
        if (action != null) advance();
        return action;
    }

    Action peekNext() {
        if (!hasNext()) return null;
        char character = text.charAt(position);
        if (character == '\r') {
            return new Action(ActionType.ENTER, '\n');
        }
        if (character == '\n') return new Action(ActionType.ENTER, '\n');
        if (character == '\t') return new Action(ActionType.TAB, '\t');
        if (character == '\b') return new Action(ActionType.BACKSPACE, '\b');
        return new Action(ActionType.CHARACTER, character);
    }

    void advance() {
        if (!hasNext()) return;
        if (text.charAt(position) == '\r'
            && position + 1 < text.length()
            && text.charAt(position + 1) == '\n') {
            position += 2;
        } else {
            position++;
        }
    }

    int getPosition() {
        return position;
    }

    void reset() {
        position = 0;
    }

    void setPosition(int position) {
        this.position = Math.max(0, Math.min(text.length(), position));
    }

    int getTextLength() {
        return text.length();
    }
}
