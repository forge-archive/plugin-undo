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

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.forge.jgit.api.Git;
import org.jboss.forge.jgit.lib.Ref;
import org.jboss.forge.jgit.lib.Repository;
import org.jboss.forge.jgit.lib.RepositoryBuilder;
import org.jboss.forge.jgit.revwalk.RevCommit;
import org.jboss.forge.parser.java.util.Strings;
import org.jboss.forge.project.Project;
import org.jboss.forge.resources.DirectoryResource;
import org.jboss.forge.resources.FileResource;
import org.jboss.forge.test.AbstractShellTest;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="mailto:jevgeni.zelenkov@gmail.com">Jevgeni Zelenkov</a>
 *
 */
public class UndoFacetBasicTest extends AbstractShellTest
{
   @Deployment
   public static JavaArchive getDeployment()
   {
      return AbstractShellTest.getDeployment().addPackages(true, UndoPlugin.class.getPackage(),
               UndoFacet.class.getPackage());
   }

   @Test
   public void shouldInstallPlugin() throws Exception
   {
      Project project = initializeJavaProject();

      Assert.assertNotNull(project);

      getShell().execute("undo setup");

      Git repo = getGit(project);
      Assert.assertNotNull("git is not initialized", repo);

      String undoBranch = project.getFacet(UndoFacet.class).getUndoBranchName();

      boolean containsUndoBranch = false;
      for (Ref branch : repo.branchList().call())
         if (Strings.areEqual(Repository.shortenRefName(branch.getName()), undoBranch))
            containsUndoBranch = true;

      Assert.assertTrue("should contain undo-branch", containsUndoBranch);
   }

   @Test
   public void shouldInstallPluginWithCustomName() throws Exception
   {
      Project project = initializeJavaProject();

      getShell().execute("undo setup --branchName custom");

      Git repo = getGit(project);
      Assert.assertNotNull("git is not initialized", repo);

      String undoBranch = project.getFacet(UndoFacet.class).getUndoBranchName();

      boolean containsUndoBranch = false;
      for (Ref branch : repo.branchList().call())
         if (Strings.areEqual(Repository.shortenRefName(branch.getName()), undoBranch))
            containsUndoBranch = true;

      Assert.assertTrue("should contain undo-branch", containsUndoBranch);
   }
   
   @Test
   public void shouldIgnoreCommandWhichDoesntChangeResources() throws Exception
   {
      Project project = initializeJavaProject();
      getShell().execute("undo setup");

      String commandName = "ls";
      getShell().execute(commandName);

      // assert the no commits created
      Iterable<RevCommit> commits = project.getFacet(UndoFacet.class).getStoredCommitsOnHistoryBranch();
      List<String> commitMsgs = extractCommitMsgs(commits);

      Assert.assertEquals("wrong number of commits in the history branch", 0, commitMsgs.size());
   }

   @Test
   public void shouldAddChangesIntoUndoBranch() throws Exception
   {
      Project project = initializeJavaProject();
      getShell().execute("undo setup");

      String filename = "test1.txt";

      String forgeUndoPrefix = "history-branch: changes introduced by the ";
      String commandName = "touch";
      String command = commandName + " " + filename;
      getShell().execute(command);

      DirectoryResource dir = project.getProjectRoot();
      FileResource<?> file = dir.getChild(filename).reify(FileResource.class);
      Assert.assertTrue("file doesn't exist", file.exists());

      // assert the results of the previous command
      Iterable<RevCommit> commits = project.getFacet(UndoFacet.class).getStoredCommitsOnHistoryBranch();
      List<String> commitMsgs = extractCommitMsgs(commits);

      Assert.assertEquals("wrong number of commits in the history branch", 1, commitMsgs.size());
      Assert.assertEquals("commit messages do not match", forgeUndoPrefix + Strings.enquote(commandName) +
               " command",
               commitMsgs.get(0));
   }

   @Test
   // here only for test purposes. Actually belongs to UndoFacetMultipleOpsTest.java
   public void shouldAddTwoChangesIntoUndoBranch() throws Exception
   {
      Project project = initializeJavaProject();
      getShell().execute("undo setup");

      String filename = "test1.txt";
      String filename2 = "test2.txt";

      String forgeUndoPrefix = "history-branch: changes introduced by the ";
      String commandName = "touch";
      String command = commandName + " " + filename;
      getShell().execute(command);

      DirectoryResource dir = project.getProjectRoot();
      FileResource<?> file = dir.getChild(filename).reify(FileResource.class);
      Assert.assertTrue("file doesn't exist", file.exists());

      // assert the results of the previous command
      Iterable<RevCommit> commits = project.getFacet(UndoFacet.class).getStoredCommitsOnHistoryBranch();
      List<String> commitMsgs = extractCommitMsgs(commits);

      Assert.assertEquals("wrong number of commits in the history branch", 1,
               commitMsgs.size());
      Assert.assertEquals("commit messages do not match", forgeUndoPrefix + Strings.enquote(commandName) +
               " command",
               commitMsgs.get(0));

      String command2 = commandName + " " + filename2;
      getShell().execute(command2);

      DirectoryResource dir2 = project.getProjectRoot();
      FileResource<?> file2 = dir2.getChild(filename2).reify(FileResource.class);
      Assert.assertTrue("file doesn't exist", file2.exists());

      // assert the results of the previous command
      Iterable<RevCommit> commits2 = project.getFacet(UndoFacet.class).getStoredCommitsOnHistoryBranch();
      List<String> commitMsgs2 = extractCommitMsgs(commits2);

      Assert.assertEquals("wrong number of commits in the history branch", 2,
               commitMsgs2.size());
      Assert.assertEquals("commit messages do not match", forgeUndoPrefix + Strings.enquote(commandName) +
               " command",
               commitMsgs2.get(0));

      // file1 should still exist
      // FileResource<?> firstFileAgain = dir.getChild(filename).reify(FileResource.class);
      // Assert.assertTrue("file doesn't exist", firstFileAgain.exists());
   }

   @Test
   public void shouldAddChangesFromInsideNonTrackedDirs() throws Exception
   {
      Project project = initializeJavaProject();
      DirectoryResource dir = project.getProjectRoot();
      getShell().execute("undo setup");

      String filename = "test1.txt";
      String subdir = "subdir";

      Assert.assertFalse("failed because subdir exists already", dir.getChildDirectory(subdir).exists());

      String forgeUndoPrefix = "history-branch: changes introduced by the ";
      String commandName = "touch";
      String command = commandName + " " + filename;
      DirectoryResource subDirResource = dir.getOrCreateChildDirectory(subdir);

      getShell().setCurrentResource(subDirResource);
      getShell().execute(command);
      getShell().setCurrentResource(dir);

      boolean subdirExists = dir.getChildDirectory(subdir).exists();
      Assert.assertTrue("failed to recreate a subdir during the updateHistoryBranch()", subdirExists);

      FileResource<?> file = dir.getChildDirectory(subdir).getChild(filename).reify(FileResource.class);
      Assert.assertTrue("file doesn't exist", file.exists());

      // assert the results of the previous command
      Iterable<RevCommit> commits = project.getFacet(UndoFacet.class).getStoredCommitsOnHistoryBranch();
      List<String> commitMsgs = extractCommitMsgs(commits);

      Assert.assertEquals("wrong number of commits in the history branch", 1,
               commitMsgs.size());
      Assert.assertEquals("commit messages do not match", forgeUndoPrefix + Strings.enquote(commandName) +
               " command",
               commitMsgs.get(0));
   }

   @Test
   public void shouldUndoLastChange() throws Exception
   {
      // init
      // touch plugin file1
      // verify file1 exists
      // verify commit in history branch exists
      // undo last change
      // verify file1 doesn't exist
      // verify commit in history branch doesn't exist

      Project project = initializeJavaProject();
      getShell().execute("undo setup");

      String filename = "test1.txt";

      String forgeUndoPrefix = "history-branch: changes introduced by the ";
      String commandName = "touch";
      String command = commandName + " " + filename;
      getShell().execute(command);

      DirectoryResource dir = project.getProjectRoot();
      FileResource<?> file = dir.getChild(filename).reify(FileResource.class);
      Assert.assertTrue("file doesn't exist", file.exists());

      Iterable<RevCommit> commits = project.getFacet(UndoFacet.class).getStoredCommitsOnHistoryBranch();
      List<String> commitMsgs = extractCommitMsgs(commits);

      Assert.assertEquals("wrong number of commits in the history branch", 1,
               commitMsgs.size());
      Assert.assertEquals("commit messages do not match", forgeUndoPrefix + Strings.enquote(commandName) +
               " command",
               commitMsgs.get(0));

      // restore
      boolean isRestored = project.getFacet(UndoFacet.class).undoLastChange();
      Assert.assertTrue("undo failed", isRestored);

      file = dir.getChild(filename).reify(FileResource.class);

      Assert.assertFalse("file should not exist", file.exists());
      commits = project.getFacet(UndoFacet.class).getStoredCommitsOnHistoryBranch();
      commitMsgs = extractCommitMsgs(commits);

      File forgeXml = new File(dir.getFullyQualifiedName() + "/src/main/resources/META-INF/forge.xml");
      Assert.assertTrue("forge project file doesn't exist anymore", forgeXml.exists());

      Assert.assertEquals("wrong number of commits in the history branch", 0,
               commitMsgs.size());
   }

   @Test
   public void shouldNotCrashWhenCalledUndoRestoreInEmptyHistory1() throws Exception
   {
      Project project = initializeJavaProject();

      getShell().execute("undo setup");

      boolean isRestored = project.getFacet(UndoFacet.class).undoLastChange();
      Assert.assertFalse("should not be able to undo last commit", isRestored);
   }

   @Test
   public void shouldNotCrashWhenCalledUndoRestoreInEmptyHistory2() throws Exception
   {
      // init
      // touch plugin file1
      // verify file1 exists
      // verify commit in history branch exists
      // undo last change
      // verify file1 doesn't exist
      // verify commit in history branch doesn't exist
      // undo last change 2 times

      boolean isRestored = false;

      Project project = initializeJavaProject();

      getShell().execute("undo setup");

      String filename = "test1.txt";

      String forgeUndoPrefix = "history-branch: changes introduced by the ";
      String commandName = "touch";
      String command = commandName + " " + filename;
      getShell().execute(command);

      DirectoryResource dir = project.getProjectRoot();
      FileResource<?> file = dir.getChild(filename).reify(FileResource.class);
      Assert.assertTrue("file doesn't exist", file.exists());

      Iterable<RevCommit> commits = project.getFacet(UndoFacet.class).getStoredCommitsOnHistoryBranch();
      List<String> commitMsgs = extractCommitMsgs(commits);

      Assert.assertEquals("wrong number of commits in the history branch", 1,
               commitMsgs.size());
      Assert.assertEquals("commit messages do not match", forgeUndoPrefix + Strings.enquote(commandName) + " command",
               commitMsgs.get(0));

      // restore
      isRestored = project.getFacet(UndoFacet.class).undoLastChange();
      Assert.assertTrue("undo failed", isRestored);

      file = dir.getChild(filename).reify(FileResource.class);

      Assert.assertFalse("file should not exist", file.exists());
      commits = project.getFacet(UndoFacet.class).getStoredCommitsOnHistoryBranch();
      commitMsgs = extractCommitMsgs(commits);

      Assert.assertEquals("wrong number of commits in the history branch", 0,
               commitMsgs.size());

      isRestored = project.getFacet(UndoFacet.class).undoLastChange();
      Assert.assertFalse("should not be able to undo last commit", isRestored);
   }

   @Test
   public void shouldShowOnlyCommitsOnHistoryBranch() throws Exception
   {
      Project project = initializeJavaProject();
      getShell().execute("undo setup");

      Iterable<RevCommit> commits = project.getFacet(UndoFacet.class).getStoredCommitsOnHistoryBranch();
      List<String> commitMsgs = extractCommitMsgs(commits);

      Assert.assertEquals(0, commitMsgs.size());
   }

   // helper methods
   private List<String> extractCommitMsgs(final Iterable<RevCommit> collection)
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
