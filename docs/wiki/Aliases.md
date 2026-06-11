# Aliases

Aliases are one of the most important quality-of-life features in Re:T-UI.

They let you turn a longer command sequence into a short command that feels natural to type every day.

## Why Aliases Matter

Aliases keep the launcher command-first without forcing you to retype everything the hard way.

They are useful for:

- shortening repetitive commands
- creating your own vocabulary for common actions
- keeping roleplay flavor in the launcher
- building a more personal prompt-and-command experience

## Main Commands

- `alias -add <name> <command content>`
- `alias -add -a <name> <command content>`
- `alias -add -s <name> <script path>`
- `alias -rm <name>`
- `alias -ls`
- `alias -file`

## Examples

### Open a favorite app quickly

`alias -add yt open "YouTube Music"`

Now you can just type:

`yt`

### Save a common search flow

`alias -add bughunt search -gg Re-T-UI notifications`

Now:

`bughunt`

### Make the launcher feel more personal

`alias -add home apps -ls`

Now:

`home`

### Add a Termux script alias

`alias -add -s test /data/data/com.termux/files/home/retui/test.sh`

Now:

`termux -run test`

The `-s` scope means the alias appears where script aliases make sense, such as after `termux -run`.

### Open a tmux workspace tool

`alias -add MC tmux launch mc`

Now:

`MC`

opens the tmux workspace and launches Midnight Commander in a named tmux window. Running `MC` again focuses the existing `mc` window instead of creating another one.

You can also alias existing tmux windows by index:

```text
alias -add BASH2 tmux switch 2
```

Visible tokens such as `2:bash` also work:

```text
alias -add BASH2 tmux switch 2:bash
```

Use `tmux launch <id>` for workspace launchers and `tmux switch <window>` for already-open tmux windows.

## Workflow Aliases

Aliases can also chain multiple Re:T-UI commands. The default command separator is `;`.

This makes aliases the official workflow layer for Re:T-UI. A workflow alias is not a separate recipe system; it is just an inspectable alias that expands into several normal commands.

Examples:

```text
alias -add focus module -show timer; timer 25m; notifications -off
```

```text
alias -add dev module -show server; termux -run status; apps -lsgp dev
```

```text
alias -add morning module -show calendar; module -show notifications; wallpaper -auto
```

```text
alias -add privacy notifications -off; apps -hide Instagram; apps -hide YouTube
```

```text
alias -add commute module -show music; music -play; module -show notifications
```

Run one by typing its name:

```text
focus
```

Use `alias -ls` or `alias -file` to inspect what a workflow alias does before trusting it.

## Where Aliases Live

Aliases are stored in the launcher alias file and can also be opened directly with:

`alias -file`

This keeps them visible and editable for power users.

## How They Fit Re:T-UI

Aliases are part of what makes Re:T-UI feel like a terminal you live in instead of a command shell you visit occasionally.

They let users:

- shape the launcher around their habits
- hide complexity behind memorable words
- stay in the command flow instead of reaching for UI every time

## Recommended Alias Style

The best aliases are:

- short
- obvious to you
- tied to a real repeated action

Good examples:

- `yt`
- `mail`
- `favapps`
- `work`
- `night`

Less useful aliases are the ones you invent once and never remember again.

## Suggested Workflow

1. Notice a command you type often
2. Turn it into an alias
3. Use `alias -ls` occasionally to clean house
4. Remove aliases that no longer match how you use the launcher

## Future Possibilities

Aliases are also a natural place for future interactive flows, like confirmation prompts or protected commands.

That is not the main user flow today, but aliases are already the right conceptual home for that kind of power-user behavior.

Deferred alias polish:

- better `alias -ls` formatting
- `alias -show <name>`
- `alias -run <name>` only if direct alias execution becomes unclear
- warnings if an alias chain contains destructive commands
