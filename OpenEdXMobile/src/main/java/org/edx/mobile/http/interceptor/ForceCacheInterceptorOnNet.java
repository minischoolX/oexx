package org.edx.mobile.http.interceptor;

import androidx.annotation.NonNull;
import android.content.Context;
import android.net.ConnectivityManager;

import java.io.IOException;

import okhttp3.CacheControl;
import okhttp3.Interceptor;
import okhttp3.Response;
import okhttp3.Request;

public class ForceCacheInterceptorOnNet implements Interceptor {
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request.Builder builder = chain.request().newBuilder();
        builder.header("Cache-Control","public, max-age=" + 60 * 60 * 24 * 365).build()
//        builder.cacheControl(CacheControl.FORCE_CACHE);
        return chain.proceed(builder.build());
    }
}