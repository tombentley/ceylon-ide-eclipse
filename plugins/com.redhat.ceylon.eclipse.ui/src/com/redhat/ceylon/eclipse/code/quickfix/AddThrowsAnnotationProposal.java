package com.redhat.ceylon.eclipse.code.quickfix;

import static com.redhat.ceylon.eclipse.code.outline.CeylonLabelProvider.CORRECTION;
import static com.redhat.ceylon.eclipse.code.quickfix.AddAnnotionProposal.createInsertAnnotationEdit;
import static com.redhat.ceylon.eclipse.code.quickfix.AddAnnotionProposal.getAnnotationIdentifier;

import java.util.Collection;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.text.edits.InsertEdit;

import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.ProducedType;
import com.redhat.ceylon.compiler.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Annotation;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.AnnotationList;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.CompilationUnit;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Expression;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.MemberOrTypeExpression;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.PositionalArgument;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.PositionalArgumentList;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Statement;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Term;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Throw;
import com.redhat.ceylon.eclipse.code.editor.Util;
import com.redhat.ceylon.eclipse.util.FindContainerVisitor;

public class AddThrowsAnnotationProposal extends ChangeCorrectionProposal {
    
    public static void addThrowsAnnotationProposal(Collection<ICompletionProposal> proposals, Statement statement, CompilationUnit cu, IFile file, IDocument doc) {
        ProducedType exceptionType = determineExceptionType(statement);
        if (exceptionType == null) {
            return;
        }
        
        Tree.Declaration throwContainer = determineThrowContainer(statement, cu);
        if( !(throwContainer instanceof Tree.MethodDefinition) && 
            !(throwContainer instanceof Tree.ClassOrInterface) ) {
            return;               
        }
        
        if (isAlreadyPresent(throwContainer, exceptionType)) {
            return;
        }

        String throwsAnnotation = "throws(" + exceptionType.getProducedTypeName() + ", \"\")";
        InsertEdit throwsAnnotationInsertEdit = createInsertAnnotationEdit(throwsAnnotation, throwContainer, doc);
        TextFileChange throwsAnnotationChange = new TextFileChange("Add throws annotation", file);
        throwsAnnotationChange.setEdit(throwsAnnotationInsertEdit);

        int cursorOffset = throwsAnnotationInsertEdit.getOffset() + throwsAnnotationInsertEdit.getText().indexOf(")") - 1;
        AddThrowsAnnotationProposal proposal = new AddThrowsAnnotationProposal(throwsAnnotationChange, exceptionType, file, cursorOffset, throwContainer.getIdentifier() != null ? throwContainer.getIdentifier().getText() : "");
        if (!proposals.contains(proposal)) {
            proposals.add(proposal);
        }
    }

    private static ProducedType determineExceptionType(Statement statement) {
        ProducedType exceptionType = null;
    
        if (statement instanceof Throw) {
            ProducedType ceylonLangExceptionType = statement.getUnit().getExceptionDeclaration().getType();
            Expression throwExpression = ((Throw) statement).getExpression();
            if (throwExpression == null) {
                exceptionType = ceylonLangExceptionType;
            } else {
                ProducedType throwExpressionType = throwExpression.getTypeModel();
                if ( throwExpressionType != null && 
                     throwExpressionType.isSubtypeOf(ceylonLangExceptionType) ) {
                    exceptionType = throwExpressionType;
                }
            }
        }
    
        return exceptionType;
    }

    private static Tree.Declaration determineThrowContainer(Statement statement, CompilationUnit cu) {
        FindContainerVisitor fcv = new FindContainerVisitor(statement);
        fcv.visit(cu);
        return fcv.getDeclaration();
    }

    private static boolean isAlreadyPresent(Tree.Declaration throwContainer, ProducedType exceptionType) {
        AnnotationList annotationList = throwContainer.getAnnotationList();
        if (annotationList != null) {
            for (Annotation annotation : annotationList.getAnnotations()) {
                String annotationIdentifier = getAnnotationIdentifier(annotation);
                if ("throws".equals(annotationIdentifier)) {
                    PositionalArgumentList positionalArgumentList = annotation.getPositionalArgumentList();
                    if (positionalArgumentList != null && 
                            positionalArgumentList.getPositionalArguments() != null &&
                            positionalArgumentList.getPositionalArguments().size() > 0) {
                        PositionalArgument throwsArg = positionalArgumentList.getPositionalArguments().get(0);
                        Expression throwsArgExp = throwsArg.getExpression();
                        if (throwsArgExp != null) {
                            Term term = throwsArgExp.getTerm();
                            if (term instanceof MemberOrTypeExpression) {
                                Declaration declaration = ((MemberOrTypeExpression) term).getDeclaration();
                                if (declaration instanceof TypeDeclaration) {
                                    ProducedType type = ((TypeDeclaration) declaration).getType();
                                    if (exceptionType.isExactly(type)) {
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private IFile file;
    private int offset;

    private AddThrowsAnnotationProposal(Change change, ProducedType exceptionType, IFile file, int offset, String declName) {
        super("Add 'throws("+exceptionType.getProducedTypeName()+")' annotation to '" + declName + "'", change, 10, CORRECTION);
        this.file = file;
        this.offset = offset;
    }
    
    @Override
    public void apply(IDocument document) {
        super.apply(document);
        Util.gotoLocation(file, offset);
    }    

}