-- morvo/scripts/universal/esp.lua
-- Universal ESP — highlights all players through walls
-- Works on: Arsenal, Phantom Forces, Bad Business, etc.

local Players = game:GetService("Players")
local RunService = game:GetService("RunService")
local LocalPlayer = Players.LocalPlayer
local Camera = workspace.CurrentCamera

-- Config
local CONFIG = {
    Enabled = true,
    TeamCheck = false,         -- Don't highlight teammates
    ShowName = true,
    ShowDistance = true,
    ShowHealth = true,
    ShowBox = true,
    BoxColor = Color3.fromRGB(255, 0, 0),
    NameColor = Color3.fromRGB(255, 255, 255),
    MaxDistance = 2000,
    RefreshRate = 60,         -- Updates per second
}

-- Cache for ESP objects
local ESPCache = {}

-- Create ESP for a player
local function createESP(player)
    if player == LocalPlayer then return end
    
    local highlight = Instance.new("Highlight")
    highlight.Name = "Morvo_ESP_" .. player.Name
    highlight.FillColor = CONFIG.BoxColor
    highlight.FillTransparency = 0.6
    highlight.OutlineColor = Color3.fromRGB(255, 255, 255)
    highlight.OutlineTransparency = 0.3
    highlight.DepthMode = Enum.HighlightDepthMode.AlwaysOnTop
    
    -- Billboard for name/distance/health
    local billboard = Instance.new("BillboardGui")
    billboard.Name = "Morvo_ESP_Billboard"
    billboard.Size = UDim2.new(0, 200, 0, 60)
    billboard.StudsOffset = Vector3.new(0, 3, 0)
    billboard.AlwaysOnTop = true
    
    local label = Instance.new("TextLabel")
    label.Size = UDim2.new(1, 0, 1, 0)
    label.BackgroundTransparency = 1
    label.TextColor3 = CONFIG.NameColor
    label.TextStrokeTransparency = 0
    label.TextScaled = true
    label.Font = Enum.Font.SourceSansBold
    label.Parent = billboard
    
    ESPCache[player] = { highlight = highlight, billboard = billboard, label = label }
    
    -- Wait for character
    local function onCharacter(character)
        highlight.Parent = character
        billboard.Parent = character:WaitForChild("Head")
    end
    
    if player.Character then
        onCharacter(player.Character)
    end
    
    player.CharacterAdded:Connect(onCharacter)
    player.CharacterRemoving:Connect(function()
        highlight.Parent = nil
        billboard.Parent = nil
    end)
end

-- Update ESP every frame
RunService.RenderStepped:Connect(function()
    if not CONFIG.Enabled then return end
    
    for player, cache in pairs(ESPCache) do
        if not player.Parent then
            -- Player left — cleanup
            cache.highlight:Destroy()
            cache.billboard:Destroy()
            ESPCache[player] = nil
            continue
        end
        
        local char = player.Character
        if not char or not char:FindFirstChild("Head") then
            cache.highlight.Parent = nil
            cache.billboard.Parent = nil
            continue
        end
        
        -- Team check
        if CONFIG.TeamCheck and player.Team == LocalPlayer.Team then
            cache.highlight.Enabled = false
            cache.billboard.Enabled = false
            continue
        end
        
        -- Distance check
        local localRoot = LocalPlayer.Character and LocalPlayer.Character:FindFirstChild("HumanoidRootPart")
        local targetRoot = char:FindFirstChild("HumanoidRootPart")
        
        if localRoot and targetRoot then
            local dist = (localRoot.Position - targetRoot.Position).Magnitude
            
            if dist > CONFIG.MaxDistance then
                cache.highlight.Enabled = false
                cache.billboard.Enabled = false
                continue
            end
            
            -- Update billboard info
            local info = ""
            if CONFIG.ShowName then
                info = info .. player.Name .. "\n"
            end
            if CONFIG.ShowDistance then
                info = info .. string.format("%.0f studs", dist) .. "\n"
            end
            if CONFIG.ShowHealth then
                local humanoid = char:FindFirstChild("Humanoid")
                if humanoid then
                    info = info .. string.format("❤ %d", humanoid.Health)
                end
            end
            
            cache.label.Text = info
        end
        
        cache.highlight.Enabled = true
        cache.billboard.Enabled = true
    end
end)

-- Hook all players
for _, player in pairs(Players:GetPlayers()) do
    task.spawn(createESP, player)
end
Players.PlayerAdded:Connect(function(player)
    task.spawn(createESP, player)
end)

Players.PlayerRemoving:Connect(function(player)
    local cache = ESPCache[player]
    if cache then
        cache.highlight:Destroy()
        cache.billboard:Destroy()
        ESPCache[player] = nil
    end
end)

print("[Morvo] ESP loaded — press F9 to toggle")
