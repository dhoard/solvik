package parser

import (
	"testing"

	"github.com/dhoard/solvik-language/internal/ast"
	"github.com/dhoard/solvik-language/internal/types"
)

func TestRecursiveTypeSyntax(t *testing.T) {
	prog := requireParseSuccess(t, `package test
func main() -> int {
    matrix: list<list<int>>
    values: map<string, list<int>>
    names: list<string?>
    users: map<string, Person?>
    nested: list<map<string, Person?>?>
    nullableMatrix: list<list<int>>?
    return 0
}
`)

	decls := make([]*ast.VariableDecl, 0, 6)
	for _, stmt := range prog.Funcs[0].Body.Statements {
		if decl, ok := stmt.(*ast.VariableDecl); ok {
			decls = append(decls, decl)
		}
	}
	if len(decls) != 6 {
		t.Fatalf("expected six type declarations, got %d", len(decls))
	}

	if got := decls[0].Type; got.Kind != types.KindList || got.Element == nil || got.Element.Kind != types.KindList || got.Element.Element == nil || got.Element.Element.Kind != types.KindInt {
		t.Fatalf("unexpected nested list type: %#v", got)
	}
	if got := decls[1].Type; got.Kind != types.KindMap || got.ValueType == nil || got.ValueType.Kind != types.KindList || got.ValueType.Element == nil || got.ValueType.Element.Kind != types.KindInt {
		t.Fatalf("unexpected map/list type: %#v", got)
	}
	if got := decls[2].Type; got.Element == nil || !got.Element.Nullable {
		t.Fatalf("expected nullable list element: %#v", got)
	}
	if got := decls[3].Type; got.ValueType == nil || !got.ValueType.Nullable {
		t.Fatalf("expected nullable map value: %#v", got)
	}
	if got := decls[4].Type; got.Element == nil || !got.Element.Nullable || got.Element.ValueType == nil || !got.Element.ValueType.Nullable {
		t.Fatalf("expected nullable nested map and map value: %#v", got)
	}
	if got := decls[5].Type; !got.Nullable || got.Element == nil || got.Element.Element == nil || got.Element.Element.Nullable {
		t.Fatalf("expected nullable outer list: %#v", got)
	}
}

func TestRecursiveTypeRejectsExtraGenericCloser(t *testing.T) {
	requireParseError(t, `package test
func main() -> int {
    value: list<int>>
    return 0
}
`, "P091")
}
