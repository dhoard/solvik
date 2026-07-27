// Copyright (c) 2026-present Douglas Hoard
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// Package source provides source-file representation and position tracking.
package source

import (
	"fmt"
	"path/filepath"
)

// Pos represents a position within a source file.
type Pos struct {
	File   string
	Offset int // byte offset from start of file
	Line   int // 1-based
	Column int // 1-based
}

// Span represents a contiguous region of source text.
type Span struct {
	File   string
	Start  int // byte offset of first character
	End    int // byte offset past last character
	StartL int // 1-based start line
	StartC int // 1-based start column
	EndL   int // 1-based end line
	EndC   int // 1-based end column
}

// NewSpan creates a source span.
func NewSpan(file string, start, end int, startL, startC, endL, endC int) Span {
	return Span{
		File: file, Start: start, End: end,
		StartL: startL, StartC: startC,
		EndL: endL, EndC: endC,
	}
}

// SpanBetween creates a span covering the range from start to end.
func SpanBetween(start, end Pos) Span {
	return Span{
		File: start.File, Start: start.Offset, End: end.Offset,
		StartL: start.Line, StartC: start.Column,
		EndL: end.Line, EndC: end.Column,
	}
}

// SpanAt creates a point span at a given position.
func SpanAt(pos Pos) Span {
	return Span{File: pos.File, Start: pos.Offset, End: pos.Offset, StartL: pos.Line, StartC: pos.Column, EndL: pos.Line, EndC: pos.Column}
}

// String returns a human-readable span representation.
func (s Span) String() string {
	if s.StartL == s.EndL {
		return fmt.Sprintf("%s:%d:%d", shortFile(s.File), s.StartL, s.StartC)
	}
	return fmt.Sprintf("%s:%d:%d-%d:%d", shortFile(s.File), s.StartL, s.StartC, s.EndL, s.EndC)
}

func shortFile(path string) string {
	if path == "" {
		return "<unknown>"
	}
	base := filepath.Base(path)
	dir := filepath.Base(filepath.Dir(path))
	if dir == "." || dir == "" {
		return base
	}
	return dir + "/" + base
}

// Source represents a named source file with its content.
type Source struct {
	Name    string
	Content []byte
	lines   []int // start offsets of each line (0-indexed)
}

// NewSource creates a new source from content.
func NewSource(name string, content []byte) *Source {
	s := &Source{Name: name, Content: content}
	s.buildLines()
	return s
}

// NewSourceText creates a new source from a string.
func NewSourceText(name, text string) *Source {
	return NewSource(name, []byte(text))
}

// Text returns the source text for a given span.
func (s *Source) Text(span Span) string {
	if span.Start < 0 || span.End > len(s.Content) || span.Start > span.End {
		return ""
	}
	return string(s.Content[span.Start:span.End])
}

// PosFromOffset converts a byte offset to a line/column position.
func (s *Source) PosFromOffset(offset int) Pos {
	if offset < 0 {
		offset = 0
	}
	if offset >= len(s.Content) {
		offset = len(s.Content)
	}
	line := 1
	col := offset + 1
	for i := len(s.lines) - 1; i >= 0; i-- {
		if offset >= s.lines[i] {
			line = i + 1
			col = offset - s.lines[i] + 1
			break
		}
	}
	return Pos{File: s.Name, Offset: offset, Line: line, Column: col}
}

// SpanFromRange creates a span from an offset range.
func (s *Source) SpanFromRange(start, end int) Span {
	sp := s.PosFromOffset(start)
	ep := s.PosFromOffset(end)
	return SpanBetween(sp, ep)
}

// LineStart returns the byte offset of the start of the given line (1-based).
func (s *Source) LineStart(line int) int {
	if line < 1 || line > len(s.lines) {
		return 0
	}
	return s.lines[line-1]
}

// NumLines returns the number of lines.
func (s *Source) NumLines() int {
	return len(s.lines)
}

// LineContent returns the content of a line (1-based).
func (s *Source) LineContent(line int) string {
	if line < 1 || line > len(s.lines) {
		return ""
	}
	start := s.lines[line-1]
	var end int
	if line < len(s.lines) {
		end = s.lines[line] - 1 // exclude the newline
		if end >= 0 && end < len(s.Content) && s.Content[end] == '\r' {
			end--
		}
	} else {
		end = len(s.Content)
	}
	if start > end {
		return ""
	}
	return string(s.Content[start:end])
}

func (s *Source) buildLines() {
	s.lines = append(s.lines, 0)
	for i, b := range s.Content {
		if b == '\n' {
			s.lines = append(s.lines, i+1)
		}
	}
}
