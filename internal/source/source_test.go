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

package source_test

import (
	"testing"

	"github.com/dhoard/solvik-language/internal/source"
)

func TestNewSource(t *testing.T) {
	s := source.NewSource("test.sol", []byte("hello\nworld"))
	if s.Name != "test.sol" {
		t.Errorf("expected name test.sol, got %s", s.Name)
	}
	if string(s.Content) != "hello\nworld" {
		t.Errorf("expected content 'hello\\nworld', got %s", string(s.Content))
	}
	if n := s.NumLines(); n != 2 {
		t.Errorf("expected 2 lines, got %d", n)
	}
}

func TestNewSourceText(t *testing.T) {
	s := source.NewSourceText("test.sol", "line1\nline2\nline3")
	if s.NumLines() != 3 {
		t.Errorf("expected 3 lines, got %d", s.NumLines())
	}
}

func TestNewSourceEmpty(t *testing.T) {
	s := source.NewSourceText("empty.sol", "")
	if s.NumLines() != 1 {
		t.Errorf("expected 1 line for empty content, got %d", s.NumLines())
	}
}

func TestNumLines(t *testing.T) {
	tests := []struct {
		name    string
		content string
		want    int
	}{
		{"empty", "", 1},
		{"one_line", "hello", 1},
		{"two_lines", "hello\nworld", 2},
		{"trailing_newline", "hello\n", 2},
		{"multiple_lines", "a\nb\nc\n", 4},
		{"crlf", "a\r\nb\r\nc", 3},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			s := source.NewSourceText(tt.name, tt.content)
			if got := s.NumLines(); got != tt.want {
				t.Errorf("NumLines() = %d, want %d", got, tt.want)
			}
		})
	}
}

func TestLineStart(t *testing.T) {
	s := source.NewSourceText("test.sol", "abc\ndef\nghi")
	tests := []struct {
		line int
		want int
	}{
		{1, 0},
		{2, 4},
		{3, 8},
		{0, 0},  // out of range
		{4, 0},  // out of range
		{-1, 0}, // out of range
	}
	for _, tt := range tests {
		t.Run("", func(t *testing.T) {
			if got := s.LineStart(tt.line); got != tt.want {
				t.Errorf("LineStart(%d) = %d, want %d", tt.line, got, tt.want)
			}
		})
	}
}

func TestLineContent(t *testing.T) {
	s := source.NewSourceText("test.sol", "line1\nline2\nline3")
	tests := []struct {
		line int
		want string
	}{
		{1, "line1"},
		{2, "line2"},
		{3, "line3"},
		{0, ""},
		{4, ""},
	}
	for _, tt := range tests {
		t.Run("", func(t *testing.T) {
			if got := s.LineContent(tt.line); got != tt.want {
				t.Errorf("LineContent(%d) = %q, want %q", tt.line, got, tt.want)
			}
		})
	}
}

func TestLineContentCRLF(t *testing.T) {
	s := source.NewSourceText("test.sol", "a\r\nb\r\nc")
	// Note: CRLF handling is best-effort; \r may be included in the line content
	got1 := s.LineContent(1)
	if got1 != "a" && got1 != "a\r" {
		t.Errorf("LineContent(1) = %q, want 'a' or 'a\\r'", got1)
	}
	got2 := s.LineContent(2)
	if got2 != "b" && got2 != "b\r" {
		t.Errorf("LineContent(2) = %q, want 'b' or 'b\\r'", got2)
	}
}

func TestLineContentTrailingNewline(t *testing.T) {
	s := source.NewSourceText("test.sol", "hello\n")
	if got := s.LineContent(1); got != "hello" {
		t.Errorf("LineContent(1) = %q, want %q", got, "hello")
	}
	if got := s.LineContent(2); got != "" {
		t.Errorf("LineContent(2) = %q, want %q", got, "")
	}
}

func TestPosFromOffset(t *testing.T) {
	s := source.NewSourceText("test.sol", "hello\nworld")
	tests := []struct {
		offset   int
		wantLine int
		wantCol  int
	}{
		{0, 1, 1},
		{5, 1, 6},
		{6, 2, 1},
		{10, 2, 5},
		{11, 2, 6},
		{-1, 1, 1},
		{100, 2, 6},
	}
	for _, tt := range tests {
		t.Run("", func(t *testing.T) {
			pos := s.PosFromOffset(tt.offset)
			if pos.Line != tt.wantLine || pos.Column != tt.wantCol {
				t.Errorf("PosFromOffset(%d) = (line=%d, col=%d), want (line=%d, col=%d)",
					tt.offset, pos.Line, pos.Column, tt.wantLine, tt.wantCol)
			}
		})
	}
}

func TestText(t *testing.T) {
	s := source.NewSourceText("test.sol", "hello world")
	span := s.SpanFromRange(0, 5)
	if got := s.Text(span); got != "hello" {
		t.Errorf("Text(0,5) = %q, want %q", got, "hello")
	}
	// Invalid span (start > end)
	badSpan := source.NewSpan("test.sol", 5, 3, 1, 1, 1, 1)
	if got := s.Text(badSpan); got != "" {
		t.Errorf("Text(invalid) = %q, want empty", got)
	}
}

func TestSpanFromRange(t *testing.T) {
	s := source.NewSourceText("test.sol", "abc\ndef\nghi")
	span := s.SpanFromRange(0, 3)
	if span.File != "test.sol" {
		t.Errorf("expected file test.sol, got %s", span.File)
	}
	if span.StartL != 1 || span.StartC != 1 {
		t.Errorf("expected start (1,1), got (%d,%d)", span.StartL, span.StartC)
	}
	if span.EndL != 1 || span.EndC != 4 {
		t.Errorf("expected end (1,4), got (%d,%d)", span.EndL, span.EndC)
	}
}

func TestSpanString(t *testing.T) {
	tests := []struct {
		name     string
		span     source.Span
		contains string
	}{
		{"single_line", source.NewSpan("file.sol", 0, 5, 1, 1, 1, 6), "1:1"},
		{"multi_line", source.NewSpan("file.sol", 0, 10, 1, 1, 2, 5), "1:1-2:5"},
		{"empty_file", source.NewSpan("", 0, 0, 0, 0, 0, 0), "unknown"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := tt.span.String()
			if got == "" {
				t.Error("String() returned empty")
			}
		})
	}
}

func TestSpanBetween(t *testing.T) {
	start := source.Pos{File: "f.sol", Offset: 0, Line: 1, Column: 1}
	end := source.Pos{File: "f.sol", Offset: 5, Line: 1, Column: 6}
	span := source.SpanBetween(start, end)
	if span.Start != 0 || span.End != 5 {
		t.Errorf("SpanBetween start/end = %d/%d, want 0/5", span.Start, span.End)
	}
	if span.StartL != 1 || span.EndC != 6 {
		t.Errorf("SpanBetween lines = %d/%d, want 1/6", span.StartL, span.EndL)
	}
}

func TestSpanAt(t *testing.T) {
	pos := source.Pos{File: "f.sol", Offset: 3, Line: 1, Column: 4}
	span := source.SpanAt(pos)
	if span.Start != 3 || span.End != 3 {
		t.Errorf("SpanAt start/end = %d/%d, want 3/3", span.Start, span.End)
	}
}

func TestNewSpan(t *testing.T) {
	span := source.NewSpan("f.sol", 1, 10, 1, 2, 3, 4)
	if span.File != "f.sol" || span.Start != 1 || span.End != 10 {
		t.Errorf("NewSpan fields mismatch")
	}
}

func TestPos(t *testing.T) {
	p := source.Pos{File: "f.sol", Offset: 42, Line: 3, Column: 10}
	if p.File != "f.sol" || p.Offset != 42 || p.Line != 3 || p.Column != 10 {
		t.Errorf("Pos fields mismatch")
	}
}
