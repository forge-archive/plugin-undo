/*
 * Copyright (C) 2011, GitHub Inc.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.jboss.forge.jgit.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jboss.forge.jgit.api.CloneCommand;
import org.jboss.forge.jgit.api.Git;
import org.jboss.forge.jgit.api.MergeCommand;
import org.jboss.forge.jgit.api.RebaseCommand;
import org.jboss.forge.jgit.api.SubmoduleUpdateCommand;
import org.jboss.forge.jgit.api.TransportCommand;
import org.jboss.forge.jgit.api.errors.CheckoutConflictException;
import org.jboss.forge.jgit.api.errors.ConcurrentRefUpdateException;
import org.jboss.forge.jgit.api.errors.GitAPIException;
import org.jboss.forge.jgit.api.errors.InvalidConfigurationException;
import org.jboss.forge.jgit.api.errors.InvalidMergeHeadsException;
import org.jboss.forge.jgit.api.errors.JGitInternalException;
import org.jboss.forge.jgit.api.errors.NoHeadException;
import org.jboss.forge.jgit.api.errors.NoMessageException;
import org.jboss.forge.jgit.api.errors.RefNotFoundException;
import org.jboss.forge.jgit.api.errors.WrongRepositoryStateException;
import org.jboss.forge.jgit.dircache.DirCacheCheckout;
import org.jboss.forge.jgit.errors.ConfigInvalidException;
import org.jboss.forge.jgit.lib.ConfigConstants;
import org.jboss.forge.jgit.lib.Constants;
import org.jboss.forge.jgit.lib.NullProgressMonitor;
import org.jboss.forge.jgit.lib.ProgressMonitor;
import org.jboss.forge.jgit.lib.RefUpdate;
import org.jboss.forge.jgit.lib.Repository;
import org.jboss.forge.jgit.revwalk.RevCommit;
import org.jboss.forge.jgit.revwalk.RevWalk;
import org.jboss.forge.jgit.submodule.SubmoduleWalk;
import org.jboss.forge.jgit.treewalk.filter.PathFilterGroup;

/**
 * A class used to execute a submodule update command.
 *
 * @see <a
 *      href="http://www.kernel.org/pub/software/scm/git/docs/git-submodule.html"
 *      >Git documentation about submodules</a>
 */
public class SubmoduleUpdateCommand extends
		TransportCommand<SubmoduleUpdateCommand, Collection<String>> {

	private ProgressMonitor monitor;

	private final Collection<String> paths;

	/**
	 * @param repo
	 */
	public SubmoduleUpdateCommand(final Repository repo) {
		super(repo);
		paths = new ArrayList<String>();
	}

	/**
	 * The progress monitor associated with the clone operation. By default,
	 * this is set to <code>NullProgressMonitor</code>
	 *
	 * @see NullProgressMonitor
	 * @param monitor
	 * @return this command
	 */
	public SubmoduleUpdateCommand setProgressMonitor(
			final ProgressMonitor monitor) {
		this.monitor = monitor;
		return this;
	}

	/**
	 * Add repository-relative submodule path to initialize
	 *
	 * @param path
	 * @return this command
	 */
	public SubmoduleUpdateCommand addPath(final String path) {
		paths.add(path);
		return this;
	}

	/**
	 * Execute the SubmoduleUpdateCommand command.
	 *
	 * @return a collection of updated submodule paths
	 * @throws ConcurrentRefUpdateException
	 * @throws CheckoutConflictException
	 * @throws InvalidMergeHeadsException
	 * @throws InvalidConfigurationException
	 * @throws NoHeadException
	 * @throws NoMessageException
	 * @throws RefNotFoundException
	 * @throws WrongRepositoryStateException
	 * @throws GitAPIException
	 */
	public Collection<String> call() throws InvalidConfigurationException,
			NoHeadException, ConcurrentRefUpdateException,
			CheckoutConflictException, InvalidMergeHeadsException,
			WrongRepositoryStateException, NoMessageException, NoHeadException,
			RefNotFoundException, GitAPIException {
		checkCallable();

		try {
			SubmoduleWalk generator = SubmoduleWalk.forIndex(repo);
			if (!paths.isEmpty())
				generator.setFilter(PathFilterGroup.createFromStrings(paths));
			List<String> updated = new ArrayList<String>();
			while (generator.next()) {
				// Skip submodules not registered in .gitmodules file
				if (generator.getModulesPath() == null)
					continue;
				// Skip submodules not registered in parent repository's config
				String url = generator.getConfigUrl();
				if (url == null)
					continue;

				Repository submoduleRepo = generator.getRepository();
				// Clone repository is not present
				if (submoduleRepo == null) {
					CloneCommand clone = Git.cloneRepository();
					configure(clone);
					clone.setURI(url);
					clone.setDirectory(generator.getDirectory());
					if (monitor != null)
						clone.setProgressMonitor(monitor);
					submoduleRepo = clone.call().getRepository();
				}

				try {
					RevWalk walk = new RevWalk(submoduleRepo);
					RevCommit commit = walk
							.parseCommit(generator.getObjectId());

					String update = generator.getConfigUpdate();
					if (ConfigConstants.CONFIG_KEY_MERGE.equals(update)) {
						MergeCommand merge = new MergeCommand(submoduleRepo);
						merge.include(commit);
						merge.call();
					} else if (ConfigConstants.CONFIG_KEY_REBASE.equals(update)) {
						RebaseCommand rebase = new RebaseCommand(submoduleRepo);
						rebase.setUpstream(commit);
						rebase.call();
					} else {
						// Checkout commit referenced in parent repository's
						// index as a detached HEAD
						DirCacheCheckout co = new DirCacheCheckout(
								submoduleRepo, submoduleRepo.lockDirCache(),
								commit.getTree());
						co.setFailOnConflict(true);
						co.checkout();
						RefUpdate refUpdate = submoduleRepo.updateRef(
								Constants.HEAD, true);
						refUpdate.setNewObjectId(commit);
						refUpdate.forceUpdate();
					}
				} finally {
					submoduleRepo.close();
				}
				updated.add(generator.getPath());
			}
			return updated;
		} catch (IOException e) {
			throw new JGitInternalException(e.getMessage(), e);
		} catch (ConfigInvalidException e) {
			throw new InvalidConfigurationException(e.getMessage(), e);
		}
	}
}
