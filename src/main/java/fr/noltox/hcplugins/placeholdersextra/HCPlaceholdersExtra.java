package fr.noltox.hcplugins.placeholdersextra;

import fr.noltox.hcplugins.core.api.HCPluginsCore;
import fr.noltox.hcplugins.core.api.command.CoreCommandRegistration;
import fr.noltox.hcplugins.placeholdersextra.api.PlaceholderProviderRegistry;
import fr.noltox.hcplugins.placeholdersextra.command.PlaceholdersCommand;
import fr.noltox.hcplugins.placeholdersextra.provider.checkitem.CheckItemIntegration;
import fr.noltox.hcplugins.placeholdersextra.provider.luckperms.LuckPermsIntegration;
import fr.noltox.hcplugins.placeholdersextra.provider.voicechat.VoiceChatIntegration;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.logging.Level;

public final class HCPlaceholdersExtra extends JavaPlugin implements Listener {

    private static final String LUCKPERMS_PLUGIN = "LuckPerms";
    private static final String NEXO_PLUGIN = "Nexo";
    private static final String VOICE_CHAT_PLUGIN = "voicechat";
    private static final String VOICE_CHAT_INTEGRATION = "Simple Voice Chat";

    private ProviderRegistry registry;
    private PlaceholderExpansion expansion;
    private AutoCloseable luckPermsIntegration;
    private CheckItemIntegration checkItemIntegration;
    private AutoCloseable voiceChatIntegration;
    private CoreCommandRegistration commandRegistration;

    @Override
    public void onEnable() {
        try {
            if (!getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                throw new IllegalStateException("PlaceholderAPI est requis et doit être activé.");
            }
            registry = new ProviderRegistry(getLogger());
            getServer().getServicesManager().register(
                    PlaceholderProviderRegistry.class,
                    registry,
                    this,
                    ServicePriority.Normal
            );

            expansion = new HCExtraExpansion(this, registry);
            if (!expansion.register()) {
                throw new IllegalStateException("PlaceholderAPI a refusé l'expansion hcextra.");
            }

            getServer().getPluginManager().registerEvents(this, this);
            checkItemIntegration = CheckItemIntegration.start(this, registry);
            startNexoIntegration();
            startLuckPermsIntegration();
            startVoiceChatIntegration();
            PlaceholdersCommand commands = new PlaceholdersCommand(HCPluginsCore.translations(this));
            commandRegistration = HCPluginsCore.require(this).register(
                    this,
                    "placeholders",
                    "Placeholders HeavenCube",
                    List.of(),
                    commands
            );
        } catch (LinkageError | RuntimeException exception) {
            getLogger().log(Level.SEVERE,
                    "Impossible d'initialiser HCPlaceholdersExtra. Le plugin va être désactivé.",
                    exception);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        CoreCommandRegistration registeredCommands = commandRegistration;
        commandRegistration = null;
        if (registeredCommands != null) {
            try {
                registeredCommands.close();
            } catch (RuntimeException exception) {
                getLogger().log(Level.WARNING, "Impossible de désenregistrer les commandes.", exception);
            }
        }

        AutoCloseable registeredVoiceChatIntegration = voiceChatIntegration;
        voiceChatIntegration = null;
        closeIntegration(registeredVoiceChatIntegration, VOICE_CHAT_INTEGRATION);

        AutoCloseable registeredLuckPermsIntegration = luckPermsIntegration;
        luckPermsIntegration = null;
        closeIntegration(registeredLuckPermsIntegration, LUCKPERMS_PLUGIN);

        CheckItemIntegration registeredCheckItemIntegration = checkItemIntegration;
        checkItemIntegration = null;
        closeIntegration(registeredCheckItemIntegration, "CheckItem");

        PlaceholderExpansion registeredExpansion = expansion;
        expansion = null;
        if (registeredExpansion != null && registeredExpansion.isRegistered()) {
            try {
                if (!registeredExpansion.unregister()) {
                    getLogger().warning("PlaceholderAPI a refusé de désenregistrer l'expansion hcextra.");
                }
            } catch (RuntimeException exception) {
                getLogger().log(Level.WARNING,
                        "Impossible de désenregistrer l'expansion hcextra.", exception);
            }
        }

        getServer().getServicesManager().unregisterAll(this);
        ProviderRegistry currentRegistry = registry;
        registry = null;
        if (currentRegistry != null) {
            try {
                currentRegistry.close();
            } catch (RuntimeException exception) {
                getLogger().log(Level.WARNING, "Impossible de fermer le registre de placeholders.", exception);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginEnable(PluginEnableEvent event) {
        switch (event.getPlugin().getName()) {
            case LUCKPERMS_PLUGIN -> startLuckPermsIntegration();
            case NEXO_PLUGIN -> startNexoIntegration();
            case VOICE_CHAT_PLUGIN -> startVoiceChatIntegration();
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        ProviderRegistry currentRegistry = registry;
        if (currentRegistry != null) {
            currentRegistry.unregisterAll(event.getPlugin());
        }

        switch (event.getPlugin().getName()) {
            case LUCKPERMS_PLUGIN -> {
                closeIntegration(luckPermsIntegration, LUCKPERMS_PLUGIN);
                luckPermsIntegration = null;
            }
            case NEXO_PLUGIN -> stopNexoIntegration();
            case VOICE_CHAT_PLUGIN -> {
                closeIntegration(voiceChatIntegration, VOICE_CHAT_INTEGRATION);
                voiceChatIntegration = null;
            }
            default -> {
            }
        }
    }

    private void startLuckPermsIntegration() {
        if (luckPermsIntegration != null
                || registry == null
                || !getServer().getPluginManager().isPluginEnabled(LUCKPERMS_PLUGIN)) {
            return;
        }
        try {
            luckPermsIntegration = LuckPermsIntegration.start(this, registry);
        } catch (LinkageError | RuntimeException exception) {
            getLogger().log(Level.WARNING,
                    "L'intégration optionnelle LuckPerms n'a pas pu être activée.", exception);
        }
    }

    private void startVoiceChatIntegration() {
        if (voiceChatIntegration != null
                || registry == null
                || !getServer().getPluginManager().isPluginEnabled(VOICE_CHAT_PLUGIN)) {
            return;
        }
        try {
            voiceChatIntegration = VoiceChatIntegration.start(this, registry);
        } catch (LinkageError | RuntimeException exception) {
            getLogger().log(Level.WARNING,
                    "L'intégration optionnelle Simple Voice Chat n'a pas pu être activée.", exception);
        }
    }

    private void startNexoIntegration() {
        if (checkItemIntegration == null
                || !getServer().getPluginManager().isPluginEnabled(NEXO_PLUGIN)) {
            return;
        }
        try {
            checkItemIntegration.enableNexo();
        } catch (LinkageError | RuntimeException exception) {
            checkItemIntegration.disableNexo();
            getLogger().log(Level.WARNING,
                    "L'intégration optionnelle Nexo n'a pas pu être activée.", exception);
        }
    }

    private void stopNexoIntegration() {
        if (checkItemIntegration != null) {
            checkItemIntegration.disableNexo();
        }
    }

    private void closeIntegration(AutoCloseable integration, String name) {
        if (integration == null) {
            return;
        }
        try {
            integration.close();
        } catch (Exception | LinkageError exception) {
            getLogger().log(Level.WARNING, exception,
                    () -> "L'intégration " + name + " n'a pas pu être arrêtée proprement.");
        }
    }
}
