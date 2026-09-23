package dev.gulp.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

/**
 * Plugin {@code dev.gulp.game}. In stage 0 it only registers the {@code gulp} extension so game builds can already
 * declare it; tasks are added from stage 3.
 *
 * <pre>{@code
 * plugins { id("dev.gulp.game") version "0.1.0" }
 * }</pre>
 */
public final class GulpGamePlugin implements Plugin<Project> {

    /** Creates the plugin; instantiated by Gradle. */
    public GulpGamePlugin() {}

    @Override
    public void apply(Project project) {
        project.getExtensions().create("gulp", GulpExtension.class);
    }
}
