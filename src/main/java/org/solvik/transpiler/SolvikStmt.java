package org.solvik.transpiler;

import java.util.List;

/**
 * Backend-neutral typed Solvik statement IR produced from the analyzed AST.
 *
 * <p>Statements keep structured control flow (blocks, if, loops) rather than
 * lowering to jumps. Expressions are {@link SolvikIr} nodes. The Java backend
 * renders Java syntax from these nodes.</p>
 */
public sealed interface SolvikStmt {
    record VarDecl(String name, TypeModel.Type type, SolvikIr initializer) implements SolvikStmt {}

    record Expr(SolvikIr expression) implements SolvikStmt {}

    record Return(SolvikIr value) implements SolvikStmt {}

    record If(SolvikIr condition, List<SolvikStmt> thenBranch, List<SolvikStmt> elseBranch) implements SolvikStmt {}

    record While(SolvikIr condition, List<SolvikStmt> body) implements SolvikStmt {}

    record ForRange(String name, TypeModel.Type loopType, SolvikIr start, SolvikIr end, boolean inclusive, List<SolvikStmt> body) implements SolvikStmt {}

    record ForEach(String name, TypeModel.Type elementType, SolvikIr iterable, List<SolvikStmt> body) implements SolvikStmt {}

    record Break() implements SolvikStmt {}

    record Continue() implements SolvikStmt {}

    record Throw(SolvikIr value) implements SolvikStmt {}

    record Switch(SolvikIr subject, TypeModel.Type subjectType, boolean directEquality, List<SwitchCase> cases) implements SolvikStmt {}
    record SwitchCase(boolean isDefault, List<SolvikIr> values, List<SolvikStmt> body) {}

    record Try(List<SolvikStmt> body, List<Catch> catches, List<SolvikStmt> finallyBlock) implements SolvikStmt {}
    record Catch(TypeModel.Type type, String name, List<SolvikStmt> body) {}

    record Block(List<SolvikStmt> statements) implements SolvikStmt {}

    record MatchStmt(SolvikIr subject, java.util.List<SolvikIr.MatchArm> arms) implements SolvikStmt {}
}
