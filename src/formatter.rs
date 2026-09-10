//! Conservative source formatter for Solvik.
//!
//! Formatting is deliberately source-preserving: comments and literal text
//! remain untouched while indentation and type-body spacing are normalized.
//! The parser remains the authority for syntax validation.

#[derive(Default)]
struct LexicalState {
    quote: Option<u8>,
    raw: bool,
    escaped: bool,
    comment_depth: usize,
}

impl LexicalState {
    fn protected(&self) -> bool {
        self.quote.is_some() || self.comment_depth > 0
    }

    fn brace_delta(&mut self, line: &str) -> (i32, bool) {
        let bytes = line.as_bytes();
        let mut i = 0;
        let mut delta = 0;
        let mut leading_close = false;
        let mut saw_code = false;
        while i < bytes.len() {
            let b = bytes[i];
            if self.comment_depth > 0 {
                if bytes.get(i..i + 2) == Some(b"/*") {
                    self.comment_depth += 1;
                    i += 2;
                } else if bytes.get(i..i + 2) == Some(b"*/") {
                    self.comment_depth -= 1;
                    i += 2;
                } else {
                    i += 1;
                }
                continue;
            }
            if let Some(quote) = self.quote {
                if self.escaped {
                    self.escaped = false;
                } else if b == b'\\' && !self.raw {
                    self.escaped = true;
                } else if b == quote {
                    self.quote = None;
                }
                i += 1;
                continue;
            }
            if bytes.get(i..i + 2) == Some(b"//") {
                break;
            }
            if bytes.get(i..i + 2) == Some(b"/*") {
                self.comment_depth = 1;
                i += 2;
                continue;
            }
            if !b.is_ascii_whitespace() && !saw_code {
                leading_close = b == b'}';
                saw_code = true;
            }
            match b {
                b'r' if bytes.get(i + 1) == Some(&b'"') => {
                    self.quote = Some(b'"');
                    self.raw = true;
                    i += 1;
                }
                b'"' | b'\'' => {
                    self.quote = Some(b);
                    self.raw = false;
                }
                // Consume whole identifiers so an ending `r` is not a raw prefix.
                b'a'..=b'z' | b'A'..=b'Z' | b'_' => {
                    while bytes
                        .get(i + 1)
                        .is_some_and(|b| b.is_ascii_alphanumeric() || *b == b'_')
                    {
                        i += 1;
                    }
                }
                b'{' => delta += 1,
                b'}' => delta -= 1,
                _ => {}
            }
            i += 1;
        }
        (delta, leading_close)
    }
}

fn opens_type_body(line: &str) -> bool {
    let trimmed = line.trim();
    (trimmed.starts_with("class ")
        || trimmed.starts_with("interface ")
        || trimmed.starts_with("enum "))
        && trimmed.ends_with('{')
}

/// Format a syntactically valid Solvik source file.
pub fn format_source(source: &str) -> String {
    // Retain line terminators: CRLF inside a literal is part of its value.
    let lines: Vec<&str> = source.split_inclusive('\n').collect();
    let mut out = Vec::new();
    let mut indent = 0i32;
    let mut state = LexicalState::default();

    for (index, raw) in lines.iter().enumerate() {
        let trimmed = raw.trim();
        let protected_start = state.protected();
        let (delta, leading_close) = state.brace_delta(raw);
        let protected_end = state.protected();
        if trimmed.is_empty() && !protected_start {
            if out.last().is_none_or(|line: &String| !line.is_empty()) {
                out.push(String::new());
            }
            continue;
        }

        let line_indent = if leading_close {
            (indent - 1).max(0)
        } else {
            indent
        };
        let content = if protected_end {
            raw.strip_suffix('\n').unwrap_or(raw)
        } else {
            raw.trim_end()
        };
        if protected_start {
            out.push(content.to_string());
        } else {
            out.push(format!(
                "{}{}",
                "    ".repeat(line_indent as usize),
                content.trim_start()
            ));
        }
        indent = (indent + delta).max(0);

        if !protected_start
            && !protected_end
            && opens_type_body(trimmed)
            && lines
                .get(index + 1)
                .is_some_and(|next| !next.trim().is_empty() && next.trim() != "}")
            && out.last().is_some_and(|line| !line.is_empty())
        {
            out.push(String::new());
        }
    }

    while out.last().is_some_and(|line| line.is_empty()) {
        out.pop();
    }
    let mut result = out.join("\n");
    result.push('\n');
    result
}

#[cfg(test)]
mod tests {
    use super::format_source;

    #[test]
    fn preserves_multiline_literal_values() {
        for literal in [
            "\"first  \r\n  second\n\n\nlast\"",
            "r\"first  \n  second\nlast\"",
        ] {
            let source = format!("module demo\nclass Main {{\npublic static run(args: String...): Long {{\nlet s: String = {literal}\nreturn 0\n}}\n}}\n");
            assert!(crate::compile("test.sol", &source).is_ok());
            let formatted = format_source(&source);
            assert!(
                formatted.contains(literal),
                "literal changed: {formatted:?}"
            );
            assert_eq!(formatted, format_source(&formatted));
        }
    }

    #[test]
    fn ignores_nested_block_comment_braces() {
        let source = "module demo\nclass Main {\n/* { /* { */\n } */\npublic static run(args: String...): Long {\nreturn 0\n}\n}\n";
        assert!(crate::compile("test.sol", source).is_ok());
        let formatted = format_source(source);
        assert!(formatted.contains("\n    public static run"));
        assert!(formatted.contains("\n        return 0"));
        assert_eq!(formatted, format_source(&formatted));
    }

    #[test]
    fn raw_backslash_does_not_escape_closing_quote() {
        let source = "module demo\nclass Main {\npublic static run(args: String...): Long {\nif r\"\\\" == r\"\\\" {\nreturn 0\n}\nreturn 1\n}\n}\n";
        assert!(crate::compile("test.sol", source).is_ok());
        assert!(format_source(source).contains("            return 0"));
    }

    #[test]
    fn formats_indentation_and_type_spacing() {
        let source = "module demo\nclass Main {\npublic static run(args: String...): Long {\nreturn 0\n}\n}\n";
        assert_eq!(
            format_source(source),
            "module demo\nclass Main {\n\n    public static run(args: String...): Long {\n        return 0\n    }\n}\n"
        );
    }

    #[test]
    fn preserves_strings_and_comments_when_counting_braces() {
        let source = "module demo\nclass Main {\npublic static run(args: String...): Long {\nstdout.println(\"{ // not a block\")\nreturn 0\n}\n}\n";
        let formatted = format_source(source);
        assert!(formatted.contains("\"{ // not a block\""));
        assert!(formatted.contains("        return 0"));
    }

    #[test]
    fn preserves_let_declarations() {
        // `let` / `let mutable` are line-based and must round-trip unchanged.
        let source = "module demo\nclass Main {\npublic static run(args: String...): Long {\nlet x: Long = 1\nlet mutable y: Long = 2\nreturn x + y\n}\n}\n";
        let formatted = format_source(source);
        assert!(formatted.contains("        let x: Long = 1"));
        assert!(formatted.contains("        let mutable y: Long = 2"));
    }

    #[test]
    fn preserves_multiple_delegates() {
        let source = "module demo\nclass Employee {\nperson: Person\nidentity: Identity\ndelegate Named to person\ndelegate Identified to identity\n}\n";
        let formatted = format_source(source);
        assert!(formatted.contains("    delegate Named to person\n"));
        assert!(formatted.contains("    delegate Identified to identity\n"));
        // Delegation lines are ordinary class members and round-trip.
        let again = format_source(&formatted);
        assert_eq!(formatted, again, "formatting must be idempotent");
    }

    #[test]
    fn preserves_static_field_lines() {
        // `static` / `static mutable` field lines are ordinary class members
        // and must round-trip unchanged.
        let source = "module demo\nclass Counter {\nstatic count: Long = 0\nstatic mutable total: Long = 0\nstatic mutable cache: Map<String, Long> = {}\npublic static new(): Self { return Self {} }\n}\nclass Main { public static run(args: String...): Long { return 0 } }\n";
        assert!(crate::compile("test.sol", source).is_ok());
        let formatted = format_source(source);
        assert!(formatted.contains("    static count: Long = 0"));
        assert!(formatted.contains("    static mutable total: Long = 0"));
        assert!(formatted.contains("    static mutable cache: Map<String, Long> = {}"));
        let again = format_source(&formatted);
        assert_eq!(formatted, again, "formatting must be idempotent");
    }
}
