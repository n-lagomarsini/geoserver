/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.gwc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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
import org.geowebcache.storage.blobstore.cache.CacheConfiguration;
import org.geowebcache.storage.blobstore.cache.CacheProvider;
import org.geowebcache.storage.blobstore.cache.GuavaCacheProvider;
import org.geowebcache.storage.blobstore.cache.MemoryBlobStore;
import org.geowebcache.storage.blobstore.cache.NullBlobStore;
import org.geowebcache.storage.blobstore.file.FileBlobStore;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class ConfigurableBlobStoreTest extends GeoServerSystemTestSupport {

    public static final Logger LOG = Logging.getLogger(ConfigurableBlobStoreTest.class);

    public static final String TEST_BLOB_DIR_NAME = "gwcTestBlobs";

    private static CacheProvider cache;

    private static ConfigurableBlobStore blobStore;

    private File newFile;

    @BeforeClass
    public static void initialSetup() {
        cache = new GuavaCacheProvider();
        CacheConfiguration configuration = new CacheConfiguration();
        cache.setConfiguration(configuration);
    }

    @Before
    public void setup() throws IOException {
        File dataDirectoryRoot = getTestData().getDataDirectoryRoot();

        MemoryBlobStore mbs = new MemoryBlobStore();

        NullBlobStore nbs = new NullBlobStore();

        newFile = new File(dataDirectoryRoot, "testConfigurableBlobStore");
        if (newFile.exists()) {
            FileUtils.deleteDirectory(newFile);
        }
        newFile.mkdirs();

        BlobStore defaultStore = new FileBlobStore(newFile.getAbsolutePath());
        blobStore = new ConfigurableBlobStore(defaultStore, mbs, nbs, cache);
    }

    @After
    public void after() throws IOException {
        if (newFile.exists()) {
            FileUtils.deleteDirectory(newFile);
        }

    }

    @Test
    public void testNullStore() throws Exception {

        GWCConfig gwcConfig = new GWCConfig();
        gwcConfig.setInnerCachingEnabled(true);
        gwcConfig.setAvoidPersistence(true);
        blobStore.setChanged(gwcConfig);

        BlobStore delegate = blobStore.getDelegate();
        assertTrue(delegate instanceof MemoryBlobStore);
        
        assertTrue(((MemoryBlobStore)delegate).getStore() instanceof NullBlobStore);

        Resource bytes = new ByteArrayResource("1 2 3 4 5 6 test".getBytes());
        long[] xyz = { 1L, 2L, 3L };
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("a", "x");
        parameters.put("b", "ø");
        TileObject to = TileObject.createCompleteTileObject("test:123123 112", xyz, "EPSG:4326",
                "image/jpeg", parameters, bytes);
        to.setId(11231231);

        blobStore.put(to);

        TileObject to2 = TileObject.createQueryTileObject("test:123123 112", xyz, "EPSG:4326",
                "image/jpeg", parameters);
        to2.setId(11231231);
        blobStore.get(to2);

        assertEquals(to.getBlobFormat(), to2.getBlobFormat());

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

        // Ensure that NullBlobStore does not contain anything
        assertFalse(((MemoryBlobStore)delegate).getStore().get(to));
    }

    @Test
    public void testTilePut() throws Exception {

        GWCConfig gwcConfig = new GWCConfig();
        gwcConfig.setInnerCachingEnabled(true);
        gwcConfig.setAvoidPersistence(false);
        blobStore.setChanged(gwcConfig);

        assertTrue(blobStore.getDelegate() instanceof MemoryBlobStore);

        Resource bytes = new ByteArrayResource("1 2 3 4 5 6 test".getBytes());
        long[] xyz = { 1L, 2L, 3L };
        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("a", "x");
        parameters.put("b", "ø");
        TileObject to = TileObject.createCompleteTileObject("test:123123 112", xyz, "EPSG:4326",
                "image/jpeg", parameters, bytes);
        to.setId(11231231);

        blobStore.put(to);

        TileObject to2 = TileObject.createQueryTileObject("test:123123 112", xyz, "EPSG:4326",
                "image/jpeg", parameters);
        to2.setId(11231231);
        blobStore.get(to2);

        assertEquals(to.getBlobFormat(), to2.getBlobFormat());

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
        blobStore.setChanged(gwcConfig);

        assertTrue(blobStore.getDelegate() instanceof FileBlobStore);

        Map<String, String> parameters = new HashMap<String, String>();
        parameters.put("a", "x");
        parameters.put("b", "ø");

        Resource bytes = new ByteArrayResource("1 2 3 4 5 6 test".getBytes());
        long[] xyz = { 5L, 6L, 7L };
        TileObject to = TileObject.createCompleteTileObject("test:123123 112", xyz, "EPSG:4326",
                "image/jpeg", parameters, bytes);
        to.setId(11231231);

        blobStore.put(to);

        TileObject to2 = TileObject.createQueryTileObject("test:123123 112", xyz, "EPSG:4326",
                "image/jpeg", parameters);
        to2.setId(11231231);
        blobStore.get(to2);

        InputStream is = to2.getBlob().getInputStream();
        InputStream is2 = bytes.getInputStream();
        checkInputStreams(is, is2);

        TileObject to3 = TileObject.createQueryTileObject("test:123123 112", xyz, "EPSG:4326",
                "image/jpeg", parameters);
        blobStore.delete(to3);

        TileObject to4 = TileObject.createQueryTileObject("test:123123 112", xyz, "EPSG:4326",
                "image/jpeg", parameters);
        assertFalse(blobStore.get(to4));
    }

    /**
     * Checks if the streams are equals, note that
     */
    private void checkInputStreams(InputStream is, InputStream is2) throws IOException {
        try {
            assertTrue(IOUtils.contentEquals(is, is2));
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                LOG.log(Level.SEVERE, e.getMessage(), e);
                assertTrue(false);
            }
            try {
                is2.close();
            } catch (IOException e) {
                LOG.log(Level.SEVERE, e.getMessage(), e);
                assertTrue(false);
            }
        }
    }
}
