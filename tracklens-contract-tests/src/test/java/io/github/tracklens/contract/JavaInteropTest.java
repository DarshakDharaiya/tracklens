package io.github.tracklens.contract;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import android.app.Application;
import android.net.Uri;
import android.os.Bundle;
import androidx.test.core.app.ApplicationProvider;
import io.github.tracklens.AnalyticsDelegate;
import io.github.tracklens.BubbleConfig;
import io.github.tracklens.ExportCallback;
import io.github.tracklens.ExportFormat;
import io.github.tracklens.RedactionConfig;
import io.github.tracklens.RedactionPattern;
import io.github.tracklens.ScreenNameResolver;
import io.github.tracklens.SessionConfig;
import io.github.tracklens.Severity;
import io.github.tracklens.Subscription;
import io.github.tracklens.TrackLens;
import io.github.tracklens.TrackLensBundleExtensions;
import io.github.tracklens.TrackLensBundles;
import io.github.tracklens.TrackLensConfig;
import io.github.tracklens.ValidationConfig;
import io.github.tracklens.ValidationIssue;
import io.github.tracklens.ValidationIssues;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Written the way a Java host app would use TrackLens. Compiling it proves the API is usable from Java (static calls,
 * overloads, builders, SAM lambdas, default interface methods); running it checks the basics behave.
 */
@RunWith(RobolectricTestRunner.class)
public class JavaInteropTest {

    @Test
    public void facadeAndBuildersAreUsableFromJava() {
        Application app = ApplicationProvider.getApplicationContext();
        TrackLensConfig config = new TrackLensConfig.Builder()
                .enabled(true)
                .liveBufferSize(500)
                .sessions(new SessionConfig.Builder().backgroundTimeout(30, TimeUnit.SECONDS).build())
                .bubble(BubbleConfig.DEFAULT)
                .redaction(new RedactionConfig.Builder().keys("*_token").patterns(RedactionPattern.IPV4).build())
                .validation(new ValidationConfig.Builder()
                        .severity("FB_PARAM_VALUE_TOO_LONG", Severity.ERROR)
                        .disable("FB_RESERVED_PREFIX")
                        .rule(event -> Collections.emptyList())
                        .build())
                .categorizer(eventName -> eventName.startsWith("checkout_") ? "commerce" : null)
                .build();
        assertEquals(30_000L, config.getSessions().getBackgroundTimeoutMillis());

        TrackLens.install(app, config);
        TrackLens.install(app);

        Map<String, Object> params = new HashMap<>();
        params.put("price", 9.99);
        TrackLens.log("app_open");
        TrackLens.log("purchase", params);
        TrackLens.log("purchase", params, "firebase");
        TrackLens.log("legacy", new Bundle());
        TrackLens.setUserProperty("tier", "gold");
        TrackLens.setUserId(null);
        TrackLens.setScreen("Home");
        TrackLens.setScreen("Home", "com.example.HomeActivity");

        TrackLens.startSession();
        TrackLens.startSession("Checkout regression - build 142");
        TrackLens.setSessionNotes("notes");
        TrackLens.endSession();

        boolean enabled = TrackLens.isEnabled();
        TrackLens.setEnabled(enabled);
        assertNotNull(TrackLens.getLibraryVersion());
        TrackLens.getCurrentSessionId();

        Subscription subscription = TrackLens.addListener(event -> { });
        subscription.cancel();
        TrackLens.clearAllData(success -> { });
        TrackLens.clearAllData();
        TrackLens.exportSession(app, "no-such-session", ExportFormat.JSON, new ExportCallback() {
            @Override
            public void onExported(Uri uri, String mimeType) { }

            @Override
            public void onFailed(String reason) { }
        });

        ValidationIssue issue = ValidationIssues.create("CODE", Severity.INFO, "message");
        assertNull(issue.getParamKey());
        assertEquals("default", AnalyticsDelegate.DEFAULT_SOURCE);
        assertEquals("SessionConfig", ScreenNameResolver.SIMPLE_CLASS_NAME.resolve(SessionConfig.DEFAULT));
    }

    @Test
    public void javaDelegatesImplementingOnlyLogEventAreForwarded() {
        List<String> names = new ArrayList<>();
        AnalyticsDelegate lambda = (name, eventParams) -> names.add(name);
        AnalyticsDelegate wrapped = TrackLens.wrap("firebase", lambda);

        wrapped.logEvent("checkout_started", Collections.emptyMap());
        wrapped.setUserProperty("tier", null);
        wrapped.setUserId("user-42");
        TrackLens.wrap(new OnlyLogEvent()).setUserId("user-42");

        assertEquals(Collections.singletonList("checkout_started"), names);
    }

    @Test
    public void bundleHelpersAreUsableFromJava() {
        Map<String, Object> params = new HashMap<>();
        params.put("item_id", "SKU-1");

        Bundle viaObject = TrackLensBundles.toBundle(params);
        Bundle viaExtension = TrackLensBundleExtensions.toBundle(params);

        assertEquals("SKU-1", viaObject.getString("item_id"));
        assertEquals("SKU-1", viaExtension.getString("item_id"));
        Map<String, Object> back = TrackLensBundles.toMap(viaObject);
        assertSame(String.class, back.get("item_id").getClass());
    }

    /** Implements only the one abstract method; the other two are Java default methods. */
    private static final class OnlyLogEvent implements AnalyticsDelegate {
        @Override
        public void logEvent(String name, Map<String, ?> params) { }
    }
}
