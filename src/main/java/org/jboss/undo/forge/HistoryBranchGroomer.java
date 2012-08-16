package org.jboss.undo.forge;

import java.util.List;

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

public class HistoryBranchGroomer implements EventBusGroomer
{

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
      return events;
   }

   private void updateHistoryBranch()
   {

      Shell shell = HistoryBranchChecker.getShell();
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
      String[] commandDetails = HistoryBranchChecker.getCommandDetails();
      String cmdParentName = commandDetails[0];
      String cmdName = commandDetails[1];
      String enquotedCommand = Strings.enquote(
               Strings.areEqual(cmdParentName, cmdName) ? cmdName : cmdParentName + " " + cmdName
               );
      return UndoFacet.UNDO_STORE_COMMIT_MSG_PREFIX + enquotedCommand + " command";
   }

   private boolean anythingChanged(Git repo) throws NoWorkTreeException, GitAPIException
   {
      Status beforeCommandExecution = HistoryBranchChecker.getBeforeCommandExecution();
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
