package com.redhat.ceylon.eclipse.code.correct;

import static com.redhat.ceylon.model.typechecker.model.Util.intersectionType;
import static com.redhat.ceylon.model.typechecker.model.Util.isTypeUnknown;
import static com.redhat.ceylon.model.typechecker.model.Util.unionType;

import com.redhat.ceylon.model.typechecker.model.Declaration;
import com.redhat.ceylon.model.typechecker.model.Interface;
import com.redhat.ceylon.model.typechecker.model.NothingType;
import com.redhat.ceylon.model.typechecker.model.Parameter;
import com.redhat.ceylon.model.typechecker.model.ProducedReference;
import com.redhat.ceylon.model.typechecker.model.ProducedType;
import com.redhat.ceylon.model.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.model.typechecker.model.TypedDeclaration;
import com.redhat.ceylon.model.typechecker.model.Unit;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;

class InferredType {
    
    private Unit unit;

    InferredType(Unit unit) {
        this.unit = unit;
        
    }
    
    ProducedType inferredType;
    ProducedType generalizedType;
    
    void intersect(ProducedType pt) {
        if (!isTypeUnknown(pt)) {
            if (generalizedType==null) {
                generalizedType = pt;
            }
            else {
                ProducedType it = 
                        intersectionType(generalizedType, 
                                pt, unit);
                if (!(it.getDeclaration() 
                        instanceof NothingType)) {
                    generalizedType = it;
                }
            }
        }
    }
    
    void union(ProducedType pt) {
        if (!isTypeUnknown(pt)) {
            if (inferredType==null) {
                inferredType = pt;
            }
            else {
                inferredType = 
                        unionType(inferredType, 
                                unit.denotableType(pt), 
                                unit);
            }
        }
    }
    
}

class InferTypeVisitor extends Visitor {
    
    Unit unit;
    Declaration dec;
    InferredType result;
    
    InferTypeVisitor(Unit unit) {
        this.unit = unit;
        result = new InferredType(unit);
    }
    
    @Override public void visit(Tree.AttributeDeclaration that) {
        super.visit(that);
        //TODO: an assignment to something with an inferred
        //      type doesn't _directly_ constrain the type
        //      ... but _indirectly_ it can!
//        if (!(that.getType() instanceof Tree.LocalModifier)) {
        Tree.SpecifierOrInitializerExpression sie = 
                    that.getSpecifierOrInitializerExpression();
            Tree.Term term = sie==null ? 
                    null : sie.getExpression().getTerm();
            if (term instanceof Tree.BaseMemberExpression) {
                Tree.BaseMemberExpression bme = 
                        (Tree.BaseMemberExpression) term;
                Declaration d = bme.getDeclaration();
                if (d!=null && d.equals(dec)) {
                    ProducedType t = 
                            that.getType().getTypeModel();
                    result.intersect(t);
                }
            }
            else if (term!=null) {
                if (that.getDeclarationModel().equals(dec)) {
                    result.union(term.getTypeModel());
                }
            }
//        }
    }
    
    @Override public void visit(Tree.MethodDeclaration that) {
        super.visit(that);
        //TODO: an assignment to something with an inferred
        //      type doesn't _directly_ constrain the type
        //      ... but _indirectly_ it can!
//        if (!(that.getType() instanceof Tree.LocalModifier)) {
            Tree.SpecifierExpression se = 
                    that.getSpecifierExpression();
            Tree.Term term = se==null ? 
                    null : se.getExpression().getTerm();
            if (term instanceof Tree.BaseMemberExpression) {
                Tree.BaseMemberExpression bme = 
                        (Tree.BaseMemberExpression) term;
                Declaration d = bme.getDeclaration();
                if (d!=null && d.equals(dec)) {
                    ProducedType t = 
                            that.getType().getTypeModel();
                    result.intersect(t);
                }
            }
            else if (term!=null) {
                if (that.getDeclarationModel().equals(dec)) {
                    result.union(term.getTypeModel());
                }
            }
//        }
    }
    
    @Override public void visit(Tree.SpecifierStatement that) {
        super.visit(that);
        Tree.Term bme = that.getBaseMemberExpression();
        Tree.SpecifierExpression se = 
                that.getSpecifierExpression();
        Tree.Term term = se==null ? 
                null : se.getExpression().getTerm();
        if (bme instanceof Tree.BaseMemberExpression) {
            Tree.BaseMemberExpression ibme = 
                    (Tree.BaseMemberExpression) bme;
            Declaration d = ibme.getDeclaration();
            if (d!=null && d.equals(dec)) {
                if (term!=null)
                    result.union(term.getTypeModel());
            }
        } 
        if (term instanceof Tree.BaseMemberExpression) {
            Tree.BaseMemberExpression ibme = 
                    (Tree.BaseMemberExpression) term;
            Declaration d = ibme.getDeclaration();
            if (d!=null && d.equals(dec)) {
                if (bme!=null)
                    result.intersect(bme.getTypeModel());
            }
        }
    }
    
    @Override public void visit(Tree.AssignmentOp that) {
        super.visit(that);
        Tree.Term rt = that.getRightTerm();
        Tree.Term lt = that.getLeftTerm();
        if (lt instanceof Tree.BaseMemberExpression) {
            Tree.BaseMemberExpression bme = 
                    (Tree.BaseMemberExpression) lt;
            if (bme.getDeclaration().equals(dec)) {
                if (rt!=null)
                    result.union(rt.getTypeModel());
            }
        }
        if (rt instanceof Tree.BaseMemberExpression) {
            Tree.BaseMemberExpression bme = 
                    (Tree.BaseMemberExpression) rt;
            if (bme.getDeclaration().equals(dec)) {
                if (lt!=null)
                    result.intersect(lt.getTypeModel());
            }
        }
    }
    
    private ProducedReference pr;
    
    @Override public void visit(Tree.InvocationExpression that) {
        ProducedReference opr=null;
        Tree.Primary primary = that.getPrimary();
        if (primary!=null) {
            if (primary instanceof Tree.MemberOrTypeExpression) {
                Tree.MemberOrTypeExpression mte = 
                        (Tree.MemberOrTypeExpression) primary;
                pr = mte.getTarget();
            }
        }
        super.visit(that);
        pr = opr;
    }
    
    @Override public void visit(Tree.ListedArgument that) {
        super.visit(that);
        Tree.Term t = that.getExpression().getTerm();
        if (t instanceof Tree.BaseMemberExpression) {
            Tree.BaseMemberExpression bme = 
                    (Tree.BaseMemberExpression) t;
            Declaration d = bme.getDeclaration();
            if (d!=null && d.equals(dec)) {
                Parameter p = that.getParameter();
                if (p!=null && pr!=null) {
                    ProducedType ft = 
                            pr.getTypedParameter(p)
                                .getFullType();
                    if (p.isSequenced()) {
                        ft = unit.getIteratedType(ft);
                    }
                    result.intersect(ft);
                }
            }
        }
    }
    
    @Override public void visit(Tree.SpreadArgument that) {
        super.visit(that);
        Tree.Term t = that.getExpression().getTerm();
        if (t instanceof Tree.BaseMemberExpression) {
            Tree.BaseMemberExpression bme = 
                    (Tree.BaseMemberExpression) t;
            Declaration d = bme.getDeclaration();
            if (d!=null && d.equals(dec)) {
                Parameter p = that.getParameter();
                if (p!=null && pr!=null) {
                    //TODO: is this correct?
                    ProducedType ft = 
                            pr.getTypedParameter(p)
                                .getFullType();
                    ProducedType et = 
                            unit.getIteratedType(ft);
                    ProducedType it = 
                            unit.getIterableType(et);
                    result.intersect(it);
                }
            }
        }
    }
    
    @Override public void visit(Tree.SpecifiedArgument that) {
        super.visit(that);
        Tree.Term t = 
                that.getSpecifierExpression()
                    .getExpression().getTerm();
        if (t instanceof Tree.BaseMemberExpression) {
            Tree.BaseMemberExpression bme = 
                    (Tree.BaseMemberExpression) t;
            Declaration d = bme.getDeclaration();
            if (d!=null && d.equals(dec)) {
                Parameter p = that.getParameter();
                if (p!=null && pr!=null) {
                    ProducedType ft = 
                            pr.getTypedParameter(p)
                                .getFullType();
                    result.intersect(ft);
                }
            }
        }
    }
    
    @Override public void visit(Tree.Return that) {
        super.visit(that);
        Tree.Term bme = that.getExpression().getTerm();
        if (bme instanceof Tree.BaseMemberExpression) {
            Tree.BaseMemberExpression ibme = 
                    (Tree.BaseMemberExpression) bme;
            Declaration bmed = ibme.getDeclaration();
            if (bmed!=null && bmed.equals(dec)) {
                Declaration d = that.getDeclaration();
                if (d instanceof TypedDeclaration) {
                    TypedDeclaration td = 
                            (TypedDeclaration) d;
                    result.intersect(td.getType());
                }
            }
        }
        else if (bme!=null) {
            if (that.getDeclaration().equals(dec)) {
                result.union(bme.getTypeModel());
            }
        }
    }
    
    @Override
    public void visit(Tree.QualifiedMemberOrTypeExpression that) {
        super.visit(that);
        Tree.Primary primary = that.getPrimary();
        if (primary instanceof Tree.BaseMemberExpression) {
            Tree.BaseMemberExpression bme = 
                    (Tree.BaseMemberExpression) primary;
            Declaration bmed = bme.getDeclaration();
            if (bmed!=null && bmed.equals(dec)) {
                TypeDeclaration td = (TypeDeclaration) 
                        that.getDeclaration()
                            .getRefinedDeclaration()
                            .getContainer();
                ProducedType st = 
                        that.getTarget()
                            .getQualifyingType()
                            .getSupertype(td);
                result.intersect(st);
            }
        }
    }
    
    /*@Override
    public void visit(Tree.PatternIterator that) {
        super.visit(that);
        Tree.Term primary = that.getSpecifierExpression().getExpression().getTerm();
        if (primary instanceof Tree.BaseMemberExpression) {
            Declaration bmed = ((Tree.BaseMemberExpression) primary).getDeclaration();
            if (bmed!=null && bmed.equals(dec)) {
                ProducedType kt = that.getKeyVariable().getType().getTypeModel();
                ProducedType vt = that.getValueVariable().getType().getTypeModel();
                result.intersect(that.getUnit().getIterableType(that.getUnit().getEntryType(kt, vt)));
            }
        }
    }*/
    
    @Override
    public void visit(Tree.ValueIterator that) {
        super.visit(that);
        Tree.Term primary = 
                that.getSpecifierExpression()
                    .getExpression().getTerm();
        if (primary instanceof Tree.BaseMemberExpression) {
            Tree.BaseMemberExpression bme = 
                    (Tree.BaseMemberExpression) primary;
            Declaration bmed = bme.getDeclaration();
            if (bmed!=null && bmed.equals(dec)) {
                ProducedType vt = 
                        that.getVariable().getType()
                            .getTypeModel();
                ProducedType it = unit.getIterableType(vt);
                result.intersect(it);
            }
        }
    }
    
    @Override
    public void visit(Tree.BooleanCondition that) {
        super.visit(that);
        Tree.Term primary = that.getExpression().getTerm();
        if (primary instanceof Tree.BaseMemberExpression) {
            Tree.BaseMemberExpression bme = 
                    (Tree.BaseMemberExpression) primary;
            Declaration bmed = bme.getDeclaration();
            if (bmed!=null && bmed.equals(dec)) {
                ProducedType bt = 
                        unit.getBooleanDeclaration()
                            .getType();
                result.intersect(bt);
            }
        }
    }

    @Override
    public void visit(Tree.NonemptyCondition that) {
        super.visit(that);
        Tree.Statement s = that.getVariable();
        if (s instanceof Tree.Variable) {
            Tree.Variable var = (Tree.Variable) s;
            Tree.Term primary = 
                    var.getSpecifierExpression()
                        .getExpression().getTerm();
            if (primary instanceof Tree.BaseMemberExpression) {
                Tree.BaseMemberExpression bme = 
                        (Tree.BaseMemberExpression) primary;
                Declaration bmed = bme.getDeclaration();
                if (bmed!=null && bmed.equals(dec)) {
                    ProducedType vt = 
                            var.getType().getTypeModel();
                    ProducedType et = 
                            unit.getSequentialElementType(vt);
                    ProducedType st = 
                            unit.getSequentialType(et);
                    result.intersect(st);
                }
            }
        }
    }
    
    @Override
    public void visit(Tree.ArithmeticOp that) {
        super.visit(that);
        Interface sd = getArithmeticDeclaration(that);
        genericOperatorTerm(sd, that.getLeftTerm());
        genericOperatorTerm(sd, that.getRightTerm());
    }

    @Override
    public void visit(Tree.NegativeOp that) {
        super.visit(that);
        Interface sd = unit.getInvertableDeclaration();
        genericOperatorTerm(sd, that.getTerm());
    }

    @Override
    public void visit(Tree.PrefixOperatorExpression that) {
        super.visit(that);
        Interface sd = unit.getOrdinalDeclaration();
        genericOperatorTerm(sd, that.getTerm());
    }

    @Override
    public void visit(Tree.PostfixOperatorExpression that) {
        super.visit(that);
        Interface sd = unit.getOrdinalDeclaration();
        genericOperatorTerm(sd, that.getTerm());
    }

    @Override
    public void visit(Tree.BitwiseOp that) {
        super.visit(that);
        Interface sd = unit.getSetDeclaration();
        genericOperatorTerm(sd, that.getLeftTerm());
        genericOperatorTerm(sd, that.getRightTerm());
    }

    @Override
    public void visit(Tree.ComparisonOp that) {
        super.visit(that);
        Interface sd = unit.getComparableDeclaration();
        genericOperatorTerm(sd, that.getLeftTerm());
        genericOperatorTerm(sd, that.getRightTerm());
    }

    @Override
    public void visit(Tree.CompareOp that) {
        super.visit(that);
        Interface sd = unit.getComparableDeclaration();
        genericOperatorTerm(sd, that.getLeftTerm());
        genericOperatorTerm(sd, that.getRightTerm());
    }

    @Override
    public void visit(Tree.LogicalOp that) {
        super.visit(that);
        TypeDeclaration sd = unit.getBooleanDeclaration();
        operatorTerm(sd, that.getLeftTerm());
        operatorTerm(sd, that.getRightTerm());
    }

    @Override
    public void visit(Tree.NotOp that) {
        super.visit(that);
        TypeDeclaration sd = unit.getBooleanDeclaration();
        operatorTerm(sd, that.getTerm());
    }

    @Override
    public void visit(Tree.EntryOp that) {
        super.visit(that);
        TypeDeclaration sd = unit.getObjectDeclaration();
        operatorTerm(sd, that.getLeftTerm());
        operatorTerm(sd, that.getRightTerm());
    }

    private Interface getArithmeticDeclaration(Tree.ArithmeticOp that) {
        if (that instanceof Tree.PowerOp) {
            return unit.getExponentiableDeclaration();
        }
        else if (that instanceof Tree.SumOp) {
            return unit.getSummableDeclaration();
        }
        else if (that instanceof Tree.DifferenceOp) {
            return unit.getInvertableDeclaration();
        }
        else if (that instanceof Tree.RemainderOp) {
            return unit.getIntegralDeclaration();
        }
        else {
            return unit.getNumericDeclaration();
        }
    }

    public void operatorTerm(TypeDeclaration sd, Tree.Term lhs) {
        if (lhs instanceof Tree.BaseMemberExpression) {
            Tree.BaseMemberExpression bme = 
                    (Tree.BaseMemberExpression) lhs;
            Declaration bmed = bme.getDeclaration();
            if (bmed!=null && bmed.equals(dec)) {
                result.intersect(sd.getType());
            }
        }
    }
    
    public void genericOperatorTerm(TypeDeclaration sd, Tree.Term lhs) {
        if (lhs instanceof Tree.BaseMemberExpression) {
            Tree.BaseMemberExpression bme = 
                    (Tree.BaseMemberExpression) lhs;
            Declaration bmed = bme.getDeclaration();
            if (bmed!=null && bmed.equals(dec)) {
                ProducedType st = 
                        lhs.getTypeModel().getSupertype(sd);
                ProducedType at = 
                        st.getTypeArguments().get(0);
                result.intersect(at);
            }
        }
    }
    
    //TODO: more operator expressions!
}