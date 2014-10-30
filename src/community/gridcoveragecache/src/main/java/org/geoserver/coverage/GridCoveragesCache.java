/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.coverage;

import java.io.File;

import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSetFactory;
import org.geowebcache.grid.SRS;
import org.geowebcache.storage.StorageBroker;

public class GridCoveragesCache {

    public static final GridSet WORLD_EPSG4326_FINE;

    public static GridSet REFERENCE;

    // TODO: remove after testings
    public static final BoundingBox WORLD4326_FINE = new BoundingBox(10, 40, 16, 46);

    static final File tempDir;
    static {

        // TODO: Customize this location through Spring
        tempDir = new File(System.getProperty("java.io.tmpdir"));
        WORLD_EPSG4326_FINE = GridSetFactory.createGridSet("EPSG4326FINE", SRS.getEPSG4326(),
                WORLD4326_FINE, false, GridSetFactory.DEFAULT_LEVELS, null,
                GridSetFactory.DEFAULT_PIXEL_SIZE_METER, 512, 512, true);
    }

    private GridCoveragesCache() {
    }

    private StorageBroker storageBroker;

    private GridSetBroker gridSetBroker;

    public GridSetBroker getGridSetBroker() {
        return gridSetBroker;
    }

    public void setGridSetBroker(GridSetBroker gridSetBroker) {
        this.gridSetBroker = gridSetBroker;
        this.gridSetBroker.put(WORLD_EPSG4326_FINE);
    }

    public StorageBroker getStorageBroker() {
        return storageBroker;
    }

    public void setStorageBroker(StorageBroker storageBroker) {
        this.storageBroker = storageBroker;
    }

    private GridCoveragesCache(StorageBroker storageBroker, GridSetBroker gridSetBroker) {
        this.storageBroker = storageBroker;
        this.gridSetBroker = gridSetBroker;
        this.gridSetBroker.put(WORLD_EPSG4326_FINE);
        REFERENCE = gridSetBroker.WORLD_EPSG4326;
    }
}
