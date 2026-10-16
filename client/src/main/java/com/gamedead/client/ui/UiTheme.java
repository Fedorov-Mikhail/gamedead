package com.gamedead.client.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.*;
import com.badlogic.gdx.utils.Disposable;

final class UiTheme implements Disposable {
    static final Color TEXT = Color.valueOf("e9e4d8");
    static final Color MUTED = Color.valueOf("a9adb9");
    static final Color GOLD = Color.valueOf("c3a466");
    static final Color ERROR = Color.valueOf("ffa99d");
    static final String CHARACTERS = FreeTypeFontGenerator.DEFAULT_CHARS
            + "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдеёжзийклмнопрстуфхцчшщъыьэюя"
            + "«»—–…•№";
    final Skin skin = new Skin();

    UiTheme() {
        var generator = new FreeTypeFontGenerator(Gdx.files.internal("fonts/NotoSans.ttf"));
        try {
            font(generator, "default-font", 21);
            font(generator, "small", 16);
            font(generator, "heading", 32);
            font(generator, "title", 52);
            font(generator, "world-font", 9);
        } finally { generator.dispose(); }
        skin.add("default", new Label.LabelStyle(skin.getFont("default-font"), TEXT));
        skin.add("small", new Label.LabelStyle(skin.getFont("small"), MUTED));
        skin.add("heading", new Label.LabelStyle(skin.getFont("heading"), TEXT));
        skin.add("title", new Label.LabelStyle(skin.getFont("title"), TEXT));
        skin.add("panel", box("panel-texture", "151c2bf2", "6b5c40ff", 2), Drawable.class);
        var field = new TextField.TextFieldStyle();
        field.font = skin.getFont("default-font");
        field.fontColor = TEXT;
        field.disabledFontColor = MUTED;
        field.background = box("field", "0b111eff", "424958ff", 1);
        field.focusedBackground = box("field-focus", "11192aff", "c3a466ff", 2);
        field.disabledBackground = field.background;
        field.cursor = box("cursor", "e9e4d8ff", "e9e4d8ff", 0);
        field.cursor.setMinWidth(2);
        field.selection = box("selection", "604b80ff", "604b80ff", 0);
        skin.add("default", field);
        var button = new TextButton.TextButtonStyle();
        button.font = skin.getFont("default-font");
        button.fontColor = TEXT;
        button.disabledFontColor = MUTED;
        button.up = box("button", "303047ff", "9e8658ff", 1);
        button.over = box("button-over", "45405aff", "d1b478ff", 2);
        button.down = box("button-down", "242136ff", "b89addff", 2);
        button.disabled = box("button-disabled", "232735ff", "4b4c52ff", 1);
        skin.add("default", button);
        skin.add("default", new ScrollPane.ScrollPaneStyle());
    }

    private void font(FreeTypeFontGenerator generator, String name, int size) {
        var parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();
        parameter.size = size;
        parameter.characters = CHARACTERS;
        parameter.magFilter = Texture.TextureFilter.Linear;
        parameter.minFilter = Texture.TextureFilter.Linear;
        BitmapFont font = generator.generateFont(parameter);
        font.getData().markupEnabled = false;
        skin.add(name, font);
    }

    private NinePatchDrawable box(String name, String fill, String edge, int border) {
        Pixmap pixmap = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.valueOf(edge));
        pixmap.fill();
        pixmap.setColor(Color.valueOf(fill));
        pixmap.fillRectangle(border, border, 16 - border * 2, 16 - border * 2);
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        skin.add(name, texture);
        NinePatchDrawable drawable = new NinePatchDrawable(new NinePatch(texture, 4, 4, 4, 4));
        drawable.setLeftWidth(12);
        drawable.setRightWidth(12);
        drawable.setTopHeight(8);
        drawable.setBottomHeight(8);
        return drawable;
    }

    @Override public void dispose() { skin.dispose(); }
}
