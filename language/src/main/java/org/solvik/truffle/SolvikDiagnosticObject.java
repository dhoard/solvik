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
package org.solvik.truffle;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import org.solvik.diagnostic.Diagnostic;
import org.solvik.source.LineColumn;
import org.solvik.source.SourceFile;
import org.solvik.source.SourceSpan;

/**
 * The interop view of a single {@link Diagnostic}, produced when a consumer reads the structured
 * diagnostic list off a {@link SolvikParseException}. It exposes a fixed set of members carrying the
 * stable code, its family, the human message, the source file name, and the authoritative character
 * span together with derived 1-based line/column positions.
 *
 * <p>The span is reported in the compiler's authoritative UTF-16 character offsets plus display
 * line/column; translating those into the TCK protocol's UTF-8 byte-offset convention is the
 * consuming boundary's job, exactly as the protocol requires, so the language never hard-codes one
 * host encoding.
 */
@ExportLibrary(InteropLibrary.class)
final class SolvikDiagnosticObject implements TruffleObject {

    /** Member names in a fixed order; {@code getMembers} returns exactly these. */
    private static final String[] MEMBERS = {
            "family", "code", "text", "file",
            "startCharOffset", "endCharOffset",
            "startLine", "startColumn", "endLine", "endColumn"
    };

    private final String family;
    private final String code;
    private final String text;
    private final String file;
    private final long startCharOffset;
    private final long endCharOffset;
    private final long startLine;
    private final long startColumn;
    private final long endLine;
    private final long endColumn;

    /**
     * Builds the interop view of {@code diagnostic} located in {@code source}. Character offsets are
     * taken from the diagnostic's span; line/column positions are derived for display only.
     */
    @TruffleBoundary
    SolvikDiagnosticObject(Diagnostic diagnostic, SourceFile source) {
        String stable = diagnostic.code().stableCode();
        this.family = familyOf(stable);
        this.code = stable;
        this.text = diagnostic.message();
        this.file = source.name();
        SourceSpan span = diagnostic.span();
        this.startCharOffset = span.startOffset();
        this.endCharOffset = span.endOffset();
        LineColumn start = source.lineColumnAt(span.startOffset());
        LineColumn end = source.lineColumnAt(Math.max(span.startOffset(), span.endOffset() - 1));
        this.startLine = start.line();
        this.startColumn = start.column();
        this.endLine = end.line();
        this.endColumn = end.column();
    }

    /**
     * Extracts the {@code <FAMILY>} segment from a stable code of the form {@code SOLV-<FAMILY>-NNN}.
     * The family is the second hyphen-delimited component and is one of LEX, PARS, RESOL, TYPE, SEM,
     * or LOWER by the {@link org.solvik.diagnostic.DiagnosticCode} naming convention.
     */
    static String familyOf(String stableCode) {
        int first = stableCode.indexOf('-');
        if (first < 0) {
            return "";
        }
        int second = stableCode.indexOf('-', first + 1);
        return second < 0 ? stableCode.substring(first + 1) : stableCode.substring(first + 1, second);
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    @TruffleBoundary
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return new SolvikStringArray(MEMBERS);
    }

    @ExportMessage
    boolean isMemberReadable(String member) {
        for (String name : MEMBERS) {
            if (name.equals(member)) {
                return true;
            }
        }
        return false;
    }

    @ExportMessage
    @TruffleBoundary
    Object readMember(String member) throws UnsupportedMessageException {
        switch (member) {
            case "family":
                return family;
            case "code":
                return code;
            case "text":
                return text;
            case "file":
                return file;
            case "startCharOffset":
                return Long.valueOf(startCharOffset);
            case "endCharOffset":
                return Long.valueOf(endCharOffset);
            case "startLine":
                return Long.valueOf(startLine);
            case "startColumn":
                return Long.valueOf(startColumn);
            case "endLine":
                return Long.valueOf(endLine);
            case "endColumn":
                return Long.valueOf(endColumn);
            default:
                throw UnsupportedMessageException.create();
        }
    }
}
