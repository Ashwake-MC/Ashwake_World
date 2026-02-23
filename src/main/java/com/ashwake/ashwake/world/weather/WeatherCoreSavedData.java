package com.ashwake.ashwake.world.weather;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class WeatherCoreSavedData extends SavedData {
    private static final String DATA_NAME = "ashwake_weather_core_data";

    private boolean initialized;
    private String currentStateId;
    private String nextStateId;
    private String phaseId;
    private int ticksRemaining;
    private int totalTicks;
    private int omenTicks;
    private long eclipseRestoreDayTime;

    public WeatherCoreSavedData() {
        this.initialized = false;
        this.currentStateId = WeatherCoreState.DAWN_BLESSING.id();
        this.nextStateId = "";
        this.phaseId = WeatherCorePhase.ACTIVE.id();
        this.ticksRemaining = 0;
        this.totalTicks = 0;
        this.omenTicks = 0;
        this.eclipseRestoreDayTime = -1L;
    }

    public static WeatherCoreSavedData get(ServerLevel level) {
        SavedData.Factory<WeatherCoreSavedData> factory = new SavedData.Factory<>(
                WeatherCoreSavedData::new,
                WeatherCoreSavedData::load);
        return level.getDataStorage().computeIfAbsent(factory, DATA_NAME);
    }

    private static WeatherCoreSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        WeatherCoreSavedData data = new WeatherCoreSavedData();
        data.initialized = tag.getBoolean("initialized");
        data.currentStateId = tag.getString("currentStateId");
        data.nextStateId = tag.getString("nextStateId");
        data.phaseId = tag.getString("phaseId");
        data.ticksRemaining = tag.getInt("ticksRemaining");
        data.totalTicks = tag.getInt("totalTicks");
        data.omenTicks = tag.getInt("omenTicks");
        data.eclipseRestoreDayTime = tag.contains("eclipseRestoreDayTime") ? tag.getLong("eclipseRestoreDayTime") : -1L;
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putBoolean("initialized", this.initialized);
        tag.putString("currentStateId", this.currentStateId);
        tag.putString("nextStateId", this.nextStateId);
        tag.putString("phaseId", this.phaseId);
        tag.putInt("ticksRemaining", this.ticksRemaining);
        tag.putInt("totalTicks", this.totalTicks);
        tag.putInt("omenTicks", this.omenTicks);
        tag.putLong("eclipseRestoreDayTime", this.eclipseRestoreDayTime);
        return tag;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public WeatherCoreState getCurrentState() {
        return WeatherCoreState.fromId(currentStateId);
    }

    public WeatherCoreState getNextState() {
        return nextStateId == null || nextStateId.isBlank() ? null : WeatherCoreState.fromId(nextStateId);
    }

    public WeatherCorePhase getPhase() {
        return WeatherCorePhase.fromId(phaseId);
    }

    public int getTicksRemaining() {
        return ticksRemaining;
    }

    public int getTotalTicks() {
        return totalTicks;
    }

    public int getOmenTicks() {
        return omenTicks;
    }

    public long getEclipseRestoreDayTime() {
        return eclipseRestoreDayTime;
    }

    public void initializeCycle(WeatherCoreState currentState, int ticksRemaining, int totalTicks, int omenTicks) {
        this.initialized = true;
        this.currentStateId = currentState.id();
        this.nextStateId = "";
        this.phaseId = WeatherCorePhase.ACTIVE.id();
        this.ticksRemaining = ticksRemaining;
        this.totalTicks = totalTicks;
        this.omenTicks = omenTicks;
        this.setDirty();
    }

    public void setCurrentState(WeatherCoreState state) {
        this.currentStateId = state.id();
        this.setDirty();
    }

    public void setNextState(WeatherCoreState state) {
        this.nextStateId = state == null ? "" : state.id();
        this.setDirty();
    }

    public void setPhase(WeatherCorePhase phase) {
        this.phaseId = phase.id();
        this.setDirty();
    }

    public void setTicksRemaining(int ticksRemaining) {
        this.ticksRemaining = ticksRemaining;
        this.setDirty();
    }

    public void setTotalTicks(int totalTicks) {
        this.totalTicks = totalTicks;
        this.setDirty();
    }

    public void setOmenTicks(int omenTicks) {
        this.omenTicks = omenTicks;
        this.setDirty();
    }

    public void setEclipseRestoreDayTime(long eclipseRestoreDayTime) {
        this.eclipseRestoreDayTime = eclipseRestoreDayTime;
        this.setDirty();
    }
}
