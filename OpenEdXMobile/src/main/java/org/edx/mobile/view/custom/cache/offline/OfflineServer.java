package org.edx.mobile.view.custom.cache.offline;

import android.webkit.WebResourceResponse;

public interface OfflineServer {

    WebResourceResponse get(CacheRequest request);

    void addResourceInterceptor(ResourceInterceptor interceptor);

    void destroy();
}
