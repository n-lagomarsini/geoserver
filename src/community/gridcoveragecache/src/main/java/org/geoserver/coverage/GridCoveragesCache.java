package org.geoserver.coverage;

import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.storage.StorageBroker;

public class GridCoveragesCache {

    private static GridSetBroker gridSetBroker;

    public static GridSetBroker getGridSetBroker() {
        return gridSetBroker;
    }

    private GridCoveragesCache() {
    }

    private static StorageBroker storageBroker;

    public static StorageBroker getStorageBroker() {
        return storageBroker;
    }

    //TODO review this static assignment
    private GridCoveragesCache(StorageBroker storageBroker, GridSetBroker gridSetBroker) {
        this.storageBroker = storageBroker;
        this.gridSetBroker = gridSetBroker; 
    }
}
