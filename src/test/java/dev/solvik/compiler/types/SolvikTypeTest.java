package dev.solvik.compiler.types;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SolvikType")
class SolvikTypeTest {

    @Test
    void widening_order_follows_java_binary_numeric_promotion() {
        assertThat(SolvikType.widest(SolvikType.BYTE, SolvikType.BYTE))
                .as("Byte op Byte promotes to Integer").isEqualTo(SolvikType.INTEGER);
        assertThat(SolvikType.widest(SolvikType.BYTE, SolvikType.INTEGER))
                .isEqualTo(SolvikType.INTEGER);
        assertThat(SolvikType.widest(SolvikType.INTEGER, SolvikType.DOUBLE))
                .isEqualTo(SolvikType.DOUBLE);
        assertThat(SolvikType.widest(SolvikType.INTEGER, SolvikType.LONG))
                .isEqualTo(SolvikType.LONG);
        assertThat(SolvikType.widest(SolvikType.BIG_DECIMAL, SolvikType.INTEGER))
                .as("big numbers never join the primitive promotion ladder").isNull();
    }

    @Test
    void widening_is_undefined_for_non_numeric_operands() {
        assertThat(SolvikType.widest(SolvikType.STRING, SolvikType.INTEGER)).isNull();
        assertThat(SolvikType.widest(SolvikType.INTEGER, SolvikType.BOOLEAN)).isNull();
        assertThat(SolvikType.widest(SolvikType.VOID, SolvikType.INTEGER)).isNull();
    }

    @Test
    void assignability_follows_the_hierarchy() {
        assertThat(SolvikType.INTEGER.isAssignableTo(SolvikType.INTEGER)).isTrue();
        assertThat(SolvikType.INTEGER.isAssignableTo(SolvikType.NUMBER)).isTrue();
        assertThat(SolvikType.BYTE.isAssignableTo(SolvikType.ANY)).isTrue();
        assertThat(SolvikType.array(SolvikType.INTEGER).isAssignableTo(SolvikType.ANY)).isTrue();
        assertThat(SolvikType.INTEGER.isAssignableTo(SolvikType.BIG_DECIMAL)).isFalse();
        assertThat(SolvikType.STRING.isAssignableTo(SolvikType.NUMBER)).isFalse();
        assertThat(SolvikType.array(SolvikType.INTEGER)
                .isAssignableTo(SolvikType.array(SolvikType.INTEGER))).isTrue();
        assertThat(SolvikType.array(SolvikType.INTEGER)
                .isAssignableTo(SolvikType.array(SolvikType.ANY))).isFalse();
        assertThat(SolvikType.array(SolvikType.INTEGER)
                .isAssignableTo(SolvikType.array(SolvikType.INTEGER).asNullable()))
                .as("a non-null array is assignable to a nullable array").isTrue();
        assertThat(SolvikType.array(SolvikType.INTEGER, 2)
                .isAssignableTo(SolvikType.array(SolvikType.INTEGER, 3)))
                .as("a fixed length is part of the array type (§5)").isFalse();
        assertThat(SolvikType.array(SolvikType.INTEGER, 2)
                .isAssignableTo(SolvikType.array(SolvikType.INTEGER)))
                .as("a fixed-length array is assignable to the size-erased array type").isTrue();
        assertThat(SolvikType.array(SolvikType.INTEGER)
                .isAssignableTo(SolvikType.array(SolvikType.INTEGER, 2)))
                .as("an array of unknown length is not a fixed-length array").isFalse();
        assertThat(SolvikType.array(SolvikType.array(SolvikType.INTEGER, 2), 2)
                .isAssignableTo(SolvikType.array(SolvikType.array(SolvikType.INTEGER, 3), 2)))
                .as("a nested fixed length is part of the array type").isFalse();
        assertThat(SolvikType.array(SolvikType.array(SolvikType.INTEGER, 2), 2)
                .isAssignableTo(SolvikType.array(SolvikType.array(SolvikType.INTEGER), 2)))
                .as("a nested fixed array is assignable to the size-erased array type").isTrue();
    }

    @Test
    @DisplayName("Null is assignable only to a nullable type, and Void to nothing")
    void null_and_void_are_special() {
        assertThat(SolvikType.NULL.isAssignableTo(SolvikType.INTEGER)).isFalse();
        assertThat(SolvikType.NULL.isAssignableTo(SolvikType.INTEGER.asNullable())).isTrue();
        assertThat(SolvikType.NULL.isAssignableTo(SolvikType.ANY)).isFalse();
        assertThat(SolvikType.NULL.isAssignableTo(SolvikType.ANY.asNullable())).isTrue();
        assertThat(SolvikType.NULL.isAssignableTo(SolvikType.array(SolvikType.STRING))).isFalse();
        assertThat(SolvikType.NULL.isAssignableTo(
                SolvikType.array(SolvikType.STRING).asNullable())).isTrue();
        assertThat(SolvikType.VOID.isAssignableTo(SolvikType.ANY)).isFalse();
        assertThat(SolvikType.user("Point").isAssignableTo(SolvikType.ANY)).isTrue();
    }

    @Test
    @DisplayName("an ordinary type is non-null and a nullable type admits Null")
    void nullability_is_part_of_the_type() {
        assertThat(SolvikType.STRING.isNullable()).isFalse();
        assertThat(SolvikType.STRING.asNullable().isNullable()).isTrue();
        assertThat(SolvikType.STRING.asNullable().asNonNull()).isSameAs(SolvikType.STRING);
        assertThat(SolvikType.STRING.asNullable()).hasToString("String?");
        assertThat(SolvikType.array(SolvikType.STRING.asNullable(), 2)).hasToString("String?[2]");
        assertThat(SolvikType.array(SolvikType.STRING, 2).asNullable())
                .hasToString("String[2]?");
        assertThat(SolvikType.STRING.asNullable().isAssignableTo(SolvikType.STRING)).isFalse();
        assertThat(SolvikType.STRING.isAssignableTo(SolvikType.STRING.asNullable())).isTrue();
    }

    @Test
    void predicates_identify_numeric_and_array_types() {
        assertThat(SolvikType.INTEGER.isNumeric()).isTrue();
        assertThat(SolvikType.BYTE.isNumeric()).isTrue();
        assertThat(SolvikType.BIG_DECIMAL.isNumeric()).isTrue();
        assertThat(SolvikType.STRING.isNumeric()).isFalse();
        assertThat(SolvikType.array(SolvikType.INTEGER).isArray()).isTrue();
        assertThat(SolvikType.INTEGER.isArray()).isFalse();
    }

    @Test
    void renders_written_type_syntax() {
        assertThat(SolvikType.INTEGER).hasToString("Integer");
        assertThat(SolvikType.array(SolvikType.BYTE)).hasToString("Byte[]");
        assertThat(SolvikType.user("Point")).hasToString("Point");
        assertThat(SolvikType.array(SolvikType.array(SolvikType.STRING)))
                .hasToString("String[][]");
        assertThat(SolvikType.array(SolvikType.STRING, 3)).hasToString("String[3]");
        assertThat(SolvikType.NULL).hasToString("Null");
        assertThat(SolvikType.ANY.asNullable()).hasToString("Any?");
    }
}
