package org.codedefenders.util.analysis;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.PrettyPrinterConfiguration;

import org.apache.commons.lang3.Range;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Code analysing class which uses {@link ResultVisitor} to iterate through java class code
 * using the {@link #visitCode(String, String) visitCode} method.
 * Stores results in a {@link CodeAnalysisResult} instance and returns it.
 *
 * @author <a href="https://github.com/werli">Phil Werli<a/>
 * @see ResultVisitor
 * @see CodeAnalysisResult
 */
public class ClassCodeAnalyser {
    private static final Logger logger = LoggerFactory.getLogger(ClassCodeAnalyser.class);
    private static final VoidVisitorAdapter<CodeAnalysisResult> resultVisitor = new ResultVisitor();

    /**
     * Iterates through a java class code to extract following information in a {@link CodeAnalysisResult}:
     * <ul>
     * <li>Strings of imports</li>
     * <li>Lines of compile time constants</li>
     * <li>Lines of non coverable code</li>
     * <li>Lines of not initialized fields</li>
     * <li>{@link Range Ranges} of methods signatures</li>
     * <li>{@link Range Ranges} of methods</li>
     * <li>{@link Range Ranges} if-condition statements and their bracket pair</li>
     * </ul>
     *
     * @param className The name of the visited class.
     * @param sourceCode the source code of the visited class.
     * @return a result, may be empty, but never {@code null}.
     */
    public static CodeAnalysisResult visitCode(String className, String sourceCode) {
        final CodeAnalysisResult result = new CodeAnalysisResult();
        try {
            final CompilationUnit cu = JavaParser.parse(sourceCode);
            resultVisitor.visit(cu, result);
        } catch (ParseProblemException e) {
            logger.warn("Failed to parse {}. Aborting code visit.", className);
        }
        return result;
    }

    /**
     * Custom implementation of {@link VoidVisitorAdapter} which collects lines of non coverable code, not
     * initialized fields and ranges of methods and its signatures as well as matching brackets.
     */
    private static class ResultVisitor extends VoidVisitorAdapter<CodeAnalysisResult> {
        private final PrettyPrinterConfiguration printer = new PrettyPrinterConfiguration().setPrintComments(false);

        @Override
        public void visit(IfStmt ifStmt, CodeAnalysisResult result) {
            super.visit(ifStmt, result);
            extractResultsFromIfStmt(ifStmt, result);
        }

        @Override
        public void visit(BlockStmt n, CodeAnalysisResult result) {
            super.visit(n, result);

            NodeList<Statement> statements = n.getStatements();
            if (!statements.isEmpty()) {
                Statement lastInnerStatement = statements.get(statements.size() - 1);
                int end = n.getEnd().get().line;
                if (lastInnerStatement.getEnd().get().line < end) {
                    result.nonCoverableCode(end);
                }
            }
        }

        @Override
        public void visit(FieldDeclaration n, CodeAnalysisResult arg) {
            super.visit(n, arg);
            extractResultsFromFieldDeclaration(n, arg);
        }

        @Override
        public void visit(MethodDeclaration n, CodeAnalysisResult arg) {
            super.visit(n, arg);
            extractResultsFromMethodDeclaration(n, arg);
        }

        @Override
        public void visit(ConstructorDeclaration n, CodeAnalysisResult arg) {
            super.visit(n, arg);
            extractResultsFromConstructorDeclaration(n, arg);
        }

        @Override
        public void visit(ImportDeclaration n, CodeAnalysisResult arg) {
            super.visit(n, arg);
            final String imported = n.toString(printer);
            arg.imported(imported);
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration n, CodeAnalysisResult result) {
            super.visit(n, result);
            result.nonCoverableCode(n.getEnd().get().line);
        }

        private void extractResultsFromIfStmt(IfStmt ifStmt, CodeAnalysisResult result) {
            Statement then = ifStmt.getThenStmt();
            Statement otherwise = ifStmt.getElseStmt().orElse(null);
            if (then instanceof BlockStmt) {

                List<Statement> thenBlockStmts = ((BlockStmt) then).getStatements();
                if (otherwise == null) {
                    /*
                     * This takes only the non-coverable one, meaning
                     * that if } is on the same line of the last stmt it
                     * is not considered here because it is should be already
                     * considered
                     */
                    if (!thenBlockStmts.isEmpty()) {
                        Statement lastInnerStatement = thenBlockStmts.get(thenBlockStmts.size() - 1);
                        if (lastInnerStatement.getEnd().get().line < ifStmt.getEnd().get().line) {
                            result.closingBracket(Range.between(then.getBegin().get().line, ifStmt.getEnd().get().line));
                            result.nonCoverableCode(ifStmt.getEnd().get().line);
                        }
                    }
                } else {
                    result.closingBracket(Range.between(then.getBegin().get().line, then.getEnd().get().line));
                    result.nonCoverableCode(otherwise.getBegin().get().line);
                }
            }
        }

        private static void extractResultsFromFieldDeclaration(FieldDeclaration f, CodeAnalysisResult result) {
            final boolean compileTimeConstant = f.isFinal() && ((f.getCommonType() instanceof PrimitiveType) || (String.class.getSimpleName().equals(f.getElementType().asString())));
            for (VariableDeclarator v : f.getVariables()) {
                for (int line = v.getBegin().get().line; line <= v.getEnd().get().line; line++) {
                    if (compileTimeConstant) {
                        logger.debug("Found compile-time constant " + v);
                        // compile time targets are non coverable, too
                        result.compileTimeConstant(line);
                        result.nonCoverableCode(line);
                    }
                    if (!v.getInitializer().isPresent()) {
                        // non initialized fields are non coverable
                        result.nonInitializedField(line);
                        result.nonCoverableCode(line);
                    }
                }
            }
        }

        private static void extractResultsFromMethodDeclaration(MethodDeclaration md, CodeAnalysisResult result) {
            // Note that md.getEnd().get().line returns the last line of the method, not of the signature
            if (!md.getBody().isPresent()) {
                return;
            }
            BlockStmt body = md.getBody().get();

            // Since a signature might span over different lines we need to get to its body and take its beginning
            // Also note that interfaces have no body ! So this might fail !
            int methodBegin = md.getBegin().get().line;
            int methodBodyBegin = body.getBegin().get().line;
            int methodEnd = md.getEnd().get().line;
            for (int line = methodBegin; line <= methodBodyBegin; line++) {
                // method signatures are non coverable
                result.nonCoverableCode(line);
            }
            result.methodSignatures(Range.between(methodBegin, methodBodyBegin));
            result.methods(Range.between(methodBegin, methodEnd));
        }

        private static void extractResultsFromConstructorDeclaration(ConstructorDeclaration cd, CodeAnalysisResult result) {
            // Constructors always have a body.
            int constructorBegin = cd.getBegin().get().line;
            int constructorBodyBegin = cd.getBody().getBegin().get().line;
            int constructorEnd = cd.getEnd().get().line;

            for (int line = constructorBegin; line <= constructorBodyBegin; line++) {
                // constructor signatures are non coverable
                result.nonCoverableCode(line);
            }

            result.methodSignatures(Range.between(constructorBegin, constructorBodyBegin));
            result.methods(Range.between(constructorBegin, constructorEnd));
        }
    }
}
