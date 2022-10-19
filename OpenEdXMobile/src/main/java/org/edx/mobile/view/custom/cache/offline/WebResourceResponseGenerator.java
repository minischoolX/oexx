package org.edx.mobile.view.custom.cache.offline;

import android.webkit.WebResourceResponse;

import org.edx.mobile.view.custom.cache.WebResource;

public interface WebResourceResponseGenerator {

    WebResourceResponse generate(WebResource resource, String urlMime);

}
