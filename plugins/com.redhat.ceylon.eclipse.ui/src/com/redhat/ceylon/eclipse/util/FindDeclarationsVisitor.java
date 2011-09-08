package com.redhat.ceylon.eclipse.util;

import java.util.HashSet;
import java.util.Set;

import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;

public class FindDeclarationsVisitor extends Visitor {
	
	private final Declaration declaration;
	private Set<Tree.Declaration> declarationNodes = new HashSet<Tree.Declaration>();
	
	public FindDeclarationsVisitor(Declaration declaration) {
		this.declaration = declaration;
	}
	
	public Set<Tree.Declaration> getDeclarationNodes() {
		return declarationNodes;
	}
	
    protected boolean isRefinement(Declaration dec) {
        return dec!=null && dec.refines(declaration);
    }
    
	@Override
	public void visit(Tree.Declaration that) {
		if (isRefinement(that.getDeclarationModel())) {
			declarationNodes.add(that);
		}
		super.visit(that);
	}
		
}
