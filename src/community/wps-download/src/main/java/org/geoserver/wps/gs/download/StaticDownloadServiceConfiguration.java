/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wps.gs.download;
/**
 * Implementation of {@link DownloadServiceConfigurationGenerator} that uses a specific {@link DownloadServiceConfiguration}.
 * 
 * @author Simone Giannecchini, GeoSolutions
 *
 */
public class StaticDownloadServiceConfiguration implements DownloadServiceConfigurationGenerator {

	DownloadServiceConfiguration config;

	public StaticDownloadServiceConfiguration(DownloadServiceConfiguration config) {
		this.config = config;
	}

	public StaticDownloadServiceConfiguration() {
		config= new DownloadServiceConfiguration();
	}
	
	@Override
	public DownloadServiceConfiguration getConfiguration() {
		return config;
	}
}
