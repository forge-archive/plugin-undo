package org.jboss.undo.forge;

import java.io.IOException;
import java.lang.annotation.Annotation;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.jboss.forge.git.GitUtils;
import org.jboss.forge.parser.java.util.Strings;
import org.jboss.forge.project.Project;
import org.jboss.forge.shell.Shell;
import org.jboss.forge.shell.events.CommandExecuted;
import org.jboss.forge.shell.project.ProjectScoped;
import org.jboss.weld.context.ContextNotActiveException;

public class HistoryBranchUpdater
{
   @Inject
   private BeanManager beanManager;

   @Inject
   private Shell shell;

   public void updateHistoryBranch(@Observes final CommandExecuted command)
   {
      if (!isContextActive(ProjectScoped.class))
         return;

      Project project = shell.getCurrentProject();

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

   private boolean isContextActive(Class<? extends Annotation> scope)
   {
      try
      {
         beanManager.getContext(scope);
      }
      catch (ContextNotActiveException e)
      {
         return false;
      }
      return true;
   }

}
