package org.geoserver.coverage;

import it.geosolutions.imageioimpl.plugins.tiff.TIFFImageReader;
import it.geosolutions.imageioimpl.plugins.tiff.TIFFImageReaderSpi;

import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

import javax.imageio.stream.FileCacheImageInputStream;
import javax.media.jai.ImageLayout;

import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.ResourcePool;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridCoverageFactory;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.coverage.grid.io.GridCoverage2DReader;
import org.geotools.coverage.grid.io.OverviewPolicy;
import org.geotools.factory.Hints;
import org.geotools.geometry.GeneralEnvelope;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.GridSubsetFactory;
import org.geowebcache.grid.OutsideCoverageException;
import org.geowebcache.io.Resource;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.storage.StorageBroker;
import org.opengis.coverage.grid.Format;
import org.opengis.coverage.grid.GridEnvelope;
import org.opengis.geometry.Envelope;
import org.opengis.parameter.GeneralParameterDescriptor;
import org.opengis.parameter.GeneralParameterValue;
import org.opengis.parameter.ParameterDescriptor;
import org.opengis.parameter.ParameterValue;
import org.opengis.referencing.ReferenceIdentifier;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.datum.PixelInCell;
import org.opengis.referencing.operation.MathTransform;

public class CachingGridCoverageReader implements GridCoverage2DReader {

    GridCoverage2DReader delegate;

    private CoverageInfo info;

    private WCSLayer wcsLayer;

    GridCoveragesCache cache;
    
    private static final TIFFImageReaderSpi SPI = new TIFFImageReaderSpi();
    
    private static final GridCoverageFactory gcf = new GridCoverageFactory();

    public CachingGridCoverageReader(ResourcePool pool, GridCoveragesCache cache,
            CoverageInfo info, String coverageName, Hints hints) {
        this.info = info;
        this.cache = cache;
        Hints localHints = null;
        
        // Set hints to exclude gridCoverage extensions lookup to go through
        // the standard gridCoverage reader lookup on ResourcePool
        Hints newHints = new Hints(ResourcePool.SKIP_COVERAGE_EXTENSIONS_LOOKUP, true);
        if (hints != null) {
            localHints = hints.clone();
            localHints.add(newHints);
        } else {
            localHints = newHints;
        }

        try {
            delegate = (GridCoverage2DReader) pool.getGridCoverageReader(info, coverageName,
                    localHints);
            GridSubset gridSubSet = buildGridSubSet();
            wcsLayer = new WCSLayer(pool, info, cache.getGridSetBroker(), gridSubSet);

        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private GridSubset buildGridSubSet() throws IOException {
        GridSet gridSet = buildGridSet();
        GeneralEnvelope env = getOriginalEnvelope();
        return GridSubsetFactory.createGridSubSet(
                gridSet,
                new BoundingBox(env.getMinimum(0), env.getMinimum(1), env.getMaximum(0), env
                        .getMaximum(1)), null, null);
    }

    GridSet buildGridSet() throws IOException {
        // TODO: Replace that using global GridSet
        GridSetBroker broker = cache.getGridSetBroker();

        // TODO: Support different grids
        GridSet set = broker.get(broker.WORLD_EPSG4326.getName());

        return set;

        // Previous code for dynamic gridSet
        //
        // int epsgCode = 4326;
        // String name = info.getName() + "_" + epsgCode + "_" + 1;
        // SRS srs = SRS.getSRS(epsgCode);
        // GeneralEnvelope envelope = delegate.getOriginalEnvelope();
        // BoundingBox extent = new BoundingBox(envelope.getMinimum(0), envelope.getMinimum(1),
        // envelope.getMaximum(0), envelope.getMaximum(1));
        // double[][] resolution = delegate.getResolutionLevels();
        // return GridSetFactory.createGridSet(name, srs, extent, true /*CHECKTHAT*/, 3 /*CHECKTHAT_LEVELS*/,
        // 1d /*CHECKTHAT_METERS_PER_UNIT*/,
        // resolution[0][0] /*CHECKTHAT_PIXELSIZE*/,
        // 512/*CHECKTHAT_TILEWIDTH*/ , 512/*CHECKTHAT_TILEHEIGHT*/ ,
        // false /*CHECKTHAT_yCoordinateFirst*/);
        //
    }

    @Override
    public Format getFormat() {
        return delegate.getFormat();
    }

    @Override
    public Object getSource() {
        return delegate.getSource();
    }

    @Override
    public String[] getMetadataNames() throws IOException {
        return delegate.getMetadataNames();
    }

    @Override
    public String[] getMetadataNames(String coverageName) throws IOException {
        return delegate.getMetadataNames(coverageName);
    }

    @Override
    public String getMetadataValue(String name) throws IOException {
        return delegate.getMetadataValue(name);
    }

    @Override
    public String getMetadataValue(String coverageName, String name) throws IOException {
        return delegate.getMetadataValue(coverageName, name);
    }

    @Override
    public String[] listSubNames() throws IOException {
        return delegate.listSubNames();
    }

    @Override
    public String[] getGridCoverageNames() throws IOException {
        return delegate.getGridCoverageNames();
    }

    @Override
    public int getGridCoverageCount() throws IOException {
        return delegate.getGridCoverageCount();
    }

    @Override
    public String getCurrentSubname() throws IOException {
        return delegate.getCurrentSubname();
    }

    @Override
    public boolean hasMoreGridCoverages() throws IOException {
        return delegate.hasMoreGridCoverages();
    }

    @Override
    public void skip() throws IOException {
        delegate.skip();
    }

    @Override
    public void dispose() throws IOException {
        delegate.dispose();

    }

    @Override
    public GeneralEnvelope getOriginalEnvelope() {
        return delegate.getOriginalEnvelope();
    }

    @Override
    public GeneralEnvelope getOriginalEnvelope(String coverageName) {
        return delegate.getOriginalEnvelope(coverageName);
    }

    @Override
    public CoordinateReferenceSystem getCoordinateReferenceSystem() {
        return delegate.getCoordinateReferenceSystem();
    }

    @Override
    public CoordinateReferenceSystem getCoordinateReferenceSystem(String coverageName) {
        return delegate.getCoordinateReferenceSystem(coverageName);
    }

    @Override
    public GridEnvelope getOriginalGridRange() {
        return delegate.getOriginalGridRange();
    }

    @Override
    public GridEnvelope getOriginalGridRange(String coverageName) {
        return delegate.getOriginalGridRange(coverageName);
    }

    @Override
    public MathTransform getOriginalGridToWorld(PixelInCell pixInCell) {
        return delegate.getOriginalGridToWorld(pixInCell);
    }

    @Override
    public MathTransform getOriginalGridToWorld(String coverageName, PixelInCell pixInCell) {
        return getOriginalGridToWorld(coverageName, pixInCell);
    }

    @Override
    public Set<ParameterDescriptor<List>> getDynamicParameters() throws IOException {
        return delegate.getDynamicParameters();
    }

    @Override
    public Set<ParameterDescriptor<List>> getDynamicParameters(String coverageName)
            throws IOException {
        return delegate.getDynamicParameters(coverageName);
    }

    @Override
    public double[] getReadingResolutions(OverviewPolicy policy, double[] requestedResolution)
            throws IOException {
        return delegate.getReadingResolutions(policy, requestedResolution);
    }

    @Override
    public double[] getReadingResolutions(String coverageName, OverviewPolicy policy,
            double[] requestedResolution) throws IOException {
        return delegate.getReadingResolutions(coverageName, policy, requestedResolution);
    }

    @Override
    public int getNumOverviews() {
        return delegate.getNumOverviews();
    }

    @Override
    public int getNumOverviews(String coverageName) {
        return delegate.getNumOverviews(coverageName);
    }

    @Override
    public ImageLayout getImageLayout() throws IOException {
        return delegate.getImageLayout();
    }

    @Override
    public ImageLayout getImageLayout(String coverageName) throws IOException {
        return delegate.getImageLayout(coverageName);
    }

    @Override
    public double[][] getResolutionLevels() throws IOException {
        return delegate.getResolutionLevels();
    }

    @Override
    public double[][] getResolutionLevels(String coverageName) throws IOException {
        return delegate.getResolutionLevels(coverageName);
    }

    @Override
    public GridCoverage2D read(GeneralParameterValue[] parameters) throws IOException {
        return read(null, parameters);
    }

    @Override
    public GridCoverage2D read(String coverageName, GeneralParameterValue[] parameters)
            throws IOException {

        ConveyorTile ct;
//        ConveyorTile tile;
        try {
            StorageBroker storageBroker = cache.getStorageBroker();
            GridSetBroker gridsetBroker = cache.getGridSetBroker();
            GridSet gridSet = gridsetBroker.WORLD_EPSG4326;
            Envelope envelope = extractEnvelope(coverageName, parameters);
            ReferencedEnvelope env = new ReferencedEnvelope(envelope);
            BoundingBox bbox = new BoundingBox(env.getMinX(), env.getMinY(), env.getMaxX(), env.getMaxY()); 
            
            long[] tiles = gridSet.closestRectangle(bbox);
            
            int minX = (int) tiles[0];
            int minY = (int) tiles[1];
            int maxX = (int) tiles[2];
            int maxY = (int) tiles[3];
            int level = (int) tiles[4];
            int wTiles = maxX - minX + 1; 
            int hTiles = maxY - minY + 1;
            final int numTiles = wTiles * hTiles;
            ConveyorTile cTiles[] = new ConveyorTile[numTiles];

            String id = info.getId();
            String name = gridsetBroker.WORLD_EPSG4326.getName();
            int k = 0;
            for (int i = minX; i <= maxX; i++) {
                for (int j = minY; j <= maxY; j++) {
                    ct = new ConveyorTile(
                            storageBroker,
                            id, name, new long[] { i, j, level },
                            MimeType.createFromExtension("tiff"), null, null, null);
                    cTiles[k++] = wcsLayer.getTile(ct);
                }
            }
            
            final RenderedImage[] riTiles = new RenderedImage[numTiles];
            for (k = 0; k < numTiles; k++) {
                riTiles[k] = getResource(cTiles[k]);
            }
            
            //TODO: SET MOSAICKING 
            RenderedImage mosaicCoverage = riTiles[0];/*MosaicDescriptor.create(riTiles,
                    MosaicDescriptor.MOSAIC_TYPE_OVERLAY, null, null,
                    new double[][] { { 1.0 } }, new double[] { 0.0 }, 
                    //TODO: SET PROPER HINTS
                    null);*/
            // setup tile set to satisfy the request
            
            //TODO: NOTE THAT THE ENVELOPE IS NOT THE SAME OF THE TILES.
            // THEY ARE A SUPER ENSEMBLE OF THE REAL REQUESTED AREA:
            // WE STILL NEED TO CROP IT.
            return gcf.create(name, mosaicCoverage, envelope);
        } catch (MimeException e) {
            throw new IOException(e);
        } catch (OutsideCoverageException e) {
            throw new IOException(e);
        } catch (GeoWebCacheException e) {
            throw new IOException(e);
        }

    }

    private RenderedImage getResource(ConveyorTile conveyorTile) throws IOException {
        Resource blob = conveyorTile.getBlob();
        //
        InputStream stream = null;
        TIFFImageReader reader = null;
        stream = blob.getInputStream();
        reader = new TIFFImageReader(SPI);
        FileCacheImageInputStream fciis = null;
        try {
            fciis = new FileCacheImageInputStream(stream, new File("C:\\"));
            reader.setInput(fciis);
            return reader.read(0);
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (Throwable t) {

                }
            }

            if (fciis != null) {
                try {
                    fciis.close();
                } catch (Throwable t) {

                }
            }
            if (reader != null) {
                try {
                    reader.dispose();
                } catch (Throwable t) {

                }
            }
        }
    }

    private Envelope extractEnvelope(String coverageName, GeneralParameterValue[] parameters) {
        Envelope envelope = null;
        for (GeneralParameterValue gParam : parameters) {
            GeneralParameterDescriptor descriptor = gParam.getDescriptor();
            final ReferenceIdentifier name = descriptor.getName();
            if (name.equals(AbstractGridFormat.READ_GRIDGEOMETRY2D.getName())) {
                if (gParam instanceof ParameterValue<?>) {
                    final ParameterValue<?> param = (ParameterValue<?>) gParam;
                    final Object value = param.getValue();
                    final GridGeometry2D gg = (GridGeometry2D) value;
                    envelope = gg.getEnvelope();
                    break;
                }
            }
        }
        if (envelope == null) {
            envelope = getOriginalEnvelope(coverageName);
        }
        return envelope;
    }
}
