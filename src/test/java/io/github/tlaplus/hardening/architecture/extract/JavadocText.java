package io.github.tlaplus.hardening.architecture.extract;

import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.EntityTree;
import com.sun.source.doctree.LinkTree;
import com.sun.source.doctree.LiteralTree;
import com.sun.source.doctree.TextTree;
import com.sun.source.util.DocTrees;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;
import javax.lang.model.element.Element;

/**
 * The text the atlas takes from Javadoc: a summary, which is the first sentence as plain text
 * with code in backticks, and the ADRs the comment mentions.
 */
final class JavadocText {
    private static final Pattern ADR = Pattern.compile("\\bADR (\\d{4})\\b");
    private static final Map<String, String> ENTITIES =
            Map.of("lt", "<", "gt", ">", "amp", "&", "nbsp", " ", "quot", "\"", "apos", "'");

    private final DocTrees trees;

    JavadocText(DocTrees trees) {
        this.trees = trees;
    }

    /** Whether the element has a doc comment. */
    boolean isDocumented(Element element) {
        return trees.getDocCommentTree(element) != null;
    }

    /** The first sentence of the element's doc comment, or the empty string. */
    String summary(Element element) {
        var comment = trees.getDocCommentTree(element);
        return comment == null ? "" : render(comment.getFirstSentence());
    }

    /** The four-digit numbers of the ADRs the element's doc comment mentions, in order. */
    List<String> adrs(Element element) {
        var comment = trees.getDocCommentTree(element);
        if (comment == null) {
            return List.of();
        }
        var numbers = new TreeSet<String>();
        var matcher = ADR.matcher(render(comment.getFullBody()));
        while (matcher.find()) {
            numbers.add(matcher.group(1));
        }
        return List.copyOf(numbers);
    }

    static String render(List<? extends DocTree> trees) {
        var text = new StringBuilder();
        for (var tree : trees) {
            switch (tree) {
                case TextTree t -> text.append(t.getBody());
                case LiteralTree t when t.getKind() == DocTree.Kind.CODE ->
                        text.append('`').append(t.getBody().getBody()).append('`');
                case LiteralTree t -> text.append(t.getBody().getBody());
                case LinkTree t when !t.getLabel().isEmpty() -> text.append(render(t.getLabel()));
                case LinkTree t -> text.append('`').append(simpleName(t.getReference().getSignature())).append('`');
                case EntityTree t -> text.append(ENTITIES.getOrDefault(t.getName().toString(), ""));
                default -> {
                    // HTML tags and other inline tags carry no text of their own.
                }
            }
        }
        return text.toString().replaceAll("\\s+", " ").strip();
    }

    /** {@code a.b.Type#member(int)} as {@code Type.member}, and {@code a.b.Type} as {@code Type}. */
    static String simpleName(String signature) {
        var hash = signature.indexOf('#');
        var type = hash < 0 ? signature : signature.substring(0, hash);
        type = type.substring(type.lastIndexOf('.') + 1);
        if (hash < 0) {
            return type;
        }
        var member = signature.substring(hash + 1);
        var paren = member.indexOf('(');
        member = paren < 0 ? member : member.substring(0, paren);
        return type.isEmpty() ? member : type + "." + member;
    }
}
