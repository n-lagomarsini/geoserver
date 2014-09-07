/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wps.gs.download;

import java.io.File;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.platform.GeoServerResourceLoader;
import org.geoserver.platform.resource.Resource;
import org.geoserver.security.PropertyFileWatcher;
import org.geotools.util.Utilities;
import org.geotools.util.logging.Logging;

/**
 * Basic property file based {@link DownloadServiceConfigurationGenerator} implementation
 * with ability to reload config when the file changes.
 * 
 * @author Simone Giannecchini, GeoSolutions
 */
public class DownloadServiceConfigurationWatcher extends TimerTask implements DownloadServiceConfigurationGenerator{
	
	private final static Logger LOGGER =Logging.getLogger(DownloadServiceConfigurationWatcher.class);
	
	private static final String PROPERTYFILENAME="download.properties";
	
	private PropertyFileWatcher watcher;

	private long period = 60*2;
	
	private long delay=60*2;
	
	private DownloadServiceConfiguration configuration=new DownloadServiceConfiguration();

	private Timer timer;

    /** Default watches controlflow.properties */
    public DownloadServiceConfigurationWatcher() {
        GeoServerResourceLoader loader = GeoServerExtensions.bean(GeoServerResourceLoader.class);
        Resource downloadProperties = loader.get(PROPERTYFILENAME);
        init(new PropertyFileWatcher(downloadProperties));   
    }
    
    private void init(PropertyFileWatcher propertyFileWatcher) {
        Utilities.ensureNonNull("propertyFileWatcher", propertyFileWatcher); 
        this.watcher = propertyFileWatcher;  
        DownloadServiceConfiguration newConfiguration=loadConfiguration();
        if(newConfiguration!=null){
        	configuration=newConfiguration;
        	if(LOGGER.isLoggable(Level.FINE)){
        		LOGGER.fine("New configuration loaded:\n"+configuration );
        	}
        }
        
        // start background checks
        timer = new Timer(true);
        timer.scheduleAtFixedRate(this, delay*1000, period*1000);
		
	}

	/** Default watches controlflow.properties */
    public DownloadServiceConfigurationWatcher(PropertyFileWatcher watcher) {
        init(watcher);
    }

	/**
	 * Loads the configuration from disk.
	 * @return 
	 */
	private DownloadServiceConfiguration loadConfiguration() {
		// load download Process Properties
    	final File file = watcher.getFile();
    	DownloadServiceConfiguration newConfiguration=null;
        try {
			if(file.exists()&&file.canRead()){
				// load contents
				Properties properties = watcher.getProperties();
				
				// parse contents
				newConfiguration = parseConfigurationValues(properties);
			}else{
				if(LOGGER.isLoggable(Level.INFO)){
					LOGGER.info("Unable to read confguration file for download service: "+file.getAbsolutePath()+" continuing with default configuration-->\n"+configuration);
				}
			}
		} catch (Exception e) {
			if(LOGGER.isLoggable(Level.INFO)){
				LOGGER.log(Level.INFO,e.getLocalizedMessage(),e);
			}
			if(LOGGER.isLoggable(Level.INFO)){
				LOGGER.info("Unable to read confguration file for download service: "+file.getAbsolutePath()+" continuing with default configuration-->\n"+configuration);
			}			
		}
        // return
        return newConfiguration;
        
	}

    /**
     * Parses the properties file for the download process configuration.
     * When it runs into problems it uses default values
     * 
     * @param downloadProcessProperties the {@link Properties} file to parse. Cannot be null.
     * @return an instance of {@link DownloadServiceConfiguration}.
     */
	private DownloadServiceConfiguration parseConfigurationValues(
			Properties downloadProcessProperties) {
		Utilities.ensureNonNull("downloadProcessProperties", downloadProcessProperties);
		
		long maxFeatures=DownloadServiceConfiguration.DEFAULT_MAX_FEATURES; 
	    long readLimits=DownloadServiceConfiguration.DEFAULT_READ_LIMITS ;
	    long writeLimits=DownloadServiceConfiguration.DEFAULT_READ_LIMITS;
	    long hardOutputLimit=DownloadServiceConfiguration.DEFAULT_WRITE_LIMITS ;
		int compressionLevel=DownloadServiceConfiguration.DEFAULT_COMPRESSION_LEVEL;
		
		// max features
		if(downloadProcessProperties.contains("maxFeatures")){
			// get value
			String value = (String) downloadProcessProperties.get("maxFeatures");
			
			// check and assign
			try{
				final long parseLong = Long.parseLong(value);
				if(LOGGER.isLoggable(Level.FINE)){
					LOGGER.fine("maxFeatures parsed to "+parseLong);
				}
				if(parseLong>0){
					maxFeatures=parseLong;	
				}
				
			}catch(NumberFormatException e){
				if(LOGGER.isLoggable(Level.INFO)){
					LOGGER.log(Level.INFO,e.getLocalizedMessage(),e);
				}
			}
			if(LOGGER.isLoggable(Level.FINE)){
				LOGGER.fine("maxFeatures assigned to "+maxFeatures);
			}			
		}
		
		// readLimits
		if(downloadProcessProperties.contains("readLimits")){
			// get value
			String value = (String) downloadProcessProperties.get("readLimits");
		
			// check and assign
			try{
				final long parseLong = Long.parseLong(value);
				if(LOGGER.isLoggable(Level.FINE)){
					LOGGER.fine("readLimits parsed to "+parseLong);
				}
				if(parseLong>0){
					readLimits=Long.parseLong(value);
				}
				
			}catch(NumberFormatException e){
				if(LOGGER.isLoggable(Level.INFO)){
					LOGGER.log(Level.INFO,e.getLocalizedMessage(),e);
				}
			}
			if(LOGGER.isLoggable(Level.FINE)){
				LOGGER.fine("readLimits assigned to "+readLimits);
			}				
		}
		
		// writeLimits
		if(downloadProcessProperties.contains("writeLimits")){
			// get value
			String value = (String) downloadProcessProperties.get("writeLimits");
			
			// check and assign
			try{
				final long parseLong = Long.parseLong(value);
				if(LOGGER.isLoggable(Level.FINE)){
					LOGGER.fine("writeLimits parsed to "+parseLong);
				}
				if(parseLong>0){
					writeLimits=Long.parseLong(value);
				}
				
			}catch(NumberFormatException e){
				if(LOGGER.isLoggable(Level.INFO)){
					LOGGER.log(Level.INFO,e.getLocalizedMessage(),e);
				}
			}
			if(LOGGER.isLoggable(Level.FINE)){
				LOGGER.fine("writeLimits assigned to "+writeLimits);
			}	
		}
		
		// hardOutputLimit
		if(downloadProcessProperties.contains("hardOutputLimit")){
			// get value
			String value = (String) downloadProcessProperties.get("hardOutputLimit");
			
			// check and assign
			try{
				final long parseLong = Long.parseLong(value);
				if(LOGGER.isLoggable(Level.FINE)){
					LOGGER.fine("hardOutputLimit parsed to "+parseLong);
				}
				if(parseLong>0){
					hardOutputLimit=Long.parseLong(value);
				}
				
			}catch(NumberFormatException e){
				if(LOGGER.isLoggable(Level.INFO)){
					LOGGER.log(Level.INFO,e.getLocalizedMessage(),e);
				}
			}
			if(LOGGER.isLoggable(Level.FINE)){
				LOGGER.fine("hardOutputLimit assigned to "+hardOutputLimit);
			}	
		}
		
		// compressionLevel
		if(downloadProcessProperties.contains("compressionLevel")){
			// get value
			String value = (String) downloadProcessProperties.get("compressionLevel");
			
			
			// check and assign
			try{
				final long parseLong = Long.parseLong(value);
				if(LOGGER.isLoggable(Level.FINE)){
					LOGGER.fine("compressionLevel parsed to "+parseLong);
				}
				if(parseLong>=0&&parseLong<=8){
					compressionLevel=Integer.parseInt(value);
				}
				
			}catch(NumberFormatException e){
				if(LOGGER.isLoggable(Level.INFO)){
					LOGGER.log(Level.INFO,e.getLocalizedMessage(),e);
				}
			}
			if(LOGGER.isLoggable(Level.FINE)){
				LOGGER.fine("compressionLevel assigned to "+compressionLevel);
			}	
		}		
		
		// create 
		return new DownloadServiceConfiguration(maxFeatures, readLimits, writeLimits, hardOutputLimit, compressionLevel);
	}

	@Override
	public void run() {
		if(watcher.isStale()){
			// reload
	        DownloadServiceConfiguration newConfiguration=loadConfiguration();
	        if(newConfiguration!=null){
	        	synchronized (newConfiguration) {
		        	configuration=newConfiguration;
		        	if(LOGGER.isLoggable(Level.FINE)){
		        		LOGGER.fine("New configuration loaded:\n"+configuration );
		        	}
				}

	        }
		}
		
	}

	public DownloadServiceConfiguration getConfiguration() {
		return configuration;
	}

	/**
	 * Stop the configuration watcher.
	 */
	public void stop(){
		try{
			timer.cancel();
		}catch(Throwable t){
			if(LOGGER.isLoggable(Level.FINE)){
				LOGGER.log(Level.FINE,t.getLocalizedMessage(),t);				
			}
		}
	}
}
