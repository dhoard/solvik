package dev.solvik.compiler.parser;

import dev.solvik.compiler.ast.Ast;
import dev.solvik.compiler.diagnostic.Diagnostics;
import dev.solvik.compiler.diagnostic.SourcePos;
import dev.solvik.compiler.lexer.Token;
import dev.solvik.compiler.lexer.TokenKind;
import java.util.ArrayList;
import java.util.List;

/**
 * Recursive-descent parser for Solvik, operating on the token list from {@link
 * dev.solvik.compiler.lexer.Lexer}.
 *
 * <p>Precedence follows {@code LANGUAGE.md} §7 exactly, loosest first:
 * {@code ||}, {@code &&}, equality ({@code == != ===}), comparison, concatenation {@code ..},
 * additive, multiplicative, unary, postfix, primary.
 *
 * <p>Newlines are significant only between statements: a newline ends a statement outside a
 * delimited construct and is layout inside one, so parenthesized expressions, argument lists,
 * parameter lists, array literals, index expressions, type arguments, and type bodies may span
 * lines (§1). The lexer has already removed newlines canceled by a continuation line.
 */
public final class Parser {

    public static final String UNEXPECTED_TOKEN = "SOL-P001";
    public static final String EXPECTED_TOKEN = "SOL-P002";
    public static final String INVALID_STATEMENT = "SOL-P003";
    public static final String INVALID_DECLARATION = "SOL-P004";
    public static final String ARRAY_SPELLING = "SOL-P005";
    /**
     * Syntax this revision removed: the {@code var} keyword, the lowercase {@code null} literal, and
     * the {@code import} declarations replaced by {@code include} and {@code alias} (§10.1, §10.2).
     */
    public static final String REMOVED_SYNTAX = "SOL-P006";
    /** A binding, field, or array-type spelling that does not follow the declaration grammar (§3). */
    public static final String DECLARATION_FORM = "SOL-P007";
    /** A type spelling that is not a type, such as a repeated {@code ?} suffix (§2.1). */
    public static final String TYPE_FORM = "SOL-P008";

    private final List<Token> tokens;
    private final Diagnostics diagnostics;
    private int index;
    /**
     * How many {@code (}, {@code [}, or type-argument {@code <} constructs are open (§1).
     *
     * <p>Positive while the parser is inside a delimited construct, where a newline is layout and
     * not a statement terminator.
     */
    private int delimiterDepth;
    private boolean nativeBody;
    private boolean traitBody;

    public Parser(List<Token> tokens, Diagnostics diagnostics) {
        this.tokens = tokens;
        this.diagnostics = diagnostics;
    }

    /** Parses a whole source file. */
    public Ast.Program parseProgram() {
        SourcePos start = peek().pos();
        List<Ast.TypeDecl> types = new ArrayList<>();
        List<Ast.FuncDecl> functions = new ArrayList<>();
        List<Ast.Stmt> statements = new ArrayList<>();
        List<Ast.ModuleDecl> modules = new ArrayList<>();
        List<Ast.IncludeDecl> includes = new ArrayList<>();
        List<Ast.AliasDecl> aliases = new ArrayList<>();
        while (!check(TokenKind.EOF)) {
            if (match(TokenKind.NEWLINE) || match(TokenKind.SEMICOLON)) {
                continue;
            }
            if (check(TokenKind.INCLUDE)) {
                includes.addAll(parseIncludeBlock());
            } else if (check(TokenKind.ALIAS)) {
                aliases.addAll(parseAliasBlock());
            } else if (check(TokenKind.IMPORT)) {
                reportRemovedImport();
            } else if (check(TokenKind.MODULE)) {
                modules.add(parseModuleDecl());
            } else if (check(TokenKind.TYPE) || check(TokenKind.TRAIT)) {
                types.add(parseTypeDecl(false, check(TokenKind.TRAIT)));
            } else if (check(TokenKind.FUNC) || check(TokenKind.NATIVE)) {
                functions.add(parseFuncDecl(false));
            } else if (check(TokenKind.PUB)) {
                errorAt(INVALID_DECLARATION, peek().pos(),
                        "'pub' marks a module declaration visible in other files; "
                                + "it is only valid inside a module");
                advance();
            } else if (check(TokenKind.STATIC)) {
                reportMisplacedStatic();
                advance();
            } else {
                statements.add(parseStatement());
                consumeTerminators();
            }
        }
        return new Ast.Program(types, functions, statements, modules, includes, aliases, start);
    }

    // ------------------------------------------------------------ declarations

    /**
     * Parses an {@code include { "path.sol" ... }} block (§3, "Source files").
     *
     * <p>The block is mandatory, even for one entry, and entries are separated by a newline or
     * {@code ;}: an entry may follow {@code &#123;} directly, and a separator may follow the last
     * entry before {@code &#125;}. Paths are string literals kept exactly as written; the loader
     * resolves them, because a single source compiled from memory has no directory to resolve
     * against. Commas are not separators, and adjacent entries need one.
     */
    private List<Ast.IncludeDecl> parseIncludeBlock() {
        Token keyword = advance(); // 'include'
        List<Ast.IncludeDecl> entries = new ArrayList<>();
        if (!check(TokenKind.LBRACE)) {
            errorAt(EXPECTED_TOKEN, peek().pos(),
                    "'include' requires a block: write include { \"path.sol\" }, found "
                            + peek());
            recoverEntry();
            return entries;
        }
        advance();
        int before = diagnostics.size();
        boolean separated = true;   // an entry may follow '{' without a separator
        while (!check(TokenKind.RBRACE) && !check(TokenKind.EOF)) {
            if (match(TokenKind.NEWLINE) || match(TokenKind.SEMICOLON)) {
                separated = true;
                continue;
            }
            if (check(TokenKind.COMMA)) {
                reportCommaNotSeparator();
                advance();
                separated = true;
                continue;
            }
            requireSeparator(separated, "include");
            if (check(TokenKind.STRING_LITERAL)) {
                Token path = advance();
                entries.add(new Ast.IncludeDecl(path.value(), path.pos()));
            } else {
                errorAt(EXPECTED_TOKEN, peek().pos(),
                        "an include entry is a quoted file path, found " + peek());
                recoverEntry();
            }
            separated = false;
        }
        expect(TokenKind.RBRACE, "expected '}' to close the include block");
        if (entries.isEmpty() && diagnostics.size() == before) {
            errorAt(DECLARATION_FORM, keyword.pos(),
                    "an include block names at least one file: write include { \"path.sol\" }");
        }
        return entries;
    }

    /**
     * Parses an {@code alias { module::Type Name ... }} block (§3, "Source files").
     *
     * <p>Each entry writes the canonical target first and the file-local name second, and entries
     * follow the same newline/{@code ;} separator rules as an include block.
     */
    private List<Ast.AliasDecl> parseAliasBlock() {
        Token keyword = advance(); // 'alias'
        List<Ast.AliasDecl> entries = new ArrayList<>();
        if (!check(TokenKind.LBRACE)) {
            errorAt(EXPECTED_TOKEN, peek().pos(),
                    "'alias' requires a block: write alias { module::Type Name }, found " + peek());
            recoverEntry();
            return entries;
        }
        advance();
        int before = diagnostics.size();
        boolean separated = true;
        while (!check(TokenKind.RBRACE) && !check(TokenKind.EOF)) {
            if (match(TokenKind.NEWLINE) || match(TokenKind.SEMICOLON)) {
                separated = true;
                continue;
            }
            if (check(TokenKind.COMMA)) {
                reportCommaNotSeparator();
                advance();
                separated = true;
                continue;
            }
            requireSeparator(separated, "alias");
            Ast.AliasDecl entry = parseAliasEntry();
            if (entry != null) {
                entries.add(entry);
            }
            separated = false;
        }
        expect(TokenKind.RBRACE, "expected '}' to close the alias block");
        if (entries.isEmpty() && diagnostics.size() == before) {
            errorAt(DECLARATION_FORM, keyword.pos(),
                    "an alias block names at least one target: write alias { module::Type Name }");
        }
        return entries;
    }

    /** One {@code qualified-name IDENTIFIER} entry, or {@code null} after reporting a failure. */
    private Ast.AliasDecl parseAliasEntry() {
        if (!check(TokenKind.IDENTIFIER)) {
            errorAt(EXPECTED_TOKEN, peek().pos(),
                    "an alias entry is a qualified target followed by a local name, found "
                            + peek());
            recoverEntry();
            return null;
        }
        Token first = advance();
        StringBuilder target = new StringBuilder(first.lexeme());
        while (match(TokenKind.COLON_COLON)) {
            if (!check(TokenKind.IDENTIFIER)) {
                errorAt(EXPECTED_TOKEN, peek().pos(),
                        "expected a name after '::', found " + peek());
                recoverEntry();
                return null;
            }
            target.append("::").append(advance().lexeme());
        }
        if (!check(TokenKind.IDENTIFIER)) {
            errorAt(EXPECTED_TOKEN, peek().pos(),
                    "expected a local alias name after the target, found " + peek());
            recoverEntry();
            return null;
        }
        Token local = advance();
        return new Ast.AliasDecl(target.toString(), local.lexeme(), first.pos(), local.pos());
    }

    /** Reports an adjacent entry, which the grammar requires to be separated. */
    private void requireSeparator(boolean separated, String block) {
        if (separated) {
            return;
        }
        errorAt(EXPECTED_TOKEN, peek().pos(), block
                + " entries are separated by a newline or ';', found " + peek());
    }

    /** Reports a comma inside a block, where only a newline or {@code ;} separates entries. */
    private void reportCommaNotSeparator() {
        errorAt(EXPECTED_TOKEN, peek().pos(),
                "a comma is not an entry separator; separate entries with a newline or ';'");
    }

    /**
     * Rejects the removed {@code import} spelling, naming both replacements.
     *
     * <p>It is reported once and skipped, so a file that still imports does not also produce a
     * cascade of statement and declaration errors.
     */
    private void reportRemovedImport() {
        Token keyword = advance(); // 'import'
        errorAt(REMOVED_SYNTAX, keyword.pos(), "'import' was replaced: include source files with"
                + " 'include { \"path.sol\" }' and name existing symbols with"
                + " 'alias { module::Type Name }'");
        if (check(TokenKind.LBRACE)) {
            skipBracedBlock();
        } else {
            recoverEntry();
        }
    }

    /**
     * Rejects an {@code include} or {@code alias} block outside a file's top level.
     *
     * <p>The keyword and its braced block are skipped, so the enclosing module, type, or function
     * body keeps its own closing brace and reports at most this one placement error.
     */
    private void recoverFileLevelBlock() {
        Token keyword = advance();
        errorAt(INVALID_DECLARATION, keyword.pos(), "'" + keyword.lexeme()
                + "' is a file-level declaration; move the block outside the enclosing body");
        if (check(TokenKind.LBRACE)) {
            skipBracedBlock();
        }
        synchronize();
    }

    /** Skips a balanced {@code { ... }} block, starting at its opening brace. */
    private void skipBracedBlock() {
        int depth = 0;
        do {
            Token token = advance();
            if (token.kind() == TokenKind.LBRACE) {
                depth++;
            } else if (token.kind() == TokenKind.RBRACE) {
                depth--;
            }
        } while (depth > 0 && !check(TokenKind.EOF));
    }

    /**
     * Skips to the next entry boundary: the newline, {@code ;}, {@code &#125;}, or end of input.
     *
     * <p>The boundary itself is left for the caller, so a block loop still sees its closing brace.
     */
    private void recoverEntry() {
        while (!check(TokenKind.EOF) && !check(TokenKind.NEWLINE)
                && !check(TokenKind.SEMICOLON) && !check(TokenKind.RBRACE)) {
            advance();
        }
    }

    /**
     * Parses {@code module Name { type ... } } (§2, "Modules").
     *
     * <p>A module holds type declarations and nothing that executes, so a statement or a variable
     * in the body is rejected here rather than by the checker.
     */
    private Ast.ModuleDecl parseModuleDecl() {
        Token keyword = expect(TokenKind.MODULE, "expected 'module'");
        Token name = expect(TokenKind.IDENTIFIER, "expected a module name after 'module'");
        expect(TokenKind.LBRACE, "expected '{' to open the module");
        List<Ast.TypeDecl> types = new ArrayList<>();
        List<Ast.FuncDecl> functions = new ArrayList<>();
        List<Ast.ModuleDecl> modules = new ArrayList<>();
        skipNewlines();
        while (!check(TokenKind.RBRACE) && !check(TokenKind.EOF)) {
            if (match(TokenKind.SEMICOLON)) {
                continue;
            }
            if (check(TokenKind.MODULE)) {
                modules.add(parseModuleDecl());
            } else if (check(TokenKind.TYPE) || check(TokenKind.TRAIT)) {
                types.add(parseTypeDecl(false, check(TokenKind.TRAIT)));
            } else if (check(TokenKind.PUB)
                    && (lookahead(1) == TokenKind.TYPE || lookahead(1) == TokenKind.TRAIT)) {
                advance();
                types.add(parseTypeDecl(true, check(TokenKind.TRAIT)));
            } else if (check(TokenKind.STATIC)) {
                reportMisplacedStatic();
                synchronize();
            } else if (startsMethod()) {
                functions.add(parseFuncDecl(false));
            } else if (check(TokenKind.INCLUDE) || check(TokenKind.ALIAS)) {
                recoverFileLevelBlock();
            } else {
                errorAt(INVALID_DECLARATION, peek().pos(),
                        "a module holds type, function, and nested module declarations");
                synchronize();
            }
            skipNewlines();
        }
        expect(TokenKind.RBRACE, "expected '}' to close the module");
        return new Ast.ModuleDecl(name.lexeme(), types, functions, modules, keyword.pos());
    }

    private Ast.TypeDecl parseTypeDecl(boolean isPublic, boolean trait) {
        Token typeKeyword = expect(trait ? TokenKind.TRAIT : TokenKind.TYPE,
                trait ? "expected 'trait'" : "expected 'type'");
        Token name = expect(TokenKind.IDENTIFIER,
                trait ? "expected a trait name after 'trait'" : "expected a type name after 'type'");
        List<String> typeParameters = new ArrayList<>();
        if (check(TokenKind.LT)) {
            delimited(() -> {
                advance();
                do {
                    Token parameter = expect(TokenKind.IDENTIFIER, "expected a type parameter name");
                    typeParameters.add(parameter.lexeme());
                    if (parameter.lexeme().isEmpty()
                            || !Character.isUpperCase(parameter.lexeme().charAt(0))) {
                        errorAt(INVALID_DECLARATION, parameter.pos(),
                                "a type parameter must start with an uppercase letter");
                    }
                } while (match(TokenKind.COMMA));
                expect(TokenKind.GT, "expected '>' to close the type parameters");
            });
        }
        List<Ast.TypeRef> implementedTraits = new ArrayList<>();
        if (match(TokenKind.IMPLEMENTS)) {
            do {
                implementedTraits.add(parseType());
            } while (match(TokenKind.COMMA));
        }
        String nativeClass = match(TokenKind.NATIVE)
                ? expect(TokenKind.STRING_LITERAL, "expected a Java class name").value() : null;
        boolean previousNative = nativeBody;
        boolean previousTrait = traitBody;
        nativeBody = nativeClass != null;
        traitBody = trait;
        expect(TokenKind.LBRACE, "expected '{' to open the type body");
        List<Ast.FieldDecl> fields = new ArrayList<>();
        List<Ast.FuncDecl> methods = new ArrayList<>();
        List<Ast.StaticInitializer> staticInitializers = new ArrayList<>();
        skipNewlines();
        while (!check(TokenKind.RBRACE) && !check(TokenKind.EOF)) {
            if (startsStaticInitializer()) {
                Token keyword = advance();
                if (trait) {
                    errorAt(INVALID_DECLARATION, keyword.pos(),
                            "a trait contains only public instance functions");
                }
                if (nativeClass != null) {
                    errorAt(INVALID_DECLARATION, keyword.pos(),
                            "a native type binding cannot declare a static initializer");
                }
                if (!staticInitializers.isEmpty()) {
                    errorAt(INVALID_DECLARATION, keyword.pos(),
                            "a type declares at most one static initializer block");
                }
                skipNewlines();
                List<Ast.Stmt> body = parseBlock();
                if (!trait && nativeClass == null && staticInitializers.isEmpty()) {
                    staticInitializers.add(new Ast.StaticInitializer(body, fields.size(),
                            keyword.pos()));
                }
                consumeTerminators();
            } else if (startsMethod()) {
                methods.add(parseFuncDecl(true));
            } else if (startsField()) {
                if (trait) {
                    errorAt(INVALID_DECLARATION, peek().pos(),
                            "a trait contains only public instance functions");
                }
                fields.add(parseField(name.lexeme()));
                consumeTerminators();
            } else if (check(TokenKind.STATIC) && lookahead(1) == TokenKind.TYPE) {
                errorAt(INVALID_DECLARATION, peek().pos(),
                        "'static' identifies type members; it cannot be applied to a type declaration");
                synchronize();
            } else if (check(TokenKind.INCLUDE) || check(TokenKind.ALIAS)) {
                recoverFileLevelBlock();
            } else {
                errorAt(INVALID_DECLARATION, peek().pos(),
                        "a type body contains field and function declarations");
                synchronize();
            }
            skipNewlines();
        }
        expect(TokenKind.RBRACE, "expected '}' to close the type body");
        nativeBody = previousNative;
        traitBody = previousTrait;
        return new Ast.TypeDecl(name.lexeme(), typeParameters, fields, methods, isPublic,
                typeKeyword.pos(), nativeClass,
                trait ? Ast.TypeKind.TRAIT : Ast.TypeKind.TYPE,
                List.copyOf(implementedTraits), List.copyOf(staticInitializers));
    }

    /**
     * A field declaration: {@code [static] [pub] [mut] name[: Type] :=|= initializer} (§3.3).
     *
     * <p>The initializer is required: there is no uninitialized field slot, so a field's type is
     * known where it is declared (§3.3).
     */
    private Ast.FieldDecl parseField(String typeName) {
        SourcePos pos = peek().pos();
        boolean isPublic = false;
        boolean isMutable = false;
        boolean isStatic = false;
        int rank = 0;
        while (check(TokenKind.PUB) || check(TokenKind.MUT) || check(TokenKind.STATIC)) {
            if (check(TokenKind.STATIC)) {
                Token modifier = advance();
                rank = checkModifierOrder(rank, 0, modifier);
                if (isStatic) {
                    errorAt(INVALID_DECLARATION, pos, "a field declares 'static' only once");
                }
                isStatic = true;
            } else if (check(TokenKind.PUB)) {
                Token modifier = advance();
                rank = checkModifierOrder(rank, 1, modifier);
                if (isPublic) {
                    errorAt(INVALID_DECLARATION, pos, "a field declares 'pub' only once");
                }
                isPublic = true;
            } else {
                Token modifier = advance();
                rank = checkModifierOrder(rank, 2, modifier);
                if (isMutable) {
                    errorAt(INVALID_DECLARATION, pos, "a field declares 'mut' only once");
                }
                isMutable = true;
            }
        }
        if (check(TokenKind.VAR)) {
            reportRemovedVar("a type body declares a field with 'name := value', "
                    + "'mut name := value', or 'name: Type = value'");
            advance();
            synchronize();
            return new Ast.FieldDecl("$error", null, false, new Ast.NullLit(pos), false, false,
                    false, pos);
        }
        Token name = expect(TokenKind.IDENTIFIER, "expected a field name");
        Ast.TypeRef type = null;
        boolean nullableInferred = false;
        if (match(TokenKind.QUESTION)) {
            nullableInferred = true;
        }
        boolean missingInitializer = false;
        if (match(TokenKind.COLON)) {
            type = parseDeclaredType(name.lexeme(), nullableInferred);
            if (!match(TokenKind.ASSIGN)) {
                errorAt(DECLARATION_FORM, peek().pos(),
                        "an explicitly typed declaration writes '= initializer'");
                missingInitializer = true;
            }
        } else if (!match(TokenKind.DECLARE)) {
            errorAt(DECLARATION_FORM, peek().pos(), nullableInferred
                    ? "a nullable inferred declaration writes '" + name.lexeme() + "? := value'"
                    : "a declaration writes ':=' before its initializer; "
                            + "'=' assigns to an existing binding");
            missingInitializer = true;
        }
        Ast.Expr initializer = missingInitializer && atTerminator()
                ? null : parseExpression();
        return new Ast.FieldDecl(name.lexeme(), type, nullableInferred, initializer, isPublic,
                isMutable, isStatic, pos);
    }

    /**
     * Reports modifiers written out of the documented order and returns the rank to carry on.
     *
     * <p>A declaration writes {@code static} first, then {@code pub}, then {@code mut} (§3.3).
     */
    private int checkModifierOrder(int previous, int rank, Token modifier) {
        if (rank < previous) {
            errorAt(INVALID_DECLARATION, modifier.pos(),
                    "a declaration writes its modifiers in the order 'static', 'pub', 'mut'");
        }
        return rank;
    }

    /**
     * The written type of an explicitly typed declaration, refusing a second nullability marker.
     *
     * <p>{@code name?: Type = value} mixes the two declaration styles; the explicit form spells
     * nullability in the type ({@code name: Type? = value}) (§3.2).
     */
    private Ast.TypeRef parseDeclaredType(String name, boolean nullableInferred) {
        SourcePos pos = peek().pos();
        Ast.TypeRef type = parseType();
        if (nullableInferred) {
            errorAt(DECLARATION_FORM, pos, "'" + name
                    + "' marks nullability twice: write '" + name + ": " + type
                    + "? = value' or '" + name + "? := value'");
        }
        return type;
    }

    private Ast.FuncDecl parseFuncDecl(boolean allowStatic) {
        SourcePos firstPos = peek().pos();
        boolean isPublic = false;
        boolean isStatic = false;
        boolean isNative = false;
        while (check(TokenKind.PUB) || check(TokenKind.STATIC) || check(TokenKind.NATIVE)) {
            if (match(TokenKind.PUB)) {
                if (isPublic) {
                    errorAt(INVALID_DECLARATION, firstPos, "a function declares 'pub' only once");
                }
                isPublic = true;
            } else if (match(TokenKind.NATIVE)) {
                if (isNative) {
                    errorAt(INVALID_DECLARATION, peek().pos(),
                            "a function declares 'native' only once");
                }
                isNative = true;
            } else {
                Token modifier = advance();
                if (isStatic) {
                    errorAt(INVALID_DECLARATION, modifier.pos(),
                            "a function declares 'static' only once");
                }
                isStatic = true;
                if (!allowStatic) {
                    errorAt(INVALID_DECLARATION, modifier.pos(),
                            "'static' makes a type member belong to its type; "
                                    + "it is only valid inside a type body");
                }
            }
        }
        if (check(TokenKind.MUT)) {
            errorAt(INVALID_DECLARATION, peek().pos(),
                    "'mut' declares a mutable field; a function is not mutable");
            advance();
        }
        Token funcKeyword = expect(TokenKind.FUNC, "expected 'func'");
        Token name = expect(TokenKind.IDENTIFIER, "expected a function name after 'func'");
        expect(TokenKind.LPAREN, "expected '(' after the function name");
        List<Ast.Param> params = new ArrayList<>();
        skipNewlines();
        while (!check(TokenKind.RPAREN) && !check(TokenKind.EOF)) {
            if (check(TokenKind.MUT)) {
                errorAt(INVALID_DECLARATION, peek().pos(),
                        "'mut' declares a mutable member; a parameter is assignable already");
                advance();
            }
            Token paramName = expect(TokenKind.IDENTIFIER, "expected a parameter name");
            expect(TokenKind.COLON, "expected ':' after the parameter name");
            Ast.TypeRef type = parseType();
            params.add(new Ast.Param(paramName.lexeme(), type, paramName.pos()));
            skipNewlines();
            if (!match(TokenKind.COMMA)) {
                break;
            }
            skipNewlines(); // a trailing comma simply ends the list
        }
        expect(TokenKind.RPAREN, "expected ')' to close the parameter list");
        Ast.TypeRef returnType = null;
        if (match(TokenKind.ARROW)) {
            returnType = parseType();
        }
        String nativeName = match(TokenKind.NATIVE)
                ? expect(TokenKind.STRING_LITERAL, "expected a Java method name").value() : null;
        List<Ast.Stmt> body;
        boolean hasBody;
        if (traitBody) {
            skipNewlines();
            hasBody = check(TokenKind.LBRACE);
            body = hasBody ? parseBlock() : List.of();
            if (!hasBody) consumeTerminators();
        } else if (nativeBody || isNative) {
            if (isNative && check(TokenKind.LBRACE)) {
                errorAt(INVALID_DECLARATION, peek().pos(),
                        "a native function is resolved through the compiler's binding registry"
                                + " and has no Solvik body");
                parseBlock();
            }
            hasBody = false;
            body = List.of();
            consumeTerminators();
        } else {
            hasBody = true;
            body = parseBlock();
        }
        return new Ast.FuncDecl(name.lexeme(), params, returnType, body, isPublic, isStatic,
                funcKeyword.pos(), nativeName, hasBody, isNative);
    }

    /**
     * A written type (§2.1, §5).
     *
     * <p>A named type may be followed by {@code ?} and by any number of {@code [N]} or {@code []}
     * array suffixes. Each suffix applies to the type immediately to its left, so
     * {@code String?[2]} is an array of nullable strings, {@code String[2]?} is a nullable array of
     * non-null strings, and {@code String[2][3]} nests a fixed-length array as the element type.
     */
    private Ast.TypeRef parseType() {
        Ast.TypeRef type = parseNamedType();
        while (true) {
            if (check(TokenKind.QUESTION)) {
                SourcePos position = advance().pos();
                if (type.nullable()) {
                    errorAt(TYPE_FORM, position,
                            "a type takes at most one '?' at each level, found '" + type + "?'");
                } else {
                    type = nullable(type);
                }
            } else if (check(TokenKind.LBRACKET)) {
                SourcePos position = advance().pos();
                Ast.Expr size = delimited(() -> {
                    skipNewlines();
                    Ast.Expr written = check(TokenKind.RBRACKET) ? null : parseExpression();
                    skipNewlines();
                    expect(TokenKind.RBRACKET, "expected ']' to close the array type");
                    return written;
                });
                type = new Ast.TypeRef.Array(type, size, false, position);
            } else {
                return type;
            }
        }
    }

    private static Ast.TypeRef nullable(Ast.TypeRef type) {
        return switch (type) {
            case Ast.TypeRef.Named named ->
                    new Ast.TypeRef.Named(named.name(), named.arguments(), true, named.pos());
            case Ast.TypeRef.Array array -> array.asNullable();
        };
    }

    /**
     * A type name with its written arguments, before any {@code ?} or array suffix.
     *
     * <p>{@code Null} is a keyword in expression position, so it is accepted here as the name of
     * the singleton type as well (§2.1).
     */
    private Ast.TypeRef parseNamedType() {
        Token name = check(TokenKind.NULL) ? advance()
                : expect(TokenKind.IDENTIFIER, "expected a type name");
        String written = name.lexeme();
        while (match(TokenKind.COLON_COLON)) {
            Token member = expect(TokenKind.IDENTIFIER, "expected a type name after '::'");
            written = written + "::" + member.lexeme();
        }
        List<Ast.TypeRef> arguments = new ArrayList<>();
        if (check(TokenKind.LT)) {
            delimited(() -> {
                advance();
                do { arguments.add(parseType()); } while (match(TokenKind.COMMA));
                expect(TokenKind.GT, "expected '>' to close the type arguments");
            });
        }
        if ("Array".equals(written) && !arguments.isEmpty()) {
            Ast.TypeRef element = arguments.getFirst();
            errorAt(ARRAY_SPELLING, name.pos(), "the 'Array<T>' array spelling was removed; write '"
                    + element + "[]' for a size-erased array or '" + element + "[N]' for a"
                    + " fixed-length one" + (arguments.size() > 1
                            ? " (Array takes one type argument)" : ""));
            return new Ast.TypeRef.Array(element, null, false, name.pos());
        }
        return new Ast.TypeRef.Named(written, List.copyOf(arguments), false, name.pos());
    }

    // -------------------------------------------------------------- statements

    private List<Ast.Stmt> parseBlock() {
        expect(TokenKind.LBRACE, "expected '{' to open a block");
        List<Ast.Stmt> body = new ArrayList<>();
        skipNewlines();
        while (!check(TokenKind.RBRACE) && !check(TokenKind.EOF)) {
            body.add(parseStatement());
            consumeTerminators();
        }
        expect(TokenKind.RBRACE, "expected '}' to close the block");
        return body;
    }

    private Ast.Stmt parseStatement() {
        if (check(TokenKind.PUB)) {
            errorAt(INVALID_STATEMENT, peek().pos(),
                    "'pub' declares a type member; it is only valid inside a type body");
            advance();
            return parseStatement();
        }
        if (check(TokenKind.STATIC)) {
            errorAt(INVALID_STATEMENT, peek().pos(),
                    "'static' makes a type member belong to its type; "
                            + "it is only valid inside a type body");
            advance();
            return parseStatement();
        }
        if (check(TokenKind.INCLUDE) || check(TokenKind.ALIAS)) {
            SourcePos pos = peek().pos();
            recoverFileLevelBlock();
            return new Ast.BlockStmt(List.of(), pos);
        }
        if (check(TokenKind.MUT) && (lookahead(1) == TokenKind.IDENTIFIER
                || lookahead(1) == TokenKind.VAR)) {
            advance(); // 'mut' makes the declared binding assignable
            return parseDeclaration(true);
        }
        if (startsDeclaration()) {
            return parseDeclaration(false);
        }
        if (check(TokenKind.VAR)) {
            reportRemovedVar("declare a binding with 'name := value' or 'name: Type = value'");
            advance();
            synchronize();
            SourcePos pos = peek().pos();
            return new Ast.VarStmt("$error", null, false, new Ast.NullLit(pos), false, pos);
        }
        if (check(TokenKind.IF)) {
            return parseIfStatement();
        }
        if (check(TokenKind.WHILE)) {
            return parseWhileStatement();
        }
        if (check(TokenKind.FOR)) {
            return parseForStatement();
        }
        if (check(TokenKind.SWITCH)) {
            return parseSwitchStatement();
        }
        if (check(TokenKind.TRY)) {
            return parseTryStatement();
        }
        if (check(TokenKind.BREAK)) {
            return new Ast.BreakStmt(advance().pos());
        }
        if (check(TokenKind.CONTINUE)) {
            return new Ast.ContinueStmt(advance().pos());
        }
        if (check(TokenKind.RETURN)) {
            return parseReturnStatement();
        }
        if (check(TokenKind.RAISE)) {
            return parseRaiseStatement();
        }
        if (check(TokenKind.SYNC)) {
            return parseSyncStatement();
        }
        if (check(TokenKind.LBRACE)) {
            SourcePos pos = peek().pos();
            return new Ast.BlockStmt(parseBlock(), pos);
        }
        return parseAssignmentOrCall();
    }

    /** The written name at the base of an array type, used to tell a literal from an index chain. */
    private static String baseTypeName(Ast.TypeRef type) {
        Ast.TypeRef current = type;
        while (current instanceof Ast.TypeRef.Array array) {
            current = array.element();
        }
        String written = ((Ast.TypeRef.Named) current).name();
        int separator = written.lastIndexOf("::");
        return separator < 0 ? written : written.substring(separator + 2);
    }

    private static boolean startsUppercase(String name) {
        return !name.isEmpty() && Character.isUpperCase(name.charAt(0));
    }

    /** Whether the cursor is at a declaration: {@code name :=}, {@code name? :=}, or {@code name: T =}. */
    private boolean startsDeclaration() {
        if (!check(TokenKind.IDENTIFIER)) {
            return false;
        }
        return lookahead(1) == TokenKind.DECLARE || lookahead(1) == TokenKind.COLON
                || lookahead(1) == TokenKind.QUESTION
                        && (lookahead(2) == TokenKind.DECLARE || lookahead(2) == TokenKind.COLON);
    }

    /**
     * A local or top-level binding declaration (§3.1).
     *
     * <p>The three forms are distinct: {@code name := value} infers the type,
     * {@code name: Type = value} checks it against a written type, and {@code name? := value} asks
     * for an inferred nullable type. {@code mut} makes the binding assignable afterwards.
     */
    private Ast.Stmt parseDeclaration(boolean isMutable) {
        SourcePos pos = peek().pos();
        Token name = expect(TokenKind.IDENTIFIER, "expected a binding name");
        boolean nullableInferred = match(TokenKind.QUESTION);
        Ast.TypeRef type = null;
        boolean missingInitializer = false;
        if (match(TokenKind.COLON)) {
            type = parseDeclaredType(name.lexeme(), nullableInferred);
            nullableInferred = false;
            if (!match(TokenKind.ASSIGN)) {
                errorAt(DECLARATION_FORM, peek().pos(),
                        "an explicitly typed declaration writes '= initializer'");
                missingInitializer = true;
            }
        } else if (!match(TokenKind.DECLARE)) {
            errorAt(DECLARATION_FORM, peek().pos(), nullableInferred
                    ? "a nullable inferred declaration writes '" + name.lexeme() + "? := value'"
                    : "a declaration writes ':=' before its initializer; "
                            + "'=' assigns to an existing binding");
            missingInitializer = true;
        }
        Ast.Expr initializer = missingInitializer && atTerminator()
                ? null : parseExpression();
        return new Ast.VarStmt(name.lexeme(), type, nullableInferred, initializer, isMutable, pos);
    }

    /** Whether the cursor is at a token that ends the current statement or declaration. */
    private boolean atTerminator() {
        return check(TokenKind.NEWLINE) || check(TokenKind.SEMICOLON)
                || check(TokenKind.RBRACE) || check(TokenKind.EOF);
    }

    /** Reports the removed {@code var} keyword with the spelling that replaces it (§13.1). */
    private void reportRemovedVar(String replacement) {
        errorAt(REMOVED_SYNTAX, peek().pos(), "'var' was removed; " + replacement);
    }

    /**
     * Parses {@code switch subject { case value { ... } case regex "pattern" { ... } default
     * { ... } }}.
     *
     * <p>Parentheses around the subject are optional: {@code switch (x) { }} groups {@code x} and
     * parses to the same tree. A case label must be a literal, optionally negated, so the
     * compiler can compare labels for duplicates and emit a table dispatch (§3, "switch").
     */
    private Ast.Stmt parseSwitchStatement() {
        Token switchKeyword = expect(TokenKind.SWITCH, "expected 'switch'");
        Ast.Expr subject = parseExpression();
        expect(TokenKind.LBRACE, "expected '{' to open the switch body");
        List<Ast.CaseClause> cases = new ArrayList<>();
        List<Ast.Stmt> defaultBody = null;
        skipNewlines();
        while (!check(TokenKind.RBRACE) && !check(TokenKind.EOF)) {
            if (match(TokenKind.DEFAULT)) {
                List<Ast.Stmt> body = parseBlock();
                if (defaultBody != null) {
                    errorAt(EXPECTED_TOKEN, peek().pos(),
                            "a switch may declare 'default' only once");
                } else {
                    defaultBody = body;
                }
            } else {
                Token caseKeyword = expect(TokenKind.CASE, "expected 'case' or 'default'");
                if (defaultBody != null) {
                    errorAt(EXPECTED_TOKEN, caseKeyword.pos(),
                            "'default' must be the last arm of a switch");
                }
                boolean regex = match(TokenKind.REGEX);
                Ast.Expr label = parseCaseLabel(regex);
                cases.add(new Ast.CaseClause(label, regex, parseBlock(), caseKeyword.pos()));
            }
            skipNewlines();
        }
        expect(TokenKind.RBRACE, "expected '}' to close the switch body");
        return new Ast.SwitchStmt(subject, cases, defaultBody, switchKeyword.pos());
    }

    /** Parses a case label: a literal, optionally negated, or a string literal after {@code regex}. */
    private Ast.Expr parseCaseLabel(boolean regex) {
        SourcePos pos = peek().pos();
        if (check(TokenKind.MINUS)) {
            Token minus = advance();
            return new Ast.UnaryExpr("-", parseLiteral("a case label after '-'"), minus.pos());
        }
        if (regex && !check(TokenKind.STRING_LITERAL)) {
            errorAt(EXPECTED_TOKEN, peek().pos(),
                    "a 'case regex' label must be a string literal containing a pattern");
            return new Ast.StringLit("", pos); // the block still parses, so the error stands alone
        }
        return parseLiteral("a case label");
    }

    private Ast.Expr parseLiteral(String what) {
        Ast.Expr literal = literalAtCursor();
        if (literal != null) {
            return literal;
        }
        errorAt(EXPECTED_TOKEN, peek().pos(),
                what + " must be a literal value, found " + peek());
        return new Ast.NullLit(peek().pos());
    }

    /**
     * Consumes and returns the literal at the cursor, or {@code null} when the token is not a
     * literal value.
     *
     * <p>{@code null} is deliberately not a literal here: a case label is never null.
     */
    private Ast.Expr literalAtCursor() {
        Token token = peek();
        return switch (token.kind()) {
            case INT_LITERAL -> {
                advance();
                yield new Ast.IntLit(token.lexeme(), token.suffix(), token.pos());
            }
            case DECIMAL_LITERAL -> {
                advance();
                yield new Ast.DecimalLit(token.lexeme(), token.suffix(), token.pos());
            }
            case STRING_LITERAL -> {
                advance();
                yield new Ast.StringLit(token.value(), token.pos());
            }
            case TRUE -> {
                advance();
                yield new Ast.BoolLit(true, token.pos());
            }
            case FALSE -> {
                advance();
                yield new Ast.BoolLit(false, token.pos());
            }
            default -> null;
        };
    }

    private Ast.Stmt parseIfStatement() {
        Token ifKeyword = expect(TokenKind.IF, "expected 'if'");
        List<Ast.Branch> branches = new ArrayList<>();
        Ast.Expr condition = parseExpression();
        branches.add(new Ast.Branch(condition, parseBlock(), condition.pos()));
        while (check(TokenKind.ELSE)) {
            advance(); // 'else'
            if (match(TokenKind.IF)) {
                Ast.Expr elseIfCondition = parseExpression();
                branches.add(new Ast.Branch(elseIfCondition, parseBlock(), elseIfCondition.pos()));
            } else {
                List<Ast.Stmt> elseBody = parseBlock();
                return new Ast.IfStmt(branches, elseBody, ifKeyword.pos());
            }
        }
        return new Ast.IfStmt(branches, null, ifKeyword.pos());
    }

    private Ast.Stmt parseWhileStatement() {
        Token whileKeyword = expect(TokenKind.WHILE, "expected 'while'");
        Ast.Expr condition = parseExpression();
        return new Ast.WhileStmt(condition, parseBlock(), whileKeyword.pos());
    }

    /**
     * Parses {@code try { } catch (e: Exception) { } finally { }} (§7.4).
     *
     * <p>At most one {@code catch} clause is accepted, because {@code Exception} is the only
     * catchable type. A second one is reported and dropped so the rest of the file still checks.
     * As with {@code else}, a clause keyword after the closing brace must be on the same line:
     * outside a delimiter a newline ends the statement.
     */
    private Ast.Stmt parseTryStatement() {
        Token tryKeyword = expect(TokenKind.TRY, "expected 'try'");
        List<Ast.Stmt> body = parseBlock();
        requireClauseOnSameLine();
        Ast.CatchClause catcher = null;
        if (check(TokenKind.CATCH)) {
            catcher = parseCatchClause();
            requireClauseOnSameLine();
            while (check(TokenKind.CATCH)) {
                errorAt(EXPECTED_TOKEN, peek().pos(), "a 'try' statement takes at most one 'catch'"
                        + " clause; 'Exception' is the only catchable type (§7.4)");
                parseCatchClause(); // parse it so the rest of the block still checks, then drop it
            }
        }
        List<Ast.Stmt> finallyBody = null;
        if (check(TokenKind.FINALLY)) {
            advance(); // 'finally'
            finallyBody = parseBlock();
        }
        if (catcher == null && finallyBody == null) {
            errorAt(EXPECTED_TOKEN, tryKeyword.pos(),
                    "a 'try' statement needs a 'catch' or a 'finally' clause");
        }
        return new Ast.TryStmt(body, catcher, finallyBody, tryKeyword.pos());
    }

    /** Parses {@code catch (name: Type) { }}: the clause form of §7.4. */
    private Ast.CatchClause parseCatchClause() {
        Token catchKeyword = expect(TokenKind.CATCH, "expected 'catch'");
        expect(TokenKind.LPAREN, "expected '(' after 'catch'");
        Token name = expect(TokenKind.IDENTIFIER, "expected a catch variable name");
        expect(TokenKind.COLON, "expected ':' after the catch variable name");
        Ast.TypeRef type = parseType();
        expect(TokenKind.RPAREN, "expected ')' after the caught type");
        return new Ast.CatchClause(name.lexeme(), type, parseBlock(), catchKeyword.pos());
    }

    /** Parses {@code raise expression} (§7.4). */
    private Ast.Stmt parseRaiseStatement() {
        Token raiseKeyword = expect(TokenKind.RAISE, "expected 'raise'");
        return new Ast.RaiseStmt(parseExpression(), raiseKeyword.pos());
    }

    /**
     * Parses {@code sync monitor { ... }} (§7.5).
     *
     * <p>As with {@code while}, the monitor is an unparenthesized expression and the block must
     * open on the same line: outside a delimiter a newline ends the statement (§1).
     */
    private Ast.Stmt parseSyncStatement() {
        Token syncKeyword = expect(TokenKind.SYNC, "expected 'sync'");
        Ast.Expr monitor = parseExpression();
        return new Ast.SyncStmt(monitor, parseBlock(), syncKeyword.pos());
    }

    /**
     * Reports a {@code catch} or {@code finally} that starts a new line (§1, §7.4).
     *
     * <p>Outside a delimiter a newline ends the statement, so a clause must share the line of the
     * {@code }} that closes the block before it, exactly as {@code else} does. The newline is
     * consumed anyway so the clause still parses and one diagnostic covers the mistake.
     */
    private void requireClauseOnSameLine() {
        if (!check(TokenKind.NEWLINE)) {
            return;
        }
        int offset = 1;
        while (lookahead(offset) == TokenKind.NEWLINE) {
            offset++;
        }
        TokenKind next = lookahead(offset);
        if (next != TokenKind.CATCH && next != TokenKind.FINALLY) {
            return;
        }
        skipNewlines();
        errorAt(UNEXPECTED_TOKEN, peek().pos(),
                "a 'catch' or 'finally' clause must follow '}' on the same line (§1)");
    }

    private Ast.Stmt parseForStatement() {
        Token forKeyword = expect(TokenKind.FOR, "expected 'for'");
        Token name = expect(TokenKind.IDENTIFIER, "expected a loop variable name");
        expect(TokenKind.IN, "expected 'in' after the loop variable");
        Ast.Expr iterable = parseExpression();
        return new Ast.ForStmt(name.lexeme(), iterable, parseBlock(), forKeyword.pos());
    }

    private Ast.Stmt parseReturnStatement() {
        Token returnKeyword = expect(TokenKind.RETURN, "expected 'return'");
        Ast.Expr value = null;
        if (!check(TokenKind.NEWLINE) && !check(TokenKind.SEMICOLON) && !check(TokenKind.RBRACE)
                && !check(TokenKind.EOF)) {
            value = parseExpression();
        }
        return new Ast.ReturnStmt(value, returnKeyword.pos());
    }

    private Ast.Stmt parseAssignmentOrCall() {
        SourcePos start = peek().pos();
        Ast.Expr expression = parseExpression();
        if (match(TokenKind.ASSIGN)) {
            return new Ast.AssignStmt(expression, "=", parseExpression(), start);
        }
        if (match(TokenKind.CONCAT_ASSIGN)) {
            return new Ast.AssignStmt(expression, "..=", parseExpression(), start);
        }
        if (expression instanceof Ast.CallExpr) {
            return new Ast.ExprStmt(expression, start);
        }
        errorAt(INVALID_STATEMENT, start, "only an assignment or a call may be used as a statement");
        return new Ast.ExprStmt(expression, start);
    }

    // ------------------------------------------------------------- expressions

    public Ast.Expr parseExpression() {
        return parseConcatenation();
    }

    // `..` is the loosest operator (LANGUAGE.md §4.3). Concatenation renders values that are
    // already fully evaluated, so arithmetic, comparisons, and conditions need no parentheses
    // when they are rendered. `("id:" .. n) == "id:5"` is the one grouping that still matters.
    private Ast.Expr parseConcatenation() {
        Ast.Expr left = parseDisjunction();
        while (check(TokenKind.CONCAT)) {
            Token op = advance();
            left = new Ast.BinaryExpr(left, op.lexeme(), parseDisjunction(), op.pos());
        }
        return left;
    }

    private Ast.Expr parseDisjunction() {
        Ast.Expr left = parseConjunction();
        while (check(TokenKind.OR_OR)) {
            Token op = advance();
            left = new Ast.BinaryExpr(left, op.lexeme(), parseConjunction(), op.pos());
        }
        return left;
    }

    private Ast.Expr parseConjunction() {
        Ast.Expr left = parseEquality();
        while (check(TokenKind.AND_AND)) {
            Token op = advance();
            left = new Ast.BinaryExpr(left, op.lexeme(), parseEquality(), op.pos());
        }
        return left;
    }

    private Ast.Expr parseEquality() {
        Ast.Expr left = parseComparison();
        while (check(TokenKind.EQ) || check(TokenKind.NOT_EQ) || check(TokenKind.EQ_IDENTITY)) {
            Token op = advance();
            left = new Ast.BinaryExpr(left, op.lexeme(), parseComparison(), op.pos());
        }
        return left;
    }

    private Ast.Expr parseComparison() {
        Ast.Expr left = parseAdditive();
        while (check(TokenKind.LT) || check(TokenKind.LE) || check(TokenKind.GT)
                || check(TokenKind.GE)) {
            Token op = advance();
            left = new Ast.BinaryExpr(left, op.lexeme(), parseAdditive(), op.pos());
        }
        return left;
    }

    private Ast.Expr parseAdditive() {
        Ast.Expr left = parseMultiplicative();
        while (check(TokenKind.PLUS) || check(TokenKind.MINUS)) {
            Token op = advance();
            left = new Ast.BinaryExpr(left, op.lexeme(), parseMultiplicative(), op.pos());
        }
        return left;
    }

    private Ast.Expr parseMultiplicative() {
        Ast.Expr left = parseUnary();
        while (check(TokenKind.STAR) || check(TokenKind.SLASH) || check(TokenKind.PERCENT)) {
            Token op = advance();
            left = new Ast.BinaryExpr(left, op.lexeme(), parseUnary(), op.pos());
        }
        return left;
    }

    private Ast.Expr parseUnary() {
        if (check(TokenKind.MINUS) || check(TokenKind.BANG)) {
            Token op = advance();
            return new Ast.UnaryExpr(op.lexeme(), parseUnary(), op.pos());
        }
        return parsePostfix();
    }

    private Ast.Expr parsePostfix() {
        Ast.Expr expression = parsePrimary();
        while (true) {
            if (check(TokenKind.LT) && (expression instanceof Ast.NameExpr
                    || expression instanceof Ast.QualifiedNameExpr)) {
                int saved = index;
                int savedDiagnostics = diagnostics.size();
                List<Ast.TypeRef> arguments = delimited(() -> {
                    advance();
                    List<Ast.TypeRef> written = new ArrayList<>();
                    do { written.add(parseType()); } while (match(TokenKind.COMMA));
                    return match(TokenKind.GT) ? written : null;
                });
                if (arguments != null && check(TokenKind.LPAREN)) {
                    expression = new Ast.TypeApplyExpr(expression, List.copyOf(arguments), expression.pos());
                    continue;
                }
                rollback(saved, savedDiagnostics);
            }
            if (check(TokenKind.LPAREN)) {
                SourcePos pos = peek().pos();
                advance();
                List<Ast.Expr> arguments = delimited(this::parseArguments);
                expression = new Ast.CallExpr(expression, arguments, pos);
            } else if (check(TokenKind.DOT)) {
                advance();
                // A member name may be a keyword: `.type()` is part of the Any contract (§8),
                // and `type` is reserved as a declaration keyword (§2).
                Token name = (check(TokenKind.IDENTIFIER) || peek().kind().isKeyword())
                        ? advance()
                        : expect(TokenKind.IDENTIFIER, "expected a member name after '.'");
                expression = new Ast.MemberExpr(expression, name.lexeme(), name.pos());
            } else if (check(TokenKind.LBRACKET)) {
                SourcePos pos = peek().pos();
                advance();
                Ast.Expr indexExpr = delimited(() -> {
                    skipNewlines();
                    Ast.Expr index = parseExpression();
                    skipNewlines();
                    expect(TokenKind.RBRACKET, "expected ']' after the index");
                    return index;
                });
                expression = new Ast.IndexExpr(expression, indexExpr, pos);
            } else {
                return expression;
            }
        }
    }

    private List<Ast.Expr> parseArguments() {
        List<Ast.Expr> arguments = new ArrayList<>();
        skipNewlines();
        while (!check(TokenKind.RPAREN) && !check(TokenKind.EOF)) {
            arguments.add(parseExpression());
            skipNewlines();
            if (!match(TokenKind.COMMA)) {
                break;
            }
            skipNewlines(); // a trailing comma simply ends the list
        }
        expect(TokenKind.RPAREN, "expected ')' to close the argument list");
        return arguments;
    }

    private Ast.Expr parsePrimary() {
        Token token = peek();
        switch (token.kind()) {
            case INT_LITERAL, DECIMAL_LITERAL, STRING_LITERAL, TRUE, FALSE -> {
                return literalAtCursor();
            }
            case NULL -> {
                advance();
                return new Ast.NullLit(token.pos());
            }
            case NULL_LOWER -> {
                advance();
                errorAt(REMOVED_SYNTAX, token.pos(),
                        "lowercase 'null' is not part of the language; write 'Null' for the"
                                + " singleton value (§4.1)");
                return new Ast.NullLit(token.pos());
            }
            case VAR -> {
                reportRemovedVar("declare a binding with 'name := value'");
                advance();
                return new Ast.NullLit(token.pos());
            }
            case OUTER -> {
                Token outer = advance();
                Token name = expect(TokenKind.IDENTIFIER,
                        "expected a variable name after 'outer'");
                return new Ast.OuterExpr(name.lexeme(), outer.pos());
            }
            case LPAREN -> {
                advance();
                Ast.Expr inner = delimited(() -> {
                    skipNewlines();
                    Ast.Expr expression = parseExpression();
                    skipNewlines();
                    expect(TokenKind.RPAREN, "expected ')' to close the group");
                    return expression;
                });
                return inner;
            }
            case IDENTIFIER -> {
                Ast.Expr arrayLiteral = tryParseArrayLiteral();
                if (arrayLiteral != null) {
                    return arrayLiteral;
                }
                advance();
                if (match(TokenKind.COLON_COLON)) {
                    StringBuilder path = new StringBuilder(token.lexeme());
                    Token member = expect(TokenKind.IDENTIFIER,
                            "expected a type name after '::'");
                    path.append("::").append(member.lexeme());
                    while (match(TokenKind.COLON_COLON)) {
                        member = expect(TokenKind.IDENTIFIER, "expected a name after '::'");
                        path.append("::").append(member.lexeme());
                    }
                    String full = path.toString();
                    int last = full.lastIndexOf("::");
                    return new Ast.QualifiedNameExpr(full.substring(0, last),
                            full.substring(last + 2), token.pos());
                }
                return new Ast.NameExpr(token.lexeme(), token.pos());
            }
            default -> {
                errorAt(UNEXPECTED_TOKEN, token.pos(), "expected an expression, found " + token);
                if (!check(TokenKind.EOF)) {
                    advance();
                }
                return new Ast.NameExpr("<error>", token.pos());
            }
        }
    }

    /**
     * Recognizes {@code Type[N] { elements }} by speculative parse (§5.1).
     *
     * <p>{@code a[0]} must stay an index expression, so the whole shape — an array type followed by
     * an opening brace — has to be present before the array literal interpretation is committed to.
     * Returns {@code null} and restores the cursor when the shape does not match.
     */
    private Ast.Expr tryParseArrayLiteral() {
        int saved = index;
        int savedDiagnostics = diagnostics.size();
        SourcePos start = peek().pos();
        Ast.TypeRef type = parseType();
        if (!(type instanceof Ast.TypeRef.Array array) || !check(TokenKind.LBRACE)
                || !startsUppercase(baseTypeName(array))) {
            // A type name begins with an uppercase letter (§2), so `items[0][1] { ... }` stays an
            // index chain and only `Type[N] { ... }` commits to a literal.
            rollback(saved, savedDiagnostics);
            return null;
        }
        advance(); // '{'
        List<Ast.Expr> elements = new ArrayList<>();
        skipNewlines();
        while (!check(TokenKind.RBRACE) && !check(TokenKind.EOF)) {
            elements.add(parseExpression());
            skipNewlines();
            if (!match(TokenKind.COMMA)) {
                break;
            }
            skipNewlines(); // a trailing comma simply ends the list
        }
        expect(TokenKind.RBRACE, "expected '}' to close the array literal");
        return new Ast.ArrayLit(array, elements, start);
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Reports a {@code static} token in a position where it cannot introduce a member.
     *
     * <p>The caller consumes the token: a module or type body resynchronizes, and a top-level
     * declaration advances one token so the next declaration still parses.
     */
    private void reportMisplacedStatic() {
        boolean onType = lookahead(1) == TokenKind.TYPE;
        errorAt(INVALID_DECLARATION, peek().pos(), onType
                ? "'static' identifies type members; it cannot be applied to a type declaration"
                : "'static' makes a type member belong to its type; "
                        + "it is only valid inside a type body");
    }

    private void rollback(int savedIndex, int savedDiagnostics) {
        index = savedIndex;
        while (diagnostics.size() > savedDiagnostics) {
            diagnostics.removeLast();
        }
    }

    private void consumeTerminators() {
        while (match(TokenKind.NEWLINE) || match(TokenKind.SEMICOLON)) {
            // consume
        }
    }

    private void skipNewlines() {
        while (match(TokenKind.NEWLINE)) {
            // skip
        }
    }

    private void synchronize() {
        while (!check(TokenKind.EOF) && !check(TokenKind.RBRACE)
                && !check(TokenKind.NEWLINE) && !check(TokenKind.SEMICOLON)) {
            advance();
        }
        consumeTerminators();
    }

    private Token peek() {
        skipDelimitedNewlines();
        return tokens.get(Math.min(index, tokens.size() - 1));
    }

    /**
     * Consumes newline tokens at the cursor while a delimited construct is open (§1).
     *
     * <p>Inside {@code ( ... )}, {@code [ ... ]}, or {@code < ... >} a newline is layout, so every
     * token access skips it. Outside a delimiter the cursor stays put, which keeps a newline a
     * statement terminator.
     */
    private void skipDelimitedNewlines() {
        if (delimiterDepth == 0) {
            return;
        }
        while (index < tokens.size() - 1
                && tokens.get(index).kind() == TokenKind.NEWLINE) {
            index++;
        }
    }

    /**
     * Runs {@code body} inside a delimited construct, where newlines are layout (§1).
     *
     * <p>The depth is restored on every exit, so an error inside the construct cannot leave the
     * parser believing it is still inside one.
     */
    private <T> T delimited(java.util.function.Supplier<T> body) {
        delimiterDepth++;
        try {
            return body.get();
        } finally {
            delimiterDepth--;
        }
    }

    /** Runs {@code body} inside a delimited construct, where newlines are layout (§1). */
    private void delimited(Runnable body) {
        delimiterDepth++;
        try {
            body.run();
        } finally {
            delimiterDepth--;
        }
    }

    /** Whether the cursor begins a {@code static { ... }} block, allowing layout newlines. */
    private boolean startsStaticInitializer() {
        if (!check(TokenKind.STATIC)) {
            return false;
        }
        int offset = 1;
        while (lookahead(offset) == TokenKind.NEWLINE) {
            offset++;
        }
        return lookahead(offset) == TokenKind.LBRACE;
    }

    /** Whether the current position begins a type-body method, including pub/static modifiers. */
    private boolean startsMethod() {
        int offset = 0;
        while (lookahead(offset) == TokenKind.PUB || lookahead(offset) == TokenKind.STATIC
                || lookahead(offset) == TokenKind.NATIVE) {
            offset++;
        }
        return lookahead(offset) == TokenKind.FUNC;
    }

    /** Whether the current position begins a type-body field, including its modifiers. */
    private boolean startsField() {
        int offset = 0;
        while (lookahead(offset) == TokenKind.PUB || lookahead(offset) == TokenKind.STATIC
                || lookahead(offset) == TokenKind.MUT) {
            offset++;
        }
        if (lookahead(offset) == TokenKind.VAR) {
            return true; // removed syntax: parseField names the replacement
        }
        if (lookahead(offset) != TokenKind.IDENTIFIER) {
            return false;
        }
        offset++;
        return lookahead(offset) == TokenKind.DECLARE || lookahead(offset) == TokenKind.COLON
                || lookahead(offset) == TokenKind.QUESTION
                        && lookahead(offset + 1) == TokenKind.DECLARE;
    }

    /** The kind {@code offset} tokens ahead of the cursor, clamped to the end of the input. */
    private TokenKind lookahead(int offset) {
        int position = index;
        int remaining = offset;
        while (remaining > 0 && position < tokens.size() - 1) {
            position++;
            if (delimiterDepth > 0 && tokens.get(position).kind() == TokenKind.NEWLINE) {
                continue; // layout inside a delimited construct (§1)
            }
            remaining--;
        }
        return tokens.get(Math.min(position, tokens.size() - 1)).kind();
    }

    private boolean check(TokenKind kind) {
        return peek().kind() == kind;
    }

    private Token advance() {
        Token token = peek();
        if (!check(TokenKind.EOF)) {
            index++;
        }
        return token;
    }

    private boolean match(TokenKind kind) {
        if (check(kind)) {
            advance();
            return true;
        }
        return false;
    }

    private Token expect(TokenKind kind, String message) {
        if (check(kind)) {
            return advance();
        }
        errorAt(EXPECTED_TOKEN, peek().pos(), message + ", found " + peek());
        return new Token(kind, "", null, peek().pos());
    }

    private void errorAt(String code, SourcePos pos, String message) {
        diagnostics.error(code, pos, message);
    }
}
