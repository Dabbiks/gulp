package dev.gulp.processor;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;

/**
 * Generates, at compile time, everything the engine would otherwise need reflection for:
 *
 * <ul>
 *   <li>{@code <Listener>$Handlers} — direct dispatch for each {@code @EventHandler} method of a listener class;
 *   <li>module descriptors from {@code @ModuleInfo}, with validation of ids and dependency cycles;
 *   <li>{@code <Record>$Codec} — a {@code Codec} for each {@code @Serializable} record.
 * </ul>
 *
 * <p>Per package with annotated types, a public {@code GulpIndex_<hash>} class implementing
 * {@code dev.gulp.api.spi.GeneratedIndex} registers the generated code; all of them are listed in
 * {@code META-INF/services/dev.gulp.api.spi.GeneratedIndex}. Per-package indexes can reach package-private listeners,
 * modules and records.
 */
public final class GulpProcessor extends AbstractProcessor {

    static final String EVENT_HANDLER = "dev.gulp.api.event.EventHandler";
    static final String MODULE_INFO = "dev.gulp.api.module.ModuleInfo";
    static final String SERIALIZABLE = "dev.gulp.api.data.Serializable";
    static final String LISTENER = "dev.gulp.api.event.Listener";
    static final String EVENT = "dev.gulp.api.event.Event";
    static final String GAME_MODULE = "dev.gulp.api.module.GameModule";

    private Elements elements;
    private Types types;
    private Filer filer;
    private Messager messager;

    /** Registrations per package, accumulated over rounds: package name → lines of the index body. */
    private final Map<String, List<String>> pendingByPackage = new TreeMap<>();

    private final Map<String, ModuleEntry> modulesById = new LinkedHashMap<>();
    private final Set<String> generatedTypes = new HashSet<>();
    private boolean indexWritten;
    private int sourcesWritten;

    /** Creates the processor; instantiated by javac. */
    public GulpProcessor() {}

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        elements = processingEnv.getElementUtils();
        types = processingEnv.getTypeUtils();
        filer = processingEnv.getFiler();
        messager = processingEnv.getMessager();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(EVENT_HANDLER, MODULE_INFO, SERIALIZABLE);
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        int sourcesBefore = sourcesWritten;
        TypeElement handlerAnnotation = elements.getTypeElement(EVENT_HANDLER);
        TypeElement moduleAnnotation = elements.getTypeElement(MODULE_INFO);
        TypeElement serializableAnnotation = elements.getTypeElement(SERIALIZABLE);

        if (handlerAnnotation != null) {
            Map<TypeElement, List<ExecutableElement>> byListener = new LinkedHashMap<>();
            for (Element element : round.getElementsAnnotatedWith(handlerAnnotation)) {
                if (element.getKind() != ElementKind.METHOD) {
                    continue;
                }
                ExecutableElement method = (ExecutableElement) element;
                byListener
                        .computeIfAbsent((TypeElement) method.getEnclosingElement(), k -> new ArrayList<>())
                        .add(method);
            }
            for (Map.Entry<TypeElement, List<ExecutableElement>> entry : byListener.entrySet()) {
                generateHandlers(entry.getKey(), entry.getValue());
            }
        }
        if (moduleAnnotation != null) {
            for (Element element : round.getElementsAnnotatedWith(moduleAnnotation)) {
                registerModule((TypeElement) element);
            }
            validateModuleGraph();
        }
        if (serializableAnnotation != null) {
            for (Element element : round.getElementsAnnotatedWith(serializableAnnotation)) {
                generateCodec((TypeElement) element);
            }
        }

        // Write the indexes in the first round that generated no other sources (registrations alone do not start
        // another round, so waiting for an idle round would lose a module-only index); generating sources in the
        // last round would make javac warn, and that breaks -Werror builds.
        if (sourcesWritten == sourcesBefore
                && !round.processingOver()
                && !indexWritten
                && !pendingByPackage.isEmpty()) {
            writeIndexes();
            indexWritten = true;
        }
        return true;
    }

    // ---------------------------------------------------------------- listeners

    private boolean generateHandlers(TypeElement listener, List<ExecutableElement> methods) {
        String listenerName = listener.getQualifiedName().toString();
        if (!generatedTypes.add("handlers:" + listenerName)) {
            return false;
        }
        TypeMirror listenerType = type(LISTENER);
        TypeMirror eventType = type(EVENT);
        boolean valid = true;
        if (listenerType == null || !types.isAssignable(types.erasure(listener.asType()), listenerType)) {
            error(listener, "Classes with @EventHandler methods must implement dev.gulp.api.event.Listener");
            valid = false;
        }
        valid &= checkAccessible(listener, "Listener");
        List<String> registrations = new ArrayList<>();
        for (ExecutableElement method : methods) {
            if (method.getModifiers().contains(Modifier.PRIVATE)
                    || method.getModifiers().contains(Modifier.STATIC)) {
                error(method, "@EventHandler methods must be neither private nor static");
                valid = false;
                continue;
            }
            if (method.getParameters().size() != 1) {
                error(method, "@EventHandler methods take exactly one parameter: the event");
                valid = false;
                continue;
            }
            TypeMirror parameter =
                    types.erasure(method.getParameters().getFirst().asType());
            if (eventType == null || !types.isAssignable(parameter, eventType)) {
                error(
                        method,
                        "The parameter of an @EventHandler method must be a subclass of dev.gulp.api.event.Event");
                valid = false;
                continue;
            }
            AnnotationMirror annotation = annotation(method, EVENT_HANDLER);
            String priority = "NORMAL";
            boolean ignoreCancelled = false;
            if (annotation != null) {
                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> value :
                        elements.getElementValuesWithDefaults(annotation).entrySet()) {
                    String name = value.getKey().getSimpleName().toString();
                    if (name.equals("priority")) {
                        priority = ((VariableElement) value.getValue().getValue())
                                .getSimpleName()
                                .toString();
                    } else if (name.equals("ignoreCancelled")) {
                        ignoreCancelled = (Boolean) value.getValue().getValue();
                    }
                }
            }
            registrations.add(
                    "        sink.handler(" + parameter + ".class, dev.gulp.api.event.EventPriority." + priority + ", "
                            + ignoreCancelled + ", event -> listener." + method.getSimpleName() + "(event));");
        }
        if (!valid) {
            return false;
        }
        String packageName = packageOf(listener);
        String generated = binaryTail(listener) + "$Handlers";
        String listenerRef = sourceName(listener);
        StringBuilder source = new StringBuilder();
        header(source, packageName);
        source.append("/** Generated dispatcher for {@link ")
                .append(listenerRef)
                .append("}. Do not edit. */\n")
                .append("@SuppressWarnings({\"unchecked\", \"rawtypes\"})\n")
                .append("public final class ")
                .append(generated)
                .append(" implements dev.gulp.api.spi.ListenerHandlers<")
                .append(listenerRef)
                .append("> {\n\n")
                .append("    /** Creates the dispatcher. */\n    public ")
                .append(generated)
                .append("() {}\n\n")
                .append("    @Override\n    public void register(")
                .append(listenerRef)
                .append(" listener, dev.gulp.api.spi.ListenerHandlers.HandlerSink sink) {\n");
        for (String registration : registrations) {
            source.append(registration).append('\n');
        }
        source.append("    }\n}\n");
        writeSource(qualified(packageName, generated), source, listener);
        pending(packageName).add("        sink.listener(" + listenerRef + ".class, new " + generated + "());");
        return true;
    }

    // ---------------------------------------------------------------- modules

    private record ModuleEntry(TypeElement type, String id, List<String> dependsOn, List<String> softDependsOn) {}

    private boolean registerModule(TypeElement module) {
        String moduleName = module.getQualifiedName().toString();
        if (!generatedTypes.add("module:" + moduleName)) {
            return false;
        }
        TypeMirror gameModule = type(GAME_MODULE);
        boolean valid = true;
        if (gameModule == null || !types.isAssignable(types.erasure(module.asType()), gameModule)) {
            error(module, "@ModuleInfo can only be placed on subclasses of dev.gulp.api.module.GameModule");
            valid = false;
        }
        if (module.getModifiers().contains(Modifier.ABSTRACT)) {
            error(module, "A module annotated with @ModuleInfo must not be abstract");
            valid = false;
        }
        valid &= checkAccessible(module, "Module");
        AnnotationMirror annotation = annotation(module, MODULE_INFO);
        String id = "";
        List<String> dependsOn = new ArrayList<>();
        List<String> softDependsOn = new ArrayList<>();
        boolean enabledByDefault = true;
        if (annotation != null) {
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> value :
                    elements.getElementValuesWithDefaults(annotation).entrySet()) {
                String name = value.getKey().getSimpleName().toString();
                switch (name) {
                    case "id" -> id = (String) value.getValue().getValue();
                    case "dependsOn" -> dependsOn = strings(value.getValue());
                    case "softDependsOn" -> softDependsOn = strings(value.getValue());
                    case "enabledByDefault" ->
                        enabledByDefault = (Boolean) value.getValue().getValue();
                    default -> {}
                }
            }
        }
        if (!ModuleIds.isValid(id)) {
            error(module, "Invalid module id '" + id + "': use lower case letters, digits, '_', '.', '-'");
            valid = false;
        }
        for (String dependency : concat(dependsOn, softDependsOn)) {
            if (dependency.equals(id)) {
                error(module, "Module '" + id + "' cannot depend on itself");
                valid = false;
            } else if (!ModuleIds.isValid(dependency)) {
                error(module, "Invalid dependency id '" + dependency + "'");
                valid = false;
            }
        }
        ModuleEntry existing = modulesById.get(id);
        if (existing != null) {
            error(
                    module,
                    "Duplicate module id '" + id + "', also used by "
                            + existing.type().getQualifiedName());
            valid = false;
        }
        if (!valid) {
            return false;
        }
        modulesById.put(id, new ModuleEntry(module, id, dependsOn, softDependsOn));
        pending(packageOf(module))
                .add("        sink.module(" + sourceName(module) + ".class, new dev.gulp.api.spi.ModuleDescriptor("
                        + literal(id) + ", " + listLiteral(dependsOn) + ", " + listLiteral(softDependsOn) + ", "
                        + enabledByDefault + "));");
        return true;
    }

    private void validateModuleGraph() {
        Map<String, List<String>> graph = new LinkedHashMap<>();
        for (ModuleEntry entry : modulesById.values()) {
            List<String> edges = new ArrayList<>();
            for (String dependency : concat(entry.dependsOn(), entry.softDependsOn())) {
                if (modulesById.containsKey(dependency)) {
                    edges.add(dependency);
                }
            }
            graph.put(entry.id(), edges);
        }
        List<String> cycle = ModuleIds.findCycle(graph);
        if (cycle != null) {
            ModuleEntry first = modulesById.get(cycle.getFirst());
            error(first.type(), "Module dependency cycle: " + String.join(" -> ", cycle));
            // Report once: remove the edge so later rounds do not repeat the error.
            modulesById.remove(cycle.getFirst());
        }
    }

    // ---------------------------------------------------------------- codecs

    private boolean generateCodec(TypeElement record) {
        String recordName = record.getQualifiedName().toString();
        if (!generatedTypes.add("codec:" + recordName)) {
            return false;
        }
        if (record.getKind() != ElementKind.RECORD) {
            error(record, "@Serializable is supported on records only");
            return false;
        }
        if (!record.getTypeParameters().isEmpty()) {
            error(record, "@Serializable records must not be generic");
            return false;
        }
        if (!checkAccessible(record, "Serializable record")) {
            return false;
        }
        String recordRef = sourceName(record);
        List<String> fields = new ArrayList<>();
        List<String> encode = new ArrayList<>();
        List<String> decodeLocals = new ArrayList<>();
        List<String> constructorArgs = new ArrayList<>();
        boolean valid = true;
        int index = 0;
        for (RecordComponentElement component : record.getRecordComponents()) {
            String name = component.getSimpleName().toString();
            String codec = codecExpression(component.asType(), component);
            if (codec == null) {
                valid = false;
                continue;
            }
            boolean nullable =
                    isNullable(component) && !component.asType().getKind().isPrimitive();
            String field = "C" + index++;
            fields.add("    private static final dev.gulp.api.data.Codec " + field + " = " + codec
                    + (nullable ? ".nullable()" : "") + ";");
            encode.add("        json.put(" + literal(name) + ", " + field + ".encode(value." + name + "()));");
            String boxed = boxedName(component.asType());
            String local = "v" + index;
            if (nullable) {
                decodeLocals.add("        dev.gulp.api.data.JsonValue " + local + "Json = object.get(" + literal(name)
                        + ");\n        " + boxed + " " + local + " = " + local + "Json == null ? null : ("
                        + boxed + ") decode(" + field + ", " + local + "Json, " + literal(name) + ");");
            } else {
                decodeLocals.add("        " + boxed + " " + local + " = (" + boxed + ") decode(" + field
                        + ", member(object, " + literal(name) + "), " + literal(name) + ");");
            }
            constructorArgs.add(local);
        }
        if (!valid) {
            return false;
        }
        String packageName = packageOf(record);
        String generated = binaryTail(record) + "$Codec";
        StringBuilder source = new StringBuilder();
        header(source, packageName);
        source.append("/** Generated codec for {@link ")
                .append(recordRef)
                .append("}. Do not edit. */\n")
                .append("@SuppressWarnings({\"unchecked\", \"rawtypes\"})\n")
                .append("public final class ")
                .append(generated)
                .append(" implements dev.gulp.api.data.Codec<")
                .append(recordRef)
                .append("> {\n\n");
        for (String field : fields) {
            source.append(field).append('\n');
        }
        source.append("\n    /** Creates the codec. */\n    public ")
                .append(generated)
                .append("() {}\n\n")
                .append("    @Override\n    public dev.gulp.api.data.JsonValue encode(")
                .append(recordRef)
                .append(" value) {\n")
                .append(
                        "        dev.gulp.api.data.JsonObject.Builder json = dev.gulp.api.data.JsonObject.builder();\n");
        for (String line : encode) {
            source.append(line).append('\n');
        }
        source.append("        return json.build();\n    }\n\n")
                .append("    @Override\n    public ")
                .append(recordRef)
                .append(" decode(dev.gulp.api.data.JsonValue json) {\n")
                .append("        if (!(json instanceof dev.gulp.api.data.JsonObject object)) {\n")
                .append("            throw new dev.gulp.api.data.CodecException(\"Expected a JSON object for ")
                .append(record.getSimpleName())
                .append(" but found \" + json);\n        }\n");
        for (String line : decodeLocals) {
            source.append(line).append('\n');
        }
        source.append("        return new ")
                .append(recordRef)
                .append("(")
                .append(String.join(", ", constructorArgs))
                .append(");\n    }\n\n")
                .append("    private static dev.gulp.api.data.JsonValue member(dev.gulp.api.data.JsonObject object,"
                        + " String name) {\n")
                .append("        dev.gulp.api.data.JsonValue value = object.get(name);\n")
                .append("        if (value == null) {\n")
                .append("            throw new dev.gulp.api.data.CodecException(\"Missing member '\" + name +"
                        + " \"'\").at(\".\" + name);\n")
                .append("        }\n        return value;\n    }\n\n")
                .append("    private static Object decode(dev.gulp.api.data.Codec codec, dev.gulp.api.data.JsonValue"
                        + " json, String name) {\n")
                .append("        try {\n            return codec.decode(json);\n")
                .append("        } catch (dev.gulp.api.data.CodecException e) {\n")
                .append("            throw e.at(\".\" + name);\n        }\n    }\n}\n");
        writeSource(qualified(packageName, generated), source, record);
        pending(packageName).add("        sink.codec(" + recordRef + ".class, new " + generated + "());");
        return true;
    }

    private String codecExpression(TypeMirror type, Element where) {
        switch (type.getKind()) {
            case BOOLEAN:
                return "dev.gulp.api.data.Codec.BOOLEAN";
            case INT:
                return "dev.gulp.api.data.Codec.INT";
            case LONG:
                return "dev.gulp.api.data.Codec.LONG";
            case FLOAT:
                return "dev.gulp.api.data.Codec.FLOAT";
            case DOUBLE:
                return "dev.gulp.api.data.Codec.DOUBLE";
            case DECLARED:
                break;
            default:
                error(where, "Unsupported type in @Serializable record: " + type);
                return null;
        }
        DeclaredType declared = (DeclaredType) type;
        TypeElement element = (TypeElement) declared.asElement();
        String name = element.getQualifiedName().toString();
        switch (name) {
            case "java.lang.Boolean":
                return "dev.gulp.api.data.Codec.BOOLEAN";
            case "java.lang.Integer":
                return "dev.gulp.api.data.Codec.INT";
            case "java.lang.Long":
                return "dev.gulp.api.data.Codec.LONG";
            case "java.lang.Float":
                return "dev.gulp.api.data.Codec.FLOAT";
            case "java.lang.Double":
                return "dev.gulp.api.data.Codec.DOUBLE";
            case "java.lang.String":
                return "dev.gulp.api.data.Codec.STRING";
            case "dev.gulp.api.registry.Key":
                return "dev.gulp.api.data.Codec.KEY";
            case "dev.gulp.api.data.JsonValue":
                return "dev.gulp.api.data.Codec.JSON";
            case "java.util.List": {
                String element0 = codecExpression(declared.getTypeArguments().getFirst(), where);
                return element0 == null ? null : "dev.gulp.api.data.Codec.listOf(" + element0 + ")";
            }
            case "java.util.Map": {
                TypeMirror keyType = declared.getTypeArguments().getFirst();
                if (!keyType.toString().equals("java.lang.String")) {
                    error(where, "Maps in @Serializable records must have String keys, found " + keyType);
                    return null;
                }
                String value = codecExpression(declared.getTypeArguments().get(1), where);
                return value == null ? null : "dev.gulp.api.data.Codec.mapOf(" + value + ")";
            }
            default:
                break;
        }
        if (element.getKind() == ElementKind.ENUM) {
            return "dev.gulp.api.data.Codec.enumOf(" + sourceName(element) + ".values())";
        }
        if (element.getKind() == ElementKind.RECORD && annotation(element, SERIALIZABLE) != null) {
            String ref = sourceName(element);
            // Lazy: the nested record's codec may be registered by an index that is not loaded yet.
            return "dev.gulp.api.data.Codec.lazy(() -> dev.gulp.api.data.Codec.of(" + ref + ".class))";
        }
        error(
                where,
                "Unsupported type in @Serializable record: " + type
                        + " (use primitives, String, Key, enums, JsonValue, List, Map<String, ?> or @Serializable records)");
        return null;
    }

    private String boxedName(TypeMirror type) {
        if (type.getKind().isPrimitive()) {
            return types.boxedClass((javax.lang.model.type.PrimitiveType) type)
                    .getQualifiedName()
                    .toString();
        }
        return types.erasure(type).toString();
    }

    private static boolean isNullable(RecordComponentElement component) {
        for (AnnotationMirror mirror : component.getAnnotationMirrors()) {
            if (mirror.getAnnotationType().asElement().getSimpleName().contentEquals("Nullable")) {
                return true;
            }
        }
        for (AnnotationMirror mirror : component.asType().getAnnotationMirrors()) {
            if (mirror.getAnnotationType().asElement().getSimpleName().contentEquals("Nullable")) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- index

    private void writeIndexes() {
        List<String> indexClasses = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : pendingByPackage.entrySet()) {
            String packageName = entry.getKey();
            List<String> lines = entry.getValue();
            String name = "GulpIndex_" + Integer.toHexString(new TreeSet<>(lines).hashCode() & 0x7fffffff);
            StringBuilder source = new StringBuilder();
            header(source, packageName);
            source.append("/** Generated index of package ")
                    .append(packageName.isEmpty() ? "(default)" : packageName)
                    .append(". Do not edit. */\n")
                    .append("@SuppressWarnings({\"unchecked\", \"rawtypes\"})\n")
                    .append("public final class ")
                    .append(name)
                    .append(" implements dev.gulp.api.spi.GeneratedIndex {\n\n")
                    .append("    /** Creates the index; instantiated by ServiceLoader. */\n    public ")
                    .append(name)
                    .append("() {}\n\n")
                    .append("    @Override\n    public void register(dev.gulp.api.spi.GeneratedIndex.Sink sink) {\n");
            for (String line : lines) {
                source.append(line).append('\n');
            }
            source.append("    }\n}\n");
            String qualifiedName = qualified(packageName, name);
            writeSource(qualifiedName, source, null);
            indexClasses.add(qualifiedName);
        }
        try {
            var resource = filer.createResource(
                    StandardLocation.CLASS_OUTPUT, "", "META-INF/services/dev.gulp.api.spi.GeneratedIndex");
            try (Writer writer = resource.openWriter()) {
                for (String indexClass : indexClasses) {
                    writer.write(indexClass);
                    writer.write('\n');
                }
            }
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Could not write GeneratedIndex service file: " + e);
        }
    }

    private List<String> pending(String packageName) {
        return pendingByPackage.computeIfAbsent(packageName, k -> new ArrayList<>());
    }

    // ---------------------------------------------------------------- helpers

    private boolean checkAccessible(TypeElement type, String what) {
        for (Element e = type; e instanceof TypeElement t; e = e.getEnclosingElement()) {
            if (t.getNestingKind() == NestingKind.LOCAL || t.getNestingKind() == NestingKind.ANONYMOUS) {
                error(type, what + " classes must not be local or anonymous; use lambdas with on(...) instead");
                return false;
            }
            if (t.getModifiers().contains(Modifier.PRIVATE)) {
                error(type, what + " classes and their enclosing classes must not be private");
                return false;
            }
        }
        return true;
    }

    private void writeSource(String qualifiedName, CharSequence source, Element origin) {
        try {
            var file = origin == null
                    ? filer.createSourceFile(qualifiedName)
                    : filer.createSourceFile(qualifiedName, origin);
            try (Writer writer = file.openWriter()) {
                writer.write(source.toString());
            }
            sourcesWritten++;
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Could not write " + qualifiedName + ": " + e, origin);
        }
    }

    private static void header(StringBuilder source, String packageName) {
        if (!packageName.isEmpty()) {
            source.append("package ").append(packageName).append(";\n\n");
        }
    }

    private String packageOf(TypeElement type) {
        PackageElement pkg = elements.getPackageOf(type);
        return pkg.isUnnamed() ? "" : pkg.getQualifiedName().toString();
    }

    /** Name of a possibly nested type relative to its package, joined with {@code $}: {@code Outer$Inner}. */
    private String binaryTail(TypeElement type) {
        String packageName = packageOf(type);
        String qualified = type.getQualifiedName().toString();
        String tail = packageName.isEmpty() ? qualified : qualified.substring(packageName.length() + 1);
        return tail.replace('.', '$');
    }

    private static String sourceName(TypeElement type) {
        return type.getQualifiedName().toString();
    }

    private static String qualified(String packageName, String simpleName) {
        return packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
    }

    private TypeMirror type(String name) {
        TypeElement element = elements.getTypeElement(name);
        return element == null ? null : types.erasure(element.asType());
    }

    private static AnnotationMirror annotation(Element element, String annotationName) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            TypeElement type = (TypeElement) mirror.getAnnotationType().asElement();
            if (type.getQualifiedName().contentEquals(annotationName)) {
                return mirror;
            }
        }
        return null;
    }

    private static List<String> strings(AnnotationValue value) {
        List<String> result = new ArrayList<>();
        Object raw = value.getValue();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                result.add((String) ((AnnotationValue) item).getValue());
            }
        }
        return result;
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    private static String literal(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                default -> out.append(c);
            }
        }
        return out.append('"').toString();
    }

    private static String listLiteral(List<String> items) {
        List<String> literals = new ArrayList<>();
        for (String item : items) {
            literals.add(literal(item));
        }
        return "java.util.List.of(" + String.join(", ", literals) + ")";
    }

    private void error(Element element, String message) {
        messager.printMessage(Diagnostic.Kind.ERROR, message, element);
    }
}
