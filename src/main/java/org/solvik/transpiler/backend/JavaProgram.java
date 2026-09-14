package org.solvik.transpiler.backend;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.solvik.transpiler.SolvikIr;
import org.solvik.transpiler.SolvikProgram;
import org.solvik.transpiler.SolvikStmt;
import org.solvik.transpiler.TypeModel;

/**
 * The Java backend's compilation unit: the optimized Solvik IR together with
 * every Java representation decision the lowerer has made.
 *
 * <p>Name mangling, generated type spellings, the trivial-constructor table, and
 * the set of required {@link RuntimeFeature runtime facilities} live here rather
 * than in the emitter. The set is filled by {@link JavaLowerer} at the moment it
 * selects a helper or a runtime type, so reachability is a compiler decision and
 * never a scan over rendered text.</p>
 */
public final class JavaProgram {
    private final SolvikProgram program;
    private final String outputClass;
    private final String sourceName;
    private final Map<String, String> structNames = new HashMap<>();
    private final Map<String, String> traitNames = new HashMap<>();
    private final Map<String, String> enumNames = new HashMap<>();
    private final Map<String, SolvikProgram.Enum> enums = new HashMap<>();
    private final Map<String, SolvikProgram.Struct> structs = new HashMap<>();
    private final Map<String, SolvikProgram.Trait> traits = new HashMap<>();
    private final Map<String, int[]> trivialFactories = new HashMap<>();
    private final EnumSet<RuntimeFeature> features = EnumSet.noneOf(RuntimeFeature.class);

    public JavaProgram(SolvikProgram program, String outputClass, String sourceName) {
        this.program = program;
        this.outputClass = outputClass;
        this.sourceName = sourceName;
        for (SolvikProgram.Struct struct : program.structs()) { structNames.put(struct.name(), "__S_" + struct.name()); structs.put(struct.name(), struct); }
        for (SolvikProgram.Trait trait : program.traits()) { traitNames.put(trait.name(), "__I_" + trait.name()); traits.put(trait.name(), trait); }
        for (SolvikProgram.Enum enumeration : program.enums()) { enumNames.put(enumeration.name(), "__E_" + enumeration.name()); enums.put(enumeration.name(), enumeration); }
        for (SolvikProgram.Struct struct : program.structs()) indexTrivialFactory(struct);
    }

    /**
     * Records structs whose {@code new} factory is a pure field permutation of
     * its parameters ({@code new(a, b) { return Self { x: a, y: b } }}). Those
     * calls lower straight to the generated all-fields constructor instead of
     * routing through the synthetic Java factory method.
     */
    private void indexTrivialFactory(SolvikProgram.Struct struct) {
        SolvikProgram.Method factory = null;
        for (SolvikProgram.Method method : struct.methods()) {
            if (method.name().equals("new") && !method.instance() && method.body() != null) { factory = method; break; }
        }
        if (factory == null || factory.body().size() != 1) return;
        if (!(factory.body().get(0) instanceof SolvikStmt.Return result)) return;
        if (!(result.value() instanceof SolvikIr.SelfInit init)) return;
        if (init.values().size() != factory.params().size()) return;
        int[] map = new int[init.values().size()];
        boolean[] used = new boolean[factory.params().size()];
        for (int i = 0; i < init.values().size(); i++) {
            if (!(init.values().get(i) instanceof SolvikIr.Local local)) return;
            int parameter = -1;
            for (int p = 0; p < factory.params().size(); p++) if (factory.params().get(p).name().equals(local.name())) { parameter = p; break; }
            if (parameter < 0 || used[parameter]) return;
            used[parameter] = true;
            map[i] = parameter;
        }
        trivialFactories.put(struct.name(), map);
    }

    public SolvikProgram program() { return program; }
    public String outputClass() { return outputClass; }
    public String sourceName() { return sourceName; }

    public String structName(String name) { return structNames.get(name); }
    public String traitName(String name) { return traitNames.get(name); }
    public String enumName(String name) { return enumNames.get(name); }
    public SolvikProgram.Enum enumDecl(String name) { return enums.get(name); }
    public SolvikProgram.Struct structDecl(String name) { return structs.get(name); }
    public SolvikProgram.Trait traitDecl(String name) { return traits.get(name); }
    public String generatedTypeName(TypeModel.Type type) {
        return switch (type.base()) {
            case STRUCT -> structNames.get(type.name());
            case TRAIT -> traitNames.get(type.name());
            case ENUM -> enumNames.get(type.name());
            default -> type.name();
        };
    }

    public void registerTrivialFactory(String structName, int[] fieldToParameter) { trivialFactories.put(structName, fieldToParameter); }
    public int[] trivialFactory(String structName) { return trivialFactories.get(structName); }

    /** Records that a runtime facility is required. */
    public void require(RuntimeFeature feature) { features.add(feature); }

    /** The required facilities, expanded over their explicit dependencies. */
    public EnumSet<RuntimeFeature> features() { return RuntimeFeature.closure(features); }

    public List<SolvikProgram.Trait> traits() { return program.traits(); }
    public List<SolvikProgram.Enum> enums() { return program.enums(); }
    public List<SolvikProgram.Struct> structs() { return program.structs(); }
}
