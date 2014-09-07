/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wps.gs.download;

/**
 * Bean that includes the configurations parameters for the download service
 * 
 * @author Simone Giannecchini, GeoSolutions
 *
 */
public class DownloadServiceConfiguration {
	
    /** Value used to indicate no limits */
    public static final long NO_LIMIT = 0;

	public static final int DEFAULT_COMPRESSION_LEVEL = 4;

	public static final long DEFAULT_HARD_OUTPUT_LIMITS = NO_LIMIT;

	public static final long DEFAULT_READ_LIMITS = NO_LIMIT;
	
	public static final long DEFAULT_WRITE_LIMITS = NO_LIMIT;

	public static final long DEFAULT_MAX_FEATURES = NO_LIMIT;

	/** Max #of features	 */
	private long maxFeatures=DEFAULT_MAX_FEATURES; 

	/** 8000 px X 8000 px	 */
    private long readLimits=DEFAULT_READ_LIMITS ;
    
    /** 8000 px X 8000 px (USELESS RIGHT NOW)	 */
    private long writeLimits=DEFAULT_READ_LIMITS;
    		
    /** 50 MB	 */
    private long hardOutputLimit=DEFAULT_WRITE_LIMITS ;

    /** STORE =0, BEST =8	 */
	private int compressionLevel=DEFAULT_COMPRESSION_LEVEL;
	

	
	/** Constructor:*/
	public DownloadServiceConfiguration(long maxFeatures, long readLimits,
			long writeLimits, long hardOutputLimit, int compressionLevel) {
		this.maxFeatures = maxFeatures;
		this.readLimits = readLimits;
		this.writeLimits = writeLimits;
		this.hardOutputLimit = hardOutputLimit;
		this.compressionLevel = compressionLevel;
	}

	/** Default constructor*/
	public DownloadServiceConfiguration() {
		this(DEFAULT_MAX_FEATURES, DEFAULT_READ_LIMITS, DEFAULT_WRITE_LIMITS, DEFAULT_HARD_OUTPUT_LIMITS, DEFAULT_COMPRESSION_LEVEL);
	}	

	public long getMaxFeatures() {
		return maxFeatures;
	}

	public long getReadLimits() {
		return readLimits;
	}

	public long getWriteLimits() {
		return writeLimits;
	}

	public long getHardOutputLimit() {
		return hardOutputLimit;
	}

	public int getCompressionLevel() {
		return compressionLevel;
	}

	@Override
	public String toString() {
		return "DownloadServiceConfiguration [maxFeatures=" + maxFeatures
				+ ", readLimits=" + readLimits + ", writeLimits=" + writeLimits
				+ ", hardOutputLimit=" + hardOutputLimit
				+ ", compressionLevel=" + compressionLevel + "]";
	}

}
