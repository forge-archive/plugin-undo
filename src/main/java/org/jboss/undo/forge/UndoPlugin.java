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

import javax.enterprise.event.Event;
import javax.inject.Inject;

import org.jboss.forge.env.Configuration;
import org.jboss.forge.jgit.revwalk.RevCommit;
import org.jboss.forge.parser.java.util.Strings;
import org.jboss.forge.project.Project;
import org.jboss.forge.project.facets.events.InstallFacets;
import org.jboss.forge.shell.ShellMessages;
import org.jboss.forge.shell.plugins.Alias;
import org.jboss.forge.shell.plugins.Command;
import org.jboss.forge.shell.plugins.Help;
import org.jboss.forge.shell.plugins.Option;
import org.jboss.forge.shell.plugins.PipeOut;
import org.jboss.forge.shell.plugins.Plugin;
import org.jboss.forge.shell.plugins.RequiresFacet;
import org.jboss.forge.shell.plugins.RequiresProject;
import org.jboss.forge.shell.plugins.SetupCommand;

/**
 * @author <a href="mailto:jevgeni.zelenkov@gmail.com">Jevgeni Zelenkov</a>
 *
 */
@Alias("undo")
@Help("Provides a possibility to revert changes introduced by the forge commands")
@RequiresFacet(UndoFacet.class)
@RequiresProject
public class UndoPlugin implements Plugin
{
   private static final int GIT_HASH_ABBREV_SIZE = 7;

   @Inject
   private Configuration config;

   @Inject
   private Project project;

   @Inject
   private Event<InstallFacets> install;

   @SetupCommand()
   public void setup(@Option(name = "branchName") String branchName,
            PipeOut out)
   {
      if (Strings.isNullOrEmpty(branchName))
         config.addProperty(UndoFacet.HISTORY_BRANCH_CONFIG_KEY, UndoFacet.DEFAULT_HISTORY_BRANCH_NAME);
      else
         config.addProperty(UndoFacet.HISTORY_BRANCH_CONFIG_KEY, branchName);

      if (!project.hasFacet(UndoFacet.class))
         install.fire(new InstallFacets(UndoFacet.class));

      if (project.hasFacet(UndoFacet.class))
         ShellMessages.success(out, "Undo plugin is installed.");
   }

   @Command(value = "list", help = "list changes stored in the undo branch")
   public void listCommand(PipeOut out) throws Exception
   {
      Iterable<RevCommit> commits = project.getFacet(UndoFacet.class).getStoredCommits();

      for (RevCommit commit : commits)
         out.println(commit.getId().abbreviate(GIT_HASH_ABBREV_SIZE).name() + " " + commit.getShortMessage());
   }

   @Command(value = "restore", help = "reverts the changes introduced by the last forge command")
   public void undoCommand(PipeOut out) throws Exception
   {
      boolean isReverted = project.getFacet(UndoFacet.class).undoLastChange();

      if (isReverted)
         ShellMessages.success(out, "latest forge command is reverted.");
      else
         ShellMessages.info(out, "nothing changed.");
   }

   @Command(value = "reset", help = "remove all stored changesets in the history branch")
   public void resetCommand(PipeOut out) throws Exception
   {
      boolean isReset = project.getFacet(UndoFacet.class).reset();

      if (isReset)
         ShellMessages.success(out, "history branch was reset successfully.");
      else
         ShellMessages.info(out, "nothing changed.");
   }

}
