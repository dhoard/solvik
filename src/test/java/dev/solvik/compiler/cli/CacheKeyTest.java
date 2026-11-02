package dev.solvik.compiler.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("cache keys")
class CacheKeyTest {

    @Test
    @DisplayName("a scratch key is random, so concurrent checks cannot collide or hit a published entry")
    void unique_keys_never_repeat() {
        String first = CacheKey.unique();
        String second = CacheKey.unique();

        assertThat(first)
                .describedAs("the scratch prefix marks the entry as check-only work")
                .startsWith("scratch-")
                .isNotEqualTo(second);
    }
}
