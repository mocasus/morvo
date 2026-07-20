-- morvo/scripts/games/arsenal.lua
-- Arsenal — Silent Aim + ESP
print("[Morvo] Arsenal script loaded")

local Players = game:GetService("Players")
local LocalPlayer = Players.LocalPlayer
local Camera = workspace.CurrentCamera
local UIS = game:GetService("UserInputService")

local CONFIG = {
    AimEnabled = false,
    AimKey = Enum.UserInputType.MouseButton2,
    FOV = 120,
    AimPart = "Head",
}

local function getClosest()
    local best, bestD = nil, CONFIG.FOV
    local center = Camera.ViewportSize / 2
    
    for _, p in pairs(Players:GetPlayers()) do
        if p ~= LocalPlayer and p.Character then
            local h = p.Character:FindFirstChild(CONFIG.AimPart)
            if h then
                local pos, vis = Camera:WorldToViewportPoint(h.Position)
                if vis then
                    local d = (Vector2.new(pos.X, pos.Y) - center).Magnitude
                    if d < bestD then best, bestD = p, d end
                end
            end
        end
    end
    return best
end

-- Silent aim via namecall hook
local old = hookmetamethod(game, "__namecall", function(self, ...)
    local args = {...}
    local method = getnamecallmethod()
    
    if CONFIG.AimEnabled and (method == "FireServer" or method == "InvokeServer") then
        local t = getClosest()
        if t and t.Character then
            local part = t.Character:FindFirstChild(CONFIG.AimPart)
            if part then
                for i, arg in pairs(args) do
                    if typeof(arg) == "Vector3" then args[i] = part.Position end
                end
            end
        end
    end
    return old(self, unpack(args))
end)

UIS.InputBegan:Connect(function(input, p)
    if p then return end
    if input.UserInputType == CONFIG.AimKey then CONFIG.AimEnabled = true end
end)
UIS.InputEnded:Connect(function(input, p)
    if p then return end
    if input.UserInputType == CONFIG.AimKey then CONFIG.AimEnabled = false end
end)

print("[Morvo] Arsenal: Silent Aim ready")