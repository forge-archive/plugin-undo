/*
 * JBoss, by Red Hat.
 * Copyright 2010, Red Hat, Inc., and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.undo.forge;

import java.io.IOException;
import java.util.List;

import javax.inject.Inject;

import org.eclipse.jgit.api.CherryPickResult;
import org.eclipse.jgit.api.CherryPickResult.CherryPickStatus;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.jboss.forge.env.Configuration;
import org.jboss.forge.git.GitFacet;
import org.jboss.forge.git.GitUtils;
import org.jboss.forge.project.facets.BaseFacet;
import org.jboss.forge.resources.FileResource;
import org.jboss.forge.shell.plugins.Alias;
import org.jboss.forge.shell.plugins.Help;
import org.jboss.forge.shell.plugins.RequiresFacet;

/**
 * @author <a href="mailto:jevgeni.zelenkov@gmail.com">Jevgeni Zelenkov</a>
 * 
 */
@Alias("forge.plugin.undo")
@Help("Undo plugin facet")
@RequiresFacet(GitFacet.class)
public class UndoFacet extends BaseFacet
{
   public static final String DEFAULT_HISTORY_BRANCH_NAME = "forge-history";
   public static final String HISTORY_BRANCH_CONFIG_KEY = "forge-undo-branch";
   public static final String INITIAL_COMMIT_MSG = "repository initial commit";
   public static final String UNDO_INSTALL_COMMIT_MSG = "FORGE PLUGIN-UNDO: initial commit";
   private Git gitObject = null;

   @Inject
   Configuration config;

   @Override
   public boolean install()
   {
      try
      {
         Git git = getGitObject();

         ensureGitRepositoryIsInitialized(git);
         commitAllToHaveCleanTree(git);
         initializeHistoryBranch(git);

         return true;
      }
      catch (Exception e)
      {
         throw new RuntimeException("Failed to install the UndoFacet", e.getCause());
      }
   }

   @Override
   public boolean isInstalled()
   {
      try
      {
         Git git = getGitObject();
         for (Ref branch : GitUtils.getLocalBranches(git))
            if (Strings.areEqual(Repository.shortenRefName(branch.getName()), getUndoBranchName()))
               return true;

         return false;
      }
      catch (Exception e)
      {
         throw new RuntimeException("Failed to check if UndoFacet is installed", e.getCause());
      }
   }

   public Iterable<RevCommit> getStoredCommits()
   {
      try
      {
         return GitUtils.getLogForBranch(getGitObject(), getUndoBranchName());
      }
      catch (Exception e)
      {
         throw new RuntimeException("Failed to get a list of stored commits in the history branch", e.getCause());
      }
   }

   public boolean undoLastChange()
   {
      try
      {
         Git repo = getGitObject();
         // boolean isWorkingTreeClean = repo.status().call().isClean();

         // if (!isWorkingTreeClean)
         // {
         // // GitUtils.addAll(repo);
         // // GitUtils.stashCreate(repo);
         // createTempCommit(repo);
         // }

         GitUtils.addAll(repo);

         // TODO: Use inverted patch here instead of cherrypicking!
         // CherryPickResult cherryPickResult = repo.cherryPick().include(getUndoBranchRef()).call();
         // if (cherryPickResult.getStatus() != CherryPickStatus.OK)
         // throw new RuntimeException("UndoLastChange() failed. CherryPick returned a bad status");

         // CherryPickResult cherryPickResult = GitUtils.cherryPickNoMerge(repo, getUndoBranchRef());
         // if (cherryPickResult.getStatus() != CherryPickStatus.OK)
         // throw new RuntimeException("UndoLastChange() failed. CherryPickNoMerge returned a bad status");

         // if (!isWorkingTreeClean)
         // {
         // // GitUtils.stashApply(repo);
         // // GitUtils.stashDrop(repo);
         // destroyTempCommit(repo);
         // }

         createTempCommit(repo);

         removeLastCommitInHistoryBranch(repo);

         GitUtils.resetMixed(repo, "HEAD^1"); // tmp commit
         GitUtils.resetMixed(repo, "HEAD^1"); // cherry picked commit
         return true;
      }
      catch (IOException e)
      {
         throw new RuntimeException("Failed to undo last change [" + e.getMessage() + "]", e.getCause());
      }
   }

   private void removeLastCommitInHistoryBranch(Git repo) throws IOException, GitAPIException
   {
      String previousBranch = GitUtils.getCurrentBranchName(repo);
      GitUtils.switchToBranch(repo, getUndoBranchName());
      GitUtils.resetHard(repo, "HEAD^1");
      GitUtils.switchToBranch(repo, previousBranch);
   }

   private void createTempCommit(Git git) throws GitAPIException
   {
      GitUtils.addAll(git);
      GitUtils.commit(git, "tmp-commit");
   }

   private void ensureGitRepositoryIsInitialized(Git git) throws GitAPIException
   {
      List<Ref> branches = GitUtils.getLocalBranches(git);
      if (branches != null && branches.size() == 0)
      {
         FileResource<?> file = project.getProjectRoot().getChild(".gitignore").reify(FileResource.class);
         file.createNewFile();
         GitUtils.add(git, ".gitignore");
         GitUtils.commit(git, INITIAL_COMMIT_MSG);
      }
   }

   private void commitAllToHaveCleanTree(Git git) throws GitAPIException
   {
      GitUtils.addAll(git);
      GitUtils.commit(git, UNDO_INSTALL_COMMIT_MSG);
   }

   private void initializeHistoryBranch(Git git) throws IOException, RefAlreadyExistsException, RefNotFoundException,
            InvalidRefNameException, GitAPIException
   {
      GitUtils.createBranch(git, getUndoBranchName());
   }

   public String getUndoBranchName()
   {
      return config.getString(HISTORY_BRANCH_CONFIG_KEY, DEFAULT_HISTORY_BRANCH_NAME);
   }

   public Ref getUndoBranchRef() throws IOException, RefAlreadyExistsException, RefNotFoundException,
            InvalidRefNameException, CheckoutConflictException, GitAPIException
   {
      Git repo = getGitObject();
      return GitUtils.getRef(repo, getUndoBranchName());
   }

   public Git getGitObject() throws IOException
   {
      if (this.gitObject == null)
      {
         this.gitObject = GitUtils.git(project.getProjectRoot());
      }
      return gitObject;
   }
}
