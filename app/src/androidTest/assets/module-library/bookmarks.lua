-- name = "Bookmarks"
-- type = "module"
-- retui = "1"
-- version = "1.0.1"
-- author = "Re:TUI"
-- description = "Keep named HTTPS links together and open them in your browser."
-- license = "MIT"
-- catalogue_reference = "https://github.com/SimonSchubert/omarchy-widgets/tree/b1a799ec0aef4865871fed1c4e1d9a8c1d3ad93e/widgets/hoangitk/bookmarks"
-- permissions = "intents"

local prefs = require "prefs"
local intents = require "intents"
local function valid_url(url)
    if not url or url:sub(1,8):lower() ~= "https://" then return nil end
    url = "https://" .. url:sub(9)
    local authority = url:sub(9):match("^([^/?#]+)")
    if not authority then return nil end
    local host, port = authority:match("^([%w%.%-]+):(%d+)$")
    if not host then host = authority:match("^([%w%.%-]+)$") end
    if not host or not host:match("^[%w]") or not host:match("[%w]$") or host:find("..",1,true) then return nil end
    for label in host:gmatch("[^.]+") do
        if #label > 63 or not label:match("^[%w]") or not label:match("[%w]$") then return nil end
    end
    if port and (tonumber(port) < 1 or tonumber(port) > 65535) then return nil end
    return url
end
local function render()
    ui:set_title("Bookmarks")
    local lines = {}
    for line in (prefs.links or "Re:TUI | https://github.com/DvilSpawn/Re-TUI"):gmatch("[^\r\n]+") do
        local name, url = line:match("^%s*(.-)%s*|%s*(%S+)%s*$")
        url = valid_url(url)
        if name and name ~= "" and url then
            lines[#lines+1]=name .. "\n" .. url
            ui:suggest_command(name,intents:view(url))
        else lines[#lines+1]="Invalid link: " .. line end
    end
    ui:show_text(#lines == 0 and "Use Edit to add links." or table.concat(lines,"\n\n"))
end
function on_load() render() end
function on_resume() render() end
function on_config()
    ui:show_config({title="Bookmarks",fields={{id="links",label="One Name | https://url per line",type="textarea",value=prefs.links or "Re:TUI | https://github.com/DvilSpawn/Re-TUI",lines=6}},buttons={{id="save",label="Save",primary=true}}})
end
function on_config_submit(action, values)
    if action == "save" then prefs.links=values.links or "" end
    render()
end
