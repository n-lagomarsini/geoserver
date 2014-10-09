/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.coverage.layer;

import it.geosolutions.imageio.plugins.tiff.TIFFImageWriteParam;

import javax.imageio.ImageWriteParam;

import org.geoserver.gwc.layer.GeoServerTileLayerInfo;

public interface CoverageTileLayerInfo extends GeoServerTileLayerInfo {

    public enum SeedingPolicy {
        DIRECT, RECURSIVE;
    }

    public enum TiffCompression {
        NONE {
            @Override
            public ImageWriteParam getCompressionParams() {
                return null;
            }
        },
        LZW {
            @Override
            public ImageWriteParam getCompressionParams() {
                TIFFImageWriteParam writeParam = new TIFFImageWriteParam(null);
                writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                writeParam.setCompressionType("LZW");
                return writeParam;
            }
        },
        DEFLATE {
            @Override
            public ImageWriteParam getCompressionParams() {
                TIFFImageWriteParam writeParam = new TIFFImageWriteParam(null);
                writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                writeParam.setCompressionType("DEFLATE");
                return writeParam;
            }

        },
        JPEG {
            @Override
            public ImageWriteParam getCompressionParams() {
                TIFFImageWriteParam writeParam = new TIFFImageWriteParam(null);
                writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                writeParam.setCompressionType("JPEG");
                return writeParam;
            }

        };

        public abstract ImageWriteParam getCompressionParams();
    }

//    public void setEnabledCaching(boolean enabledCaching);
//    
//    public boolean isEnabledCaching();
    
    //public void setResamplingAlgorithm(Interpolation resamplingAlgorithm);
    
    //public Interpolation getResamplingAlgorithm();
    
    //public void setSeedingPolicy(SeedingPolicy seedingPolicy);
    
    //public SeedingPolicy getSeedingPolicy();
}
