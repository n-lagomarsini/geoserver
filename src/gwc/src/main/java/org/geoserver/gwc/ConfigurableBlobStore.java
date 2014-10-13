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
import org.geowebcache.storage.blobstore.memory.CacheConfiguration;
import org.geowebcache.storage.blobstore.memory.CacheProvider;
import org.geowebcache.storage.blobstore.memory.CacheStatistics;
import org.geowebcache.storage.blobstore.memory.MemoryBlobStore;
import org.geowebcache.storage.blobstore.memory.NullBlobStore;
import org.geowebcache.storage.blobstore.file.FileBlobStore;

/**
 * {@link MemoryBlobStore} implementation used for changing {@link CacheProvider} and wrapped {@link BlobStore} at runtime. An instance of this class
 * requires to call the setChanged() method for modifying its configuration.
 * 
 * @author Nicola Lagomarsini Geosolutions
 */
public class ConfigurableBlobStore extends MemoryBlobStore implements BlobStore {

    /** Delegate Object to use for executing the operations */
    private BlobStore delegate;

    /** {@link MemoryBlobStore} used for in memory caching */
    private MemoryBlobStore memoryStore;

    /** Cache provider to add to the {@link MemoryBlobStore} */
    private CacheProvider cache;

    /** {@link NullBlobStore} used for avoiding persistence */
    private NullBlobStore nullStore;

    /** {@link FileBlobStore} used as default by GWC */
    private BlobStore defaultStore;

    /** Atomic counter used for keeping into account how many operations are exceuted in parallel */
    private AtomicLong actualOperations;

    /** Atomic boolean indicating if the BlobStore has been configured */
    private AtomicBoolean configured;

    /** Internal {@link CacheConfiguration} instance */
    private CacheConfiguration internalCacheConfig;

    public ConfigurableBlobStore(BlobStore defaultStore, MemoryBlobStore memoryStore,
            NullBlobStore nullStore, CacheProvider cache) {
        // Initialization
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
        // NOTE that if the blobstore has already been configured, the user must always call setConfig() for
        // setting the new configuration
        if (configured.get()) {
            // Increment the number of current operations
            // This behavior is used in order to wait
            // the end of all the operations after setting
            // the configured parameter to false
            actualOperations.incrementAndGet();
            try {
                // Delete the selected Layer
                return delegate.delete(layerName);
            } finally {
                // Decrement the number of current operations.
                actualOperations.decrementAndGet();
            }
        }
        return true;
    }

    @Override
    public boolean deleteByGridsetId(String layerName, String gridSetId) throws StorageException {
        // Check if the blobstore has already been configured
        if (configured.get()) {
            // Increment the number of current operations
            // This behavior is used in order to wait
            // the end of all the operations after setting
            // the configured parameter to false
            actualOperations.incrementAndGet();
            try {
                // Delete the TileObjects related to the selected gridset
                return delegate.deleteByGridsetId(layerName, gridSetId);
            } finally {
                // Decrement the number of current operations.
                actualOperations.decrementAndGet();
            }
        }
        return true;
    }

    @Override
    public boolean delete(TileObject obj) throws StorageException {
        // Check if the blobstore has already been configured
        if (configured.get()) {
            // Increment the number of current operations
            // This behavior is used in order to wait
            // the end of all the operations after setting
            // the configured parameter to false
            actualOperations.incrementAndGet();
            try {
                // Deletes the single TileObject
                return delegate.delete(obj);
            } finally {
                // Decrement the number of current operations.
                actualOperations.decrementAndGet();
            }
        }
        return true;
    }

    @Override
    public boolean delete(TileRange obj) throws StorageException {
        // Check if the blobstore has already been configured
        if (configured.get()) {
            // Increment the number of current operations
            // This behavior is used in order to wait
            // the end of all the operations after setting
            // the configured parameter to false
            actualOperations.incrementAndGet();
            try {
                // Deletes this TileRange
                return delegate.delete(obj);
            } finally {
                // Decrement the number of current operations.
                actualOperations.decrementAndGet();
            }
        }
        return true;
    }

    @Override
    public boolean get(TileObject obj) throws StorageException {
        // Check if the blobstore has already been configured
        if (configured.get()) {
            // Increment the number of current operations
            // This behavior is used in order to wait
            // the end of all the operations after setting
            // the configured parameter to false
            actualOperations.incrementAndGet();
            try {
                // Get a TileObject
                return delegate.get(obj);
            } finally {
                // Decrement the number of current operations.
                actualOperations.decrementAndGet();
            }
        }
        return false;
    }

    @Override
    public void put(TileObject obj) throws StorageException {
        // Check if the blobstore has already been configured
        if (configured.get()) {
            // Increment the number of current operations
            // This behavior is used in order to wait
            // the end of all the operations after setting
            // the configured parameter to false
            actualOperations.incrementAndGet();
            try {
                // Put the TileObject
                delegate.put(obj);
            } finally {
                // Decrement the number of current operations.
                actualOperations.decrementAndGet();
            }
        }
    }

    @Override
    public void clear() throws StorageException {
        // Check if the blobstore has already been configured
        if (configured.get()) {
            // Increment the number of current operations
            // This behavior is used in order to wait
            // the end of all the operations after setting
            // the configured parameter to false
            actualOperations.incrementAndGet();
            try {
                // Clear the BlobStore
                delegate.clear();
            } finally {
                // Decrement the number of current operations.
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
            // Destroy all
            super.destroy();
            delegate.destroy();
            cache.reset();
        }
    }

    @Override
    public void addListener(BlobStoreListener listener) {
        // Check if the blobstore has already been configured
        if (configured.get()) {
            // Increment the number of current operations
            // This behavior is used in order to wait
            // the end of all the operations after setting
            // the configured parameter to false
            actualOperations.incrementAndGet();
            try {
                // Add a new Listener to the NullBlobStore
                delegate.addListener(listener);
            } finally {
                // Decrement the number of current operations.
                actualOperations.decrementAndGet();
            }
        }
    }

    @Override
    public boolean removeListener(BlobStoreListener listener) {
        // Check if the blobstore has already been configured
        if (configured.get()) {
            // Increment the number of current operations
            // This behavior is used in order to wait
            // the end of all the operations after setting
            // the configured parameter to false
            actualOperations.incrementAndGet();
            try {
                // Remove a Listener from the BlobStore
                return delegate.removeListener(listener);
            } finally {
                // Decrement the number of current operations.
                actualOperations.decrementAndGet();
            }
        }
        return true;
    }

    @Override
    public boolean rename(String oldLayerName, String newLayerName) throws StorageException {
        // Check if the blobstore has already been configured
        if (configured.get()) {
            // Increment the number of current operations
            // This behavior is used in order to wait
            // the end of all the operations after setting
            // the configured parameter to false
            actualOperations.incrementAndGet();
            try {
                // Rename a Layer
                return delegate.rename(oldLayerName, newLayerName);
            } finally {
                // Decrement the number of current operations.
                actualOperations.decrementAndGet();
            }
        }
        return false;
    }

    @Override
    public String getLayerMetadata(String layerName, String key) {
        // Check if the blobstore has already been configured
        if (configured.get()) {
            // Increment the number of current operations
            // This behavior is used in order to wait
            // the end of all the operations after setting
            // the configured parameter to false
            actualOperations.incrementAndGet();
            try {
                // Get The Layer metadata
                return delegate.getLayerMetadata(layerName, key);
            } finally {
                // Decrement the number of current operations.
                actualOperations.decrementAndGet();
            }
        }
        return null;
    }

    @Override
    public void putLayerMetadata(String layerName, String key, String value) {
        // Check if the blobstore has already been configured
        if (configured.get()) {
            // Increment the number of current operations
            // This behavior is used in order to wait
            // the end of all the operations after setting
            // the configured parameter to false
            actualOperations.incrementAndGet();
            try {
                // Put the Layer metadata
                delegate.putLayerMetadata(layerName, key, value);
            } finally {
                // Decrement the number of current operations.
                actualOperations.decrementAndGet();
            }
        }
    }

    @Override
    public CacheStatistics getCacheStatistics() {
        // Check if the blobstore has already been configured
        if (configured.get()) {
            // Increment the number of current operations
            // This behavior is used in order to wait
            // the end of all the operations after setting
            // the configured parameter to false
            actualOperations.incrementAndGet();
            try {
                // Get Cache Statistics
                return cache.getStatistics();
            } finally {
                // Decrement the number of current operations.
                actualOperations.decrementAndGet();
            }
        }
        // Not configured, returns an empty statistics
        return new CacheStatistics();
    }

    public void clearCache() {
        // Check if the blobstore has already been configured
        if (configured.get()) {
            // Increment the number of current operations
            // This behavior is used in order to wait
            // the end of all the operations after setting
            // the configured parameter to false
            actualOperations.incrementAndGet();
            try {
                // Clear the cache
                cache.clear();
            } finally {
                // Decrement the number of current operations.
                actualOperations.decrementAndGet();
            }
        }
    }

    public CacheProvider getCache() {
        // Check if the blobstore has already been configured
        if (configured.get()) {
            // Increment the number of current operations
            // This behavior is used in order to wait
            // the end of all the operations after setting
            // the configured parameter to false
            actualOperations.incrementAndGet();
            try {
                // Returns the cache object used
                return cache;
            } finally {
                // Decrement the number of current operations.
                actualOperations.decrementAndGet();
            }
        }
        return null;
    }

    /**
     * This method changes the {@link ConfigurableBlobStore} configuration. It can be used for changing cache configuration or the blobstore used.
     * 
     * @param gwcConfig
     */
    public synchronized void setChanged(GWCConfig gwcConfig) {
        // Change the blobstore configuration
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
        // Add the internal Cache configuration for the first time
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
            cache.reset();
            cache.setConfiguration(cacheConfiguration);
            // It is not the first time so we must cycle on all the layers in order to check
            // which must not be cached
            Iterable<GeoServerTileLayer> geoServerTileLayers = GWC.get().getGeoServerTileLayers();

            for (GeoServerTileLayer layer : geoServerTileLayers) {
                if (layer.getInfo().isEnabled() && layer.getInfo().isInMemoryUncached()) {
                    cache.addUncachedLayer(layer.getName());
                }
            }
        }

        // BlobStore configuration
        if (gwcConfig.isInnerCachingEnabled()) {
            memoryStore.setCacheProvider(cache);
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

    /**
     * @return the used {@link BlobStore} for testing purpose
     */
    BlobStore getDelegate() {
        return delegate;
    }
}
