package net.citizensnpcs.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;


public class PlayerUpdateTask extends Thread {

    private final JavaPlugin plugin;

    public PlayerUpdateTask(JavaPlugin plugin) {

        this.plugin = plugin;

        start();

        setName("Citizen - PlayerUpdate");

    }

    public void cancel() {

        TICKERS.clear();
        TICKERS_PENDING_ADD.clear();
        TICKERS_PENDING_REMOVE.clear();

    }

    @Override
    public void run() {

        while(plugin.isEnabled()) {

            for (int i = 0; i < TICKERS_PENDING_ADD.size(); i++) {

                org.bukkit.entity.Entity ent = TICKERS_PENDING_ADD.get(i);
                TICKERS.put(ent.getUniqueId(), ent);

            }

            for (int i = 0; i < TICKERS_PENDING_REMOVE.size(); i++) {

                TICKERS.remove(TICKERS_PENDING_REMOVE.get(i).getUniqueId());

            }

            TICKERS_PENDING_ADD.clear();
            TICKERS_PENDING_REMOVE.clear();

            Iterator<org.bukkit.entity.Entity> itr = TICKERS.values()
                    .iterator();

            while (itr.hasNext()) {

                Entity entity = itr.next();

                if (NMS.tick(entity)) {

                    itr.remove();

                }

            }

            for (Entity entity : PLAYERS_PENDING_REMOVE) {

                PLAYERS.remove(entity.getUniqueId());

            }

            for (Entity entity : PLAYERS_PENDING_ADD) {

                PLAYERS.put(entity.getUniqueId(), (Player) entity);

            }

            PLAYERS_PENDING_ADD.clear();
            PLAYERS_PENDING_REMOVE.clear();

            for (Player entity : PLAYERS.values()) {

                if (entity.isValid()) {

                    NMS.playerTick(entity);

                }

            }

            //Wait (1s)
            try {Thread.sleep(1000);} catch (InterruptedException e) {}

        }

        cancel();

    }

    public static void addOrRemove(org.bukkit.entity.Entity entity, boolean remove) {
        boolean contains = TICKERS.containsKey(entity.getUniqueId());
        if (!remove) {
            if (contains) {
                TICKERS_PENDING_REMOVE.add(entity);
            }
        } else if (!contains) {
            TICKERS_PENDING_ADD.add(entity);
        }
    }

    public static void deregisterPlayer(org.bukkit.entity.Entity entity) {
        PLAYERS_PENDING_ADD.remove(entity);
        PLAYERS_PENDING_REMOVE.add(entity);
    }

    public static void registerPlayer(org.bukkit.entity.Entity entity) {
        PLAYERS_PENDING_REMOVE.remove(entity);
        PLAYERS_PENDING_ADD.add(entity);
    }

    private static Map<UUID, org.bukkit.entity.Player> PLAYERS = new ConcurrentHashMap<>();
    private static List<org.bukkit.entity.Entity> PLAYERS_PENDING_ADD = new ArrayList<org.bukkit.entity.Entity>();
    private static List<org.bukkit.entity.Entity> PLAYERS_PENDING_REMOVE = new ArrayList<org.bukkit.entity.Entity>();
    private static Map<UUID, org.bukkit.entity.Entity> TICKERS = new ConcurrentHashMap<UUID, org.bukkit.entity.Entity>();
    private static List<org.bukkit.entity.Entity> TICKERS_PENDING_ADD = new ArrayList<org.bukkit.entity.Entity>();
    private static List<org.bukkit.entity.Entity> TICKERS_PENDING_REMOVE = new ArrayList<org.bukkit.entity.Entity>();
}
