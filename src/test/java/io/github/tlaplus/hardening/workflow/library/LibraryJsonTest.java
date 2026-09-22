package io.github.tlaplus.hardening.workflow.library;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonParser;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LibraryJsonTest {
    @Test
    void keepsTheReachableOperatorsAndEveryOtherDeclaration() {
        var json = """
                {"name": "ApalacheIR", "modules": [{"kind": "TlaModule", "name": "M", "declarations": [
                  {"kind": "TlaOperDecl", "name": "Root", "body": {"kind": "OperEx", "oper": "OPER_APP",
                    "args": [{"kind": "NameEx", "name": "Helper"}]}},
                  {"kind": "TlaOperDecl", "name": "Helper", "body": {"kind": "LetInEx",
                    "decls": [{"kind": "TlaOperDecl", "name": "L", "body": {"kind": "NameEx", "name": "Deep"}}],
                    "body": {"kind": "ValEx"}}},
                  {"kind": "TlaOperDecl", "name": "Deep", "body": {"kind": "ValEx"}},
                  {"kind": "TlaOperDecl", "name": "Unused", "body": {"kind": "OperEx",
                    "oper": "ApalacheInternal!__ApalacheSeqCapacity", "args": []}},
                  {"kind": "TlaAssumeDecl", "body": {"kind": "ValEx"}}
                ]}]}
                """;
        var pruned = JsonParser.parseString(LibraryJson.prune(json, Set.of("Root")));
        var declarations = pruned.getAsJsonObject().getAsJsonArray("modules").get(0).getAsJsonObject()
                .getAsJsonArray("declarations");
        var kept = declarations.asList().stream()
                .map(declaration -> declaration.getAsJsonObject().has("name")
                        ? declaration.getAsJsonObject().get("name").getAsString()
                        : declaration.getAsJsonObject().get("kind").getAsString())
                .toList();
        assertEquals(List.of("Root", "Helper", "Deep", "TlaAssumeDecl"), kept);
    }
}
