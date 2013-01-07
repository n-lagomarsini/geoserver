/*
 *    GeoTools - The Open Source Java GIS
 Toolkit
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

import it.geosolutions.imageio.utilities.ImageIOUtilities;

import java.awt.image.RenderedImage;
import java.io.File;
import java.util.logging.Logger;

import javax.media.jai.PlanarImage;

import junit.framework.Assert;

import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.geotools.factory.GeoTools;
import org.geotools.resources.image.ImageUtilities;
import org.junit.Test;
import org.opengis.filter.Filter;


/**
 * Testing the {@link CoverageProcessor} collector.
 * 
 * @author Simone Giannecchini, GeoSolutions SAS
 *
 */
public class CoverageProcessorTest extends BaseRasterAlgebraTest {

    private final Logger LOGGER= org.geotools.util.logging.Logging.getLogger(getClass());
    @Test
    public void testFilters() throws Exception{
        final File directory= new File("./src/test/resources");
        final String[] files = directory.list(
                FileFilterUtils.and(FileFilterUtils.suffixFileFilter("xml"), new IOFileFilter() {
                    
                    @Override
                    public boolean accept(File arg0, String arg1) {
                        try {
                            return getFilter(arg0.getAbsolutePath()+File.separator+arg1) instanceof Filter;
                        } catch (Exception e) {
                            return false;
                        }
                    }
                    
                    @Override
                    public boolean accept(File arg0) {
                        try {
                            return getFilter(arg0.getAbsolutePath()) instanceof Filter;
                        } catch (Exception e) {
                            return false;
                        }
                    }
                })
        );
        // real testing
        Assert.assertNotNull(files);
        for(String file:files){
            LOGGER.info("Testing filter "+file);
            testFilter("./src/test/resources"+File.separator+file); 
            LOGGER.info("Testing filter "+file+" --> Ok");
        }
    }

    /**
     * Testing the filter at the provided path
     * @param filterPath 
     * @throws Exception
     */
    private void testFilter(String filterPath) throws Exception{
        final Filter filter = getFilter(filterPath);
        Assert.assertNotNull(filter);
                
        // instantiate collector
        final CoverageCollector collector= new CoverageCollector(catalog,GeoTools.getDefaultHints());
        filter.accept(collector, null);
        
        // instantiate processor
        final CoverageProcessor processor= new CoverageProcessor(
                collector.getCoverages(),
                collector.getGridGeometry(),
                GeoTools.getDefaultHints());
        final RenderedImage result=testProcessor(filter, processor);
        
        // dispose
        collector.dispose();
        processor.dispose();
        ImageUtilities.disposePlanarImageChain(PlanarImage.wrapRenderedImage(result));
    }

    /**
     * Deep testing of {@link CoverageProcessor};
     * 
     * @param filter
     * @param processor
     * @return 
     */ 
    private RenderedImage testProcessor(final Filter filter, final CoverageProcessor processor) {
        Object result_ = filter.accept(processor, null);
        Assert.assertNotNull(result_);
        Assert.assertTrue(result_ instanceof RenderedImage);
        RenderedImage result= (RenderedImage) result_;
        PlanarImage.wrapRenderedImage(result).getTiles();
        
        // check values
        testBinaryImage(result);
        return result;
    }
}
