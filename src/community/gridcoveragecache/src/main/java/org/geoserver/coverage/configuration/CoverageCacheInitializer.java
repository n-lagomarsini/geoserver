/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.coverage.configuration;

import org.geoserver.catalog.Catalog;
import org.geoserver.gwc.GWC;

public class CoverageCacheInitializer {

    public CoverageCacheInitializer(GWC mediator, Catalog gsCatalog) {
        gsCatalog.addListener(new CoverageListener(mediator, gsCatalog));
    }

}
