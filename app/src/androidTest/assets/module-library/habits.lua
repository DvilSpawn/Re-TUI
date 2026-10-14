-- name = "Daily Habits"
-- type = "module"
-- retui = "1"
-- version = "1.0.1"
-- author = "Re:TUI"
-- description = "Track daily habits with a checklist that starts fresh each day."
-- license = "MIT"
-- catalogue_reference = "https://github.com/SimonSchubert/omarchy-widgets/tree/b1a799ec0aef4865871fed1c4e1d9a8c1d3ad93e/widgets/simonschubert/habits"
-- permissions = ""

local prefs = require "prefs"
local date = require "date"
local function render()
    local today = date:format("%Y-%m-%d")
    if prefs.day ~= today then prefs.day = today; prefs.done = {} end
    ui:set_title("Daily Habits")
    local lines, done = {}, prefs.done or {}
    for name in (prefs.names or "Read\nExercise\nDrink water"):gmatch("[^\r\n]+") do
        lines[#lines + 1] = (done[name] and "[x] " or "[ ] ") .. name
        ui:suggest_action((done[name] and "Undo " or "Done ") .. name, "toggle:" .. name)
    end
    ui:show_text(today .. "\n\n" .. (#lines == 0 and "Use Edit to add habits." or table.concat(lines,"\n")))
end
function on_load() render() end
function on_resume() render() end
function on_action(action)
    render()
    local name = action:match("^toggle:(.+)$")
    if name then local done=prefs.done or {}; done[name]=not done[name]; prefs.done=done end
    ui:clear(); render()
end
function on_config()
    ui:show_config({title="Daily Habits",fields={{id="names",label="One habit per line",type="textarea",value=prefs.names or "Read\nExercise\nDrink water",lines=6}},buttons={{id="save",label="Save",primary=true}}})
end
function on_config_submit(action, values)
    if action == "save" then prefs.names=values.names or "" end
    render()
end
