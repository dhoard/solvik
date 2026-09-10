//! Compile-time diagnostics with stable codes and source spans.
//!
//! Code families:
//! - `L###` lexical errors
//! - `P###` parse errors
//! - `C###` semantic / compile errors
//! - `W###` warnings (e.g. `W101` shadowing) — never fail the compile
//! - `V###` bytecode verification errors
//! - `E###` runtime errors (see SEMANTICS.md)

use crate::source::{SourceManager, Span};

/// Whether a diagnostic fails the compile (`Error`) or is advisory
/// (`Warning`). Warnings are printed but never affect the exit code or
/// `lib::compile` success.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Severity {
    Error,
    Warning,
}

#[derive(Debug, Clone, PartialEq)]
pub struct Diagnostic {
    pub code: &'static str,
    pub message: String,
    pub span: Option<Span>,
    pub severity: Severity,
}

impl Diagnostic {
    /// An error diagnostic (the default; fails the compile).
    pub fn new(code: &'static str, message: impl Into<String>) -> Self {
        Diagnostic {
            code,
            message: message.into(),
            span: None,
            severity: Severity::Error,
        }
    }

    /// A warning diagnostic (advisory; never fails the compile).
    pub fn warning(code: &'static str, message: impl Into<String>) -> Self {
        Diagnostic {
            code,
            message: message.into(),
            span: None,
            severity: Severity::Warning,
        }
    }

    pub fn at(mut self, span: Span) -> Self {
        self.span = Some(span);
        self
    }

    pub fn render(&self, sources: &crate::source::SourceManager) -> String {
        let mut out = String::new();
        if let Some(span) = self.span {
            if let Some(loc) = sources.location(span) {
                out.push_str(&format!(
                    "{}:{}:{}: ",
                    loc.file_display, loc.line, loc.column
                ));
            }
        }
        let kind = match self.severity {
            Severity::Error => "error",
            Severity::Warning => "warning",
        };
        out.push_str(&format!("{} {}: {}", kind, self.code, self.message));
        out
    }
}

/// A set of diagnostics; compilation fails when it contains an error.
#[derive(Debug, Default)]
pub struct Diagnostics {
    pub items: Vec<Diagnostic>,
}

impl Diagnostics {
    pub fn push(&mut self, d: Diagnostic) {
        self.items.push(d);
    }

    pub fn err(&mut self, code: &'static str, message: impl Into<String>) {
        self.push(Diagnostic::new(code, message));
    }

    pub fn err_at(&mut self, code: &'static str, message: impl Into<String>, span: Span) {
        self.push(Diagnostic::new(code, message).at(span));
    }

    pub fn warn(&mut self, code: &'static str, message: impl Into<String>) {
        self.push(Diagnostic::warning(code, message));
    }

    pub fn warn_at(&mut self, code: &'static str, message: impl Into<String>, span: Span) {
        self.push(Diagnostic::warning(code, message).at(span));
    }

    pub fn is_empty(&self) -> bool {
        self.items.is_empty()
    }

    /// True when at least one item is an error. A warning-only set does not
    /// fail the compile.
    pub fn has_errors(&self) -> bool {
        self.items.iter().any(|d| d.severity == Severity::Error)
    }

    /// Print all diagnostics to stderr and return true iff an error was
    /// printed (i.e. the compile should fail). The "compilation failed"
    /// trailer is emitted only when an error is present.
    pub fn report(&self, sources: &SourceManager) -> bool {
        if self.items.is_empty() {
            return false;
        }
        for d in &self.items {
            eprintln!("{}", d.render(sources));
        }
        if self.has_errors() {
            eprintln!("error: compilation failed");
        }
        self.has_errors()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn warning_only_does_not_fail_compile() {
        let mut diags = Diagnostics::default();
        diags.warn_at(
            "W101",
            "local 'x' shadows declaration at line 1",
            Span::new(0, 0, 0),
        );
        // A warning-only set must not fail the compile.
        assert!(!diags.has_errors());
        assert!(!diags.report(&SourceManager::default()));
        // The rendered line carries the `warning` prefix, not `error`.
        let rendered = diags.items[0].render(&SourceManager::default());
        assert!(rendered.contains("warning W101:"), "got: {rendered}");
        assert!(!rendered.starts_with("error"));
    }

    #[test]
    fn error_and_warning_fail_compile() {
        let mut diags = Diagnostics::default();
        diags.err("C136", "unknown variable 'x'");
        diags.warn("W101", "local 'x' shadows declaration at line 1");
        assert!(diags.has_errors());
        assert!(diags.report(&SourceManager::default()));
    }
}
