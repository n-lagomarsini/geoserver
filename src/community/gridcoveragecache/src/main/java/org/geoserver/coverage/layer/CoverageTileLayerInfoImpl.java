/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.coverage.layer;

import java.io.Serializable;

import org.geoserver.gwc.layer.GeoServerTileLayerInfoImpl;
//import javax.media.jai.Interpolation;
import org.geoserver.gwc.layer.GeoServerTileLayerInfo;

public class CoverageTileLayerInfoImpl extends GeoServerTileLayerInfoImpl implements CoverageTileLayerInfo,Serializable {

    public CoverageTileLayerInfoImpl() {
        super();
    }

    public CoverageTileLayerInfoImpl(GeoServerTileLayerInfo info) {
        super();
        setEnabled(info.isEnabled());
        setGutter(info.getGutter());
        setMetaTilingX(info.getMetaTilingX());
        setMetaTilingY(info.getMetaTilingY());
        setParameterFilters(info.getParameterFilters());
        setGridSubsets(info.getGridSubsets());
        if(info instanceof CoverageTileLayerInfoImpl){
            setInterpolationType(((CoverageTileLayerInfoImpl) info).getInterpolationType());
            setSeedingPolicy(((CoverageTileLayerInfoImpl) info).getSeedingPolicy());
            setTiffCompression(((CoverageTileLayerInfoImpl) info).getTiffCompression());
        }
    }

    /** serialVersionUID */
    private static final long serialVersionUID = 1L;

    private SeedingPolicy seedingPolicy = SeedingPolicy.DIRECT;
    
    private InterpolationType interpolationType = InterpolationType.NEAREST;
    
    private TiffCompression tiffCompression = TiffCompression.NONE;

    @Override
    public void setInterpolationType(InterpolationType resamplingAlgorithm) {
        this.interpolationType = resamplingAlgorithm;
    }

    @Override
    public InterpolationType getInterpolationType() {
        return interpolationType;
    }

    @Override
    public void setSeedingPolicy(SeedingPolicy seedingPolicy) {
        this.seedingPolicy = seedingPolicy;
    }

    @Override
    public SeedingPolicy getSeedingPolicy() {
        return seedingPolicy;
    }

    @Override
    public void setTiffCompression(TiffCompression tiffCompression) {
        this.tiffCompression = tiffCompression;
    }

    @Override
    public TiffCompression getTiffCompression() {
        return tiffCompression;
    }

    @Override
    public GeoServerTileLayerInfoImpl clone() {
        GeoServerTileLayerInfoImpl info = super.clone();
        if(info instanceof CoverageTileLayerInfoImpl){
            return info;
        } else {
            CoverageTileLayerInfoImpl infoImpl = new CoverageTileLayerInfoImpl(info);
            infoImpl.setSeedingPolicy(SeedingPolicy.DIRECT);
            infoImpl.setInterpolationType(InterpolationType.NEAREST);
            infoImpl.setTiffCompression(TiffCompression.NONE);
            return infoImpl;
        }
    }
}
