-- morvo/scripts/universal/autofarm.lua
-- Universal Auto-Farm — auto-click + auto-collect
-- Works on: simulators, tycoons, anime games, etc.

local VirtualInputManager = game:GetService("VirtualInputManager")
local Players = game:GetService("Players")
local RunService = game:GetService("RunService")
local LocalPlayer = Players.LocalPlayer

local CONFIG = {
    Enabled = false,
    ClickInterval = 0.05,    -- 20 clicks per second
    CollectRadius = 50,      -- Auto-collect range
    AutoEquip = true,        -- Auto-equip best tool
    AutoRejoin = false,      -- Auto-rejoin on death
}

local farming = false

-- Auto-click
local function autoClickLoop()
    while farming do
        VirtualInputManager:SendMouseButtonEvent(0, 0, 0, true, game, 0)
        task.wait(0.01)
        VirtualInputManager:SendMouseButtonEvent(0, 0, 0, false, game, 0)
        task.wait(CONFIG.ClickInterval)
    end
end

-- Auto-collect items near player
local function autoCollect()
    while farming do
        local root = LocalPlayer.Character and LocalPlayer.Character:FindFirstChild("HumanoidRootPart")
        if not root then
            task.wait(0.5)
            continue
        end
        
        for _, obj in pairs(workspace:GetDescendants()) do
            if not farming then break end
            
            -- Common collectible names
            local collectible = obj.Name == "Coin" or obj.Name == "Gem" 
                or obj.Name == "Diamond" or obj.Name == "Crystal"
                or obj.Name == "Drop" or obj.Name == "Loot"
                or obj:FindFirstChild("ClickDetector")
            
            if collectible and obj:IsA("BasePart") then
                local dist = (obj.Position - root.Position).Magnitude
                if dist <= CONFIG.CollectRadius then
                    -- Fire proximity prompt or click detector
                    local prompt = obj:FindFirstChild("ProximityPrompt")
                    if prompt and prompt.Enabled then
                        fireproximityprompt(prompt)
                    end
                    
                    local click = obj:FindFirstChild("ClickDetector")
                    if click then
                        fireclickdetector(click)
                    end
                end
            end
        end
        
        task.wait(0.1)
    end
end

-- Auto-equip best tool
local function autoEquipBest()
    if not CONFIG.AutoEquip then return end
    
    local backpack = LocalPlayer:FindFirstChild("Backpack")
    if not backpack then return end
    
    local bestTool, bestDamage = nil, 0
    
    for _, tool in pairs(backpack:GetChildren()) do
        if tool:IsA("Tool") then
            -- Try to find damage value in tool
            local damage = tool:GetAttribute("Damage") or tool:GetAttribute("Strength")
                or tool:FindFirstChild("Damage")
            
            local dmgVal = typeof(damage) == "number" and damage 
                or (damage and damage.Value or 0)
            
            if dmgVal > bestDamage then
                bestTool = tool
                bestDamage = dmgVal
            end
        end
    end
    
    if bestTool then
        local humanoid = LocalPlayer.Character and LocalPlayer.Character:FindFirstChild("Humanoid")
        if humanoid then
            humanoid:EquipTool(bestTool)
        end
    end
end

-- Toggle farm
local function toggleFarm()
    farming = not farming
    CONFIG.Enabled = farming
    
    if farming then
        task.spawn(autoClickLoop)
        task.spawn(autoCollect)
        task.spawn(function()
            while farming do
                autoEquipBest()
                task.wait(5)
            end
        end)
    end
end

-- Keyboard toggle
game:GetService("UserInputService").InputBegan:Connect(function(input, processed)
    if processed then return end
    if input.KeyCode == Enum.KeyCode.F then
        toggleFarm()
        print("[Morvo] Auto-Farm:", farming and "ON" or "OFF")
    end
end)

print("[Morvo] Auto-Farm loaded — press F to toggle")