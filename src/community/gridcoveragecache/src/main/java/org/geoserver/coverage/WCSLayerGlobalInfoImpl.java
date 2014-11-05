package org.geoserver.coverage;

public class WCSLayerGlobalInfoImpl extends WCSLayerInfoImpl implements WCSLayerInfo {

    /** serialVersionUID */
    private static final long serialVersionUID = 1L;

    private static final String DEFAULT_GLOBAL_NAME = "global";

    @Override
    public String getName() {
        return DEFAULT_GLOBAL_NAME;
    }

    @Override
    public String getId() {
        return DEFAULT_GLOBAL_NAME;
    }
}
