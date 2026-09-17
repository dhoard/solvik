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
package org.solvik.truffle.object;

import java.util.regex.Matcher;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import org.solvik.truffle.SolvikException;

/**
 * The immutable runtime value of the built-in Solvik {@code RegexMatch} type (docs/LANGUAGE_SPEC.md
 * section 14). It snapshots one match: the complete matched text, zero-based character offsets with
 * an exclusive {@code end}, the capturing-group count, and every group's text. Group zero is the
 * complete match; a group that did not participate is {@code null}, which is why
 * {@code RegexMatch.group} has a nullable result type.
 */
public final class SolvikRegexMatch {

    private final String[] groups;
    private final int start;
    private final int end;

    private SolvikRegexMatch(String[] groups, int start, int end) {
        this.groups = groups;
        this.start = start;
        this.end = end;
    }

    /** Snapshots the matcher's current match before a later {@code find} advances it. */
    static SolvikRegexMatch from(Matcher matcher) {
        int groupCount = matcher.groupCount();
        String[] groups = new String[groupCount + 1];
        for (int i = 0; i <= groupCount; i++) {
            groups[i] = matcher.group(i);
        }
        return new SolvikRegexMatch(groups, matcher.start(), matcher.end());
    }

    /** The complete matched text, exposed as the immutable {@code value: String} property. */
    public String value() {
        return groups[0];
    }

    /** The zero-based start offset, exposed as the immutable {@code start: Int} property. */
    public int start() {
        return start;
    }

    /** The exclusive zero-based end offset, exposed as the immutable {@code end: Int} property. */
    public int end() {
        return end;
    }

    /** The number of capturing groups, exposed as the immutable {@code groupCount: Int} property. */
    public int groupCount() {
        return groups.length - 1;
    }

    /**
     * The text of capturing group {@code index}, or {@code null} when that group did not
     * participate. Group zero is the complete match. An index outside {@code 0..groupCount} raises a
     * Solvik runtime bounds error.
     */
    public String group(int index, Node location) {
        if (index < 0 || index >= groups.length) {
            throw boundsFailure(index, location);
        }
        return groups[index];
    }

    /**
     * Builds the out-of-range failure outside compiled code, so the message construction is not
     * reachable for Truffle runtime compilation (docs/ARCHITECTURE.md native-image concerns).
     */
    @TruffleBoundary
    private SolvikException boundsFailure(int index, Node location) {
        return SolvikException.boundsError("group index " + index + " is out of range for " + groupCount() + " capturing group(s)", location);
    }
}
