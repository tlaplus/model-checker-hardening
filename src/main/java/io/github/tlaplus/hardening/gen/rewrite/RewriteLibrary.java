package io.github.tlaplus.hardening.gen.rewrite;

import at.forsyte.apalache.tla.lir.OperEx;
import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaModule;
import at.forsyte.apalache.tla.lir.TlaOperDecl;
import at.forsyte.apalache.tla.lir.oper.TlaOper;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apalache_mc.tla.jir.TlaModules;

/**
 * The rules of one rule module, in declaration order (ADR 0017 §1). Declaration order is part of
 * the byte encoding: a rewrite's two-byte index selects among the weighted slots of the rules that
 * apply at a node, in this order.
 *
 * <p>The library indexes the rules by the operator at the head of their pattern. A rule whose
 * pattern is a bare parameter or a literal is generic: it is a candidate at every node.
 */
public final class RewriteLibrary {
    /** The default weight of a rule that the configuration does not weigh. */
    public static final int DEFAULT_WEIGHT = 1;
    /** The largest weight of one rule, the same bound as for an expression form. */
    public static final int MAXIMUM_WEIGHT = IrGenerationConfig.MAXIMUM_FORM_WEIGHT;

    private static final RewriteLibrary EMPTY = new RewriteLibrary(List.of());

    private final List<RewriteRule> rules;
    private final Map<TlaOper, List<RewriteRule>> byHead = new IdentityHashMap<>();
    private final List<RewriteRule> generic = new ArrayList<>();

    private RewriteLibrary(List<RewriteRule> rules) {
        this.rules = List.copyOf(rules);
        var heads = Collections.newSetFromMap(new IdentityHashMap<TlaOper, Boolean>());
        for (var rule : this.rules) {
            if (rule.pattern() instanceof OperEx application) {
                heads.add(application.oper());
            }
        }
        for (var head : heads) {
            byHead.put(head, this.rules.stream()
                    .filter(rule -> !(rule.pattern() instanceof OperEx application) || application.oper() == head)
                    .toList());
        }
        this.rules.stream().filter(rule -> !(rule.pattern() instanceof OperEx)).forEach(generic::add);
    }

    public static RewriteLibrary empty() {
        return EMPTY;
    }

    /**
     * Reads the rules named {@code ruleNames} from a typed module; every other definition of the
     * module is a helper a replacement may apply.
     *
     * @param weights rule weights by name; a rule without one has {@link #DEFAULT_WEIGHT}
     * @throws IllegalArgumentException if a rule breaks the rule contract, a weight names no rule
     *     or is out of range, or no rule has a positive weight
     */
    public static RewriteLibrary fromModule(TlaModule module, Set<String> ruleNames, Map<String, Integer> weights) {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(ruleNames, "ruleNames");
        Objects.requireNonNull(weights, "weights");
        var definitions = new LinkedHashMap<String, TlaOperDecl>();
        for (var declaration : TlaModules.declarations(module)) {
            if (declaration instanceof TlaOperDecl definition) {
                definitions.put(definition.name(), definition);
            }
        }
        for (var name : ruleNames) {
            if (!definitions.containsKey(name)) {
                throw new IllegalArgumentException("rewrite rule " + name + " is not defined");
            }
        }
        for (var entry : weights.entrySet()) {
            if (!ruleNames.contains(entry.getKey())) {
                throw new IllegalArgumentException("weight names no rewrite rule: " + entry.getKey());
            }
            if (entry.getValue() < 0 || entry.getValue() > MAXIMUM_WEIGHT) {
                throw new IllegalArgumentException("weight of rewrite rule " + entry.getKey()
                        + " must be in the range 0.." + MAXIMUM_WEIGHT);
            }
        }
        var helpers = new HashSet<>(definitions.keySet());
        helpers.removeAll(ruleNames);
        var rules = new ArrayList<RewriteRule>();
        for (var definition : definitions.values()) {
            if (ruleNames.contains(definition.name())) {
                rules.add(RuleContract.rule(
                        definition, helpers, weights.getOrDefault(definition.name(), DEFAULT_WEIGHT)));
            }
        }
        if (!rules.isEmpty() && rules.stream().mapToInt(RewriteRule::weight).sum() == 0) {
            throw new IllegalArgumentException("at least one rewrite rule needs a positive weight");
        }
        return new RewriteLibrary(rules);
    }

    /** Returns every rule, in declaration order. */
    public List<RewriteRule> rules() {
        return rules;
    }

    public boolean isEmpty() {
        return rules.isEmpty();
    }

    /**
     * Returns the rules whose pattern may match {@code node}: those headed by the node's operator
     * and the generic ones, in declaration order.
     */
    public List<RewriteRule> candidates(TlaEx node) {
        if (node instanceof OperEx application) {
            var headed = byHead.get(application.oper());
            if (headed != null) {
                return headed;
            }
        }
        return Collections.unmodifiableList(generic);
    }
}
