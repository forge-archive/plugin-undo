/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.forge.git.GitUtils;
import org.jboss.forge.jgit.api.Git;
import org.jboss.forge.jgit.lib.Ref;
import org.jboss.forge.jgit.lib.Repository;
import org.jboss.forge.jgit.revwalk.RevCommit;
import org.jboss.forge.parser.java.util.Strings;
import org.jboss.forge.project.Project;
import org.jboss.forge.resources.DirectoryResource;
import org.jboss.forge.resources.FileResource;
import org.jboss.forge.test.AbstractShellTest;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.undo.forge.UndoFacet;
import org.jboss.undo.forge.UndoPlugin;
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

      Git repo = GitUtils.git(project.getProjectRoot());
      Assert.assertNotNull("git is not initialized", repo);

      String undoBranch = project.getFacet(UndoFacet.class).getUndoBranchName();

      boolean containsUndoBranch = false;
      for (Ref branch : GitUtils.getLocalBranches(repo))
         if (Strings.areEqual(Repository.shortenRefName(branch.getName()), undoBranch))
            containsUndoBranch = true;

      Assert.assertTrue("should contain undo-branch", containsUndoBranch);
   }

   @Test
   public void shouldInstallPluginWithCustomName() throws Exception
   {
      Project project = initializeJavaProject();

      getShell().execute("undo setup --branchName custom");

      Git repo = GitUtils.git(project.getProjectRoot());
      Assert.assertNotNull("git is not initialized", repo);

      String undoBranch = project.getFacet(UndoFacet.class).getUndoBranchName();

      boolean containsUndoBranch = false;
      for (Ref branch : GitUtils.getLocalBranches(repo))
         if (Strings.areEqual(Repository.shortenRefName(branch.getName()), undoBranch))
            containsUndoBranch = true;

      Assert.assertTrue("should contain undo-branch", containsUndoBranch);
   }

   @Test
   public void shouldAddChangesIntoUndoBranch() throws Exception
   {
      Project project = initializeJavaProject();
      getShell().execute("undo setup");

      String filename = "test1.txt";
      String contents = "foo bar baz";

      String forgeUndoPrefix = "history-branch: changes introduced by the ";
      String commandName = "touch";
      String command = commandName + " --filename " + filename + " --contents " + Strings.enquote(contents);
      getShell().execute(command);

      DirectoryResource dir = project.getProjectRoot();
      FileResource<?> file = dir.getChild(filename).reify(FileResource.class);
      Assert.assertTrue("file doesn't exist", file.exists());

      // assert the results of the previous command
      Iterable<RevCommit> commits = project.getFacet(UndoFacet.class).getStoredCommits();
      List<String> commitMsgs = extractCommitMsgs(commits);

      Assert.assertEquals(1, getHistoryBranchSize(project));
      Assert.assertEquals("wrong number of commits in the history branch", getHistoryBranchSize(project),
               commitMsgs.size());
      Assert.assertEquals("commit messages do not match", forgeUndoPrefix + Strings.enquote(commandName) +
               " command",
               commitMsgs.get(0));
   }

   @Test
   public void shouldAddChangesFromInsideNonTrackedDirs() throws Exception
   {
      Project project = initializeJavaProject();
      DirectoryResource dir = project.getProjectRoot();
      getShell().execute("undo setup");

      String filename = "test1.txt";
      String contents = "foo bar baz";
      String subdir = "subdir";

      Assert.assertFalse("failed because subdir exists already", dir.getChildDirectory(subdir).exists());

      String forgeUndoPrefix = "history-branch: changes introduced by the ";
      String commandName = "touch";
      String command = commandName + " --filename " + filename + " --contents " + Strings.enquote(contents);
      DirectoryResource subDirResource = dir.getOrCreateChildDirectory(subdir);

      getShell().setCurrentResource(subDirResource);
      getShell().execute(command);
      getShell().setCurrentResource(dir);

      boolean subdirExists = dir.getChildDirectory(subdir).exists();
      Assert.assertTrue("failed to recreate a subdir during the updateHistoryBranch()", subdirExists);

      FileResource<?> file = dir.getChildDirectory(subdir).getChild(filename).reify(FileResource.class);
      Assert.assertTrue("file doesn't exist", file.exists());

      // assert the results of the previous command
      Iterable<RevCommit> commits = project.getFacet(UndoFacet.class).getStoredCommits();
      List<String> commitMsgs = extractCommitMsgs(commits);

      Assert.assertEquals(1, getHistoryBranchSize(project));
      Assert.assertEquals("wrong number of commits in the history branch", getHistoryBranchSize(project),
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
      String contents = "foo bar baz";

      String forgeUndoPrefix = "history-branch: changes introduced by the ";
      String commandName = "touch";
      String command = commandName + " --filename " + filename + " --contents " + Strings.enquote(contents);
      getShell().execute(command);

      DirectoryResource dir = project.getProjectRoot();
      FileResource<?> file = dir.getChild(filename).reify(FileResource.class);
      Assert.assertTrue("file doesn't exist", file.exists());

      Iterable<RevCommit> commits = project.getFacet(UndoFacet.class).getStoredCommits();
      List<String> commitMsgs = extractCommitMsgs(commits);

      Assert.assertEquals(1, getHistoryBranchSize(project));
      Assert.assertEquals("wrong number of commits in the history branch", getHistoryBranchSize(project),
               commitMsgs.size());
      Assert.assertEquals("commit messages do not match", forgeUndoPrefix + Strings.enquote(commandName) +
               " command",
               commitMsgs.get(0));

      // restore
      boolean isRestored = project.getFacet(UndoFacet.class).undoLastChange();
      Assert.assertTrue("undo failed", isRestored);

      file = dir.getChild(filename).reify(FileResource.class);

      Assert.assertFalse("file should not exist", file.exists());
      commits = project.getFacet(UndoFacet.class).getStoredCommits();
      commitMsgs = extractCommitMsgs(commits);

      File forgeXml = new File(dir.getFullyQualifiedName() + "/src/main/resources/META-INF/forge.xml");
      Assert.assertTrue("forge project file doesn't exist anymore", forgeXml.exists());

      Assert.assertEquals(0, getHistoryBranchSize(project));
      Assert.assertEquals("wrong number of commits in the history branch", getHistoryBranchSize(project),
               commitMsgs.size());
   }

   @Test
   public void shouldNotCrashWhenCalledUndoRestoreInEmptyHistory() throws Exception
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
      String contents = "foo bar baz";

      String forgeUndoPrefix = "history-branch: changes introduced by the ";
      String commandName = "touch";
      String command = commandName + " --filename " + filename + " --contents " + Strings.enquote(contents);
      getShell().execute(command);

      DirectoryResource dir = project.getProjectRoot();
      FileResource<?> file = dir.getChild(filename).reify(FileResource.class);
      Assert.assertTrue("file doesn't exist", file.exists());

      Iterable<RevCommit> commits = project.getFacet(UndoFacet.class).getStoredCommits();
      List<String> commitMsgs = extractCommitMsgs(commits);

      Assert.assertEquals(1, getHistoryBranchSize(project));
      Assert.assertEquals("wrong number of commits in the history branch", getHistoryBranchSize(project),
               commitMsgs.size());
      Assert.assertEquals("commit messages do not match", forgeUndoPrefix + Strings.enquote(commandName) + " command",
               commitMsgs.get(0));

      // restore
      isRestored = project.getFacet(UndoFacet.class).undoLastChange();
      Assert.assertTrue("undo failed", isRestored);

      file = dir.getChild(filename).reify(FileResource.class);

      Assert.assertFalse("file should not exist", file.exists());
      commits = project.getFacet(UndoFacet.class).getStoredCommits();
      commitMsgs = extractCommitMsgs(commits);

      Assert.assertEquals(0, getHistoryBranchSize(project));
      Assert.assertEquals("wrong number of commits in the history branch", getHistoryBranchSize(project),
               commitMsgs.size());

      isRestored = project.getFacet(UndoFacet.class).undoLastChange();
      Assert.assertFalse("should not be able to undo last commit", isRestored);
   }

   @Test
   public void shouldShowOnlyCommitsOnHistoryBranch() throws Exception
   {
      Project project = initializeJavaProject();
      getShell().execute("undo setup");

      Assert.assertEquals(0, getHistoryBranchSize(project));
      verifyHistoryBranchSize(project);
   }

   private void verifyHistoryBranchSize(Project project)
   {
      int storedCommits = extractCommitMsgs(project.getFacet(UndoFacet.class).getStoredCommits()).size();
      Assert.assertEquals(getHistoryBranchSize(project), storedCommits);
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

   private int getHistoryBranchSize(Project project)
   {
      return project.getFacet(UndoFacet.class).historyBranchSize;
   }

}
