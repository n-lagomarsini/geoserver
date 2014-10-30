/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.coverage;

import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import net.opengis.wcs20.DimensionSubsetType;
import net.opengis.wcs20.DimensionTrimType;
import net.opengis.wcs20.ExtensionItemType;
import net.opengis.wcs20.ExtensionType;
import net.opengis.wcs20.GetCoverageType;
import net.opengis.wcs20.ScaleToSizeType;
import net.opengis.wcs20.ScalingType;
import net.opengis.wcs20.TargetAxisSizeType;
import net.opengis.wcs20.Wcs20Factory;

import org.eclipse.emf.common.util.EList;
import org.geoserver.catalog.CoverageInfo;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.wcs2_0.DefaultWebCoverageService20;
import org.geotools.coverage.grid.GeneralGridEnvelope;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.coverage.processing.CoverageProcessor;
import org.geotools.coverage.processing.operation.Mosaic;
import org.geotools.factory.GeoTools;
import org.geotools.geometry.GeneralEnvelope;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.conveyor.ConveyorTile;
import org.geowebcache.grid.BoundingBox;
import org.geowebcache.grid.GridSubset;
import org.opengis.parameter.ParameterValueGroup;

public class WCSSourceHelper {

    private static final String AXIS_Y = "http://www.opengis.net/def/axis/OGC/1/j";

    private static final String AXIS_X = "http://www.opengis.net/def/axis/OGC/1/i";

    private static final String DOUBLE_UNDERSCORE = "__";

    private static final String WCS_SERVICE_NAME = "WCS";

    private static final String WCS_VERSION = "2.0.1";

    private static final String DIMENSION_LONG = "http://www.opengis.net/def/axis/OGC/0/Long";

    private static final String DIMENSION_LAT = "http://www.opengis.net/def/axis/OGC/0/Lat";

    private static final Wcs20Factory WCS20_FACTORY = Wcs20Factory.eINSTANCE;

    private static final CoverageProcessor processor = CoverageProcessor.getInstance(GeoTools
            .getDefaultHints());

    private static final Mosaic MOSAIC = (Mosaic) processor.getOperation("Mosaic");

    private DefaultWebCoverageService20 service;

    private WCSLayer layer;

    public WCSSourceHelper(WCSLayer layer) {
        this.layer = layer;
        List<DefaultWebCoverageService20> extensions = GeoServerExtensions
                .extensions(DefaultWebCoverageService20.class);
        service = extensions.get(0);
    }

    public void makeRequest(WCSMetaTile metaTile, ConveyorTile tile) throws GeoWebCacheException {
        final GridSubset gridSubset = layer.getGridSubset(tile.getGridSetId());

        final GetCoverageType request = setupGetCoverageRequest(metaTile, gridSubset);

        final GridCoverage2D coverage = (GridCoverage2D) service.getCoverage(request);

        // WCS May return an area which is smaller then requested since it's internally
        // doing an intersection between the requested envelope and the
        // original coverage envelope. We need to properly fill the GridSet tile
        // using a mosaic operation

        // Creation of a List of the input Sources
        List<GridCoverage2D> sources = new ArrayList<GridCoverage2D>(2);
        sources.add(coverage);

        ParameterValueGroup param = MOSAIC.getParameters();
        // Setting of the sources
        param.parameter("Sources").setValue(sources);

        // Getting Metatile properties
        final BoundingBox bbox = metaTile.getMetaTileBounds();
        final int width = metaTile.getMetaTileWidth();
        final int height = metaTile.getMetaTileHeight();

        // Setting the imposed GridGeometry to satisfy the request.
        final GridGeometry2D ggStart = new GridGeometry2D(new GeneralGridEnvelope(new Rectangle(0,
                0, width, height)), new GeneralEnvelope(new Rectangle2D.Double(bbox.getMinX(),
                bbox.getMinY(), bbox.getWidth(), bbox.getHeight())));

        param.parameter("geometry").setValue(ggStart);

        // Mosaic
        final GridCoverage2D mosaic = (GridCoverage2D) processor.doOperation(param);
        metaTile.setImage(mosaic.getRenderedImage());
    }

    /**
     * Setup a proper WCS 2.0 getCoverage request by inspecting tile bbox and forcing size.
     * 
     * @param metaTile
     * @param gridSubset
     * @return
     */
    private GetCoverageType setupGetCoverageRequest(WCSMetaTile metaTile, GridSubset gridSubset) {
        CoverageInfo info = layer.getInfo();
        final GetCoverageType getCoverage = WCS20_FACTORY.createGetCoverageType();
        getCoverage.setVersion(WCS_VERSION);
        getCoverage.setService(WCS_SERVICE_NAME);
        getCoverage.setCoverageId(info.getNamespace().getName() + DOUBLE_UNDERSCORE
                + info.getName());
        final EList<DimensionSubsetType> dimensionSubset = getCoverage.getDimensionSubset();

        // Setting BBOX
        final BoundingBox bbox = metaTile.getMetaTileBounds();
        final int width = metaTile.getMetaTileWidth();
        final int height = metaTile.getMetaTileHeight();

        final DimensionTrimType trimLon = WCS20_FACTORY.createDimensionTrimType();
        trimLon.setDimension(DIMENSION_LONG);
        trimLon.setTrimLow(Double.toString(bbox.getMinX()));
        trimLon.setTrimHigh(Double.toString(bbox.getMaxX()));
        dimensionSubset.add(trimLon);

        final DimensionTrimType trimLat = WCS20_FACTORY.createDimensionTrimType();
        trimLat.setDimension(DIMENSION_LAT);
        trimLat.setTrimLow(Double.toString(bbox.getMinY()));
        trimLat.setTrimHigh(Double.toString(bbox.getMaxY()));
        dimensionSubset.add(trimLat);

        // Setting output size
        final ExtensionType extension = WCS20_FACTORY.createExtensionType();
        getCoverage.setExtension(extension);

        //TODO: Checking targetSize params are properly set
//        ReferencedEnvelope nativeBbox = info.getNativeBoundingBox();
        // Check lon-lat order
//        final double envWidth = nativeBbox.getSpan(0);
//        final double envHeight = nativeBbox.getSpan(1);
//        final int refinedWidth = (int) (((long) envWidth) * width / bbox.getWidth());
//        final int refinedHeight = (int) (((long) envHeight) * height / bbox.getHeight());

        final EList<ExtensionItemType> content = extension.getContents();
        final ExtensionItemType extensionItem = WCS20_FACTORY.createExtensionItemType();
        final ScalingType scalingType = WCS20_FACTORY.createScalingType();

        extensionItem.setName("Scaling");
        extensionItem.setObjectContent(scalingType);
        content.add(extensionItem);

        final ScaleToSizeType scaleToSize = WCS20_FACTORY.createScaleToSizeType();
        scalingType.setScaleToSize(scaleToSize);

        final TargetAxisSizeType lonScalingValue = WCS20_FACTORY.createTargetAxisSizeType();
        lonScalingValue.setAxis(AXIS_X);
        lonScalingValue.setTargetSize(width);

        final TargetAxisSizeType latScalingValue = WCS20_FACTORY.createTargetAxisSizeType();
        latScalingValue.setAxis(AXIS_Y);
        latScalingValue.setTargetSize(height);

        final EList<TargetAxisSizeType> targets = scaleToSize.getTargetAxisSize();
        targets.add(lonScalingValue);
        targets.add(latScalingValue);

        // TODO: Deal with other dimensions
        return getCoverage;
    }

}
