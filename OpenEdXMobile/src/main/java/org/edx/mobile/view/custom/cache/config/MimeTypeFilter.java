package org.edx.mobile.view.custom.cache.config;

public interface MimeTypeFilter {

    boolean isFilter(String mimeType);

    void addMimeType(String mimeType);

    void removeMimeType(String mimeType);

    void clear();

}
