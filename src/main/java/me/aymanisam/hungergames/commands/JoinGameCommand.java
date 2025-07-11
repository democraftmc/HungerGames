package me.aymanisam.hungergames.commands;

import me.aymanisam.hungergames.HungerGames;
import me.aymanisam.hungergames.handlers.*;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static me.aymanisam.hungergames.HungerGames.gameStarted;
import static me.aymanisam.hungergames.HungerGames.*;
import static me.aymanisam.hungergames.handlers.GameSequenceHandler.startingPlayers;

public class JoinGameCommand implements CommandExecutor {
    private final HungerGames plugin;
    private final LangHandler langHandler;
    private final SetSpawnHandler setSpawnHandler;
    private final ArenaHandler arenaHandler;
    private final ConfigHandler configHandler;
    private final ScoreBoardHandler scoreBoardHandler;

    public JoinGameCommand(HungerGames plugin, LangHandler langHandler, SetSpawnHandler setSpawnHandler, ScoreBoardHandler scoreBoardHandler) {
        this.plugin = plugin;
        this.langHandler = langHandler;
        this.setSpawnHandler = setSpawnHandler;
	    this.configHandler = plugin.getConfigHandler();
	    this.scoreBoardHandler = scoreBoardHandler;
	    this.arenaHandler = new ArenaHandler(plugin, langHandler);
    }

    public static void giveLeaveItem(Player player, LangHandler langHandler) {
        ItemStack leaveItem = new ItemStack(Material.RED_BED);
        ItemMeta meta = leaveItem.getItemMeta();
        assert meta != null;
        meta.setDisplayName(langHandler.getMessage(player, "spectate.leave-item-name"));
        List<String> lore = new ArrayList<>();
        lore.add(langHandler.getMessage(player, "spectate.leave-item-lore"));
        meta.setLore(lore);
        leaveItem.setItemMeta(meta);
        player.getInventory().setItem(8, leaveItem);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(langHandler.getMessage(null, "no-server"));
            return true;
        }

        if (!(player.hasPermission("hungergames.join"))) {
            sender.sendMessage(langHandler.getMessage(player, "no-permission"));
            return true;
        }

        if (!(args.length == 1)) {
            sender.sendMessage(langHandler.getMessage(player, "teleport.no-arena"));
            return true;
        }

        String worldName = args[0];

        if (!hgWorldNames.contains(worldName)) {
            sender.sendMessage(langHandler.getMessage(player, "teleport.invalid-world", worldName));
            plugin.getLogger().info("Loaded maps:" + plugin.getServer().getWorlds().stream().map(World::getName).collect(Collectors.joining(", ")));
            return true;
        }

        World world = Bukkit.getWorld(worldName);

        Map<String, Player> worldSpawnPointMap = setSpawnHandler.spawnPointMap.computeIfAbsent(worldName, k -> new HashMap<>());
        List<Player> worldStartingPlayers = startingPlayers.computeIfAbsent(worldName, k -> new ArrayList<>());

        if (configHandler.getPluginSettings().getBoolean("custom-teams")) {
            if (!teamsFinalized) {
                player.sendMessage(langHandler.getMessage(player, "team.no-finalize"));
                return true;
            }
            if (!isPlayerInAnyCustomTeam(player)) {
                player.sendMessage(langHandler.getMessage(player, "team.no-team"));
                return true;
            }
        }

        if (gameStarted.getOrDefault(worldName, false)) {
            player.sendMessage(langHandler.getMessage(player, "startgame.started"));
            teleportPlayerForSpectating(player, worldName, world, configHandler, scoreBoardHandler, langHandler);
            return true;
        }

        if (gameStarting.getOrDefault(worldName, false)) {
            player.sendMessage(langHandler.getMessage(player, "startgame.starting"));
            teleportPlayerForSpectating(player, worldName, world, configHandler, scoreBoardHandler, langHandler);
            return true;
        }

        if ((worldSpawnPointMap.containsValue(player) || worldStartingPlayers.contains(player))) {
            player.sendMessage(langHandler.getMessage(player, "game.already-joined"));
            return true;
        }

        if (world == null) {
            World createdWorld = Bukkit.createWorld(WorldCreator.name(worldName));
            assert createdWorld != null;
            arenaHandler.loadWorldFiles(createdWorld);
            List<Player> worldPlayersWaiting = setSpawnHandler.playersWaiting.computeIfAbsent(worldName, k -> new ArrayList<>());
            if (worldPlayersWaiting.contains(player)) {
                return true;
            }
            setSpawnHandler.teleportPlayerToSpawnpoint(player, createdWorld);
            setSpawnHandler.createSetSpawnConfig(createdWorld);
        } else {
            setSpawnHandler.teleportPlayerToSpawnpoint(player, world);
            setSpawnHandler.createSetSpawnConfig(world);
        }

        return true;
    }

    public static void teleportPlayerForSpectating(Player player, String worldName, World world, ConfigHandler configHandler, ScoreBoardHandler scoreBoardHandler, LangHandler langHandler) {
        if (configHandler.getPluginSettings().getBoolean("spectating")) {
            assert world != null;
            player.teleport(world.getSpawnLocation());
            if (gameStarted.getOrDefault(worldName, false)) {
                scoreBoardHandler.createBoard(player);
            }
            player.setGameMode(GameMode.SPECTATOR);
            player.sendMessage(langHandler.getMessage(player, "spectate.spectating-player"));
             giveLeaveItem(player, langHandler);
        }
    }

    public static boolean isPlayerInAnyCustomTeam(Player player) {
        for (List<Player> team : customTeams.values()) {
            if (team.contains(player)) {
                return true;
            }
        }
        return false;
    }
}
