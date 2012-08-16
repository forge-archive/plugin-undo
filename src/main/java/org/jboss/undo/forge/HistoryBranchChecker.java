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

import org.jboss.forge.jgit.api.Git;
import org.jboss.forge.jgit.api.Status;
import org.jboss.forge.parser.java.util.Strings;
import org.jboss.forge.project.Project;
import org.jboss.forge.shell.Shell;
import org.jboss.forge.shell.events.CommandExecuted;

public class HistoryBranchChecker
{
   private static Shell shell = null;

   private static Status beforeCommandExecution = null;

   private static String[] commandDetails = new String[] { "", "" };

   private static final List<String> IGNORED_COMMANDS = Arrays.asList("new-project", "undo");

   public void getGitStatusBefore(@Observes final CommandExecuted command, Shell shell)
   {
      if (HistoryBranchChecker.shell == null)
         HistoryBranchChecker.shell = shell;

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
         beforeCommandExecution = repo.status().call();

         commandDetails[0] = command.getCommand().getParent() != null ? command.getCommand().getParent().getName() : "";
         commandDetails[1] = command.getCommand().getName();
      }
      catch (Exception e)
      {
         throw new RuntimeException("Failed to check if repository changed: [" + e.getMessage() + "]", e.getCause());
      }
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

   public static Shell getShell()
   {
      return shell;
   }

   public static Status getBeforeCommandExecution()
   {
      return beforeCommandExecution;
   }

   public static String[] getCommandDetails()
   {
      return commandDetails;
   }
}
