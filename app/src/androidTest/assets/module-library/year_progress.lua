-- name = "Year Progress"
-- type = "module"
-- retui = "1"
-- version = "1.0.1"
-- author = "Re:TUI"
-- description = "See how much of the day, week, month and year has passed."
-- license = "MIT"
-- catalogue_reference = "https://github.com/SimonSchubert/omarchy-widgets/tree/b1a799ec0aef4865871fed1c4e1d9a8c1d3ad93e/widgets/simonschubert/year-progress"
-- permissions = "active-tick"

local date = require "date"
local fmt = require "fmt"
local function render()
    local p = date:parts()
    local leap = p.year%4==0 and (p.year%100~=0 or p.year%400==0)
    local days = {31,leap and 29 or 28,31,30,31,30,31,31,30,31,30,31}
    local day = (p.hour*3600+p.minute*60+p.second)/86400
    local passed = p.day-1
    for month=1,p.month-1 do passed=passed+days[month] end
    local bars={{"Day",day},{"Week",((p.weekday+5)%7+day)/7},{"Month",(p.day-1+day)/days[p.month]},{"Year",(passed+day)/(leap and 366 or 365)}}
    local lines={}
    for _,bar in ipairs(bars) do
        lines[#lines+1]=fmt:pad_right(bar[1],5) .. " " .. fmt:pad_left(fmt:fixed(bar[2]*100,1),5) .. "%  " .. fmt:progress_bar(bar[2]*100,100,10)
    end
    ui:set_title("Year Progress · " .. p.year)
    ui:show_text(table.concat(lines,"\n"))
    ui:set_tick_interval(60)
end
function on_load() render() end
function on_resume() render() end
function on_tick() render() end
