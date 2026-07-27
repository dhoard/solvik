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

package diagnostic_test

import (
	"testing"

	"github.com/dhoard/solvik-language/internal/diagnostic"
	"github.com/dhoard/solvik-language/internal/source"
)

func TestSeverity(t *testing.T) {
	tests := []struct {
		sev  diagnostic.Severity
		want string
	}{
		{diagnostic.SeverityError, "error"},
		{diagnostic.SeverityWarning, "warning"},
		{diagnostic.SeverityNote, "note"},
		{diagnostic.Severity(99), "unknown"},
	}
	for _, tt := range tests {
		if got := tt.sev.String(); got != tt.want {
			t.Errorf("Severity(%d).String() = %q, want %q", tt.sev, got, tt.want)
		}
	}
}

func TestNewError(t *testing.T) {
	span := source.NewSpan("test.sol", 0, 5, 1, 1, 1, 6)
	d := diagnostic.NewError("C001", "type mismatch", span)
	if d.Severity != diagnostic.SeverityError {
		t.Error("expected error severity")
	}
	if d.Code != "C001" || d.Message != "type mismatch" {
		t.Error("fields mismatch")
	}
	if d.Span.Start != 0 || d.Span.End != 5 {
		t.Error("span mismatch")
	}
}

func TestNewWarning(t *testing.T) {
	span := source.NewSpan("test.sol", 0, 3, 1, 1, 1, 4)
	d := diagnostic.NewWarning("W001", "unused variable", span)
	if d.Severity != diagnostic.SeverityWarning {
		t.Error("expected warning severity")
	}
}

func TestNewNote(t *testing.T) {
	span := source.NewSpan("test.sol", 0, 3, 1, 1, 1, 4)
	d := diagnostic.NewNote("N001", "see here", span)
	if d.Severity != diagnostic.SeverityNote {
		t.Error("expected note severity")
	}
}

func TestWithNote(t *testing.T) {
	span := source.NewSpan("test.sol", 0, 3, 1, 1, 1, 4)
	noteSpan := source.NewSpan("test.sol", 5, 10, 2, 1, 2, 6)
	d := diagnostic.NewError("C001", "main error", span).WithNote("additional context", noteSpan)
	if len(d.Notes) != 1 {
		t.Fatalf("expected 1 note, got %d", len(d.Notes))
	}
	if d.Notes[0].Message != "additional context" {
		t.Error("note message mismatch")
	}
	if d.Notes[0].Span.Start != 5 {
		t.Error("note span mismatch")
	}
}

func TestDiagnosticError(t *testing.T) {
	span := source.NewSpan("test.sol", 0, 3, 1, 1, 1, 4)
	d := diagnostic.NewError("C001", "type mismatch", span)
	errStr := d.Error()
	if errStr == "" {
		t.Error("Error() returned empty string")
	}
}

func TestDiagnosticsCollection(t *testing.T) {
	diags := diagnostic.NewDiagnostics()
	if diags == nil {
		t.Fatal("NewDiagnostics returned nil")
	}

	// Initially no errors
	if diags.HasErrors() {
		t.Error("expected no errors initially")
	}
	if len(diags.All()) != 0 {
		t.Error("expected empty all() initially")
	}

	span := source.NewSpan("test.sol", 0, 3, 1, 1, 1, 4)

	// Add an error
	diags.AddError("C001", "type error", span)
	if !diags.HasErrors() {
		t.Error("expected HasErrors after adding error")
	}
	if len(diags.All()) != 1 {
		t.Errorf("expected 1 diagnostic, got %d", len(diags.All()))
	}

	// Add a warning (should not cause HasErrors to change)
	diags.AddWarning("W001", "unused", span)
	if len(diags.All()) != 2 {
		t.Errorf("expected 2 diagnostics, got %d", len(diags.All()))
	}
	if !diags.HasErrors() {
		t.Error("HasErrors should still be true (error present)")
	}

	// Add a Diagnostic directly
	d := diagnostic.NewNote("N001", "note", span)
	diags.Add(d)
	if len(diags.All()) != 3 {
		t.Errorf("expected 3 diagnostics, got %d", len(diags.All()))
	}
}

func TestDiagnosticsNoErrors(t *testing.T) {
	diags := diagnostic.NewDiagnostics()
	span := source.NewSpan("test.sol", 0, 3, 1, 1, 1, 4)
	diags.AddWarning("W001", "warning only", span)
	diags.Add(diagnostic.NewNote("N001", "note only", span))
	if diags.HasErrors() {
		t.Error("expected no errors with only warnings and notes")
	}
}

func TestFormatDiagnostic(t *testing.T) {
	src := source.NewSourceText("test.sol", "x: int = 42\n")
	span := source.NewSpan("test.sol", 0, 1, 1, 1, 1, 2)
	d := diagnostic.NewError("C001", "type mismatch", span)
	output := diagnostic.FormatDiagnostic(d, src)
	if output == "" {
		t.Error("FormatDiagnostic returned empty")
	}
}

func TestFormatDiagnosticNoSource(t *testing.T) {
	span := source.NewSpan("test.sol", 0, 1, 1, 1, 1, 2)
	d := diagnostic.NewError("C001", "type mismatch", span)
	output := diagnostic.FormatDiagnostic(d, nil)
	if output == "" {
		t.Error("FormatDiagnostic with nil source returned empty")
	}
}

func TestFormatDiagnosticWithNotes(t *testing.T) {
	src := source.NewSourceText("test.sol", "a: int = 1\nb: string = 2\n")
	span := source.NewSpan("test.sol", 13, 14, 2, 10, 2, 11)
	noteSpan := source.NewSpan("test.sol", 0, 9, 1, 1, 1, 10)
	d := diagnostic.NewError("C002", "cannot assign int to string", span).
		WithNote("variable declared here", noteSpan)
	output := diagnostic.FormatDiagnostic(d, src)
	if output == "" {
		t.Error("FormatDiagnostic with notes returned empty")
	}
}

func TestFormatAll(t *testing.T) {
	src := source.NewSourceText("test.sol", "x: int = \"hello\"\n")
	span1 := source.NewSpan("test.sol", 0, 1, 1, 1, 1, 2)
	span2 := source.NewSpan("test.sol", 8, 15, 1, 9, 1, 16)
	diags := []diagnostic.Diagnostic{
		diagnostic.NewError("C001", "type mismatch", span1),
		diagnostic.NewWarning("W001", "unused variable", span2),
	}
	output := diagnostic.FormatAll(diags, src)
	if output == "" {
		t.Error("FormatAll returned empty")
	}
}

func TestFormatAllEmpty(t *testing.T) {
	output := diagnostic.FormatAll(nil, nil)
	if output != "" {
		t.Errorf("FormatAll(nil) should be empty, got %q", output)
	}
}

func TestCodeCategory(t *testing.T) {
	tests := []struct {
		code string
		want string
	}{
		{"L001", "lexer"},
		{"P001", "parser"},
		{"R001", "resolver"},
		{"C001", "checker"},
		{"E001", "runtime"},
		{"", "unknown"},
		{"X001", "unknown"},
	}
	for _, tt := range tests {
		if got := diagnostic.CodeCategory(tt.code); got != tt.want {
			t.Errorf("CodeCategory(%q) = %q, want %q", tt.code, got, tt.want)
		}
	}
}

func TestNewDiagnostics(t *testing.T) {
	diags := diagnostic.NewDiagnostics()
	if diags == nil {
		t.Fatal("NewDiagnostics returned nil")
	}
	// Should be usable immediately
	diags.AddError("C001", "test", source.NewSpan("", 0, 0, 0, 0, 0, 0))
}
