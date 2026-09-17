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
package org.solvik.source;

/**
 * A 1-based line/column position derived from a zero-based character offset for display only.
 * Positions are never the authoritative source location; {@link SourceSpan} is.
 */
public record LineColumn(int line, int column) implements Comparable<LineColumn> {

    public LineColumn {
        if (line < 1) {
            throw new IllegalArgumentException("line must be >= 1 but was " + line);
        }
        if (column < 1) {
            throw new IllegalArgumentException("column must be >= 1 but was " + column);
        }
    }

    @Override
    public int compareTo(LineColumn other) {
        int c = Integer.compare(line, other.line);
        return c != 0 ? c : Integer.compare(column, other.column);
    }

    @Override
    public String toString() {
        return line + ":" + column;
    }
}
