package org.geoserver.coverage;

import org.geowebcache.storage.StorageBroker;

public class GridCoveragesCache {

    private GridCoveragesCache() {
    }

    private static StorageBroker storageBroker;

    public static StorageBroker getStorageBroker() {
        return storageBroker;
    }

    //TODO review this static assignment
    private GridCoveragesCache(StorageBroker storageBroker) {
        this.storageBroker = storageBroker;
    }
}
