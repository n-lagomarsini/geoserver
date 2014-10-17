package org.geoserver.coverage;

import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.storage.StorageBroker;

public class GridCoveragesCache {

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

    //TODO review this static assignment
    private GridCoveragesCache(StorageBroker storageBroker, GridSetBroker gridSetBroker) {
        this.storageBroker = storageBroker;
        this.gridSetBroker = gridSetBroker; 
    }
}
