-- morvo/scripts/games/bladball.lua
-- Blade Ball — Auto-Parry
print("[Morvo] Blade Ball auto-parry loaded")

local Players = game:GetService("Players")
local RunService = game:GetService("RunService")
local LocalPlayer = Players.LocalPlayer
local VIM = game:GetService("VirtualInputManager")

local CONFIG = {
    Enabled = false,
    ParryRange = 15,
    ToggleKey = Enum.KeyCode.F,
}

local function parry()
    VIM:SendMouseButtonEvent(0, 0, 0, true, game, 0)
    task.wait(0.05)
    VIM:SendMouseButtonEvent(0, 0, 0, false, game, 0)
end

local function findBall()
    for _, obj in pairs(workspace:GetDescendants()) do
        if obj.Name == "Ball" or obj:FindFirstChild("Ball") then
            local ball = obj.Name == "Ball" and obj or obj.Ball
            if ball and ball:IsA("BasePart") then
                return ball
            end
        end
    end
    return nil
end

RunService.Heartbeat:Connect(function()
    if not CONFIG.Enabled then return end
    if not LocalPlayer.Character then return end
    
    local root = LocalPlayer.Character:FindFirstChild("HumanoidRootPart")
    if not root then return end
    
    local ball = findBall()
    if ball then
        local dist = (ball.Position - root.Position).Magnitude
        if dist <= CONFIG.ParryRange then
            parry()
        end
    end
end)

game:GetService("UserInputService").InputBegan:Connect(function(input, processed)
    if processed then return end
    if input.KeyCode == CONFIG.ToggleKey then
        CONFIG.Enabled = not CONFIG.Enabled
        print("[Morvo] Parry:", CONFIG.Enabled and "ON" or "OFF")
    end
end)

print("[Morvo] Press F to toggle auto-parry")