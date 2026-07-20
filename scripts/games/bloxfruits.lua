-- morvo/scripts/games/bloxfruits.lua
-- Blox Fruits — auto farm, auto boss, fruit sniper, teleport
print("[Morvo] Blox Fruits script loaded")

local Players = game:GetService("Players")
local LocalPlayer = Players.LocalPlayer
local VirtualInputManager = game:GetService("VirtualInputManager")

-- Auto Farm
local function autoFarmBoss(bossName)
    -- Find nearest boss
    local nearest, nearestDist = nil, math.huge
    for _, obj in pairs(workspace.Enemies:GetChildren()) do
        if obj.Name == bossName and obj:FindFirstChild("Humanoid") 
           and obj.Humanoid.Health > 0 then
            local dist = (LocalPlayer.Character.HumanoidRootPart.Position 
                         - obj.HumanoidRootPart.Position).Magnitude
            if dist < nearestDist then
                nearest = obj
                nearestDist = dist
            end
        end
    end
    
    if nearest then
        -- Teleport to boss
        LocalPlayer.Character.HumanoidRootPart.CFrame = 
            nearest.HumanoidRootPart.CFrame * CFrame.new(0, 0, 5)
        -- Auto attack
        VirtualInputManager:SendMouseButtonEvent(0, 0, 0, true, game, 0)
        task.wait(0.1)
        VirtualInputManager:SendMouseButtonEvent(0, 0, 0, false, game, 0)
    end
end

-- Fruit Sniper
-- Monitors for rare fruits spawning and auto-collects
task.spawn(function()
    while true do
        for _, obj in pairs(workspace:GetDescendants()) do
            if obj.Name == "Fruit" and obj:IsA("Tool") then
                -- Teleport and collect
                firetouchinterest(LocalPlayer.Character.HumanoidRootPart, obj.Handle, 0)
                firetouchinterest(LocalPlayer.Character.HumanoidRootPart, obj.Handle, 1)
            end
        end
        task.wait(1)
    end
end)

print("[Morvo] Blox Fruits: Boss farm + Fruit sniper active")