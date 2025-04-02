package me.abhimaanst.fastcane;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FastGrowPlugin extends JavaPlugin {

    private double growthInterval;
    private int maxCaneHeight;
    private int checkRadius;
    private int growthAmount;
    private final Map<Location, Long> lastProcessed = new ConcurrentHashMap<>();
    private final int PROCESS_COOLDOWN = 500; 

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfigValues();
        getLogger().info(String.format(
            "FastCane enabled! Settings: interval=%.1fs, amount=%d, max-height=%d, radius=%d",
            growthInterval, growthAmount, maxCaneHeight, checkRadius
        ));
        startGrowthScheduler();
    }

    private void reloadConfigValues() {
        FileConfiguration config = getConfig();
        growthInterval = Math.max(0.05, config.getDouble("growth-interval", 0.5));
        checkRadius = Math.min(256, Math.max(16, config.getInt("check-radius", 64)));
        maxCaneHeight = Math.min(255, Math.max(1, config.getInt("max-height", 3)));
        growthAmount = Math.min(maxCaneHeight, Math.max(1, config.getInt("growth-amount", 1)));
    }

    private void startGrowthScheduler() {
        new BukkitRunnable() {
            @Override
            public void run() {
                Bukkit.getScheduler().runTaskAsynchronously(FastGrowPlugin.this, () -> {
                    long currentTime = System.currentTimeMillis();
                    Set<Block> blocksToGrow = new HashSet<>();
                    
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        Location loc = player.getLocation();
                        World world = loc.getWorld();
                        
                        
                        int minX = loc.getBlockX() - checkRadius;
                        int maxX = loc.getBlockX() + checkRadius;
                        int minZ = loc.getBlockZ() - checkRadius;
                        int maxZ = loc.getBlockZ() + checkRadius;
                        
                        // Check blocks in cuboid
                        for (int x = minX; x <= maxX; x++) {
                            for (int z = minZ; z <= maxZ; z++) {
                               
                                for (int y = world.getMaxHeight(); y >= 0; y--) {
                                    Block block = world.getBlockAt(x, y, z);
                                    
                                    if (block.getType() == Material.SUGAR_CANE_BLOCK) {
                                        Block base = findBaseBlock(block);
                                        Location baseLoc = base.getLocation();
                                        
                                       
                                        Long lastProcessedTime = lastProcessed.get(baseLoc);
                                        if (lastProcessedTime == null || 
                                            currentTime - lastProcessedTime > PROCESS_COOLDOWN) {
                                            
                                            int currentHeight = getCaneHeight(base);
                                            if (currentHeight < maxCaneHeight) {
                                                Block top = getTopBlock(base);
                                                Block above = top.getRelative(BlockFace.UP);
                                                
                                                if (above.getType() == Material.AIR && canGrow(top)) {
                                                    blocksToGrow.add(above);
                                                    lastProcessed.put(baseLoc, currentTime);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    if (!blocksToGrow.isEmpty()) {
                        Bukkit.getScheduler().runTask(FastGrowPlugin.this, () -> {
                            for (Block block : blocksToGrow) {
                                int remainingGrowth = Math.min(growthAmount, 
                                    maxCaneHeight - getCaneHeight(block.getRelative(BlockFace.DOWN)));
                                
                                for (int i = 0; i < remainingGrowth; i++) {
                                    Block current = block.getRelative(0, i, 0);
                                    if (current.getType() == Material.AIR && 
                                        canGrow(current.getRelative(BlockFace.DOWN))) {
                                        current.setType(Material.SUGAR_CANE_BLOCK);
                                    }
                                }
                            }
                        });
                    }
                });
            }
        }.runTaskTimer(this, 20L, (long) (growthInterval * 20L));
    }

    private Block findBaseBlock(Block block) {
        while (block.getRelative(BlockFace.DOWN).getType() == Material.SUGAR_CANE_BLOCK) {
            block = block.getRelative(BlockFace.DOWN);
        }
        return block;
    }

    private Block getTopBlock(Block base) {
        Block top = base;
        while (top.getRelative(BlockFace.UP).getType() == Material.SUGAR_CANE_BLOCK) {
            top = top.getRelative(BlockFace.UP);
        }
        return top;
    }

    private int getCaneHeight(Block base) {
        int height = 1;
        Block current = base;
        while ((current = current.getRelative(BlockFace.UP)).getType() == Material.SUGAR_CANE_BLOCK) {
            height++;
        }
        return height;
    }

    private boolean canGrow(Block sugarCane) {
        Block below = sugarCane.getRelative(BlockFace.DOWN);
        Material belowType = below.getType();
        return belowType == Material.SUGAR_CANE_BLOCK || 
               belowType == Material.GRASS || 
               belowType == Material.DIRT || 
               belowType == Material.SAND;
    }
}