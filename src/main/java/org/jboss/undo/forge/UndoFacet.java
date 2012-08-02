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

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.MultipleParentsNotAllowedException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.jboss.forge.env.Configuration;
import org.jboss.forge.git.GitFacet;
import org.jboss.forge.git.GitUtils;
import org.jboss.forge.parser.java.util.Strings;
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
   public static boolean isReady = false;
   public int historyBranchSize = 0;
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

         UndoFacet.isReady = true;
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
      Git repo = null;
      String previousBranch = "";

      try
      {
         if (historyBranchSize > 0)
         {
            repo = getGitObject();
            previousBranch = GitUtils.getCurrentBranchName(repo);

            repo.add().addFilepattern(".").call();
            repo.commit().setMessage("FORGE PLUGIN-UNDO: preparing to undo a change").call();
            Ref historyBranchRef = repo.checkout().setName(getUndoBranchName()).call();
            RevCommit reverted = repo.revert().include(historyBranchRef).call();
            if(reverted == null)
               throw new RuntimeException("failed to revert a commit on a history branch");

            repo.checkout().setName(previousBranch).call();
            repo.cherryPick().include(reverted).call();

            repo.checkout().setName(getUndoBranchName()).call();
            repo.reset().setMode(ResetType.HARD).setRef("HEAD~2").call();
            repo.checkout().setName(previousBranch).call();

            historyBranchSize--;
            return true;
         }

         return false;
      }
      catch (MultipleParentsNotAllowedException e)
      {
         // revert of a merged commit failed. Roll back the changes introduced so far.
         try
         {
            repo.checkout().setName(previousBranch).call();
         }
         catch (Exception e2)
         {
            throw new RuntimeException(
                     "Failed during revert command (MultipleParentsNotAllowed). Then failed trying to rollback changes ["
                              + e.getMessage() + "]", e2.getCause());
         }

         return false;
      }
      catch (Exception e)
      {
         throw new RuntimeException("Failed to undo last change [" + e.getMessage() + "]", e.getCause());
      }
   }

   private void ensureGitRepositoryIsInitialized(Git repo) throws GitAPIException
   {
      List<Ref> branches = GitUtils.getLocalBranches(repo);
      if (branches != null && branches.size() == 0)
      {
         FileResource<?> file = project.getProjectRoot().getChild(".gitignore").reify(FileResource.class);
         file.createNewFile();
         GitUtils.add(repo, ".gitignore");
         GitUtils.commit(repo, INITIAL_COMMIT_MSG);
      }
   }

   private void commitAllToHaveCleanTree(Git repo) throws GitAPIException
   {
      GitUtils.addAll(repo);
      GitUtils.commit(repo, UNDO_INSTALL_COMMIT_MSG);
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
      return repo.getRepository().getRef(getUndoBranchName());
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
