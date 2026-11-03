package dev.solvik.compiler.load;

import dev.solvik.compiler.ast.Ast;
import dev.solvik.compiler.diagnostic.Diagnostics;
import dev.solvik.compiler.diagnostic.SourcePos;
import dev.solvik.compiler.lexer.Lexer;
import dev.solvik.compiler.parser.Parser;
import dev.solvik.compiler.report.DiagnosticReporter;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Loads the packaged library and the program's files into one program ({@code LANGUAGE.md} §3,
 * "Source files" and §11, "Standard library").
 *
 * <p>Every {@code .sol} file the compiler packages under its own resources belongs to every
 * program. Nothing includes them, and their paths say nothing about the namespaces they declare:
 * a file's own {@code module} blocks decide that. They are loaded first, so a program is resolved
 * against them before its own files are read, and a new file under {@code src/main/resources} is
 * part of the language as soon as the compiler is built — there is no list to register it in.
 *
 * <p>An {@code include { "path.sol" }} entry names a <em>file</em>, resolved relative to the file
 * that writes it, taken literally, and followed transitively. A file contributes its declarations;
 * only the entry file contributes statements, so the execution order stays the source order of one
 * file. A file reached twice is loaded once, and a cycle is reported rather than silently tolerated:
 * it has no meaning here, and naming it is more useful than the partial program a cycle would
 * produce. An included file may include further files, and may declare {@code alias} blocks, which
 * stay local to the file that writes them.
 *
 * <p>Provenance is what separates the two kinds of source. The loader records the identity of every
 * module block parsed from a packaged resource, and only those may open the reserved {@code solvik}
 * root module: an included file that writes {@code module solvik { … }} is rejected by the checker
 * even when its names look bundled.
 */
public final class ProgramLoader {

    /** A missing included file (§3, "Source files"). */
    public static final String MISSING_FILE = "SOL-I001";
    /** An include path written as an absolute path. */
    public static final String ABSOLUTE_PATH = "SOL-I002";
    /** An included file, or a packaged resource, that holds something other than declarations. */
    public static final String STATEMENTS_IN_INCLUDED_FILE = "SOL-I003";
    /** An include that closes a cycle: a file reached again while it is still being loaded. */
    public static final String INCLUDE_CYCLE = "SOL-I004";

    /**
     * A loaded program together with diagnostics, cache inputs and packaged-module provenance.
     *
     * @param program   the merged program: every file's declarations, the entry file's statements
     * @param sources   each source's text keyed by the display name used in diagnostics
     * @param fileNames every loaded source in load order: packaged resources first, entry file last
     * @param packagedNames the loaded sources that are packaged library resources, not program files
     * @param standardLibraryModules root module identities parsed from packaged resources
     * @param packagedTypes type declarations parsed from packaged resources, compared by identity;
     *                      only these may declare {@code native} members
     * @param packagedTypeSources the packaged source text each packaged type was parsed from
     */
    public record Loaded(Ast.Program program, Map<String, String> sources, List<String> fileNames,
                         List<String> packagedNames,
                         Set<Ast.ModuleDecl> standardLibraryModules,
                         Set<Ast.TypeDecl> packagedTypes,
                         Map<Ast.TypeDecl, String> packagedTypeSources) {

        public Loaded {
            Set<Ast.ModuleDecl> identities = Collections.newSetFromMap(new IdentityHashMap<>());
            identities.addAll(standardLibraryModules);
            standardLibraryModules = Collections.unmodifiableSet(identities);
            Set<Ast.TypeDecl> typeIdentities = Collections.newSetFromMap(new IdentityHashMap<>());
            typeIdentities.addAll(packagedTypes);
            packagedTypes = Collections.unmodifiableSet(typeIdentities);
            Map<Ast.TypeDecl, String> sourceIdentities = new IdentityHashMap<>();
            sourceIdentities.putAll(packagedTypeSources);
            packagedTypeSources = Collections.unmodifiableMap(sourceIdentities);
        }

        /** The program's own files, in load order: the packaged library is not one of them. */
        public List<String> programFileNames() {
            return fileNames.stream().filter(name -> !packagedNames.contains(name)).toList();
        }

        /**
         * The packaged types a program actually reaches, by simple name.
         *
         * <p>A packaged type is reached when the program's own source mentions its name, or when
         * the source of an already-reached packaged type mentions it. The generated files of an
         * unreached packaged type are omitted, so a program that does no I/O neither emits the I/O
         * types nor the generated binding adapter.
         */
        public Set<String> reachedPackagedTypeNames() {
            List<String> texts = sources.entrySet().stream()
                    .filter(entry -> !packagedNames.contains(entry.getKey()))
                    .map(Map.Entry::getValue).collect(Collectors.toCollection(ArrayList::new));
            Set<Ast.TypeDecl> reached = Collections.newSetFromMap(new IdentityHashMap<>());
            boolean changed = true;
            while (changed) {
                changed = false;
                for (Map.Entry<Ast.TypeDecl, String> entry : packagedTypeSources.entrySet()) {
                    if (reached.contains(entry.getKey())) {
                        continue;
                    }
                    String name = entry.getKey().name();
                    if (texts.stream().anyMatch(text -> mentions(text, name))) {
                        reached.add(entry.getKey());
                        texts.add(entry.getValue());
                        changed = true;
                    }
                }
            }
            Set<String> names = new LinkedHashSet<>();
            for (Ast.TypeDecl type : reached) {
                names.add(type.name());
            }
            return Collections.unmodifiableSet(names);
        }

        /**
         * Whether {@code name} appears as a whole word outside comments and literals.
         *
         * <p>Reachability is an optimization, so a false positive only emits an extra packaged
         * type; skipping comments and literals keeps a documentation sentence from pulling in a
         * type, while still finding every real reference.
         */
        private static boolean mentions(String text, String name) {
            int index = 0;
            while (index < text.length()) {
                char current = text.charAt(index);
                if (current == '/' && index + 1 < text.length()
                        && text.charAt(index + 1) == '/') {
                    int newline = text.indexOf('\n', index + 2);
                    if (newline < 0) {
                        return false;
                    }
                    index = newline + 1;
                    continue;
                }
                if (current == '/' && index + 1 < text.length()
                        && text.charAt(index + 1) == '*') {
                    int close = text.indexOf("*/", index + 2);
                    if (close < 0) {
                        return false;
                    }
                    index = close + 2;
                    continue;
                }
                if (current == '"' || current == '\'') {
                    index = skipLiteral(text, index + 1, current);
                    continue;
                }
                if (text.startsWith(name, index)
                        && (index == 0 || !Character.isLetterOrDigit(text.charAt(index - 1))
                                && text.charAt(index - 1) != '_')
                        && (index + name.length() == text.length()
                                || !Character.isLetterOrDigit(text.charAt(index + name.length()))
                                        && text.charAt(index + name.length()) != '_')) {
                    return true;
                }
                index++;
            }
            return false;
        }

        /** The index after a string or character literal, or the end of the text. */
        private static int skipLiteral(String text, int index, char quote) {
            while (index < text.length()) {
                char current = text.charAt(index);
                if (current == '\\' && index + 1 < text.length()) {
                    index += 2;
                    continue;
                }
                if (current == quote) {
                    return index + 1;
                }
                index++;
            }
            return text.length();
        }

        /** A stable description of the whole loaded closure, used to key the compilation cache. */
        public String fingerprint() {
            StringBuilder text = new StringBuilder();
            for (String name : fileNames) {
                text.append(name).append('\u0000').append(sources.get(name)).append('\u0000');
            }
            return text.toString();
        }
    }

    private final Diagnostics diagnostics;
    private final Map<Path, String> loaded = new LinkedHashMap<>();
    private final Map<Path, Ast.Program> programs = new LinkedHashMap<>();
    private final List<String> packagedNames = new ArrayList<>();
    private final Set<Path> loading = new LinkedHashSet<>();
    private final Set<Ast.ModuleDecl> standardLibraryModules =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Ast.TypeDecl> packagedTypes =
            Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<Ast.TypeDecl, String> packagedTypeSources = new IdentityHashMap<>();
    /** The files being loaded right now, entry first, so a cycle can be named. */
    private final Deque<Path> stack = new ArrayDeque<>();

    public ProgramLoader(Diagnostics diagnostics) {
        this.diagnostics = diagnostics;
    }

    /**
     * Loads the packaged library, then {@code entry} and the files it includes.
     *
     * <p>Loading never throws for a program's own mistakes: a missing file, an absolute path, or
     * statements in an included file are recorded as diagnostics, and the caller decides what to do
     * with them. The returned program holds whatever could be loaded, so its sources can still be
     * rendered against the failures.
     */
    public Loaded load(Path entry) {
        Path root = normalize(entry);
        loadPackagedLibrary();
        loadFile(root, true);
        return new Loaded(merge(root), sourcesByName(), List.copyOf(displayNames().keySet()),
                List.copyOf(packagedNames), standardLibraryModules, packagedTypes,
                packagedTypeSources);
    }

    /** Loads only the packaged library, for a caller that checks an AST it parsed itself. */
    public Loaded loadStandardLibrary() {
        loadPackagedLibrary();
        return new Loaded(packagedProgram(), sourcesByName(),
                List.copyOf(displayNames().keySet()), List.copyOf(packagedNames),
                standardLibraryModules, packagedTypes, packagedTypeSources);
    }

    /** In-memory compilation sees the packaged library, but has no filesystem include base. */
    public Loaded loadSource(String source, String fileName) {
        Path entry = Path.of(fileName).toAbsolutePath().normalize();
        String name = DiagnosticReporter.displayName(entry);
        loadPackagedLibrary();
        loaded.put(entry, source);
        Ast.Program program = new Parser(new Lexer(source, diagnostics, name).tokenize(), diagnostics)
                .parseProgram();
        programs.put(entry, program);
        for (Ast.IncludeDecl declaration : program.includes()) {
            diagnostics.error(MISSING_FILE, declaration.pos(),
                    "an include names a file, resolved from the including file's directory; run "
                            + "the compiler on a file path to use includes (found '"
                            + declaration.path() + "')");
        }
        return new Loaded(merge(entry), sourcesByName(), List.copyOf(displayNames().keySet()),
                List.copyOf(packagedNames), standardLibraryModules, packagedTypes,
                packagedTypeSources);
    }

    // ------------------------------------------------------- packaged library

    /**
     * Reads every packaged {@code .sol} resource, in path order, before any program file.
     *
     * <p>A packaged resource is declaration-only: it declares types, functions, and modules, and
     * includes nothing, because it is not a file a program can reach. Its module blocks are recorded
     * as packaged, which is what permits the reserved {@code solvik} root namespace (§11).
     */
    private void loadPackagedLibrary() {
        for (String resource : packagedResources()) {
            Path key = Path.of(resource);
            if (loaded.containsKey(key)) {
                continue;
            }
            String source;
            try (var stream = ProgramLoader.class.getResourceAsStream("/" + resource)) {
                if (stream == null) {
                    throw new IllegalStateException("the compiler lists a resource it cannot read: "
                            + resource);
                }
                source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException failure) {
                throw new IllegalStateException("cannot read packaged resource '" + resource + "'",
                        failure);
            }
            loaded.put(key, source);
            packagedNames.add(resource);
            Ast.Program program = new Parser(new Lexer(source, diagnostics, resource).tokenize(),
                    diagnostics).parseProgram();
            programs.put(key, program);
            if (!program.includes().isEmpty()) {
                diagnostics.error(STATEMENTS_IN_INCLUDED_FILE,
                        program.includes().getFirst().pos(),
                        "packaged library '" + resource + "' includes a file; a packaged resource"
                                + " declares its own modules and includes nothing");
            }
            if (!program.statements().isEmpty()) {
                diagnostics.error(STATEMENTS_IN_INCLUDED_FILE,
                        program.statements().getFirst().pos(),
                        "packaged library '" + resource + "' may declare types and functions but hold"
                                + " no top-level statements");
            }
            standardLibraryModules.addAll(program.modules());
            packagedTypes.addAll(program.types());
            for (Ast.TypeDecl type : program.types()) {
                packagedTypeSources.put(type, source);
            }
            for (Ast.ModuleDecl module : program.modules()) {
                collectTypes(module, packagedTypes, packagedTypeSources, source);
            }
        }
    }

    /**
     * Every {@code .sol} file packaged with the compiler, as classpath resource paths in sorted
     * order.
     *
     * <p>The scan covers the compiler's own code location: a directory when the compiler runs from
     * {@code target/classes}, a JAR when it runs packaged — reached either as a {@code jar:} URL or,
     * under {@code java -jar}, as a plain file path to the JAR. Sorting keeps the load order, the
     * merged program, and therefore the compilation cache deterministic.
     */
    private static List<String> packagedResources() {
        List<String> found = new ArrayList<>();
        URL location = ProgramLoader.class.getProtectionDomain().getCodeSource().getLocation();
        if (location == null) {
            return List.of();
        }
        try {
            URLConnection connection = location.openConnection();
            if (connection instanceof JarURLConnection jarConnection) {
                try (JarFile jar = jarConnection.getJarFile()) {
                    collectPackaged(jar, found);
                }
                return sorted(found);
            }
            Path where = Path.of(location.toURI());
            if (where.toString().endsWith(".jar")) {
                try (JarFile jar = new JarFile(where.toFile())) {
                    collectPackaged(jar, found);
                }
                return sorted(found);
            }
            try (Stream<Path> files = Files.walk(where)) {
                files.filter(Files::isRegularFile)
                        .filter(file -> file.getFileName().toString().endsWith(".sol"))
                        .forEach(file -> found.add(where.relativize(file).toString()
                                .replace('\\', '/')));
            }
        } catch (IOException | URISyntaxException failure) {
            throw new IllegalStateException("cannot list the compiler's packaged resources", failure);
        }
        return sorted(found);
    }

    private static void collectPackaged(JarFile jar, List<String> found) {
        jar.stream()
                .filter(entry -> !entry.isDirectory() && entry.getName().endsWith(".sol"))
                .map(JarEntry::getName)
                .forEach(found::add);
    }

    /** Every type a packaged module block declares, nested blocks included, by identity. */
    private static void collectTypes(Ast.ModuleDecl module, Set<Ast.TypeDecl> types,
            Map<Ast.TypeDecl, String> sources, String source) {
        types.addAll(module.types());
        for (Ast.TypeDecl type : module.types()) {
            sources.put(type, source);
        }
        for (Ast.ModuleDecl nested : module.modules()) {
            collectTypes(nested, types, sources, source);
        }
    }

    private static List<String> sorted(List<String> found) {
        Collections.sort(found);
        return List.copyOf(found);
    }

    // ------------------------------------------------------------------ loading

    private void loadFile(Path file, boolean entry) {
        if (loaded.containsKey(file) || !loading.add(file)) {
            return;   // already loaded: a diamond include loads the file once
        }
        stack.addLast(file);
        try {
            String name = DiagnosticReporter.displayName(file);
            String text;
            try {
                text = Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException unreadable) {
                diagnostics.error(MISSING_FILE, SourcePos.START, "cannot read '" + name + "': "
                        + unreadable.getMessage());
                return;
            }
            loaded.put(file, text);
            Ast.Program program = new Parser(new Lexer(text, diagnostics, name).tokenize(), diagnostics)
                    .parseProgram();
            programs.put(file, program);
            if (!entry && !program.statements().isEmpty()) {
                diagnostics.error(STATEMENTS_IN_INCLUDED_FILE,
                        program.statements().getFirst().pos(),
                        "'" + name + "' is included, so it may declare types and functions but hold"
                                + " no top-level statements");
            }
            for (Ast.IncludeDecl declaration : program.includes()) {
                loadInclude(file, declaration);
            }
        } finally {
            // The load stack describes the files being loaded right now, including after a failure.
            stack.removeLast();
            loading.remove(file);
        }
    }

    private void loadInclude(Path includer, Ast.IncludeDecl declaration) {
        Path written;
        try {
            written = Path.of(declaration.path());
        } catch (InvalidPathException unusable) {
            diagnostics.error(MISSING_FILE, declaration.pos(),
                    "'" + declaration.path() + "' is not a file path");
            return;
        }
        if (written.isAbsolute()) {
            diagnostics.error(ABSOLUTE_PATH, declaration.pos(),
                    "an include path is relative to the including file, found '"
                            + declaration.path() + "'");
            return;
        }
        Path resolved = includer.getParent() == null
                ? written.toAbsolutePath().normalize()
                : includer.getParent().resolve(written).toAbsolutePath().normalize();
        if (!Files.isRegularFile(resolved)) {
            diagnostics.error(MISSING_FILE, declaration.pos(), "no such file: '"
                    + declaration.path() + "' resolved to '" + DiagnosticReporter.displayName(resolved)
                    + "'");
            return;
        }
        Path target = normalize(resolved);
        if (stack.contains(target)) {
            diagnostics.error(INCLUDE_CYCLE, declaration.pos(),
                    "include cycle: " + cyclePath(target));
            return;
        }
        loadFile(target, false);
    }

    /** The files on the load stack from {@code target} onward, closed by {@code target} again. */
    private String cyclePath(Path target) {
        StringBuilder path = new StringBuilder();
        boolean reached = false;
        for (Path file : stack) {
            if (!reached && !file.equals(target)) {
                continue;
            }
            reached = true;
            path.append(DiagnosticReporter.displayName(file)).append(" -> ");
        }
        return path.append(DiagnosticReporter.displayName(target)).toString();
    }

    private static Path normalize(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException notResolvable) {
            return path.toAbsolutePath().normalize();
        }
    }

    // ------------------------------------------------------------------ merging

    /** Only the packaged declarations, with no program statements. */
    private Ast.Program packagedProgram() {
        List<Ast.TypeDecl> types = new ArrayList<>();
        List<Ast.FuncDecl> functions = new ArrayList<>();
        List<Ast.ModuleDecl> modules = new ArrayList<>();
        List<Ast.AliasDecl> aliases = new ArrayList<>();
        SourcePos start = SourcePos.START;
        for (Ast.Program program : programs.values()) {
            types.addAll(program.types());
            functions.addAll(program.functions());
            modules.addAll(program.modules());
            aliases.addAll(program.aliases());
            start = program.pos();
        }
        return new Ast.Program(types, functions, List.of(), modules, List.of(), aliases, start);
    }

    /**
     * The merged program: every file's declarations, include resolution already done.
     *
     * <p>Alias declarations keep the source position that names the file writing them, which is
     * what lets the checker keep one file's aliases out of another file. Includes are dropped
     * because the loader has already followed them; only the entry file's statements remain.
     */
    private Ast.Program merge(Path entry) {
        List<Ast.TypeDecl> types = new ArrayList<>();
        List<Ast.FuncDecl> functions = new ArrayList<>();
        List<Ast.ModuleDecl> modules = new ArrayList<>();
        List<Ast.AliasDecl> aliases = new ArrayList<>();
        List<Ast.Stmt> statements = new ArrayList<>();
        SourcePos start = programs.get(entry) == null ? SourcePos.START : programs.get(entry).pos();
        for (Map.Entry<Path, Ast.Program> file : programs.entrySet()) {
            Ast.Program program = file.getValue();
            types.addAll(program.types());
            functions.addAll(program.functions());
            modules.addAll(program.modules());
            aliases.addAll(program.aliases());
            if (file.getKey().equals(entry)) {
                statements.addAll(program.statements());
            }
        }
        return new Ast.Program(types, functions, statements, modules,
                List.of(), aliases, start);
    }

    /** Packaged resources keep their classpath path; files keep the name a diagnostic shows. */
    private static String sourceName(Path file) {
        return file.isAbsolute() ? DiagnosticReporter.displayName(file)
                : file.toString().replace('\\', '/');
    }

    private Map<String, String> sourcesByName() {
        Map<String, String> sources = new LinkedHashMap<>();
        for (Map.Entry<Path, String> file : loaded.entrySet()) {
            sources.put(sourceName(file.getKey()), file.getValue());
        }
        return sources;
    }

    private Map<String, Path> displayNames() {
        Map<String, Path> names = new LinkedHashMap<>();
        for (Path file : loaded.keySet()) {
            names.put(sourceName(file), file);
        }
        return names;
    }
}
