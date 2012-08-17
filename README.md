# plugin undo

This plugin provides a possibility to revert a previously executed command in the [JBoss Forge](http://forge.jboss.org/).

## Usage

### installing the plugin

Undo plugin only works in project scope, so we should create a project first:

```
[no project] forge-projects $ new-project --named forge-demo --topLevelPackage org.forge.demo
```

And simply install the plugin:

```
[forge-demo] forge-demo $ setup undo
```


### Undoing a forge command

Let's create something to work with:

```
[forge-demo] forge-demo $ persistence setup --provider HIBERNATE --container JBOSS_AS7
[forge-demo] forge-demo $ entity --named User
```

Now let's add an attribute name to user:

```
[forge-demo] forge-demo $ field string --named name
```

Here is what we have so far: 
```
[forge-demo] User.java $ ls

[fields]
private::Long::id;                    private::String::name;
private::int::version;                

[methods]
public::equals(Object that)::boolean
public::getId()::Long
public::getName()::String
public::getVersion()::int
public::hashCode()::int
public::setId(final Long id)::void
public::setName(final String name)::void
public::setVersion(final int version)::void
public::toString()::String
```

As we are doing this our boss comes and says that the customer requirements changed and they would like to have username split into 2 attributes: first name and last name. No problem we say! And undo the last command!

```
[forge-demo] User.java $ undo restore
***SUCCESS*** latest forge command is reverted.
[forge-demo] User.java $ ls

[fields]
private::Long::id;                    private::int::version;

[methods]
public::equals(Object that)::boolean
public::getId()::Long
public::getVersion()::int
public::hashCode()::int
public::setId(final Long id)::void
public::setVersion(final int version)::void
public::toString()::String
```

Now we add two attributes and get another coffee.

```
[forge-demo] forge-demo $ field string --named firstName
[forge-demo] forge-demo $ field string --named lastName
```

```
[forge-demo] User.java $ ls

[fields]
private::Long::id;                    private::String::firstName;
private::String::lastName;            private::int::version;

[methods]
public::equals(Object that)::boolean
public::getFirstName()::String
public::getId()::Long
public::getLastName()::String
public::getVersion()::int
public::hashCode()::int
public::setFirstName(final String firstName)::void
public::setId(final Long id)::void
public::setLastName(final String lastName)::void
public::setVersion(final int version)::void
public::toString()::String

```


### Showing stored changes

At any time, `undo list` command could be called to see what is stored in undo buffer:

```
[forge-demo] forge-demo $ undo list
a0c8f6c [*uncommitted*] history-branch: changes introduced by the "field string" command
e96cabb [*uncommitted*] history-branch: changes introduced by the "field string" command
761b5f4 [*uncommitted*] history-branch: changes introduced by the "entity" command
978ee78 [*uncommitted*] history-branch: changes introduced by the "persistence setup" command
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

## undo reset

### resets plugin's history (only works in clean state)

```
$ undo reset
```


# How it works

## Storing changes

plugin-undo stores changes directly in git. It has its own branch for that. Commits on that branch represent changes introduced by a single forge command. Every forge command execution produces a separate commit which could be reverted later. Forge commands are reverted in the same reversed order of their execution (from the most recent to the least recent).

Support for undoing changes separately coming from different branches is planned.


## Working with git

~~Git repository is managed using JGit. plugin-undo has a dependency on forge-git-tools which provide JGit transitively. This way the plugin uses the same version of the JGit as forge.~~

Due to the bug in the jgit-2.0, which breaks the plugin-undo functionality, a custom version of jgit-2.1-snapshot with the bugfix is provided with this plugin. This way, plugin-undo does not use jgit version provided by forge. Also, all references to the GitUtils class from forge-git-tools are currently removed. This is a temprorary measure until jgit-2.1 is released. (planned release date: 28 September 2012)


# Limitations

## Never switch to the history-branch while having a dirty index/working tree

Due to the nature of git, it's highly advisable not to checkout history-branch created by the plugin-undo while having uncommitted changes either in the git index or in the working tree. Git will silently remove all identical files from your index and/or working tree which are already stored on the history-branch.


## plugin-undo creates a commit before reverting changes

To limit the number of merging conflicts and other potential problems, plugin-undo will create a commit, adding all uncommitted and unstaged changes (including untracked files). This way a clean working tree is ensured before reverting the latest change.

Notice, that untracked files are also added. You should either use `git clean -f` before, or be absolutely OK with those files being kept in your repository after the commit is reverted. Use `.gitignore` to keep important files (e.g. IDE `.project` files, etc) untracked.


## undo reset works only when git working tree is clean (contains no uncommitted changes)

Reset runs `git-reset --hard` internally which removes all uncommitted files. To make sure your files will not be lost, reset only works in clean state.


# Changelog

This project uses [Apache Maven](http://maven.apache.org/) for release numbering.

version 1.0.3: reset command, _in development_

version 1.0.2: store and revert multiple commands from different branches and working tree

version 1.0.1: store and revert unlimited commits from the working tree

version 1.0.0: store and revert one commit at a time


# Contributors

Project Lead: Jevgeni Zelenkov


# License

Eclipse Public License (EPL) - v 1.0

See the [License](http://github.com/forge/plugin-undo/blob/master/License) file.
