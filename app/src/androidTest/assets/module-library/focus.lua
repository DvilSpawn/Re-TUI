-- name = "Focus Timer"
-- type = "module"
-- retui = "1"
-- version = "1.0.1"
-- author = "Re:TUI"
-- description = "Start a 5–60 minute focus session and see the time remaining."
-- license = "MIT"
-- catalogue_reference = "https://github.com/SimonSchubert/omarchy-widgets/tree/b1a799ec0aef4865871fed1c4e1d9a8c1d3ad93e/widgets/simonschubert/focus"
-- permissions = "active-tick"

local prefs = require "prefs"
local date = require "date"
local fmt = require "fmt"
local function render()
    local minutes = tonumber(prefs.minutes) or 25
    local remaining = math.max(0, (tonumber(prefs.ends) or 0) - date:seconds())
    ui:set_title("Focus Timer")
    if remaining > 0 then
        ui:show_text(string.format("%02d:%02d remaining",math.floor(remaining/60),remaining%60) .. "\n" .. fmt:progress_bar(remaining,minutes*60,20))
        ui:suggest_action("Stop", "stop")
        ui:set_tick_interval(1)
    else
        ui:show_text((tonumber(prefs.ends) or 0) > 0 and "Session complete." or (minutes .. " minute focus session"))
        ui:suggest_action("Start", "start")
        ui:disable_tick()
    end
end
function on_load() render() end
function on_resume() render() end
function on_tick() render() end
function on_action(action)
    if action == "start" then prefs.ends=date:seconds()+(tonumber(prefs.minutes) or 25)*60 end
    if action == "stop" then prefs.ends=0 end
    render()
end
function on_config()
    ui:show_config({title="Focus Timer",fields={{id="minutes",label="Minutes (5–60)",type="number",value=prefs.minutes or 25,min=5,max=60}},buttons={{id="save",label="Save",primary=true}}})
end
function on_config_submit(action, values)
    if action == "save" then prefs.minutes=math.max(5,math.min(60,tonumber(values.minutes) or 25)); prefs.ends=0 end
    render()
end
