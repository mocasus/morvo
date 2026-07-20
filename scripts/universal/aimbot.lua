-- morvo/scripts/universal/aimbot.lua
-- Silent Aim — redirects bullets to target without visual snap
-- Uses RemoteEvent/RemoteFunction interception

local Players = game:GetService("Players")
local RunService = game:GetService("RunService")
local LocalPlayer = Players.LocalPlayer
local Camera = workspace.CurrentCamera
local UserInputService = game:GetService("UserInputService")

-- Config
local CONFIG = {
    Enabled = false,
    AimKey = Enum.UserInputType.MouseButton2,  -- Right mouse button
    FOV = 150,         -- Pixels radius
    AimPart = "Head",  -- Target body part
    TeamCheck = true,
    VisibilityCheck = true,
    Smoothness = 1,    -- 1 = instant, 5 = smooth
}

local Target = nil

-- Get closest player to crosshair within FOV
local function getClosestPlayer()
    local closest, minDist = nil, CONFIG.FOV
    local mousePos = UserInputService:GetMouseLocation()
    local screenCenter = Camera.ViewportSize / 2
    
    for _, player in pairs(Players:GetPlayers()) do
        if player ~= LocalPlayer and player.Character then
            if CONFIG.TeamCheck and player.Team == LocalPlayer.Team then
                continue
            end
            
            local part = player.Character:FindFirstChild(CONFIG.AimPart)
            if not part then continue end
            
            local screenPos, visible = Camera:WorldToViewportPoint(part.Position)
            
            if CONFIG.VisibilityCheck and not visible then
                continue
            end
            
            local dist = (Vector2.new(screenPos.X, screenPos.Y) - screenCenter).Magnitude
            
            if dist < minDist then
                closest = player
                minDist = dist
            end
        end
    end
    
    return closest
end

-- Hook RemoteEvent firing to redirect aim
-- This is the "silent" part — no visual camera movement
local oldNamecall
oldNamecall = hookmetamethod(game, "__namecall", function(self, ...)
    local args = {...}
    local method = getnamecallmethod()
    
    if CONFIG.Enabled and Target then
        local targetChar = Target.Character
        if targetChar then
            local aimPart = targetChar:FindFirstChild(CONFIG.AimPart)
            
            if aimPart and (method == "FireServer" or method == "InvokeServer") then
                -- Scan args for Vector3 positions (bullet targets)
                for i, arg in pairs(args) do
                    if typeof(arg) == "Vector3" then
                        -- Redirect to target position
                        args[i] = aimPart.Position
                    end
                end
            end
        end
    end
    
    return oldNamecall(self, unpack(args))
end)

-- Update target every frame
RunService.RenderStepped:Connect(function()
    if not CONFIG.Enabled then
        Target = nil
        return
    end
    
    Target = getClosestPlayer()
end)

-- Toggle with right mouse button
UserInputService.InputBegan:Connect(function(input, processed)
    if processed then return end
    
    if input.UserInputType == CONFIG.AimKey then
        CONFIG.Enabled = true
    end
end)

UserInputService.InputEnded:Connect(function(input, processed)
    if processed then return end
    
    if input.UserInputType == CONFIG.AimKey then
        CONFIG.Enabled = false
        Target = nil
    end
end)

print("[Morvo] Silent Aim loaded — right-click to aim")
