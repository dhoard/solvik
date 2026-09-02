package reference

// Loader loads the entry file and its `use` dependencies, mirroring the
// Python Loader: package-name checks, cyclic detection, library main
// rejection, and builtin-namespace conflicts.

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

var builtinNamespaceNames = map[string]bool{
	"string": true, "math": true, "env": true, "file": true, "process": true,
	"time": true, "random": true, "path": true, "hash": true, "secrets": true, "base64": true,
}

type loader struct {
	interp   *Interpreter
	loaded   map[string]*Program
	loading  map[string]bool
	entryDir string
}

// RunProgram runs a Solvik file and returns (exitCode, stdout, error).
func RunProgram(path string, programArgs []string) (code int, stdout string, err error) {
	l := &loader{interp: NewInterpreter(), loaded: map[string]*Program{}, loading: map[string]bool{}}
	defer func() {
		if r := recover(); r != nil {
			if recovered, ok := r.(error); ok {
				err = recovered
			} else {
				err = newSolvikError("%v", r)
			}
			code = 2
			stdout = l.interp.stdout.String()
		}
	}()
	abs, err := filepath.Abs(path)
	if err != nil {
		return 1, "", newSolvikError("error: cannot read source file: %v", err)
	}
	l.entryDir = filepath.Dir(abs)
	prog, err := l.loadFile(abs, true, path)
	if err != nil {
		return 1, "", err
	}
	code, err = l.interp.runBytecode(prog.Package)
	if err != nil {
		return 2, l.interp.stdout.String(), err
	}
	return code, l.interp.stdout.String(), nil
}

// CheckProgram parses and validates without executing; returns an error with
// diagnostics otherwise.
func CheckProgram(path string) (err error) {
	l := &loader{interp: NewInterpreter(), loaded: map[string]*Program{}, loading: map[string]bool{}}
	defer func() {
		if r := recover(); r != nil {
			if recovered, ok := r.(error); ok {
				err = recovered
			} else {
				err = newSolvikError("%v", r)
			}
		}
	}()
	abs, err := filepath.Abs(path)
	if err != nil {
		return newSolvikError("error: cannot read source file: %v", err)
	}
	l.entryDir = filepath.Dir(abs)
	_, err = l.loadFile(abs, true, path)
	return err
}

func (l *loader) loadFile(path string, isEntry bool, sourceName string) (*Program, error) {
	if p, ok := l.loaded[path]; ok {
		return p, nil
	}
	if l.loading[path] {
		return nil, newSolvikError("cyclic dependency involving %s", path)
	}
	l.loading[path] = true
	defer delete(l.loading, path)
	srcBytes, err := os.ReadFile(path)
	if err != nil {
		return nil, newSolvikError("error: cannot read source file: %v", err)
	}
	src := string(srcBytes)
	registerSource(sourceName, src)
	prog, perr := parseSource(src, sourceName)
	if perr != nil {
		return nil, perr
	}
	if err := l.checkPackageName(prog, sourceName); err != nil {
		return nil, err
	}
	if !isEntry {
		for _, d := range prog.Declarations {
			if f, ok := d.(*FunctionDecl); ok && f.Name == "main" {
				return nil, newSolvikError("library file %s may not declare main", path)
			}
		}
	}
	l.loaded[path] = prog
	for _, use := range prog.Uses {
		dep := l.resolveUse(filepath.Dir(path), use)
		if dep == "" {
			return nil, newSolvikError("unsupported dependency scheme %s", use.Scheme)
		}
		if _, err := l.loadFile(dep, false, dep); err != nil {
			return nil, err
		}
	}
	l.interp.addProgram(prog)
	return prog, nil
}

func (l *loader) checkPackageName(prog *Program, path string) error {
	if builtinNamespaceNames[prog.Package] {
		return diagErr("C121", SourcePos{File: path, Line: 1, Col: 1}, 1,
			"package name '%s' conflicts with a built-in namespace; choose a different name", prog.Package)
	}
	return nil
}

func (l *loader) resolveUse(ownerDir string, use UseDecl) string {
	if use.Scheme != "file" {
		return ""
	}
	raw := os.ExpandEnv(use.Value)
	if !strings.ContainsAny(raw, "/\\") {
		raw = strings.ReplaceAll(raw, ".", string(os.PathSeparator))
	}
	if !strings.HasSuffix(raw, ".sol") {
		raw += ".sol"
	}
	if filepath.IsAbs(raw) {
		return raw
	}
	return filepath.Join(ownerDir, raw)
}

func fmtS(s string) string { return fmt.Sprintf("%s", s) }
