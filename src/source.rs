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
    /// Byte offsets of line starts, including the empty line after a final LF.
    line_starts: Vec<usize>,
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
        let line_starts = std::iter::once(0)
            .chain(
                text.bytes()
                    .enumerate()
                    .filter_map(|(i, b)| (b == b'\n').then_some(i + 1)),
            )
            .collect();
        self.files.push(SourceFile {
            path: path.to_string(),
            display,
            text,
            line_starts,
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
        let start = (span.start as usize).min(file.text.len());
        let line = file.line_starts.partition_point(|&offset| offset <= start);
        let line_start = file.line_starts[line - 1];
        let column = (start - line_start) as u32 + 1;
        Some(Location {
            file_display: file.display.clone(),
            line: line as u32,
            column,
        })
    }

    /// Allocation-free lookup for compiler line maps. Full diagnostics use
    /// `location` to obtain an owned filename and a byte-based column as well.
    pub fn line_number(&self, span: Span) -> Option<u32> {
        let file = self.files.get(span.file as usize)?;
        let start = (span.start as usize).min(file.text.len());
        Some(file.line_starts.partition_point(|&offset| offset <= start) as u32)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn indexed_locations_match_byte_scanning_at_every_offset() {
        for text in ["", "abc", "\n", "a\r\n\né🙂\nend\n"] {
            let mut sources = SourceManager::default();
            sources.add("test.sol", text.into());
            for offset in 0..text.len() + 4 {
                let span = Span::new(0, offset as u32, offset as u32);
                let prefix = &text.as_bytes()[..offset.min(text.len())];
                let line = 1 + prefix.iter().filter(|&&b| b == b'\n').count() as u32;
                let start = prefix
                    .iter()
                    .rposition(|&b| b == b'\n')
                    .map_or(0, |i| i + 1);
                let loc = sources.location(span).unwrap();
                assert_eq!(loc.line, line);
                assert_eq!(loc.column, (prefix.len() - start + 1) as u32);
                assert_eq!(sources.line_number(span), Some(line));
            }
            assert_eq!(sources.line_number(Span::new(99, 0, 0)), None);
        }
    }

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
