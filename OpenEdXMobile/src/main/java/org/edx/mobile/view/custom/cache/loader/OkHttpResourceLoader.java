package org.edx.mobile.view.custom.cache.loader;

import android.content.Context;
import android.text.TextUtils;

import org.edx.mobile.core.EdxDefaultModule;
import org.edx.mobile.http.provider.OkHttpClientProvider;
import dagger.hilt.android.EntryPointAccessors;

import org.edx.mobile.view.custom.cache.WebResource;
//import org.edx.mobile.view.custom.cache.okhttp.OkHttpClientProvider;
import org.edx.mobile.view.custom.cache.utils.HeaderUtils;
import org.edx.mobile.view.custom.cache.utils.LogUtils;
import org.edx.mobile.BuildConfig;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.CacheControl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.internal.Util;

import static android.webkit.WebSettings.LOAD_CACHE_ELSE_NETWORK;
import static android.webkit.WebSettings.LOAD_CACHE_ONLY;
import static android.webkit.WebSettings.LOAD_NO_CACHE;
import static java.net.HttpURLConnection.HTTP_NOT_MODIFIED;

public class OkHttpResourceLoader implements ResourceLoader {

    private static final String HEADER_USER_AGENT = "User-Agent";
    private static final String DEFAULT_USER_AGENT = "FastWebView" + BuildConfig.VERSION_NAME;
    private Context mContext;

    OkHttpClientProvider okHttpClientProvider;

    public OkHttpResourceLoader(Context context) {
        okHttpClientProvider = EntryPointAccessors.fromApplication(context, EdxDefaultModule.ProviderEntryPoint.class).getOkHttpClientProvider();        
        mContext = context;
    }

    @Override
    public WebResource getResource(SourceRequest sourceRequest) {
        String url = sourceRequest.getUrl();
        LogUtils.d(String.format("load url: %s", url));
        boolean isCacheByOkHttp = sourceRequest.isCacheable();
        OkHttpClient client = OkHttpClientProvider.get(mContext);
//        OkHttpClient client = OkHttpClientProvider.get(mContext);
        CacheControl cacheControl = getCacheControl(sourceRequest.getWebViewCache(), isCacheByOkHttp);
        String userAgent = sourceRequest.getUserAgent();
        if (TextUtils.isEmpty(userAgent)) {
            userAgent = DEFAULT_USER_AGENT;
        }
        Locale locale = Locale.getDefault();
        String acceptLanguage;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            acceptLanguage = locale.toLanguageTag();
        } else {
            acceptLanguage = locale.getLanguage();
        }
        if (!acceptLanguage.equalsIgnoreCase("en-US")) {
            acceptLanguage += ",en-US;q=0.9";
        }
        Request.Builder requestBuilder = new Request.Builder()
                .removeHeader(HEADER_USER_AGENT)
                .addHeader(HEADER_USER_AGENT, userAgent)
                .addHeader("Upgrade-Insecure-Requests", "1")
                .addHeader("X-Requested-With", mContext.getPackageName())
                .addHeader("Accept", "*/*")
                .addHeader("Accept-Language", acceptLanguage);
        Map<String, String> headers = sourceRequest.getHeaders();
        if (headers != null && !headers.isEmpty()) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                String header = entry.getKey();
                if (!isNeedStripHeader(header)) {
                    requestBuilder.removeHeader(header);
                    requestBuilder.addHeader(header, entry.getValue());
                }
            }
        }
        Request request = requestBuilder
                .url(url)
                .cacheControl(cacheControl)
                .get()
                .build();
        Response response = null;
        try {
            WebResource remoteResource = new WebResource();
            response = okHttpClientProvider.get.newCall(request).execute();
//            response = client.newCall(request).execute();
            if (isInterceptorThisRequest(response)) {
                remoteResource.setResponseCode(response.code());
                remoteResource.setReasonPhrase(response.message());
                remoteResource.setModified(response.code() != HTTP_NOT_MODIFIED);
                ResponseBody responseBody = response.body();
                if (responseBody != null) {
                    remoteResource.setOriginBytes(responseBody.bytes());
                }
                remoteResource.setResponseHeaders(HeaderUtils.generateHeadersMap(response.headers()));
                remoteResource.setCacheByOurselves(!isCacheByOkHttp);
                return remoteResource;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (response != null) {
                response.close();
            }
        }
        return null;
    }

    private CacheControl getCacheControl(int webViewCacheMode, boolean isCacheByOkHttp) {
        // return the appropriate cache-control according to webview cache mode.
        switch (webViewCacheMode) {
            case LOAD_CACHE_ONLY:
                return CacheControl.FORCE_CACHE;
            case LOAD_CACHE_ELSE_NETWORK:
                if (!isCacheByOkHttp) {
                    // if it happens, because there is no local cache.
                    return createNoStoreCacheControl();
                }
                // tell okhttp that we are willing to receive expired cache.
                return new CacheControl.Builder().maxStale(Integer.MAX_VALUE, TimeUnit.SECONDS).build();
            case LOAD_NO_CACHE:
                return CacheControl.FORCE_NETWORK;
            default: // LOAD_DEFAULT
                return isCacheByOkHttp ? new CacheControl.Builder().build() : createNoStoreCacheControl();
        }
    }

    private CacheControl createNoStoreCacheControl() {
        return new CacheControl.Builder().noStore().build();
    }

    private boolean isNeedStripHeader(String headerName) {
        return headerName.equalsIgnoreCase("If-Match")
                || headerName.equalsIgnoreCase("If-None-Match")
                || headerName.equalsIgnoreCase("If-Modified-Since")
                || headerName.equalsIgnoreCase("If-Unmodified-Since")
                || headerName.equalsIgnoreCase("Last-Modified")
                || headerName.equalsIgnoreCase("Expires")
                || headerName.equalsIgnoreCase("Cache-Control");
    }

    /**
     * references {@link android.webkit.WebResourceResponse} setStatusCodeAndReasonPhrase
     */
    private boolean isInterceptorThisRequest(Response response) {
        int code = response.code();
        return !(code < 100 || code > 599 || (code > 299 && code < 400));
    }
}
