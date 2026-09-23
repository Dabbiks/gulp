package dev.gulp.core.module;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Topological ordering of modules by their dependencies. */
public final class ModuleGraph {

    private ModuleGraph() {}

    /**
     * Orders modules so that every module comes after the modules it depends on. Among independent modules, the
     * declaration order is kept. Edges to unknown ids are ignored (they are reported elsewhere).
     *
     * @param dependencies module id to the ids it depends on (hard and present soft dependencies), in declaration
     *     order
     * @return the ids in load order
     * @throws IllegalStateException if there is a cycle, with the full path of the cycle
     */
    public static List<String> sort(Map<String, List<String>> dependencies) {
        List<String> order = new ArrayList<>();
        Map<String, Integer> state = new HashMap<>(); // 1 = visiting, 2 = done
        List<String> path = new ArrayList<>();
        for (String id : dependencies.keySet()) {
            visit(id, dependencies, state, path, order);
        }
        return order;
    }

    private static void visit(
            String id,
            Map<String, List<String>> dependencies,
            Map<String, Integer> state,
            List<String> path,
            List<String> order) {
        Integer current = state.get(id);
        if (current != null && current == 2) {
            return;
        }
        if (current != null && current == 1) {
            List<String> cycle = new ArrayList<>(path.subList(path.indexOf(id), path.size()));
            cycle.add(id);
            throw new IllegalStateException("Module dependency cycle: " + String.join(" -> ", cycle));
        }
        state.put(id, 1);
        path.add(id);
        for (String dependency : dependencies.getOrDefault(id, List.of())) {
            if (dependencies.containsKey(dependency)) {
                visit(dependency, dependencies, state, path, order);
            }
        }
        path.removeLast();
        state.put(id, 2);
        order.add(id);
    }
}
