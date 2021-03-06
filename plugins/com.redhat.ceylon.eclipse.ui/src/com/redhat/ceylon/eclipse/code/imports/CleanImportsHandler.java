package com.redhat.ceylon.eclipse.code.imports;

import static com.redhat.ceylon.eclipse.code.editor.Util.getCurrentEditor;
import static org.eclipse.core.resources.ResourcesPlugin.getWorkspace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.window.Window;
import org.eclipse.ltk.core.refactoring.PerformChangeOperation;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;

import com.redhat.ceylon.compiler.typechecker.analyzer.AnalysisError;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.tree.Message;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.ImportList;
import com.redhat.ceylon.eclipse.code.editor.CeylonEditor;
import com.redhat.ceylon.eclipse.code.parse.CeylonParseController;

public class CleanImportsHandler extends AbstractHandler {
    
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        CeylonEditor editor = (CeylonEditor) getCurrentEditor();
        Tree.CompilationUnit cu = editor.getParseController().getRootNode();
        if (cu!=null) {
            String imports = imports(cu);
            if (imports!=null) {
                TextFileChange tfc = new TextFileChange("Clean Imports", 
                        ((IFileEditorInput) editor.getEditorInput()).getFile());
                tfc.setEdit(new MultiTextEdit());
                ImportList il = cu.getImportList();
                int start;
                int length;
                String extra;
                if (il==null || il.getImports().isEmpty()) {
                	start=0;
                	length=0;
                	extra="\n";
                }
                else {
                	start = il.getStartIndex();
                	length = il.getStopIndex()-il.getStartIndex()+1;
                	extra="";
                }
//                if (!imports.trim().isEmpty()) {
                	tfc.addEdit(new ReplaceEdit(start, length, imports+extra));
                	tfc.initializeValidationData(null);
                	try {
                		getWorkspace().run(new PerformChangeOperation(tfc), 
                				new NullProgressMonitor());
                	}
                	catch (CoreException ce) {
                		throw new ExecutionException("Error cleaning imports", ce);
                	}
//                }
            }
        }
        return null;
    }

    public static String imports(Node node, ImportList til) {
    	final List<Declaration> unused = new ArrayList<Declaration>();
        DetectUnusedImportsVisitor duiv = new DetectUnusedImportsVisitor(unused);
        til.visit(duiv);
        node.visit(duiv);
        return reorganizeImports(til, unused, Collections.<Declaration>emptyList());
    }
    
    private String imports(final Tree.CompilationUnit cu) {
		final List<Declaration> proposals = new ArrayList<Declaration>();
		final List<Declaration> unused = new ArrayList<Declaration>();
    	new ImportProposalsVisitor(cu, proposals, this).visit(cu);
        new DetectUnusedImportsVisitor(unused).visit(cu);
        return reorganizeImports(cu.getImportList(), unused, proposals);
    }
    
    public static String imports(List<Declaration> proposed) {
    	return reorganizeImports(null, Collections.<Declaration>emptyList(), proposed);
    }
    
    private static String reorganizeImports(ImportList til, List<Declaration> unused, 
    		List<Declaration> proposed) {
        Map<String,List<Tree.Import>> packages = new TreeMap<String,List<Tree.Import>>();
        if (til!=null) {
        	for (Tree.Import i: til.getImports()) {
        		List<Tree.Import> is = packages.get(packageName(i));
        		if (is==null) {
        			is = new ArrayList<Tree.Import>();
            		packages.put(packageName(i), is);
        		}
        		is.add(i);
        	}
        }
        for (Declaration d: proposed) {
        	String pn = d.getUnit().getPackage().getNameAsString();
        	// TODO shouldn't we filter ceylon.language packages ?
        	if (!packages.containsKey(pn)) {
        		packages.put(pn, Collections.<Tree.Import>emptyList());
        	}
        }
        
        StringBuilder builder = new StringBuilder();
        String lastToplevel=null;
        for (Map.Entry<String, List<Tree.Import>> pack: packages.entrySet()) {
        	String packageName = pack.getKey();
        	List<Tree.Import> imports = pack.getValue();
        	boolean hasWildcard = hasWildcard(imports);
        	List<Tree.ImportMemberOrType> list = getUsedImportElements(imports, unused, hasWildcard);
        	if (hasWildcard || !list.isEmpty() || 
        			imports.isEmpty()) { //in this last case there is no existing import, but imports are proposed
        		lastToplevel = appendBreakIfNecessary(lastToplevel, packageName, builder);
        		builder.append("import ").append(packageName).append(" { ");
        		appendImportElements(packageName, list, unused, proposed, hasWildcard, builder);
        		builder.append(" }\n");
        	}
        }
        if (builder.length()!=0) {
        	builder.setLength(builder.length()-1);
        }
        return builder.toString();
    }

	private static boolean hasWildcard(List<Tree.Import> imports) {
		boolean hasWildcard = false;
		for (Tree.Import i: imports) {
			hasWildcard = hasWildcard || 
					i!=null && i.getImportMemberOrTypeList()
							.getImportWildcard()!=null;
		}
		return hasWildcard;
	}

	private static String appendBreakIfNecessary(String lastToplevel,
			String currentPackage, StringBuilder builder) {
		int di = currentPackage.indexOf('.');
		String topLevel = di<0 ? currentPackage:currentPackage.substring(0,di);
		if (lastToplevel!=null && !topLevel.equals(lastToplevel)) {
			builder.append("\n");
		}
		return topLevel;
	}

	private static void appendImportElements(String packageName,
			List<Tree.ImportMemberOrType> elements, List<Declaration> unused, 
			List<Declaration> proposed, boolean hasWildcard, 
			StringBuilder builder) {
		for (Tree.ImportMemberOrType i: elements) {
			if (i.getDeclarationModel()!=null && 
					i.getIdentifier().getErrors().isEmpty() &&
					i.getErrors().isEmpty()) {
				if ( !i.getImportModel().getAlias()
						.equals(i.getDeclarationModel().getName()) ) {
					builder.append(i.getImportModel().getAlias())
							.append("=");
				}
				builder.append(i.getDeclarationModel().getName());
				appendNestedImportElements(i, unused, builder);
				builder.append(", ");
			}
		}
		for (Declaration d: proposed) {
			if (d.getUnit().getPackage().getNameAsString()
					.equals(packageName)) {
				builder.append(d.getName()).append(", ");
			}
		}
		if (hasWildcard) {
			builder.append("...");
		}
		else {
			builder.setLength(builder.length()-2);
		}
	}

	private static void appendNestedImportElements(Tree.ImportMemberOrType imt,
			List<Declaration> unused, StringBuilder builder) {
		if (imt.getImportMemberOrTypeList()!=null) {
			builder.append(" { ");
			boolean found=false;
			for (Tree.ImportMemberOrType nimt: imt.getImportMemberOrTypeList()
					.getImportMemberOrTypes()) {
				if (nimt.getDeclarationModel()!=null && 
						nimt.getIdentifier().getErrors().isEmpty() &&
						nimt.getErrors().isEmpty()) {
					if (!unused.contains(nimt.getDeclarationModel())) {
						found=true;
						if (!nimt.getImportModel().getAlias()
								.equals(nimt.getDeclarationModel().getName())) {
							builder.append(nimt.getImportModel().getAlias())
									.append("=");
						}
						builder.append(nimt.getDeclarationModel().getName())
								.append(", ");
					}
				}
			}
			if (found) builder.setLength(builder.length()-2);
			builder.append(" }");
			if (!found) builder.setLength(builder.length()-5);
		}
	}

	private static boolean hasRealErrors(Node node) {
	    for (Message m: node.getErrors()) {
	        if (m instanceof AnalysisError) {
	            return true;
	        }
	    }
	    return false;
	}
	
	private static List<Tree.ImportMemberOrType> getUsedImportElements(
			List<Tree.Import> imports, List<Declaration> unused, boolean hasWildcard) {
		List<Tree.ImportMemberOrType> list = new ArrayList<Tree.ImportMemberOrType>();
		for (Tree.Import ti: imports) {
			for (Tree.ImportMemberOrType imt: ti.getImportMemberOrTypeList()
					.getImportMemberOrTypes()) {
				Declaration dm = imt.getDeclarationModel();
                if (dm!=null && 
				        !hasRealErrors(imt.getIdentifier()) && 
				        !hasRealErrors(imt)) {
					if (unused.contains(dm)) {
						if (imt.getImportMemberOrTypeList()!=null) {
							for (Tree.ImportMemberOrType nimt: imt.getImportMemberOrTypeList()
									.getImportMemberOrTypes()) {
								Declaration ndm = nimt.getDeclarationModel();
                                if (ndm!=null && 
                                        !hasRealErrors(nimt.getIdentifier()) && 
										!hasRealErrors(nimt)) {
									if (!unused.contains(ndm)) {
										list.add(imt);
										break;
									}
								}
							}
						}
					} 
					else {
						if (!hasWildcard || imt.getAlias()!=null || 
								imt.getImportMemberOrTypeList()!=null) {
							list.add(imt);
						}
					}
				}
			}
		}
		return list;
	}
    
    private static String packageName(Tree.Import i) {
        return i.getImportMemberOrTypeList()
                .getImportList().getImportedScope()
                .getQualifiedNameString();
    }
    
    @Override
    public boolean isEnabled() {
        IEditorPart editor = getCurrentEditor();
        if (super.isEnabled() && 
                editor instanceof CeylonEditor &&
                editor.getEditorInput() instanceof IFileEditorInput) {
            CeylonParseController cpc = ((CeylonEditor) editor).getParseController();
            return cpc==null || cpc.getRootNode()==null ? false : true;
                //!cpc.getRootNode().getImportList().getImports().isEmpty();
        }
        return false;
    }
    
	public Declaration select(List<Declaration> proposals) {
		CeylonEditor editor = (CeylonEditor) getCurrentEditor();
		ImportSelectionDialog fid = new ImportSelectionDialog(editor.getSite().getShell(),
				proposals);
		if (fid.open() == Window.OK) {
			return (Declaration) fid.getFirstResult();
		}
		return null;
	}

}
