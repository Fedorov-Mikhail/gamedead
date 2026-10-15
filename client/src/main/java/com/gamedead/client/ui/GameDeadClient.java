package com.gamedead.client.ui;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.*;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.gamedead.client.auth.*;
import com.gamedead.client.net.GameApi;
import java.util.ArrayList;
import java.util.List;

public final class GameDeadClient extends ApplicationAdapter {
    private final GameApi api;
    private final String serverAddress;
    private Stage stage;
    private UiTheme theme;
    private ShapeRenderer shapes;
    private AuthController auth;
    private AuthState.View currentView;
    private final List<TextField> fields = new ArrayList<>();
    private final List<TextButton> buttons = new ArrayList<>();
    private TextField loginField, nameField, passwordField, confirmationField;
    private Label status;
    private boolean wasBusy;
    private float time;

    public GameDeadClient(GameApi api, String serverAddress) {
        this.api = api;
        this.serverAddress = serverAddress;
    }

    @Override public void create() {
        theme = new UiTheme();
        shapes = new ShapeRenderer();
        stage = new Stage(new ExtendViewport(1080, 720));
        Gdx.input.setInputProcessor(stage);
        auth = new AuthController(api, action -> Gdx.app.postRunnable(action), this::show);
        show(auth.state());
    }

    private void show(AuthState state) {
        boolean rebuild = currentView != state.view() || (state.view() == AuthState.View.PROFILE && !state.busy());
        if (rebuild) build(state);
        else if (wasBusy && !state.busy()) clearPasswords();
        fields.forEach(field -> field.setDisabled(state.busy()));
        buttons.forEach(button -> button.setDisabled(state.busy()));
        String message = state.message();
        if (!state.fieldErrors().isEmpty()) message += "\n" + String.join("\n", state.fieldErrors().values());
        status.setText(message);
        status.setColor(state.error() ? UiTheme.ERROR : state.busy() ? UiTheme.GOLD : UiTheme.MUTED);
        wasBusy = state.busy();
    }

    private void build(AuthState state) {
        clearPasswords();
        stage.clear();
        fields.clear();
        buttons.clear();
        loginField = nameField = passwordField = confirmationField = null;
        currentView = state.view();
        Table root = new Table();
        root.setFillParent(true);
        root.pad(32);
        stage.addActor(root);
        Table brand = new Table();
        brand.left();
        Label eyebrow = label("GAMEDEAD  /  КООПЕРАТИВНОЕ ВЫЖИВАНИЕ", "small");
        eyebrow.setColor(UiTheme.GOLD);
        brand.add(eyebrow).left().padBottom(20).row();
        brand.add(label("ПЕЧАТЬ\nСУМРАКА", "title")).left().padBottom(22).row();
        Label description = label("Собери отряд. Переживи натиск.\nРазорви древнюю печать.", "default");
        description.setColor(UiTheme.MUTED);
        brand.add(description).left().padBottom(36).row();
        brand.add(label("2–4 ИГРОКА   •   30 МИНУТ НА ЗАБЕГ", "small")).left().row();
        root.add(brand).expandX().fillX().padRight(32);

        Table form = new Table();
        form.background(theme.skin.getDrawable("panel"));
        form.pad(26);
        form.defaults().growX();
        if (state.view() == AuthState.View.PROFILE) buildProfile(form, state);
        else buildForm(form, state);
        Label server = label("Сервер: " + serverAddress, "small");
        server.setWrap(true);
        form.add(server).padTop(20).row();
        ScrollPane scroll = new ScrollPane(form, theme.skin);
        scroll.setScrollingDisabled(true, false);
        scroll.setFadeScrollBars(false);
        scroll.setFlickScroll(false);
        root.add(scroll).width(460).expandY().fillY();
        stage.setScrollFocus(scroll);
        stage.setKeyboardFocus(loginField);
        if (loginField != null && !loginField.getText().isEmpty()) stage.setKeyboardFocus(passwordField);
    }

    private void buildForm(Table form, AuthState state) {
        boolean registration = state.view() == AuthState.View.REGISTER;
        form.add(label(registration ? "Создать аккаунт" : "Войти в игру", "heading")).left().padBottom(7).row();
        Label subtitle = label(registration ? "Выбери имя для своего героя." : "Твой отряд ждёт по ту сторону печати.", "small");
        subtitle.setWrap(true);
        form.add(subtitle).padBottom(16).row();
        loginField = field(form, "Логин", state.login(), "login", false);
        if (registration) nameField = field(form, "Имя в игре", "", "displayName", false);
        passwordField = field(form, "Пароль", "", "password", true);
        if (registration) confirmationField = field(form, "Повтори пароль", "", "confirmation", true);
        TextButton reveal = button("Показать пароль", "reveal", () -> {
            boolean visible = passwordField.isPasswordMode();
            passwordField.setPasswordMode(!visible);
            if (confirmationField != null) confirmationField.setPasswordMode(!visible);
        });
        reveal.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                reveal.setText(passwordField.isPasswordMode() ? "Показать пароль" : "Скрыть пароль");
            }
        });
        form.add(reveal).height(34).padTop(2).padBottom(10).row();
        addStatus(form);
        form.add(button(registration ? "Создать аккаунт" : "Войти", "submit", this::submit))
                .height(46).padTop(8).row();
        form.add(button(registration ? "Назад ко входу" : "Создать аккаунт", "navigate", () -> {
            if (registration) auth.showLogin(); else auth.showRegister();
        })).height(44).padTop(8).row();
    }

    private void buildProfile(Table form, AuthState state) {
        Label caption = label("ТВОЙ ГЕРОЙ", "small");
        caption.setColor(UiTheme.GOLD);
        form.add(caption).padBottom(12).row();
        Label name = label(state.profile().displayName(), "heading");
        name.setName("profile-name");
        name.setWrap(true);
        form.add(name).padBottom(10).row();
        form.add(label("Логин: " + state.profile().login(), "default")).padBottom(8).row();
        Label xp = label("Опыт аккаунта: " + state.profile().accountXp(), "default");
        xp.setName("profile-xp");
        form.add(xp).padBottom(24).row();
        Label note = label("Профиль сохранён. Используй этот аккаунт при следующем входе.", "small");
        note.setWrap(true);
        form.add(note).padBottom(20).row();
        addStatus(form);
        form.add(button("Обновить профиль", "refresh", auth::refreshProfile)).height(46).padTop(10).row();
        form.add(button("Выйти из аккаунта", "logout", auth::logout)).height(46).padTop(10).row();
        form.add(button("Закрыть игру", "close", () -> Gdx.app.exit())).height(44).padTop(10).row();
    }

    private TextField field(Table form, String title, String value, String name, boolean password) {
        form.add(label(title, "small")).left().padBottom(3).row();
        TextField field = password ? new ExactPasswordField(theme.skin) : new TextField(value, theme.skin);
        field.setName(name);
        field.setOnlyFontChars(false);
        field.setPasswordCharacter('*');
        field.setPasswordMode(password);
        field.setTextFieldListener((input, character) -> {
            if (character == '\r' || character == '\n') submit();
        });
        form.add(field).height(42).padBottom(8).row();
        fields.add(field);
        return field;
    }

    private void submit() {
        if (auth.state().busy() || loginField == null || passwordField == null) return;
        if (currentView == AuthState.View.REGISTER) auth.register(loginField.getText(), nameField.getText(),
                passwordField.getText(), confirmationField.getText());
        else auth.login(loginField.getText(), passwordField.getText());
    }

    private void addStatus(Table form) {
        status = label("", "small");
        status.setName("status");
        status.setWrap(true);
        form.add(status).minHeight(42).row();
    }

    private Label label(String text, String style) { return new Label(text, theme.skin, style); }

    private TextButton button(String text, String name, Runnable action) {
        TextButton button = new TextButton(text, theme.skin);
        button.setName(name);
        button.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) { if (!button.isDisabled()) action.run(); }
        });
        buttons.add(button);
        return button;
    }

    private void clearPasswords() {
        if (passwordField != null) passwordField.setText("");
        if (confirmationField != null) confirmationField.setText("");
    }

    @Override public void render() {
        time += Math.min(Gdx.graphics.getDeltaTime(), 0.1f);
        ScreenUtils.clear(0.035f, 0.05f, 0.09f, 1);
        stage.getViewport().apply();
        drawRunes();
        stage.act(Math.min(Gdx.graphics.getDeltaTime(), 0.1f));
        stage.draw();
    }

    private void drawRunes() {
        float width = stage.getViewport().getWorldWidth();
        float height = stage.getViewport().getWorldHeight();
        shapes.setProjectionMatrix(stage.getCamera().combined);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(0.13f, 0.12f, 0.23f, 1);
        float x = width * 0.22f, y = height * 0.56f;
        shapes.circle(x, y, 214, 80);
        shapes.circle(x, y, 197, 80);
        shapes.circle(x, y, 155, 64);
        for (int i = 0; i < 12; i++) {
            float angle = (float) (i * Math.PI / 6 + time * 0.012f);
            float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
            shapes.line(x + cos * 168, y + sin * 168, x + cos * 188, y + sin * 188);
            shapes.line(x + cos * 180 - sin * 5, y + sin * 180 + cos * 5,
                    x + cos * 186 + sin * 5, y + sin * 186 - cos * 5);
        }
        shapes.setColor(0.2f, 0.18f, 0.21f, 1);
        shapes.line(32, 54, width - 32, 54);
        shapes.end();
    }

    @Override public void resize(int width, int height) {
        if (stage != null) stage.getViewport().update(width, height, true);
    }

    @Override public void dispose() {
        if (auth != null) auth.close(); else api.close();
        clearPasswords();
        Gdx.input.setInputProcessor(null);
        if (stage != null) stage.dispose();
        if (shapes != null) shapes.dispose();
        if (theme != null) theme.dispose();
    }
}
