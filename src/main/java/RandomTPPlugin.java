package com.example.randomtp;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.lang.reflect.Method;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Random;

public class RandomTPPlugin extends JavaPlugin implements CommandExecutor {

    private final Random random = new Random();
    private final int LEVEL_COST = 20; // レベルコスト

    @Override
    public void onEnable() {
        this.getCommand("randomtp").setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみ使用可能です。");
            return true;
        }

        Player player = (Player) sender;

        if (player.getGameMode() != GameMode.SURVIVAL) {
            player.sendMessage("§cこのコマンドはサバイバルモードのプレイヤーのみ使用可能です。");
            return true;
        }

        if (player.getLevel() < LEVEL_COST) {
            player.sendMessage("§cレベルが不足しています。最低 " + LEVEL_COST + " レベルが必要です。");
            return true;
        }

        World world = player.getWorld();

        if (world.getEnvironment() != World.Environment.NORMAL) {
            player.sendMessage("§cこのコマンドはオーバーワールドでのみ使用可能です。");
            return true;
        }

        double borderSize = world.getWorldBorder().getSize() / 2;
        Location center = world.getWorldBorder().getCenter();

        double x, z;
        Location tpLoc;

        do {
            x = center.getX() + random.nextDouble() * borderSize * 2 - borderSize;
            z = center.getZ() + random.nextDouble() * borderSize * 2 - borderSize;
            int y = world.getHighestBlockYAt((int) x, (int) z) + 1;
            tpLoc = new Location(world, x, y, z);
        } while (!isSafeLocation(tpLoc));

        player.teleport(tpLoc);
        player.setLevel(player.getLevel() - LEVEL_COST);

        String locMessage = String.format("§a%sが、 (%.0f, %.0f, %.0f) にランダムテレポートしました！（レベルを%d消費）", 
                player.getName(), tpLoc.getX(), tpLoc.getY(), tpLoc.getZ(), LEVEL_COST);

        // 全員に通知
        Bukkit.broadcastMessage(locMessage);

        JavaPlugin itemGiverPlugin = (JavaPlugin) Bukkit.getPluginManager().getPlugin("RandomItemGiver");
        if (itemGiverPlugin != null && itemGiverPlugin.isEnabled()) {
            try {
                Method method = itemGiverPlugin.getClass().getDeclaredMethod("giveRandomSpawnItem", Player.class);
                method.setAccessible(true);
                method.invoke(itemGiverPlugin, player);
            } catch (Exception e) {
                getLogger().warning("RandomItemGiverのアイテム付与処理に失敗しました: " + e.getMessage());
                e.printStackTrace();
            }
        }

        return true;
    }

    private boolean isSafeLocation(Location loc) {
        return loc.getBlock().isEmpty() && loc.clone().add(0, -1, 0).getBlock().getType().isSolid();
    }
}
