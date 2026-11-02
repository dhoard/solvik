package dev.solvik.compiler.types;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The text grammar every conversion accepts (§4 "Conversion"). */
@DisplayName("Conversion text grammar")
class ConversionTextTest {

    @Test
    void integers_are_an_optional_sign_and_ascii_digits() {
        assertThat(ConversionText.isInteger("0")).isTrue();
        assertThat(ConversionText.isInteger("42")).isTrue();
        assertThat(ConversionText.isInteger("+42")).isTrue();
        assertThat(ConversionText.isInteger("-42")).isTrue();
        assertThat(ConversionText.isInteger("0042")).isTrue();
        assertThat(ConversionText.isInteger("")).isFalse();
        assertThat(ConversionText.isInteger("+")).isFalse();
        assertThat(ConversionText.isInteger(" 42")).isFalse();
        assertThat(ConversionText.isInteger("42 ")).isFalse();
        assertThat(ConversionText.isInteger("1_000")).isFalse();
        assertThat(ConversionText.isInteger("4.2")).isFalse();
        assertThat(ConversionText.isInteger("4e2")).isFalse();
        assertThat(ConversionText.isInteger("0x10")).isFalse();
        assertThat(ConversionText.isInteger("42a")).isFalse();
    }

    @Test
    void decimals_accept_an_optional_fraction_and_exponent() {
        assertThat(ConversionText.isDecimal("0")).isTrue();
        assertThat(ConversionText.isDecimal("1.5")).isTrue();
        assertThat(ConversionText.isDecimal("-1.5")).isTrue();
        assertThat(ConversionText.isDecimal("+1.5")).isTrue();
        assertThat(ConversionText.isDecimal(".5")).isTrue();
        assertThat(ConversionText.isDecimal("1e3")).isTrue();
        assertThat(ConversionText.isDecimal("1E3")).isTrue();
        assertThat(ConversionText.isDecimal("1e+3")).isTrue();
        assertThat(ConversionText.isDecimal("1.5e-3")).isTrue();
        assertThat(ConversionText.isDecimal("")).isFalse();
        assertThat(ConversionText.isDecimal(".")).isFalse();
        assertThat(ConversionText.isDecimal("5.")).isFalse();
        assertThat(ConversionText.isDecimal(".e3")).isFalse();
        assertThat(ConversionText.isDecimal("1e")).isFalse();
        assertThat(ConversionText.isDecimal("1e+")).isFalse();
        assertThat(ConversionText.isDecimal("1.5.5")).isFalse();
        assertThat(ConversionText.isDecimal(" 1.5")).isFalse();
        assertThat(ConversionText.isDecimal("1,5")).isFalse();
        assertThat(ConversionText.isDecimal("NaN")).isFalse();
        assertThat(ConversionText.isDecimal("Infinity")).isFalse();
    }

    @Test
    void booleans_are_exactly_true_or_false_in_lower_case() {
        assertThat(ConversionText.isBoolean("true")).isTrue();
        assertThat(ConversionText.isBoolean("false")).isTrue();
        assertThat(ConversionText.isBoolean("True")).isFalse();
        assertThat(ConversionText.isBoolean("TRUE")).isFalse();
        assertThat(ConversionText.isBoolean("1")).isFalse();
        assertThat(ConversionText.isBoolean("")).isFalse();
        assertThat(ConversionText.isBoolean(" true")).isFalse();
    }
}
