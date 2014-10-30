/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.coverage;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.CoverageStoreInfo;
import org.geoserver.catalog.ResourcePool;
import org.geoserver.catalog.impl.LayerGroupInfoImpl;
import org.geoserver.gwc.GWC;
import org.geoserver.gwc.config.GWCConfig;
import org.geoserver.gwc.layer.GeoServerTileLayer;
import org.geoserver.gwc.layer.GeoServerTileLayerInfo;
import org.geoserver.platform.GeoServerExtensions;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.OutsideCoverageException;
import org.geowebcache.locks.LockProvider.Lock;
import org.geowebcache.mime.FormatModifier;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.util.GWCVars;

/**
 * A tile layer backed by a WCS server
 */
public class WCSLayer extends GeoServerTileLayer {

    private static GWCConfig config;

    static {
        List<GWCConfig> extensions = GeoServerExtensions
        .extensions(GWCConfig.class);
        config = GWC.get().getConfig().saneConfig();
    }
    
    private final static Logger LOGGER = org.geotools.util.logging.Logging
            .getLogger(WCSLayer.class);

//    private Integer concurrency;

    // private transient int expireCacheInt = -1;

    // private transient int expireClientsInt = -1;

    private transient WCSSourceHelper sourceHelper;

    private CoverageInfo coverageInfo;

    public CoverageInfo getCoverageInfo() {
        return coverageInfo;
    }

    public void setInfo(CoverageInfo coverageInfo) {
        this.coverageInfo = coverageInfo;
    }

    protected String name;
    
    protected transient Map<String, GridSubset> subSets;


    protected transient List<MimeType> formats;

    WCSLayer(ResourcePool pool, CoverageInfo info, GridSetBroker broker, GridSubset gridSubSet) {
        super(new LayerGroupInfoImpl(), config, broker);
        try {
            // TODO: FIX THESE PARAMS
            formats = new ArrayList<MimeType>();
            formats.add(MimeType.createFromExtension("tiff"));

//            this.broker = broker;

            subSets = new HashMap<String, GridSubset>();
            subSets.put(GridCoveragesCache.REFERENCE.getName(), gridSubSet);
            this.coverageInfo = info;
//            expireCacheList = new ArrayList<ExpirationRule>(1);

            // TODO: Check that expiring cache topic
//            expireCacheList.add(new ExpirationRule(0, GWCVars.CACHE_NEVER_EXPIRE));
            final CoverageStoreInfo storeInfo = info.getStore();
            final String workspaceName = storeInfo.getWorkspace().getName();
            name = workspaceName + ":" + info.getName();
            sourceHelper = new WCSSourceHelper(this);
            GeoServerTileLayerInfo localLayerInfo = getInfo();
            localLayerInfo.setName(name);
            localLayerInfo.setId(info.getId());
            localLayerInfo.getMimeFormats().add("image/tiff");
            
        } catch (MimeException e) {
            // TODO: CLEANUP
        }
    }

    @Override
    public Set<String> getGridSubsets() {
        return Collections.unmodifiableSet(this.subSets.keySet());
    }

    @Override
    public GridSubset getGridSubset(String gridSetId) {
        return subSets.get(gridSetId);
    }
//    /**
//     * @see org.geowebcache.layer.TileLayer#initializeInternal(org.geowebcache.grid.GridSetBroker)
//     */
//    @Override
//    protected boolean initializeInternal(GridSetBroker gridSetBroker) {
//        if (null == this.enabled) {
//            this.enabled = Boolean.TRUE;
//        }
//
//        if (null == this.sourceHelper) {
//            LOGGER.warning(this.name
//                    + " is configured without a source, which is a bug unless you're running tests that don't care.");
//        }
//
//        if (this.metaWidthHeight == null || this.metaWidthHeight.length != 2) {
//            this.metaWidthHeight = new int[2];
//            this.metaWidthHeight[0] = 3;
//            this.metaWidthHeight[1] = 3;
//        }
//
//        if (concurrency == null) {
//            concurrency = 32;
//        }
//
//        if (this.requestFilters != null) {
//            Iterator<RequestFilter> iter = requestFilters.iterator();
//            while (iter.hasNext()) {
//                try {
//                    iter.next().initialize(this);
//                } catch (GeoWebCacheException e) {
//                    LOGGER.severe(e.getMessage());
//                }
//            }
//        }
//
//        return true;
//    }

    /**
     * Used for seeding
     */
    public void seedTile(ConveyorTile tile, boolean tryCache) throws GeoWebCacheException,
            IOException {
        GridSubset gridSubset = getGridSubset(tile.getGridSetId());
        if (gridSubset.shouldCacheAtZoom(tile.getTileIndex()[2])) {
            // Always use metaTiling on seeding since we are implementing our
            // custom GWC layer
            getMetatilingReponse(tile, tryCache, 2, 2);
        }
    }

    private ConveyorTile getMetatilingReponse(ConveyorTile tile, final boolean tryCache,
            final int metaX, final int metaY) throws GeoWebCacheException, IOException {

        final GridSubset gridSubset = getGridSubset(tile.getGridSetId());
        final int zLevel = (int) tile.getTileIndex()[2];
        tile.setMetaTileCacheOnly(!gridSubset.shouldCacheAtZoom(zLevel));

        if (tryCache && tryCacheFetch(tile)) {
            return finalizeTile(tile);
        }

        final WCSMetaTile metaTile = createMetaTile(tile, metaX, metaY);
        Lock lock = null;
        try {
            /** ****************** Acquire lock ******************* */
            lock = GWC.get().getLockProvider().getLock(buildLockKey(tile, metaTile));
            // got the lock on the meta tile, try again
            if (tryCache && tryCacheFetch(tile)) {
                LOGGER.finest("--> " + Thread.currentThread().getName() + " returns cache hit for "
                        + Arrays.toString(metaTile.getMetaGridPos()));
            } else {
                LOGGER.finer("--> " + Thread.currentThread().getName()
                        + " submitting request for meta grid location "
                        + Arrays.toString(metaTile.getMetaGridPos()) + " on " + metaTile);
                try {
                    long requestTime = System.currentTimeMillis();

                    sourceHelper.makeRequest(metaTile, tile);
                    saveTiles(metaTile, tile, requestTime);
                } catch (Exception e) {
                    throw new GeoWebCacheException("Problem communicating with GeoServer", e);
                }
            }
            /** ****************** Return lock and response ****** */
        } finally {
            if (lock != null) {
                lock.release();
            }
            metaTile.dispose();
        }
        return finalizeTile(tile);
    }

    private String buildLockKey(ConveyorTile tile, WCSMetaTile metaTile) {
        StringBuilder metaKey = new StringBuilder();

        final long[] tileIndex;
        if (metaTile != null) {
            tileIndex = metaTile.getMetaGridPos();
            metaKey.append("meta_");
        } else {
            tileIndex = tile.getTileIndex();
            metaKey.append("tile_");
        }
        long x = tileIndex[0];
        long y = tileIndex[1];
        long z = tileIndex[2];

        metaKey.append(tile.getLayerId());
        metaKey.append("_").append(tile.getGridSetId());
        metaKey.append("_").append(x).append("_").append(y).append("_").append(z);
        if (tile.getParametersId() != null) {
            metaKey.append("_").append(tile.getParametersId());
        }
        metaKey.append(".").append(tile.getMimeType().getFileExtension());

        return metaKey.toString();
    }

    public boolean tryCacheFetch(ConveyorTile tile) {
        int expireCache = this.getExpireCache((int) tile.getTileIndex()[2]);
        if (expireCache != GWCVars.CACHE_DISABLE_CACHE) {
            try {
                return tile.retrieve(expireCache * 1000L);
            } catch (GeoWebCacheException gwce) {
                LOGGER.severe(gwce.getMessage());
                tile.setErrorMsg(gwce.getMessage());
                return false;
            }
        }
        return false;
    }

    public ConveyorTile doNonMetatilingRequest(ConveyorTile tile) throws GeoWebCacheException {
        // We are doing our custom GWC layer implementation for gridCoverage setup
        throw new UnsupportedOperationException();
    }

    public ConveyorTile getNoncachedTile(ConveyorTile tile) throws GeoWebCacheException {
        // We are doing our custom GWC layer implementation for gridCoverage setup
        throw new UnsupportedOperationException();
    }


    private ConveyorTile finalizeTile(ConveyorTile tile) {
        if (tile.getStatus() == 0 && !tile.getError()) {
            tile.setStatus(200);
        }

        if (tile.servletResp != null) {
            setExpirationHeader(tile.servletResp, (int) tile.getTileIndex()[2]);
        }

        return tile;
    }

//    protected void saveExpirationInformation(int backendExpire) {
//        this.saveExpirationHeaders = false;
//
//        try {
//            if (getExpireCache(0) == GWCVars.CACHE_USE_WMS_BACKEND_VALUE) {
//                if (backendExpire == -1) {
//                    this.expireCacheList.set(0, new ExpirationRule(0, 7200));
//                    LOGGER.severe("Layer profile wants MaxAge from backend,"
//                            + " but backend does not provide this. Setting to 7200 seconds.");
//                } else {
//                    this.expireCacheList.set(backendExpire, new ExpirationRule(0, 7200));
//                }
//                LOGGER.finest("Setting expireCache to: " + expireCache);
//            }
//            if (getExpireCache(0) == GWCVars.CACHE_USE_WMS_BACKEND_VALUE) {
//                if (backendExpire == -1) {
//                    this.expireClientsList.set(0, new ExpirationRule(0, 7200));
//                    LOGGER.severe("Layer profile wants MaxAge from backend,"
//                            + " but backend does not provide this. Setting to 7200 seconds.");
//                } else {
//                    this.expireClientsList.set(0, new ExpirationRule(0, backendExpire));
//                    LOGGER.fine("Setting expireClients to: " + expireClients);
//                }
//
//            }
//        } catch (Exception e) {
//            // Sometimes this doesn't work (network conditions?),
//            // and it's really not worth getting caught up on it.
//            e.printStackTrace();
//        }
//    }

    public long[][] getZoomedInGridLoc(String gridSetId, long[] gridLoc)
            throws GeoWebCacheException {
        return null;
    }

//    public void addMetaWidthHeight(int w, int h) {
//        this.metaWidthHeight[0] = w;
//        this.metaWidthHeight[1] = h;
//    }

    public void setSourceHelper(WCSSourceHelper source) {
        LOGGER.fine("Setting sourceHelper on " + this.name);
        this.sourceHelper = source;

    }


    // public void cleanUpThreadLocals() {
    // WMS_BUFFER.remove();
    // WMS_BUFFER2.remove();
    // }

//    public void setMetaInformation(LayerMetaInformation layerMetaInfo) {
//        this.metaInformation = layerMetaInfo;
//    }
//
//    public void setMetadataURLs(List<MetadataURL> metadataURLs) {
//        this.metadataURLs = metadataURLs;
//    }

    @Override
    public String getStyles() {
        // Styles are ignored
        return null;
    }

    @Override
    public ConveyorTile getTile(ConveyorTile tile) throws GeoWebCacheException, IOException,
            OutsideCoverageException {
        MimeType mime = tile.getMimeType();
        final List<MimeType> formats = getMimeTypes();
        if (mime == null) {
            mime = formats.get(0);
        } else {
            if (!formats.contains(mime)) {
                throw new IllegalArgumentException(mime.getFormat()
                        + " is not a supported format for " + getName());
            }
        }

        final String tileGridSetId = tile.getGridSetId();
        final GridSubset gridSubset = getGridSubset(tileGridSetId);
        if (gridSubset == null) {
            throw new IllegalArgumentException("Requested gridset not found: " + tileGridSetId);
        }

        final long[] gridLoc = tile.getTileIndex();
        checkNotNull(gridLoc);

        // Final preflight check, throws OutsideCoverageException if necessary
        gridSubset.checkCoverage(gridLoc);

        ConveyorTile returnTile;

        int metaX;
        int metaY;
        if (mime.supportsTiling()) {
            // TODO: Customize metatiling size
            metaX = 2;
            metaY = 2;
        } else {
            metaX = metaY = 1;
        }

        returnTile = getMetatilingReponse(tile, true, metaX, metaY);

        sendTileRequestedEvent(returnTile);

        return returnTile;
    }

    private WCSMetaTile createMetaTile(ConveyorTile tile, final int metaX, final int metaY) {
        WCSMetaTile metaTile;

        final String tileGridSetId = tile.getGridSetId();
        final GridSubset gridSubset = getGridSubset(tileGridSetId);
        final MimeType responseFormat = tile.getMimeType();
        FormatModifier formatModifier = null;
        long[] tileGridPosition = tile.getTileIndex();
        metaTile = new WCSMetaTile(this, gridSubset, responseFormat, formatModifier,
                tileGridPosition, metaX, metaY, tile.getFullParameters());

        return metaTile;
    }

}
