/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.gwc;

import org.geoserver.gwc.config.GWCConfig;
import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.BlobStoreListener;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.TileRange;
import org.geowebcache.storage.blobstore.cache.CacheProvider;
import org.geowebcache.storage.blobstore.cache.MemoryBlobStore;
import org.geowebcache.storage.blobstore.cache.NullBlobStore;

public class ConfigurableBlobStore implements BlobStore {

    private BlobStore delegate;

    private MemoryBlobStore memoryStore;

    private CacheProvider cache;

    private NullBlobStore nullStore;

    private BlobStore defaultStore;

    public ConfigurableBlobStore(BlobStore defaultStore, MemoryBlobStore memoryStore,
            NullBlobStore nullStore, CacheProvider cache) {
        this.delegate = defaultStore;
        this.defaultStore = defaultStore;
        this.memoryStore = memoryStore;
        this.nullStore = nullStore;
        this.cache = cache;
    }

    @Override
    public boolean delete(String layerName) throws StorageException {
        return delegate.delete(layerName);
    }

    @Override
    public boolean deleteByGridsetId(String layerName, String gridSetId) throws StorageException {
        return delegate.deleteByGridsetId(layerName, gridSetId);
    }

    @Override
    public boolean delete(TileObject obj) throws StorageException {
        return delegate.delete(obj);
    }

    @Override
    public boolean delete(TileRange obj) throws StorageException {
        return delegate.delete(obj);
    }

    @Override
    public boolean get(TileObject obj) throws StorageException {
        return delegate.get(obj);
    }

    @Override
    public void put(TileObject obj) throws StorageException {
        delegate.put(obj);
    }

    @Override
    public void clear() throws StorageException {
        delegate.clear();
    }

    @Override
    public void destroy() {
        delegate.destroy();
    }

    @Override
    public void addListener(BlobStoreListener listener) {
        delegate.addListener(listener);
    }

    @Override
    public boolean removeListener(BlobStoreListener listener) {
        return delegate.removeListener(listener);
    }

    @Override
    public boolean rename(String oldLayerName, String newLayerName) throws StorageException {
        return delegate.rename(oldLayerName, newLayerName);
    }

    @Override
    public String getLayerMetadata(String layerName, String key) {
        return delegate.getLayerMetadata(layerName, key);
    }

    @Override
    public void putLayerMetadata(String layerName, String key, String value) {
        delegate.putLayerMetadata(layerName, key, value);
    }

    public BlobStore getDelegate() {
        return delegate;
    }

    public void setDelegate(BlobStore delegate) {
        this.delegate = delegate;
    }

    public synchronized void setChanged(GWCConfig gwcConfig) {
        configureBlobStore(gwcConfig);
    }

    private void configureBlobStore(GWCConfig gwcConfig) {
        if (gwcConfig.isInnerCachingEnabled()) {
            cache.setConfiguration(gwcConfig.getCacheConfiguration());
            memoryStore.setCache(cache);
            if (gwcConfig.isAvoidPersistence()) {
                memoryStore.setStore(nullStore);
            } else {
                memoryStore.setStore(defaultStore);
            }
            delegate = memoryStore;
        } else {
            delegate = defaultStore;
        }
    }
}
