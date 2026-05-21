package tech.chron0.burpcord.discord;

import tech.chron0.burpcord.config.BurpcordConfig;
import tech.chron0.burpcord.core.BurpSuiteInfo;
import tech.chron0.burpcord.ui.BurpcordSettingsTab;

import com.jagrosh.discordipc.IPCClient;
import com.jagrosh.discordipc.IPCListener;
import com.jagrosh.discordipc.entities.Packet;
import com.jagrosh.discordipc.entities.RichPresence;
import com.jagrosh.discordipc.entities.User;
import com.jagrosh.discordipc.entities.pipe.PipeStatus;
import com.google.gson.JsonObject;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <h1>Discord RPC Manager</h1>
 * <p>
 * Central controller for the Discord Rich Presence integration.
 * </p>
 * 
 * <h2>Functionality</h2>
 * <ul>
 * <li><b>Connection Handling:</b> Manages the lifecycle of the IPC connection
 * to the Discord client.</li>
 * <li><b>Activity Registry:</b> Maintains a prioritized list of
 * {@link ActivityProvider}s.</li>
 * <li><b>Scheduler:</b> Periodically refreshes the user's presence based on the
 * configured interval.</li>
 * </ul>
 * 
 * @author Jon Marien
 */
public class DiscordRPCManager {

    private final BurpcordConfig config;
    private final BurpSuiteInfo burpSuiteInfo;
    private final List<ActivityProvider> providers = new ArrayList<>();

    // IPC Client
    private IPCClient client;
    private ScheduledExecutorService scheduler;
    private final AtomicBoolean shutdownCalled = new AtomicBoolean(false);
    private final OffsetDateTime startTime;
    private int rotationIndex = 0;

    public DiscordRPCManager(BurpcordConfig config, BurpSuiteInfo burpSuiteInfo) {
        this.config = config;
        this.burpSuiteInfo = burpSuiteInfo;
        this.startTime = OffsetDateTime.now();
    }

    /**
     * Registers a new activity provider.
     * 
     * @param provider The provider implementation to register.
     */
    public void registerProvider(ActivityProvider provider) {
        providers.add(provider);
        providers.sort(java.util.Comparator.comparingInt(ActivityProvider::getPriority));
    }

    /**
     * Registers multiple activity providers at once.
     * 
     * @param newProviders Variable arguments of providers to register.
     */
    public void registerProviders(ActivityProvider... newProviders) {
        java.util.Collections.addAll(providers, newProviders);
        providers.sort(java.util.Comparator.comparingInt(ActivityProvider::getPriority));
    }

    public void registerProviders(List<ActivityProvider> newProviders) {
        providers.addAll(newProviders);
        providers.sort(java.util.Comparator.comparingInt(ActivityProvider::getPriority));
    }

    private static final int MAX_CONNECT_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 3000;
    private static final long MAX_RETRY_DELAY_MS = 15000;

    /**
     * Initializes the IPC client and starts the update scheduler.
     * <p>
     * Connection is attempted on a background thread with exponential backoff
     * retries to handle transient Discord IPC failures (e.g., Discord not yet
     * running when Burp Suite starts).
     * </p>
     */
    public void initialize() {
        if (!config.isRpcEnabled())
            return;

        Thread connectThread = new Thread(() -> {
            try {
                connectWithRetry();
            } catch (Exception e) {
                BurpcordSettingsTab.log("Burpcord: Failed to connect to Discord IPC after "
                        + MAX_CONNECT_RETRIES + " attempts: " + e.getMessage());
                BurpcordSettingsTab.log(
                        "Hint: Ensure Discord is fully loaded and you are logged in, then click 'Reload Discord RPC'.");
                System.err.println("Burpcord: Failed to connect to Discord IPC.");
                e.printStackTrace();
            }
        }, "Burpcord-IPC-Connect");
        connectThread.setDaemon(true);
        connectThread.start();
    }

    /**
     * Attempts to connect to Discord IPC with exponential backoff retries.
     * <p>
     * Uses capped exponential backoff between attempts. Fails fast on
     * invalid App IDs.
     * </p>
     *
     * @throws Exception if all retry attempts are exhausted.
     */
    private void connectWithRetry() throws Exception {
        long appId = Long.parseLong(config.getAppId());

        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_CONNECT_RETRIES; attempt++) {
            try {
                client = new IPCClient(appId);
                client.setListener(createIPCListener());
                client.connect();
                startScheduler();
                return; // Success
            } catch (Exception e) {
                lastException = e;
                if (isAppIdNotFoundError(e)) {
                    BurpcordSettingsTab.log("Burpcord: Discord App ID '" + appId
                            + "' is invalid or not registered for RPC (HTTP 404).");
                    BurpcordSettingsTab.log("Hint: Check your App ID in Settings, or reset to the default.");
                    throw e;
                }
                if (attempt < MAX_CONNECT_RETRIES) {
                    long delay = Math.min(INITIAL_RETRY_DELAY_MS * (1L << (attempt - 1)), MAX_RETRY_DELAY_MS);
                    BurpcordSettingsTab.log("Discord IPC connect attempt " + attempt + "/" + MAX_CONNECT_RETRIES
                            + " failed: " + e.getMessage() + " — retrying in " + (delay / 1000) + "s...");
                    Thread.sleep(delay);
                }
            }
        }
        throw lastException;
    }

    /**
     * Checks if the exception indicates the Discord App ID is not registered
     * (HTTP 404 from Discord's oauth2 applications endpoint).
     */
    private static boolean isAppIdNotFoundError(Exception e) {
        String msg = e.getMessage();
        if (msg == null)
            return false;
        // The library surfaces this as an error when Discord returns 404 for the app
        return msg.contains("404") || msg.contains("not found") || msg.contains("Not Found");
    }

    /**
     * Creates the {@link IPCListener} for handling IPC lifecycle events.
     */
    private IPCListener createIPCListener() {
        return new IPCListener() {
            @Override
            public void onReady(IPCClient client) {
                BurpcordSettingsTab.log("Connected to Discord IPC.");
                BurpcordSettingsTab.updateConnectionStatusStatic(true);
                updatePresence("Ready");
            }

            @Override
            public void onDisconnect(IPCClient client, Throwable t) {
                BurpcordSettingsTab.log("Disconnected from Discord IPC.");
                BurpcordSettingsTab.updateConnectionStatusStatic(false);
            }

            @Override
            public void onPacketSent(IPCClient client, Packet packet) {
                // No-op
            }

            @Override
            public void onPacketReceived(IPCClient client, Packet packet) {
                // No-op
            }

            @Override
            public void onActivityJoin(IPCClient client, String secret) {
                // No-op
            }

            @Override
            public void onActivitySpectate(IPCClient client, String secret) {
                // No-op
            }

            @Override
            public void onActivityJoinRequest(IPCClient client, String secret, User user) {
                // No-op
            }

            @Override
            public void onClose(IPCClient client, JsonObject json) {
                BurpcordSettingsTab.log("Discord IPC Closed.");
                BurpcordSettingsTab.updateConnectionStatusStatic(false);
            }
        };
    }

    /**
     * Shuts down the scheduler and closes the IPC connection.
     */
    public void shutdown() {
        if (!shutdownCalled.compareAndSet(false, true)) {
            return; // Already shut down
        }
        rotationIndex = 0;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        if (client != null && client.getStatus() == PipeStatus.CONNECTED) {
            try {
                client.sendRichPresence(null); // Clear activity from Discord
            } catch (Exception e) {
                // Best-effort clear; log but don't prevent close
            }
            client.close();
        }
    }

    /**
     * Restarts the scheduler with the current configuration.
     */
    public void restartScheduler() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        startScheduler();
    }

    private void startScheduler() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::updateLoop, 0, config.getUpdateInterval(), TimeUnit.SECONDS);
    }

    private void updateLoop() {
        if (client == null || client.getStatus() != PipeStatus.CONNECTED) {
            return;
        }
        updatePresence(null);
    }

    /**
     * Constructs and sends the Rich Presence update to Discord.
     * <p>
     * Iterates through registered providers to determine the current activity
     * state.
     * If no specific provider is active, a default state is used.
     * </p>
     * 
     * @param manualState Optional manual state override.
     */
    public void updatePresence(String manualState) {
        if (client == null || client.getStatus() != PipeStatus.CONNECTED) {
            return;
        }

        RichPresence.Builder builder = new RichPresence.Builder();
        builder.setLargeImageWithTooltip("burp", burpSuiteInfo.fullVersionString());
        builder.setStartTimestamp(startTime.toEpochSecond());

        String state = manualState != null && !manualState.isEmpty() ? manualState : config.getCustomState();
        boolean providerFound = false;

        List<ActivityProvider> activeProviders = new ArrayList<>();
        for (ActivityProvider provider : providers) {
            if (provider.isActive()) {
                activeProviders.add(provider);
            }
        }

        if (!activeProviders.isEmpty()) {
            rotationIndex = Math.floorMod(rotationIndex, activeProviders.size());
            activeProviders.get(rotationIndex).updatePresence(builder);
            rotationIndex++;
            providerFound = true;
        }

        if (!providerFound) {
            builder.setDetails(burpSuiteInfo.fullVersionString());
            builder.setState(state != null && !state.isEmpty() ? state : "Security Researching");
        }

        try {
            client.sendRichPresence(builder.build());
        } catch (Exception e) {
            BurpcordSettingsTab.log("Burpcord: Failed to update presence: " + e.getMessage());
        }
    }

    /**
     * Reinitializes the entire RPC subsystem.
     */
    public void reloadRPC() {
        shutdown();
        shutdownCalled.set(false); // Reset so next initialize() cycle can shut down
        rotationIndex = 0;
        initialize();
    }
}
