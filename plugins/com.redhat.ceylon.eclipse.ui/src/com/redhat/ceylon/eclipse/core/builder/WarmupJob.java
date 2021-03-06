package com.redhat.ceylon.eclipse.core.builder;

import static java.lang.Math.max;

import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

import com.redhat.ceylon.compiler.typechecker.TypeChecker;
import com.redhat.ceylon.compiler.typechecker.model.Module;
import com.redhat.ceylon.compiler.typechecker.model.Package;

final class WarmupJob extends Job {
	private final TypeChecker typeChecker;

	WarmupJob(String name, TypeChecker typeChecker) {
		super("Warming up completion processor for " + name);
		this.typeChecker = typeChecker;
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		monitor.beginTask("Warming up completion processor", 100000);
		Set<Module> modules = typeChecker.getPhasedUnits().getModuleManager()
				.getContext().getModules().getListOfModules();
		monitor.worked(10000);
		for (Module m: modules) {
			List<Package> packages = m.getAllPackages();
			for (Package p: packages) {
				p.getMembers();
			}
			monitor.worked(90000/max(modules.size(),1));
			if (monitor.isCanceled()) {
				return Status.CANCEL_STATUS;
			}
		}
		monitor.done();
		return Status.OK_STATUS;
	}
}