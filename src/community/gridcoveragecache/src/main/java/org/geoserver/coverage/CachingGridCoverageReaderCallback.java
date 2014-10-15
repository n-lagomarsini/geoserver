/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.coverage;

import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.FeatureTypeCallback;
import org.geoserver.catalog.GridCoverageReaderCallback;
import org.geoserver.catalog.ResourcePool;
import org.geoserver.config.GeoServer;
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

    @Override
    public boolean canHandle(CoverageInfo info) {
        return true;
    }

//    @Override
//    public boolean initialize(CoverageInfo info)
//            throws IOException {
//        return true;
//    }
//
//    @Override
//    public void dispose(FeatureTypeInfo info,
//            DataAccess<? extends FeatureType, ? extends Feature> dataAccess, Name temporaryName)
//            throws IOException {
//        SolrLayerConfiguration configuration = (SolrLayerConfiguration) info.getMetadata().get(
//                SolrLayerConfiguration.KEY);
//        SolrDataStore dataStore = (SolrDataStore) dataAccess;
//        dataStore.getSolrConfigurations().remove(configuration.getLayerName());
//    }
//
//    @Override
//    public void flush(FeatureTypeInfo info,
//            DataAccess<? extends FeatureType, ? extends Feature> dataAccess) throws IOException {
//        // nothing to do
//    }

    @Override
    public GridCoverageReader wrapGridCoverageReader(ResourcePool pool,
            CoverageInfo info,
            String coverageName,
            Hints hints) {
        return new CachingGridCoverageReader(pool, info, coverageName, hints);
    }

}
