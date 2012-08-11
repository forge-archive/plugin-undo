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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.jboss.forge.env.Configuration;
import org.jboss.forge.git.GitFacet;
import org.jboss.forge.jgit.api.Git;
import org.jboss.forge.jgit.api.ResetCommand.ResetType;
import org.jboss.forge.jgit.api.errors.CheckoutConflictException;
import org.jboss.forge.jgit.api.errors.GitAPIException;
import org.jboss.forge.jgit.api.errors.InvalidRefNameException;
import org.jboss.forge.jgit.api.errors.MultipleParentsNotAllowedException;
import org.jboss.forge.jgit.api.errors.RefAlreadyExistsException;
import org.jboss.forge.jgit.api.errors.RefNotFoundException;
import org.jboss.forge.jgit.errors.IncorrectObjectTypeException;
import org.jboss.forge.jgit.errors.MissingObjectException;
import org.jboss.forge.jgit.lib.ObjectLoader;
import org.jboss.forge.jgit.lib.Ref;
import org.jboss.forge.jgit.lib.Repository;
import org.jboss.forge.jgit.lib.RepositoryBuilder;
import org.jboss.forge.jgit.notes.Note;
import org.jboss.forge.jgit.revwalk.RevCommit;
import org.jboss.forge.jgit.revwalk.RevWalk;
import org.jboss.forge.parser.java.util.Strings;
import org.jboss.forge.project.facets.BaseFacet;
import org.jboss.forge.resources.FileResource;
import org.jboss.forge.shell.plugins.Alias;
import org.jboss.forge.shell.plugins.Help;
import org.jboss.forge.shell.plugins.RequiresFacet;
import org.jboss.undo.forge.RepositoryCommitsMonitor.RepositoryCommitState;

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
   public static final String INITIAL_COMMIT_MSG = "repository initial commit";
   public static final String UNDO_INSTALL_COMMIT_MSG = "FORGE PLUGIN-UNDO: initial commit";
   public static final String UNDO_STORE_COMMIT_MSG_PREFIX = "history-branch: changes introduced by the ";
   public static final String DEFAULT_NOTE = "*WT";
   public static final String DELETED_COMMIT_NOTE = "*DELETED";
   public static boolean isReady = false;
   public int historyBranchSize = 0;
   private Git gitObject = null;
   private final RepositoryCommitsMonitor commitsMonitor = new RepositoryCommitsMonitor();

   @Inject
   Configuration config;

   @Override
   public boolean install()
   {
      try
      {
         Git git = getGitObject();

         ensureGitRepositoryIsInitialized(git);
         commitAllToHaveCleanTree(git);
         initializeHistoryBranch(git);
         commitsMonitor.setUndoBranchName(getUndoBranchName());

         UndoFacet.isReady = true;
         return true;
      }
      catch (Exception e)
      {
         throw new RuntimeException("Failed to install the UndoFacet", e.getCause());
      }
   }

   @Override
   public boolean isInstalled()
   {
      try
      {
         Git git = getGitObject();
         for (Ref branch : git.branchList().call())
            if (Strings.areEqual(Repository.shortenRefName(branch.getName()), getUndoBranchName()))
               return true;

         return false;
      }
      catch (Exception e)
      {
         throw new RuntimeException("Failed to check if UndoFacet is installed", e.getCause());
      }
   }

   public Iterable<RevCommit> getStoredCommits()
   {
      try
      {
         List<RevCommit> storedCommits = new ArrayList<RevCommit>();
         Iterable<RevCommit> commits = getLogForBranch(getGitObject(), getUndoBranchName(), historyBranchSize);

         for(RevCommit commit : commits)
         {
            if(!isMarkDeleted(commit))
               storedCommits.add(commit);
         }

         return storedCommits;
      }
      catch (Exception e)
      {
         throw new RuntimeException("Failed to get a list of stored commits in the history branch", e.getCause());
      }
   }

   public boolean undoLastChange()
   {
      Git repo = null;

      try
      {
         checkAndUpdateRepositoryForNewCommits();

         if (historyBranchSize > 0)
         {
            repo = getGitObject();
            RevCommit commitWithDefaultNote = findLatestCommitWithGivenNote(DEFAULT_NOTE);

            if (commitWithDefaultNote != null) // commit with default note is found!
            {
               boolean result = undoGivenCommit(commitWithDefaultNote);
               if (!result)
                  return result;

               return true;
            }
            else
            // no commits with default note are found
            {
               // try to look for commits with the current branch name
               RevCommit commitWithCurrentBranchNote = findLatestCommitWithGivenNote(repo.getRepository().getBranch());

               if (commitWithCurrentBranchNote != null) // commit with the current branch name is found!
               {
                  boolean result = undoGivenCommit(commitWithCurrentBranchNote);
                  if (!result)
                     return result;

                  return true;
               }
               else
               // no commits are found for restoring
               {
                  return false;
               }
            }
         }
         return false;
      }
      catch (Exception e)
      {
         throw new RuntimeException("Failed to undo last change [" + e.getMessage() + "]", e.getCause());
      }
   }

   private boolean undoGivenCommit(RevCommit commitToRevert) throws IOException, GitAPIException
   {
      Git repo = getGitObject();
      String previousBranch = "";

      try
      {
         previousBranch = repo.getRepository().getBranch();
         repo.add().addFilepattern(".").call();
         repo.commit().setMessage("FORGE PLUGIN-UNDO: preparing to undo a change").call();
         repo.checkout().setName(getUndoBranchName()).call();
         RevCommit reverted = repo.revert().include(commitToRevert).call();
         if (reverted == null)
            throw new RuntimeException("failed to revert a commit on a history branch");

         repo.checkout().setName(previousBranch).call();
         repo.cherryPick().include(reverted).call();

         repo.checkout().setName(getUndoBranchName()).call();
         repo.reset().setMode(ResetType.HARD).setRef("HEAD~1").call();
         repo.checkout().setName(previousBranch).call();

         markDeleted(commitToRevert);

         historyBranchSize--;
      }
      catch (MultipleParentsNotAllowedException e)
      {
         // revert of a merged commit failed. Roll back the changes introduced so far.
         try
         {
            repo.checkout().setName(previousBranch).call();
         }
         catch (Exception e2)
         {
            throw new RuntimeException(
                     "Failed during revert command (MultipleParentsNotAllowed). Then failed trying to rollback changes ["
                              + e.getMessage() + "]", e2.getCause());
         }
         return false;
      }
      return true;
   }

   private void markDeleted(RevCommit commitToRevert) throws IOException, GitAPIException
   {
      Git repo = getGitObject();
      repo.notesRemove().setObjectId(commitToRevert).call();
      repo.notesAdd().setObjectId(commitToRevert).setMessage(DELETED_COMMIT_NOTE).call();
   }

   private boolean isMarkDeleted(RevCommit commitToCheck) throws IOException, GitAPIException
   {
      Git repo = getGitObject();

      Note note = repo.notesShow().setObjectId(commitToCheck).call();
      if(note == null)
         return false;

      ObjectLoader noteBlob = repo.getRepository().open(note.getData());
      BufferedReader reader = new BufferedReader(new InputStreamReader(noteBlob.openStream()));
      String noteMsg = reader.readLine();

      if (Strings.areEqual(DELETED_COMMIT_NOTE, noteMsg))
         return true;

      return false;
   }

   private RevCommit findLatestCommitWithGivenNote(String msg) throws MissingObjectException,
            IncorrectObjectTypeException,
            IOException, RefAlreadyExistsException, RefNotFoundException, InvalidRefNameException,
            CheckoutConflictException, GitAPIException
   {
      Git repo = getGitObject();
      int size = historyBranchSize;
      RevCommit commitWithGivenNote = null;

      RevWalk revWalk = new RevWalk(repo.getRepository());

      RevCommit undoBranchHEAD = revWalk.parseCommit(getUndoBranchRef().getObjectId());
      revWalk.markStart(undoBranchHEAD);

      for (RevCommit commit = undoBranchHEAD; commit != null && size > 0; commit = revWalk.next(), size--)
      {
         Note note = repo.notesShow().setObjectId(commit).call();
         if(note == null)
            continue;

         ObjectLoader noteBlob = repo.getRepository().open(note.getData());
         BufferedReader reader = new BufferedReader(new InputStreamReader(noteBlob.openStream()));
         String noteMsg = reader.readLine();

         if(Strings.areEqual(DELETED_COMMIT_NOTE, noteMsg))
            continue;

         if (Strings.areEqual(msg, noteMsg))
         {
            commitWithGivenNote = commit;
            break;
         }
      }

      return commitWithGivenNote;
   }

   public boolean reset()
   {
      // TODO: add history-branch reset functionality
      // should remove all commits from historyBranch.
      // reset().head~[number of commits]

      return false;
   }

   private void ensureGitRepositoryIsInitialized(Git repo) throws GitAPIException
   {
      List<Ref> branches = repo.branchList().call();
      if (branches != null && branches.size() == 0)
      {
         FileResource<?> file = project.getProjectRoot().getChild(".gitignore").reify(FileResource.class);
         file.createNewFile();
         repo.add().addFilepattern(".gitignore").call();
         repo.commit().setMessage(INITIAL_COMMIT_MSG).call();
      }
   }

   private void commitAllToHaveCleanTree(Git repo) throws GitAPIException
   {
      repo.add().addFilepattern(".").call();
      repo.commit().setMessage(UNDO_INSTALL_COMMIT_MSG).call();
   }

   private void initializeHistoryBranch(Git git) throws IOException, RefAlreadyExistsException, RefNotFoundException,
            InvalidRefNameException, GitAPIException
   {
      git.branchCreate().setName(getUndoBranchName()).call();
   }

   public RepositoryCommitState checkAndUpdateRepositoryForNewCommits() throws IOException, GitAPIException
   {
      RepositoryCommitState state = commitsMonitor.updateCommitCounters(getGitObject());

      switch (state)
      {
      case NO_CHANGES:
         break;
      case ONE_NEW_COMMIT:
         String branchWithNewCommit = getCommitMonitorBranchWithOneNewCommit();
         changeWorkingTreeNotesTo(branchWithNewCommit);
         break;
      case MULTIPLE_CHANGED_COMMITS:
         reset();
         break;
      default:
         throw new RuntimeException("Unknown RepositoryCommitState: " + state.toString());
      }

      return state;
   }

   public RepositoryCommitState getCommitMonitorState()
   {
      return commitsMonitor.getCurrentState();
   }

   public String getCommitMonitorBranchWithOneNewCommit()
   {
      return commitsMonitor.getBranchWithOneNewCommit();
   }

   public void changeWorkingTreeNotesTo(String branchWithNewCommit) throws IOException, GitAPIException
   {
      Git git = getGitObject();
      RevWalk revWalk = new RevWalk(git.getRepository());
      List<Note> notes = git.notesList().call();

      for (Note note : notes)
      {
         ObjectLoader noteBlob = git.getRepository().open(note.getData());
         BufferedReader reader = new BufferedReader(new InputStreamReader(noteBlob.openStream()));
         String noteMsg = reader.readLine();

         if(Strings.areEqual(noteMsg, DELETED_COMMIT_NOTE))
            continue;

         if (Strings.areEqual(noteMsg, DEFAULT_NOTE))
         {
            RevCommit commitWithDefaultNote = revWalk.parseCommit(note);
            git.notesRemove().setObjectId(commitWithDefaultNote).call();

            git.notesAdd().setObjectId(commitWithDefaultNote).setMessage(branchWithNewCommit).call();
         }
      }
   }

   public String getUndoBranchName()
   {
      return config.getString(HISTORY_BRANCH_CONFIG_KEY, DEFAULT_HISTORY_BRANCH_NAME);
   }

   public Ref getUndoBranchRef() throws IOException, RefAlreadyExistsException, RefNotFoundException,
            InvalidRefNameException, CheckoutConflictException, GitAPIException
   {
      Git repo = getGitObject();
      return repo.getRepository().getRef(getUndoBranchName());
   }

   public Git getGitObject() throws IOException
   {
      if (this.gitObject == null)
      {
         RepositoryBuilder db = new RepositoryBuilder().findGitDir(project.getProjectRoot()
                  .getUnderlyingResourceObject());
         this.gitObject = new Git(db.build());
      }
      return gitObject;
   }

   public static Iterable<RevCommit> getLogForBranch(final Git repo, String branchName, int maxCount)
            throws GitAPIException,
            IOException
   {
      if (maxCount <= 0)
      {
         return new ArrayList<RevCommit>();
      }

      String oldBranch = repo.getRepository().getBranch();
      repo.checkout().setName(branchName).call();

      Iterable<RevCommit> commits = repo.log().setMaxCount(maxCount).call();

      repo.checkout().setName(oldBranch).call();
      return commits;
   }
}
