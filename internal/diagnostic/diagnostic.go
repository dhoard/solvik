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

// Package diagnostic provides shared diagnostics for the language toolchain.
package diagnostic

import (
	"fmt"
	"strings"
	"unicode/utf8"

	"github.com/dhoard/solvik-language/internal/source"
)

// Severity represents diagnostic severity.
type Severity int

const (
	// SeverityError indicates a violation that prevents further processing.
	SeverityError Severity = iota
	// SeverityWarning indicates a potential problem.
	SeverityWarning
	// SeverityNote indicates informational context.
	SeverityNote
)

func (s Severity) String() string {
	switch s {
	case SeverityError:
		return "error"
	case SeverityWarning:
		return "warning"
	case SeverityNote:
		return "note"
	default:
		return "unknown"
	}
}

// Diagnostic represents a single compiler or runtime diagnostic.
type Diagnostic struct {
	Severity Severity
	Code     string
	Message  string
	Span     source.Span
	Notes    []DiagnosticNote
}

// DiagnosticNote provides additional context for a diagnostic.
type DiagnosticNote struct {
	Message string
	Span    source.Span
}

// Error implements the error interface for diagnostics.
func (d Diagnostic) Error() string {
	return fmt.Sprintf("%s %s: %s", d.Severity, d.Code, d.Message)
}

// NewError creates a new error diagnostic.
func NewError(code, message string, span source.Span) Diagnostic {
	return Diagnostic{
		Severity: SeverityError,
		Code:     code,
		Message:  message,
		Span:     span,
	}
}

// NewWarning creates a new warning diagnostic.
func NewWarning(code, message string, span source.Span) Diagnostic {
	return Diagnostic{
		Severity: SeverityWarning,
		Code:     code,
		Message:  message,
		Span:     span,
	}
}

// NewNote creates a new note diagnostic.
func NewNote(code, message string, span source.Span) Diagnostic {
	return Diagnostic{
		Severity: SeverityNote,
		Code:     code,
		Message:  message,
		Span:     span,
	}
}

// WithNote appends a note to a diagnostic.
func (d Diagnostic) WithNote(message string, span source.Span) Diagnostic {
	d.Notes = append(d.Notes, DiagnosticNote{Message: message, Span: span})
	return d
}

// Diagnostics is a collection of diagnostics.
type Diagnostics struct {
	items []Diagnostic
}

// NewDiagnostics creates a new diagnostics collection.
func NewDiagnostics() *Diagnostics {
	return &Diagnostics{}
}

// Add adds a diagnostic to the collection.
func (d *Diagnostics) Add(diag Diagnostic) {
	d.items = append(d.items, diag)
}

// AddError adds an error diagnostic.
func (d *Diagnostics) AddError(code, message string, span source.Span) {
	d.Add(NewError(code, message, span))
}

// AddWarning adds a warning diagnostic.
func (d *Diagnostics) AddWarning(code, message string, span source.Span) {
	d.Add(NewWarning(code, message, span))
}

// HasErrors returns true if any error diagnostics are present.
func (d *Diagnostics) HasErrors() bool {
	for _, diag := range d.items {
		if diag.Severity == SeverityError {
			return true
		}
	}
	return false
}

// All returns all diagnostics.
func (d *Diagnostics) All() []Diagnostic {
	return d.items
}

// FormatDiagnostic formats a diagnostic for human-readable output.
// Produces Rust-like formatted output:
//
//	error E0123: message
//	  --> file.sol:4:9
//	   |
//	 4 | source line
//	   |        ^^^^^^ annotation
//
// The source snippet is only rendered when src matches the file named by
// the diagnostic's span, so a diagnostic that points into a dependency
// file never shows a source line from a different file.
func FormatDiagnostic(diag Diagnostic, src *source.Source) string {
	var b strings.Builder

	// Header: lowercase severity + code + message
	b.WriteString(fmt.Sprintf("%s %s: %s\n", diag.Severity.String(), diag.Code, diag.Message))

	// Position header; synthetic zero spans (e.g. "too many parse errors")
	// carry no location, so the arrow line is suppressed entirely.
	hasPosition := diag.Span.File != "" || diag.Span.StartL > 0
	if hasPosition {
		b.WriteString(fmt.Sprintf("  --> %s\n", diag.Span.String()))
	}

	// Source context — only when the provided source matches the span's file
	if src != nil && hasPosition && src.Name == diag.Span.File && diag.Span.StartL > 0 {
		line := diag.Span.StartL
		lineStr := src.LineContent(line)
		lineWidth := len(fmt.Sprintf("%d", diag.Span.EndL))
		padding := strings.Repeat(" ", lineWidth)

		b.WriteString(fmt.Sprintf(" %s |\n", padding))

		if lineStr != "" {
			lineNum := fmt.Sprintf("%*d", lineWidth, line)
			b.WriteString(fmt.Sprintf(" %s | %s\n", lineNum, lineStr))

			// Underline. Columns are byte-based; count runes so the caret
			// aligns visually on lines containing multi-byte characters.
			startCol := diag.Span.StartC - 1
			endCol := diag.Span.EndC - 1
			if endCol > len(lineStr) {
				endCol = len(lineStr)
			}
			if startCol < 0 {
				startCol = 0
			}
			if startCol > endCol {
				// Multi-line span: a single caret at the start position.
				endCol = startCol
			}
			prefix := lineStr
			if startCol < len(prefix) {
				prefix = lineStr[:startCol]
			}
			under := lineStr[startCol:endCol]
			width := utf8.RuneCountInString(under)
			if width < 1 {
				width = 1
			}
			caret := strings.Repeat(" ", utf8.RuneCountInString(prefix)) + strings.Repeat("^", width)
			b.WriteString(fmt.Sprintf(" %s | %s\n", padding, caret))
		}

		// Show extra lines if multiline span
		for l := diag.Span.StartL + 1; l <= diag.Span.EndL; l++ {
			lineStr := src.LineContent(l)
			if lineStr != "" {
				lineNum := fmt.Sprintf("%*d", lineWidth, l)
				b.WriteString(fmt.Sprintf(" %s | %s\n", lineNum, lineStr))
			}
		}
		b.WriteString(fmt.Sprintf(" %s |\n", padding))
	}

	// Notes
	for _, note := range diag.Notes {
		b.WriteString(fmt.Sprintf("  = note: %s\n", note.Message))
		if note.Span.File != "" && note.Span.StartL > 0 {
			b.WriteString(fmt.Sprintf("         --> %s\n", note.Span.String()))
		}
	}

	return b.String()
}

// FormatAll formats all diagnostics for output.
func FormatAll(diags []Diagnostic, src *source.Source) string {
	var b strings.Builder
	for i, d := range diags {
		if i > 0 {
			b.WriteString("\n")
		}
		b.WriteString(FormatDiagnostic(d, src))
	}
	return b.String()
}
