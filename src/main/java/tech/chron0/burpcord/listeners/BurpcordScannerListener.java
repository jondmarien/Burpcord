package tech.chron0.burpcord.listeners;

import tech.chron0.burpcord.config.BurpcordConfig;
import tech.chron0.burpcord.discord.ActivityProvider;

import burp.api.montoya.scanner.audit.issues.AuditIssue;
import burp.api.montoya.scanner.audit.AuditIssueHandler;

import com.jagrosh.discordipc.entities.RichPresence;
import java.util.concurrent.atomic.AtomicInteger;

import burp.api.montoya.MontoyaApi;

/**
 * <h1>Burpcord Scanner Listener</h1>
 * <p>
 * Listens for audit issues identified by the Burp Scanner.
 * Provides real-time stats on vulnerabilities found.
 * </p>
 * 
 * @author Jon Marien
 */
public class BurpcordScannerListener implements AuditIssueHandler, ActivityProvider, BurpComponent {

    private static final long SCAN_ACTIVITY_TTL_MS = 60_000;

    private final BurpcordConfig config;
    private final AtomicInteger issueCount = new AtomicInteger(0);
    private long lastScanActivityTime = 0;

    public BurpcordScannerListener(BurpcordConfig config) {
        this.config = config;
    }

    @Override
    public void handleNewAuditIssue(AuditIssue auditIssue) {
        issueCount.incrementAndGet();
        lastScanActivityTime = System.currentTimeMillis();
    }

    @Override
    public boolean isActive() {
        if (!config.isShowScan()) {
            return false;
        }
        return (System.currentTimeMillis() - lastScanActivityTime) < SCAN_ACTIVITY_TTL_MS;
    }

    @Override
    public void updatePresence(RichPresence.Builder builder) {
        builder.setDetails("Scanning Targets");
        builder.setState("Issues Found: " + issueCount.get());
        builder.setSmallImageWithTooltip("scanner", "Scanner");
    }

    @Override
    public void register(MontoyaApi api) {
        api.scanner().registerAuditIssueHandler(this);
    }

    @Override
    public int getPriority() {
        return 20;
    }
}
