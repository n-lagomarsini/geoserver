/**
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */

package org.geoserver.coverage;

import static com.google.common.base.Preconditions.checkNotNull;
import it.geosolutions.imageio.stream.output.ImageOutputStreamAdapter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import net.opengis.wcs20.DimensionSubsetType;
import net.opengis.wcs20.GetCoverageType;
import net.opengis.wcs20.Wcs20Factory;

import org.eclipse.emf.common.util.EList;
import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.ResourcePool;
import org.geoserver.gwc.GWC;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.wcs2_0.DefaultWebCoverageService20;
import org.geoserver.wms.map.RenderedImageMap;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.filter.request.RequestFilter;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.OutsideCoverageException;
import org.geowebcache.io.ByteArrayResource;
import org.geowebcache.io.Resource;
import org.geowebcache.layer.AbstractTileLayer;
import org.geowebcache.layer.ExpirationRule;
import org.geowebcache.layer.MetaTile;
import org.geowebcache.layer.meta.LayerMetaInformation;
import org.geowebcache.layer.meta.MetadataURL;
import org.geowebcache.locks.LockProvider;
import org.geowebcache.locks.LockProvider.Lock;
import org.geowebcache.mime.FormatModifier;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.StorageException;
import org.geowebcache.storage.TileObject;
import org.geowebcache.util.GWCVars;
import org.opengis.coverage.grid.GridCoverage;

/**
 * A tile layer backed by a WCS server
 */
public class WCSLayer extends AbstractTileLayer {

    
    private static final Wcs20Factory WCS20_FACTORY = Wcs20Factory.eINSTANCE;
    private final static Logger LOGGER = org.geotools.util.logging.Logging.getLogger(WCSLayer.class);
    protected Integer gutter;

    // Not used, should be removed through XSL
    @SuppressWarnings("unused")
    private Boolean tiled;

    private String vendorParameters;

    // Not used, should be removed through XSL
    @SuppressWarnings("unused")
    private String cachePrefix;

    private Integer concurrency;

    // private transient int expireCacheInt = -1;

    // private transient int expireClientsInt = -1;

    private transient WCSSourceHelper sourceHelper;

    private transient LockProvider lockProvider;

    private CoverageInfo info;

    WCSLayer(ResourcePool pool, CoverageInfo info, GridSubset gridSubSet) {
        try {
            //TODO: FIX THESE PARAMS
            formats = new ArrayList<MimeType>();
            formats.add(MimeType.createFromExtension("tiff"));
            subSets = new HashMap<String, GridSubset>();
            subSets.put("1", gridSubSet);
            this.info = info;
            expireCacheList = new ArrayList<ExpirationRule>(1);
            expireCacheList.add(new ExpirationRule(0, GWCVars.CACHE_NEVER_EXPIRE));
            name = info.getName();
        } catch (MimeException e) {
            //TODO: CLEANUP
        }
        //default constructor for XStream
    }
//    

    protected WCSLayer readResolve() {
        super.readResolve();
        return this;
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

        if (backendTimeout == null) {
            backendTimeout = 120;
        }

        if (this.metaWidthHeight == null || this.metaWidthHeight.length != 2) {
            this.metaWidthHeight = new int[2];
            this.metaWidthHeight[0] = 3;
            this.metaWidthHeight[1] = 3;
        }

        // Create conditions for tile locking
        if (concurrency == null) {
            concurrency = 32;
        }

//        if (this.sourceHelper instanceof WMSHttpHelper) {
//            for (int i = 0; i < wmsUrl.length; i++) {
//                String url = wmsUrl[i];
//                if (!url.contains("?")) {
//                    wmsUrl[i] = url + "?";
//                }
//            }
//        }
//
        if (gutter == null) {
            gutter = Integer.valueOf(0);
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

//    /**
//     * The main function
//     * 
//     * 1) Create cache key, test whether we can retrieve without locking 2) Get lock for metatile,
//     * monitor condition variable if not (Recheck cache after signal) 3) Create metatile request,
//     * execute 4) Get tiles and save them to cache 5) Unlock metatile, signal other threads 6) Set
//     * Cache-Control, return tile
//     * 
//     * @param wmsparams
//     * @return
//     * @throws OutsideCoverageException
//     */
//    public ConveyorTile getTile(ConveyorTile tile) throws GeoWebCacheException, IOException,
//            OutsideCoverageException {
//        MimeType mime = tile.getMimeType();
//
//        if (mime == null) {
//            mime = this.formats.get(0);
//        }
//
//        if (!formats.contains(mime)) {
//            throw new GeoWebCacheException(mime.getFormat() + " is not a supported format for "
//                    + name);
//        }
//
//        String tileGridSetId = tile.getGridSetId();
//
//        long[] gridLoc = tile.getTileIndex();
//
//        GridSubset gridSubset = getGridSubset(tileGridSetId);
//        // Final preflight check, throws exception if necessary
//        gridSubset.checkCoverage(gridLoc);
//
//        ConveyorTile returnTile;
//
//        tile.setMetaTileCacheOnly(!gridSubset.shouldCacheAtZoom(gridLoc[2]));
//        try {
//            if (tryCacheFetch(tile)) {
//                returnTile = finalizeTile(tile);
//            } else if (mime.supportsTiling()) { // Okay, so we need to go to the backend
//                returnTile = getMetatilingReponse(tile, true);
//            } else {
//                returnTile = getNonMetatilingReponse(tile, true);
//            }
//        } finally {
////            cleanUpThreadLocals();
//        }
//        
//        sendTileRequestedEvent(returnTile);
//
//        return returnTile;
//    }

    /**
     * Used for seeding
     */
    public void seedTile(ConveyorTile tile, boolean tryCache) throws GeoWebCacheException,
            IOException {
        GridSubset gridSubset = getGridSubset(tile.getGridSetId());
        if (gridSubset.shouldCacheAtZoom(tile.getTileIndex()[2])) {
            if (tile.getMimeType().supportsTiling()
                    && (metaWidthHeight[0] > 1 || metaWidthHeight[1] > 1)) {
                //TODO: right now Hardcoded metatiling size
                getMetatilingReponse(tile, tryCache, 2, 2);
            } else {
                getNonMetatilingReponse(tile, tryCache);
            }
        }
    }
//
//    /**
//     * Metatiling request forwarding
//     * 
//     * @param tile
//     *            the Tile with all the information
//     * @param tryCache
//     *            whether to try the cache, or seed
//     * @throws GeoWebCacheException
//     */
//    private ConveyorTile getMetatilingReponse(ConveyorTile tile, boolean tryCache)
//            throws GeoWebCacheException {
//
//        // int idx = this.getSRSIndex(tile.getSRS());
//        long[] gridLoc = tile.getTileIndex();
//
//        GridSubset gridSubset = subSets.get(tile.getGridSetId());
//
//        // GridCalculator gridCalc = getGrid(tile.getSRS()).getGridCalculator();
//
//        MimeType mimeType = tile.getMimeType();
//        Map<String, String> fullParameters = tile.getFullParameters();
//        if (fullParameters.isEmpty()) {
//            fullParameters = getDefaultParameterFilters();
//        }
//        WCSMetaTile metaTile = new WCSMetaTile(this, gridSubset, mimeType,
//                this.getFormatModifier(tile.getMimeType()), gridLoc, metaWidthHeight[0],
//                metaWidthHeight[1], fullParameters);
//
//        // Leave a hint to save expiration, if necessary
//        if (saveExpirationHeaders) {
//            metaTile.setExpiresHeader(GWCVars.CACHE_USE_WMS_BACKEND_VALUE);
//        }
//
//        String metaKey = buildLockKey(tile, metaTile);
//        Lock lock = null;
//        try {
//            /** ****************** Acquire lock ******************* */
//            lock = lockProvider.getLock(metaKey);
//            /** ****************** Check cache again ************** */
//            if (tryCache && tryCacheFetch(tile)) {
//                // Someone got it already, return lock and we're done
//                return finalizeTile(tile);
//            }
//    
//            tile.setCacheResult(CacheResult.MISS);
//            
//            /*
//             * This thread's byte buffer
//             */
//            ByteArrayResource buffer = getImageBuffer(WMS_BUFFER);
//
//            /** ****************** No luck, Request metatile ****** */
//            // Leave a hint to save expiration, if necessary
//            if (saveExpirationHeaders) {
//                metaTile.setExpiresHeader(GWCVars.CACHE_USE_WMS_BACKEND_VALUE);
//            }
//            long requestTime = System.currentTimeMillis();
//            sourceHelper.makeRequest(metaTile, buffer);
//
//            if (metaTile.getError()) {
//                throw new GeoWebCacheException("Empty metatile, error message: "
//                        + metaTile.getErrorMessage());
//            }
//
//            if (saveExpirationHeaders) {
//                // Converting to seconds
//                saveExpirationInformation((int) (tile.getExpiresHeader() / 1000));
//            }
//
//            metaTile.setImageBytes(buffer);
//
//            saveTiles(metaTile, tile, requestTime);
//
//            /** ****************** Return lock and response ****** */
//        } finally {
//            if(lock != null) {
//                lock.release();
//            }
//            metaTile.dispose();
//        }
//        return finalizeTile(tile);
//    }

    private String buildLockKey(ConveyorTile tile, WCSMetaTile metaTile) {
        StringBuilder metaKey = new StringBuilder();
        
        final long[] tileIndex;
        if(metaTile != null) {
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
        if(tile.getParametersId() != null) {
            metaKey.append("_").append(tile.getParametersId());
        }            
        metaKey.append(".").append(tile.getMimeType().getFileExtension());

        return metaKey.toString();
    }

    /**
     * Non-metatiling forward to backend
     * 
     * @param tile
     *            the Tile with all the information
     * @param tryCache
     *            whether to try the cache, or seed
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
            if(lock != null) {
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
        tile.setTileLayer(this);

        ByteArrayResource buffer = getImageBuffer(WMS_BUFFER);
        sourceHelper.makeRequest(tile, buffer);

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
//
//    public void setErrorMime(String errormime) {
//        this.errorMime = errormime;
//    }

    public void addMetaWidthHeight(int w, int h) {
        this.metaWidthHeight[0] = w;
        this.metaWidthHeight[1] = h;
    }

    /**
     * Mandatory
     */
    public void setSourceHelper(WCSSourceHelper source) {
        LOGGER.fine("Setting sourceHelper on " + this.name);
        this.sourceHelper = source;
        if(concurrency != null) {
            this.sourceHelper.setConcurrency(concurrency);
        } else {
            this.sourceHelper.setConcurrency(32);
        }
        if(backendTimeout != null) {
            this.sourceHelper.setBackendTimeout(backendTimeout);
        } else {
            this.sourceHelper.setBackendTimeout(120);
        }
    }

//    public WMSSourceHelper getSourceHelper() {
//        return sourceHelper;
//    }

    public void setTiled(boolean tiled) {
        this.tiled = tiled;
    }

    public ConveyorTile getNoncachedTile(ConveyorTile tile) throws GeoWebCacheException {

        // Should we do mime type checks?

        // note: not using getImageBuffer() here cause this method is not called during seeding, so
        // there's no gain
        Resource buffer = new ByteArrayResource(2048);
        sourceHelper.makeRequest(tile, buffer);
        tile.setBlob(buffer);

        return tile;
    }

//    public String backendSRSOverride(SRS srs) {
//        if (sphericalMercatorOverride != null && srs.equals(SRS.getEPSG3857())) {
//            return sphericalMercatorOverride;
//        } else {
//            return srs.toString();
//        }
//    }
//
//    public void cleanUpThreadLocals() {
//        WMS_BUFFER.remove();
//        WMS_BUFFER2.remove();
//    }

    public void setMetaInformation(LayerMetaInformation layerMetaInfo) {
        this.metaInformation = layerMetaInfo;
    }

    public void setMetadataURLs(List<MetadataURL> metadataURLs) {
        this.metadataURLs = metadataURLs;
    }

    @Override
    public String getStyles() {
        return null;
        //TODO: update that
//        return wmsStyles;
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
            //TODO: Customize metatiling size
            metaX = 2;
            metaY = 2;
        } else {
            metaX = metaY = 1;
        }

        returnTile = getMetatilingReponse(tile, true, metaX, metaY);

        sendTileRequestedEvent(returnTile);

        return returnTile;
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
                        + " submitting getMap request for meta grid location "
                        + Arrays.toString(metaTile.getMetaGridPos()) + " on " + metaTile);
                try {
                    long requestTime = System.currentTimeMillis();
                    
                    List<DefaultWebCoverageService20> extensions = GeoServerExtensions.extensions(DefaultWebCoverageService20.class);
                    DefaultWebCoverageService20 service = extensions.get(0); 
                    makeRequest(metaTile, tile, service);
                    saveTiles(metaTile, tile, requestTime);
                } catch (Exception e) {
                    throw new GeoWebCacheException("Problem communicating with GeoServer", e);
                } 
            }
            /** ****************** Return lock and response ****** */
        } finally {
            if(lock != null) {
                lock.release();
            }
            metaTile.dispose();
        }


        return finalizeTile(tile);
    }
    
    private WCSMetaTile createMetaTile(ConveyorTile tile, final int metaX, final int metaY) {
        WCSMetaTile metaTile;

        String tileGridSetId = tile.getGridSetId();
        GridSubset gridSubset = getGridSubset(tileGridSetId);
        MimeType responseFormat = tile.getMimeType();
        FormatModifier formatModifier = null;
        long[] tileGridPosition = tile.getTileIndex();
//        int gutter = info.getGutter();
        metaTile = new WCSMetaTile(this, gridSubset, responseFormat, formatModifier,
                tileGridPosition, metaX, metaY, null/*,gutter*/);

        return metaTile;
    }
    
    
    public void makeRequest(WCSMetaTile metaTile, ConveyorTile tile, DefaultWebCoverageService20 service) throws GeoWebCacheException {
        GridSubset gridSubset = getGridSubset(tile.getGridSetId());

        Map<String, String> wcsParams = new HashMap<String,String>();

        GetCoverageType request = setupGetCoverageRequest();
        
        BoundingBox bbox = gridSubset.boundsFromIndex(tile.getTileIndex());
        wcsParams.put("BBOX", bbox.toString());
        
        
        GridCoverage coverage = service.getCoverage(request);
        metaTile.setImage(coverage.getRenderedImage());
        
    }

    private GetCoverageType setupGetCoverageRequest() {
        GetCoverageType getCoverage = WCS20_FACTORY.createGetCoverageType();
        getCoverage.setVersion("2.0.1");
        getCoverage.setService("WCS");
        getCoverage.setCoverageId(info.getNamespace().getName() +"__" + info.getName());
        EList<DimensionSubsetType> dimensionSubset = getCoverage.getDimensionSubset();
        return getCoverage;
}
    
    protected void saveTiles(MetaTile metaTile, ConveyorTile tileProto, long requestTime) throws GeoWebCacheException {

        final long[][] gridPositions = metaTile.getTilesGridPositions();
        final long[] gridLoc = tileProto.getTileIndex();
        final GridSubset gridSubset = getGridSubset(tileProto.getGridSetId());

        final int zoomLevel = (int) gridLoc[2];
        final boolean store = this.getExpireCache(zoomLevel) != GWCVars.CACHE_DISABLE_CACHE;

        Resource resource;
        boolean encode;
        
        for (int i = 0; i < gridPositions.length; i++) {
            final long[] gridPos = gridPositions[i];
            if (Arrays.equals(gridLoc, gridPos)) {
                // Is this the one we need to save? then don't use the buffer or it'll be overridden
                // by the next tile
                resource = getImageBuffer(WMS_BUFFER2);
                tileProto.setBlob(resource);
                encode = true;
            } else {
                resource = getImageBuffer(WMS_BUFFER);
                encode = store;
            }

            if (encode) {
                if (!gridSubset.covers(gridPos)) {
                    // edge tile outside coverage, do not store it
                    continue;
                }

                try {
                    boolean completed = metaTile.writeTileToStream(i, resource);
                    if (!completed) {
//                        log.error("metaTile.writeTileToStream returned false, no tiles saved");
                    }
                    if (store) {
                        long[] idx = { gridPos[0], gridPos[1], gridPos[2] };

                        TileObject tile = TileObject.createCompleteTileObject(this.getName(), idx,
                                tileProto.getGridSetId(), tileProto.getMimeType().getFormat(),
                                tileProto.getParameters(), resource);
                        tile.setCreated(requestTime);

                        try {
                            if (tileProto.isMetaTileCacheOnly()) {
                                tileProto.getStorageBroker().putTransient(tile);
                            } else {
                                tileProto.getStorageBroker().put(tile);
                            }
                            tileProto.getStorageObject().setCreated(tile.getCreated());
                        } catch (StorageException e) {
                            throw new GeoWebCacheException(e);
                        }
                    }
                } catch (IOException ioe) {
//                    log.error("Unable to write image tile to " + "ByteArrayOutputStream: "
//                            + ioe.getMessage());
                    ioe.printStackTrace();
                }
            }
        }
    }

}
