package tech.chron0.burpcord.core;

import tech.chron0.burpcord.config.BurpcordConfig;
import tech.chron0.burpcord.discord.ActivityProvider;
import tech.chron0.burpcord.discord.DiscordRPCManager;
import tech.chron0.burpcord.listeners.*;
import tech.chron0.burpcord.listeners.providers.*;
import tech.chron0.burpcord.listeners.handlers.*;
import tech.chron0.burpcord.ui.BurpcordBurpSettingsPanel;
import tech.chron0.burpcord.ui.BurpcordSettingsTab;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.Version;
import burp.api.montoya.core.Registration;
import burp.api.montoya.extension.ExtensionUnloadingHandler;

import java.util.Arrays;
import java.util.List;

import javax.swing.SwingUtilities;

/**
 * <h1>Burpcord Extension</h1>
 * <p>
 * The main entry point for the Burpcord extension.
 * </p>
 * 
 * <h2>Overview</h2>
 * <p>
 * Burpcord integrates Discord Rich Presence into Burp Suite, allowing users to
 * share their security research status
 * and tool usage with colleagues or communities on Discord.
 * </p>
 * 
 * <h3>Features</h3>
 * <ul>
 * <li>Manage Discord Rich Presence connection.</li>
 * <li>Track activity across Proxy, Scanner, Repeater, Intruder, and more.</li>
 * <li>Customize presence status and details.</li>
 * </ul>
 * 
 * @author Jon Marien
 * @see <a href="https://discord.gg/wXWJp9M9Cq">Burp Suite Discord</a>
 */
public class BurpcordExtension implements BurpExtension, ExtensionUnloadingHandler {

        private DiscordRPCManager rpcManager;
        private Thread shutdownHook;
        private BurpcordSiteMapProvider siteMapProvider;
        private Registration settingsPanelRegistration;

        @Override
        public void initialize(MontoyaApi api) {
                api.extension().setName("Burpcord");

                // Config
                BurpcordConfig config = new BurpcordConfig(api.persistence().preferences());

                Version version = api.burpSuite().version();
                BurpSuiteInfo burpSuiteInfo = new BurpSuiteInfo(
                                version.name(),
                                version.toString(),
                                version.edition());

                // Core RPC Manager
                rpcManager = new DiscordRPCManager(config, burpSuiteInfo);

                // Initialize Components
                initializeComponents(api, config, rpcManager);

                // UI — Burp Settings dialog (BApp Store: prefer registerSettingsPanel over suite tab)
                SwingUtilities.invokeLater(() -> {
                        BurpcordSettingsTab settingsRoot = new BurpcordSettingsTab(api, config, rpcManager);
                        api.userInterface().applyThemeToComponent(settingsRoot);
                        settingsPanelRegistration = api.userInterface()
                                        .registerSettingsPanel(new BurpcordBurpSettingsPanel(settingsRoot));
                });

                // Register Unload
                api.extension().registerUnloadingHandler(this);

                // JVM shutdown hook as safety net for abnormal exits
                shutdownHook = new Thread(() -> {
                        if (rpcManager != null) {
                                rpcManager.shutdown();
                        }
                }, "Burpcord-Shutdown-Hook");
                Runtime.getRuntime().addShutdownHook(shutdownHook);

                logBanner();
                BurpcordSettingsTab.log("Burpcord v" + BurpcordConstants.VERSION + " loaded.");
                BurpcordSettingsTab.log("Detected: " + burpSuiteInfo.fullVersionString());

                logEnabledFeatures(config);

                // Start RPC
                BurpcordSettingsTab.log("Initializing Discord RPC...");
                rpcManager.initialize();
        }

        @Override
        public void extensionUnloaded() {
                if (siteMapProvider != null) {
                        siteMapProvider.shutdown();
                        siteMapProvider = null;
                }
                if (settingsPanelRegistration != null) {
                        settingsPanelRegistration.deregister();
                        settingsPanelRegistration = null;
                }
                if (rpcManager != null) {
                        rpcManager.shutdown();
                }
                // Remove the shutdown hook since we've already cleaned up
                try {
                        if (shutdownHook != null) {
                                Runtime.getRuntime().removeShutdownHook(shutdownHook);
                        }
                } catch (IllegalStateException ignored) {
                        // JVM is already shutting down — hook will run but shutdown() is idempotent
                }
                BurpcordSettingsTab.log("Burpcord unloaded.");
        }

        public static void logBanner() {
                BurpcordSettingsTab.log("========================================");
                BurpcordSettingsTab.log("   Burpcord - Discord RPC for Burp");
                BurpcordSettingsTab.log("========================================");
        }

        public static void logEnabledFeatures(BurpcordConfig config) {
                StringBuilder sb = new StringBuilder("Enabled Features: ");
                if (config.isShowIntercept())
                        sb.append("[Intercept] ");
                if (config.isShowScan())
                        sb.append("[Scanner] ");
                if (config.isShowProxy())
                        sb.append("[Proxy] ");
                if (config.isShowRepeater())
                        sb.append("[Repeater] ");
                if (config.isShowIntruder())
                        sb.append("[Intruder] ");
                if (config.isShowSiteMap())
                        sb.append("[Site Map] ");
                if (config.isShowScope())
                        sb.append("[Scope] ");
                if (config.isShowCollaborator())
                        sb.append("[Collaborator] ");
                if (config.isShowWebSockets())
                        sb.append("[WebSocket] ");
                BurpcordSettingsTab.log(sb.toString());
        }

        private void initializeComponents(MontoyaApi api, BurpcordConfig config, DiscordRPCManager rpcManager) {
                siteMapProvider = new BurpcordSiteMapProvider(api, config);
                // Initialize all components - Priority is now defined within each class via
                // getPriority()
                List<ActivityProvider> components = Arrays.asList(
                                new BurpcordProxyHandler(config),
                                new BurpcordScannerListener(config),
                                new BurpcordRepeaterListener(config),
                                new BurpcordIntruderListener(config),
                                new BurpcordWebSocketListener(config),
                                siteMapProvider,
                                new BurpcordScopeProvider(api, config),
                                new BurpcordCollaboratorProvider(api, config));

                // Register Providers (Manager handles sorting by priority)
                rpcManager.registerProviders(components);

                // Auto-register Burp Components
                for (ActivityProvider component : components) {
                        if (component instanceof BurpComponent) {
                                ((BurpComponent) component).register(api);
                        }
                }
        }
}
