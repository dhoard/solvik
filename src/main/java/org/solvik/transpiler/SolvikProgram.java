package org.solvik.transpiler;

import java.util.List;

/**
 * Backend-neutral typed Solvik program IR (declarations).
 *
 * <p>Carries resolved types, resolved signatures (including whether a slot must
 * use the boxed/reference spelling), lowered method bodies, and lowered field
 * initializers/static blocks. The Java backend renders Java from it.</p>
 */
public final class SolvikProgram {
    public record TypeParameter(String name, List<TypeModel.Type> bounds) {}
    public record Parameter(String name, TypeModel.Type type, boolean variadic, boolean boxed) {}
    public record Method(String name, List<Parameter> params, TypeModel.Type returnType, boolean returnBoxed,
                         boolean isPublic, boolean instance, List<TypeParameter> typeParameters, List<SolvikStmt> body) {}
    public record Field(String name, TypeModel.Type type, boolean mutable, boolean isStatic, SolvikIr initializer) {}
    public record Struct(String name, List<TypeParameter> typeParameters, List<Field> fields, List<Method> methods,
                         List<SolvikStmt> staticBlock, List<TypeModel.Type> implementsTypes, int index) {}
    public record Interface(String name, List<TypeParameter> typeParameters, List<TypeModel.Type> extendsTypes,
                            List<Method> methods) {}
    public record Variant(String name, TypeModel.Type payload) {}
    public record Enum(String name, List<TypeParameter> typeParameters, List<Variant> variants) {}

    private final List<Interface> interfaces;
    private final List<Enum> enums;
    private final List<Struct> structs;

    public SolvikProgram(List<Interface> interfaces, List<Enum> enums, List<Struct> structs) {
        this.interfaces = List.copyOf(interfaces);
        this.enums = List.copyOf(enums);
        this.structs = List.copyOf(structs);
    }

    public List<Interface> interfaces() { return interfaces; }
    public List<Enum> enums() { return enums; }
    public List<Struct> structs() { return structs; }
}
