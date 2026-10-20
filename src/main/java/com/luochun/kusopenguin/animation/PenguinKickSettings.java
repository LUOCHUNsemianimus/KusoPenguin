package com.luochun.kusopenguin.animation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Server-owned per-player skill settings, independent of player entity lifetime. */
public final class PenguinKickSettings extends SavedData {
    public static final double MAX_DAMAGE_MULTIPLIER=100;
    public static final int MAX_KNOCKBACK_LEVEL=20;
    public record Values(double damageMultiplier,int knockbackLevel) {
        public Values {
            if(!Double.isFinite(damageMultiplier)||damageMultiplier<0||damageMultiplier>MAX_DAMAGE_MULTIPLIER
                ||knockbackLevel<0||knockbackLevel>MAX_KNOCKBACK_LEVEL)
                throw new IllegalArgumentException("Invalid flying-kick settings");
        }
        public double knockbackStrength(){return knockbackLevel*.5;}
    }
    public static final Values DEFAULT=new Values(1,2);
    private static final Factory<PenguinKickSettings> FACTORY=new Factory<>(PenguinKickSettings::new,PenguinKickSettings::load);
    private final Map<UUID,Values> players=new HashMap<>();
    public static PenguinKickSettings get(MinecraftServer server){
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY,"kuso_penguin_flyingkick");
    }
    public Values values(UUID player){return players.getOrDefault(player,DEFAULT);}
    public void set(UUID player,Values value){
        if(value.equals(DEFAULT))players.remove(player);else players.put(player,value);
        setDirty();
    }
    private static PenguinKickSettings load(CompoundTag tag,HolderLookup.Provider registries){
        PenguinKickSettings data=new PenguinKickSettings();
        ListTag entries=tag.getList("Players",Tag.TAG_COMPOUND);
        for(int i=0;i<entries.size();i++){
            CompoundTag entry=entries.getCompound(i);
            data.players.put(entry.getUUID("Player"),new Values(entry.getDouble("DamageMultiplier"),entry.getInt("KnockbackLevel")));
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){
        ListTag entries=new ListTag();
        players.forEach((id,value)->{
            CompoundTag entry=new CompoundTag();entry.putUUID("Player",id);
            entry.putDouble("DamageMultiplier",value.damageMultiplier());entry.putInt("KnockbackLevel",value.knockbackLevel());entries.add(entry);
        });
        tag.put("Players",entries);return tag;
    }
}
