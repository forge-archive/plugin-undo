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

import javax.enterprise.event.Event;
import javax.inject.Inject;

import org.eclipse.jgit.revwalk.RevCommit;
import org.jboss.forge.env.Configuration;
import org.jboss.forge.parser.java.util.Strings;
import org.jboss.forge.project.Project;
import org.jboss.forge.project.facets.events.InstallFacets;
import org.jboss.forge.shell.ShellMessages;
import org.jboss.forge.shell.plugins.Alias;
import org.jboss.forge.shell.plugins.Command;
import org.jboss.forge.shell.plugins.DefaultCommand;
import org.jboss.forge.shell.plugins.Help;
import org.jboss.forge.shell.plugins.Option;
import org.jboss.forge.shell.plugins.PipeOut;
import org.jboss.forge.shell.plugins.Plugin;
import org.jboss.forge.shell.plugins.RequiresFacet;
import org.jboss.forge.shell.plugins.SetupCommand;

/**
 * @author <a href="mailto:jevgeni.zelenkov@gmail.com">Jevgeni Zelenkov</a>
 * 
 */
@Alias("undo")
@Help("Provides a possibility to revert changes introduced by the forge commands")
@RequiresFacet(UndoFacet.class)
public class UndoPlugin implements Plugin
{
   private static final int GIT_HASH_ABBREV_SIZE = 8;

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

   @DefaultCommand(help = "list changes stored in the undo branch")
   // @Command("list")
   public void listCommand(PipeOut out) throws Exception
   {
      Iterable<RevCommit> commits = project.getFacet(UndoFacet.class).getStoredCommits();

      for (RevCommit commit : commits)
         out.println("@" + commit.getId().abbreviate(GIT_HASH_ABBREV_SIZE) + ": " + commit.getShortMessage());
   }

   @Command(value = "undo", help = "reverts the changes introduced by the last forge command")
   public void undoCommand(PipeOut out) throws Exception
   {
      boolean isReverted = project.getFacet(UndoFacet.class).undoLastChange();

      if (isReverted)
         ShellMessages.success(out, "latest forge command is reverted.");
      else
         ShellMessages.info(out, "nothing changed.");
   }
}
