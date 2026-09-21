package io.github.tlaplus.hardening.workflow.library;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Prunes typechecker JSON to the operator declarations reachable from the selected operators.
 *
 * <p>Apalache's JSON reader rejects a whole module when any declaration uses an operator it does
 * not know (apalache-json-003), so declarations nothing selected reaches are removed before
 * decoding. Reachability over-approximates: every {@code NameEx} counts, including bound names
 * that coincide with a declaration. Other declaration kinds are kept, so validation still sees
 * them.
 */
final class LibraryJson {
    private static final String KIND = "kind";
    private static final String NAME = "name";
    private static final String OPERATOR_DECLARATION = "TlaOperDecl";

    private LibraryJson() {}

    static String prune(String json, Set<String> roots) {
        var document = JsonParser.parseString(json).getAsJsonObject();
        for (var module : document.getAsJsonArray("modules")) {
            var declarations = module.getAsJsonObject().getAsJsonArray("declarations");
            var operators = new HashMap<String, JsonObject>();
            for (var declaration : declarations) {
                var object = declaration.getAsJsonObject();
                if (isOperator(object)) operators.put(object.get(NAME).getAsString(), object);
            }
            var reached = reachable(operators, roots);
            var kept = new JsonArray();
            for (var declaration : declarations) {
                var object = declaration.getAsJsonObject();
                if (!isOperator(object) || reached.contains(object.get(NAME).getAsString())) kept.add(object);
            }
            module.getAsJsonObject().add("declarations", kept);
        }
        return document.toString();
    }

    private static boolean isOperator(JsonObject declaration) {
        return declaration.has(KIND) && OPERATOR_DECLARATION.equals(declaration.get(KIND).getAsString());
    }

    private static Set<String> reachable(Map<String, JsonObject> operators, Set<String> roots) {
        var reached = new HashSet<String>();
        var pending = new ArrayDeque<>(roots);
        while (!pending.isEmpty()) {
            var name = pending.pop();
            var declaration = operators.get(name);
            if (declaration == null || !reached.add(name)) continue;
            collectNames(declaration.get("body"), pending);
        }
        return reached;
    }

    private static void collectNames(JsonElement element, ArrayDeque<String> names) {
        if (element == null) return;
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> collectNames(child, names));
        } else if (element.isJsonObject()) {
            var object = element.getAsJsonObject();
            if (object.has(KIND) && "NameEx".equals(object.get(KIND).getAsString())) {
                names.push(object.get(NAME).getAsString());
            }
            object.entrySet().forEach(entry -> collectNames(entry.getValue(), names));
        }
    }
}
