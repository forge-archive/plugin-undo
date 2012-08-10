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
import org.jboss.forge.jgit.revwalk.RevCommit;
import org.jboss.forge.parser.java.util.Strings;
import org.jboss.forge.project.Project;
import org.jboss.forge.resources.DirectoryResource;
import org.jboss.forge.resources.FileResource;
import org.jboss.forge.test.AbstractShellTest;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
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
   private static final String[] FILENAMES = { "test1.txt", "test2.txt", "test3.txt" };

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
      verifyCommitMsgs();
   }

   @Test
   public void shouldUndoTwoLastChanges() throws Exception
   {
      executeForgeCommand(FILENAMES[0]);
      executeForgeCommand(FILENAMES[1]);

      undoRestore(FILENAMES[1]);
      verifyFilesExistance(true, false);
      verifyCommitMsgs();

      undoRestore(FILENAMES[0]);
      verifyFilesExistance(false, false);
      verifyCommitMsgs();
   }

   @Test
   public void shouldBeAbleToAddAddRevertAdd() throws Exception
   {
      executeForgeCommand(FILENAMES[0]);
      executeForgeCommand(FILENAMES[1]);
      undoRestore(FILENAMES[1]);

      executeForgeCommand(FILENAMES[2]);
      verifyFilesExistance(true, false, true);
      verifyCommitMsgs();
   }

   @Test
   public void shouldBeAbleToAddAddRevertRevertAdd() throws Exception
   {
      executeForgeCommand(FILENAMES[0]);
      executeForgeCommand(FILENAMES[1]);
      undoRestore(FILENAMES[1]);
      undoRestore(FILENAMES[0]);
      verifyFilesExistance(false, false);
      verifyCommitMsgs();

      executeForgeCommand(FILENAMES[2]);
      verifyFilesExistance(false, false, true);
      verifyCommitMsgs();
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

   private void undoRestore(String revertedFile)
   {
      boolean isRestored = myProject.getFacet(UndoFacet.class).undoLastChange();
      Assert.assertTrue("undo failed", isRestored);

      verifyForgeProjectFileExists();
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

   private void verifyCommitMsgs()
   {
      commits = myProject.getFacet(UndoFacet.class).getStoredCommits();
      commitMsgs = extractCommitMsgs(commits);

      Assert.assertEquals("wrong number of commits in the history branch",
               getHistoryBranchSize(), commitMsgs.size());

      if (!commitMsgs.isEmpty())
      {
         Assert.assertEquals("commit messages do not match",
                  UndoFacet.UNDO_STORE_COMMIT_MSG_PREFIX + Strings.enquote(COMMAND_NAME) + " command",
                  commitMsgs.get(0));
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

   private int getHistoryBranchSize()
   {
      return myProject.getFacet(UndoFacet.class).historyBranchSize;
   }
}