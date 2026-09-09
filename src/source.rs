//! Source file management and span -> location mapping.//
// Data-structure fields retained for diagnostics/debugging/future use.
#![allow(dead_code)]

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Span {
    pub file: u32,
    pub start: u32,
    pub end: u32,
}

impl Span {
    pub fn new(file: u32, start: u32, end: u32) -> Self {
        Span { file, start, end }
    }

    pub fn join(self, other: Span) -> Span {
        Span {
            file: self.file,
            start: self.start.min(other.start),
            end: self.end.max(other.end),
        }
    }
}

#[derive(Debug, Clone)]
pub struct SourceFile {
    pub path: String,
    /// Display name used in diagnostics (relative when possible).
    pub display: String,
    pub text: String,
}

#[derive(Debug, Clone)]
pub struct Location {
    pub file_display: String,
    pub line: u32,
    pub column: u32,
}

#[derive(Default)]
pub struct SourceManager {
    files: Vec<SourceFile>,
}

impl SourceManager {
    pub fn add(&mut self, path: &str, text: String) -> u32 {
        let display = {
            let cwd = std::env::current_dir().unwrap_or_default();
            match std::path::Path::new(path).strip_prefix(&cwd) {
                Ok(rel) => rel.to_string_lossy().into_owned(),
                Err(_) => path.to_string(),
            }
        };
        self.files.push(SourceFile {
            path: path.to_string(),
            display,
            text,
        });
        (self.files.len() - 1) as u32
    }

    pub fn get(&self, id: u32) -> &SourceFile {
        &self.files[id as usize]
    }

    /// Display names in file-id order (for the bytecode source map).
    pub fn file_names(&self) -> Vec<String> {
        self.files.iter().map(|f| f.display.clone()).collect()
    }

    pub fn len(&self) -> usize {
        self.files.len()
    }

    pub fn is_empty(&self) -> bool {
        self.files.is_empty()
    }

    pub fn location(&self, span: Span) -> Option<Location> {
        let file = self.files.get(span.file as usize)?;
        let bytes = file.text.as_bytes();
        let start = (span.start as usize).min(bytes.len());
        let mut line = 1u32;
        for b in &bytes[..start] {
            if *b == b'\n' {
                line += 1;
            }
        }
        let line_start = bytes[..start]
            .iter()
            .rposition(|b| *b == b'\n')
            .map(|i| i + 1)
            .unwrap_or(0);
        let column = (start - line_start) as u32 + 1;
        Some(Location {
            file_display: file.display.clone(),
            line,
            column,
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn locations_handle_invalid_files_and_utf8_byte_offsets() {
        let mut sources = SourceManager::default();
        sources.add("unicode.sol", "é\nx".into());
        for start in 0..=5 {
            let loc = sources.location(Span::new(0, start, start)).unwrap();
            assert_eq!(loc.line, if start < 3 { 1 } else { 2 });
        }
        assert!(sources.location(Span::new(99, 0, 0)).is_none());
    }
}
