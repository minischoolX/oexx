package org.edx.mobile.view.custom.cache.offline;

import android.content.Context;

import org.edx.mobile.view.custom.cache.WebResource;
import org.edx.mobile.view.custom.cache.loader.OkHttpResourceLoader;
import org.edx.mobile.view.custom.cache.loader.ResourceLoader;
import org.edx.mobile.view.custom.cache.loader.SourceRequest;

/**
 * Created by Ryan
 * at 2019/9/27
 */
public class DefaultRemoteResourceInterceptor implements ResourceInterceptor {

    private ResourceLoader mResourceLoader;

    DefaultRemoteResourceInterceptor(Context context) {
        mResourceLoader = new OkHttpResourceLoader(context);
    }

    @Override
    public WebResource load(Chain chain) {
        CacheRequest request = chain.getRequest();
        SourceRequest sourceRequest = new SourceRequest(request, true);
        WebResource resource = mResourceLoader.getResource(sourceRequest);
        if (resource != null) {
            return resource;
        }
        return chain.process(request);
    }
}
