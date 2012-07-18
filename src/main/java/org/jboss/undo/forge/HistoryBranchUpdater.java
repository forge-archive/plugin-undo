package org.jboss.undo.forge;

import java.io.IOException;

import javax.enterprise.event.Observes;
import javax.enterprise.event.TransactionPhase;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.jboss.forge.git.GitUtils;
import org.jboss.forge.parser.java.util.Strings;
import org.jboss.forge.project.Project;
import org.jboss.forge.shell.events.CommandExecuted;

@Singleton
public class HistoryBranchUpdater
{
   @Inject
   private Project project;

   public void updateHistoryBranch(@Observes(during = TransactionPhase.AFTER_COMPLETION) final CommandExecuted command)
   {
      if (project == null)
         return;

      if (Strings.areEqual(command.getCommand().getName(), "new-project"))
         return;

      if (Strings.areEqual(command.getCommand().getName(), "setup")
               && Strings.areEqual(command.getCommand().getParent().getName(), "undo"))
         return;

      if (Strings.areEqual(command.getCommand().getName(), "setup")
               && Strings.areEqual(command.getCommand().getParent().getName(), "git"))
         return;

      if (!project.hasFacet(UndoFacet.class))
         return;

      try
      {
         Git repo = GitUtils.git(project.getProjectRoot());
         String oldBranch = GitUtils.getCurrentBranchName(repo);

         GitUtils.addAll(repo);
         GitUtils.stashCreate(repo);

         String undoBranchName = project.getFacet(UndoFacet.class).getUndoBranchName();
         GitUtils.switchBranch(repo, undoBranchName);
         GitUtils.stashApply(repo);

         GitUtils.commitAll(repo,
                  "history-branch: changes introduced by the " + Strings.enquote(command.getCommand().getName()));

         GitUtils.switchBranch(repo, oldBranch);
         GitUtils.stashApply(repo);
         GitUtils.stashDrop(repo);
      }
      catch (IOException e)
      {
         e.printStackTrace();
      }
      catch (GitAPIException e)
      {
         e.printStackTrace();
      }
   }
}
