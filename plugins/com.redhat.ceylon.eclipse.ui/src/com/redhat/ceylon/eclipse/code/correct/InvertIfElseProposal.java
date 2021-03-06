package com.redhat.ceylon.eclipse.code.correct;

import static com.redhat.ceylon.eclipse.util.Indents.getDefaultIndent;
import static com.redhat.ceylon.eclipse.util.Indents.getDefaultLineDelimiter;
import static com.redhat.ceylon.eclipse.util.Indents.getIndent;

import java.util.Collection;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.text.edits.ReplaceEdit;

import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Block;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.BooleanCondition;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.ComparisonOp;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Condition;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.EqualOp;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.EqualityOp;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.IfClause;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.IfStatement;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.LargeAsOp;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.LargerOp;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.NotOp;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.SmallAsOp;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.SmallerOp;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Statement;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Term;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;

class InvertIfElseProposal extends CorrectionProposal {
    
    InvertIfElseProposal(int offset, TextChange change) {
        super("Invert 'if' 'else' statement", change, 
                new Region(offset, 0));
    }
    
    static void addReverseIfElseProposal(IDocument doc,
                Collection<ICompletionProposal> proposals, 
                IFile file, final Statement statement, 
                Tree.CompilationUnit cu) {
        try {
            IfStatement ifStmt;
            if (statement instanceof IfStatement) {
                ifStmt = (IfStatement) statement;
            }
            else {
                class FindIf extends Visitor {
                    IfStatement result;
                    @Override
                    public void visit(IfStatement that) {
                        super.visit(that);
                        if (that.getIfClause()!=null &&
                                that.getIfClause()
                                    .getBlock()
                                    .getStatements()
                                    .contains(statement)) {
                            result = that;
                        }
                        if (that.getElseClause()!=null &&
                                that.getElseClause()
                                    .getBlock()
                                    .getStatements()
                                    .contains(statement)) {
                            result = that;
                        }
                    }
                }
                FindIf fi = new FindIf();
                fi.visit(cu);
                if (fi.result==null) {
                    return;
                }
                else {
                    ifStmt = fi.result;
                }
            }
            
            if (ifStmt.getElseClause() == null) {
                return;
            }
            IfClause ifClause = ifStmt.getIfClause();
            Block ifBlock = ifClause.getBlock();
            Block elseBlock = 
                    ifStmt.getElseClause()
                        .getBlock();
            List<Condition> conditions = 
                    ifClause.getConditionList()
                        .getConditions();
            if (conditions.size()!=1) {
                return;
            }
            Condition ifCondition = conditions.get(0);
            
            String test = null;
            String term = getTerm(doc, ifCondition);
            if (term.equals("(true)")) {
                test = "false";
            } else if (term.equals("(false)")) {
                test = "true";
            } else if (ifCondition instanceof BooleanCondition) {
                BooleanCondition boolCond = 
                        (BooleanCondition) ifCondition;
                Term bt = boolCond.getExpression().getTerm();
                if (bt instanceof NotOp) {
                    String t = getTerm(doc, 
                            ((NotOp) bt).getTerm());
                    test = removeEnclosingParenthesis(t);
                } else if (bt instanceof EqualityOp) {
                    test = getInvertedEqualityTest(doc, 
                            (EqualityOp)bt);
                } else if (bt instanceof ComparisonOp) {
                    test = getInvertedComparisonTest(doc, 
                            (ComparisonOp)bt);
                } else if (! (bt instanceof Tree.OperatorExpression) 
                        || bt instanceof Tree.UnaryOperatorExpression) {
                    term = removeEnclosingParenthesis(term);
                }
            } else {
                   term = removeEnclosingParenthesis(term);
            }
            if (test == null) {
                if (term.startsWith("!")) {
                    test = term.substring(1);
                }
                else {
                    test = "!" + term;
                }
            }
            String baseIndent = getIndent(ifStmt, doc);
            String indent = getDefaultIndent();
            String delim = getDefaultLineDelimiter(doc);

            String elseStr = getTerm(doc, elseBlock);
            elseStr = addEnclosingBraces(elseStr, 
                    baseIndent, indent, delim);
            test = removeEnclosingParenthesis(test);

            StringBuilder replace = new StringBuilder();
            replace.append("if (").append(test).append(") ")
                    .append(elseStr);
            if (isElseOnOwnLine(doc, ifBlock, elseBlock)) {
                replace.append(delim)
                    .append(baseIndent);
            } else {
                replace.append(" ");
            }
            replace.append("else ")
                .append(getTerm(doc, ifBlock));

            TextChange change = 
                    new TextFileChange("Invert If Else", 
                            file);
            change.setEdit(new ReplaceEdit(ifStmt.getStartIndex(), 
                    ifStmt.getStopIndex() - ifStmt.getStartIndex() + 1, 
                    replace.toString()));
            proposals.add(new InvertIfElseProposal(ifStmt.getStartIndex(), change));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getInvertedEqualityTest(IDocument doc, 
                EqualityOp equalityOp)
            throws BadLocationException {
        String op = 
                equalityOp instanceof EqualOp ? 
                        " != " : " == ";
        return getTerm(doc, 
                equalityOp.getLeftTerm()) + op + 
                getTerm(doc, equalityOp.getRightTerm());
    }

    private static String getInvertedComparisonTest(IDocument doc, 
            ComparisonOp compOp)
                    throws BadLocationException {
        String op;
        if (compOp instanceof LargerOp) {
            op = " <= ";
        } else if (compOp instanceof LargeAsOp) {
            op = " < ";
        } else if (compOp instanceof SmallerOp) {
            op = " >= ";
        } else if (compOp instanceof SmallAsOp) {
            op = " > ";
        } else {
            throw new RuntimeException("Unknown Comparision op " + 
                    compOp);
        }
        return getTerm(doc, 
                compOp.getLeftTerm()) + op + 
                getTerm(doc, compOp.getRightTerm());
    }


    private static boolean isElseOnOwnLine(IDocument doc, 
            Block ifBlock, Block elseBlock) 
                    throws BadLocationException {
        return doc.getLineOfOffset(ifBlock.getStopIndex()) != 
                doc.getLineOfOffset(elseBlock.getStartIndex());
    }

    private static String addEnclosingBraces(String s, 
            String baseIndent, String indent, String delim) {
        if (s.charAt(0) != '{') {
            return "{" + delim + baseIndent + 
                    indent + indent(s, indent, delim) + 
                    delim + baseIndent + "}";
        }
        return s;
    }
    
    private static String indent(String s, String indentation,
            String delim) {
        return s.replaceAll(delim+"(\\s*)", 
                delim+"$1" + indentation);
    }

    private static String removeEnclosingParenthesis(String s) {
        if (s.charAt(0) == '(') {
            int endIndex = 0;
            int startIndex = 0;
            //Make sure we are not in this case ((a) == (b))
            while ((endIndex = s.indexOf(')', endIndex + 1)) > 0) {
                if (endIndex == s.length() -1 ) {
                    return s.substring(1, s.length() - 1);
                }
                if ((startIndex = s.indexOf('(', startIndex + 1)) >  endIndex) {
                    return s;
                }
            }
        }
        return s;
    }
    
    private static String getTerm(IDocument doc, Node node) 
            throws BadLocationException {
        return doc.get(node.getStartIndex(), 
                node.getStopIndex() - node.getStartIndex() + 1);
    }
}