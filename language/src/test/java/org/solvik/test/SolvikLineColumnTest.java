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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.solvik.source.LineColumn;

/**
 * Unit tests for the display-only 1-based {@link LineColumn} position: construction validation,
 * ordering by line then column, and rendering. Source spans remain the authoritative location, but
 * {@code LineColumn} is what diagnostics render for people.
 */
public final class SolvikLineColumnTest {

    @Test
    public void positionsMustBeOneBased() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> new LineColumn(0, 1));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> new LineColumn(1, 0));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> new LineColumn(-1, -1));
        assertThat(new LineColumn(1, 1).line()).isEqualTo(1);
        assertThat(new LineColumn(1, 1).column()).isEqualTo(1);
    }

    @Test
    public void positionsOrderByLineThenColumn() {
        assertThat(new LineColumn(1, 2)).isLessThan(new LineColumn(1, 3));
        assertThat(new LineColumn(1, 9)).isLessThan(new LineColumn(2, 1));
        assertThat(new LineColumn(2, 1)).isGreaterThan(new LineColumn(1, 9));
        assertThat(new LineColumn(3, 4)).isEqualByComparingTo(new LineColumn(3, 4));
    }

    @Test
    public void sortingUsesLineThenColumn() {
        List<LineColumn> positions = List.of(new LineColumn(2, 1), new LineColumn(1, 3), new LineColumn(1, 1), new LineColumn(2, 2));
        assertThat(positions.stream().sorted().toList()).isEqualTo(List.of(
                new LineColumn(1, 1), new LineColumn(1, 3), new LineColumn(2, 1), new LineColumn(2, 2)));
    }

    @Test
    public void renderingIsLineColonColumn() {
        assertThat(new LineColumn(1, 1)).hasToString("1:1");
        assertThat(new LineColumn(12, 34)).hasToString("12:34");
    }
}
