package org.jboss.undo.forge;

import java.io.IOException;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.jboss.forge.git.GitUtils;
import org.jboss.forge.parser.java.util.Strings;
import org.jboss.forge.project.Project;
import org.jboss.forge.shell.Shell;
import org.jboss.forge.shell.events.CommandExecuted;
import org.jboss.forge.shell.project.ProjectScoped;
import org.jboss.weld.context.ContextNotActiveException;

@Singleton
public class HistoryBranchUpdater
{
   @Inject
   private BeanManager beanManager;

   @Inject
   private Shell shell;

   public void updateHistoryBranch(@Observes final CommandExecuted command)
   {
      if (!projectScopedIsAvailable())
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
            createAndApplyStash(repo);
            createTempCommit(repo);
            addStashedChangesOntoHistoryBranch(repo, undoBranch, command);
            destroyTempCommit(repo);
            applyAndDestroyStash(repo);
         }
      }
      catch (Exception e)
      {
         throw new RuntimeException("Failed to add changes onto history branch: [" + e.getMessage() + "]", e.getCause());
      }
   }

   private void createAndApplyStash(Git repo) throws GitAPIException
   {
      GitUtils.addAll(repo); // to add untracked files
      GitUtils.stashCreate(repo);
      GitUtils.stashApply(repo);
   }

   private void createTempCommit(Git repo) throws GitAPIException
   {
      GitUtils.addAll(repo);
      GitUtils.commit(repo, "tmp-commit");
   }

   private void addStashedChangesOntoHistoryBranch(Git repo, String undoBranch, final CommandExecuted command)
            throws IOException, GitAPIException
   {
      String oldBranch = GitUtils.getCurrentBranchName(repo);

      String cmdParentName = command.getCommand().getParent() != null ? command.getCommand().getParent().getName() : "";
      String cmdName = command.getCommand().getName();
      String enquotedCommand = Strings.enquote(
               Strings.areEqual(cmdParentName, cmdName) ? cmdName : cmdParentName + " " + cmdName
               );

      GitUtils.switchToBranch(repo, undoBranch);
      GitUtils.stashApply(repo);
      GitUtils.addAll(repo);
      GitUtils.commit(
               repo,
               "history-branch: changes introduced by the " + enquotedCommand + " command");

      GitUtils.switchToBranch(repo, oldBranch);
   }

   private void destroyTempCommit(Git repo) throws GitAPIException
   {
      GitUtils.resetMixed(repo, "HEAD^1");
   }

   private void applyAndDestroyStash(Git repo) throws GitAPIException
   {
      GitUtils.stashApply(repo);
      GitUtils.stashDrop(repo);
   }

   private boolean projectScopedIsAvailable()
   {
      try
      {
         beanManager.getContext(ProjectScoped.class);
      }
      catch (ContextNotActiveException e)
      {
         return false;
      }
      return true;
   }

   private boolean validRequirements(Project project, final CommandExecuted command)
   {
      if (project == null)
         return false;

      if (Strings.areEqual(command.getCommand().getName(), "new-project"))
         return false;

      if (Strings.areEqual(command.getCommand().getName(), "cd"))
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
