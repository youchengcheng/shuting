import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.EngineFilter;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/** Runs only the named pure tests; no Spring application, broker or existing database is started. */
public class ReviewTestLauncher {
    public static void main(String[] args) throws Exception {
        var names = new ArrayList<>(java.util.List.of("NotificationServiceTest", "NotificationMessageHandlerTest",
                "NotificationControllerTest", "NotificationTokenValidatorTest", "NotificationStreamHubTest", "NotificationPreviewServiceTest"));
        if (Boolean.getBoolean("notification.mysql.test.enabled")) names.add("NotificationMySqlIntegrationTest");
        var selectors = names.stream().map(name -> DiscoverySelectors.selectClass(
                "com.quanxiaoha.xiaohashu.notification.biz." + name)).toList();
        var request = LauncherDiscoveryRequestBuilder.request().selectors(selectors)
                .filters(EngineFilter.includeEngines("junit-jupiter")).build();
        var listener = new SummaryGeneratingListener();
        var launcher = LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);
        var summary = listener.getSummary();
        summary.printTo(new PrintWriter(System.out, true));
        summary.printFailuresTo(new PrintWriter(System.out, true));
        String counts = "{\"found\":" + summary.getTestsFoundCount() + ",\"started\":" + summary.getTestsStartedCount()
                + ",\"successful\":" + summary.getTestsSucceededCount() + ",\"failed\":" + summary.getTestsFailedCount()
                + ",\"skipped\":" + summary.getTestsSkippedCount() + ",\"aborted\":" + summary.getTestsAbortedCount() + "}";
        Files.writeString(Path.of(args[0]), counts);
        if (summary.getTestsFoundCount() == 0 || summary.getTestsFailedCount() > 0 || summary.getTestsAbortedCount() > 0) System.exit(1);
    }
}
