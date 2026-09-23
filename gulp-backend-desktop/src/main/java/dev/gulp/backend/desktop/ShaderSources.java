package dev.gulp.backend.desktop;

import java.util.regex.Pattern;

/**
 * Translates GLSL ES 3.00 sources, the engine's shader language, to desktop GLSL 3.30 core: replaces the
 * {@code #version} header and removes precision statements and qualifiers.
 */
final class ShaderSources {

    private static final Pattern VERSION = Pattern.compile("^\\s*#version\\s+300\\s+es\\b.*$", Pattern.MULTILINE);
    private static final Pattern PRECISION_STATEMENT =
            Pattern.compile("^\\s*precision\\s+(lowp|mediump|highp)\\s+\\w+\\s*;[ \\t]*\\R?", Pattern.MULTILINE);
    private static final Pattern PRECISION_QUALIFIER = Pattern.compile("\\b(lowp|mediump|highp)\\s+");

    private ShaderSources() {}

    static String toDesktop(String glslEs) {
        String source = VERSION.matcher(glslEs).replaceFirst("#version 330 core");
        source = PRECISION_STATEMENT.matcher(source).replaceAll("");
        return PRECISION_QUALIFIER.matcher(source).replaceAll("");
    }
}
