package org.edx.mobile.view.custom.cache.offline;

import org.edx.mobile.view.custom.cache.WebResource;

public interface ResourceInterceptor {

    WebResource load(Chain chain);

}
