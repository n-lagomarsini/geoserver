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

import java.awt.RenderingHints;
import java.awt.image.RenderedImage;

import javax.media.jai.operator.AndDescriptor;
import javax.media.jai.operator.NotDescriptor;
import javax.media.jai.operator.OrDescriptor;

/**
 * {@link Enum} instance that contains the operations implemented so far for the {@link RasterAlgebraProcess}.
 * 
 * @author Simone Giannecchini, GeoSolutions SAS
 * 
 * TODO add aritmetic operations
 * TODO add geometric operations
 */
enum Operator {
    NOT {
        @Override
        public RenderedImage process(RenderedImage[] sources, final RenderingHints hints) {
            assert sources.length==1;            
            return NotDescriptor.create(sources[0],hints);
        }
    },AND {
        @Override
        public RenderedImage process(RenderedImage[] sources, final RenderingHints hints) {
            assert sources.length>=2;
            if(sources.length==2){
                return AndDescriptor.create(sources[0], sources[1], hints);
            }
            
            // more than 2
            RenderedImage out=sources[0];
            for(int i=1;i<sources.length;i++){
                out=AndDescriptor.create(out, sources[1], hints);
            }
            return out;
            
        }
    },OR {
        @Override
        public RenderedImage process(RenderedImage[] sources, final RenderingHints hints) {
            assert sources.length>=2;
            if(sources.length==2){
                return OrDescriptor.create(sources[0], sources[1], hints);
            }
            
            // more than 2
            RenderedImage out=sources[0];
            for(int i=1;i<sources.length;i++){
                out=OrDescriptor.create(out, sources[1], hints);
            }
            return out;
        }
    },NULL {
        @Override
        public RenderedImage process(RenderedImage[] sources, final RenderingHints hints) {
            assert sources.length==1;
            return sources[0];
        }
    };
    
    public abstract RenderedImage process(final RenderedImage sources[], final RenderingHints hints);
}
