//! Shared diagnostics for the language toolchain.
//!
//! Port of internal/diagnostic/diagnostic.go.

use crate::source::{Source, Span};

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Severity {
    Error,
    Warning,
    Note,
}

impl Severity {
    pub fn as_str(&self) -> &'static str {
        match self {
            Severity::Error => "error",
            Severity::Warning => "warning",
            Severity::Note => "note",
        }
    }
}

#[derive(Clone, Debug)]
pub struct Diagnostic {
    pub severity: Severity,
    pub code: String,
    pub message: String,
    pub span: Span,
}

impl Diagnostic {
    pub fn new_error(code: &str, message: &str, span: Span) -> Diagnostic {
        Diagnostic {
            severity: Severity::Error,
            code: code.to_string(),
            message: message.to_string(),
            span,
        }
    }

    pub fn new_warning(code: &str, message: &str, span: Span) -> Diagnostic {
        Diagnostic {
            severity: Severity::Warning,
            code: code.to_string(),
            message: message.to_string(),
            span,
        }
    }
}

/// Collection of diagnostics.
#[derive(Default)]
pub struct Diagnostics {
    items: Vec<Diagnostic>,
}

impl Diagnostics {
    pub fn new() -> Diagnostics {
        Diagnostics { items: Vec::new() }
    }

    pub fn add_error(&mut self, code: &str, message: &str, span: Span) {
        self.items.push(Diagnostic::new_error(code, message, span));
    }

    pub fn add_warning(&mut self, code: &str, message: &str, span: Span) {
        self.items.push(Diagnostic::new_warning(code, message, span));
    }

    pub fn extend(&mut self, other: &Diagnostics) {
        self.items.extend(other.items.iter().cloned());
    }

    pub fn has_errors(&self) -> bool {
        self.items.iter().any(|d| d.severity == Severity::Error)
    }

    pub fn all(&self) -> &[Diagnostic] {
        &self.items
    }
}

/// Diagnostic codes shared across packages (subset of codes.go).
pub const CODE_PARSER_STATEMENT_SEPARATOR: &str = "P073";
pub const CODE_PARSER_BARE_RETURN_ARROW: &str = "P074";
pub const CODE_CHECKER_STRUCT_POSITIONAL: &str = "C074";

/// Formats a diagnostic in the Rust-like style used by the Go toolchain.
///
/// The source snippet is only rendered when `src` matches the file named by
/// the diagnostic's span, so a diagnostic pointing into a dependency file
/// never shows a source line from a different file.
pub fn format_diagnostic(diag: &Diagnostic, src: Option<&Source>) -> String {
    let mut b = String::new();
    b.push_str(&format!(
        "{} {}: {}\n",
        diag.severity.as_str(),
        diag.code,
        diag.message
    ));

    // Position header; synthetic zero spans (e.g. "too many parse errors")
    // carry no location, so the arrow line is suppressed entirely.
    let has_position = !diag.span.file.is_empty() || diag.span.start_l > 0;
    if has_position {
        b.push_str(&format!("  --> {}\n", diag.span.display()));
    }

    // Source context — only when the provided source matches the span's file
    if let Some(src) = src {
        if has_position && src.name == diag.span.file && diag.span.start_l > 0 {
            let line = diag.span.start_l;
            let line_str = src.line_content(line);
            let line_width = diag.span.end_l.to_string().len();
            let padding = " ".repeat(line_width);

            b.push_str(&format!(" {} |\n", padding));
            if !line_str.is_empty() {
                let line_num = format!("{:>width$}", line, width = line_width);
                b.push_str(&format!(" {} | {}\n", line_num, line_str));

                // Underline. Columns are byte-based; count characters so the
                // caret aligns visually on lines with multi-byte characters.
                let mut start_col = diag.span.start_c.saturating_sub(1);
                let mut end_col = diag.span.end_c.saturating_sub(1);
                if end_col > line_str.len() {
                    end_col = line_str.len();
                }
                if start_col > end_col {
                    // Multi-line span: a single caret at the start position.
                    end_col = start_col;
                }
                if start_col > line_str.len() {
                    start_col = line_str.len();
                }
                let prefix = &line_str[..start_col];
                let under = &line_str[start_col..end_col];
                let mut width = under.chars().count();
                if width < 1 {
                    width = 1;
                }
                let caret = " ".repeat(prefix.chars().count()) + &"^".repeat(width);
                b.push_str(&format!(" {} | {}\n", padding, caret));
            }
            for l in (diag.span.start_l + 1)..=diag.span.end_l {
                let line_str = src.line_content(l);
                if !line_str.is_empty() {
                    let line_num = format!("{:>width$}", l, width = line_width);
                    b.push_str(&format!(" {} | {}\n", line_num, line_str));
                }
            }
            b.push_str(&format!(" {} |\n", padding));
        }
    }

    b
}

pub fn format_all(diags: &[Diagnostic], src: Option<&Source>) -> String {
    let mut b = String::new();
    for (i, d) in diags.iter().enumerate() {
        if i > 0 {
            b.push('\n');
        }
        b.push_str(&format_diagnostic(d, src));
    }
    b
}
