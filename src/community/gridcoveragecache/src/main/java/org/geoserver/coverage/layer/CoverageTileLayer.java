/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.coverage.layer;

import static com.google.common.base.Preconditions.checkNotNull;

import java.awt.image.RenderedImage;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.media.jai.ImageLayout;
import javax.media.jai.Interpolation;
import javax.media.jai.operator.MosaicDescriptor;
import javax.media.jai.operator.ScaleDescriptor;
import javax.media.jai.operator.TranslateDescriptor;

import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.CoverageStoreInfo;
import org.geoserver.catalog.impl.LayerGroupInfoImpl;
import org.geoserver.coverage.WCSSourceHelper;
import org.geoserver.coverage.configuration.CoverageConfiguration;
import org.geoserver.coverage.layer.CoverageTileLayerInfo.InterpolationType;
import org.geoserver.coverage.layer.CoverageTileLayerInfo.SeedingPolicy;
import org.geoserver.coverage.layer.CoverageTileLayerInfo.TiffCompression;
import org.geoserver.gwc.GWC;
import org.geoserver.gwc.layer.GeoServerTileLayer;
import org.geoserver.gwc.layer.GeoServerTileLayerInfo;
import org.geotools.coverage.grid.io.OverviewPolicy;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.OutsideCoverageException;
import org.geowebcache.locks.LockProvider.Lock;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.util.GWCVars;

/**
 * A tile layer backed by a WCS server
 */
public class CoverageTileLayer extends GeoServerTileLayer {

    private static final float ZERO = 0f;

    private static final float HALF_FACTOR = 0.5f;

    private final static Logger LOGGER = org.geotools.util.logging.Logging
            .getLogger(CoverageTileLayer.class);

    private transient WCSSourceHelper sourceHelper;

    private transient CoverageInfo coverageInfo;
    
    protected String name;

    protected Map<String, GridSubset> subSets;

    private ImageLayout layout;

    private String workspaceName;

    private ReferencedEnvelope bbox;

    private String coverageName;

    private SeedingPolicy seedingPolicy = SeedingPolicy.RECURSIVE;

    private Interpolation interpolation = Interpolation.getInstance(Interpolation.INTERP_NEAREST);

    private CoverageTileLayerInfo coverageTileLayerInfo;

    private OverviewPolicy overviewPolicy = OverviewPolicy.QUALITY;

    private TiffCompression tiffCompression  = TiffCompression.DEFLATE;

    public static final MimeType TIFF_MIME_TYPE;

    static {
        try {
            TIFF_MIME_TYPE = MimeType.createFromExtension("tiff");
        } catch (MimeException e) {
            throw new RuntimeException("Exception occurred while getting TIFF mimetype", e);
        }
    }

    public CoverageTileLayer(CoverageInfo info, GridSetBroker broker, List<GridSubset> gridSubsets,
            GeoServerTileLayerInfo tileLayerInfo, boolean init) throws Exception {
        super(new LayerGroupInfoImpl(), broker, tileLayerInfo);

        subSets = new HashMap<String, GridSubset>();
        for(GridSubset gridSubset : gridSubsets){
            subSets.put(gridSubset.getName(), gridSubset);
        }

        final CoverageStoreInfo storeInfo = info.getStore();
        this.coverageInfo = info;
        workspaceName = storeInfo.getWorkspace().getName();
        coverageName = info.getName();
        name = workspaceName + ":" + coverageName;
        bbox = info.boundingBox();
        sourceHelper = new WCSSourceHelper(this);

        GeoServerTileLayerInfo localInfo = super.getInfo();
        if(localInfo instanceof CoverageTileLayerInfo){
            this.coverageTileLayerInfo = (CoverageTileLayerInfo) localInfo;
        } else {
            this.coverageTileLayerInfo = new CoverageTileLayerInfoImpl(localInfo);
        }
        if (init) {
            coverageTileLayerInfo.setId(info.getId());
            coverageTileLayerInfo.setName(name + CoverageConfiguration.COVERAGE_LAYER_SUFFIX);
            coverageTileLayerInfo.getMimeFormats().add("image/tiff");
        }
        seedingPolicy = coverageTileLayerInfo.getSeedingPolicy();
        tiffCompression = coverageTileLayerInfo.getTiffCompression();
        InterpolationType interpolationType = coverageTileLayerInfo.getInterpolationType();
        interpolation = interpolationType != null ? interpolationType.getInterpolationObject() : null;
        overviewPolicy = coverageTileLayerInfo.getOverviewPolicy();
    }

    @Override
    public Set<String> getGridSubsets() {
        return Collections.unmodifiableSet(this.subSets.keySet());
    }

    @Override
    public GridSubset getGridSubset(String gridSetId) {
        return subSets.get(gridSetId);
    }

    public String getCoverageName() {
        return coverageName;
    }

    public String getWorkspaceName() {
        return workspaceName;
    }

    public ReferencedEnvelope getBbox() {
        return bbox;
    }
    
    @Override
    public GeoServerTileLayerInfo getInfo() {
       return coverageTileLayerInfo;
    }

    public CoverageInfo getCoverageInfo() {
        return coverageInfo;
    }

    public void setLayout(ImageLayout layout) {
        this.layout = layout;
    }

    /**
     * Used for seeding
     */
    public void seedTile(ConveyorTile tile, boolean tryCache) throws GeoWebCacheException,
            IOException {
        GridSubset gridSubset = getGridSubset(tile.getGridSetId());
        long[] index = tile.getTileIndex();
        long zLevel = index[2];
        if (gridSubset.shouldCacheAtZoom(zLevel)) {
            // Always use metaTiling on seeding since we are implementing our
            // custom GWC layer
            if (seedingPolicy == SeedingPolicy.DIRECT || zLevel >= gridSubset.getZoomStop()) {
                if (LOGGER.isLoggable(Level.FINER)) {
                    LOGGER.finer("Seeding tile (DIRECT method): " + tile);
                }
                getMetatilingReponse(tile, tryCache, coverageTileLayerInfo.getMetaTilingX(),
                        coverageTileLayerInfo.getMetaTilingY());
            } else {
                if (LOGGER.isLoggable(Level.FINER)) {
                    LOGGER.finer("Seeding tile (recursive method): " + tile);
                }
                recurseTile(tile);
            }
        }
    }

    private ConveyorTile recurseTile(ConveyorTile tile) throws GeoWebCacheException, IOException {
        final GridSubset gridSubset = getGridSubset(tile.getGridSetId());
        final GridSet gridSet = gridSubset.getGridSet();
        final long[] tileIndex = tile.getTileIndex();

        // Getting current tile indexes
        long x = tileIndex[0];
        long y = tileIndex[1];
        long z = tileIndex[2];

        // Setting indexes for 4 tiles coming from the higher resolution level
        z = z + 1; 
        final long minX = x * 2;
        final long maxX = minX + 1;
        final long minY = y * 2;
        final long maxY = minY + 1;

        final int tileWidth = gridSet.getTileWidth();
        final int tileHeight = gridSet.getTileHeight();
        Map<String, ConveyorTile> cTiles = new HashMap<String, ConveyorTile>();
        ConveyorTile ct = null;
        final Map<String, String> parameters = tile.getFullParameters();
        final String gridSetId = tile.getGridSetId();
        for (long i = minX; i <= maxX; i++) {
            for (long j = minY; j <= maxY; j++) {
                // Accessing the 4 tiles from the upper level to obtain this tile
                ct = new ConveyorTile(tile.getStorageBroker(), getName(), gridSetId, new long[] { i, j, z },
                        TIFF_MIME_TYPE, parameters, null, null);
                try {
                    ConveyorTile returnedTile = getTile(ct);
                    String index = i + "_" + j;
                    cTiles.put(index, returnedTile);
                } catch (OutsideCoverageException oce) {
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.fine("Exception occurred while getting tile (" + i + "," + j
                                + ") due to " + oce);
                    }
                }
            }
        }

        // setup tile set to satisfy the request
        // TODO: arrange the ConveyorTilesRenderedImage instead of using the mosaic.

        final Set<String> keys = cTiles.keySet();
        RenderedImage outputTile;
        if (!keys.isEmpty()) {
            int i = 0;
            RenderedImage sources[] = new RenderedImage[4];
            for (String key : keys) {
                final ConveyorTile componentTile = cTiles.get(key);
                final RenderedImage ri = CoverageMetaTile.getResource(componentTile);
                final String indexes[] = key.split("_");
                final int xIndex = Integer.parseInt(indexes[0]);
                final int yIndex = Integer.parseInt(indexes[1]);
                final float translateX = (xIndex - minX) * tileWidth;
                final float translateY = (maxY - yIndex) * tileHeight;

                // Getting the parent tiles and translate them to setup the proper layout before the scaling operation
                sources[i++] = TranslateDescriptor.create(ri, translateX, translateY,
                        Interpolation.getInstance(Interpolation.INTERP_NEAREST), null);
            }

            // Mosaick these 4 tiles to get the current tile.
            // TODO: We should arrange the ConveyorTilesRenderedImage to delegate to job to it.

            final RenderedImage mosaicked = MosaicDescriptor.create(sources,
                    MosaicDescriptor.MOSAIC_TYPE_BLEND, null, null, null, null, null);
//            RenderedImage mosaicked = null;
//
//            try{
//                mosaicked = new ConveyorTilesRenderedImage(cTiles, gridSet, gridSubset, layout);
//            }catch (Exception e){
//                throw new RuntimeException(e);
//            }

            // create the current Tile from the previous 4 using a scale which
            outputTile = ScaleDescriptor.create(mosaicked, HALF_FACTOR, HALF_FACTOR, ZERO, ZERO,
                    interpolation, null);
        } else {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Creating constant image for tile with coordinates: x = " + x + " y = "
                        + y + " z = " + z);
            }
            outputTile = CoverageMetaTile.createConstantImage(layout, tileWidth, tileHeight, null);
        }

        // Create a tile on top of the generated image and save it to store.
        CoverageMetaTile metaTile = null;
        try {
            metaTile = new CoverageMetaTile(this, gridSubset, TIFF_MIME_TYPE, tile.getTileIndex(),
                    1, 1, parameters, 0);
            metaTile.setImage(outputTile);
            saveTiles(metaTile, tile, System.currentTimeMillis());
            return tile;
        } finally {
            metaTile.dispose();
        }
    }

    private ConveyorTile getMetatilingReponse(ConveyorTile tile, final boolean tryCache,
            final int metaX, final int metaY) throws GeoWebCacheException, IOException {

        final CoverageMetaTile metaTile = createMetaTile(tile, metaX, metaY);
        Lock lock = null;
        try {
            /** ****************** Acquire lock ******************* */
            lock = GWC.get().getLockProvider().getLock(buildLockKey(tile, metaTile));
            // got the lock on the meta tile, try again
            if (tryCache && tryCacheFetch(tile)) {
                if (LOGGER.isLoggable(Level.FINEST)) {
                    LOGGER.finest("--> " + Thread.currentThread().getName()
                            + " returns cache hit for "
                            + Arrays.toString(metaTile.getMetaGridPos()));
                }
            } else {
                if (LOGGER.isLoggable(Level.FINER)) {
                    LOGGER.finer("--> " + Thread.currentThread().getName()
                            + " submitting request for meta grid location "
                            + Arrays.toString(metaTile.getMetaGridPos()) + " on " + metaTile);
                }
                try {
                    long requestTime = System.currentTimeMillis();

                    sourceHelper.makeRequest(metaTile, tile, interpolation
                            /*coverageTileLayerInfo.getResamplingAlgorithm()*/,
                            overviewPolicy);
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
        return /*finalizeTile(*/tile;/*);*/
    }

    private String buildLockKey(ConveyorTile tile, CoverageMetaTile metaTile) {
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

    public void setSourceHelper(WCSSourceHelper source) {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Setting sourceHelper on " + this.name);
        }
        this.sourceHelper = source;
    }

    public void cleanUpThreadLocals() {
        WMS_BUFFER.remove();
        WMS_BUFFER2.remove();
    }

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
        final long[] tileIndex = tile.getTileIndex();
        checkNotNull(tileIndex);
        final int zLevel = (int) tileIndex[2];
        tile.setMetaTileCacheOnly(!gridSubset.shouldCacheAtZoom(zLevel));

        if (tryCacheFetch(tile)) {
            return /*finalizeTile(*/tile;/*);*/
        }

        final int numLevels = gridSubset.getGridSet().getNumLevels();

        // Final preflight check, throws OutsideCoverageException if necessary
        gridSubset.checkCoverage(tileIndex);

        ConveyorTile returnTile = null;

        try {
            if (seedingPolicy == SeedingPolicy.DIRECT || zLevel == numLevels - 1 || zLevel == gridSubset.getZoomStart()) {
                returnTile = getMetatilingReponse(tile, true,
                        coverageTileLayerInfo.getMetaTilingX(),
                        coverageTileLayerInfo.getMetaTilingY());
            } else {
                returnTile = recurseTile(tile);
            }

        } finally {
            cleanUpThreadLocals();
        }

        sendTileRequestedEvent(returnTile);

        return returnTile;
    }

    private CoverageMetaTile createMetaTile(ConveyorTile tile, final int metaX, final int metaY) {
        CoverageMetaTile metaTile;

        final String tileGridSetId = tile.getGridSetId();
        final GridSubset gridSubset = getGridSubset(tileGridSetId);
        final MimeType responseFormat = tile.getMimeType();
        long[] tileGridPosition = tile.getTileIndex();
        metaTile = new CoverageMetaTile(this, gridSubset, responseFormat, 
                tileGridPosition, metaX, metaY, tile.getFullParameters(), getInfo().getGutter());

        return metaTile;
    }

    public ImageLayout getLayout() {
        return layout;
    }

    public TiffCompression getTiffCompression() {
        return tiffCompression;
    }

    @Override
    public boolean isAdvertised() {
        // Coverage tile layer aren't advertised by default.
        // We won't deal with them as standard GWC layers.
        // They are special ones which aren't exposed
        return false;
    }

    @Override
    public void setAdvertised(boolean advertised) {
        return;
    }
}
