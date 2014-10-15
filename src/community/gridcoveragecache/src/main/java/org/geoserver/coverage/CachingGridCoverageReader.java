package org.geoserver.coverage;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import javax.media.jai.ImageLayout;

import org.geoserver.catalog.CoverageInfo;
import org.geoserver.catalog.ResourcePool;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.io.GridCoverage2DReader;
import org.geotools.coverage.grid.io.OverviewPolicy;
import org.geotools.factory.Hints;
import org.geotools.geometry.GeneralEnvelope;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetFactory;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.GridSubsetFactory;
import org.geowebcache.grid.OutsideCoverageException;
import org.geowebcache.grid.SRS;
import org.geowebcache.io.Resource;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.opengis.coverage.grid.Format;
import org.opengis.coverage.grid.GridEnvelope;
import org.opengis.parameter.GeneralParameterValue;
import org.opengis.parameter.ParameterDescriptor;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.datum.PixelInCell;
import org.opengis.referencing.operation.MathTransform;

public class CachingGridCoverageReader implements GridCoverage2DReader{

    GridCoverage2DReader delegate;
    
    CoverageInfo info;
    
    WCSLayer wcsLayer;
    
    public CachingGridCoverageReader (
            ResourcePool pool,
            CoverageInfo info,
            String coverageName,
            Hints hints
            ) {
       this.info = info;
       Hints localHints = null;
       Hints newHints = new Hints(ResourcePool.SKIP_COVERAGE_EXTENSIONS_LOOKUP, true);
       if (hints != null) {
           localHints = hints.clone();
           localHints.add(newHints);
       } else {
           localHints = newHints;
       }
       
       try {
        delegate = (GridCoverage2DReader) pool.getGridCoverageReader(info, coverageName, localHints);
        GridSubset gridSubSet = buildGridSubSet();
        wcsLayer = new WCSLayer(pool, info, gridSubSet);
    } catch (IOException e) {
        throw new IllegalArgumentException(e);
    }
    }
    
    
    private GridSubset buildGridSubSet() throws IOException {
        GridSet gridSet = buildGridSet();
        GeneralEnvelope env = getOriginalEnvelope(); 
        return GridSubsetFactory.createGridSubSet(gridSet, new BoundingBox(env.getMinimum(0), env.getMinimum(1),
                env.getMaximum(0), env.getMaximum(1)), null, null);
    }


    GridSet buildGridSet () throws IOException{
        int epsgCode = 4326;
        String name = info.getName() + "_" + epsgCode + "_" + 1;
        SRS srs = SRS.getSRS(epsgCode);
        GeneralEnvelope envelope = delegate.getOriginalEnvelope();
        BoundingBox extent = new BoundingBox(envelope.getMinimum(0), envelope.getMinimum(1), 
                envelope.getMaximum(0), envelope.getMaximum(1));
        double[][] resolution = delegate.getResolutionLevels();
        return GridSetFactory.createGridSet(name, srs, extent, true /*CHECKTHAT*/, 3 /*CHECKTHAT_LEVELS*/, 
                1d /*CHECKTHAT_METERS_PER_UNIT*/, 
                resolution[0][0] /*CHECKTHAT_PIXELSIZE*/, 
                512/*CHECKTHAT_TILEWIDTH*/ , 512/*CHECKTHAT_TILEHEIGHT*/ , 
                false /*CHECKTHAT_yCoordinateFirst*/);
        
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
        // TODO Auto-generated method stub
        return read(null, parameters);
    }

    @Override
    public GridCoverage2D read(String coverageName, GeneralParameterValue[] parameters)
            throws IOException {
        // TODO Auto-generated method stub

        ConveyorTile ct;
        ConveyorTile tile;
        try {
            ct = new ConveyorTile(GridCoveragesCache.getStorageBroker(), 
                    //TODO restore layerId
//                layerId, 
                    info.getId(),
                    "1",
                    new long[]{0,0,0}, 
                    MimeType.createFromExtension("tiff"),
                    null, null, null);
            tile = wcsLayer.getTile(ct);

        } catch (MimeException e) {
            throw new IOException(e);
        } catch (OutsideCoverageException e) {
            throw new IOException(e);
        } catch (GeoWebCacheException e) {
            throw new IOException(e);
        }
        Resource blob = tile.getBlob();
        
        
        return null;
    }
}
