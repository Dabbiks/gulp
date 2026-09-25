package dev.gulp.core;

import static dev.gulp.api.ui.Ui.*;
import static org.assertj.core.api.Assertions.assertThat;

import dev.gulp.api.graphics.Color;
import dev.gulp.api.input.ActionSet;
import dev.gulp.api.input.GamepadButton;
import dev.gulp.api.input.InputAction;
import dev.gulp.api.input.Keys;
import dev.gulp.api.input.MouseButtonPressEvent;
import dev.gulp.api.registry.Key;
import dev.gulp.api.ui.Anchor;
import dev.gulp.api.ui.Button;
import dev.gulp.api.ui.ButtonGroup;
import dev.gulp.api.ui.Checkbox;
import dev.gulp.api.ui.ColorPicker;
import dev.gulp.api.ui.Direction;
import dev.gulp.api.ui.Dropdown;
import dev.gulp.api.ui.FoldGroup;
import dev.gulp.api.ui.Foldable;
import dev.gulp.api.ui.ItemGrid;
import dev.gulp.api.ui.KeybindButton;
import dev.gulp.api.ui.ListState;
import dev.gulp.api.ui.ListView;
import dev.gulp.api.ui.MenuPopup;
import dev.gulp.api.ui.Node;
import dev.gulp.api.ui.NodeEvent;
import dev.gulp.api.ui.ProgressBar;
import dev.gulp.api.ui.Radio;
import dev.gulp.api.ui.RichText;
import dev.gulp.api.ui.Slider;
import dev.gulp.api.ui.SpinBox;
import dev.gulp.api.ui.Split;
import dev.gulp.api.ui.State;
import dev.gulp.api.ui.Tabs;
import dev.gulp.api.ui.TextArea;
import dev.gulp.api.ui.TextField;
import dev.gulp.api.ui.Toggle;
import dev.gulp.api.ui.Tree;
import dev.gulp.api.ui.TreeItem;
import dev.gulp.api.ui.VirtualJoystick;
import dev.gulp.api.ui.Window;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class UiInputTest extends UiFixture {

    @Test
    void mouseClicksHoverTooltipsAndConsumption() {
        startUi();
        List<String> log = new ArrayList<>();
        List<Boolean> consumed = new ArrayList<>();
        game.on(MouseButtonPressEvent.class, e -> consumed.add(e.isConsumedByUi()));
        Button save = button("Save").tooltip("Saves the game").onClick(() -> log.add("save"));
        save.onHover(on -> log.add("hover " + on)).onFocus(on -> log.add("focus " + on));
        ui.hud().add(game, save.anchor(Anchor.TOP_LEFT).offset(10f, 10f));
        step(1);
        move(save.x() + 5f, save.y() + 5f);
        assertThat(save.isHovered()).isTrue();
        assertThat(ui.hovered()).isSameAs(save);
        seconds(0.8f);
        click(save);
        assertThat(log).contains("hover true", "save", "focus true");
        click(600f, 600f);
        assertThat(consumed).containsExactly(true, false);
        assertThat(ui.focused()).isNull();
        save.enabled(false);
        click(save);
        assertThat(log).filteredOn("save"::equals).hasSize(1);
        assertThat(save.isEnabled()).isFalse();
    }

    @Test
    void keyboardAndGamepadMoveTheFocusAndPressButtons() {
        startUi();
        List<String> pressed = new ArrayList<>();
        Button a = button("A").onClick(() -> pressed.add("a"));
        Button b = button("B").onClick(() -> pressed.add("b"));
        Button c = button("C").onClick(() -> pressed.add("c"));
        Button d = button("D").onClick(() -> pressed.add("d"));
        open(center(column(row(a, b).gap(10f), row(c, d).gap(10f)).gap(10f)));
        assertThat(ui.focused()).isSameAs(a);
        key(Keys.RIGHT);
        assertThat(ui.focused()).isSameAs(b);
        key(Keys.DOWN);
        assertThat(ui.focused()).isSameAs(d);
        pad(GamepadButton.DPAD_LEFT);
        assertThat(ui.focused()).isSameAs(c);
        pad(GamepadButton.SOUTH);
        key(Keys.SPACE);
        assertThat(pressed).containsExactly("c", "c");
        key(Keys.TAB);
        assertThat(ui.focused()).isSameAs(d);
        key(Keys.TAB, 1);
        assertThat(ui.focused()).isSameAs(c);
        c.focusNeighbor(Direction.UP, b);
        key(Keys.UP);
        assertThat(ui.focused()).isSameAs(b);
        ui.focus(a);
        assertThat(a.hasFocus()).isTrue();
        backend().input().inject(l -> l.keyDown(Keys.DOWN.code(), 0, 0, false));
        seconds(0.7f);
        backend().input().inject(l -> l.keyUp(Keys.DOWN.code(), 0, 0));
        step(1);
        assertThat(ui.focused()).isSameAs(c);
    }

    @Test
    void valueWidgetsReactToPointerKeysAndPad() {
        startUi();
        State<Float> volume = State.of(0.5f);
        State<Boolean> music = State.of(true);
        ButtonGroup<String> quality = new ButtonGroup<>("low");
        Slider slider = slider(0f, 1f).bind(volume).width(200f);
        Checkbox box = checkbox("Music").bind(music);
        Toggle vsync = toggle("VSync");
        Radio<String> low = radio("Low", quality, "low");
        Radio<String> high = radio("High", quality, "high");
        SpinBox spin = spinBox(0f, 10f, 1f).value(5f);
        SpinBox fine = spinBox(-1f, 1f, 0.25f).value(-0.5f);
        ProgressBar bar = progressBar(volume).width(100f);
        ProgressBar ring = progressBar(0.3f).circular().smooth(false);
        List<Float> spins = new ArrayList<>();
        spin.onChange(spins::add);
        open(column(slider, box, vsync, row(low, high), spin, fine, bar, ring).gap(8f));
        click(slider.x() + slider.width() - 2f, slider.y() + slider.height() / 2f);
        assertThat(volume.get()).isGreaterThan(0.95f);
        ui.focus(slider);
        key(Keys.LEFT);
        assertThat(volume.get()).isLessThan(1f);
        wheel(slider.x() + 5f, slider.y() + 5f, 1f);
        slider.step(0.25f);
        assertThat(slider.value() % 0.25f).isZero();
        click(box);
        assertThat(music.get()).isFalse();
        ui.focus(vsync);
        key(Keys.RIGHT);
        assertThat(vsync.isChecked()).isTrue();
        key(Keys.LEFT);
        assertThat(vsync.isChecked()).isFalse();
        click(high);
        assertThat(quality.value().get()).isEqualTo("high");
        assertThat(low.isChecked()).isFalse();
        assertThat(high.value()).isEqualTo("high");
        click(spin.x() + spin.width() - 3f, spin.y() + 3f);
        click(spin.x() + 3f, spin.y() + 3f);
        ui.focus(spin);
        key(Keys.RIGHT);
        wheel(spin.x() + 3f, spin.y() + 3f, 1f);
        assertThat(spins).containsExactly(6f, 5f, 6f, 5f);
        seconds(0.5f);
        assertThat(bar.value()).isEqualTo(volume.get());
        assertThat(ring.value()).isEqualTo(0.3f);
        assertThat(fine.value()).isEqualTo(-0.5f);
    }

    @Test
    void textFieldsEditSelectCopyValidateAndSubmit() {
        startUi();
        State<String> name = State.of("");
        List<String> submitted = new ArrayList<>();
        TextField field =
                textField().placeholder("Name").maxLength(8).bind(name).onSubmit(submitted::add);
        TextField digits = textField().filter(Character::isDigit).validate(s -> !s.isEmpty());
        TextField secret = textField().password(true);
        TextArea notes = textArea().rows(3);
        open(column(field, digits, secret, notes).gap(8f));
        click(field);
        type("Hello world");
        assertThat(name.get()).isEqualTo("Hello wo");
        key(Keys.BACKSPACE);
        key(Keys.LEFT, 1);
        assertThat(field.selectedText()).isEqualTo("w");
        key(Keys.C, 2);
        key(Keys.END);
        key(Keys.V, 2);
        step(2);
        assertThat(field.text()).isEqualTo("Hello ww");
        key(Keys.HOME);
        key(Keys.DELETE);
        key(Keys.RIGHT, 2);
        key(Keys.A, 2);
        key(Keys.X, 2);
        assertThat(field.text()).isEmpty();
        type("Ann");
        key(Keys.ENTER);
        assertThat(submitted).containsExactly("Ann");
        assertThat(field.caret()).isEqualTo(3);

        assertThat(digits.isValid()).isFalse();
        assertThat(digits.variant()).isEqualTo("invalid");
        click(digits);
        type("a1b2");
        assertThat(digits.text()).isEqualTo("12");
        assertThat(digits.isValid()).isTrue();
        key(Keys.BACKSPACE, 2);
        key(Keys.DELETE, 2);

        click(secret);
        type("pw");
        key(Keys.A, 2);
        key(Keys.C, 2);
        click(notes);
        type("one");
        key(Keys.ENTER);
        type("two");
        key(Keys.UP);
        key(Keys.DOWN);
        key(Keys.HOME);
        key(Keys.END, 2);
        key(Keys.LEFT, 2);
        key(Keys.ENTER, 2);
        assertThat(notes.text()).isEqualTo("one\ntwo");
        press(notes.x() + 4f, notes.y() + 4f, 0);
        move(notes.x() + 60f, notes.y() + 30f);
        release(notes.x() + 60f, notes.y() + 30f, 0);
        field.text("x");
        field.insert("yz");
        key(Keys.ESCAPE);
        assertThat(ui.current()).isNull();
    }

    @Test
    void popupsListsTreesAndContextMenus() {
        startUi();
        Dropdown<String> dropdown = dropdown(List.of("Low", "Medium", "High"), s -> s);
        State<String> quality = State.of("Medium");
        dropdown.bind(quality);
        ListState<String> items = ListState.of();
        for (int i = 0; i < 50; i++) {
            items.add("Item " + i);
        }
        List<String> activated = new ArrayList<>();
        ListView<String> list = listView(items, s -> label(s))
                .selection(ListView.SelectionMode.SINGLE)
                .visibleRows(5)
                .onActivate(activated::add);
        TreeItem<String> root = new TreeItem<>("root");
        TreeItem<String> branch = root.add(new TreeItem<>("branch"));
        branch.add(new TreeItem<>("leaf"));
        root.expanded(true);
        List<String> picked = new ArrayList<>();
        Tree<String> tree = tree(root, s -> s).onSelect(picked::add);
        List<String> menu = new ArrayList<>();
        Button target = button("Target")
                .contextMenu(menu(
                        item("Copy", () -> menu.add("copy")),
                        item("Delete", () -> menu.add("delete")).disabled()));
        open(column(dropdown, list, tree, target).gap(6f));
        assertThat(dropdown.value()).isEqualTo("Medium");
        click(dropdown);
        assertThat(ui.focused()).isNotSameAs(dropdown);
        key(Keys.DOWN);
        key(Keys.ENTER);
        assertThat(quality.get()).isEqualTo("High");
        ui.focus(dropdown);
        key(Keys.LEFT);
        assertThat(quality.get()).isEqualTo("Medium");
        click(dropdown);
        key(Keys.ESCAPE);
        assertThat(ui.current()).isNotNull();
        click(dropdown);
        click(1200f, 700f);

        ui.focus(list);
        key(Keys.DOWN);
        key(Keys.DOWN);
        key(Keys.ENTER);
        assertThat(list.selectedItems()).containsExactly("Item 2");
        assertThat(activated).containsExactly("Item 2");
        wheel(list.x() + 5f, list.y() + 5f, 3f);
        items.set(3, "Changed");
        items.remove(0);
        items.add(0, "New");
        step(1);
        list.select(4);
        assertThat(list.cursorIndex()).isEqualTo(4);
        click(list.x() + 10f, list.y() + 10f);
        press(list.x() + 10f, list.y() + 40f, 0);
        move(list.x() + 10f, list.y() + 5f);
        release(list.x() + 10f, list.y() + 5f, 0);
        list.selection(ListView.SelectionMode.MULTIPLE);
        items.clear();
        step(1);

        ui.focus(tree);
        key(Keys.DOWN);
        key(Keys.RIGHT);
        key(Keys.RIGHT);
        key(Keys.ENTER);
        assertThat(picked).containsExactly("leaf");
        key(Keys.LEFT);
        key(Keys.LEFT);
        assertThat(branch.isExpanded()).isFalse();
        assertThat(tree.visibleItems()).hasSize(2);
        assertThat(branch.depth()).isEqualTo(1);
        assertThat(branch.parent()).isSameAs(root);
        assertThat(tree.selected()).isNotNull();
        step(1);
        Node<?> branchRow = tree.children().get(0).children().get(1);
        click(branchRow.x() + 20f, branchRow.y() + 5f);
        assertThat(branch.isExpanded()).isTrue();
        root.remove(branch);

        rightClick(target);
        List<MenuPopup> popups = new ArrayList<>();
        assertThat(ui.focused()).isNotNull();
        key(Keys.ENTER);
        assertThat(menu).containsExactly("copy");
        ui.focus(target);
        backend().input().setGamepad(0, "Xbox", new float[6], buttons(GamepadButton.WEST));
        step(1);
        backend().input().setGamepad(0, "Xbox", new float[6], new boolean[17]);
        step(1);
        key(Keys.ESCAPE);
        assertThat(popups).isEmpty();
    }

    private static boolean[] buttons(GamepadButton... pressed) {
        boolean[] result = new boolean[17];
        for (GamepadButton button : pressed) {
            result[button.index()] = true;
        }
        return result;
    }

    @Test
    void containersWithInteraction() {
        startUi();
        Tabs tabs = tabs(tab("One", label("first")), tab("Two", label("second"), label("more")));
        State<Integer> page = State.of(0);
        tabs.bind(page);
        FoldGroup group = new FoldGroup();
        Foldable a = foldable("A", label("a body")).group(group);
        Foldable b = foldable("B", label("b body")).group(group).open(true);
        Split split = split(label("left"), label("right"));
        Window window = window("Tools", label("inside")).offset(400f, 300f);
        List<String> closed = new ArrayList<>();
        window.onClose(() -> closed.add("closed"));
        open(stack(column(tabs, a, b, split.height(60f)).anchor(Anchor.TOP_LEFT).width(500f), window));
        ui.focus(tabs.children().get(0).children().get(0));
        pad(GamepadButton.RIGHT_BUMPER);
        assertThat(tabs.selected()).isEqualTo(1);
        assertThat(page.get()).isEqualTo(1);
        key(Keys.PAGE_UP);
        assertThat(tabs.selected()).isZero();
        tabs.previous();
        tabs.next();
        assertThat(tabs.count()).isEqualTo(2);
        click(tabs.children().get(0).children().get(1));
        assertThat(tabs.selected()).isEqualTo(1);

        assertThat(group.open()).isSameAs(b);
        click(a.children().get(0));
        assertThat(a.isOpen()).isTrue();
        assertThat(b.isOpen()).isFalse();
        ui.focus(a.children().get(0));
        key(Keys.LEFT);
        assertThat(a.isOpen()).isFalse();
        key(Keys.RIGHT);
        assertThat(group.open()).isSameAs(a);

        float divider = split.x() + split.width() / 2f;
        press(divider, split.y() + 10f, 0);
        move(divider + 100f, split.y() + 10f);
        release(divider + 100f, split.y() + 10f, 0);
        assertThat(split.ratio()).isGreaterThan(0.6f);
        ui.focus(split);
        key(Keys.ENTER);
        key(Keys.LEFT);
        key(Keys.ESCAPE);
        assertThat(ui.current()).isNotNull();

        float wx = window.x();
        press(window.x() + 20f, window.y() + 10f, 0);
        move(window.x() + 70f, window.y() + 40f);
        release(window.x() + 70f, window.y() + 40f, 0);
        assertThat(window.x()).isGreaterThan(wx);
        assertThat(window.content()).isNotNull();
        click(window.findAll(Button.class).get(0));
        assertThat(closed).containsExactly("closed");
        assertThat(window.isVisible()).isFalse();
    }

    @Test
    void dragAndDropColoursKeybindsJoysticksAndLinks() {
        startUi();
        ListState<String> slots = ListState.of(Arrays.asList("sword", null, "shield", null));
        ListState<String> chest = ListState.of(Arrays.asList(null, "gem"));
        List<List<String>> moves = new ArrayList<>();
        ItemGrid<String> bag = itemGrid(2, slots, s -> label(s)).onChange(moves::add);
        ItemGrid<String> box = itemGrid(2, chest, s -> label(s));
        State<Color> color = State.of(Color.hex("#3b82f6"));
        ColorPicker picker = colorPicker().bind(color);
        InputAction jump =
                InputAction.builder(Key.of("test", "jump")).bind(Keys.SPACE).build();
        KeybindButton rebind = keybindButton(jump, 1);
        InputAction left =
                InputAction.builder(Key.of("test", "left")).set(ActionSet.MENU).build();
        InputAction right =
                InputAction.builder(Key.of("test", "right")).set(ActionSet.MENU).build();
        InputAction up =
                InputAction.builder(Key.of("test", "up")).set(ActionSet.MENU).build();
        InputAction down =
                InputAction.builder(Key.of("test", "down")).set(ActionSet.MENU).build();
        VirtualJoystick stick = virtualJoystick().actions(left, right, up, down);
        List<String> links = new ArrayList<>();
        RichText rich = richText("Open the [link=shop]shop[/link] now").onLink(links::add);
        open(column(row(bag, box).gap(20f), picker, rebind, stick, rich.width(400f))
                .anchor(Anchor.TOP_LEFT));
        Node<?> from = bag.slot(0);
        Node<?> to = bag.slot(1);
        press(from.x() + 5f, from.y() + 5f, 0);
        move(from.x() + 30f, from.y() + 5f);
        move(to.x() + 10f, to.y() + 10f);
        release(to.x() + 10f, to.y() + 10f, 0);
        assertThat(slots.get()).containsExactly(null, "sword", "shield", null);
        ui.focus(box.slot(1));
        key(Keys.ENTER);
        ui.focus(bag.slot(0));
        key(Keys.ENTER);
        assertThat(slots.get(0)).isEqualTo("gem");
        assertThat(chest.get(1)).isNull();
        assertThat(moves).isNotEmpty();
        ui.focus(bag.slot(2));
        key(Keys.ENTER);
        key(Keys.ESCAPE);
        assertThat(bag.slots()).isSameAs(slots);

        click(picker.x() + 5f, picker.y() + 5f);
        assertThat(color.get().r()).isGreaterThan(0.9f);
        press(picker.x() + picker.width() - 5f, picker.y() + 50f, 0);
        move(picker.x() + picker.width() - 5f, picker.y() + 60f);
        release(picker.x() + picker.width() - 5f, picker.y() + 60f, 0);
        ui.focus(picker);
        key(Keys.ENTER);
        key(Keys.RIGHT);
        key(Keys.DOWN);
        key(Keys.ENTER);
        key(Keys.DOWN);
        key(Keys.ESCAPE);
        assertThat(picker.color()).isEqualTo(color.get());
        picker.color(Color.GREEN);
        picker.color(Color.BLUE);

        click(rebind);
        assertThat(rebind.isWaiting()).isTrue();
        key(Keys.K);
        assertThat(rebind.isWaiting()).isFalse();
        assertThat(game.engine().input().bindings().of(jump).get(1)).isEqualTo(Keys.K);

        float cx = stick.x() + stick.width() / 2f;
        float cy = stick.y() + stick.height() / 2f;
        press(cx, cy, 0);
        move(cx + stick.width(), cy);
        step(2);
        assertThat(stick.value().x()).isEqualTo(1f);
        assertThat(game.engine().input().strength(right)).isEqualTo(1f);
        release(cx + stick.width(), cy, 0);
        step(1);
        assertThat(game.engine().input().strength(right)).isZero();

        assertThat(rich.characterCount()).isGreaterThan(10);
        for (float x = rich.x(); x < rich.x() + rich.width() && links.isEmpty(); x += 8f) {
            click(x, rich.y() + rich.height() / 2f);
        }
        assertThat(links).containsExactly("shop");
        rich.on(NodeEvent.Click.class, e -> {});
    }
}
