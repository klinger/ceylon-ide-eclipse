package com.redhat.ceylon.eclipse.code.correct;

import static com.redhat.ceylon.eclipse.code.correct.ImportProposals.applyImports;
import static com.redhat.ceylon.eclipse.code.correct.ImportProposals.importType;
import static com.redhat.ceylon.eclipse.util.Indents.getDefaultIndent;
import static com.redhat.ceylon.eclipse.util.Indents.getDefaultLineDelimiter;
import static com.redhat.ceylon.eclipse.util.Indents.getIndent;
import static com.redhat.ceylon.eclipse.util.Nodes.getReferencedNodeInUnit;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;

import com.redhat.ceylon.model.typechecker.model.Declaration;
import com.redhat.ceylon.model.typechecker.model.ProducedType;
import com.redhat.ceylon.model.typechecker.model.TypedDeclaration;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.ParameterList;

class SplitDeclarationProposal extends CorrectionProposal {
    
	private SplitDeclarationProposal(Declaration dec, 
	        int offset, TextChange change) {
        super("Split declaration of '" + dec.getName() + "'", 
                change, new Region(offset, 0));
    }
    
	private static void addSplitDeclarationProposal(IDocument doc, 
	        Tree.TypedDeclaration decNode, Tree.CompilationUnit rootNode, 
	        IFile file, Collection<ICompletionProposal> proposals) {
        TypedDeclaration dec = 
                decNode.getDeclarationModel();
        if (dec==null) return;
        if (dec.isToplevel()) return;
        Tree.Identifier id = decNode.getIdentifier();
        if (id==null || id.getToken()==null) return;
        int idStartOffset = id.getStartIndex();
        int idEndOffset = id.getStopIndex()+1;
        int startOffset = decNode.getStartIndex();
        int paramsEndOffset = idEndOffset;
        String paramsString = "";
        String typeString;
        Tree.Type type = decNode.getType();
        if (type==null || type.getToken()==null) return;
        try {
            int typeStartOffset = type.getStartIndex();
            int typeEndOffset = type.getStopIndex()+1;
            typeString = 
                    doc.get(typeStartOffset, 
                            typeEndOffset-typeStartOffset);
            if (decNode instanceof Tree.MethodDeclaration) {
                Tree.MethodDeclaration md = 
                        (Tree.MethodDeclaration) decNode;
                List<ParameterList> pls = 
                        md.getParameterLists();
                if (pls.isEmpty()) {
                    return;
                } 
                else {
                    int paramsOffset = 
                            pls.get(0).getStartIndex();
                    paramsEndOffset = 
                            pls.get(pls.size()-1)
                                .getStopIndex()+1;
                    paramsString = 
                            doc.get(paramsOffset, 
                                    paramsEndOffset-paramsOffset);
                }
            }
        } 
        catch (BadLocationException e) {
            e.printStackTrace();
            return;
        }
        TextChange change = 
                new TextFileChange("Split Declaration", 
                        file);
        change.setEdit(new MultiTextEdit());
        String delim = getDefaultLineDelimiter(doc);
        String indent = getIndent(decNode, doc);
        if (dec.isParameter()) {
            //TODO: does not handle default args correctly 
            //      for callable parameters
            change.addEdit(new DeleteEdit(startOffset, 
                    idStartOffset-startOffset));
            change.addEdit(new DeleteEdit(idEndOffset, 
                    paramsEndOffset-idEndOffset));
            Declaration container = 
                    (Declaration) dec.getContainer();
            Node containerNode = 
                    getReferencedNodeInUnit(container, 
                            rootNode);
            Tree.Body body;
            if (containerNode instanceof Tree.ClassDefinition) {
                Tree.ClassDefinition cd = 
                        (Tree.ClassDefinition) containerNode;
                body = cd.getClassBody();
            }
            else if (containerNode instanceof Tree.MethodDefinition) {
                Tree.MethodDefinition md = 
                        (Tree.MethodDefinition) containerNode;
                body = md.getBlock();
            }
            else if (containerNode instanceof Tree.FunctionArgument) {
                Tree.FunctionArgument fa = 
                        (Tree.FunctionArgument) containerNode;
                body = fa.getBlock();
            }
            else {
                return;
            }
            if (body==null || 
                    body.getStatements()
                        .contains(decNode)) {
                return;
            }
            Tree.AnnotationList al = 
                    decNode.getAnnotationList();
            String annotations;
            if (al==null || al.getToken()==null) {
                annotations = "";
            }
            else {
                try {
                    int alstop = al.getStopIndex();
                    int alstart = al.getStartIndex();
                    int len = alstop-alstart+1;
                    if (len==0) {
                        annotations = "";
                    }
                    else {
                        annotations = 
                                doc.get(alstart, len) + " ";
                    }
                }
                catch (BadLocationException e) {
                    annotations = "";
                }
            }
            String text = 
                    delim + indent + getDefaultIndent() + 
                    annotations + typeString + " " + 
                    dec.getName() + 
                    paramsString + ";";
            int bstart = body.getStartIndex();
            int bstop = body.getStopIndex();
            if (bstop==bstart+1) {
                text += delim + indent;
            }
            change.addEdit(new InsertEdit(bstart+1, 
                    text));
        }
        else {
            String text = paramsString +";" + 
                    delim + indent + dec.getName();
            change.addEdit(new InsertEdit(idEndOffset, 
                    text));
        }
        int il;
        if (type instanceof Tree.LocalModifier) {
            ProducedType infType = type.getTypeModel();
            String explicitType;
            if (infType==null) {
                explicitType = "Object";
                il=0;
            }
            else {
                explicitType = 
                        infType.getProducedTypeNameInSource(
                                decNode.getUnit());
                HashSet<Declaration> decs = 
                        new HashSet<Declaration>();
                importType(decs, infType, rootNode);
                il=applyImports(change, decs, rootNode, doc);
            }
            int typeOffset = type.getStartIndex();
            int typeLen = type.getStopIndex()-typeOffset+1;
            change.addEdit(new ReplaceEdit(typeOffset, 
                    typeLen, explicitType));
        }
        else {
            il=0;
        }
        proposals.add(new SplitDeclarationProposal(dec, 
                idEndOffset+il, change));
    }

	static void addSplitDeclarationProposals(
			Collection<ICompletionProposal> proposals, 
			IDocument doc, IFile file, 
			Tree.CompilationUnit cu, 
			Tree.Declaration decNode,
			Tree.Statement statement) {
	    if (decNode==null) return;
	    Declaration dec = decNode.getDeclarationModel();
	    if (dec!=null) {
	        if (decNode instanceof Tree.AttributeDeclaration) {
	            Tree.AttributeDeclaration attDecNode = 
	                    (Tree.AttributeDeclaration) decNode;
	            Tree.SpecifierOrInitializerExpression sie = 
	                    attDecNode.getSpecifierOrInitializerExpression();
	            if (sie!=null || dec.isParameter()) {
	                addSplitDeclarationProposal(doc, 
	                        attDecNode, cu, file, proposals);
	            }
	        }
	        if (decNode instanceof Tree.MethodDeclaration) {
	            Tree.MethodDeclaration methDecNode = 
	                    (Tree.MethodDeclaration) decNode;
	            Tree.SpecifierExpression sie = 
	                    methDecNode.getSpecifierExpression();
	            if (sie!=null || dec.isParameter()) {
	                addSplitDeclarationProposal(doc, 
	                        methDecNode, cu, file, proposals);
	            }
	        }
	        if (decNode instanceof Tree.Variable &&
	                statement instanceof Tree.ControlStatement) {
	            Tree.Variable varNode = 
                        (Tree.Variable) decNode;
                Tree.SpecifierExpression sie = 
                        varNode.getSpecifierExpression();
                if (sie!=null) {
                    addSplitDeclarationProposal(doc, varNode, 
                            (Tree.ControlStatement) statement, 
                            cu, file, proposals);
                }
	        }
	    }
	}

    private static void addSplitDeclarationProposal(IDocument doc,
            Tree.Variable varNode, Tree.ControlStatement statement, 
            Tree.CompilationUnit cu, IFile file, 
            Collection<ICompletionProposal> proposals) {
        Tree.SpecifierExpression sie = 
                varNode.getSpecifierExpression();
        Tree.Identifier id = varNode.getIdentifier();
        if (sie!=null && id!=null && 
                !(varNode.getType() instanceof Tree.SyntheticVariable)) {
            TextFileChange tfc = 
                    new TextFileChange("Split Variable", 
                            file);
            tfc.setEdit(new MultiTextEdit());
            int vstart = varNode.getStartIndex();
            int vstop = varNode.getStopIndex();
            String text;
            try {
                text = "value " +
                        doc.get(vstart, vstop-vstart+1) + 
                        ";" + getDefaultLineDelimiter(doc) + 
                        getIndent(statement, doc);
            }
            catch (BadLocationException e) {
                e.printStackTrace();
                return;
            }
            int start = statement.getStartIndex();
            tfc.addEdit(new InsertEdit(start, text));
            int estart = id.getStopIndex()+1;
            int estop = sie.getStopIndex();
            tfc.addEdit(new DeleteEdit(estart, estop-estart+1));
            proposals.add(new SplitDeclarationProposal(
                    varNode.getDeclarationModel(),
                    start+6, tfc));
        }
    }
    
}