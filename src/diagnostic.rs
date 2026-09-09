//! Compile-time diagnostics with stable codes and source spans.
//!
//! Code families:
//! - `L###` lexical errors
//! - `P###` parse errors
//! - `C###` semantic / compile errors
//! - `V###` bytecode verification errors
//! - `E###` runtime errors (see SEMANTICS.md)

use crate::source::{SourceManager, Span};

#[derive(Debug, Clone, PartialEq)]
pub struct Diagnostic {
    pub code: &'static str,
    pub message: String,
    pub span: Option<Span>,
}

impl Diagnostic {
    pub fn new(code: &'static str, message: impl Into<String>) -> Self {
        Diagnostic {
            code,
            message: message.into(),
            span: None,
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
        out.push_str(&format!("error {}: {}", self.code, self.message));
        out
    }
}

/// A set of diagnostics; compilation fails when non-empty.
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

    pub fn is_empty(&self) -> bool {
        self.items.is_empty()
    }

    /// Print all diagnostics to stderr and return true if any were printed.
    pub fn report(&self, sources: &SourceManager) -> bool {
        if self.items.is_empty() {
            return false;
        }
        for d in &self.items {
            eprintln!("{}", d.render(sources));
        }
        eprintln!("error: compilation failed");
        true
    }
}
