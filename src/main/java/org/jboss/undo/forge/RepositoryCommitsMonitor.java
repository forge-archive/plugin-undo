package org.jboss.undo.forge;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.jboss.forge.jgit.api.Git;
import org.jboss.forge.jgit.api.errors.GitAPIException;
import org.jboss.forge.jgit.errors.IncorrectObjectTypeException;
import org.jboss.forge.jgit.errors.MissingObjectException;
import org.jboss.forge.jgit.lib.Ref;
import org.jboss.forge.jgit.lib.Repository;
import org.jboss.forge.jgit.revwalk.RevCommit;
import org.jboss.forge.jgit.revwalk.RevWalk;

public class RepositoryCommitsMonitor
{
   private Map<String, Integer> commitCounts = new HashMap<String, Integer>();
   private RepositoryCommitState currentStatus = RepositoryCommitState.NO_CHANGES;
   private String branchWithOneNewCommit = "";

   public enum RepositoryCommitState{
      NO_CHANGES, ONE_NEW_COMMIT, MULTIPLE_CHANGED_COMMITS
   }

   public RepositoryCommitState updateCommitCounters(Git repo) throws GitAPIException, MissingObjectException, IncorrectObjectTypeException, IOException
   {
      this.branchWithOneNewCommit = "";

      // get all localbranches
      List<Ref> localBranches = repo.branchList().call();

      // get the number of commits for each branch
      Map<String, Integer> newCommitCounts = new HashMap<String, Integer>();
      for(Ref branch : localBranches)
      {
         RevWalk revWalk = new RevWalk(repo.getRepository());
         revWalk.markStart(revWalk.parseCommit(branch.getObjectId()));
         int commits = countCommits(revWalk.iterator());
         newCommitCounts.put(Repository.shortenRefName(branch.getName()), commits);
      }

      if(commitCounts.isEmpty()) // first check
      {
         commitCounts = newCommitCounts;
         currentStatus = RepositoryCommitState.MULTIPLE_CHANGED_COMMITS;
         return currentStatus;
      }

      if(localBranches.size() == commitCounts.size() + 1)
      {
         // special case. New branch was created. Could still be that only 1 commit was added
         // TODO: add support for this in the future

         commitCounts = newCommitCounts;
         currentStatus = RepositoryCommitState.MULTIPLE_CHANGED_COMMITS;
         return currentStatus;
      }

      if(newCommitCounts.size() != commitCounts.size()) // compare number of branches
      {
         commitCounts = newCommitCounts;
         currentStatus = RepositoryCommitState.MULTIPLE_CHANGED_COMMITS;
         return currentStatus;
      }
      else // same number of branches. Check the number of commits on each branch
      {
         Set<String> branchesWithOneNewCommit = new HashSet<String>();
         for(Entry<String, Integer> oldCommitCount : commitCounts.entrySet())
         {
            String branchName = oldCommitCount.getKey();
            int diff = newCommitCounts.get(branchName) - oldCommitCount.getValue();

            if(diff == 1)
            {
               branchesWithOneNewCommit.add(branchName);
            }
            else if(diff < 0 || diff > 1)
            {
               commitCounts = newCommitCounts;
               currentStatus = RepositoryCommitState.MULTIPLE_CHANGED_COMMITS;
               return currentStatus;
            }
         }

         switch (branchesWithOneNewCommit.size())
         {
         case 0:
            currentStatus = RepositoryCommitState.NO_CHANGES;
            break;
         case 1:
            this.branchWithOneNewCommit = branchesWithOneNewCommit.iterator().next();
            currentStatus = RepositoryCommitState.ONE_NEW_COMMIT;
            break;
         default:
            currentStatus = RepositoryCommitState.MULTIPLE_CHANGED_COMMITS;
            break;
         }

         // replace commitCounts
         commitCounts = newCommitCounts;
         return currentStatus;
      }
   }

   private int countCommits(Iterator<RevCommit> iterator)
   {
      int ret = 0;
      while(iterator.hasNext())
      {
         ret++;
         iterator.next();
      }
      return ret;
   }

   public RepositoryCommitState getCurrentStatus()
   {
      return currentStatus;
   }

   public String getBranchWithOneNewCommit()
   {
      return branchWithOneNewCommit;
   }

}
