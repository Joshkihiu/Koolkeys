package com.koolkeys.app.session;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LiveTypingCursorTest {
    @Test
    public void handlesEmptyAndSingleCharacterDocuments() {
        LiveTypingCursor empty = new LiveTypingCursor("");
        assertFalse(empty.hasNext());
        assertNull(empty.next());
        assertEquals(0, empty.getPosition());

        assertEquals("x", reconstruct("x"));
    }

    @Test
    public void preservesCaseRepeatedCharactersAndPunctuation() {
        assertEquals("AaZZ!!??.,:;()[]{}",
            reconstruct("AaZZ!!??.,:;()[]{}"));
    }

    @Test
    public void preservesPythonTextAndIndentation() {
        String source = "import secrets\nimport string\n\ndef generate_password(length):\n"
            + "\tcharacters = string.ascii_letters + string.digits\n"
            + "    return ''.join(secrets.choice(characters) for _ in range(length))";
        assertEquals(source, reconstruct(source));
    }

    @Test
    public void treatsCrLfAsOneEnterActionAndPreservesLf() {
        LiveTypingCursor crlf = new LiveTypingCursor("a\r\nb");
        assertEquals('a', crlf.next().character);
        assertEquals(LiveTypingCursor.ActionType.ENTER, crlf.next().type);
        assertEquals(3, crlf.getPosition());
        assertEquals('b', crlf.next().character);
        assertEquals(4, crlf.getPosition());
        assertEquals("a\nb", reconstruct("a\nb"));
    }

    @Test
    public void preservesBlankLinesTabsAndBackspaces() {
        String source = "\tfirst\n\nsecond\b!";
        assertEquals(source, reconstruct(source));
    }

    @Test
    public void handlesDocumentsWithAtLeastFourHundredLines() {
        String source = String.join("\n", Collections.nCopies(400, "line"));
        assertEquals(source, reconstruct(source));
    }

    @Test
    public void peekDoesNotAdvanceAndBoundariesStayClamped() {
        LiveTypingCursor cursor = new LiveTypingCursor("ab");
        cursor.setPosition(-10);
        LiveTypingCursor.Action first = cursor.peekNext();
        assertEquals('a', first.character);
        assertEquals(0, cursor.getPosition());

        cursor.advance();
        assertEquals('b', cursor.next().character);
        cursor.setPosition(100);
        cursor.advance();
        assertFalse(cursor.hasNext());
        assertEquals(2, cursor.getPosition());
    }

    private String reconstruct(String source) {
        LiveTypingCursor cursor = new LiveTypingCursor(source);
        StringBuilder result = new StringBuilder();
        while (cursor.hasNext()) {
            LiveTypingCursor.Action action = cursor.next();
            if (action.type == LiveTypingCursor.ActionType.ENTER) result.append('\n');
            else if (action.type == LiveTypingCursor.ActionType.TAB) result.append('\t');
            else if (action.type == LiveTypingCursor.ActionType.BACKSPACE) result.append('\b');
            else result.append(action.character);
        }
        assertEquals(source.length(), cursor.getPosition());
        return result.toString();
    }
}
