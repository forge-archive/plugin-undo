#!/bin/bash

# git init
git init
echo 'readme' > readme.txt
git add readme.txt
git commit -m 'initial commit'

# introduce some changes
echo 'system-file1' > sys1.txt
echo 'system-file2' > sys2.txt

# install plugin-undo
git add -A
git commit -m 'plugin-undo install: added all'
git branch history

# forge-command changes working tree
echo 'one' > one.txt

# plugin-undo's historyBranchUpdater adds a diff into history branch
git add -A
git stash
git checkout history
git stash apply
git commit -m 'history snapshot: one'
git checkout master
git stash apply
git stash drop

# undo last change (requires clean working tree)
git commit -m 'plugin-undo: prepare for restore'
git checkout history
git revert --no-edit HEAD
git checkout master
git cherry-pick history

# remove snapshot and it's reverted version inside of history branch
git checkout history
git reset --hard HEAD~2
git checkout master
