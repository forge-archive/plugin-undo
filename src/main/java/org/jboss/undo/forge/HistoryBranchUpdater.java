/*
 * Copyright 2012 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Eclipse Public License Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.undo.forge;

import java.util.Arrays;
import java.util.List;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.jboss.forge.jgit.api.Git;
import org.jboss.forge.jgit.api.errors.GitAPIException;
import org.jboss.forge.jgit.errors.NoWorkTreeException;
import org.jboss.forge.jgit.revwalk.RevCommit;
import org.jboss.forge.parser.java.util.Strings;
import org.jboss.forge.project.Project;
import org.jboss.forge.shell.Shell;
import org.jboss.forge.shell.events.CommandExecuted;

@Singleton
public class HistoryBranchUpdater
{
   private static final List<String> IGNORED_COMMANDS = Arrays.asList("new-project", "cd", "clear", "wait", "undo");

   @Inject
   private BeanManager beanManager;

   @Inject
   private Shell shell;

   public void updateHistoryBranch(@Observes final CommandExecuted command)
   {
      if (command.getStatus() != CommandExecuted.Status.SUCCESS)
         return;

      if (!UndoFacet.isReady)
         return;

      Project project = shell.getCurrentProject();

      if (!validRequirements(project, command))
         return;

      try
      {
         Git repo = project.getFacet(UndoFacet.class).getGitObject();
         String undoBranch = project.getFacet(UndoFacet.class).getUndoBranchName();

         if (anythingChanged(repo))
         {
            project.getFacet(UndoFacet.class).checkAndUpdateRepositoryForNewCommits();

            String previousBranch = repo.getRepository().getBranch();

            repo.add().addFilepattern(".").call();
            repo.stashCreate().call();
            repo.checkout().setName(undoBranch).call();
            repo.stashApply().call();
            RevCommit commitWithChangeset = repo.commit().setMessage(prepareHistoryBranchCommitMsg(command)).call();
            repo.notesAdd().setObjectId(commitWithChangeset).setMessage(UndoFacet.DEFAULT_NOTE).call();
            repo.checkout().setName(previousBranch).call();
            repo.stashApply().call();
            repo.stashDrop().call();
            repo.add().addFilepattern(".").call();

            project.getFacet(UndoFacet.class).increaseHistoryBranchSizeByOne();
         }
      }
      catch (Exception e)
      {
         throw new RuntimeException("Failed to add changes onto history branch: [" + e.getMessage() + "]", e.getCause());
      }
   }

   private String prepareHistoryBranchCommitMsg(final CommandExecuted command)
   {
      String cmdParentName = command.getCommand().getParent() != null ? command.getCommand().getParent().getName() : "";
      String cmdName = command.getCommand().getName();
      String enquotedCommand = Strings.enquote(
               Strings.areEqual(cmdParentName, cmdName) ? cmdName : cmdParentName + " " + cmdName
               );
      return UndoFacet.UNDO_STORE_COMMIT_MSG_PREFIX + enquotedCommand + " command";
   }

   private boolean validRequirements(Project project, final CommandExecuted command)
   {
      if (project == null)
         return false;

      if (IGNORED_COMMANDS.contains(command.getCommand().getName()))
         return false;

      if (Strings.areEqual(command.getCommand().getParent().getName(), "undo"))
         return false;

      if (Strings.areEqual(command.getCommand().getName(), "setup")
               && Strings.areEqual(command.getCommand().getParent().getName(), "git"))
         return false;

      if (!project.hasFacet(UndoFacet.class))
         return false;

      return true;
   }

   private boolean anythingChanged(Git repo) throws NoWorkTreeException, GitAPIException
   {
      return !repo.status().call().isClean();
   }
}
