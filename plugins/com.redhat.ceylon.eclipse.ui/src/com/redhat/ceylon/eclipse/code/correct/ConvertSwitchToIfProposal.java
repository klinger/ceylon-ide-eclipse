package com.redhat.ceylon.eclipse.code.correct;

import static com.redhat.ceylon.eclipse.util.Indents.getDefaultIndent;
import static com.redhat.ceylon.eclipse.util.Indents.getDefaultLineDelimiter;
import static com.redhat.ceylon.eclipse.util.Indents.getIndent;

import java.util.Collection;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;

import com.redhat.ceylon.model.typechecker.model.Declaration;
import com.redhat.ceylon.model.typechecker.model.Unit;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;

public class ConvertSwitchToIfProposal {

    static void addConvertSwitchToIfProposal(
            Collection<ICompletionProposal> proposals, 
            IDocument doc, IFile file, Tree.Statement statement) {
        if (statement instanceof Tree.SwitchStatement) {
            Tree.SwitchStatement ss = 
                    (Tree.SwitchStatement) statement;
            TextFileChange tfc = 
                    new TextFileChange("Convert Switch To If", 
                            file);
            tfc.setEdit(new MultiTextEdit());
            Tree.SwitchClause sc = ss.getSwitchClause();
            if (sc==null) return;
            Tree.SwitchCaseList scl = ss.getSwitchCaseList();
            Tree.Switched switched = sc.getSwitched();
            if (switched==null) return;
            Tree.Expression e = 
                    switched.getExpression();
            Tree.Variable v = 
                    switched.getVariable();
            String name;
            if (e!=null) {
                Tree.Term t = e.getTerm();
                if (t instanceof Tree.BaseMemberExpression) {
                    Tree.BaseMemberExpression bme = 
                            (Tree.BaseMemberExpression) t;
                    name = bme.getDeclaration().getName();
                    tfc.addEdit(new DeleteEdit(sc.getStartIndex(), 
                            scl.getStartIndex()-sc.getStartIndex()));
                }
                else {
                    return;
                }
            }
            else if (v!=null) {
                name = v.getDeclarationModel().getName();
                tfc.addEdit(new ReplaceEdit(sc.getStartIndex(), 
                        v.getStartIndex()-sc.getStartIndex(), 
                        "value "));
                tfc.addEdit(new ReplaceEdit(sc.getStopIndex(), 
                        1, ";"));
            }
            else {
                return;
            }
            String kw = "if";
            int i=0;
            for (Tree.CaseClause cc: scl.getCaseClauses()) {
                Tree.CaseItem ci = cc.getCaseItem();
                if (++i==scl.getCaseClauses().size() &&
                        scl.getElseClause()==null) {
                    tfc.addEdit(new ReplaceEdit(cc.getStartIndex(), 
                            ci.getStopIndex()+1-cc.getStartIndex(), 
                            "else"));
                }
                else {
                    tfc.addEdit(new ReplaceEdit(cc.getStartIndex(), 
                            4, kw));
                    kw = "else if";
                    if (ci instanceof Tree.IsCase) {
                        tfc.addEdit(new InsertEdit(ci.getStopIndex(), 
                                    " " + name));
                    }
                    else if (ci instanceof Tree.MatchCase) {
                        Tree.MatchCase mc = (Tree.MatchCase) ci;
                        Tree.ExpressionList el = 
                                mc.getExpressionList();
                        if (el.getExpressions().size()==1) {
                            Tree.Expression e0 = 
                                    el.getExpressions().get(0);
                            if (e0!=null) {
                                Tree.Term t0 = e0.getTerm();
                                if (t0 instanceof Tree.BaseMemberExpression) {
                                    Tree.BaseMemberExpression bme = 
                                            (Tree.BaseMemberExpression) t0;
                                    Declaration d = bme.getDeclaration();
                                    Unit unit = statement.getUnit();
                                    if (unit.getNullValueDeclaration()
                                            .equals(d)) {
                                        tfc.addEdit(new ReplaceEdit(ci.getStartIndex(), 
                                                ci.getStopIndex()-ci.getStartIndex(),
                                                "!exists " + name));
                                        continue;
                                    }
                                    else if (unit.getLanguageModuleDeclaration("true")
                                            .equals(d)) {
                                        tfc.addEdit(new ReplaceEdit(ci.getStartIndex(), 
                                                ci.getStopIndex()-ci.getStartIndex(),
                                                name));
                                        continue;
                                    }
                                    else if (unit.getLanguageModuleDeclaration("false")
                                            .equals(d)) {
                                        tfc.addEdit(new ReplaceEdit(ci.getStartIndex(), 
                                                ci.getStopIndex()-ci.getStartIndex(),
                                                "!" + name));
                                        continue;
                                    }
                                }
                            }
                            tfc.addEdit(new InsertEdit(ci.getStartIndex(), 
                                    name + " == "));
                        }
                        else {
                            tfc.addEdit(new InsertEdit(ci.getStartIndex(), 
                                    name + " in ["));
                            tfc.addEdit(new InsertEdit(ci.getStopIndex(), 
                                    "]"));
                        }
                    }
                    else {
                        return;
                    }
                }
            }
            proposals.add(new CorrectionProposal(
                    "Convert 'switch' to 'if' chain", tfc,
                    new Region(sc.getStartIndex(), 0)));
        }
    }
    
    static void addConvertIfToSwitchProposal(
            Collection<ICompletionProposal> proposals, 
            IDocument doc, IFile file, Tree.Statement statement) {
        if (statement instanceof Tree.IfStatement) {
            Tree.IfStatement is = 
                    (Tree.IfStatement) statement;
            TextFileChange tfc = 
                    new TextFileChange("Convert If To Switch", 
                            file);          
            tfc.setEdit(new MultiTextEdit());
            Tree.ConditionList cl = 
                    is.getIfClause()
                        .getConditionList();
            if (cl!=null) {
                List<Tree.Condition> conditions = 
                        cl.getConditions();
                if (conditions.size()==1) {
                    Tree.Condition condition = 
                            conditions.get(0);
                    
                    String var;
                    String type;
                    if (condition instanceof Tree.IsCondition) {
                        Tree.IsCondition ic = 
                                (Tree.IsCondition) condition;
                        if (ic.getNot()) return;
                        try {
                            Tree.Variable v = ic.getVariable();
                            int start = v.getStartIndex();
                            int len = v.getStopIndex()-start+1;
                            var = doc.get(start, len);
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                            return;
                        }
                        try {
                            Tree.Type t = ic.getType();
                            int start = t.getStartIndex();
                            int len = t.getStopIndex()-start+1;
                            type = "is " + doc.get(start, len);
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                            return;
                        }
                    }
                    else if (condition instanceof Tree.ExistsCondition) {
                        Tree.ExistsCondition ec = 
                                (Tree.ExistsCondition) condition;
                        type = ec.getNot() ? "null" : "is Object";
                        try {
                            Tree.Statement v = ec.getVariable();
                            int start = v.getStartIndex();
                            int len = v.getStopIndex()-start+1;
                            var = doc.get(start, len);
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                            return;
                        }
                    }
                    else if (condition instanceof Tree.BooleanCondition) {
                        Tree.BooleanCondition ec = 
                                (Tree.BooleanCondition) condition;
                        type = "true";
                        try {
                            Tree.Expression e = ec.getExpression();
                            int start = e.getStartIndex();
                            int len = e.getStopIndex()-start+1;
                            var = doc.get(start, len);
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                            return;
                        }
                    }
                    else {
                        return;
                    }
                    
                    String newline = 
                            getDefaultLineDelimiter(doc) +
                            getIndent(is, doc);
                    tfc.addEdit(new ReplaceEdit(is.getStartIndex(),
                            cl.getStopIndex()-is.getStartIndex()+1, 
                            "switch (" + var + ")" + newline +
                            "case (" + type +")"));
                    Tree.ElseClause ec = is.getElseClause();
                    if (ec==null) {
                        tfc.addEdit(new InsertEdit(is.getStopIndex()+1, 
                                newline + "else {}"));
                    }
                    else {
                        Tree.Block b = ec.getBlock();
                        if (b.getMainToken()==null) {
                            int start = b.getStartIndex();
                            int end = b.getStopIndex()+1;
                            tfc.addEdit(new InsertEdit(start, 
                                    "{" + newline + 
                                    getDefaultIndent()));
                            try {
                                for (int line=doc.getLineOfOffset(start)+1;
                                        line<=doc.getLineOfOffset(end);
                                        line++) {
                                    tfc.addEdit(new InsertEdit(doc.getLineOffset(line),
                                            getDefaultIndent()));
                                }
                            }
                            catch (Exception e) {
                                e.printStackTrace();
                            }
                            tfc.addEdit(new InsertEdit(end, 
                                    newline + "}"));
                        }
                    }
                    proposals.add(new CorrectionProposal(
                            "Convert 'if' to 'switch'", tfc,
                            new Region(is.getStartIndex(), 0)));
                }
            }
        }
    }

}
