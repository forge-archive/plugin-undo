#!/bin/bash

git init
echo 'readme' > readme.txt
git add readme.txt
git commit -m 'initial commit'
echo 'system-file1' > sys1.txt
echo 'system-file2' > sys2.txt
git add -A
git commit -m 'plugin-undo install: added all'
git branch history

echo 'one' > one.txt
git add -A
git stash
git checkout history
git stash apply
git commit -m 'history: one'
git checkout master
git stash apply
git stash drop

#echo 'two' > two.txt
#git add -A
#git stash
#git checkout history
#git stash apply
#git commit -m 'history: two'
#git checkout master
#git stash apply
#git stash drop

# undo last change
git commit -m 'plugin-undo: prepare for restore'
git checkout history
git revert --no-edit HEAD
git checkout master
git cherry-pick history

# remove snapshot and it's reverted version in history branch
git checkout history
git reset --hard HEAD~2
git checkout master