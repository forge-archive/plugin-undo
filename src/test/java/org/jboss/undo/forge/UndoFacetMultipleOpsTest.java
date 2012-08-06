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

   private static Project myProject = null;
   private static DirectoryResource dir = null;
   private static Iterable<RevCommit> commits = null;
   private static List<String> commitMsgs = null;
   private static FileResource<?> file = null;

   @Before
   public void setUp() throws Exception
   {
      myProject = initializeJavaProject();
      dir = myProject.getProjectRoot();
      getShell().execute("undo setup");
   }

   @After
   public void destroy()
   {
      myProject = null;
      dir = null;
      commits = null;
      commitMsgs = null;
      file = null;
   }

   @Test
   public void shouldAddTwoChangesIntoUndoBranch() throws Exception
   {
      String[] filenames = { "test1.txt", "test2.txt" };

      executeForgeCommand(filenames[0]);
      executeForgeCommand(filenames[1]);
      // TODO:
      // verify test1.txt exists
      // verify test2.txt exists

      // for (Resource<?> each : dir.listResources())
      // System.err.println(each.getFullyQualifiedName());

      // File fileOne = new File(dir.getFullyQualifiedName() + filenames[0]);
      // Assert.assertTrue("first file should also exist", fileOne.exists());

   }

   @Test
   public void shouldUndoTwoLastChanges() throws Exception
   {
      String[] filenames = { "test1.txt", "test2.txt" };

      executeForgeCommand(filenames[0]);
      executeForgeCommand(filenames[1]);

      undoRestore(filenames[1]);
      // TODO:
      // verify test1.txt exists
      // verify test2.txt doesn't exist

      undoRestore(filenames[0]);
      // TODO:
      // verify test1.txt doesn't exist
      // verify test2.txt doesn't exist
   }

   @Test
   public void shouldBeAbleToAddAddRevertAdd() throws Exception
   {
      String[] filenames = { "test1.txt", "test2.txt", "test3.txt" };

      executeForgeCommand(filenames[0]);
      executeForgeCommand(filenames[1]);

      undoRestore(filenames[1]);

      executeForgeCommand(filenames[2]);
      // TODO:
      // verify test1.txt exists
      // verify test2.txt doesn't exist
      // verify test3.txt exists
   }

   // helper methods
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

   private void executeForgeCommand(String filename)
            throws Exception
   {
      file = dir.getChild(filename).reify(FileResource.class);
      Assert.assertFalse("file already exists!", file.exists());

      String command = COMMAND_NAME + " " + filename;
      getShell().execute(command);

      file = dir.getChild(filename).reify(FileResource.class);
      Assert.assertTrue("file doesn't exist", file.exists());

      // verify commit is added into history branch
      commits = myProject.getFacet(UndoFacet.class).getStoredCommits();
      commitMsgs = extractCommitMsgs(commits);

      Assert.assertEquals("wrong number of commits in the history branch",
               getHistoryBranchSize(), commitMsgs.size());

      if (!commitMsgs.isEmpty())
      {
         Assert.assertEquals("commit messages do not match",
                  UndoFacet.UNDO_STORE_COMMIT_MSG_PREFIX + Strings.enquote(COMMAND_NAME) +
                           " command",
                  commitMsgs.get(0));
      }
   }

   private void undoRestore(String revertedFile)
   {
      boolean isRestored = myProject.getFacet(UndoFacet.class).undoLastChange();
      Assert.assertTrue("undo failed", isRestored);

      file = dir.getChild(revertedFile).reify(FileResource.class);

      Assert.assertFalse("file should not exist", file.exists());
      commits = myProject.getFacet(UndoFacet.class).getStoredCommits();
      commitMsgs = extractCommitMsgs(commits);

      verifyForgeProjectFileExists();

      Assert.assertEquals("wrong number of commits in the history branch",
               getHistoryBranchSize(), commitMsgs.size());

      if (!commitMsgs.isEmpty())
      {
         Assert.assertEquals("commit messages do not match",
                  UndoFacet.UNDO_STORE_COMMIT_MSG_PREFIX + Strings.enquote(COMMAND_NAME) + " command",
                  commitMsgs.get(0));
      }
   }

   private void verifyForgeProjectFileExists()
   {
      File forgeXml = new File(dir.getFullyQualifiedName() + "/src/main/resources/META-INF/forge.xml");
      Assert.assertTrue("forge project file doesn't exist anymore", forgeXml.exists());
   }

   private int getHistoryBranchSize()
   {
      return myProject.getFacet(UndoFacet.class).historyBranchSize;
   }
}