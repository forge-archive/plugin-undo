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

import java.io.IOException;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.forge.jgit.api.Git;
import org.jboss.forge.jgit.api.ResetCommand.ResetType;
import org.jboss.forge.jgit.lib.ObjectId;
import org.jboss.forge.jgit.lib.RepositoryBuilder;
import org.jboss.forge.project.Project;
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
public class RepositoryCommitsMonitorTest extends AbstractShellTest
{
   private static final String BRANCH_MASTER = "master";
   private static final String BRANCH_TWO = "other";
   private static final String BRANCH_THREE = "foo";
   private static final String[] FILENAMES = { "one.txt", "two.txt", "three.txt" };

   private static Project myProject = null;
   private static Git git = null;
   private static RepositoryCommitsMonitor monitor = null;
   private static RepositoryCommitState state = null;

   @Deployment
   public static JavaArchive getDeployment()
   {
      return AbstractShellTest.getDeployment().addPackages(true, RepositoryCommitsMonitor.class.getPackage());
   }

   @Before
   public void setUp() throws Exception
   {
      myProject = initializeJavaProject();
      getShell().execute("git setup");
      git = getGit(myProject);
      getShell().execute("touch README");
      git.add().addFilepattern("README").call();
      git.commit().setMessage("initial commit").call();
      monitor = new RepositoryCommitsMonitor();
   }

   @After
   public void destroy() throws Exception
   {
      myProject = null;
      git = null;
      monitor = null;
      state = null;
   }

   @Test
   public void testEmptyRepo() throws Exception
   {
      state = monitor.updateCommitCounters(git);
      verifyMultipleChanges();
   }

   @Test
   public void testOneBranchNoNewCommits() throws Exception
   {
      monitor.updateCommitCounters(git);
      state = monitor.updateCommitCounters(git);
      verifyNoChanges();
   }

   @Test
   public void testOneBranchOneNewCommit() throws Exception
   {
      monitor.updateCommitCounters(git);
      commitNewFile(FILENAMES[0]);

      state = monitor.updateCommitCounters(git);
      verifyOneChangeOnBranch(BRANCH_MASTER);
   }

   @Test
   public void testOneBranchMultipleChanges() throws Exception
   {
      monitor.updateCommitCounters(git);
      commitNewFile(FILENAMES[0]);
      commitNewFile(FILENAMES[1]);

      state = monitor.updateCommitCounters(git);
      verifyMultipleChanges();
   }

   @Test
   public void testOneBranchTwoMonitoredChanges() throws Exception
   {
      monitor.updateCommitCounters(git);
      commitNewFile(FILENAMES[0]);
      monitor.updateCommitCounters(git);
      commitNewFile(FILENAMES[1]);

      state = monitor.updateCommitCounters(git);
      verifyOneChangeOnBranch(BRANCH_MASTER);
   }

   @Test
   public void testTwoBranchesNoChanges() throws Exception
   {
      monitor.updateCommitCounters(git);

      git.branchCreate().setName(BRANCH_TWO).call();
      state = monitor.updateCommitCounters(git);
      verifyMultipleChanges();
   }

   @Test
   public void testTwoBranchesOneChangeOnMaster() throws Exception
   {
      monitor.updateCommitCounters(git);

      git.branchCreate().setName(BRANCH_TWO).call();
      state = monitor.updateCommitCounters(git);
      verifyMultipleChanges();

      commitNewFile(FILENAMES[0]);
      state = monitor.updateCommitCounters(git);
      verifyOneChangeOnBranch(BRANCH_MASTER);
   }

   @Test
   public void testTwoBranchesOneChangeOnOtherBranch() throws Exception
   {
      monitor.updateCommitCounters(git);

      git.branchCreate().setName(BRANCH_TWO).call();
      monitor.updateCommitCounters(git);

      git.checkout().setName(BRANCH_TWO).call();
      commitNewFile(FILENAMES[0]);
      git.checkout().setName(BRANCH_MASTER).call();
      state = monitor.updateCommitCounters(git);
      verifyOneChangeOnBranch(BRANCH_TWO);
   }

   @Test
   public void testTwoBranchesTwoChangesOnOneBranch() throws Exception
   {
      monitor.updateCommitCounters(git);
      git.branchCreate().setName(BRANCH_TWO).call();
      monitor.updateCommitCounters(git);

      commitNewFile(FILENAMES[0]);
      commitNewFile(FILENAMES[1]);
      state = monitor.updateCommitCounters(git);
      verifyMultipleChanges();
   }

   @Test
   public void testTwoBranchesTwoChangesOnDiffeentBranches() throws Exception
   {
      monitor.updateCommitCounters(git);
      git.branchCreate().setName(BRANCH_TWO).call();
      monitor.updateCommitCounters(git);

      commitNewFile(FILENAMES[0]);

      git.checkout().setName(BRANCH_TWO).call();
      commitNewFile(FILENAMES[1]);
      git.checkout().setName(BRANCH_MASTER).call();

      state = monitor.updateCommitCounters(git);
      verifyMultipleChanges();

      // do nothing
      state = monitor.updateCommitCounters(git);
      verifyNoChanges();
   }

   @Test
   public void testTwoBranchesOneNewBranch() throws Exception
   {
      monitor.updateCommitCounters(git);
      git.branchCreate().setName(BRANCH_TWO).call();
      monitor.updateCommitCounters(git);

      git.branchCreate().setName(BRANCH_THREE).call();
      state = monitor.updateCommitCounters(git);
      verifyMultipleChanges();
   }

   @Test
   public void testMerge() throws Exception
   {
      monitor.updateCommitCounters(git);
      git.branchCreate().setName(BRANCH_TWO).call();
      monitor.updateCommitCounters(git);

      commitNewFile(FILENAMES[0]);
      git.checkout().setName(BRANCH_TWO).call();
      commitNewFile(FILENAMES[1]);
      git.checkout().setName(BRANCH_MASTER).call();
      monitor.updateCommitCounters(git);

      git.merge().include(git.getRepository().getRef(BRANCH_TWO)).call();
      state = monitor.updateCommitCounters(git);
      verifyMultipleChanges();
   }

   @Test
   public void testResetOneCommit() throws Exception
   {
      monitor.updateCommitCounters(git);

      commitNewFile(FILENAMES[0]);
      monitor.updateCommitCounters(git);

      ObjectId headOne = git.getRepository().resolve("HEAD~1");
      git.reset().setRef(headOne.getName()).setMode(ResetType.HARD).call();
      state = monitor.updateCommitCounters(git);
      verifyMultipleChanges();
   }

   @Test
   public void testResetTwoCommits() throws Exception
   {
      monitor.updateCommitCounters(git);

      commitNewFile(FILENAMES[0]);
      commitNewFile(FILENAMES[2]);
      monitor.updateCommitCounters(git);

      ObjectId headTwo = git.getRepository().resolve("HEAD~2");
      git.reset().setRef(headTwo.getName()).setMode(ResetType.HARD).call();
      state = monitor.updateCommitCounters(git);
      verifyMultipleChanges();
   }

   @Test
   public void testDeleteBranch() throws Exception
   {
      monitor.updateCommitCounters(git);

      git.branchCreate().setName(BRANCH_TWO).call();
      monitor.updateCommitCounters(git);

      git.branchDelete().setBranchNames(BRANCH_TWO).setForce(true).call();
      state = monitor.updateCommitCounters(git);
      verifyMultipleChanges();
   }

   private Git getGit(Project project) throws IOException
   {
      RepositoryBuilder db = new RepositoryBuilder().findGitDir(project.getProjectRoot().getUnderlyingResourceObject());
      Git repo = new Git(db.build());
      return repo;
   }

   private void commitNewFile(String fname) throws Exception
   {
      getShell().execute("touch " + fname);
      git.add().addFilepattern(fname).call();
      git.commit().setMessage("file added: " + fname).call();
   }

   private void verifyNoChanges()
   {
      Assert.assertEquals("state doesn't match", state, RepositoryCommitState.NO_CHANGES);
      String branchWithNewCommit = monitor.getBranchWithOneNewCommit();
      Assert.assertEquals("should be empty", "", branchWithNewCommit);
   }

   private void verifyOneChangeOnBranch(String branchName)
   {
      Assert.assertEquals("state doesn't match", state, RepositoryCommitState.ONE_NEW_COMMIT);
      String branchWithNewCommit = monitor.getBranchWithOneNewCommit();
      Assert.assertEquals("new commit should be on master branch", branchName, branchWithNewCommit);
   }

   private void verifyMultipleChanges()
   {
      Assert.assertEquals("state doesn't match", state, RepositoryCommitState.MULTIPLE_CHANGED_COMMITS);
      String branchWithNewCommit = monitor.getBranchWithOneNewCommit();
      Assert.assertEquals("should be empty", "", branchWithNewCommit);
   }

}
