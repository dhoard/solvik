package reference

// TypeRef is a type reference: name, generic arguments, nullable flag.
// The zero value is not valid; use named constructors.
type TypeRef struct {
	Name     string
	Args     []TypeRef
	Nullable bool
}

func typeRef(name string, args ...TypeRef) TypeRef { return TypeRef{Name: name, Args: args} }
func typeRefN(name string, args []TypeRef) TypeRef { return TypeRef{Name: name, Args: args} }

func nullableRef(t TypeRef) TypeRef {
	t.Nullable = true
	return t
}

func (t TypeRef) nonnull() TypeRef {
	t.Nullable = false
	return t
}

func (t TypeRef) equal(o TypeRef) bool {
	if t.Name != o.Name || t.Nullable != o.Nullable || len(t.Args) != len(o.Args) {
		return false
	}
	for i := range t.Args {
		if !t.Args[i].equal(o.Args[i]) {
			return false
		}
	}
	return true
}

func (t TypeRef) String() string {
	body := t.Name
	if len(t.Args) > 0 {
		body += "<"
		for i, a := range t.Args {
			if i > 0 {
				body += ", "
			}
			body += a.String()
		}
		body += ">"
	}
	if t.Nullable {
		body += "?"
	}
	return body
}

var (
	anyT       = TypeRef{Name: "any"}
	voidT      = TypeRef{Name: "void"}
	exceptionT = TypeRef{Name: "exception"}
	unknownT   = TypeRef{Name: "<unknown>"}
	nullT      = TypeRef{Name: "null"}
	regexT     = TypeRef{Name: "regex"}
)

// TypeParam is a declared type parameter with trait constraints.
type TypeParam struct {
	Name        string
	Constraints []TypeRef
}

// Param is a function parameter.
type Param struct {
	Name     string
	Type     TypeRef
	Variadic bool
	Pos      *SourcePos
}

// FunctionDecl declares a function or method.
type FunctionDecl struct {
	Name        string
	Params      []Param
	ReturnType  TypeRef
	Body        *Block
	Pos         SourcePos
	Public      bool
	Mutating    bool
	OwnerStruct string
	TypeParams  []TypeParam
}

// FieldDecl declares a struct field.
type FieldDecl struct {
	Name    string
	Type    TypeRef
	Public  bool
	Mutable bool
	Pos     *SourcePos
}

// StructDecl declares a struct.
type StructDecl struct {
	Name       string
	Fields     []FieldDecl
	Methods    []*FunctionDecl
	Pos        SourcePos
	TypeParams []TypeParam
	Public     bool
}

// TraitDecl declares a trait.
type TraitDecl struct {
	Name       string
	Methods    []*FunctionDecl
	Pos        SourcePos
	TypeParams []TypeParam
	Public     bool
}

// EnumMember declares one enum case.
type EnumMember struct {
	Name         string
	Value        *int64
	PayloadTypes []TypeRef
}

// EnumDecl declares an enum.
type EnumDecl struct {
	Name       string
	Members    []EnumMember
	Pos        SourcePos
	TypeParams []TypeParam
	Public     bool
}

// Program is one parsed source file.
type Program struct {
	Package      string
	Uses         []UseDecl
	Declarations []any // *FunctionDecl | *StructDecl | *TraitDecl | *EnumDecl
	File         string
}

// UseDecl is a `use` directive.
type UseDecl struct {
	Scheme   string
	Value    string
	Checksum string
	Insecure bool
	Pos      SourcePos
}

// Statements.
type Block struct {
	Statements []any
	Pos        SourcePos
}
type VarDecl struct {
	Name    string
	Type    TypeRef
	Value   any
	Mutable bool
	Pos     SourcePos
}
type ExprStmt struct {
	Expr any
	Pos  SourcePos
}
type IfStmt struct {
	Condition  any
	ThenBlock  *Block
	ElseBranch any // *Block | *IfStmt | nil
	Pos        SourcePos
}
type WhileStmt struct {
	Condition any
	Body      *Block
	Pos       SourcePos
}
type ForStmt struct {
	Names    []string
	Iterable any
	Body     *Block
	Pos      SourcePos
}
type SwitchCase struct {
	Expr any // nil = default
	Body *Block
}
type SwitchStmt struct {
	Value any
	Cases []SwitchCase
	Pos   SourcePos
}
type TryStmt struct {
	TryBlock   *Block
	CatchName  string
	CatchType  *TypeRef
	CatchBlock *Block
	FinallyBlk *Block
	Pos        SourcePos
}
type ThrowStmt struct {
	Value any
	Pos   SourcePos
}
type ReturnStmt struct {
	Value any
	Pos   SourcePos
}
type BreakStmt struct{ Pos SourcePos }
type ContinueStmt struct{ Pos SourcePos }

// Expressions.
type Literal struct {
	Value       any
	LiteralKind string
	Pos         SourcePos
}
type Name struct {
	Name     string
	TypeArgs []TypeRef
	Pos      SourcePos
}
type Unary struct {
	Op   string
	Expr any
	Pos  SourcePos
}
type Binary struct {
	Left  any
	Op    string
	Right any
	Pos   SourcePos
}
type Assign struct {
	Target any
	Value  any
	Pos    SourcePos
}
type CallArg struct {
	Expr   any
	Spread bool
}
type Call struct {
	Callee       any
	Args         []CallArg
	Pos          SourcePos
	TypeArgs     []TypeRef
	expectedType any
}
type Member struct {
	Obj      any
	Name     string
	Pos      SourcePos
	TypeArgs []TypeRef
}
type Index struct {
	Obj   any
	Index any
	Pos   SourcePos
}
type ListExpr struct {
	Items []any
	Pos   SourcePos
}
type MapExpr struct {
	Items []mapEntry
	Pos   SourcePos
}
type mapEntry struct{ Key, Value any }
type StructExpr struct {
	TypeName     string
	Fields       []structField
	Pos          SourcePos
	TypeArgs     []TypeRef
	expectedType any
}
type structField struct {
	Name  string
	Value any
}
type FuncExpr struct {
	Params     []Param
	ReturnType TypeRef
	Body       *Block
	Pos        SourcePos
}
