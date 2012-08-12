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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.forge.jgit.api.Git;
import org.jboss.forge.jgit.api.errors.ConcurrentRefUpdateException;
import org.jboss.forge.jgit.api.errors.GitAPIException;
import org.jboss.forge.jgit.api.errors.NoFilepatternException;
import org.jboss.forge.jgit.api.errors.NoHeadException;
import org.jboss.forge.jgit.api.errors.NoMessageException;
import org.jboss.forge.jgit.api.errors.UnmergedPathsException;
import org.jboss.forge.jgit.api.errors.WrongRepositoryStateException;
import org.jboss.forge.jgit.lib.RepositoryBuilder;
import org.jboss.forge.jgit.revwalk.RevCommit;
import org.jboss.forge.parser.java.util.Strings;
import org.jboss.forge.project.Project;
import org.jboss.forge.resources.DirectoryResource;
import org.jboss.forge.resources.FileResource;
import org.jboss.forge.test.AbstractShellTest;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.undo.forge.RepositoryCommitsMonitor.RepositoryCommitState;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author <a href="mailto:jevgeni.zelenkov@gmail.com">Jevgeni Zelenkov</a>
 *
 */
public class UndoFacetMultipleOpsTest extends AbstractShellTest
{
   @Deployment
   public static JavaArchive getDeployment()
   {
      return AbstractShellTest.getDeployment().addPackages(true, UndoPlugin.class.getPackage(),
               UndoFacet.class.getPackage());
   }

   private static final String COMMAND_NAME = "touch";
   private static final String[] FILENAMES = { "test1.txt", "test2.txt", "test3.txt", "test4.txt" };
   private static final String[] BRANCHES = { "master", "other" };

   private static Project myProject = null;
   private static DirectoryResource dir = null;
   private static Iterable<RevCommit> commits = null;
   private static List<String> commitMsgs = null;
   private static FileResource<?> file = null;
   private static String dirPath = null;

   @Before
   public void setUp() throws Exception
   {
      myProject = initializeJavaProject();
      dir = myProject.getProjectRoot();
      dirPath = dir.getFullyQualifiedName();
      getShell().execute("undo setup");
   }

   @After
   public void destroy()
   {
      myProject = null;
      dir = null;
      dirPath = null;

      commits = null;
      commitMsgs = null;
      file = null;
   }

   @Test
   public void shouldAddTwoChangesIntoUndoBranch() throws Exception
   {
      executeForgeCommand(FILENAMES[0]);
      verifyFilesExistance(true, false);
      executeForgeCommand(FILENAMES[1]);
      verifyFilesExistance(true, true);
      verifyCommitNumber(2);
   }

   @Test
   public void shouldUndoTwoLastChanges() throws Exception
   {
      executeForgeCommand(FILENAMES[0]);
      executeForgeCommand(FILENAMES[1]);

      undoRestore(true);
      verifyFilesExistance(true, false);
      verifyCommitNumber(1);

      undoRestore(true);
      verifyFilesExistance(false, false);
      verifyCommitNumber(0);
   }

   @Test
   public void shouldBeAbleToAddAddRevertAdd() throws Exception
   {
      executeForgeCommand(FILENAMES[0]);
      executeForgeCommand(FILENAMES[1]);
      undoRestore(true);

      executeForgeCommand(FILENAMES[2]);
      verifyFilesExistance(true, false, true);
      verifyCommitNumber(2);
   }

   @Test
   public void shouldBeAbleToAddAddRevertRevertAdd() throws Exception
   {
      executeForgeCommand(FILENAMES[0]);
      executeForgeCommand(FILENAMES[1]);
      undoRestore(true);
      undoRestore(true);
      verifyFilesExistance(false, false);
      verifyCommitNumber(0);

      executeForgeCommand(FILENAMES[2]);
      verifyFilesExistance(false, false, true);
      verifyCommitNumber(1);
   }

   @Test
   public void shouldUndoLastChangeOnMasterBranchAfterCommit() throws Exception
   {
      // forge command
      // git-commit
      // restore (from [note]wt) // since no forge command was executed to update the note

      executeForgeCommand(FILENAMES[0]);
      executeForgeCommand(FILENAMES[1]);
      gitCommitAll();
      undoRestore(true);
      verifyFilesExistance(true, false);
   }

   @Test
   public void shouldUndoLastChangeOnMasterBranchAfterCommitAndForgeCommand() throws Exception
   {
      // forge command
      // git-commit
      // restore (from [note]master)

      executeForgeCommand(FILENAMES[0]);
      executeForgeCommand(FILENAMES[1]);
      verifyNotes(UndoFacet.DEFAULT_NOTE, UndoFacet.DEFAULT_NOTE);
      gitCommitAll();
      verifyNotes(UndoFacet.DEFAULT_NOTE, UndoFacet.DEFAULT_NOTE);

      executeForgeCommand(FILENAMES[2]); // old notes are updated
      verifyFilesExistance(true, true, true);
      verifyNotes(UndoFacet.DEFAULT_NOTE, BRANCHES[0], BRANCHES[0]);

      undoRestore(true); // from WT
      verifyFilesExistance(true, true, false);
      verifyNotes(BRANCHES[0], BRANCHES[0]);

      undoRestore(true); // from master
      verifyFilesExistance(true, false, false);
      verifyNotes(BRANCHES[0]);
   }

   @Test
   public void shouldUndoTwoChangesOnMasterBranchAfterCommit() throws Exception
   {
      executeForgeCommand(FILENAMES[0]);
      executeForgeCommand(FILENAMES[1]);
      gitCommitAll();

      executeForgeCommand(FILENAMES[2]);
      undoRestore(true); // from WT
      undoRestore(true); // from master
      undoRestore(true); // from master
      verifyFilesExistance(false, false, false);
      verifyNotes(new String[0]);
   }

   @Test
   public void shouldUndoTwoChangesOnWTAndOneOnMasterBranch() throws Exception
   {
      // forge command
      // git-commit
      // forge command
      // restore (from [note]*WT)
      // restore (from [note]*WT)
      // restore (from [note]master)

      executeForgeCommand(FILENAMES[0]);
      gitCommitAll();
      executeForgeCommand(FILENAMES[1]);
      executeForgeCommand(FILENAMES[2]);
      verifyFilesExistance(true, true, true);
      verifyNotes(UndoFacet.DEFAULT_NOTE, UndoFacet.DEFAULT_NOTE, BRANCHES[0]);
      undoRestore(true);
      verifyFilesExistance(true, true, false);
      verifyNotes(UndoFacet.DEFAULT_NOTE, BRANCHES[0]);
      undoRestore(true);
      verifyFilesExistance(true, false, false);
      verifyNotes(BRANCHES[0]);
      undoRestore(true);
      verifyFilesExistance(false, false, false);
      verifyNotes(new String[0]);
   }

   @Test
   public void shouldNotRestoreAnythingFromNewBranch() throws Exception
   {
      // forge command
      // git-commit
      // git-branch new-branch
      // switch to new-branch
      // restore should return false

      executeForgeCommand(FILENAMES[0]);
      gitCommitAll();
      executeForgeCommand(FILENAMES[1]);
      undoRestore(true);
      verifyFilesExistance(true, false);
      gitCreateNewBranch(BRANCHES[1]);
      gitCheckout(BRANCHES[1]);
      verifyFilesExistance(true, false);
      verifyNotes(BRANCHES[0]);

      undoRestore(false); // no commits to restore on this branch
      verifyFilesExistance(true, false);
      verifyNotes(BRANCHES[0]);
   }

   @Test
   public void shouldRestoreChangesFromDifferentBranches() throws Exception
   {
      // on master:
      // forge command
      // git-commit

      // checkout -b new-branch:
      // forge command
      // git-commit

      // forge command // on WT

      // restore (from [note]*WT)
      // checkout master
      // restore (from [note]master)

      // checkout other
      // restore (from [note]other)

      executeForgeCommand(FILENAMES[0]);
      gitCommitAll();
      executeForgeCommand(FILENAMES[1]); // forge command to update notes
      undoRestore(true);
      gitCommitAll();
      verifyFilesExistance(true, false);

      gitCreateNewBranch(BRANCHES[1]);
      gitCheckout(BRANCHES[1]);

      executeForgeCommand(FILENAMES[2]);
      gitCommitAll(); // commit to update notes
      executeForgeCommand(FILENAMES[3]);
      verifyFilesExistance(true, false, true, true);
      verifyNotes(UndoFacet.DEFAULT_NOTE, BRANCHES[1], BRANCHES[0]);

      undoRestore(true);
      verifyFilesExistance(true, false, true, false);
      verifyNotes(BRANCHES[1], BRANCHES[0]);

      gitCheckout(BRANCHES[0]);
      undoRestore(true);
      verifyFilesExistance(false, false); // third file is not visible on the master branch
      verifyNotes(BRANCHES[1]);

      gitCheckout(BRANCHES[1]);
      undoRestore(true);
      verifyFilesExistance(true, false, false); // first file is not restored on the other branch
      verifyNotes(new String[0]);
   }

   @Test
   public void shouldNotResetInEmptyHistory() throws Exception
   {
      undoReset(false);
   }

   @Test
   public void shouldNotSeeUncheckedForgeCommand() throws Exception
   {
      executeForgeCommand(FILENAMES[0]);
      undoReset(false);
   }

   @Test
   public void shouldNotResetIfDirtyWT() throws Exception
   {
      executeForgeCommand(FILENAMES[0]);
      gitCommitAll();
      executeForgeCommand(FILENAMES[1]); // dirty WT

      undoReset(false);
   }

   @Test
   public void shouldResetOneChangeOnMasterBranch() throws Exception
   {
      executeForgeCommand(FILENAMES[0]);
      gitCommitAll();
      executeForgeCommand(FILENAMES[1]);
      undoRestore(true); // clean WT

      undoReset(true);
   }

   @Test
   public void shouldResetChangesOnDifferentBranches() throws Exception
   {
      executeForgeCommand(FILENAMES[0]);
      gitCommitAll();
      executeForgeCommand(FILENAMES[1]);
      undoRestore(true);

      gitCreateNewBranch(BRANCHES[1]);
      gitCheckout(BRANCHES[1]);

      executeForgeCommand(FILENAMES[2]);
      gitCommitAll();
      executeForgeCommand(FILENAMES[3]);
      undoRestore(true);

      verifyNotes(BRANCHES[1], BRANCHES[0]);
      undoReset(true);
   }

   @Test
   public void shouldNotLetResetTwoTimes() throws Exception
   {
      executeForgeCommand(FILENAMES[0]);
      gitCommitAll();
      executeForgeCommand(FILENAMES[1]);
      undoRestore(true);

      undoReset(true);
      undoReset(false);
   }

   @Test
   public void shouldStoreChangesAfterReset() throws Exception
   {
      executeForgeCommand(FILENAMES[0]);
      gitCommitAll();
      executeForgeCommand(FILENAMES[1]);
      undoRestore(true);

      undoReset(true);

      executeForgeCommand(FILENAMES[2]);
      verifyFilesExistance(true, false, true);
      verifyNotes(UndoFacet.DEFAULT_NOTE);
   }

   @Test
   public void shouldStoreChangesAndRestoreAfterReset() throws Exception
   {
      executeForgeCommand(FILENAMES[0]);
      gitCommitAll();
      executeForgeCommand(FILENAMES[1]);
      undoRestore(true);

      undoReset(true);

      executeForgeCommand(FILENAMES[2]);
      verifyFilesExistance(true, false, true);
      verifyNotes(UndoFacet.DEFAULT_NOTE);

      undoRestore(true);
      verifyFilesExistance(true, false, false);
      verifyNotes(new String[0]);

      undoRestore(false);
   }

   // helper methods
   private void executeForgeCommand(String filename)
            throws Exception
   {
      file = dir.getChild(filename).reify(FileResource.class);
      Assert.assertFalse("file should not already exist", file.exists());

      String command = COMMAND_NAME + " " + filename;
      getShell().execute(command);
   }

   private void gitCommitAll() throws IOException, GitAPIException, NoFilepatternException, NoHeadException,
            NoMessageException, UnmergedPathsException, ConcurrentRefUpdateException, WrongRepositoryStateException
   {
      Git repo = getGit(myProject);
      repo.add().addFilepattern(".").call();
      repo.commit().setMessage("test commit").call();
   }

   private void gitCreateNewBranch(String branchName) throws IOException, GitAPIException
   {
      Git repo = getGit(myProject);
      repo.branchCreate().setName(branchName).call();
   }

   private void gitCheckout(String branchName) throws IOException, GitAPIException
   {
      Git repo = getGit(myProject);
      repo.checkout().setName(branchName).call();
   }

   private void undoRestore(boolean expected)
   {
      boolean isRestored = myProject.getFacet(UndoFacet.class).undoLastChange();
      Assert.assertEquals("restore values don't match", expected, isRestored);

      verifyForgeProjectFileExists();
   }

   private void undoReset(boolean expected)
   {
      boolean isReset = myProject.getFacet(UndoFacet.class).reset();
      Assert.assertEquals("reset values don't match", expected, isReset);

      if (expected)
         verifyReset();
   }

   private void verifyReset()
   {
      int historySize = myProject.getFacet(UndoFacet.class).historyBranchSize;
      RepositoryCommitState state = myProject.getFacet(UndoFacet.class).getCommitMonitorState();
      String branchWithOneCommit = myProject.getFacet(UndoFacet.class).getCommitMonitorBranchWithOneNewCommit();

      Assert.assertEquals("reset history size should be 0", 0, historySize);
      Assert.assertEquals("state doesn't match", RepositoryCommitState.NO_CHANGES, state);
      Assert.assertEquals("branch with one commit should be empty", "", branchWithOneCommit);
   }

   private void verifyForgeProjectFileExists()
   {

      String parent = dir.getChild("src").getChild("main").getChild("resources").getChild("META-INF")
               .getFullyQualifiedName();
      String child = "forge.xml";
      File forgeXml = new File(parent, child);
      Assert.assertTrue("forge project file doesn't exist anymore", forgeXml.exists());
   }

   private void verifyFilesExistance(boolean fileOneShouldExist, boolean fileTwoShouldExist)
   {
      File tmpFile = null;
      if (fileOneShouldExist)
      {
         tmpFile = new File(dirPath, FILENAMES[0]);
         Assert.assertTrue("first file should exist", tmpFile.exists());
      }
      else
      {
         tmpFile = new File(dirPath, FILENAMES[0]);
         Assert.assertFalse("first file should not exist", tmpFile.exists());
      }

      if (fileTwoShouldExist)
      {
         tmpFile = new File(dirPath, FILENAMES[1]);
         Assert.assertTrue("second file should exist", tmpFile.exists());
      }
      else
      {
         tmpFile = new File(dirPath, FILENAMES[1]);
         Assert.assertFalse("second file should not exist", tmpFile.exists());
      }
   }

   private void verifyFilesExistance(boolean fileOneShouldExist, boolean fileTwoShouldExist,
            boolean fileThreeShouldExist)
   {
      File tmpFile = null;

      verifyFilesExistance(fileOneShouldExist, fileTwoShouldExist);

      if (fileThreeShouldExist)
      {
         tmpFile = new File(dirPath, FILENAMES[2]);
         Assert.assertTrue("third file should exist", tmpFile.exists());
      }
      else
      {
         tmpFile = new File(dirPath, FILENAMES[2]);
         Assert.assertFalse("third file should not exist", tmpFile.exists());
      }
   }

   private void verifyFilesExistance(boolean fileOneShouldExist, boolean fileTwoShouldExist,
            boolean fileThreeShouldExist, boolean fileFourShouldExist)
   {
      File tmpFile = null;

      verifyFilesExistance(fileOneShouldExist, fileTwoShouldExist, fileThreeShouldExist);

      if (fileFourShouldExist)
      {
         tmpFile = new File(dirPath, FILENAMES[3]);
         Assert.assertTrue("fourth file should exist", tmpFile.exists());
      }
      else
      {
         tmpFile = new File(dirPath, FILENAMES[3]);
         Assert.assertFalse("fourth file should not exist", tmpFile.exists());
      }

   }

   private void verifyCommitNumber(int expected)
   {
      commits = myProject.getFacet(UndoFacet.class).getStoredCommitsOnHistoryBranch();
      commitMsgs = extractCommitMsgs(commits);

      if (!commitMsgs.isEmpty())
      {
         Assert.assertEquals("commit messages do not match",
                  UndoFacet.UNDO_STORE_COMMIT_MSG_PREFIX + Strings.enquote(COMMAND_NAME) + " command",
                  commitMsgs.get(0));
      }

      Assert.assertEquals("number of commit don't match", expected, commitMsgs.size());
   }

   private void verifyNotes(String... notes)
   {
      Map<RevCommit, String> commitsWithNotes = myProject.getFacet(UndoFacet.class)
               .getStoredCommitsWithNotesOnHistoryBranch();

      Assert.assertEquals("number of notes don't match", notes.length, commitsWithNotes.size());

      int index = 0;
      for (Entry<RevCommit, String> each : commitsWithNotes.entrySet())
      {
         Assert.assertEquals("notes don't match", notes[index], each.getValue());
         index++;
      }
   }

   private static List<String> extractCommitMsgs(final Iterable<RevCommit> collection)
   {
      List<String> commitMsgs = new ArrayList<String>();

      Iterator<RevCommit> iter = collection.iterator();
      while (iter.hasNext())
      {
         RevCommit commit = iter.next();
         commitMsgs.add(commit.getFullMessage());
      }

      return commitMsgs;
   }

   private Git getGit(Project project) throws IOException
   {
      RepositoryBuilder db = new RepositoryBuilder().findGitDir(project.getProjectRoot().getUnderlyingResourceObject());
      Git repo = new Git(db.build());
      return repo;
   }
}