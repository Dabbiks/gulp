package dev.gulp.processor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Validation of module ids and dependency graphs, shared by the processor and its tests. */
final class ModuleIds {

    private ModuleIds() {}

    /**
     * Returns whether a string is a valid module id: non-empty, {@code [a-z0-9_.-]+}.
     *
     * @param id the candidate
     * @return {@code true} if valid
     */
    static boolean isValid(String id) {
        if (id.isEmpty()) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.' || c == '-')) {
                return false;
            }
        }
        return true;
    }

    /**
     * Finds a dependency cycle.
     *
     * @param graph module id to the ids it depends on (edges to unknown ids are ignored)
     * @return the cycle as a path that starts and ends with the same id, or {@code null} if there is none
     */
    static List<String> findCycle(Map<String, List<String>> graph) {
        Map<String, Integer> state = new HashMap<>(); // 1 = on stack, 2 = done
        List<String> stack = new ArrayList<>();
        for (String start : graph.keySet()) {
            List<String> cycle = visit(start, graph, state, stack);
            if (cycle != null) {
                return cycle;
            }
        }
        return null;
    }

    private static List<String> visit(
            String node, Map<String, List<String>> graph, Map<String, Integer> state, List<String> stack) {
        Integer current = state.get(node);
        if (current != null && current == 2) {
            return null;
        }
        if (current != null && current == 1) {
            List<String> cycle = new ArrayList<>(stack.subList(stack.indexOf(node), stack.size()));
            cycle.add(node);
            return cycle;
        }
        state.put(node, 1);
        stack.add(node);
        for (String next : graph.getOrDefault(node, List.of())) {
            if (graph.containsKey(next)) {
                List<String> cycle = visit(next, graph, state, stack);
                if (cycle != null) {
                    return cycle;
                }
            }
        }
        stack.removeLast();
        state.put(node, 2);
        return null;
    }
}
