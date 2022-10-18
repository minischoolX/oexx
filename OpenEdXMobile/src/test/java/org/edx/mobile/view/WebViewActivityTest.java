package org.edx.mobile.view;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import android.content.pm.PackageManager;
import android.text.TextUtils;
import android.view.View;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;

import org.edx.mobile.R;
import org.edx.mobile.base.BaseTestCase;
import org.edx.mobile.view.dialog.WebViewActivity;
import org.junit.Test;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowWebView;

public class WebViewActivityTest extends BaseTestCase {

    String url;
    String title;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        url = "https://www.edx.org";
        title = "title";
    }

    /**
     * Testing method for displaying web view dialog
     */
    // TODO: Convert to Parameterized test once we have a Robolectric parameterized test runner.
    @Test
    public void test_StartWebViewActivity_LoadsUrlAndShowsTitle()
            throws PackageManager.NameNotFoundException {
        test_StartWebViewActivity_LoadsUrlAndShowsTitle(url, title);
    }

    @Test
    public void test_StartWebViewActivity_LoadsUrl()
            throws PackageManager.NameNotFoundException {
        test_StartWebViewActivity_LoadsUrlAndShowsTitle(url, null);
    }

    /**
     * Generic method for testing proper display of WebViewDialogFragment
     *
     * @param url   The url to load
     * @param title The title to show, if any
     */
    private void test_StartWebViewActivity_LoadsUrlAndShowsTitle(@NonNull String url,
                                                                 @Nullable String title)
            throws PackageManager.NameNotFoundException {
        System.out.println("************************************************");
        System.out.println(RuntimeEnvironment.getApplication().getClass().getName());

        ActivityController<WebViewActivity> controller =
                Robolectric.buildActivity(
                        WebViewActivity.class,
                        WebViewActivity.newIntent(
                                RuntimeEnvironment.application, url, title
                        )
                );
        System.out.println(controller.getClass().getName());
        controller = controller.setup();
        System.out.println(controller.getClass().getName());
        WebViewActivity activity = controller.get();
        System.out.println(activity.getClass().getName());
        System.out.println("************************************************");


        final View contentView = Shadows.shadowOf(activity).getContentView();
        assertNotNull(contentView);
        final WebView webView = (WebView) contentView.findViewById(R.id.webView);
        assertNotNull(webView);
        final ShadowWebView shadowWebView = Shadows.shadowOf(webView);
        assertEquals(shadowWebView.getLastLoadedUrl(), url);
        final ActionBar actionBar = activity.getSupportActionBar();
        assertNotNull(actionBar);
        assertTrue(actionBar.isShowing());
        if (!TextUtils.isEmpty(title)) {
            assertEquals(title, actionBar.getTitle());
        }
        /*
        Robolectric is not providing the correct default title which is why this code has
        been commented.

        else {
            final PackageManager pm = activity.getPackageManager();
            final ActivityInfo aInfo = pm.getActivityInfo(activity.getComponentName(), 0);
            final String defaultTitle = aInfo.loadLabel(pm).toString();
            assertThat(actionBar).hasTitle(defaultTitle);
        }
        */
    }
}
