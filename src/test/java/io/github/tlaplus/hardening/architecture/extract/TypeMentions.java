package io.github.tlaplus.hardening.architecture.extract;

import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.UnionType;
import javax.lang.model.type.WildcardType;

/** The top-level types that a type mentions, including inside its type arguments. */
final class TypeMentions {
    private TypeMentions() {}

    /** Adds the qualified names of the top-level types {@code type} mentions to {@code out}. */
    static void collect(TypeMirror type, Set<String> out) {
        switch (type) {
            case DeclaredType declared -> {
                out.add(topLevel(declared.asElement()).getQualifiedName().toString());
                declared.getTypeArguments().forEach(argument -> collect(argument, out));
            }
            case ArrayType array -> collect(array.getComponentType(), out);
            case WildcardType wildcard -> {
                if (wildcard.getExtendsBound() != null) {
                    collect(wildcard.getExtendsBound(), out);
                }
                if (wildcard.getSuperBound() != null) {
                    collect(wildcard.getSuperBound(), out);
                }
            }
            case IntersectionType intersection -> intersection.getBounds().forEach(b -> collect(b, out));
            case UnionType union -> union.getAlternatives().forEach(a -> collect(a, out));
            default -> {
                // Primitives, void, and type variables mention no type; a variable's bounds
                // belong to its declaration.
            }
        }
    }

    /** The top-level type that declares {@code element}, or the element itself. */
    static TypeElement topLevel(Element element) {
        var current = element;
        while (!(current.getEnclosingElement() instanceof PackageElement)) {
            current = current.getEnclosingElement();
        }
        return (TypeElement) current;
    }
}
