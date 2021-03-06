package com.redhat.ceylon.eclipse.code.correct;

import static com.redhat.ceylon.eclipse.code.correct.ImportProposals.applyImports;
import static com.redhat.ceylon.eclipse.code.correct.ImportProposals.importType;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.link.ILinkedModeListener;
import org.eclipse.jface.text.link.LinkedModeModel;
import org.eclipse.ltk.core.refactoring.DocumentChange;
import org.eclipse.swt.widgets.Display;
import org.eclipse.text.edits.MultiTextEdit;

import com.redhat.ceylon.model.typechecker.model.Declaration;
import com.redhat.ceylon.model.typechecker.model.ProducedType;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.eclipse.code.editor.CeylonEditor;

public class LinkedModeImporter implements ILinkedModeListener {
    
    private ProducedType type;
    private IDocument document;
    private CeylonEditor editor;

    public LinkedModeImporter(IDocument document, CeylonEditor editor) {
        this.document = document;
        this.editor = editor;
    }

    @Override
    public void left(LinkedModeModel model, int flags) {
        if (type!=null) {
            Display.getCurrent().asyncExec(new Runnable() {
                @Override
                public void run() {
                    Set<Declaration> imports = new HashSet<Declaration>();
                    //note: we want the very latest tree here, so 
                    //get it direct from the editor!
                    Tree.CompilationUnit rootNode = 
                            editor.getParseController().getRootNode();
                    importType(imports, type, rootNode);
                    if (!imports.isEmpty()) {
                        DocumentChange change = 
                                new DocumentChange("Import Type", document);
                        change.setEdit(new MultiTextEdit());
                        applyImports(change, imports, rootNode, document);
                        try {
                            change.perform(new NullProgressMonitor());
                        }
                        catch (CoreException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        }
    }

    @Override
    public void suspend(LinkedModeModel model) {}

    @Override
    public void resume(LinkedModeModel model, int flags) {}

    public void setImportedType(ProducedType type) {
        this.type = type;
    }

}