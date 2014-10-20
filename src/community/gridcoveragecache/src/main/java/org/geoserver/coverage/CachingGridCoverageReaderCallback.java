/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.coverage;

import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.FeatureTypeCallback;
import org.geoserver.catalog.GridCoverageReaderCallback;
import org.geoserver.catalog.ResourcePool;
//import org.geoserver.wcs2_0.WebCoverageService20;
import org.geotools.factory.Hints;
import org.opengis.coverage.grid.GridCoverageReader;

/**
 * 
 * Implementation of FeatureTypeInitializer extension point to initialize SOLR datastore
 * 
 * @see {@link FeatureTypeCallback}
 * 
 */
public class CachingGridCoverageReaderCallback implements GridCoverageReaderCallback{

    GridCoveragesCache gridCoveragesCache;

    public GridCoveragesCache getGridCoveragesCache() {
        return gridCoveragesCache;
    }

    public void setGridCoveragesCache(GridCoveragesCache gridCoveragesCache) {
        this.gridCoveragesCache = gridCoveragesCache;
    }

    @Override
    public boolean canHandle(CoverageInfo info) {
        //TODO: handle that
        return true;
    }

    @Override
    public GridCoverageReader wrapGridCoverageReader(ResourcePool pool,
            CoverageInfo info,
            String coverageName,
            Hints hints) {
        return new CachingGridCoverageReader(pool, gridCoveragesCache, info, coverageName, hints);
    }

}
