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
import java.util.ArrayList;
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
   public static final String INITIAL_COMMIT_MSG = "initial commit";
   public static final String UNDO_INSTALL_COMMIT_MSG = "added forge undo-plugin support";

   @Inject
   Configuration config;

   @Override
   public boolean install()
   {
      try
      {
         Git git = GitUtils.git(project.getProjectRoot());

         // verify repository contains at least 1 branch.
         // create a master branch with .gitingore otherwise.
         List<Ref> branches = GitUtils.getLocalBranches(git);
         if (branches != null && branches.size() == 0)
         {
            FileResource<?> file = project.getProjectRoot().getChild(".gitignore").reify(FileResource.class);
            file.createNewFile();
            GitUtils.add(git, ".gitignore");
            GitUtils.commit(git, INITIAL_COMMIT_MSG);
         }

         String master = GitUtils.getCurrentBranchName(git);

         String branchName = getUndoBranchName();
         GitUtils.createBranch(git, branchName);
         GitUtils.switchBranch(git, branchName);

         FileResource<?> dotUndoPlugin = project.getProjectRoot().getChild(".undo-plugin").reify(FileResource.class);
         dotUndoPlugin.createNewFile();
         GitUtils.add(git, ".undo-plugin");
         GitUtils.commit(git, UNDO_INSTALL_COMMIT_MSG);

         GitUtils.switchBranch(git, master);

         return true;
      }
      catch (Exception e)
      {
         e.printStackTrace();
      }

      return false;
   }

   @Override
   public boolean isInstalled()
   {
      try
      {
         Git git = GitUtils.git(project.getProjectRoot());
         for (Ref branch : GitUtils.getLocalBranches(git))
         {
            // branch.getName() contains "refs/heads/" prefix by default
            if (branch.getName().endsWith(getUndoBranchName()))
               return true;
         }
      }
      catch (IOException e)
      {
         e.printStackTrace();
      }
      catch (GitAPIException e)
      {
         e.printStackTrace();
      }

      return false;
   }

   public Iterable<RevCommit> getStoredCommits()
   {
      Iterable<RevCommit> result = new ArrayList<RevCommit>();

      try
      {
         Git repo = GitUtils.git(project.getProjectRoot());
         String branchName = getUndoBranchName();

         result = GitUtils.getLogForBranch(repo, branchName);
      }
      catch (IOException e)
      {
         e.printStackTrace();
      }
      catch (GitAPIException e)
      {
         e.printStackTrace();
      }

      return result;
   }

   public boolean undoLastChange()
   {
      boolean success = false;
      try
      {
         Git repo = GitUtils.git(project.getProjectRoot());
         String oldBranch = GitUtils.getCurrentBranchName(repo);

         CherryPickResult cherryPickResult = GitUtils.cherryPickNoMerge(repo, getUndoBranchRef());
         if (cherryPickResult == null)
         {
            return false;
         }
         if (cherryPickResult.getStatus() != CherryPickStatus.OK)
         {
            // TODO: replace with something nicer
            throw new RuntimeException("GitUtils.cherryPickNoMerge failed");
         }

         GitUtils.switchBranch(repo, getUndoBranchName());
         GitUtils.resetHard(repo, "HEAD^1");
         GitUtils.switchBranch(repo, oldBranch);
         success = true;
      }
      catch (IOException e)
      {
         e.printStackTrace();
      }
      catch (GitAPIException e)
      {
         e.printStackTrace();
      }

      return success;
   }

   // helper methods
   public String getUndoBranchName()
   {
      return config.getString(HISTORY_BRANCH_CONFIG_KEY, DEFAULT_HISTORY_BRANCH_NAME);
   }

   public Ref getUndoBranchRef() throws IOException, RefAlreadyExistsException, RefNotFoundException,
            InvalidRefNameException, CheckoutConflictException, GitAPIException
   {
      Git repo = GitUtils.git(project.getProjectRoot());

      Ref result = null;
      String oldBranch = GitUtils.getCurrentBranchName(repo);
      result = GitUtils.switchBranch(repo, getUndoBranchName());
      GitUtils.switchBranch(repo, oldBranch);

      if (result == null)
         throw new RuntimeException("Could not get the Ref of the history branch");

      return result;
   }
}
