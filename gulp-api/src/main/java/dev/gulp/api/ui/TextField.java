package dev.gulp.api.ui;

/**
 * One line of editable text; see {@link EditableText} for editing, validation and masking. Enter fires {@link
 * #onSubmit}. Created by {@link Ui#textField}. Theme type: {@code field}; variant {@code invalid} while validation
 * fails.
 *
 * <pre>{@code
 * State<String> name = State.of("");
 * textField().placeholder(tr("menu.name")).maxLength(16).bind(name).onSubmit(n -> start(n));
 * textField().password(true).placeholder("Password");
 * textField().filter(Character::isDigit).validate(s -> !s.isEmpty()).text("60");
 * }</pre>
 */
public final class TextField extends EditableText<TextField> {

    /** Creates an empty field. */
    public TextField() {
        super(false);
    }
}
