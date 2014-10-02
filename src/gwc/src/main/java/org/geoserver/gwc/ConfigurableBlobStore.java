/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.gwc;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.geoserver.gwc.config.GWCConfig;
import org.geoserver.gwc.layer.GeoServerTileLayer;
import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.BlobStoreListener;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.TileRange;
import org.geowebcache.storage.blobstore.cache.CacheConfiguration;
import org.geowebcache.storage.blobstore.cache.CacheProvider;
import org.geowebcache.storage.blobstore.cache.CacheStatistics;
import org.geowebcache.storage.blobstore.cache.MemoryBlobStore;
import org.geowebcache.storage.blobstore.cache.NullBlobStore;

import com.google.common.collect.Iterables;

public class ConfigurableBlobStore extends MemoryBlobStore implements BlobStore {

    private BlobStore delegate;

    private MemoryBlobStore memoryStore;

    private CacheProvider cache;

    private NullBlobStore nullStore;

    private BlobStore defaultStore;

    private AtomicLong actualOperations;

    private AtomicBoolean configured;

    private CacheConfiguration internalCacheConfig;

    public ConfigurableBlobStore(BlobStore defaultStore, MemoryBlobStore memoryStore,
            NullBlobStore nullStore, CacheProvider cache) {
        configured = new AtomicBoolean(false);
        actualOperations = new AtomicLong(0);
        this.delegate = defaultStore;
        this.defaultStore = defaultStore;
        this.memoryStore = memoryStore;
        this.nullStore = nullStore;
        this.cache = cache;
    }

    @Override
    public boolean delete(String layerName) throws StorageException {
        if (configured.get()) {
            actualOperations.incrementAndGet();
            try {
                return delegate.delete(layerName);
            } finally {
                actualOperations.decrementAndGet();
            }
        }
        return true;
    }

    @Override
    public boolean deleteByGridsetId(String layerName, String gridSetId) throws StorageException {
        if (configured.get()) {
            actualOperations.incrementAndGet();
            try {
                return delegate.deleteByGridsetId(layerName, gridSetId);
            } finally {
                actualOperations.decrementAndGet();
            }
        }
        return true;
    }

    @Override
    public boolean delete(TileObject obj) throws StorageException {
        if (configured.get()) {
            actualOperations.incrementAndGet();
            try {
                return delegate.delete(obj);
            } finally {
                actualOperations.decrementAndGet();
            }
        }
        return true;
    }

    @Override
    public boolean delete(TileRange obj) throws StorageException {
        if (configured.get()) {
            actualOperations.incrementAndGet();
            try {
                return delegate.delete(obj);
            } finally {
                actualOperations.decrementAndGet();
            }
        }
        return true;
    }

    @Override
    public boolean get(TileObject obj) throws StorageException {
        if (configured.get()) {
            actualOperations.incrementAndGet();
            try {
                return delegate.get(obj);
            } finally {
                actualOperations.decrementAndGet();
            }
        }
        return false;
    }

    @Override
    public void put(TileObject obj) throws StorageException {
        if (configured.get()) {
            actualOperations.incrementAndGet();
            try {
                delegate.put(obj);
            } finally {
                actualOperations.decrementAndGet();
            }
        }
    }

    @Override
    public void clear() throws StorageException {
        if (configured.get()) {
            actualOperations.incrementAndGet();
            try {
                delegate.clear();
            } finally {
                actualOperations.decrementAndGet();
            }
        }
    }

    @Override
    public synchronized void destroy() {
        if (configured.getAndSet(false)) {
            // Avoid to call the While cycle before having started an operation with configured == true
            actualOperations.incrementAndGet();
            actualOperations.decrementAndGet();
            // Wait until all the operations are finished
            while (actualOperations.get() > 0) {
            }
            super.destroy();
            delegate.destroy();
            cache.resetCache();
        }
    }

    @Override
    public void addListener(BlobStoreListener listener) {
        if (configured.get()) {
            actualOperations.incrementAndGet();
            try {
                delegate.addListener(listener);
            } finally {
                actualOperations.decrementAndGet();
            }
        }
    }

    @Override
    public boolean removeListener(BlobStoreListener listener) {
        if (configured.get()) {
            actualOperations.incrementAndGet();
            try {
                return delegate.removeListener(listener);
            } finally {
                actualOperations.decrementAndGet();
            }
        }
        return true;
    }

    @Override
    public boolean rename(String oldLayerName, String newLayerName) throws StorageException {
        if (configured.get()) {
            actualOperations.incrementAndGet();
            try {
                return delegate.rename(oldLayerName, newLayerName);
            } finally {
                actualOperations.decrementAndGet();
            }
        }
        return false;
    }

    @Override
    public String getLayerMetadata(String layerName, String key) {
        if (configured.get()) {
            actualOperations.incrementAndGet();
            try {
                return delegate.getLayerMetadata(layerName, key);
            } finally {
                actualOperations.decrementAndGet();
            }
        }
        return null;
    }

    @Override
    public void putLayerMetadata(String layerName, String key, String value) {
        if (configured.get()) {
            actualOperations.incrementAndGet();
            try {
                delegate.putLayerMetadata(layerName, key, value);
            } finally {
                actualOperations.decrementAndGet();
            }
        }
    }

    @Override
    public CacheStatistics getCacheStatistics() {
        if (configured.get()) {
            actualOperations.incrementAndGet();
            try {
                return cache.getStats();
            } finally {
                actualOperations.decrementAndGet();
            }
        }
        return new CacheStatistics();
    }

    public void clearCache() {
        if (configured.get()) {
            actualOperations.incrementAndGet();
            try {
                cache.clearCache();
            } finally {
                actualOperations.decrementAndGet();
            }
        }
    }

    public CacheProvider getCache() {
        if (configured.get()) {
            actualOperations.incrementAndGet();
            try {
                return cache;
            } finally {
                actualOperations.decrementAndGet();
            }
        }
        return null;
    }

    public synchronized void setChanged(GWCConfig gwcConfig) {
        configureBlobStore(gwcConfig);
    }

    private void configureBlobStore(GWCConfig gwcConfig) {
        // reset the configuration
        configured.getAndSet(false);

        // Avoid to call the While cycle before having started an operation with configured == true
        actualOperations.incrementAndGet();
        actualOperations.decrementAndGet();
        // Wait until all the operations are finished
        while (actualOperations.get() > 0) {
        }

        CacheConfiguration cacheConfiguration = gwcConfig.getCacheConfiguration();
        if (internalCacheConfig == null) {
            internalCacheConfig = new CacheConfiguration();
            internalCacheConfig.setConcurrencyLevel(cacheConfiguration.getConcurrencyLevel());
            internalCacheConfig.setEvictionTime(cacheConfiguration.getEvictionTime());
            internalCacheConfig.setHardMemoryLimit(cacheConfiguration.getHardMemoryLimit());
            internalCacheConfig.setPolicy(cacheConfiguration.getPolicy());

            cache.setConfiguration(cacheConfiguration);
        } else if (!internalCacheConfig.equals(cacheConfiguration)) {
            internalCacheConfig.setConcurrencyLevel(cacheConfiguration.getConcurrencyLevel());
            internalCacheConfig.setEvictionTime(cacheConfiguration.getEvictionTime());
            internalCacheConfig.setHardMemoryLimit(cacheConfiguration.getHardMemoryLimit());
            internalCacheConfig.setPolicy(cacheConfiguration.getPolicy());
            cache.resetCache();
            cache.setConfiguration(cacheConfiguration);
            // Add all the various Layers to avoid caching
            Iterable<GeoServerTileLayer> geoServerTileLayers = GWC.get().getGeoServerTileLayers();

            for (GeoServerTileLayer layer : geoServerTileLayers) {
                if (layer.getInfo().isEnabled() && layer.getInfo().isInMemoryUncached()) {
                    cache.addUncachedLayer(layer.getName());
                }
            }
        }

        if (gwcConfig.isInnerCachingEnabled()) {
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

        // Update the configured parameter
        configured.getAndSet(true);
    }

    BlobStore getDelegate() {
        return delegate;
    }
}
