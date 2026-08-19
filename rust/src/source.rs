//! Source-file representation and position tracking.
//!
//! Port of internal/source/source.go.

use std::path::Path;

/// Position within a source file.
#[derive(Clone, Debug, Default, PartialEq)]
pub struct Pos {
    pub file: String,
    pub offset: usize,
    pub line: usize,  // 1-based
    pub column: usize, // 1-based
}

/// Contiguous region of source text.
#[derive(Clone, Debug, Default, PartialEq)]
pub struct Span {
    pub file: String,
    pub start: usize,
    pub end: usize,
    pub start_l: usize,
    pub start_c: usize,
    pub end_l: usize,
    pub end_c: usize,
}

impl Span {
    pub fn between(start: &Pos, end: &Pos) -> Span {
        Span {
            file: start.file.clone(),
            start: start.offset,
            end: end.offset,
            start_l: start.line,
            start_c: start.column,
            end_l: end.line,
            end_c: end.column,
        }
    }

    pub fn at(pos: &Pos) -> Span {
        Span {
            file: pos.file.clone(),
            start: pos.offset,
            end: pos.offset,
            start_l: pos.line,
            start_c: pos.column,
            end_l: pos.line,
            end_c: pos.column,
        }
    }

    /// Human-readable span representation, matching Go's Span.String().
    pub fn display(&self) -> String {
        let f = short_file(&self.file);
        if self.start_l == self.end_l {
            format!("{}:{}:{}", f, self.start_l, self.start_c)
        } else {
            format!("{}:{}:{}-{}:{}", f, self.start_l, self.start_c, self.end_l, self.end_c)
        }
    }
}

fn short_file(path: &str) -> String {
    if path.is_empty() {
        return "<unknown>".to_string();
    }
    let base = Path::new(path)
        .file_name()
        .map(|s| s.to_string_lossy().to_string())
        .unwrap_or_default();
    let dir = Path::new(path)
        .parent()
        .and_then(|p| p.file_name())
        .map(|s| s.to_string_lossy().to_string())
        .unwrap_or_default();
    if dir.is_empty() || dir == "." {
        base
    } else {
        format!("{}/{}", dir, base)
    }
}

/// Named source file with its content and line index.
pub struct Source {
    pub name: String,
    pub content: Vec<u8>,
    lines: Vec<usize>, // start offsets of each line
}

impl Source {
    pub fn new(name: &str, content: &str) -> Source {
        let content = content.as_bytes().to_vec();
        let mut lines = vec![0usize];
        for (i, b) in content.iter().enumerate() {
            if *b == b'\n' {
                lines.push(i + 1);
            }
        }
        Source {
            name: name.to_string(),
            content,
            lines,
        }
    }

    pub fn text(&self, span: &Span) -> String {
        if span.start > self.content.len() || span.end > self.content.len() || span.start > span.end {
            return String::new();
        }
        String::from_utf8_lossy(&self.content[span.start..span.end]).to_string()
    }

    pub fn pos_from_offset(&self, offset: usize) -> Pos {
        let mut offset = offset;
        if offset >= self.content.len() {
            offset = self.content.len();
        }
        let mut line = 1;
        let mut col = offset + 1;
        for i in (0..self.lines.len()).rev() {
            if offset >= self.lines[i] {
                line = i + 1;
                col = offset - self.lines[i] + 1;
                break;
            }
        }
        Pos {
            file: self.name.clone(),
            offset,
            line,
            column: col,
        }
    }

    pub fn span_from_range(&self, start: usize, end: usize) -> Span {
        let sp = self.pos_from_offset(start);
        let ep = self.pos_from_offset(end);
        Span::between(&sp, &ep)
    }

    pub fn num_lines(&self) -> usize {
        self.lines.len()
    }

    /// Content of a line (1-based), excluding the line terminator.
    pub fn line_content(&self, line: usize) -> String {
        if line < 1 || line > self.lines.len() {
            return String::new();
        }
        let start = self.lines[line - 1];
        let mut end;
        if line < self.lines.len() {
            end = self.lines[line].saturating_sub(1); // exclude the newline
            if end < self.content.len() && self.content[end] == b'\r' {
                end = end.saturating_sub(1);
            }
        } else {
            end = self.content.len();
        }
        if start > end {
            return String::new();
        }
        String::from_utf8_lossy(&self.content[start..end]).to_string()
    }
}
