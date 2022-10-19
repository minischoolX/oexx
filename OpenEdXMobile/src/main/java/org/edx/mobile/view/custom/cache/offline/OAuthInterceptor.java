package org.edx.mobile.view.custom.cache.offline;

import android.content.Context;

import androidx.annotation.NonNull;

import org.edx.mobile.core.EdxDefaultModule;
import org.edx.mobile.logger.Logger;
import org.edx.mobile.module.prefs.LoginPrefs;

import org.edx.mobile.view.custom.cache.WebResource;
import org.edx.mobile.view.custom.cache.loader.OkHttpResourceLoader;
import org.edx.mobile.view.custom.cache.loader.ResourceLoader;
import org.edx.mobile.view.custom.cache.loader.SourceRequest;

import java.io.IOException;
import java.util.Map;


import dagger.hilt.android.EntryPointAccessors;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Injects OAuth token - if present - into Authorization header
 **/
public final class OauthInterceptor implements ResourceInterceptor {
    protected final Logger logger = new Logger(getClass().getName());

    private final LoginPrefs loginPrefs;

    public OAuthInterceptor(Context context) {
        loginPrefs = EntryPointAccessors
                .fromApplication(context, EdxDefaultModule.ProviderEntryPoint.class).getLoginPrefs();
    }

    @Override
    @NonNull
    public WebResource intercept(Chain chain) throws IOException {
//        final Request.Builder builder = chain.request().newBuilder();
        final CacheRequest request = chain.getRequest();
        SourceRequest sourceRequest = new SourceRequest(request, true);
        final String token = loginPrefs.getAuthorizationHeader();
        if (token != null) {
            Map<String, String> headers = sourceRequest.getHeaders();

            headers.put("Authorization", token);
            sourceRequest.setHeaders(headers);
        }
        return chain.process(request);
    }

}
