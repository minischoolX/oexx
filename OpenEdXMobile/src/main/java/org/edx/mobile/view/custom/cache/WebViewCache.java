package org.edx.mobile.view.custom.cache;

import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import org.edx.mobile.view.custom.cache.offline.Destroyable;

public interface WebViewCache extends FastOpenApi, Destroyable {

    WebResourceResponse getResource(WebResourceRequest request, int webViewCacheMode, String userAgent);

}
