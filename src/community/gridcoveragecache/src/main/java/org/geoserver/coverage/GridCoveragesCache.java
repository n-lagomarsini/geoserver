package org.geoserver.coverage;

import java.io.File;

import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.storage.StorageBroker;

public class GridCoveragesCache {

    static final File tempDir; 
    static {
        
        //TODO: Customize this location through Spring
        tempDir = new File(System.getProperty("java.io.tmpDir"));
    }
    
    private GridSetBroker gridSetBroker;

    public GridSetBroker getGridSetBroker() {
        return gridSetBroker;
    }

    private GridCoveragesCache() {
    }

    private StorageBroker storageBroker;

    public StorageBroker getStorageBroker() {
        return storageBroker;
    }

    private GridCoveragesCache(StorageBroker storageBroker, GridSetBroker gridSetBroker) {
        this.storageBroker = storageBroker;
        this.gridSetBroker = gridSetBroker; 
    }
}
