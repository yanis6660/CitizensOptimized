package net.citizensnpcs.npc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.SetMultimap;

import net.citizensnpcs.NPCNeedsRespawnEvent;
import net.citizensnpcs.Settings.Setting;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.astar.pathfinder.SwimmingExaminer;
import net.citizensnpcs.api.event.DespawnReason;
import net.citizensnpcs.api.event.NPCDespawnEvent;
import net.citizensnpcs.api.event.NPCSpawnEvent;
import net.citizensnpcs.api.event.SpawnReason;
import net.citizensnpcs.api.npc.AbstractNPC;
import net.citizensnpcs.api.npc.BlockBreaker;
import net.citizensnpcs.api.npc.BlockBreaker.BlockBreakerConfiguration;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.trait.trait.MobType;
import net.citizensnpcs.api.trait.trait.Spawned;
import net.citizensnpcs.api.util.DataKey;
import net.citizensnpcs.api.util.Messaging;
import net.citizensnpcs.npc.ai.CitizensNavigator;
import net.citizensnpcs.npc.skin.SkinnableEntity;
import net.citizensnpcs.trait.CurrentLocation;
import net.citizensnpcs.trait.Gravity;
import net.citizensnpcs.trait.HologramTrait;
import net.citizensnpcs.trait.ScoreboardTrait;
import net.citizensnpcs.trait.SneakTrait;
import net.citizensnpcs.util.ChunkCoord;
import net.citizensnpcs.util.Messages;
import net.citizensnpcs.util.NMS;
import net.citizensnpcs.util.PlayerAnimation;
import net.citizensnpcs.util.PlayerUpdateTask;
import net.citizensnpcs.util.Util;

public class CitizensNPC extends AbstractNPC {
    private ChunkCoord cachedCoord;
    private EntityController entityController;
    private final CitizensNavigator navigator = new CitizensNavigator(this);
    private int updateCounter = 0;

    public CitizensNPC(UUID uuid, int id, String name, EntityController entityController, NPCRegistry registry) {
        super(uuid, id, name, registry);
        Preconditions.checkNotNull(entityController);
        this.entityController = entityController;
    }

    @Override
    public boolean despawn(DespawnReason reason) {
        if (getEntity() == null && reason != DespawnReason.DEATH) {
            Messaging.debug("Tried to despawn", this, "while already despawned, DespawnReason." + reason);
            if (reason == DespawnReason.RELOAD) {
                unloadEvents();
            }
            return true;
        }
        NPCDespawnEvent event = new NPCDespawnEvent(this, reason);
        if (reason == DespawnReason.CHUNK_UNLOAD) {
            event.setCancelled(data().get(NPC.KEEP_CHUNK_LOADED_METADATA, Setting.KEEP_CHUNKS_LOADED.asBoolean()));
        }
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled() && reason != DespawnReason.DEATH) {
            Messaging.debug("Couldn't despawn", this, "due to despawn event cancellation. Will load chunk.",
                    getEntity().isValid(), ", DespawnReason." + reason);
            return false;
        }
        boolean keepSelected = getOrAddTrait(Spawned.class).shouldSpawn();
        if (!keepSelected) {
            data().remove("selectors");
        }
        if (getEntity() instanceof Player) {
            PlayerUpdateTask.deregisterPlayer(getEntity());
        }
        navigator.onDespawn();
        if (reason == DespawnReason.RELOAD) {
            unloadEvents();
        }
        for (Trait trait : new ArrayList<Trait>(traits.values())) {
            trait.onDespawn();
        }
        Messaging.debug("Despawned", this, "DespawnReason." + reason);

        if (getEntity() instanceof SkinnableEntity) {
            ((SkinnableEntity) getEntity()).getSkinTracker().onRemoveNPC();
        }

        if (reason == DespawnReason.DEATH) {
            entityController.setEntity(null);
        } else {
            entityController.remove();
        }
        return true;
    }

    @Override
    public void destroy() {
        super.destroy();
        resetCachedCoord();
    }

    @Override
    public void faceLocation(Location location) {
        if (!isSpawned())
            return;
        Util.faceLocation(getEntity(), location);
    }

    @Override
    public BlockBreaker getBlockBreaker(Block targetBlock, BlockBreakerConfiguration config) {
        return NMS.getBlockBreaker(getEntity(), targetBlock, config);
    }

    @Override
    public Entity getEntity() {
        return entityController == null ? null : entityController.getBukkitEntity();
    }

    @Override
    public Navigator getNavigator() {
        return navigator;
    }

    @Override
    public Location getStoredLocation() {
        return isSpawned() ? getEntity().getLocation() : getOrAddTrait(CurrentLocation.class).getLocation();
    }

    @Override
    public boolean isFlyable() {
        updateFlyableState();
        return super.isFlyable();
    }

    @Override
    public boolean isSpawned() {
        return getEntity() != null && NMS.isValid(getEntity());
    }

    @Override
    public void load(final DataKey root) {
        super.load(root);
        // Spawn the NPC
        CurrentLocation spawnLocation = getOrAddTrait(CurrentLocation.class);
        if (getOrAddTrait(Spawned.class).shouldSpawn() && spawnLocation.getLocation() != null) {
            if (spawnLocation.getLocation() != null) {
                spawn(spawnLocation.getLocation(), SpawnReason.RESPAWN);
            } else {
                Messaging.debug("Tried to spawn", this, "on load but world was null");
            }
        }

        navigator.load(root.getRelative("navigator"));
    }

    @Override
    public boolean requiresNameHologram() {
        return super.requiresNameHologram()
                || (getEntityType() != EntityType.ARMOR_STAND && Setting.ALWAYS_USE_NAME_HOLOGRAM.asBoolean());
    }

    private void resetCachedCoord() {
        if (cachedCoord == null)
            return;
        CHUNK_LOADERS.remove(NPC_METADATA_MARKER, CHUNK_LOADERS);
        CHUNK_LOADERS.remove(cachedCoord, this);
        if (CHUNK_LOADERS.get(cachedCoord).size() == 0) {
            cachedCoord.setForceLoaded(false);
        }
        cachedCoord = null;
    }

    @Override
    public void save(DataKey root) {
        super.save(root);
        if (!data().get(NPC.SHOULD_SAVE_METADATA, true))
            return;
        navigator.save(root.getRelative("navigator"));
    }

    @Override
    public void setBukkitEntityType(EntityType type) {
        EntityController controller = EntityControllers.createForType(type);
        if (controller == null)
            throw new IllegalArgumentException("Unsupported entity type " + type);
        setEntityController(controller);
    }

    public void setEntityController(EntityController newController) {
        Preconditions.checkNotNull(newController);
        boolean wasSpawned = isSpawned();
        Location prev = null;
        if (wasSpawned) {
            prev = getEntity().getLocation(CACHE_LOCATION);
            despawn(DespawnReason.PENDING_RESPAWN);
        }
        entityController = newController;
        if (wasSpawned) {
            spawn(prev, SpawnReason.RESPAWN);
        }
    }

    @Override
    public void setFlyable(boolean flyable) {
        super.setFlyable(flyable);
        updateFlyableState();
    }

    @Override
    public void setMoveDestination(Location destination) {
        if (!isSpawned())
            return;
        if (destination == null) {
            NMS.cancelMoveDestination(getEntity());
        } else {
            NMS.setDestination(getEntity(), destination.getX(), destination.getY(), destination.getZ(), 1);
        }
    }

    @Override
    public void setName(String name) {
        super.setName(name);

        if (requiresNameHologram() && !hasTrait(HologramTrait.class)) {
            addTrait(HologramTrait.class);
        }
    }

    @Override
    public boolean spawn(Location at) {
        return spawn(at, SpawnReason.PLUGIN);
    }

    @Override
    public boolean spawn(Location at, SpawnReason reason) {
        Preconditions.checkNotNull(at, "location cannot be null");
        Preconditions.checkNotNull(reason, "reason cannot be null");
        if (getEntity() != null) {
            Messaging.debug("Tried to spawn", this, "while already spawned. SpawnReason." + reason);
            return false;
        }
        if (at.getWorld() == null) {
            Messaging.debug("Tried to spawn", this, "but the world was null. SpawnReason." + reason);
            return false;
        }
        at = at.clone();

        if (reason == SpawnReason.CHUNK_LOAD || reason == SpawnReason.COMMAND) {
            at.getChunk().load();
        }

        getOrAddTrait(CurrentLocation.class).setLocation(at);
        entityController.spawn(at.clone(), this);
        getEntity().setMetadata(NPC_METADATA_MARKER, new FixedMetadataValue(CitizensAPI.getPlugin(), true));

        Collection<Trait> onPreSpawn = traits.values();
        for (Trait trait : onPreSpawn.toArray(new Trait[onPreSpawn.size()])) {
            try {
                trait.onPreSpawn();
            } catch (Throwable ex) {
                Messaging.severeTr(Messages.TRAIT_ONSPAWN_FAILED, trait.getName(), getId());
                ex.printStackTrace();
            }
        }

        boolean loaded = Util.isLoaded(at);
        boolean couldSpawn = !loaded ? false : NMS.addEntityToWorld(getEntity(), CreatureSpawnEvent.SpawnReason.CUSTOM);

        if (!couldSpawn) {
            if (Messaging.isDebugging()) {
                Messaging.debug("Retrying spawn of", this, "later, SpawnReason." + reason + ". Was loaded", loaded,
                        "is loaded", Util.isLoaded(at));
            }
            // we need to wait before trying to spawn
            entityController.remove();
            Bukkit.getPluginManager().callEvent(new NPCNeedsRespawnEvent(this, at));
            return false;
        }
        // send skin packets, if applicable, before other NMS packets are sent
        SkinnableEntity skinnable = getEntity() instanceof SkinnableEntity ? ((SkinnableEntity) getEntity()) : null;
        if (skinnable != null) {
            skinnable.getSkinTracker().onSpawnNPC();
        }

        getEntity().teleport(at);

        NMS.setHeadYaw(getEntity(), at.getYaw());
        NMS.setBodyYaw(getEntity(), at.getYaw());

        final Location to = at;
        Consumer<Runnable> postSpawn = new Consumer<Runnable>() {
            private int timer;

            @Override
            public void accept(Runnable cancel) {
                if (getEntity() == null || !getEntity().isValid()) {
                    if (timer++ > Setting.ENTITY_SPAWN_WAIT_TICKS.asInt()) {
                        Messaging.debug("Couldn't spawn ", CitizensNPC.this, "waited", timer,
                                "ticks but entity not added to world");
                        entityController.remove();
                        cancel.run();
                        Bukkit.getPluginManager().callEvent(new NPCNeedsRespawnEvent(CitizensNPC.this, to));
                    }

                    return;
                }

                // Set the spawned state
                getOrAddTrait(CurrentLocation.class).setLocation(to);
                getOrAddTrait(Spawned.class).setSpawned(true);

                NPCSpawnEvent spawnEvent = new NPCSpawnEvent(CitizensNPC.this, to, reason);
                Bukkit.getPluginManager().callEvent(spawnEvent);

                if (spawnEvent.isCancelled()) {
                    Messaging.debug("Couldn't spawn", CitizensNPC.this, "SpawnReason." + reason,
                            "due to event cancellation.");
                    entityController.remove();
                    cancel.run();
                    return;
                }

                navigator.onSpawn();

                for (Trait trait : Iterables.toArray(traits.values(), Trait.class)) {
                    try {
                        trait.onSpawn();
                    } catch (Throwable ex) {
                        Messaging.severeTr(Messages.TRAIT_ONSPAWN_FAILED, trait.getName(), getId());
                        ex.printStackTrace();
                    }
                }

                EntityType type = getEntity().getType();
                if (type.isAlive()) {
                    LivingEntity entity = (LivingEntity) getEntity();
                    entity.setRemoveWhenFarAway(false);

                    if (NMS.getStepHeight(entity) < 1) {
                        NMS.setStepHeight(entity, 1);
                    }

                    if (type == EntityType.PLAYER) {
                        NMS.replaceTrackerEntry((Player) getEntity());
                        PlayerUpdateTask.registerPlayer(getEntity());
                    }

                    if (SUPPORT_NODAMAGE_TICKS && (Setting.DEFAULT_SPAWN_NODAMAGE_TICKS.asInt() != 20
                            || data().has(NPC.Metadata.SPAWN_NODAMAGE_TICKS))) {
                        try {
                            entity.setNoDamageTicks(data().get(NPC.Metadata.SPAWN_NODAMAGE_TICKS,
                                    Setting.DEFAULT_SPAWN_NODAMAGE_TICKS.asInt()));
                        } catch (NoSuchMethodError err) {
                            SUPPORT_NODAMAGE_TICKS = false;
                        }
                    }
                }

                if (requiresNameHologram() && !hasTrait(HologramTrait.class)) {
                    addTrait(HologramTrait.class);
                }

                updateFlyableState();
                updateCustomNameVisibility();
                updateScoreboard();

                Messaging.debug("Spawned", CitizensNPC.this, "SpawnReason." + reason);
                cancel.run();
            }
        };
        if (getEntity() != null && getEntity().isValid()) {
            postSpawn.accept(() -> {
            });
        } else {
            new BukkitRunnable() {
                @Override
                public void run() {
                    postSpawn.accept(() -> cancel());
                }
            }.runTaskTimer(CitizensAPI.getPlugin(), 0, 1);
        }

        return true;
    }

    @Override
    public void teleport(Location location, TeleportCause reason) {
        super.teleport(location, reason);
        if (!isSpawned())
            return;
        Location npcLoc = getEntity().getLocation(CACHE_LOCATION);
        if (isSpawned() && npcLoc.getWorld() == location.getWorld() && npcLoc.distanceSquared(location) < 1) {
            NMS.setHeadYaw(getEntity(), location.getYaw());
        }
    }

    @Override
    public String toString() {
        EntityType mobType = hasTrait(MobType.class) ? getTraitNullable(MobType.class).getType() : null;
        return getId() + "{" + getName() + ", " + mobType + "}";
    }

    @Override
    public void update() {
        try {
            super.update();
            if (!isSpawned()) {
                resetCachedCoord();
                return;
            }

            if (data().has(NPC.Metadata.ACTIVATION_RANGE)) {
                int range = data().get(NPC.Metadata.ACTIVATION_RANGE);
                if (range == -1 || CitizensAPI.getLocationLookup().getNearbyPlayers(getStoredLocation(), range)
                        .iterator().hasNext()) {
                    NMS.activate(getEntity());
                }
            }

            if (navigator.isNavigating()) {
                if (data().get(NPC.Metadata.SWIMMING, true)) {
                    Location currentDest = navigator.getPathStrategy().getCurrentDestination();
                    if (currentDest == null || currentDest.getY() > getStoredLocation().getY()) {
                        NMS.trySwim(getEntity());
                    }
                }
            } else if (data().<Boolean> get(NPC.Metadata.SWIMMING, !SwimmingExaminer.isWaterMob(getEntity()))) {
                Gravity trait = getTraitNullable(Gravity.class);
                if (trait == null || trait.hasGravity()) {
                    NMS.trySwim(getEntity());
                }
            }

            navigator.run();
            if (SUPPORT_GLOWING) {
                try {
                    getEntity().setGlowing(data().get(NPC.Metadata.GLOWING, false));
                } catch (NoSuchMethodError e) {
                    SUPPORT_GLOWING = false;
                }
            }

            boolean isLiving = getEntity() instanceof LivingEntity;
            int packetUpdateDelay = data().get(NPC.Metadata.PACKET_UPDATE_DELAY, Setting.PACKET_UPDATE_DELAY.asInt());
            if (updateCounter++ > packetUpdateDelay) {
                if (Setting.KEEP_CHUNKS_LOADED.asBoolean()) {
                    ChunkCoord currentCoord = new ChunkCoord(getStoredLocation());
                    if (!currentCoord.equals(cachedCoord)) {
                        resetCachedCoord();
                        currentCoord.setForceLoaded(true);
                        CHUNK_LOADERS.put(currentCoord, this);
                        cachedCoord = currentCoord;
                    }
                }
                if (isLiving) {
                    updateScoreboard();
                }
                updateCounter = 0;
            }

            updateCustomNameVisibility();

            if (isLiving) {
                NMS.setKnockbackResistance((LivingEntity) getEntity(), isProtected() ? 1D : 0D);
                if (SUPPORT_PICKUP_ITEMS) {
                    try {
                        ((LivingEntity) getEntity())
                                .setCanPickupItems(data().get(NPC.Metadata.PICKUP_ITEMS, !isProtected()));
                    } catch (Throwable t) {
                        SUPPORT_PICKUP_ITEMS = false;
                    }
                }
            }

            if (isLiving && getEntity() instanceof Player) {
                updateUsingItemState((Player) getEntity());
                if (data().has(NPC.Metadata.SNEAKING) && !hasTrait(SneakTrait.class)) {
                    addTrait(SneakTrait.class);
                }
            }

            if (SUPPORT_SILENT && data().has(NPC.SILENT_METADATA)) {
                try {
                    getEntity().setSilent(Boolean.parseBoolean(data().get(NPC.Metadata.SILENT).toString()));
                } catch (NoSuchMethodError e) {
                    SUPPORT_SILENT = false;
                }
            }
        } catch (Exception ex) {
            Throwable error = Throwables.getRootCause(ex);
            Messaging.logTr(Messages.EXCEPTION_UPDATING_NPC, getId(), error.getMessage());
            error.printStackTrace();
        }
    }

    @Override
    public void updateCustomName() {
        super.updateCustomName();
    }

    private void updateCustomNameVisibility() {
        String nameplateVisible = data().<Object> get(NPC.Metadata.NAMEPLATE_VISIBLE, true).toString();
        if (requiresNameHologram()) {
            nameplateVisible = "false";
        }
        if (nameplateVisible.equals("true") || nameplateVisible.equals("hover")) {
            updateCustomName();
        }
        getEntity().setCustomNameVisible(Boolean.parseBoolean(nameplateVisible));
    }

    private void updateFlyableState() {
        EntityType type = isSpawned() ? getEntity().getType() : getOrAddTrait(MobType.class).getType();
        if (type == null)
            return;
        if (!Util.isAlwaysFlyable(type))
            return;
        if (!data().has(NPC.FLYABLE_METADATA)) {
            data().setPersistent(NPC.FLYABLE_METADATA, true);
        }
        if (!hasTrait(Gravity.class)) {
            getOrAddTrait(Gravity.class).setEnabled(true);
        }
    }

    private void updateScoreboard() {
        if (data().has(NPC.Metadata.SCOREBOARD_FAKE_TEAM_NAME)) {
            getOrAddTrait(ScoreboardTrait.class).update();
        }
    }

    private void updateUsingItemState(Player player) {
        boolean useItem = data().get(NPC.Metadata.USING_HELD_ITEM, false),
                offhand = data().get(NPC.Metadata.USING_OFFHAND_ITEM, false);
        if (!SUPPORT_USE_ITEM)
            return;
        try {
            if (useItem) {
                NMS.playAnimation(PlayerAnimation.STOP_USE_ITEM, player, 64);
                NMS.playAnimation(PlayerAnimation.START_USE_MAINHAND_ITEM, player, 64);
            } else if (offhand) {
                NMS.playAnimation(PlayerAnimation.STOP_USE_ITEM, player, 64);
                NMS.playAnimation(PlayerAnimation.START_USE_OFFHAND_ITEM, player, 64);
            }
        } catch (UnsupportedOperationException ex) {
            SUPPORT_USE_ITEM = false;
        }
    }

    private static final Location CACHE_LOCATION = new Location(null, 0, 0, 0);
    private static final SetMultimap<ChunkCoord, NPC> CHUNK_LOADERS = HashMultimap.create();
    private static final String NPC_METADATA_MARKER = "NPC";
    private static boolean SUPPORT_GLOWING = true;
    private static boolean SUPPORT_NODAMAGE_TICKS = true;
    private static boolean SUPPORT_PICKUP_ITEMS = true;
    private static boolean SUPPORT_SILENT = true;
    private static boolean SUPPORT_USE_ITEM = true;
}
