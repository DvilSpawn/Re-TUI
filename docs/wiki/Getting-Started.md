# Getting Started

This page is for orientation, not a guided tour. Re:T-UI is built for users who want a command-first Android workstation surface: quiet, local, configurable, and fast once your own aliases and modules are in place.

## First Launch

When Re:T-UI opens, you are dropped into a terminal-style launcher. You can:

- type commands directly
- launch apps by name
- use suggestions under the input field
- open the settings hub with `settings`
- open the companion file console with `files`

## Learn From Help

Type:

`help`

That prints the command list and the workstation quickstart. For details about one command, type:

`help <command>`

Examples:

- `help alias`
- `help apps`
- `help wallpaper`
- `help module`

## Optional Guide

Run:

`guide`

The guide is a non-blocking walkthrough. It prints paths in the terminal and, when active, uses the normal suggestion row for the current step. It does not show a first-run modal.

While a guide is active, guide suggestions take over the suggestion row. Use `guide -off` to return to normal app, alias, module, and command suggestions.

Useful paths:

- `guide -start basics`
- `guide -start customize`
- `guide -start modules`

Controls:

- `guide -next`
- `guide -back`
- `guide -off`
- `guide -resume`
- `guide -restart basics`

## Basic Navigation

- `settings` opens the settings hub.
- `files` opens Re:T-UI Files.
- `module -ls` lists built-in and script-backed modules.
- `termux -setup` prints the Termux setup checklist.
- `tbridge -doctor` checks the Termux bridge used by scripts and modules.

## Important Concepts

### Commands

Commands are the fastest way to operate the launcher once you know them.

### Config Files

Power users can still edit launcher behavior through XML and text files in the Re:T-UI folder.

### Presets

Presets let you save a theme state and reuse it later.

### Auto Color

Auto color derives colors from the current wallpaper, but it is separate from manually saved presets.

## Good First Commands

- `help`
- `guide -start basics`
- launch an app by typing its name
- `alias -add ll apps -ls`
- `apps -hide <app>`
- `wallpaper -auto`
- `preset -save <name>`
- `module -ls`
- `files`

## Tip

If something visual does not refresh immediately after a major theme change, run:

`restart`
