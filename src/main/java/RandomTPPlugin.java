package com.example.randomtp;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

public class RandomTPPlugin extends JavaPlugin implements CommandExecutor {

    private final Random random = new Random();
    private final int LEVEL_COST = 20; // レベルコスト

    @Override
    public void onEnable() {
        this.getCommand("randomtp").setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみ使用可能です。");
            return true;
        }

        // ===== 事前チェック =====
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

        // 非同期での場所探しとテレポート処理を開始する
        findAndTeleportWithRetries(player, world, 10); // 10回までリトライ

        return true;
    }

    /**
     * テレポート先の探索とテレポートをリトライ付きで実行する
     */
    private void findAndTeleportWithRetries(Player player, World world, int retriesLeft) {
        if (retriesLeft <= 0) {
            // リトライ回数を使い切ったら失敗メッセージを送る
            player.getScheduler().run(this, (task) -> {
                player.sendMessage("§c安全なテレポート先が見つかりませんでした。");
            }, null);
            return;
        }

        // 非同期で安全な場所を探す
        findSafeLocation(world).thenAccept(safeLocation -> {
            // テレポート処理はプレイヤーにとって安全なスレッドで実行する
            player.getScheduler().run(this, (task) -> {
                // 安全な非同期テレポートを実行
                CompletableFuture<Boolean> teleportFuture = player.teleportAsync(safeLocation);

                // テレポート完了後の処理
                teleportFuture.thenAccept(success -> {
                    if (success) {
                        // テレポートが成功した場合の処理
                        // レベル消費、メッセージ送信、アイテム付与をここで行う
                        player.setLevel(player.getLevel() - LEVEL_COST);

                        String locMessage = String.format("§a%sが、 (%.0f, %.0f, %.0f) にランダムテレポートしました！（レベルを%d消費）",
                                player.getName(), safeLocation.getX(), safeLocation.getY(), safeLocation.getZ(), LEVEL_COST);
                        Bukkit.broadcastMessage(locMessage);

                        giveRandomItemViaReflection(player);
                    } else {
                        // テレポート自体に失敗した場合
                        player.sendMessage("§cテレポートに失敗しました。");
                    }
                });
            }, null);
        }).exceptionally(ex -> {
            // findSafeLocationで安全な場所が見つからなかった場合(例外がスローされた場合)
            getLogger().info(player.getName() + " のテレポート先探索に失敗、リトライします... (残り: " + (retriesLeft - 1) + "回)");
            findAndTeleportWithRetries(player, world, retriesLeft - 1);
            return null;
        });
    }

    /**
     * ワールドボーダー内で安全なランダム座標を非同期で検索する
     */
    private CompletableFuture<Location> findSafeLocation(World world) {
        WorldBorder border = world.getWorldBorder();
        double borderSize = border.getSize() / 2;
        Location center = border.getCenter();

        // ワールドボーダー内でランダムな座標を決定
        double x = center.getX() + (random.nextDouble() * borderSize * 2) - borderSize;
        double z = center.getZ() + (random.nextDouble() * borderSize * 2) - borderSize;

        // チャンクを非同期でロードしてから、最高のY座標を安全に取得
        return world.getChunkAtAsync((int) x >> 4, (int) z >> 4).thenApply(chunk -> {
            int y = world.getHighestBlockYAt((int) x, (int) z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Location ground = new Location(world, x, y, z);

            // 足元が安全か（液体でないか、奈落でないかなど）を確認
            if (ground.getBlock().isLiquid() || y < world.getMinHeight()) {
                // 安全でない場合は例外をスローし、.exceptionally()でリトライさせる
                throw new IllegalStateException("Unsafe spawn location found: " + ground.getBlock().getType().name());
            }

            // 安全な座標を返す (プレイヤーの頭が埋まらないように+1)
            return ground.add(0.5, 1.0, 0.5);
        });
    }

    /**
     * リフレクションを使ってRandomItemGiverのアイテム付与メソッドを呼び出す
     */
    private void giveRandomItemViaReflection(Player player) {
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
    }
}