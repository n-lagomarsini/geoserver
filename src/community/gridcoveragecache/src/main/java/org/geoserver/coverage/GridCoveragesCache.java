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
    
    static final File tempDir;
    static {

        // TODO: Customize this location through Spring
        tempDir = new File(System.getProperty("java.io.tmpdir"));
        WORLD_EPSG4326_FINE = GridSetFactory.createGridSet("EPSG4326FINE", SRS.getEPSG4326(),
                BoundingBox.WORLD4326, false, GridSetFactory.DEFAULT_LEVELS, null,
                GridSetFactory.DEFAULT_PIXEL_SIZE_METER, 256, 256, true);
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
    }
}
