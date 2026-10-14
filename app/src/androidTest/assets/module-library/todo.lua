-- name = "Todo"
-- type = "module"
-- retui = "1"
-- version = "1.0.1"
-- author = "Re:TUI"
-- description = "Keep a task list, check off finished items and undo the last completion."
-- license = "MIT"
-- catalogue_reference = "https://github.com/SimonSchubert/omarchy-widgets/tree/b1a799ec0aef4865871fed1c4e1d9a8c1d3ad93e/widgets/simonschubert/todo"
-- permissions = ""

local prefs = require "prefs"
local function render()
    ui:set_title("Todo")
    local lines, count = {}, 0
    for line in (prefs.items or ""):gmatch("[^\r\n]+") do
        count = count + 1
        lines[#lines + 1] = tostring(count) .. ". " .. line
        ui:suggest_action("Done " .. count, "done:" .. count)
    end
    if prefs.undo_text then ui:suggest_action("Undo last", "undo") end
    ui:show_text(#lines == 0 and "All done. Use Edit to add tasks." or table.concat(lines, "\n"))
end
function on_load() render() end
function on_resume() render() end
function on_action(action)
    local selected = tonumber(action:match("^done:(%d+)$"))
    if selected then
        local kept, index = {}, 0
        for line in (prefs.items or ""):gmatch("[^\r\n]+") do
            index = index + 1
            if index ~= selected then kept[#kept + 1] = line
            else prefs.undo_text = line; prefs.undo_index = index end
        end
        prefs.items = table.concat(kept, "\n")
    end
    if action == "undo" and prefs.undo_text then
        local items = {}
        for line in (prefs.items or ""):gmatch("[^\r\n]+") do items[#items+1]=line end
        table.insert(items,math.min(tonumber(prefs.undo_index) or 1,#items+1),prefs.undo_text)
        prefs.items=table.concat(items,"\n")
        prefs.undo_text=nil; prefs.undo_index=nil
    end
    render()
end
function on_config()
    ui:show_config({title="Todo", fields={{id="items",label="One task per line",type="textarea",value=prefs.items or "",lines=6}},buttons={{id="save",label="Save",primary=true}}})
end
function on_config_submit(action, values)
    if action == "save" then prefs.items = values.items or ""; prefs.undo_text=nil; prefs.undo_index=nil end
    render()
end
