/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.coverage.configuration;

import org.geoserver.catalog.Catalog;
import org.geoserver.gwc.GWC;

public class CoverageCacheInitializer{
    
//    private final GWC mediator;
//    
//    private final Catalog gsCatalog;
    
    public CoverageCacheInitializer(GWC mediator, Catalog gsCatalog){
//        this.mediator = mediator;
//        this.gsCatalog = gsCatalog; 
        gsCatalog.addListener(new CoverageListener(mediator));
    }

//    @Override
//    public void initialize(GeoServer geoServer) throws Exception {
//        
//    }

}
