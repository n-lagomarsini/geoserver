/* (c) 2015 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.web.netcdf;

import org.geoserver.config.util.XStreamPersister;
import org.geoserver.config.util.XStreamPersisterInitializer;

public class NetCDFContainerXStreamInitializer implements
		XStreamPersisterInitializer {

	@Override
	public void init(XStreamPersister persister) {
		persister.registerBreifMapComplexType("netcdfSettingsContainer",
				NetCDFSettingsContainer.class);
	}

}
