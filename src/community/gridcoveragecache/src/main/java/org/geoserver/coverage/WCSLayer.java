/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.coverage;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.ResourcePool;
import org.geoserver.gwc.GWC;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.filter.request.RequestFilter;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.OutsideCoverageException;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;
import org.geowebcache.layer.AbstractTileLayer;
import org.geowebcache.layer.ExpirationRule;
import org.geowebcache.layer.meta.LayerMetaInformation;
import org.geowebcache.layer.meta.MetadataURL;
import org.geowebcache.locks.LockProvider;
import org.geowebcache.locks.LockProvider.Lock;
import org.geowebcache.mime.FormatModifier;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.util.GWCVars;

/**
 * A tile layer backed by a WCS server
 */
public class WCSLayer extends AbstractTileLayer {


    private final static Logger LOGGER = org.geotools.util.logging.Logging
            .getLogger(WCSLayer.class);

    private Integer concurrency;

    // private transient int expireCacheInt = -1;

    // private transient int expireClientsInt = -1;

     private transient WCSSourceHelper sourceHelper;

    private transient LockProvider lockProvider;

    private CoverageInfo info;

    public CoverageInfo getInfo() {
        return info;
    }

    public void setInfo(CoverageInfo info) {
        this.info = info;
    }

    private GridSetBroker broker;

    WCSLayer(ResourcePool pool, CoverageInfo info, GridSetBroker broker, GridSubset gridSubSet) {
        try {
            // TODO: FIX THESE PARAMS
            formats = new ArrayList<MimeType>();
            formats.add(MimeType.createFromExtension("tiff"));

            this.broker = broker;

            subSets = new HashMap<String, GridSubset>();
            subSets.put(GridCoveragesCache.REFERENCE.getName(), gridSubSet);
            this.info = info;
            expireCacheList = new ArrayList<ExpirationRule>(1);

            // TODO: Check that expiring cache topic
            expireCacheList.add(new ExpirationRule(0, GWCVars.CACHE_NEVER_EXPIRE));
            name = info.getId();
            sourceHelper = new WCSSourceHelper(this);
        } catch (MimeException e) {
            // TODO: CLEANUP
        }
    }

    /**
     * @see org.geowebcache.layer.TileLayer#initializeInternal(org.geowebcache.grid.GridSetBroker)
     */
    @Override
    protected boolean initializeInternal(GridSetBroker gridSetBroker) {
        if (null == this.enabled) {
            this.enabled = Boolean.TRUE;
        }
        
        if (null == this.sourceHelper) {
            LOGGER.warning(this.name
                    + " is configured without a source, which is a bug unless you're running tests that don't care.");
        }

        if (this.metaWidthHeight == null || this.metaWidthHeight.length != 2) {
            this.metaWidthHeight = new int[2];
            this.metaWidthHeight[0] = 3;
            this.metaWidthHeight[1] = 3;
        }

        if (concurrency == null) {
            concurrency = 32;
        }

        if (this.requestFilters != null) {
            Iterator<RequestFilter> iter = requestFilters.iterator();
            while (iter.hasNext()) {
                try {
                    iter.next().initialize(this);
                } catch (GeoWebCacheException e) {
                    LOGGER.severe(e.getMessage());
                }
            }
        }

        return true;
    }

    /**
     * Used for seeding
     */
    public void seedTile(ConveyorTile tile, boolean tryCache) throws GeoWebCacheException,
            IOException {
        GridSubset gridSubset = getGridSubset(tile.getGridSetId());
        if (gridSubset.shouldCacheAtZoom(tile.getTileIndex()[2])) {
            if (tile.getMimeType().supportsTiling()
                    && (metaWidthHeight[0] > 1 || metaWidthHeight[1] > 1)) {
                // TODO: right now Hardcoded metatiling size
                getMetatilingReponse(tile, tryCache, 2, 2);
            } else {
                getNonMetatilingReponse(tile, tryCache);
            }
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

    /**
     * Non-metatiling forward to backend
     * 
     * @param tile the Tile with all the information
     * @param tryCache whether to try the cache, or seed
     * @throws GeoWebCacheException
     */
    private ConveyorTile getNonMetatilingReponse(ConveyorTile tile, boolean tryCache)
            throws GeoWebCacheException {
        // String debugHeadersStr = null;
        long[] gridLoc = tile.getTileIndex();

        String lockKey = buildLockKey(tile, null);
        Lock lock = null;
        try {
            /** ****************** Acquire lock ******************* */
            lock = lockProvider.getLock(lockKey);

            /** ****************** Check cache again ************** */
            if (tryCache && tryCacheFetch(tile)) {
                // Someone got it already, return lock and we're done
                return tile;
            }

            /** ****************** Tile ******************* */
            // String requestURL = null;
            // Leave a hint to save expiration, if necessary
            if (saveExpirationHeaders) {
                tile.setExpiresHeader(GWCVars.CACHE_USE_WMS_BACKEND_VALUE);
            }

            tile = doNonMetatilingRequest(tile);

            if (tile.getStatus() > 299
                    || this.getExpireCache((int) gridLoc[2]) != GWCVars.CACHE_DISABLE_CACHE) {
                tile.persist();
            }

            if (saveExpirationHeaders) {
                // Converting to seconds in the process
                saveExpirationInformation((int) (tile.getExpiresHeader() / 1000));
            }

            /** ****************** Return lock and response ****** */
        } finally {
            if (lock != null) {
                lock.release();
            }
        }
        return finalizeTile(tile);
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
        // TODO: FIX that request
        tile.setTileLayer(this);

        ByteArrayResource buffer = getImageBuffer(WMS_BUFFER);

        // sourceHelper.makeRequest(tile, buffer);

        if (tile.getError() || buffer.getSize() == 0) {
            throw new GeoWebCacheException("Empty tile, error message: " + tile.getErrorMessage());
        }

        tile.setBlob(buffer);
        return tile;
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

    protected void saveExpirationInformation(int backendExpire) {
        this.saveExpirationHeaders = false;

        try {
            if (getExpireCache(0) == GWCVars.CACHE_USE_WMS_BACKEND_VALUE) {
                if (backendExpire == -1) {
                    this.expireCacheList.set(0, new ExpirationRule(0, 7200));
                    LOGGER.severe("Layer profile wants MaxAge from backend,"
                            + " but backend does not provide this. Setting to 7200 seconds.");
                } else {
                    this.expireCacheList.set(backendExpire, new ExpirationRule(0, 7200));
                }
                LOGGER.finest("Setting expireCache to: " + expireCache);
            }
            if (getExpireCache(0) == GWCVars.CACHE_USE_WMS_BACKEND_VALUE) {
                if (backendExpire == -1) {
                    this.expireClientsList.set(0, new ExpirationRule(0, 7200));
                    LOGGER.severe("Layer profile wants MaxAge from backend,"
                            + " but backend does not provide this. Setting to 7200 seconds.");
                } else {
                    this.expireClientsList.set(0, new ExpirationRule(0, backendExpire));
                    LOGGER.fine("Setting expireClients to: " + expireClients);
                }

            }
        } catch (Exception e) {
            // Sometimes this doesn't work (network conditions?),
            // and it's really not worth getting caught up on it.
            e.printStackTrace();
        }
    }

    public long[][] getZoomedInGridLoc(String gridSetId, long[] gridLoc)
            throws GeoWebCacheException {
        return null;
    }

    public void addMetaWidthHeight(int w, int h) {
        this.metaWidthHeight[0] = w;
        this.metaWidthHeight[1] = h;
    }

    public void setSourceHelper(WCSSourceHelper source) {
        LOGGER.fine("Setting sourceHelper on " + this.name);
        this.sourceHelper = source;

    }

    public ConveyorTile getNoncachedTile(ConveyorTile tile) throws GeoWebCacheException {
        // TODO: FIX THAT REQUEST
        // Should we do mime type checks?

        // note: not using getImageBuffer() here cause this method is not called during seeding, so
        // there's no gain
        Resource buffer = new ByteArrayResource(2048);
        // sourceHelper.makeRequest(tile, buffer);
        tile.setBlob(buffer);

        return tile;
    }

    // public void cleanUpThreadLocals() {
    // WMS_BUFFER.remove();
    // WMS_BUFFER2.remove();
    // }

    public void setMetaInformation(LayerMetaInformation layerMetaInfo) {
        this.metaInformation = layerMetaInfo;
    }

    public void setMetadataURLs(List<MetadataURL> metadataURLs) {
        this.metadataURLs = metadataURLs;
    }

    @Override
    public String getStyles() {
        // Styles are ignored
        return null;
    }

    public void setLockProvider(LockProvider lockProvider) {
        this.lockProvider = lockProvider;
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
        // int gutter = info.getGutter();
        metaTile = new WCSMetaTile(this, gridSubset, responseFormat, formatModifier,
                tileGridPosition, metaX, metaY, null/* ,gutter */);

        return metaTile;
    }

    

   
}
