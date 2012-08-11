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
import org.jboss.forge.parser.java.util.Strings;

public class RepositoryCommitsMonitor
{
   private Map<String, Integer> commitCounts = new HashMap<String, Integer>();
   private RepositoryCommitState currentState = RepositoryCommitState.NO_CHANGES;
   private String branchWithOneNewCommit = "";
   private String undoBranchName = "";

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
         if(Strings.areEqual(Repository.shortenRefName(branch.getName()), undoBranchName))
            continue;

         RevWalk revWalk = new RevWalk(repo.getRepository());
         revWalk.markStart(revWalk.parseCommit(branch.getObjectId()));
         int commits = countCommits(revWalk.iterator());
         newCommitCounts.put(Repository.shortenRefName(branch.getName()), commits);
      }

      if(commitCounts.isEmpty()) // first check
      {
         commitCounts = newCommitCounts;
         currentState = RepositoryCommitState.MULTIPLE_CHANGED_COMMITS;
         return currentState;
      }

      if(newCommitCounts.size() == commitCounts.size() + 1)
      {
         // special case. New branch was created. Could still be that only 1 commit was added
         // TODO: add support for this in the future

         commitCounts = newCommitCounts;
         currentState = RepositoryCommitState.MULTIPLE_CHANGED_COMMITS;
         return currentState;
      }

      if(newCommitCounts.size() != commitCounts.size()) // compare number of branches
      {
         commitCounts = newCommitCounts;
         currentState = RepositoryCommitState.MULTIPLE_CHANGED_COMMITS;
         return currentState;
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
               currentState = RepositoryCommitState.MULTIPLE_CHANGED_COMMITS;
               return currentState;
            }
         }

         switch (branchesWithOneNewCommit.size())
         {
         case 0:
            currentState = RepositoryCommitState.NO_CHANGES;
            break;
         case 1:
            branchWithOneNewCommit = branchesWithOneNewCommit.iterator().next();
            currentState = RepositoryCommitState.ONE_NEW_COMMIT;
            break;
         default:
            currentState = RepositoryCommitState.MULTIPLE_CHANGED_COMMITS;
            break;
         }

         // replace commitCounts
         commitCounts = newCommitCounts;
         return currentState;
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

   public RepositoryCommitState getCurrentState()
   {
      return currentState;
   }

   public String getBranchWithOneNewCommit()
   {
      return branchWithOneNewCommit;
   }

   public String getUndoBranchName()
   {
      return undoBranchName;
   }

   public void setUndoBranchName(String undoBranchName)
   {
      this.undoBranchName = undoBranchName;
   }

}
