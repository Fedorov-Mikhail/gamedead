package com.gamedead.client.ui;

import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;

/** Keeps pasted credentials intact while retaining single-line Enter-to-submit behavior. */
final class ExactPasswordField extends TextField {
    ExactPasswordField(Skin skin) {
        super("", skin);
        setOnlyFontChars(false);
        // TextField otherwise silently discards CR/LF in both paste() and setText().
        writeEnters = true;
    }

    @Override protected InputListener createInputListener() {
        return new TextFieldClickListener() {
            @Override public boolean keyTyped(InputEvent event, char character) {
                // A physical Enter submits; only pasted/programmatic newlines belong to the password.
                writeEnters = false;
                try { return super.keyTyped(event, character); }
                finally { writeEnters = true; }
            }
        };
    }
}
