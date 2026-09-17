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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * The runtime representation of the built-in Solvik {@code Regex} type (docs/LANGUAGE_SPEC.md
 * section 14). It wraps one compiled pattern and implements the source API: {@code matches}
 * requires the complete input to match, {@code find} returns the first match, {@code findAll}
 * returns every non-overlapping match from left to right, and {@code replace} replaces every
 * non-overlapping match treating the replacement as literal text (capture substitution is
 * deferred). The pattern dialect and the engine stay behind {@link org.solvik.regex.RegexSyntax}
 * and this class.
 */
public final class SolvikRegex {

    private final String source;
    private final Pattern pattern;

    public SolvikRegex(String source, Pattern pattern) {
        this.source = Objects.requireNonNull(source, "source");
        this.pattern = Objects.requireNonNull(pattern, "pattern");
    }

    /** The pattern text the value was constructed from. */
    public String source() {
        return source;
    }

    /** The compiled pattern, created once per source constant or per dynamic construction. */
    public Pattern pattern() {
        return pattern;
    }

    @TruffleBoundary
    public boolean matches(String value) {
        return pattern.matcher(value).matches();
    }

    @TruffleBoundary
    public SolvikRegexMatch find(String value) {
        Matcher matcher = pattern.matcher(value);
        if (!matcher.find()) {
            return null;
        }
        return SolvikRegexMatch.from(matcher);
    }

    @TruffleBoundary
    public SolvikList findAll(String value) {
        Matcher matcher = pattern.matcher(value);
        List<Object> matches = new ArrayList<>();
        while (matcher.find()) {
            matches.add(SolvikRegexMatch.from(matcher));
        }
        return new SolvikList(matches.toArray());
    }

    @TruffleBoundary
    public String replace(String value, String replacement) {
        return pattern.matcher(value).replaceAll(Matcher.quoteReplacement(replacement));
    }
}
