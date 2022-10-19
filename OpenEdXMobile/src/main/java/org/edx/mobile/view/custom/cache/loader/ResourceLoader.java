package org.edx.mobile.view.custom.cache.loader;

import org.edx.mobile.view.custom.cache.WebResource;

public interface ResourceLoader {

    WebResource getResource(SourceRequest request);

}



