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
package org.solvik.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.solvik.truffle.object.SolvikRegexMatch;

/**
 * Reads one of the immutable {@code RegexMatch} properties (docs/LANGUAGE_SPEC.md section 14):
 * {@code value: String}, {@code start: Integer}, {@code end: Integer}, or {@code groupCount: Integer}. The
 * built-in match has no Truffle shape storage, so lowering emits this dedicated read. A safe read
 * ({@code receiver?.value}) yields {@code null} for a null receiver.
 */
@NodeInfo(shortName = ".match", description = "Read a Solvik RegexMatch property")
public final class SolvikRegexMatchReadNode extends SolvikExpressionNode {

    /** The property this node reads. */
    public enum Field {
        VALUE,
        START,
        END,
        GROUP_COUNT
    }

    private final Field field;
    private final boolean safe;
    @Child private SolvikExpressionNode receiver;

    public SolvikRegexMatchReadNode(Field field, SolvikExpressionNode receiver, boolean safe) {
        this.field = field;
        this.receiver = receiver;
        this.safe = safe;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object target = receiver.executeGeneric(frame);
        if (safe && target == null) {
            return null;
        }
        SolvikRegexMatch match = (SolvikRegexMatch) target;
        return switch (field) {
            case VALUE -> match.value();
            case START -> match.start();
            case END -> match.end();
            case GROUP_COUNT -> match.groupCount();
        };
    }

    @Override
    public int executeInt(VirtualFrame frame) {
        Object target = receiver.executeGeneric(frame);
        if (safe && target == null) {
            throw new IllegalStateException("a safe RegexMatch read yielded null where an Integer was expected");
        }
        SolvikRegexMatch match = (SolvikRegexMatch) target;
        return switch (field) {
            case START -> match.start();
            case END -> match.end();
            case GROUP_COUNT -> match.groupCount();
            case VALUE -> throw new IllegalStateException("RegexMatch.value is a String, not an Integer");
        };
    }
}
