package com.redhat.ceylon.eclipse.code.correct;

import static com.redhat.ceylon.eclipse.code.correct.CorrectionUtil.getRootNode;
import static com.redhat.ceylon.eclipse.core.builder.CeylonBuilder.getFile;
import static com.redhat.ceylon.eclipse.core.builder.CeylonBuilder.getUnits;
import static com.redhat.ceylon.eclipse.util.EditorUtil.getDocument;
import static com.redhat.ceylon.eclipse.util.Indents.getIndent;
import static java.util.Arrays.asList;

import java.util.Collection;
import java.util.List;

import org.antlr.runtime.CommonToken;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;

import com.redhat.ceylon.compiler.typechecker.context.PhasedUnit;
import com.redhat.ceylon.model.typechecker.model.Class;
import com.redhat.ceylon.model.typechecker.model.ClassOrInterface;
import com.redhat.ceylon.model.typechecker.model.Constructor;
import com.redhat.ceylon.model.typechecker.model.Declaration;
import com.redhat.ceylon.model.typechecker.model.IntersectionType;
import com.redhat.ceylon.model.typechecker.model.Package;
import com.redhat.ceylon.model.typechecker.model.Parameter;
import com.redhat.ceylon.model.typechecker.model.ProducedType;
import com.redhat.ceylon.model.typechecker.model.Referenceable;
import com.redhat.ceylon.model.typechecker.model.Scope;
import com.redhat.ceylon.model.typechecker.model.TypeAlias;
import com.redhat.ceylon.model.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.model.typechecker.model.TypedDeclaration;
import com.redhat.ceylon.model.typechecker.model.UnionType;
import com.redhat.ceylon.model.typechecker.model.UnknownType;
import com.redhat.ceylon.model.typechecker.model.Value;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;
import com.redhat.ceylon.eclipse.util.FindDeclarationNodeVisitor;

public class AddAnnotionProposal extends CorrectionProposal {
    
    private static final List<String> ANNOTATIONS_ORDER = 
            asList("doc", "throws", "see", "tagged", "shared", "abstract", 
                    "actual", "formal", "default", "variable");
    private static final List<String> ANNOTATIONS_ON_SEPARATE_LINE = 
            asList("doc", "throws", "see", "tagged");
    
    private final Referenceable dec;
    private final String annotation;
    
    AddAnnotionProposal(Referenceable dec, String annotation, 
            String desc, int offset, TextFileChange change, 
            Region selection) {
        super(desc, change, selection);
        this.dec = dec;
        this.annotation = annotation;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof AddAnnotionProposal) {
            AddAnnotionProposal that = (AddAnnotionProposal) obj;
            return that.dec.equals(dec) && 
                    that.annotation.equals(annotation);
        }
        else {
            return super.equals(obj);
        }
    }

    @Override
    public int hashCode() {
        return dec.hashCode();
    }

    private static void addAddAnnotationProposal(Node node, String annotation, 
            String desc, Referenceable dec, Collection<ICompletionProposal> proposals, 
            IProject project) {
        if (dec!=null && dec.getNameAsString()!=null && 
                !(node instanceof Tree.MissingDeclaration)) {
            for (PhasedUnit unit: getUnits(project)) {
                if (dec.getUnit().equals(unit.getUnit())) {
                    FindDeclarationNodeVisitor fdv = 
                            new FindDeclarationNodeVisitor(dec);
                    getRootNode(unit).visit(fdv);
                    Tree.StatementOrArgument decNode = 
                            (Tree.StatementOrArgument) 
                                fdv.getDeclarationNode();
                    if (decNode!=null) {
                        addAddAnnotationProposal(annotation, 
                                desc, dec, proposals, unit, 
                                node, decNode);
                    }
                    break;
                }
            }
        }
    }

    private static void addAddAnnotationProposal(String annotation, 
            String desc, Referenceable dec, 
            Collection<ICompletionProposal> proposals, 
            PhasedUnit unit, Node node, 
            Tree.StatementOrArgument decNode) {
        IFile file = getFile(unit);
        TextFileChange change = new TextFileChange(desc, file);
        change.setEdit(new MultiTextEdit());
        TextEdit edit = 
                createReplaceAnnotationEdit(annotation, node, change);
        if (edit==null) {
        	edit = createInsertAnnotationEdit(annotation, decNode, 
        			getDocument(change));
        }
        change.addEdit(edit);
        createExplicitTypeEdit(decNode, change);
        Region selection;
        if (node!=null && 
                node.getUnit().equals(decNode.getUnit())) {
            selection = 
                    new Region(edit.getOffset(), 
                            annotation.length());
        }
        else {
            selection = null;
        }
        AddAnnotionProposal p = 
                new AddAnnotionProposal(dec, annotation, 
                        description(annotation, dec), 
                        edit.getOffset(), 
                        change, selection);
        if (!proposals.contains(p)) {
            proposals.add(p);
        }
    }

    private static void createExplicitTypeEdit(
            Tree.StatementOrArgument decNode, TextFileChange change) {
        if (decNode instanceof Tree.TypedDeclaration &&
                !(decNode instanceof Tree.ObjectDefinition)) {
            Tree.TypedDeclaration tdNode = 
                    (Tree.TypedDeclaration) decNode;
            Tree.Type type = tdNode.getType();
            if (type.getToken()!=null &&
                    (type instanceof Tree.FunctionModifier || 
                     type instanceof Tree.ValueModifier)) {
                ProducedType it = type.getTypeModel();
                if (it!=null && 
                        !(it.getDeclaration() instanceof UnknownType)) {
                    String explicitType = 
                            it.getProducedTypeName();
                    change.addEdit(new ReplaceEdit(type.getStartIndex(), 
                            type.getText().length(), explicitType));
                }
            }
        }
    }

    private static String description(String annotation, Referenceable dec) {
        String description;
        if (dec instanceof Declaration) {
            Declaration d = (Declaration) dec;
            Scope container = d.getContainer();
            String containerDesc = "";
            if (container instanceof TypeDeclaration) {
                TypeDeclaration td = 
                        (TypeDeclaration) container;
                String name = td.getName();
                if (name == null && 
                        container instanceof Constructor) {
                    Scope cont = container.getContainer();
                    if (cont instanceof Declaration) {
                        name = ((Declaration) cont).getName();
                    }
                }
                containerDesc = " in '" + name + "'";
            }
            String name = d.getName();
            if (name == null && 
                    dec instanceof Constructor) {
                Scope cont = d.getContainer();
                if (cont instanceof Declaration) {
                    name = ((Declaration) container).getName();
                    containerDesc = "";
                }
            }
            description = 
                    "Make '" + name + "' " + 
                            annotation + containerDesc;
        }
        else  {
            description = "Make package '" + 
                    dec.getNameAsString() + "' " + 
                    annotation;
        }
        return description;
    }

	private static ReplaceEdit createReplaceAnnotationEdit(String annotation,
			Node node, TextFileChange change) {
		String toRemove;
		if ("formal".equals(annotation)) {
			toRemove = "default";
		}
		else if ("abstract".equals(annotation)) {
			toRemove = "final";
		}
		else {
			return null;
		}
		Tree.AnnotationList annotationList = 
		        getAnnotationList(node);
		if (annotationList != null) {
			for (Tree.Annotation ann:
				annotationList.getAnnotations()) {
				if (toRemove.equals(getAnnotationIdentifier(ann))) {
					Integer start = ann.getStartIndex();
                    int length = ann.getStopIndex()+1-start;
                    return new ReplaceEdit(start, length, annotation);
				}
			}
		}
		return null;
	}

    public static InsertEdit createInsertAnnotationEdit(String newAnnotation, 
            Node node, IDocument doc) {
        String newAnnotationName = 
                getAnnotationWithoutParam(newAnnotation);

        Tree.Annotation prevAnnotation = null;
        Tree.Annotation nextAnnotation = null;
        Tree.AnnotationList annotationList = 
                getAnnotationList(node);
        if (annotationList != null) {
            for (Tree.Annotation annotation:
                    annotationList.getAnnotations()) {
                if (isAnnotationAfter(newAnnotationName, 
                        getAnnotationIdentifier(annotation))) {
                    prevAnnotation = annotation;
                } else if (nextAnnotation == null) {
                    nextAnnotation = annotation;
                    break;
                }
            }
        }

        int nextNodeStartIndex;
        if (nextAnnotation != null) {
            nextNodeStartIndex = 
                    nextAnnotation.getStartIndex();
        }
        else {
            if (node instanceof Tree.AnyAttribute || 
                node instanceof Tree.AnyMethod ) {
                Tree.TypedDeclaration tdn = 
                        (Tree.TypedDeclaration) node;
                nextNodeStartIndex = 
                        tdn.getType().getStartIndex();
            }
            else if (node instanceof Tree.ObjectDefinition ) {
                nextNodeStartIndex = 
                        ((CommonToken) node.getMainToken())
                                .getStartIndex();
            }
            else if (node instanceof Tree.ClassOrInterface) {
                nextNodeStartIndex = 
                        ((CommonToken) node.getMainToken())
                                .getStartIndex();
            }
            else {
                nextNodeStartIndex = node.getStartIndex();
            }
        }

        int newAnnotationOffset;
        StringBuilder newAnnotationText = new StringBuilder();

        if (isAnnotationOnSeparateLine(newAnnotationName) && 
                !(node instanceof Tree.Parameter)) {
            if (prevAnnotation != null && 
                    isAnnotationOnSeparateLine(getAnnotationIdentifier(prevAnnotation))) {
                newAnnotationOffset = prevAnnotation.getStopIndex() + 1;
                newAnnotationText.append(System.lineSeparator());
                newAnnotationText.append(getIndent(node, doc));
                newAnnotationText.append(newAnnotation);
            } else {
                newAnnotationOffset = nextNodeStartIndex;
                newAnnotationText.append(newAnnotation);
                newAnnotationText.append(System.lineSeparator());
                newAnnotationText.append(getIndent(node, doc));
            }
        } else {
            newAnnotationOffset = nextNodeStartIndex;
            newAnnotationText.append(newAnnotation);
            newAnnotationText.append(" ");
        }

        return new InsertEdit(newAnnotationOffset, 
                newAnnotationText.toString());
    }

    public static Tree.AnnotationList getAnnotationList(Node node) {
        Tree.AnnotationList annotationList = null;
        if (node instanceof Tree.Declaration) {
            Tree.Declaration tdn = 
                    (Tree.Declaration) node;
            annotationList = tdn.getAnnotationList();
        }
        else if (node instanceof Tree.ModuleDescriptor) {
            Tree.ModuleDescriptor mdn = 
                    (Tree.ModuleDescriptor) node;
            annotationList = mdn.getAnnotationList();
        }
        else if (node instanceof Tree.PackageDescriptor) {
            Tree.PackageDescriptor pdn = 
                    (Tree.PackageDescriptor) node;
            annotationList = pdn.getAnnotationList();
        }
        else if (node instanceof Tree.Assertion) {
            Tree.Assertion an = (Tree.Assertion) node;
            annotationList = an.getAnnotationList();
        }
        return annotationList;
    }

    public static String getAnnotationIdentifier(Tree.Annotation annotation) {
        String annotationName = null;
        if (annotation != null) {
            Tree.Primary primary = annotation.getPrimary();
            if (primary instanceof Tree.BaseMemberExpression) {
                Tree.BaseMemberExpression bme = 
                        (Tree.BaseMemberExpression) primary;
                annotationName = bme.getIdentifier().getText();
            }
        }
        return annotationName;
    }

    private static String getAnnotationWithoutParam(String annotation) {
        int index = annotation.indexOf("(");
        if (index != -1) {
            return annotation.substring(0, index).trim();
        }
        index = annotation.indexOf("\"");
        if (index != -1) {
            return annotation.substring(0, index).trim();
        }
        index = annotation.indexOf(" ");
        if (index != -1) {
            return annotation.substring(0, index).trim();
        }
        return annotation.trim();
    }

    private static boolean isAnnotationAfter(String annotation1, String annotation2) {
        int index1 = ANNOTATIONS_ORDER.indexOf(annotation1);
        int index2 = ANNOTATIONS_ORDER.indexOf(annotation2);
        return index1 >= index2;
    }

    private static boolean isAnnotationOnSeparateLine(String annotation) {
        return ANNOTATIONS_ON_SEPARATE_LINE.contains(annotation);
    }
    
    static void addMakeActualDecProposal(Collection<ICompletionProposal> proposals, 
            IProject project, Node node) {
        Declaration dec = annotatedNode(node);
        boolean shared = dec.isShared();
        addAddAnnotationProposal(node, 
        		shared ? "actual" : "shared actual", 
                shared ? "Make Actual" : "Make Shared Actual",
                dec, proposals, project);
    }

    private static Declaration annotatedNode(Node node) {
        Declaration dec;
        if (node instanceof Tree.Declaration) {
            Tree.Declaration dn = (Tree.Declaration) node;
            dec = dn.getDeclarationModel();
        }
        else {
            dec = (Declaration) node.getScope();
        }
        return dec;
    }

    static void addMakeDefaultProposal(Collection<ICompletionProposal> proposals, 
            IProject project, Node node) {
        
        Declaration d;
        if (node instanceof Tree.Declaration) {
            Tree.Declaration decNode = 
                    (Tree.Declaration) node;
            d = decNode.getDeclarationModel();
        }
        if (node instanceof Tree.SpecifierStatement) {
            Tree.SpecifierStatement specNode = 
                    (Tree.SpecifierStatement) node;
            d = specNode.getRefined();
        }
        else if (node instanceof Tree.BaseMemberExpression) {
            Tree.BaseMemberExpression bme = 
                    (Tree.BaseMemberExpression) node;
            d = bme.getDeclaration();
        }
        else {
            return;
        }
        
        if (d.isClassOrInterfaceMember()) {
            List<Declaration> rds = 
                    ((ClassOrInterface) d.getContainer())
                            .getInheritedMembers(d.getName());
            Declaration rd=null;
            if (rds.isEmpty()) {
                rd=d; //TODO: is this really correct? What case does it handle?
            }
            else {
                for (Declaration r: rds) {
                    if (!r.isDefault()) {
                        //just take the first one :-/
                        //TODO: this is very wrong! Instead, make them all default!
                        rd = r;
                        break;
                    }
                }
            }
            if (rd!=null) {
                addAddAnnotationProposal(node, 
                		"default", "Make Default", 
                        rd, proposals, project);
            }
        }
    }

    static void addMakeDefaultDecProposal(Collection<ICompletionProposal> proposals, 
            IProject project, Node node) {
    	Declaration dec = annotatedNode(node);
        addAddAnnotationProposal(node, 
                dec.isShared() ? "default" : "shared default", 
                dec.isShared() ? "Make Default" : "Make Shared Default", 
                dec, proposals, project);
    }

    static void addMakeFormalDecProposal(Collection<ICompletionProposal> proposals, 
            IProject project, Node node) {
    	Declaration dec = annotatedNode(node);
        addAddAnnotationProposal(node, 
                dec.isShared() ? "formal" : "shared formal",
                dec.isShared() ? "Make Formal" : "Make Shared Formal",
                dec, proposals, project);
    }

    static void addMakeAbstractDecProposal(Collection<ICompletionProposal> proposals, 
            IProject project, Node node) {
        Declaration dec = annotatedNode(node);
        if (dec instanceof Class) {
            addAddAnnotationProposal(node, 
            		"abstract", "Make Abstract", 
                    dec, proposals, project);
        }
    }

    static void addMakeContainerAbstractProposal(Collection<ICompletionProposal> proposals, 
            IProject project, Node node) {
        Declaration dec;
        if (node instanceof Tree.Declaration) {
            Tree.Declaration dn = (Tree.Declaration) node;
            Scope container = 
                    dn.getDeclarationModel().getContainer();
            if (container instanceof Declaration) {
                dec = (Declaration) container;
            }
            else {
                return;
            }
        }
        else {
            dec = (Declaration) node.getScope();
        }
        addAddAnnotationProposal(node, 
        		"abstract", "Make Abstract", 
                dec, proposals, project);
    }

    static void addMakeVariableProposal(Collection<ICompletionProposal> proposals, 
            IProject project, Node node) {
        Tree.Term term;
        if (node instanceof Tree.AssignmentOp) {
            Tree.AssignOp ao = (Tree.AssignOp) node;
            term = ao.getLeftTerm();
        }
        else if (node instanceof Tree.UnaryOperatorExpression) {
            Tree.PrefixOperatorExpression poe = 
                    (Tree.PrefixOperatorExpression) node;
            term = poe.getTerm();
        }
        else if (node instanceof Tree.MemberOrTypeExpression) {
            term = (Tree.MemberOrTypeExpression) node;
        }
        else if (node instanceof Tree.SpecifierStatement) {
            Tree.SpecifierStatement ss = 
                    (Tree.SpecifierStatement) node;
            term = ss.getBaseMemberExpression();
        }
        else {
            return;
        }
        if (term instanceof Tree.MemberOrTypeExpression) {
            Tree.MemberOrTypeExpression mte = 
                    (Tree.MemberOrTypeExpression) term;
            Declaration dec = mte.getDeclaration();
            if (dec instanceof Value) {
                if (((Value) dec).getOriginalDeclaration()==null) {
                    addAddAnnotationProposal(node, 
                    		"variable", "Make Variable", 
                            dec, proposals, project);
                }
            }
        }
    }
    
    static void addMakeVariableDecProposal(Collection<ICompletionProposal> proposals,
            IProject project, Tree.Declaration node) {
        final Declaration dec = node.getDeclarationModel();
        if (dec instanceof Value && 
                node instanceof Tree.AttributeDeclaration) {
            final Value v = (Value) dec;
            if (!v.isVariable() && !v.isTransient()) {
                addAddAnnotationProposal(node, 
                		"variable", "Make Variable",
                        dec, proposals, project);
            }
        }
    }
    static void addMakeVariableDecProposal(Collection<ICompletionProposal> proposals,
            IProject project, Tree.CompilationUnit cu, Node node) {
        final Tree.SpecifierOrInitializerExpression sie = 
                (Tree.SpecifierOrInitializerExpression) node;
        class GetInitializedVisitor extends Visitor {
            Value dec;
            @Override
            public void visit(Tree.AttributeDeclaration that) {
                super.visit(that);
                if (that.getSpecifierOrInitializerExpression()==sie) {
                    dec = that.getDeclarationModel();
                }
            }
        }
        GetInitializedVisitor v = 
                new GetInitializedVisitor();
        v.visit(cu);
        addAddAnnotationProposal(node, 
        		"variable", "Make Variable",
                v.dec, proposals, project);
    }
    
    static void addMakeSharedProposalForSupertypes(Collection<ICompletionProposal> proposals, 
            IProject project, Node node) {
        if (node instanceof Tree.ClassOrInterface) {
            Tree.ClassOrInterface cin = 
                    (Tree.ClassOrInterface) node;
            ClassOrInterface ci = cin.getDeclarationModel();
            
            ProducedType extendedType = 
                    ci.getExtendedType();
            if (extendedType!=null) {
                addMakeSharedProposal(proposals, project, 
                        extendedType.getDeclaration());
                for (ProducedType typeArgument:
                        extendedType.getTypeArgumentList()) {
                    addMakeSharedProposal(proposals, project, 
                            typeArgument.getDeclaration());
                }
            }
            
            List<ProducedType> satisfiedTypes = 
                    ci.getSatisfiedTypes();
            if (satisfiedTypes!=null) {
                for (ProducedType satisfiedType: satisfiedTypes) {
                    addMakeSharedProposal(proposals, project, 
                            satisfiedType.getDeclaration());
                    for (ProducedType typeArgument: 
                            satisfiedType.getTypeArgumentList()) {
                        addMakeSharedProposal(proposals, project, 
                                typeArgument.getDeclaration());
                    }
                }
            }
        }
    }
    
    static void addMakeRefinedSharedProposal(Collection<ICompletionProposal> proposals, 
            IProject project, Node node) {
        if (node instanceof Tree.Declaration) {
            Tree.Declaration tdn = (Tree.Declaration) node;
            Declaration refined = 
                    tdn.getDeclarationModel()
                        .getRefinedDeclaration();
            if (refined.isDefault() || refined.isFormal()) {
                addMakeSharedProposal(proposals, project, refined);
            }
            else {
                addAddAnnotationProposal(node, 
                		"shared default", "Make Shared Default", 
                        refined, proposals, project);
            }
        }
    }
    
    static void addMakeSharedProposal(Collection<ICompletionProposal> proposals, 
            IProject project, Node node) {
        Referenceable dec = null;
        List<ProducedType> typeArgumentList = null;
        if (node instanceof Tree.StaticMemberOrTypeExpression) {
            Tree.StaticMemberOrTypeExpression qmte = 
                    (Tree.StaticMemberOrTypeExpression) node;
            dec = qmte.getDeclaration();
        }
        //TODO: handle much more kinds of types!
        else if (node instanceof Tree.SimpleType) {
            Tree.SimpleType st = (Tree.SimpleType) node;
            dec = st.getDeclarationModel();
        }
        else if (node instanceof Tree.OptionalType) {
            Tree.OptionalType ot = 
                    (Tree.OptionalType) node;
            if (ot.getDefiniteType() instanceof Tree.SimpleType) {
                Tree.SimpleType st = (Tree.SimpleType) 
                        ot.getDefiniteType();
                dec = st.getDeclarationModel();
            }
        }
        else if (node instanceof Tree.IterableType) {
            Tree.IterableType it = (Tree.IterableType) node;
            if (it.getElementType() instanceof Tree.SimpleType) {
                Tree.SimpleType st = (Tree.SimpleType) 
                        it.getElementType();
                dec = st.getDeclarationModel();
            }
        }
        else if (node instanceof Tree.SequenceType) {
            Tree.SequenceType qt = (Tree.SequenceType) node;
            if (qt.getElementType() instanceof Tree.SimpleType) {
                Tree.SimpleType st = (Tree.SimpleType) 
                        qt.getElementType();
                dec = st.getDeclarationModel();
            }
        }
        else if (node instanceof Tree.ImportMemberOrType) {
            Tree.ImportMemberOrType imt = 
                    (Tree.ImportMemberOrType) node;
            dec = imt.getDeclarationModel();
        }
        else if (node instanceof Tree.ImportPath) {
            Tree.ImportPath ip = 
                    (Tree.ImportPath) node;
            dec = ip.getModel();
        }
        else if (node instanceof Tree.TypedDeclaration) {
            Tree.TypedDeclaration tdn = 
                    (Tree.TypedDeclaration) node;
            TypedDeclaration td = tdn.getDeclarationModel();
            if (td!=null) {
                ProducedType pt = td.getType();
                dec = pt.getDeclaration();
                typeArgumentList = pt.getTypeArgumentList();
            }
        }
        else if (node instanceof Tree.Parameter) {
            Tree.Parameter parameter = 
                    (Tree.Parameter) node;
            Parameter param = parameter.getParameterModel();
            if (param!=null && param.getType()!=null) {
                ProducedType pt = param.getType();
                dec = pt.getDeclaration();
                typeArgumentList = pt.getTypeArgumentList();
            }
        }
        addMakeSharedProposal(proposals, project, dec);
        if (typeArgumentList != null) {
            for (ProducedType typeArgument: typeArgumentList) {
                addMakeSharedProposal(proposals, project, 
                        typeArgument.getDeclaration());
            }
        }
    }
    
    static void addMakeSharedProposal(Collection<ICompletionProposal> proposals, 
            IProject project, Referenceable dec) {
        if (dec!=null) {
            if (dec instanceof UnionType) {
                List<ProducedType> caseTypes = 
                        ((UnionType) dec).getCaseTypes();
                for (ProducedType caseType: caseTypes) {
                        addMakeSharedProposal(proposals, project, 
                                caseType.getDeclaration());
                    for (ProducedType typeArgument: 
                            caseType.getTypeArgumentList()) {
                        addMakeSharedProposal(proposals, project, 
                                typeArgument.getDeclaration());
                    }
                }
            }
            else if (dec instanceof IntersectionType) {
                List<ProducedType> satisfiedTypes = 
                        ((IntersectionType) dec).getSatisfiedTypes();
                for (ProducedType satisfiedType: satisfiedTypes) {
                    addMakeSharedProposal(proposals, project,
                            satisfiedType.getDeclaration());
                    for (ProducedType typeArgument: 
                            satisfiedType.getTypeArgumentList()) {
                        addMakeSharedProposal(proposals, project,
                                typeArgument.getDeclaration());
                    }
                }
            }
            else if (dec instanceof TypedDeclaration || 
                       dec instanceof ClassOrInterface || 
                       dec instanceof TypeAlias) {
                if (!((Declaration) dec).isShared()) {
                    addAddAnnotationProposal(null, 
                    		"shared", "Make Shared", 
                            dec, proposals, project);
                }
            }
            else if (dec instanceof Package) {
                if (!((Package) dec).isShared()) {
                    addAddAnnotationProposal(null, 
                            "shared", "Make Shared", 
                            dec, proposals, project);
                }
            }
        }
    }
    
    static void addMakeSharedDecProposal(Collection<ICompletionProposal> proposals, 
            IProject project, Node node) {
        if (node instanceof Tree.Declaration) {
            Tree.Declaration dn = (Tree.Declaration) node;
            addAddAnnotationProposal(node, 
            		"shared", "Make Shared",  
                    dn.getDeclarationModel(), 
                    proposals, project);
        }
    }
    
}