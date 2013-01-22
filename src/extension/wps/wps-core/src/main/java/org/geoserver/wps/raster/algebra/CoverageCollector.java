/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 *
 *    (C) 2002-2011, Open Source Geospatial Foundation (OSGeo)
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation;
 *    version 2.1 of the License.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 */
package org.geoserver.wps.raster.algebra;

import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CoverageInfo;
import org.geoserver.wcs.CoverageCleanerCallback;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.factory.Hints;
import org.geotools.filter.visitor.DefaultFilterVisitor;
import org.geotools.geometry.GeneralEnvelope;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.geotools.referencing.operation.matrix.XAffineTransform;
import org.geotools.referencing.operation.transform.AffineTransform2D;
import org.geotools.util.NullProgressListener;
import org.geotools.util.Utilities;
import org.geotools.util.logging.Logging;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterVisitor;
import org.opengis.filter.expression.ExpressionVisitor;
import org.opengis.filter.expression.PropertyName;
import org.opengis.geometry.Envelope;
import org.opengis.parameter.GeneralParameterValue;
import org.opengis.parameter.ParameterValue;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.datum.PixelInCell;
import org.opengis.referencing.operation.MathTransform;

/**
 * Implementation of {@link ExpressionVisitor} and {@link FilterVisitor} that collects the coverages from the 
 * provided filter.
 * 
 * <p>
 * This class assumes the first one is the reference one, which means, its {@link CoordinateReferenceSystem} and
 * GridToWorld {@link AffineTransform2D} will be used to impose the final ones.
 * 
 * <p>
 * The final {@link Envelope} will be set to the intersection of the provided coverages.
 * 
 * <p>
 * The {@link PropertyName}s extracted from the provided {@link FilterVisitor} must indicate via a name existing coverage
 * in the GeoServer in which this process is running. The provided name may or not include the workspace in the usual
 * workspace:identifier form. <strong>If the workspace is not provided the default one will be used</strong>.
 * 
 * 
 * @author Simone Giannecchini, GeoSolutions SAS
 * TODO make the logic to choose the final {@link AffineTransform2D} more generic and if possible customizable.
 */
class CoverageCollector extends DefaultFilterVisitor implements FilterVisitor, ExpressionVisitor {
    
    private final static Logger LOGGER= Logging.getLogger(CoverageCollector.class);

    /** The {@link CoverageInfo} objects that we need.*/
    private final Set<CoverageInfo> coverageNames= new HashSet<CoverageInfo>();
    
    /** The coverage to be used as reference for the computation of the final {@link GridGeometry2D}.*/
    private CoverageInfo referenceCoverage;
    
    /** GeoServer {@link Catalog} to be used to extract the {@link CoverageInfo} information.*/
    private final Catalog catalog;

    /** Reference {@link CoordinateReferenceSystem}.*/
    private CoordinateReferenceSystem referenceCRS;

    /** Final envelope that contains the intersection of the various envelopes. It shall not be empty*/
    private ReferencedEnvelope finalEnvelope;
    
    private final Hints hints;

    /** Final {@link GridGeometry2D}.*/
    private GridGeometry2D finalGridGeometry;
    
    /** Map that maps names to {@link GridCoverage2D} instances. At the end of the visit it contains all the coverages used in the {@link Filter}.*/
    private Map<String, GridCoverage2D> coverages;

    private ResolutionChoice resolutionChoice;

    /** The list of Pixel Size on the X axis.*/
    private List<Double> pixelSizesX= new ArrayList<Double>();

    /** The list Pixel Size on the Y axis.*/
    private List<Double> pixelSizesY= new ArrayList<Double>(); 
    
    /**
     * Constructor.
     * 
     * @param catalog the GeoServer {@link Catalog} to get {@link CoverageInfo} from.
     * @param resolutionChoice how to choose the final pixel size.
     * @param hints {@link Hints} to be used when instantiating {@link GridCoverage2D}.
     */
    public CoverageCollector(Catalog catalog, ResolutionChoice resolutionChoice, Hints hints) {
        Utilities.ensureNonNull("resolutionChoice", resolutionChoice);
        Utilities.ensureNonNull("catalog", catalog);
        
        this.catalog = catalog;
        this.hints=hints.clone();
        this.resolutionChoice=resolutionChoice;
    }

    /**
     * {@link PropertyName} properties indicate coverage names as per the instance in which this process is running.
     * 
     */
    @Override
    public Object visit(PropertyName expression, Object data) {
        org.geotools.util.Utilities.ensureNonNull("expression", expression);
        
        // === get and check name
        final String name=expression.getPropertyName();     
        // checks
        if(name==null||name.length()<=0){
            throw new IllegalArgumentException("Unable to extract property name from the provided expression:"+expression); 
        }
        
        // === extract from catalog and check the coverage
        final CoverageInfo coverage = catalog.getCoverageByName(name);
        if(coverage==null){
            throw new IllegalArgumentException("Unable to locate coverage:"+name);
        } else {
            MathTransform tempTransform = coverage.getGrid().getGridToCRS();
            if(!(tempTransform instanceof AffineTransform)){
                throw new IllegalArgumentException("Grid to world tranform is not an AffineTransform:"+name);
            }
            final AffineTransform tr=(AffineTransform) tempTransform;
            pixelSizesX.add(XAffineTransform.getScaleX0(tr));
            pixelSizesY.add(XAffineTransform.getScaleY0(tr));
            
            if(referenceCoverage==null){
                // set the first use as reference coverage
                referenceCoverage=coverage;
                referenceCRS= referenceCoverage.getCRS();
                
                try {
                    finalEnvelope = referenceCoverage.getNativeBoundingBox();
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, e.getMessage(), e);
                }
                
                // 
            } else {
                
                // get envelope and crs
                final CoordinateReferenceSystem crs= coverage.getCRS();
                ReferencedEnvelope envelope=null;
                try {
                    envelope = coverage.getNativeBoundingBox();
                    
                    // reproject the coverage envelope if needed
                    if(!CRS.equalsIgnoreMetadata(crs, referenceCRS)){
                        envelope=envelope.transform(referenceCRS, true);
                    }                    
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                
                // intersect the reference envelope with the coverage one
                finalEnvelope=ReferencedEnvelope.reference(finalEnvelope.intersection(envelope));                

                // add to the set as this is not a reference coverage
                coverageNames.add(coverage);
            }
        }
        
        
        // return
        return null;
    }

    /**
     * Retrieves a {@link Map} that contains the source {@link GridCoverage2D} along with its name
     * as the key in order to use it later on.
     * 
     * @return a {@link Map} that contains the source {@link GridCoverage2D} along with its name
     * as the key in order to use it later on.
     * 
     * @throws IOException in case something bad happens when reading the {@link GridCoverage2D}.
     */
    public synchronized HashMap<String, GridCoverage2D> getCoverages() throws IOException {
        
        // compute final GridGeometry
        prepareFinalGridGeometry();
        
        // prepare coverages
        prepareFinalCoverage();
        
        return new HashMap<String, GridCoverage2D>(coverages);
    }

    /**
     * @throws IOException 
     * 
     */
    @SuppressWarnings("serial")
    private void prepareFinalCoverage() throws IOException {
        // === checks, we don't want to build this twice
        if(coverages!=null){
            return;
        }
        
        // === make sure we read in streaming and we read just what we need
        final ParameterValue<Boolean> streamingRead = AbstractGridFormat.USE_JAI_IMAGEREAD.createValue();
        streamingRead.setValue(true);
        
        final ParameterValue<GridGeometry2D> readGG = AbstractGridFormat.READ_GRIDGEOMETRY2D.createValue();
        readGG.setValue(finalGridGeometry);
        
        // now prepare the target coverages to match the target GridGeometry
        if(coverageNames.size()==0){
            coverages= new HashMap<String, GridCoverage2D>(){
                {
                    put(referenceCoverage.prefixedName(),(GridCoverage2D)referenceCoverage.getGridCoverageReader(
                            new NullProgressListener(), 
                            hints)
                                .read(new GeneralParameterValue[]{streamingRead,readGG})
                    );
                }
            };
        }
        
        // === we have other grid coverage beside the reference one, let's process them
        // add the reference one
        coverages= new HashMap<String, GridCoverage2D>();
        coverages.put(referenceCoverage.prefixedName(), (GridCoverage2D)referenceCoverage.getGridCoverage(null, hints));
        
        // add the others with proper reprojection if needed
        for(CoverageInfo cov:coverageNames){
            coverages.put(cov.prefixedName(), (GridCoverage2D)cov.getGridCoverage(null, hints));
        }                
    }

    /**
     * Create, once, the final {@link GridGeometry2D} to be used for futher processing.
     * 
     */
    private void prepareFinalGridGeometry() {
        if(finalGridGeometry==null){
            // prepare the envelope and make sure the CRS is set
            final GeneralEnvelope envelope=new GeneralEnvelope(finalEnvelope);
            envelope.setCoordinateReferenceSystem(referenceCRS);
            
            double finalScaleX=resolutionChoice.compute(pixelSizesX);
            double finalScaleY=resolutionChoice.compute(pixelSizesY);
            // G2W transform
            final AffineTransform2D g2w= new AffineTransform2D(
                    finalScaleX,
                    0,
                    0,
                    -finalScaleY,//TODO make this generic with respect to CRS
                    envelope.getUpperCorner().getOrdinate(0)+finalScaleX/2,
                    envelope.getUpperCorner().getOrdinate(1)-finalScaleX/2);
            
            
            // prepare final gridgeometry
            finalGridGeometry= new GridGeometry2D(
                    PixelInCell.CELL_CENTER,
                    g2w,
                    envelope,
                    hints
            );            
        }
       
    }

    /**
     * Provides access to the {@link GridGeometry2D} created for further processing.
     * 
     * @return
     * @throws IOException 
     */
    public synchronized GridGeometry2D getGridGeometry() throws IOException {
        prepareFinalGridGeometry();
        prepareFinalCoverage();
        return finalGridGeometry;
    }
    
    /**
     * Perform clean up on internal resources.
     * 
     * <p>
     * Using this {@link CoverageCollector} after this method has been invoked may result in 
     * unexpected behaviors.
     * 
     */
    public synchronized void dispose(){
        // === clean up
        coverageNames.clear();
        if(coverages!=null){
            // clean
            for(GridCoverage2D gc:coverages.values()){
                CoverageCleanerCallback.disposeCoverage(gc);
            }
            
            // clean map
            coverages.clear();
        }
        
        
    }
}
