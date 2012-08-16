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
import javax.inject.Singleton;

import org.apache.commons.collections.CollectionUtils;
import org.jboss.forge.bus.spi.EventBusGroomer;
import org.jboss.forge.jgit.api.Git;
import org.jboss.forge.jgit.api.Status;
import org.jboss.forge.jgit.api.errors.GitAPIException;
import org.jboss.forge.jgit.errors.NoWorkTreeException;
import org.jboss.forge.jgit.revwalk.RevCommit;
import org.jboss.forge.parser.java.util.Strings;
import org.jboss.forge.project.Project;
import org.jboss.forge.resources.events.ResourceEvent;
import org.jboss.forge.resources.events.TempResourceCreated;
import org.jboss.forge.shell.Shell;
import org.jboss.forge.shell.events.CommandExecuted;

@Singleton
public class HistoryBranchUpdater implements EventBusGroomer
{
   private static final List<String> IGNORED_COMMANDS = Arrays.asList("new-project", "undo");

   private Status beforeCommandExecution = null;
   private String[] commandDetails = new String[] { "", "" };

   public void getGitStatusBefore(@Observes final CommandExecuted command)
   {
      if (command.getStatus() != CommandExecuted.Status.SUCCESS)
         return;

      if (!UndoFacet.isReady)
         return;

      BeanManager manager = BeanManagerExtension.getBeanManager();
      Shell shell = (Shell) manager.resolve(manager.getBeans(Shell.class));
      Project project = shell.getCurrentProject();

      if (!validRequirements(project, command))
         return;

      try
      {
         Git repo = project.getFacet(UndoFacet.class).getGitObject();
         beforeCommandExecution = repo.status().call();

         commandDetails[0] = command.getCommand().getParent() != null ? command.getCommand().getParent().getName() : "";
         commandDetails[1] = command.getCommand().getName();
      }
      catch (Exception e)
      {
         throw new RuntimeException("Failed to check if repository changed: [" + e.getMessage() + "]", e.getCause());
      }
   }

   @Override
   public List<Object> groom(List<Object> events)
   {
      boolean isExecuted = false;

      for (Object event : events)
      {
         if (!isExecuted && event instanceof ResourceEvent && !(event instanceof TempResourceCreated))
         {
            updateHistoryBranch();
            isExecuted = true;
         }
      }

      beforeCommandExecution = null;
      commandDetails = new String[] { "", "" };

      return events;
   }

   private void updateHistoryBranch()
   {
      BeanManager manager = BeanManagerExtension.getBeanManager();
      Shell shell = (Shell) manager.resolve(manager.getBeans(Shell.class));
      Project project = shell.getCurrentProject();

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
            RevCommit commitWithChangeset = repo.commit().setMessage(prepareHistoryBranchCommitMsg()).call();
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

   private String prepareHistoryBranchCommitMsg()
   {
      String cmdParentName = commandDetails[0];
      String cmdName = commandDetails[1];
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
      if (beforeCommandExecution == null)
         return false;

      Status afterCommandExecution = repo.status().call();
      return !haveEqualStatus(beforeCommandExecution, afterCommandExecution);
   }

   private boolean haveEqualStatus(Status beforeCommandExecution, Status afterCommandExecution)
   {
      if (beforeCommandExecution.isClean() != afterCommandExecution.isClean())
         return false;

      if (!CollectionUtils.isEqualCollection(beforeCommandExecution.getAdded(), afterCommandExecution.getAdded()))
      {
         return false;
      }
      if (!CollectionUtils.isEqualCollection(beforeCommandExecution.getChanged(), afterCommandExecution.getChanged()))
      {
         return false;
      }
      if (!CollectionUtils.isEqualCollection(beforeCommandExecution.getConflicting(),
               afterCommandExecution.getConflicting()))
      {
         return false;
      }
      if (!CollectionUtils.isEqualCollection(beforeCommandExecution.getMissing(), afterCommandExecution.getMissing()))
      {
         return false;
      }
      if (!CollectionUtils.isEqualCollection(beforeCommandExecution.getModified(), afterCommandExecution.getModified()))
      {
         return false;
      }
      if (!CollectionUtils.isEqualCollection(beforeCommandExecution.getRemoved(), afterCommandExecution.getRemoved()))
      {
         return false;
      }
      if (!CollectionUtils.isEqualCollection(beforeCommandExecution.getUntracked(),
               afterCommandExecution.getUntracked()))
      {
         return false;
      }

      return true;
   }
}
