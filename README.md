# plugin undo

This plugin provides a possibility to revert a previously executed command in the [JBoss Forge](http://forge.jboss.org/).

## Usage
```
TODO: add some meaningful examples
```

# API

## undo setup [--branchName name]

### Installs the the plugin-undo for forge

```
$ undo setup

$ undo setup --branchName forge-history
```

Does the following:
* makes sure that forge's GitFacet is installed
* initializes a non-initialized git repository
* commits everything to have a clean state
* creates a history-branch to store commits

It is possible to specify a custom branch name using an optional `--branchName` argument

## undo restore

### reverts changes introduced by the executed command

```
$ undo restore
```

## undo list

### shows commands stored in the undo plugin's history

```
$ undo list
```


# How it works

## Storing changes

plugin-undo stores changes directly in git. It has its own branch for that. Commits on that branch represent changes introduced by a single forge command. Every forge command execution produces a separate commit which could be reverted later. Forge commands are reverted in the same reversed order of their execution (from the most recent to the least recent).

Support for undoing changes separately coming from different branches is planned.


## Working with git

~~Git repository is managed using JGit. plugin-undo has a dependency on forge-git-tools which provide JGit transitively. This way the plugin uses the same version of the JGit as forge.~~

Due to the bug in the jgit-2.0, which breaks the plugin-undo functionality, a custom version of jgit-2.1-snapshot with the bugfix is provided with this plugin. This way, plugin-undo does not use jgit version provided by forge. Also, all references to the GitUtils class from forge-git-tools are currently removed. This is a temprorary measure until jgit-2.1 is released. (planned release date: 28 September 2012)


# Changelog

This project uses [Apache Maven](http://maven.apache.org/) for release numbering.

version 1.0.1: store and revert unlimited commits from the working tree

version 1.0.0: store and revert one commit at a time

# Contributors

Project Lead: Jevgeni Zelenkov


# License

Eclipse Public License (EPL) - v 1.0

See the [License](http://github.com/forge/plugin-undo/blob/master/License) file.
