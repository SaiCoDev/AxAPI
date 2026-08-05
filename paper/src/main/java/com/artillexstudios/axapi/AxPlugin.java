package com.artillexstudios.axapi;

import com.artillexstudios.axapi.command.AxAPICommand;
import com.artillexstudios.axapi.config.adapters.ItemStackAdapter;
import com.artillexstudios.axapi.config.adapters.TypeAdapterHolder;
import com.artillexstudios.axapi.config.adapters.WrappedItemStackAdapter;
import com.artillexstudios.axapi.dependencies.DependencyManagerWrapper;
import com.artillexstudios.axapi.dependencies.UnsafeDependencyLoader;
import com.artillexstudios.axapi.dependency.DependencyContainer;
import com.artillexstudios.axapi.gui.AnvilListener;
import com.artillexstudios.axapi.gui.inventory.InventoryUpdater;
import com.artillexstudios.axapi.gui.inventory.listener.InventoryClickListener;
import com.artillexstudios.axapi.items.WrappedItemStack;
import com.artillexstudios.axapi.items.component.DataComponents;
import com.artillexstudios.axapi.libraries.LibraryDownloader;
import com.artillexstudios.axapi.nms.NMSHandlers;
import com.artillexstudios.axapi.placeholders.PaperPlaceholderHandler;
import com.artillexstudios.axapi.placeholders.PlaceholderAPIHook;
import com.artillexstudios.axapi.scheduler.Scheduler;
import com.artillexstudios.axapi.utils.CommandUtils;
import com.artillexstudios.axapi.utils.ComponentSerializer;
import com.artillexstudios.axapi.utils.Nameable;
import com.artillexstudios.axapi.utils.PaperNameable;
import com.artillexstudios.axapi.utils.UncheckedUtils;
import com.artillexstudios.axapi.utils.Version;
import com.artillexstudios.axapi.utils.featureflags.FeatureFlags;
import com.artillexstudios.axapi.utils.file.FileUtils;
import com.artillexstudios.axapi.utils.file.PaperFileUtils;
import com.artillexstudios.axapi.utils.logging.LogUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import revxrsal.commands.CommandHandler;
import revxrsal.commands.bukkit.BukkitCommandHandler;

import java.nio.file.Path;

public abstract class AxPlugin extends JavaPlugin {

    public AxPlugin() {
        DependencyContainer.register(FileUtils.class, new PaperFileUtils(this));
        DependencyContainer.register(Nameable.class, new PaperNameable(this));

        PaperPlaceholderHandler.registerTransformer(Player.class, OfflinePlayer.class, player -> player);
        PaperPlaceholderHandler.registerTransformer(OfflinePlayer.class, Player.class, OfflinePlayer::getPlayer);
        TypeAdapterHolder.registerExtraAdapter(WrappedItemStack.class, new WrappedItemStackAdapter());
        TypeAdapterHolder.registerExtraAdapter(ItemStack.class, new ItemStackAdapter());

        FeatureFlags.refresh();
        this.updateFlags();

        Path librariesPath = this.getDataFolder().toPath().getParent().resolve("AxAPI").resolve("libraries").resolve(this.getName());
        LibraryDownloader manager = new LibraryDownloader(librariesPath);
        DependencyManagerWrapper wrapper = new DependencyManagerWrapper(manager);
        wrapper.repository("https://repo.artillex-studios.com/releases/");
        wrapper.relocate("org{}apache{}commons{}math3", "com.artillexstudios.axapi.libs.math3");
        wrapper.relocate("com{}github{}benmanes", "com.artillexstudios.axapi.libs.caffeine");

        Version.downloadVersion(wrapper);
        this.dependencies(wrapper);

        wrapper.dependency("org{}apache{}commons:commons-math3:3.6.1");
        wrapper.dependency("com{}github{}ben-manes{}caffeine:caffeine:3.2.3");
        try {
            Class.forName("net.kyori.adventure.Adventure", false, this.getClass().getClassLoader());
        } catch (ClassNotFoundException exception) {
            wrapper.dependency("net{}kyori:adventure-api:4.26.1");
        }

        UnsafeDependencyLoader unsafeDependencyLoader = new UnsafeDependencyLoader();
        for (Path path : wrapper.wrapped().getLibraryPaths()) {
            if (FeatureFlags.DEBUG.get()) {
                LogUtils.debug("Loading library from path: {}", path);
            }
            unsafeDependencyLoader.loadUnsafeLibrary(UncheckedUtils.unsafeCast(this.getClassLoader()), path);
        }
    }

    public void updateFlags() {

    }

    @Override
    public void onEnable() {
        if (!NMSHandlers.British.initialise(this)) {
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        if (!CommandUtils.isRegistered("axapi")) {
            CommandHandler handler = BukkitCommandHandler.create(this);
            handler.register(new AxAPICommand());
        }

        ComponentSerializer.INSTANCE.refresh();
        DataComponents.setDataComponentImpl(NMSHandlers.getNmsHandler().dataComponents());
        Scheduler.scheduler.init(this);

        Bukkit.getPluginManager().registerEvents(new AnvilListener(), this);
        if (FeatureFlags.ENABLE_GUI_LISTENERS.get()) {
            Bukkit.getPluginManager().registerEvents(new InventoryClickListener(), this);
        }

        if (FeatureFlags.USE_INVENTORY_UPDATER.get()) {
            InventoryUpdater.INSTANCE.start(this);
        }

        this.enable();

        if (FeatureFlags.PLACEHOLDER_API_HOOK.get() && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderAPIHook(this).register();
        }
    }

    public void enable() {

    }

    @Override
    public void onLoad() {
        this.load();
    }

    public void dependencies(DependencyManagerWrapper manager) {

    }

    public void load() {

    }

    @Override
    public void onDisable() {
        this.disable();
        Scheduler.get().cancelAll();
        InventoryUpdater.INSTANCE.shutdown();
    }

    public void disable() {

    }

    public void reload() {

    }

    public long reloadWithTime() {
        long start = System.currentTimeMillis();
        this.reload();

        return System.currentTimeMillis() - start;
    }
}