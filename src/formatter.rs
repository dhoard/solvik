//! Conservative source formatter for Solvik.
//!
//! Formatting is deliberately source-preserving: comments and literal text
//! remain untouched while indentation and type-body spacing are normalized.
//! The parser remains the authority for syntax validation.

fn brace_delta(line: &str) -> (i32, bool) {
    let bytes = line.as_bytes();
    let mut i = 0;
    let mut delta = 0;
    let mut in_string = false;
    let mut in_char = false;
    let mut escaped = false;
    while i < bytes.len() {
        let b = bytes[i];
        if !in_string && !in_char && b == b'/' && bytes.get(i + 1) == Some(&b'/') {
            break;
        }
        if escaped {
            escaped = false;
            i += 1;
            continue;
        }
        match b {
            b'\\' if in_string || in_char => escaped = true,
            b'"' if !in_char => in_string = !in_string,
            b'\'' if !in_string => in_char = !in_char,
            b'{' if !in_string && !in_char => delta += 1,
            b'}' if !in_string && !in_char => delta -= 1,
            _ => {}
        }
        i += 1;
    }
    let leading_close = line.trim_start().starts_with('}');
    (delta, leading_close)
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
    let lines: Vec<&str> = source.lines().collect();
    let mut out = Vec::new();
    let mut indent = 0i32;

    for (index, raw) in lines.iter().enumerate() {
        let trimmed = raw.trim();
        if trimmed.is_empty() {
            if out.last().is_none_or(|line: &String| !line.is_empty()) {
                out.push(String::new());
            }
            continue;
        }

        let (delta, leading_close) = brace_delta(trimmed);
        let line_indent = if leading_close {
            (indent - 1).max(0)
        } else {
            indent
        };
        out.push(format!(
            "{}{}",
            "    ".repeat(line_indent as usize),
            trimmed
        ));
        indent = (indent + delta).max(0);

        if opens_type_body(trimmed)
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
}
