package dev.gulp.examples.gallery;

import static dev.gulp.api.ui.Ui.*;

import dev.gulp.api.graphics.Color;
import dev.gulp.api.graphics.Pixmap;
import dev.gulp.api.graphics.TextureRegion;
import dev.gulp.api.ui.Align;
import dev.gulp.api.ui.Anchor;
import dev.gulp.api.ui.ButtonGroup;
import dev.gulp.api.ui.Computed;
import dev.gulp.api.ui.FoldGroup;
import dev.gulp.api.ui.ListState;
import dev.gulp.api.ui.ListView;
import dev.gulp.api.ui.Node;
import dev.gulp.api.ui.Screen;
import dev.gulp.api.ui.State;
import dev.gulp.api.ui.Theme;
import dev.gulp.api.ui.TreeItem;
import dev.gulp.api.ui.Window;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The gallery: a header with theme settings and a tab per widget family. No position appears anywhere. */
final class GalleryScreen extends Screen {

    private static final List<String> THEMES = List.of("Dark", "Light", "Pixel");

    private final State<String> status = State.of("Welcome! Press a button.");
    private final State<Float> value = State.of(0.4f);
    private final State<Boolean> checked = State.of(true);
    private final State<String> themeName = State.of("Dark");
    private final State<Boolean> contrast = State.of(false);
    private final State<Color> accent = State.of(Color.rgb(0x4f8cff));
    private final State<Float> scale = State.of(1f);
    private final ListState<String> rows = ListState.of();
    private final ListState<TextureRegion> backpack = ListState.of();
    private final ListState<TextureRegion> chest = ListState.of();
    private @org.jspecify.annotations.Nullable Window toolWindow;

    GalleryScreen() {
        blocksGameplayInput(false);
        for (int i = 1; i <= 1000; i++) {
            rows.add("Row " + i + " of a virtual list");
        }
        TextureRegion[] gems = {
            gem(Color.rgb(0xe5484d)), gem(Color.rgb(0x46b17b)), gem(Color.rgb(0x4f8cff)), gem(Color.rgb(0xf2b63d))
        };
        backpack.setAll(Arrays.asList(gems[0], null, gems[1], null, null, gems[2], null, null));
        chest.setAll(Arrays.asList(null, gems[3], null, null));
    }

    private TextureRegion gem(Color color) {
        Pixmap image = new Pixmap(24, 24);
        image.fillCircle(12, 12, 10, color);
        image.fillCircle(9, 9, 3, color.lerp(Color.WHITE, 0.6f));
        return graphics().texture(image).region();
    }

    private void say(String text) {
        status.set(text);
    }

    private void applyTheme() {
        Theme base =
                switch (themeName.get()) {
                    case "Light" -> Theme.LIGHT;
                    case "Pixel" -> Theme.PIXEL;
                    default -> Theme.DARK;
                };
        Theme accented = base.withAccent(accent.get());
        ui().setTheme(contrast.get() ? accented.highContrast() : accented);
    }

    @Override
    protected void onOpen() {
        themeName.subscribe(t -> applyTheme());
        contrast.subscribe(c -> applyTheme());
        accent.subscribe(c -> applyTheme());
        scale.set(ui().scale());
        scale.subscribe(s -> ui().setScale(s));
    }

    @Override
    protected void onBack() {
        say("Escape closes popups and dialogs; this screen stays.");
    }

    @Override
    protected Node<?> build() {
        // A panel gives every theme its own background, whatever the clear colour of the game is.
        return panel(column(
                                header(),
                                tabs(
                                                tab("Buttons", buttons()),
                                                tab("Values", values()),
                                                tab("Text", text()),
                                                tab("Lists", lists()),
                                                tab("Layout", layout()),
                                                tab("Inventory", inventory()),
                                                tab("Overlays", overlays()))
                                        .grow(),
                                panel(label(status)).fillX())
                        .gap(12))
                .style(st -> st.padding(20));
    }

    private Node<?> header() {
        return row(
                        label("Gulp UI gallery").variant("title"),
                        spacer(),
                        label("Theme"),
                        dropdown(THEMES, t -> t).bind(themeName).width(130),
                        toggle("High contrast").bind(contrast),
                        label("Scale"),
                        slider(0.75f, 1.5f).step(0.25f).bind(scale).width(120))
                .gap(10)
                .align(Align.CENTER);
    }

    private Node<?> buttons() {
        ButtonGroup<String> size = new ButtonGroup<>("Medium");
        size.value().subscribe(s -> say("Size: " + s));
        return column(
                        label("Buttons").variant("heading"),
                        row(
                                        button("Primary").variant("primary").onClick(() -> say("Primary pressed")),
                                        button("Default").onClick(() -> say("Default pressed")),
                                        button("Danger").variant("danger").onClick(() -> say("Danger pressed")),
                                        button("Flat").variant("flat").onClick(() -> say("Flat pressed")),
                                        iconButton(gem(Color.rgb(0xf2b63d)))
                                                .tooltip("An icon button with a tooltip")
                                                .onClick(() -> say("Icon pressed")),
                                        button("Disabled").enabled(false))
                                .gap(8),
                        label("Choices").variant("heading"),
                        row(
                                        checkbox("Checkbox").bind(checked),
                                        toggle("Toggle").onChange(on -> say("Toggle " + (on ? "on" : "off"))),
                                        radio("Small", size, "Small"),
                                        radio("Medium", size, "Medium"),
                                        radio("Large", size, "Large"))
                                .gap(16)
                                .align(Align.CENTER),
                        label(checked.map(c -> c ? "The checkbox is checked" : "The checkbox is clear"))
                                .variant("caption"),
                        label("Rebinding").variant("heading"),
                        row(label("Jump"), keybindButton(UiGalleryGame.jump, 0), keybindButton(UiGalleryGame.jump, 1))
                                .gap(8)
                                .align(Align.CENTER))
                .gap(10);
    }

    private Node<?> values() {
        return grid(
                        2,
                        label("Slider"),
                        slider(0, 1).bind(value).width(260),
                        label("Progress"),
                        progressBar(value).width(260),
                        label("Ring"),
                        progressBar(value).circular().size(48, 48),
                        label("Spin box"),
                        spinBox(0, 20, 1).value(5).onChange(v -> say("Spin box: " + v.intValue())),
                        label("Dropdown"),
                        dropdown(List.of("Wood", "Stone", "Iron", "Gold"), s -> s)
                                .onChange(s -> say("Picked " + s)),
                        label("Accent"),
                        colorPicker().bind(accent),
                        label("Value"),
                        label(Computed.of(() -> Math.round(value.get() * 100) + "%")))
                .hGap(24)
                .vGap(10);
    }

    private Node<?> text() {
        return column(
                        label("Fields").variant("heading"),
                        row(
                                        textField()
                                                .placeholder("Name (required)")
                                                .validate(s -> !s.isBlank())
                                                .onSubmit(s -> say("Hello, " + s))
                                                .width(260),
                                        textField()
                                                .placeholder("Password")
                                                .password(true)
                                                .width(200),
                                        textField()
                                                .placeholder("Digits")
                                                .filter(Character::isDigit)
                                                .width(120))
                                .gap(8),
                        textArea()
                                .rows(4)
                                .placeholder("Several lines; Ctrl+Enter submits")
                                .fillX(),
                        label("Wrapping text").variant("heading"),
                        label("Labels wrap to the width their container gives them. Resize the window or change the"
                                        + " UI scale and this paragraph reflows without a single coordinate in the"
                                        + " game code.")
                                .wrap(true),
                        richText("Rich text: [b]bold[/b], [color=gold]colour[/color], [wave]waves[/wave],"
                                        + " [rainbow]rainbow[/rainbow] and a [link=about]link[/link].")
                                .onLink(id -> say("Link clicked: " + id)))
                .gap(8);
    }

    private Node<?> lists() {
        TreeItem<String> root = new TreeItem<>("assets");
        TreeItem<String> sprites = root.add(new TreeItem<>("sprites"));
        sprites.add(new TreeItem<>("player.png"));
        sprites.add(new TreeItem<>("coin.png"));
        TreeItem<String> maps = root.add(new TreeItem<>("maps"));
        maps.add(new TreeItem<>("level1.ldtk"));
        root.add(new TreeItem<>("lang")).add(new TreeItem<>("pl_pl.json"));
        root.expanded(true);
        return row(
                        column(
                                        label("Virtual list of 1000 rows").variant("heading"),
                                        listView(rows, r -> label(r))
                                                .selection(ListView.SelectionMode.MULTIPLE)
                                                .onActivate(r -> say("Activated " + r))
                                                .grow(),
                                        row(
                                                        button("Add row").onClick(() -> rows.add(0, "New row")),
                                                        button("Remove first").onClick(() -> {
                                                            if (rows.size() > 0) {
                                                                rows.remove(0);
                                                            }
                                                        }))
                                                .gap(8))
                                .gap(8)
                                .grow(),
                        column(
                                        label("Tree").variant("heading"),
                                        tree(root, s -> s)
                                                .onSelect(s -> say("Selected " + s))
                                                .grow())
                                .gap(8)
                                .grow())
                .gap(16);
    }

    private Node<?> layout() {
        FoldGroup accordion = new FoldGroup();
        List<Node<?>> tags = new ArrayList<>();
        for (String tag : List.of(
                "row",
                "column",
                "grid",
                "stack",
                "center",
                "margin",
                "panel",
                "scroll",
                "split",
                "flow",
                "tabs",
                "foldable",
                "aspect",
                "spacer")) {
            tags.add(button(tag).variant("flat").onClick(() -> say("Container: " + tag)));
        }
        return scroll(column(
                        label("Flow").variant("heading"),
                        flow(tags.toArray(new Node<?>[0])).gap(4),
                        label("Grid").variant("heading"),
                        grid(3, label("A"), label("B"), label("C"), button("1"), button("2"), button("3"))
                                .gap(6),
                        label("Split, stack and aspect").variant("heading"),
                        split(
                                        panel(label("Drag the divider, or focus it and press accept.")
                                                .wrap(true)),
                                        stack(
                                                panel(spacer()),
                                                label("top left")
                                                        .anchor(Anchor.TOP_LEFT)
                                                        .offset(10, 8),
                                                label("bottom right")
                                                        .anchor(Anchor.BOTTOM_RIGHT)
                                                        .offset(10, 8),
                                                center(aspect(16f / 9f, panel(label("16:9")))
                                                        .size(128, 72))))
                                .height(130),
                        label("Foldable sections").variant("heading"),
                        foldable(
                                        "What is a container?",
                                        label("A node that places its children.")
                                                .wrap(true))
                                .group(accordion)
                                .open(true),
                        foldable(
                                        "What is a widget?",
                                        label("A node the player sees and uses.")
                                                .wrap(true))
                                .group(accordion),
                        foldable(
                                        "Where are the coordinates?",
                                        label("There are none.").wrap(true))
                                .group(accordion))
                .gap(8));
    }

    private Node<?> inventory() {
        return column(
                        label("Drag gems between slots, or pick one up with accept and drop it with accept.")
                                .wrap(true),
                        row(
                                        column(
                                                        label("Backpack").variant("heading"),
                                                        itemGrid(4, backpack, g -> image(g)))
                                                .gap(6),
                                        column(label("Chest").variant("heading"), itemGrid(2, chest, g -> image(g)))
                                                .gap(6),
                                        spacer())
                                .gap(24),
                        button("Right-click me (or press X on the gamepad)")
                                .contextMenu(menu(
                                        item("Sort", () -> say("Sorted")),
                                        item("Split stack", () -> say("Split")),
                                        item("Destroy", () -> say("Destroyed")).disabled()))
                                .alignX(Align.START))
                .gap(10);
    }

    private Node<?> overlays() {
        return row(
                        column(
                                        button("Open a dialog")
                                                .onClick(() -> ui().push(dialog(
                                                                "Leave the gallery?",
                                                                label("The dialog pauses nothing and"
                                                                                + " dims the screen below.")
                                                                        .wrap(true))
                                                        .button("Stay", null)
                                                        .button("Leave", "danger", () -> say("You tried to leave")))),
                                        button("Open a modal")
                                                .onClick(() -> ui().push(modal(panel(column(
                                                                label("A modal").variant("heading"),
                                                                label("Press Escape or B to close it."))
                                                        .gap(8))))),
                                        button("Show a toast").onClick(() -> ui().toast("Saved!", "success", 2.5f)),
                                        button("Toggle a window").onClick(this::toggleWindow),
                                        button("Hover for a tooltip").tooltip("Tooltips wait for the theme delay"))
                                .gap(8)
                                .alignX(Align.START),
                        column(
                                        label("Virtual joystick").variant("heading"),
                                        virtualJoystick()
                                                .onChange(v -> say("Stick " + Math.round(v.x() * 100) / 100f + ", "
                                                        + Math.round(v.y() * 100) / 100f)))
                                .gap(8))
                .gap(40);
    }

    private void toggleWindow() {
        Window window = toolWindow;
        if (window == null) {
            window = window(
                            "Tool window",
                            column(label("Drag me by the title."), button("OK").onClick(() -> {
                                        Window open = toolWindow;
                                        if (open != null) {
                                            open.close();
                                        }
                                    }))
                                    .gap(8))
                    .anchor(Anchor.CENTER)
                    .onClose(() -> say("Window closed"));
            toolWindow = window;
            ui().hud().add(this, window);
        } else {
            window.visible(!window.isVisible());
        }
    }

    @Override
    protected void onClose() {
        Window window = toolWindow;
        if (window != null) {
            ui().hud().remove(window);
            toolWindow = null;
        }
    }
}
