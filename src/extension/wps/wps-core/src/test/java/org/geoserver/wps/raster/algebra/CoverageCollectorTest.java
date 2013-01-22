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


import java.io.File;
import java.util.HashMap;

import junit.framework.Assert;

import org.geoserver.wps.raster.algebra.CoverageCollector;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.factory.GeoTools;
import org.junit.Test;
import org.opengis.filter.Filter;

/**
 * Testing {@link CoverageCollector}.
 * 
 * @author Simone Giannecchini, GeoSolutions SAS
 *
 */
public class CoverageCollectorTest extends BaseRasterAlgebraTest{

    @Test
    public void orPropertyIsBetween() throws Exception{
        final File xml= new File("./src/test/resources/orPropertyIsBetween.xml");
        final Filter filter = parseFilter(xml);
        Assert.assertNotNull(filter);
        
        // instantiate collector
        final CoverageCollector collector= new CoverageCollector(catalog,ResolutionChoice.getDefault(),GeoTools.getDefaultHints());
        filter.accept(collector, null);
        
        final HashMap<String, GridCoverage2D> coverages = collector.getCoverages();
        Assert.assertNotNull(coverages);
        Assert.assertEquals("Wrong number of coverages found:"+coverages.size(),2,coverages.size());
        Assert.assertNotNull("Null gridcoverage found: srtm_39_04_1", coverages.get("wcs:srtm_39_04_1"));
        Assert.assertNotNull("Null gridcoverage found: srtm_39_04_2", coverages.get("wcs:srtm_39_04_2"));
        Assert.assertNotNull("Null Gridgeometry found:",collector.getGridGeometry());
        
        collector.dispose();
        
    }
    
    @Test
    public void notPropertyIsBetween() throws Exception{
        final File xml= new File("./src/test/resources/notPropertyIsBetween.xml");
        final Filter filter = parseFilter(xml);
        Assert.assertNotNull(filter);
        
        
        // instantiate collector
        final CoverageCollector collector= new CoverageCollector(catalog,ResolutionChoice.getDefault(),GeoTools.getDefaultHints());
        filter.accept(collector, null);
        
        final HashMap<String, GridCoverage2D> coverages = collector.getCoverages();
        Assert.assertNotNull(coverages);
        Assert.assertEquals("Wrong number of coverages found:"+coverages.size(),1,coverages.size());
        Assert.assertNotNull("Wrong number of coverages found:", coverages.get("wcs:srtm_39_04_1"));
        
        collector.dispose();
        
    }

    @Test
    public void notPropertyIsLessThanOrEqualTo() throws Exception{
        final File xml= new File("./src/test/resources/notPropertyIsLessThanOrEqualTo.xml");
        final Filter filter = parseFilter(xml);
        Assert.assertNotNull(filter);
        
        // instantiate collector
        final CoverageCollector collector= new CoverageCollector(catalog,ResolutionChoice.getDefault(),GeoTools.getDefaultHints());
        filter.accept(collector, null);
        
        final HashMap<String, GridCoverage2D> coverages = collector.getCoverages();
        Assert.assertNotNull(coverages);
        Assert.assertEquals("Wrong number of coverages found:"+coverages.size(),1,coverages.size());
        Assert.assertNotNull("Null gridcoverage found: world", coverages.get("wcs:srtm_39_04_1"));
        Assert.assertNotNull("Null Gridgeometry found:",collector.getGridGeometry());
        
        collector.dispose();
        
    }

    @Test
    public void andPropertyIsBetween() throws Exception{
        final File xml= new File("./src/test/resources/andPropertyIsBetween.xml");
        final Filter filter = parseFilter(xml);
        Assert.assertNotNull(filter);
        
        // instantiate collector
        final CoverageCollector collector= new CoverageCollector(catalog,ResolutionChoice.getDefault(),GeoTools.getDefaultHints());
        filter.accept(collector, null);
        
        final HashMap<String, GridCoverage2D> coverages = collector.getCoverages();
        Assert.assertNotNull(coverages);
        Assert.assertEquals("Wrong number of coverages found:"+coverages.size(),2,coverages.size());
        Assert.assertNotNull("Null gridcoverage found: srtm_39_04_1", coverages.get("wcs:srtm_39_04_1"));
        Assert.assertNotNull("Null gridcoverage found: srtm_39_04_2", coverages.get("wcs:srtm_39_04_2"));
        Assert.assertNotNull("Null Gridgeometry found:",collector.getGridGeometry());
        
        collector.dispose();
        
    }

    @Test
    public void notOrPropertyIsBetween() throws Exception{
        final File xml= new File("./src/test/resources/notOrPropertyIsBetween.xml");
        final Filter filter = parseFilter(xml);
        Assert.assertNotNull(filter);
        
        // instantiate collector
        final CoverageCollector collector= new CoverageCollector(catalog,ResolutionChoice.getDefault(),GeoTools.getDefaultHints());
        filter.accept(collector, null);
        
        final HashMap<String, GridCoverage2D> coverages = collector.getCoverages();
        Assert.assertNotNull(coverages);
        Assert.assertEquals("Wrong number of coverages found:"+coverages.size(),2,coverages.size());
        Assert.assertNotNull("Null gridcoverage found: srtm_39_04_1", coverages.get("wcs:srtm_39_04_1"));
        Assert.assertNotNull("Null gridcoverage found: srtm_39_04_2", coverages.get("wcs:srtm_39_04_2"));
        Assert.assertNotNull("Null Gridgeometry found:",collector.getGridGeometry());
        
        collector.dispose();
        
    } 
}
