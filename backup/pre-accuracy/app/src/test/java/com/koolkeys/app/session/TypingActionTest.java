package com.koolkeys.app.session;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class TypingActionTest {
    @Test
    public void preservesPythonCharactersNewlinesAndIndentation() {
        String source = "import secrets\nimport string\n\ndef generate_password(length):\n"
            + "    characters = string.ascii_letters + string.digits + string.punctuation";
        List<TypingAction> actions = TypingAction.build(source);

        StringBuilder rebuilt = new StringBuilder();
        for (TypingAction action : actions) {
            if (action.type == TypingAction.Type.ENTER) rebuilt.append('\n');
            else if (action.type == TypingAction.Type.TAB) rebuilt.append('\t');
            else rebuilt.append(action.character);
        }

        assertEquals(source, rebuilt.toString());
    }

    @Test
    public void collapsesCrLfToOneEnterButPreservesOffsets() {
        List<TypingAction> actions = TypingAction.build("a\r\nb");
        assertEquals(3, actions.size());
        assertEquals(TypingAction.Type.CHARACTER, actions.get(0).type);
        assertEquals(TypingAction.Type.ENTER, actions.get(1).type);
        assertEquals(TypingAction.Type.CHARACTER, actions.get(2).type);
        assertEquals(4, actions.get(2).positionAfter);
    }

    @Test
    public void preservesTabAndBackspaceCharactersAsDistinctActions() {
        List<TypingAction> actions = TypingAction.build("\t\b");
        assertEquals(TypingAction.Type.TAB, actions.get(0).type);
        assertEquals(TypingAction.Type.BACKSPACE, actions.get(1).type);
    }
}
