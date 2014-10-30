package org.geoserver.coverage;

import it.geosolutions.imageioimpl.plugins.tiff.TIFFImageReader;
import it.geosolutions.imageioimpl.plugins.tiff.TIFFImageReaderSpi;

import java.awt.Rectangle;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.stream.FileCacheImageInputStream;
import javax.media.jai.ImageLayout;

import org.apache.commons.io.IOUtils;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.io.Resource;
import org.jaitools.imageutils.ImageLayout2;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

public class ConveyorTilesRenderedImage implements RenderedImage {

    private final static Logger LOGGER = org.geotools.util.logging.Logging
            .getLogger(ConveyorTilesRenderedImage.class);

    private static final TIFFImageReaderSpi spi = new TIFFImageReaderSpi();

    private int tileWidth;

    private int tileHeight;

    private int minX;

    private int minY;

    private ReferencedEnvelope envelope;

    private int width;

    private int height;

    private int numXTiles;

    private int numYTiles;

    private SampleModel sampleModel;

    private ColorModel colorModel;

    private Map<String, ConveyorTile> cTiles;

    private long maxYTotal;

    private int cTileStartX;

    private long cTileStartY;

    public ReferencedEnvelope getEnvelope() {
        return envelope;
    }

    public ConveyorTilesRenderedImage(Map<String, ConveyorTile> cTiles, ImageLayout layout,
            boolean axisOrderingTopDown, int wTiles, int hTiles, Integer zoomLevel,
            GridSet gridSet, GridSubset subset, CoordinateReferenceSystem crs) throws IOException {

        // Value used for defining the Mosaicked image Y origin
        long minYImage = Integer.MAX_VALUE;
        // Maximum Y value for the gridset at the level defined by the zoomlevel variable
        long maxYTotal = gridSet.getGrid(zoomLevel).getNumTilesHigh() - 1;

        if (axisOrderingTopDown) {
            minYImage = 0;
        }
        final int localMinX = layout.getMinX(null);
        final int localMinY = layout.getMinY(null);
        long currentMinY = minYImage;

        tileWidth = layout.getTileWidth(null);
        tileHeight = layout.getTileHeight(null);

        BoundingBox extent = null;
        double minBBX = Double.POSITIVE_INFINITY;
        double minBBY = Double.POSITIVE_INFINITY;
        double maxBBX = Double.NEGATIVE_INFINITY;
        double maxBBY = Double.NEGATIVE_INFINITY;
        Set<String> keys = cTiles.keySet();

        this.cTiles = cTiles;
        ConveyorTile tile = null;
        for (String key : keys) {
            tile = cTiles.get(key);
            long[] tileIndex = tile.getTileIndex();
            extent = subset.boundsFromIndex(tileIndex);
            minBBX = Math.min(minBBX, extent.getMinX());
            minBBY = Math.min(minBBY, extent.getMinY());
            maxBBX = Math.max(maxBBX, extent.getMaxX());
            maxBBY = Math.max(maxBBY, extent.getMaxY());
            currentMinY = (axisOrderingTopDown ? tileIndex[1] : (maxYTotal - tileIndex[1]));
            if (!axisOrderingTopDown && currentMinY < minYImage) {
                minYImage = currentMinY;
            }
        }
        envelope = new ReferencedEnvelope(minBBX, maxBBX, minBBY, maxBBY, crs);

        ImageLayout layout2 = new ImageLayout2(localMinX * tileWidth,
                (int) (axisOrderingTopDown ? localMinY : minYImage)* tileHeight , tileWidth * wTiles,
                tileHeight * hTiles);
        cTileStartX = localMinX;
        cTileStartY = !axisOrderingTopDown ? localMinY : minYImage; 

        minX = layout2.getMinX(null);
        minY = layout2.getMinY(null);
        width = layout2.getWidth(null);
        height = layout2.getHeight(null);
        numXTiles = width / tileWidth;
        numYTiles = height / tileHeight;

        RenderedImage sampleImage = getResource(tile);
        sampleModel = sampleImage.getSampleModel().createCompatibleSampleModel(width, height);
        colorModel = sampleImage.getColorModel();

        // for (int k = 0; k < numImages; k++) {
        // ConveyorTile tile = cTiles.get(k);
        // long[] tileIndex = tile.getTileIndex();
        // RenderedImage inputImage = getResource(tile);
        //
        // // place tile in the proper position
        // riTiles[k] = numImages > 1 ? TranslateDescriptor.create(inputImage,
        // Float.valueOf(tileIndex[0] * tileWidth),
        // Float.valueOf((axisOrderingTopDown ? tileIndex[1] : (maxYTotal - tileIndex[1]) )* tileHeight),
        // Interpolation.getInstance(Interpolation.INTERP_NEAREST), null) : inputImage;
        // if(!axisOrderingTopDown && riTiles[k].getMinY() < minYImage){
        // minYImage = riTiles[k].getMinY();
        // }
        // }
        //
        // RenderedImage finalImage = riTiles[0];
        // if (numImages > 1) {
        // ImageLayout layout = new ImageLayout2(minX * tileWidth,
        // axisOrderingTopDown ? minY * tileHeight : minYImage,
        // tileWidth * wTiles, tileHeight * hTiles);
        //
        // // TODO: Replace that with parallel tile composer which assembles loaded tiles into the final image.
        // finalImage = MosaicDescriptor.create(riTiles, MosaicDescriptor.MOSAIC_TYPE_OVERLAY,
        // null, null, new double[][] { { 1.0 } }, new double[] { 0.0 },
        // // TODO: SET PROPER HINTS
        // new RenderingHints(JAI.KEY_IMAGE_LAYOUT, layout));
        // }
    }

    @Override
    public Vector<RenderedImage> getSources() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Object getProperty(String name) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String[] getPropertyNames() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public ColorModel getColorModel() {
        return colorModel;
    }

    @Override
    public SampleModel getSampleModel() {
        return sampleModel;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public int getMinX() {
        return minX;
    }

    @Override
    public int getMinY() {
        return minY;
    }

    @Override
    public int getNumXTiles() {
        return numXTiles;
    }

    @Override
    public int getNumYTiles() {
        return numYTiles;
    }

    @Override
    public int getMinTileX() {
        return 0;
    }

    @Override
    public int getMinTileY() {
        return 0;
    }

    @Override
    public int getTileWidth() {
        return tileWidth;
    }

    @Override
    public int getTileHeight() {
        return tileHeight;
    }

    @Override
    public int getTileGridXOffset() {
        return minX;
    }

    @Override
    public int getTileGridYOffset() {
        return minY;
    }

    @Override
    public Raster getTile(int tileX, int tileY) {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Getting tile: " + tileX + " , " + tileY);
        }
        final int x = (int) (cTileStartX + tileX);
        final int y = (int) (cTileStartY + tileY);
        ConveyorTile tile = cTiles.get(x + "_" + y);
        try {
            return getResource(tile).getData();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Raster getData() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Raster getData(Rectangle rect) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public WritableRaster copyData(WritableRaster raster) {
        // TODO Auto-generated method stub
        return null;
    }

    /**
     * Extract the RenderedImage from the underlying tile.
     * 
     * @param conveyorTile
     * @return
     * @throws IOException
     */
    private RenderedImage getResource(ConveyorTile conveyorTile) throws IOException {
        final Resource blob = conveyorTile.getBlob();
        //
        InputStream stream = null;
        TIFFImageReader reader = null;
        stream = blob.getInputStream();
        reader = new TIFFImageReader(spi);
        FileCacheImageInputStream fciis = null;
        try {
            fciis = new FileCacheImageInputStream(stream, GridCoveragesCache.tempDir);
            reader.setInput(fciis);
            return reader.read(0);
        } finally {
            IOUtils.closeQuietly(stream);
            IOUtils.closeQuietly(fciis);

            if (reader != null) {
                try {
                    reader.dispose();
                } catch (Throwable t) {

                }
            }
        }
    }
}
