package org.geoserver.coverage;

import javax.media.jai.Interpolation;

import org.geoserver.gwc.config.GWCConfig;
import org.geoserver.gwc.layer.GeoServerTileLayerInfoImpl;

public class WCSLayerInfoImpl extends GeoServerTileLayerInfoImpl implements WCSLayerInfo {
    
    public WCSLayerInfoImpl(){
        super();
    }

    /** serialVersionUID */
    private static final long serialVersionUID = 1L;

    private boolean enabledCaching;

    private Interpolation resamplingAlgorithm = Interpolation.getInstance(Interpolation.INTERP_NEAREST);
    
    private String seedingPolicy = "direct";

    @Override
    public void setResamplingAlgorithm(Interpolation resamplingAlgorithm) {
        this.resamplingAlgorithm = resamplingAlgorithm;
    }

    @Override
    public Interpolation getResamplingAlgorithm() {
        return resamplingAlgorithm;
    }

    @Override
    public void setEnabledCaching(boolean enabledCaching) {
        this.enabledCaching = enabledCaching;
    }

    @Override
    public boolean isEnabledCaching() {
        return enabledCaching;
    }
    
    @Override
    public void setSeedingPolicy(String seedingPolicy) {
        this.seedingPolicy = seedingPolicy;
    }

    @Override
    public String getSeedingPolicy() {
        return seedingPolicy;
    }
    
    public void loadInfo(WCSLayerInfo info){
        setEnabled(info.isEnabled());
        setEnabledCaching(info.isEnabledCaching());
        setGutter(info.getGutter());
        setMetaTilingX(info.getMetaTilingX());
        setMetaTilingY(info.getMetaTilingY());
        setParameterFilters(info.getParameterFilters());
        setResamplingAlgorithm(info.getResamplingAlgorithm());
        setGridSubsets(info.getGridSubsets());
        setSeedingPolicy(info.getSeedingPolicy());
    }
}
