/*
 * Copyright (c) 2026-present Douglas Hoard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.solvik.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import com.oracle.truffle.api.frame.FrameSlotKind;
import org.junit.jupiter.api.Test;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.semantic.CheckedProgram;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;
import org.solvik.type.AnyType;
import org.solvik.type.BooleanType;
import org.solvik.type.ByteType;
import org.solvik.type.CharacterType;
import org.solvik.type.ClassType;
import org.solvik.type.DoubleType;
import org.solvik.type.FloatType;
import org.solvik.type.IntegerType;
import org.solvik.type.LongType;
import org.solvik.type.ShortType;
import org.solvik.type.StringType;
import org.solvik.type.Type;
import org.solvik.type.UnitType;

/**
 * Phase 8 lowering kind-mapping test. {@code SolvikLowering.kindOf(Type)} decides, for every local
 * variable slot, whether the runtime uses a primitive {@code FrameSlotKind} (so the value is not
 * boxed) or the generic {@code Object} slot. This test exercises that private helper exhaustively
 * through reflection and verifies the one conditional branch: each of the five numeric/primitive
 * kinds maps to its Truffle slot kind, and every other Solvik type (boxed primitives and reference
 * types) falls through to {@code Object}.
 */
public final class SolvikKindOfTest {

    private static final Method KIND_OF = findKindOf();

    private static Method findKindOf() {
        try {
            Method method = org.solvik.lowering.SolvikLowering.class.getDeclaredMethod("kindOf", Type.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException | LinkageError error) {
            fail("kindOf(Type) must be reflectively accessible: " + error);
            return null; // unreachable; keeps the compiler's control flow total
        }
    }

    private static FrameSlotKind invoke(Type type) throws ReflectiveOperationException {
        try {
            return (FrameSlotKind) KIND_OF.invoke(null, type);
        } catch (InvocationTargetException error) {
            throw new RuntimeException("kindOf threw for " + type, error.getCause());
        }
    }

    @Test
    public void primitiveNumericAndBooleanTypesUseTheirPrimitiveFrameSlotKinds() throws Exception {
        assertThat(invoke(IntegerType.INSTANCE)).isEqualTo(FrameSlotKind.Int);
        assertThat(invoke(BooleanType.INSTANCE)).isEqualTo(FrameSlotKind.Boolean);
        assertThat(invoke(LongType.INSTANCE)).isEqualTo(FrameSlotKind.Long);
        assertThat(invoke(FloatType.INSTANCE)).isEqualTo(FrameSlotKind.Float);
        assertThat(invoke(DoubleType.INSTANCE)).isEqualTo(FrameSlotKind.Double);
    }

    @Test
    public void boxedPrimitiveTypesFallThroughToObjectFrameSlot() throws Exception {
        // Byte, Short and Character have no dedicated Truffle slot kind, so their locals box into
        // an Object slot rather than getting a specialized primitive representation.
        assertThat(invoke(ByteType.INSTANCE)).isEqualTo(FrameSlotKind.Object);
        assertThat(invoke(ShortType.INSTANCE)).isEqualTo(FrameSlotKind.Object);
        assertThat(invoke(CharacterType.INSTANCE)).isEqualTo(FrameSlotKind.Object);
        assertThat(invoke(StringType.INSTANCE)).isEqualTo(FrameSlotKind.Object);
    }

    @Test
    public void referenceAndUnitTypesFallThroughToObjectFrameSlot() throws Exception {
        assertThat(invoke(UnitType.INSTANCE)).isEqualTo(FrameSlotKind.Object);
        assertThat(invoke(AnyType.INSTANCE)).isEqualTo(FrameSlotKind.Object);
    }

    @Test
    public void concreteClassTypesFallThroughToObjectFrameSlot() throws Exception {
        // The most common reference type in a real program is an ordinary class; its locals must not
        // receive a primitive slot kind, confirming the fall-through path for nominal types.
        CompilationUnitNode unit = parseOk("kindof.sol", """
                open class Box {
                    var value: Integer = 0
                }
                """);
        SemanticResult result = SolvikSemanticAnalyzer.analyze(unit);
        assertThat(result.isSuccess()).as("analysis must succeed: " + result.diagnostics().all()).isTrue();
        CheckedProgram program = result.requireProgram();
        ClassType classType = (ClassType) program.classSymbol("Box").orElseThrow().type();
        assertThat(invoke(classType)).isEqualTo(FrameSlotKind.Object);
    }

    private static CompilationUnitNode parseOk(String name, String text) {
        org.solvik.source.SourceFile source = new org.solvik.source.SourceFile(name, text);
        return org.solvik.parser.SolvikParser.parse(source).requireAst();
    }
}
