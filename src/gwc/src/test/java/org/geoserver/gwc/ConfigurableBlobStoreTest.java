/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.gwc;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.geoserver.gwc.config.GWCConfig;
import org.geoserver.test.GeoServerSystemTestSupport;
import org.geotools.util.logging.Logging;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;
import org.geowebcache.storage.BlobStore;
import org.geowebcache.storage.TileObject;
import org.geowebcache.storage.blobstore.memory.CacheConfiguration;
import org.geowebcache.storage.blobstore.memory.CacheProvider;
import org.geowebcache.storage.blobstore.memory.distributed.HazelcastCacheProvider;
import org.geowebcache.storage.blobstore.memory.distributed.HazelcastLoader;
import org.geowebcache.storage.blobstore.memory.guava.GuavaCacheProvider;
import org.geowebcache.storage.blobstore.memory.MemoryBlobStore;
import org.geowebcache.storage.blobstore.memory.NullBlobStore;
import org.geowebcache.storage.blobstore.file.FileBlobStore;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.MapConfig.EvictionPolicy;
import com.hazelcast.config.MaxSizeConfig;
import com.hazelcast.config.MaxSizeConfig.MaxSizePolicy;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

/**
 * This class tests the functionalities of the {@link ConfigurableBlobStore} class.
 * 
 * @author Nicola Lagomarsini Geosolutions
 */
public class ConfigurableBlobStoreTest extends GeoServerSystemTestSupport {

    /** {@link Logger} used for reporting exceptions */
    private static final Logger LOGGER = Logging.getLogger(ConfigurableBlobStoreTest.class);

    /** Name of the test directory */
    public static final String TEST_BLOB_DIR_NAME = "gwcTestBlobs";

    /** {@link CacheProvider} object used for testing purposes */
    private static CacheProvider cache;

    /** {@link ConfigurableBlobStore} object to test */
    private static ConfigurableBlobStore blobStore;

    /** Directory containing files for the {@link FileBlobStore} */
    private File directory;

    @BeforeClass
    public static void initialSetup() {
        cache = new GuavaCacheProvider(new CacheConfiguration());
    }

    @Before
    public void setup() throws IOException {
        // Setup the fileBlobStore
        File dataDirectoryRoot = getTestData().getDataDirectoryRoot();

        MemoryBlobStore mbs = new MemoryBlobStore();

        NullBlobStore nbs = new NullBlobStore();

        directory = new File(dataDirectoryRoot, "testConfigurableBlobStore");
        if (directory.exists()) {
            FileUtils.deleteDirectory(directory);
        }
        directory.mkdirs();

        BlobStore defaultStore = new FileBlobStore(directory.getAbsolutePath());
        blobStore = new ConfigurableBlobStore(defaultStore, mbs, nbs);
        blobStore.setCache(cache);
    }

    @After
    public void after() throws IOException {
        // Delete the created directory
        if (directory.exists()) {
            FileUtils.deleteDirectory(directory);
        }
    }

    @Test
    public void testNullStore() throws Exception {
        // Configure the blobstore
        GWCConfig gwcConfig = new GWCConfig();
        gwcConfig.setInnerCachingEnabled(true);
        gwcConfig.setEnabledPersistence(false);
        blobStore.setChanged(gwcConfig, false);

        BlobStore delegate = blobStore.getDelegate();
        assertTrue(delegate instanceof MemoryBlobStore);

        assertTrue(((MemoryBlobStore) delegate).getStore() instanceof NullBlobStore);

        // Put a TileObject
        Resource bytes = new ByteArrayResource("1 2 3 4 5 6 test".getBytes());
        long[] xyz = { 1L, 2L, 3L };
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("a", "x");
        parameters.put("b", "ø");
        TileObject to = TileObject.createCompleteTileObject("test:123123 112", xyz, "EPSG:4326",
                "image/jpeg", parameters, bytes);
        to.setId(11231231);

        blobStore.put(to);
        // Try to get the Tile Object
        TileObject to2 = TileObject.createQueryTileObject("test:123123 112", xyz, "EPSG:4326",
                "image/jpeg", parameters);
        to2.setId(11231231);
        blobStore.get(to2);

        // Check formats
        assertEquals(to.getBlobFormat(), to2.getBlobFormat());

        // Check if the resources are equals
        InputStream is = to.getBlob().getInputStream();
        InputStream is2 = to2.getBlob().getInputStream();
        checkInputStreams(is, is2);

        // Ensure Cache contains the result
        TileObject to3 = cache.getTileObj(to);
        assertNotNull(to3);
        assertEquals(to.getBlobFormat(), to3.getBlobFormat());

        // Check if the resources are equals
        is = to.getBlob().getInputStream();
        InputStream is3 = to3.getBlob().getInputStream();
        checkInputStreams(is, is3);

        // Ensure that NullBlobStore does not contain anything
        assertFalse(((MemoryBlobStore) delegate).getStore().get(to));
    }

    @Test
    public void testTilePut() throws Exception {
        // Configure the blobstore
        GWCConfig gwcConfig = new GWCConfig();
        gwcConfig.setInnerCachingEnabled(true);
        gwcConfig.setEnabledPersistence(true);
        blobStore.setChanged(gwcConfig, false);

        assertTrue(blobStore.getDelegate() instanceof MemoryBlobStore);

        // Put a TileObject
        Resource bytes = new ByteArrayResource("1 2 3 4 5 6 test".getBytes());
        long[] xyz = { 1L, 2L, 3L };
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("a", "x");
        parameters.put("b", "ø");
        TileObject to = TileObject.createCompleteTileObject("test:123123 112", xyz, "EPSG:4326",
                "image/jpeg", parameters, bytes);
        to.setId(11231231);

        blobStore.put(to);
        // Try to get the Tile Object
        TileObject to2 = TileObject.createQueryTileObject("test:123123 112", xyz, "EPSG:4326",
                "image/jpeg", parameters);
        to2.setId(11231231);
        blobStore.get(to2);

        // Check formats
        assertEquals(to.getBlobFormat(), to2.getBlobFormat());

        // Check if the resources are equals
        InputStream is = to.getBlob().getInputStream();
        InputStream is2 = to2.getBlob().getInputStream();
        checkInputStreams(is, is2);

        // Ensure Cache contains the result
        TileObject to3 = cache.getTileObj(to);
        assertNotNull(to3);
        assertEquals(to.getBlobFormat(), to3.getBlobFormat());

        is = to.getBlob().getInputStream();
        InputStream is3 = to3.getBlob().getInputStream();
        checkInputStreams(is, is3);
    }

    @Test
    public void testTileDelete() throws Exception {

        GWCConfig gwcConfig = new GWCConfig();
        gwcConfig.setInnerCachingEnabled(false);
        blobStore.setChanged(gwcConfig, false);

        assertTrue(blobStore.getDelegate() instanceof FileBlobStore);

        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("a", "x");
        parameters.put("b", "ø");

        // Put a TileObject
        Resource bytes = new ByteArrayResource("1 2 3 4 5 6 test".getBytes());
        long[] xyz = { 5L, 6L, 7L };
        TileObject to = TileObject.createCompleteTileObject("test:123123 112", xyz, "EPSG:4326",
                "image/jpeg", parameters, bytes);
        to.setId(11231231);

        blobStore.put(to);
        // Try to get the Tile Object
        TileObject to2 = TileObject.createQueryTileObject("test:123123 112", xyz, "EPSG:4326",
                "image/jpeg", parameters);
        to2.setId(11231231);
        blobStore.get(to2);

        // Check if the resources are equals
        InputStream is = to2.getBlob().getInputStream();
        InputStream is2 = bytes.getInputStream();
        checkInputStreams(is, is2);

        // Remove TileObject
        TileObject to3 = TileObject.createQueryTileObject("test:123123 112", xyz, "EPSG:4326",
                "image/jpeg", parameters);
        blobStore.delete(to3);

        // Ensure TileObject is no more present
        TileObject to4 = TileObject.createQueryTileObject("test:123123 112", xyz, "EPSG:4326",
                "image/jpeg", parameters);
        assertFalse(blobStore.get(to4));
    }
    
    @Test
    public void testHazelcast() throws Exception {
    	// Configuring hazelcast caching
    	Config config = new Config();
    	MapConfig mapConfig = new MapConfig(HazelcastCacheProvider.HAZELCAST_MAP_DEFINITION);
    	MaxSizeConfig maxSizeConf = new MaxSizeConfig(16, MaxSizePolicy.USED_HEAP_SIZE);
    	mapConfig.setMaxSizeConfig(maxSizeConf);
    	mapConfig.setEvictionPolicy(EvictionPolicy.LRU);
    	config.addMapConfig(mapConfig);
    	HazelcastInstance instance1 = Hazelcast.newHazelcastInstance(config);
    	HazelcastLoader loader1 = new HazelcastLoader();
    	loader1.setInstance(instance1);
    	loader1.afterPropertiesSet();
    	
    	// Creating another cacheprovider for ensuring hazelcast is behaving correctly
    	HazelcastCacheProvider cacheProvider1 = new HazelcastCacheProvider(loader1);
    	
    	HazelcastInstance instance2 = Hazelcast.newHazelcastInstance(config);
    	HazelcastLoader loader2 = new HazelcastLoader();
    	loader2.setInstance(instance2);
    	loader2.afterPropertiesSet();
    	
    	HazelcastCacheProvider cacheProvider2 = new HazelcastCacheProvider(loader2);
    	
        // Configure the blobstore
        GWCConfig gwcConfig = new GWCConfig();
        gwcConfig.setInnerCachingEnabled(true);
        gwcConfig.setEnabledPersistence(false);
        blobStore.setChanged(gwcConfig, false);
        blobStore.setCache(cacheProvider1);

        assertTrue(blobStore.getDelegate() instanceof MemoryBlobStore);
        assertTrue(((MemoryBlobStore) blobStore.getDelegate()).getStore() instanceof NullBlobStore);

        // Put a TileObject
        Resource bytes = new ByteArrayResource("1 2 3 4 5 6 test".getBytes());
        long[] xyz = { 1L, 2L, 3L };
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("a", "x");
        parameters.put("b", "ø");
        TileObject to = TileObject.createCompleteTileObject("test:123123 112", xyz, "EPSG:4326",
                "image/jpeg", parameters, bytes);
        to.setId(11231231);

        blobStore.put(to);
        // Try to get the Tile Object
        TileObject to2 = TileObject.createQueryTileObject("test:123123 112", xyz, "EPSG:4326",
                "image/jpeg", parameters);
        to2.setId(11231231);
        blobStore.get(to2);

        // Check formats
        assertEquals(to.getBlobFormat(), to2.getBlobFormat());

        // Check if the resources are equals
        InputStream is = to.getBlob().getInputStream();
        InputStream is2 = to2.getBlob().getInputStream();
        checkInputStreams(is, is2);

        // Ensure Caches contain the result
        
        // cache1 
        TileObject to3 = cacheProvider1.getTileObj(to);
        assertNotNull(to3);

        is = to.getBlob().getInputStream();
        InputStream is3 = to3.getBlob().getInputStream();
        checkInputStreams(is, is3);
        
        // cache2
        TileObject to4 = cacheProvider2.getTileObj(to);
        assertNotNull(to4);

        is = to.getBlob().getInputStream();
        InputStream is4 = to4.getBlob().getInputStream();
        checkInputStreams(is, is4);
        
        // DELETE
        
        // Remove TileObject
        TileObject to5 = TileObject.createQueryTileObject("test:123123 112", xyz, "EPSG:4326",
                "image/jpeg", parameters);
        blobStore.delete(to5);
        
        // Ensure TileObject is no more present
        TileObject to6 = TileObject.createQueryTileObject("test:123123 112", xyz, "EPSG:4326",
                "image/jpeg", parameters);
        assertFalse(blobStore.get(to6));
        
        // Ensure that each cache provider does not contain the tile object
        assertNull(cacheProvider1.getTileObj(to6));
        assertNull(cacheProvider2.getTileObj(to6));
        
        
        // At the end, destroy the caches
        cacheProvider1.destroy();
        cacheProvider2.destroy();
    }

    /**
     * Checks if the streams are equals, note that the {@link InputStream}s are also closed.
     */
    private void checkInputStreams(InputStream is, InputStream is2) throws IOException {
        try {
            assertTrue(IOUtils.contentEquals(is, is2));
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, e.getMessage(), e);
                assertTrue(false);
            }
            try {
                is2.close();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, e.getMessage(), e);
                assertTrue(false);
            }
        }
    }
}
