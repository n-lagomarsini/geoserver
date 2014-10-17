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
 * @author Arne Kepp, The Open Planning Project, Copyright 2008
 *  
 */
package org.geoserver.coverage;

import it.geosolutions.imageioimpl.plugins.tiff.TIFFImageWriter;
import it.geosolutions.imageioimpl.plugins.tiff.TIFFImageWriterSpi;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.util.Map;

import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.stream.FileCacheImageOutputStream;
import javax.media.jai.PlanarImage;
import javax.media.jai.RenderedOp;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geoserver.wms.map.RenderedImageMapResponse;
import org.geotools.image.crop.GTCropDescriptor;
import org.geotools.resources.i18n.ErrorKeys;
import org.geotools.resources.i18n.Errors;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.io.Resource;
import org.geowebcache.layer.MetaTile;
import org.geowebcache.mime.FormatModifier;
import org.geowebcache.mime.MimeType;


public class WCSMetaTile extends MetaTile {
    private static Log log = LogFactory.getLog(org.geowebcache.layer.wms.WMSMetaTile.class);

    protected WCSLayer wcsLayer = null;

    protected boolean requestTiled = false;

    protected Map<String, String> fullParameters;

    private final static ImageWriterSpi SPI = new TIFFImageWriterSpi();
    
    /**
     * Used for requests by clients
     * 
     * @param profile
     * @param initGridPosition
     */
    protected WCSMetaTile(WCSLayer layer, GridSubset gridSubset, MimeType responseFormat,
            FormatModifier formatModifier, long[] tileGridPosition, int metaX, int metaY,
            Map<String, String> fullParameters) {
        
        super(gridSubset, responseFormat, formatModifier, tileGridPosition, metaX, metaY,
                (layer == null ? null : layer.gutter));
        this.wcsLayer = layer;
        this.fullParameters = fullParameters;

        // ImageUtilities.allowNativeCodec("png", ImageReaderSpi.class, false);
    }


    public int[] getGutter() {
        return gutter.clone();
    }

    protected WCSLayer getLayer() {
        return wcsLayer;
    }
    
    /**
     * Creates the {@link RenderedImage} corresponding to the tile at index {@code tileIdx} and uses
     * a {@link RenderedImageMapResponse} to encode it into the {@link #getResponseFormat() response
     * format}.
     * 
     * @see org.geowebcache.layer.MetaTile#writeTileToStream(int, org.geowebcache.io.Resource)
     * @see RenderedImageMapResponse#write
     * 
     */
    @Override
    public boolean writeTileToStream(final int tileIdx, Resource target) throws IOException {

        TIFFImageWriter writer = (TIFFImageWriter) SPI.createWriterInstance();
        // TODO: ADD try catch block
        FileCacheImageOutputStream iios = new FileCacheImageOutputStream(target.getOutputStream(), new File("c:\\"));
        writer.setOutput(iios);
        writer.write(metaTileImage);
        iios.flush();
        iios.close();
        writer.dispose();
        return true;
    }

    /**
     * Checks if this meta tile has a gutter, or not
     * @return
     */
    private boolean metaHasGutter() {
        if(this.gutter == null) {
            return false;
        }
        
        for (int element : gutter) {
            if(element > 0) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Overrides to use the same method to slice the tiles than {@code MetatileMapOutputFormat} so
     * the GeoServer settings such as use native accel are leveraged in the same way when calling
     * {@link RenderedImageMapResponse#formatImageOutputStream},
     * 
     * @see org.geowebcache.layer.MetaTile#createTile(int, int, int, int)
     */
    @Override
    public RenderedImage createTile(final int x, final int y, final int tileWidth,
            final int tileHeight) {
        // check image type
        final int type;
        if (metaTileImage instanceof PlanarImage) {
            type = 1;
        } else if (metaTileImage instanceof BufferedImage) {
            type = 2;
        } else {
            type = 0;
        }

        // now do the splitting
        RenderedImage tile;
        switch (type) {
        case 0:
            // do a crop, and then turn it into a buffered image so that we can release
            // the image chain
            RenderedOp cropped = GTCropDescriptor
                    .create(metaTileImage, Float.valueOf(x), Float.valueOf(y),
                            Float.valueOf(tileWidth), Float.valueOf(tileHeight), NO_CACHE);
            tile = cropped.getAsBufferedImage();
            disposeLater(cropped);
            break;
        case 1:
            final PlanarImage pImage = (PlanarImage) metaTileImage;
            final WritableRaster wTile = WritableRaster.createWritableRaster(pImage
                    .getSampleModel().createCompatibleSampleModel(tileWidth, tileHeight),
                    new Point(x, y));
            Rectangle sourceArea = new Rectangle(x, y, tileWidth, tileHeight);
            sourceArea = sourceArea.intersection(pImage.getBounds());

            // copying the data to ensure we don't have side effects when we clean the cache
            pImage.copyData(wTile);
            if (wTile.getMinX() != 0 || wTile.getMinY() != 0) {
                tile = new BufferedImage(pImage.getColorModel(),
                        (WritableRaster) wTile.createTranslatedChild(0, 0), pImage.getColorModel()
                                .isAlphaPremultiplied(), null);
            } else {
                tile = new BufferedImage(pImage.getColorModel(), wTile, pImage.getColorModel()
                        .isAlphaPremultiplied(), null);
            }
            break;
        case 2:
            final BufferedImage image = (BufferedImage) metaTileImage;
            tile = image.getSubimage(x, y, tileWidth, tileHeight);
            break;
        default:
            throw new IllegalStateException(Errors.format(ErrorKeys.ILLEGAL_ARGUMENT_$2,
                    "metaTile class", metaTileImage.getClass().toString()));

        }

        return tile;
    }
}
