package com.redhat.ceylon.eclipse.core.launch;

import static com.redhat.ceylon.eclipse.util.EditorUtil.getCurrentEditor;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ITreeSelection;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.views.contentoutline.ContentOutline;

import com.redhat.ceylon.model.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.eclipse.code.editor.CeylonEditor;
import com.redhat.ceylon.eclipse.code.outline.CeylonOutlineNode;
import com.redhat.ceylon.eclipse.code.parse.CeylonParseController;
import com.redhat.ceylon.eclipse.util.Nodes;

public abstract class RunAction extends Action implements IObjectActionDelegate {
//    private IWorkbenchPartSite site;
    protected Declaration declaration;
    protected IProject project;
    protected IResource resource;
    private ContentOutline outlineView;
    
    @Override
    public void run() {
        if (isValidSelection()) {
            getShortcut().launch(declaration, resource, getLaunchMode());
        }
    }

    protected abstract String getLaunchMode();

    protected abstract CeylonModuleLaunchShortcut getShortcut();

    @Override
    public void run(IAction action) {
        run();
    }

    @Override
    public void selectionChanged(IAction action, ISelection selection) {
        if (outlineView==null) return;
        try {
            final ITreeSelection outlineSelection = 
                    (ITreeSelection) outlineView.getSelection();
            CeylonOutlineNode on = 
                    (CeylonOutlineNode) outlineSelection.getFirstElement();
            if (on!=null) {
                IEditorPart currentEditor = getCurrentEditor();
                if (currentEditor instanceof CeylonEditor) {
                    CeylonParseController parseController = 
                            ((CeylonEditor) currentEditor).getParseController();
                    project = parseController.getProject();
                    resource = project.findMember(parseController.getPath());
                    Tree.CompilationUnit rootNode = 
                            parseController.getRootNode();
                    if (rootNode!=null) {
                        Node node = Nodes.findNode(rootNode, 
                                on.getStartOffset());
                        if (node instanceof Tree.Declaration) {
                            declaration = 
                                    ((Tree.Declaration) node).getDeclarationModel();
                            action.setEnabled(isValidSelection());
                            return; //early exit
                        }
                    }
                }
            }
            project=null;
            declaration=null;
            action.setEnabled(false);
        }
        catch (Exception e) {
            action.setEnabled(false);
        }
    }

    @Override
    public void setActivePart(IAction action, IWorkbenchPart targetPart) {
        outlineView = (ContentOutline) targetPart;
//        site = targetPart.getSite();
    }
    
    private boolean isValidSelection() {
        return declaration!=null;
    }

}