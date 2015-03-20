/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.wcs.responses;

import java.awt.geom.AffineTransform;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.measure.unit.Unit;
import javax.media.jai.iterator.RandomIter;
import javax.media.jai.iterator.RandomIterFactory;

import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.MetadataMap;
import org.geoserver.config.GeoServer;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.wcs.responses.NetCDFDimensionManager.DimensionValuesArray;
import org.geoserver.wcs.responses.NetCDFDimensionManager.DimensionValuesSet;
import org.geoserver.wcs2_0.response.DimensionBean;
import org.geoserver.wcs2_0.response.DimensionBean.DimensionType;
import org.geoserver.wcs2_0.response.GranuleStack;
import org.geoserver.wcs2_0.response.WCS20GetCoverageResponse;
import org.geoserver.wcs2_0.util.NCNameResourceCodec;
import org.geotools.coverage.GridSampleDimension;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.io.util.DateRangeComparator;
import org.geotools.coverage.io.util.NumberRangeComparator;
import org.geotools.imageio.netcdf.utilities.NetCDFUtilities;
import org.geotools.referencing.CRS;
import org.geotools.referencing.CRS.AxisOrder;
import org.geotools.referencing.operation.matrix.XAffineTransform;
import org.geotools.util.logging.Logging;
import org.opengis.coverage.grid.GridGeometry;
import org.opengis.geometry.Envelope;
import org.opengis.referencing.operation.MathTransform;

import ucar.ma2.Array;
import ucar.ma2.ArrayFloat;
import ucar.ma2.DataType;
import ucar.ma2.Index;
import ucar.ma2.InvalidRangeException;
import ucar.nc2.Attribute;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFileWriter;
import ucar.nc2.NetcdfFileWriter.Version;
import ucar.nc2.Variable;
import ucar.nc2.write.Nc4Chunking;
import ucar.nc2.write.Nc4ChunkingDefault;

/**
 * A class which takes care of initializing NetCDF dimension from coverages dimension, variables, values for the NetCDF output file
 * and finally write them when invoking the write method.
 * 
 * @author Daniele Romagnoli, GeoSolutions SAS
 * 
 */
public class NetCDFOutputManager {

    public static final String STANDARD_NAME = "STANDARD_NAME";

    public static final String UNIT = "UNIT";

    public static final String GLOBAL_ATTRIBUTE_PREFIX = "GLOBAL_ATTRIBUTE_";

    public static final String NETCDF_VERSION_KEY = "NetCDFVersion";

    public static final String NETCDF_COMPRESSION_LEVEL_KEY = "CompressionLevel";

    public static final String NETCDF_SHUFFLE_KEY = "Chunking";

    public static final Logger LOGGER = Logging.getLogger("org.geoserver.wcs.responses.NetCDFFileManager");

    public static final int DEFAULT_LEVEL = 5;

    public static final boolean DEFAULT_SHUFFLE = true;


    /** 
     * A dimension mapping between dimension names and dimension manager instances
     * We use a Linked map to preserve the dimension order 
     */
    private Map<String, NetCDFDimensionManager> dimensionMapping = new LinkedHashMap<String, NetCDFDimensionManager>();

    /** A sample reference granule to get basic properties. */
    private GridCoverage2D sampleGranule;

    /** The stack of granules containing all the GridCoverage2D to be written. */
    private GranuleStack granuleStack;

    /** The global attributes to be added to the output NetCDF */
    private Map<String, String> globalAttributes;

    /** The underlying {@link NetcdfFileWriter} which will be used to write down data. */
    private NetcdfFileWriter writer;

    private final int getNumDimensions() {
        return dimensionMapping.keySet().size();
    }

    /**
     * {@link NetCDFOutputManager} constructor.
     * @param granuleStack the granule stack to be written
     * @param file an output file
     * @throws IOException
     */
    public NetCDFOutputManager(final GranuleStack granuleStack, final File file) throws IOException {
       this(granuleStack, file, null, null);
    }

    /**
     * {@link NetCDFOutputManager} constructor.
     * @param granuleStack the granule stack to be written
     * @param file an output file
     * @param encodingParameters customized encoding params
     * @throws IOException
     */

    public NetCDFOutputManager(GranuleStack granuleStack, File file,
            Map<String, String> encodingParameters, String outputFormat) throws IOException {
        this.granuleStack = granuleStack;
        this.writer = getWriter(file, encodingParameters, outputFormat);
        initialize(encodingParameters);
    }

    private NetcdfFileWriter getWriter(File file, Map<String, String> encodingParameters, String outputFormat) throws IOException {
        if (outputFormat == null || outputFormat.equalsIgnoreCase(NetCDFUtilities.NETCDF3_MIMETYPE) 
                || encodingParameters == null || encodingParameters.isEmpty()) {
            return NetcdfFileWriter.createNew(Version.netcdf3, file.getAbsolutePath());

        } else {
            //TODO properly parse encoding parameters
            return getCustomWriter(file, outputFormat, encodingParameters);
        }
    }

    private NetcdfFileWriter getCustomWriter(File file, String outputFormat,
            Map<String, String> encodingParameters) throws IOException {
        NetcdfFileWriter writer = null;
        Version version = null;
        if (NetCDFUtilities.NETCDF3_MIMETYPE.equalsIgnoreCase(outputFormat)) {
            version = Version.netcdf3;
        } else if (NetCDFUtilities.NETCDF4_MIMETYPE.equalsIgnoreCase(outputFormat)) {
            version = Version.netcdf4_classic;
        }
        if (version == null && encodingParameters.containsKey(NETCDF_VERSION_KEY)) {
            String versionS = encodingParameters.get(NETCDF_VERSION_KEY);
            version = NetCDFUtilities.NETCDF_4C.equalsIgnoreCase(versionS) ? Version.netcdf4_classic
                    : Version.netcdf3;
        }
        if (version == Version.netcdf4_classic) {
            if (!NetCDFUtilities.isNC4CAvailable()) {
                throw new IOException(NetCDFUtilities.NC4_ERROR_MESSAGE);
            }
            Nc4Chunking chunker = null;
            int level = DEFAULT_LEVEL;
            if (encodingParameters.containsKey(NETCDF_COMPRESSION_LEVEL_KEY)) {
                String levelS = encodingParameters.get(NETCDF_COMPRESSION_LEVEL_KEY);
                if (levelS != null && !levelS.isEmpty()) {
                    level = Integer.parseInt(levelS);
                    if (level < 0 && level > 9) {
                        if (LOGGER.isLoggable(Level.WARNING)) {
                            LOGGER.warning("NetCDF 4 compression Level not in the proper range [0, 9]: "
                                    + level + "\nProceeding with default value: " + DEFAULT_LEVEL);
                        }
                    }
                }
            }
            boolean shuffle = DEFAULT_SHUFFLE;
            if (encodingParameters.containsKey(NETCDF_SHUFFLE_KEY)) {
                String shuffleS = encodingParameters.get(NETCDF_SHUFFLE_KEY);
                if (shuffleS != null && !shuffleS.isEmpty()) {
                    shuffle = Boolean.parseBoolean(shuffleS);
                }
            }
            chunker = new Nc4ChunkingDefault(level, shuffle);
            writer = NetcdfFileWriter.createNew(version, file.getAbsolutePath(), chunker);
        }

        return writer != null ? writer : NetcdfFileWriter.createNew(Version.netcdf3, file.getAbsolutePath());
    }

    /**
     * Initialize the Manager by collecting all dimensions from the granule stack 
     * and preparing the mapping. 
     * @param encodingParameters
     */
    private void initialize(Map<String, String> encodingParameters) {
        parseParams(encodingParameters);
        final List<DimensionBean> dimensions = granuleStack.getDimensions();
        for (DimensionBean dimension : dimensions) {

            // Create a new DimensionManager for each dimension
            final String name = dimension.getName();
            final NetCDFDimensionManager manager = new NetCDFDimensionManager(name);

            // Set the input coverage dimension
            manager.setCoverageDimension(dimension);

            // Set the dimension values type
            final DimensionType dimensionType = dimension.getDimensionType();
            final boolean isRange = dimension.isRange();
            TreeSet<Object> tree = null;
            switch (dimensionType) {
            case TIME:
                tree = new TreeSet(new DateRangeComparator());
//                isRange ? new TreeSet(new DateRangeComparator()) : new TreeSet<Date>();
                break;
            case ELEVATION:
                tree = new TreeSet(new NumberRangeComparator());
//                isRange ? new TreeSet(new NumberRangeComparator()) : new TreeSet<Number>();
                break;
            case CUSTOM:
                String dataType = dimension.getDatatype();
                if (NetCDFUtilities.isATime(dataType)) {
                    tree = 
                            //new TreeSet(new DateRangeComparator());
                            isRange ? new TreeSet(new DateRangeComparator()) : new TreeSet<Date>();
                } else {
                    tree = //new TreeSet<Object>();
                            isRange ? new TreeSet(new NumberRangeComparator()) : new TreeSet<Object>();
                }
            }
            manager.setDimensionValues(new DimensionValuesSet(tree));
            dimensionMapping.put(name, manager);
        }

        // Get the dimension values from the coverage and put them on the mapping
        // Note that using tree set allows to respect the ordering when writing
        // down the NetCDF dimensions
        for (GridCoverage2D coverage : granuleStack.getGranules()) {
            updateDimensionValues(coverage);
        }

        sampleGranule = granuleStack.getGranules().get(0);
    }

    private void parseParams(Map<String, String> encodingParameters) {
        Set<String> keys = encodingParameters.keySet();
        if (keys != null && !keys.isEmpty() && keys.contains(WCS20GetCoverageResponse.COVERAGE_ID_PARAM)) {
            String coverageId = encodingParameters.get(WCS20GetCoverageResponse.COVERAGE_ID_PARAM);
            if (coverageId != null) {
                LayerInfo info = NCNameResourceCodec.getCoverage(GeoServerExtensions.bean(GeoServer.class).getCatalog(), coverageId);
                if (info != null) {
                    MetadataMap map = info.getMetadata();
                    
                    //TODO: add here the logic to extract global attributes from the map
                    Map<String, String> attributes = new HashMap<String, String>();
                    Set<String> attributesKeys = attributes.keySet();
                    for (String attributeKey : attributesKeys) {
                        if (attributeKey.startsWith(GLOBAL_ATTRIBUTE_PREFIX)) {
                            String value = attributes.get(attributeKey);
                            if (value != null) {
                                globalAttributes.put(attributeKey, value);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Update the dimension values of a Dimension, by inspecting the coverage properties
     * 
     * @param coverage
     */
    private void updateDimensionValues(GridCoverage2D coverage) {
        Map properties = coverage.getProperties();
        for (NetCDFDimensionManager dimension : dimensionMapping.values()) {
            final String dimensionName = dimension.getName();
            final Object value = properties.get(dimensionName);
            if (value == null) {
                if (LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.warning("No Dimensions available with the specified name: " + dimensionName);
                }
            } else {
                dimension.getDimensionValues().addValue(value);
            }
        }
    }

    /**
     * Return the number of elements for a dimension.
     * 
     * @param dimensionName
     * @return
     */
    private int getDimensionSize(String dimensionName) {
        if (dimensionMapping.containsKey(dimensionName)) {
            return dimensionMapping.get(dimensionName).getDimensionValues().getSize();
        } else {
            throw new IllegalArgumentException("The specified dimension is not available: "
                    + dimensionName);
        }
    }

    /**
     * Initialize the dimensions by creating NetCDF Dimensions of the proper type.
     */
    private void initializeNetCDFDimensions() {

        // TODO: Do we support coverages which doesn't share same BBox?
        // I assume they will still have the same bbox, eventually filled with background data/fill value

        // Prepare latitude and longitude coordinate values
        // TODO: We need to support more CRS

        // Loop over dimensions
        Dimension boundDimension = null;
        for (NetCDFDimensionManager manager : dimensionMapping.values()) {
            final DimensionBean dim = manager.getCoverageDimension();
            final boolean isRange = dim.isRange();
            String dimensionName = manager.getName();
            final int dimensionLength = getDimensionSize(dimensionName);
            if (dimensionName.equalsIgnoreCase("TIME") || dimensionName.equalsIgnoreCase("ELEVATION")) {
                // Special management for TIME and ELEVATION dimensions
                // we will put these dimension lowercase for NetCDF names
                dimensionName = dimensionName.toLowerCase();
            }
            if (isRange) {
                if (boundDimension == null) {
                    boundDimension = writer.addDimension(null, NetCDFUtilities.BOUNDARY_DIMENSION, 2);
                }
            }
            final Dimension netcdfDimension = writer.addDimension(null, dimensionName, dimensionLength);
            manager.setNetCDFDimension(netcdfDimension);

            // Assign variable to dimensions having coordinates
            Variable var = writer.addVariable(null, dimensionName,
                    NetCDFUtilities.getNetCDFDataType(dim.getDatatype()), dimensionName);
            writer.addVariableAttribute(var, new Attribute(NetCDFUtilities.LONG_NAME, dimensionName));
            writer.addVariableAttribute(var, new Attribute(NetCDFUtilities.DESCRIPTION, dimensionName)); 
            // TODO: introduce some lookup table to get a description if needed

            if (NetCDFUtilities.isATime(dim.getDatatype())) {
                // Special management for times. We use the NetCDF convention of defining times starting from
                // an origin. Right now we use the Linux EPOCH
                writer.addVariableAttribute(var, new Attribute(NetCDFUtilities.UNITS, NetCDFUtilities.TIME_ORIGIN));
            } else {
                writer.addVariableAttribute(var, new Attribute(NetCDFUtilities.UNITS, dim.getSymbol()));
            }

            // Add bounds variable for ranges
            if (isRange) {
                final List<Dimension> boundsDimensions = new ArrayList<Dimension>();
                boundsDimensions.add(netcdfDimension);
                boundsDimensions.add(boundDimension);
                final String boundName = dimensionName + NetCDFUtilities.BOUNDS_SUFFIX;
                writer.addVariableAttribute(var, new Attribute(NetCDFUtilities.BOUNDS, boundName));
                writer.addVariable(null, boundName, NetCDFUtilities.getNetCDFDataType(dim.getDatatype()), boundsDimensions);
            }
        }

        setupCoordinates();
    }

    /**
     * Initialize the NetCDF variables on this writer
     * 
     * @param writer
     */
    private void initializeNetCDFVariables() {
        List<Dimension> netCDFDimensions = new LinkedList<Dimension>();
        for (NetCDFDimensionManager manager : dimensionMapping.values()) {
            netCDFDimensions.add(manager.getNetCDFDimension());
        }
        final String coverageName = sampleGranule.getName().toString();

        // Set the proper dataType
        final int dataType = sampleGranule.getRenderedImage().getSampleModel().getDataType();
        DataType varDataType = NetCDFUtilities.transcodeImageDataType(dataType);
        Variable var = writer.addVariable(null, coverageName, varDataType, netCDFDimensions);
        GridSampleDimension[] sampleDimensions = sampleGranule.getSampleDimensions();
        if (sampleDimensions != null && sampleDimensions.length > 0) {
            GridSampleDimension sampleDimension = sampleDimensions[0];
            Unit<?> units = sampleDimension.getUnits();
            double[] noData = sampleDimension.getNoDataValues();
            if (noData != null && noData.length > 0) {
                writer.addVariableAttribute(var, new Attribute(NetCDFUtilities.FILL_VALUE, 
                        NetCDFUtilities.transcodeNumber(varDataType, noData[0])));
            }
            if (units != null) {
                writer.addVariableAttribute(var, new Attribute(NetCDFUtilities.UNITS, units.toString()));
            }
        }
    }

    /**
     * Set the coordinate values for all the dimensions
     * 
     * @param writer
     * @throws IOException
     * @throws InvalidRangeException
     */
    private void setCoordinateVariables() throws IOException, InvalidRangeException {
        for (NetCDFDimensionManager manager : dimensionMapping.values()) {
            Dimension dimension = manager.getNetCDFDimension();
            if (dimension == null) {
                throw new IllegalArgumentException("No Dimension found for this manager: " + manager.getName());
            }

            // Getting coordinate variable for that dimension
            final String dimensionName = dimension.getShortName();
            Variable var = writer.findVariable(dimensionName);
            if (var == null) {
                throw new IllegalArgumentException("Unable to find the specified coordinate variable: " + dimensionName);
            }
            // Writing coordinate variable values
            writer.write(var, manager.getDimensionData(false));

            // handle ranges
            DimensionBean coverageDimension = manager.getCoverageDimension();
            if (coverageDimension != null) { // lat and lon may be null
                boolean isRange = coverageDimension.isRange();
                if (isRange) {
                    var = writer.findVariable(dimensionName + NetCDFUtilities.BOUNDS_SUFFIX);
                    writer.write(var, manager.getDimensionData(true));
                }
            }
        }
    }

    /**
     * Set the variables values
     * @param writer
     * @throws IOException
     * @throws InvalidRangeException
     */
    private void writeDataValues() throws IOException, InvalidRangeException {

        // Initialize dimensions sizes
        final int numDimensions = getNumDimensions();
        final int[] dimSize = new int[numDimensions];
        int iDim = 0;
        for (NetCDFDimensionManager manager: dimensionMapping.values()) {
            dimSize[iDim++] = getDimensionSize(manager.getName());
        }

        final Variable var = writer.findVariable(sampleGranule.getName().toString());
        if (var == null) {
            throw new IllegalArgumentException("The requested variable doesn't exists: " + sampleGranule.getName());
        }

        // Get the data type for a sample image (All granules of the same coverage will use
        final int imageDataType = sampleGranule.getRenderedImage().getSampleModel().getDataType();
        final DataType netCDFDataType = NetCDFUtilities.transcodeImageDataType(imageDataType);
        final Array matrix = NetCDFUtilities.getArray(dimSize, netCDFDataType);

        // Loop over all granules
        for (GridCoverage2D gridCoverage: granuleStack.getGranules()) {
            final RenderedImage ri = gridCoverage.getRenderedImage();

            //
            // Preparing tile properties for future scan
            //
            int width = ri.getWidth();
            int height = ri.getHeight();
            int minX = ri.getMinX();
            int minY = ri.getMinY();
            int maxX = minX + width - 1;
            int maxY = minY + height - 1;
            int tileWidth = Math.min(ri.getTileWidth(), width);
            int tileHeight = Math.min(ri.getTileHeight(), height);

            int minTileX = minX / tileWidth - (minX < 0 ? (-minX % tileWidth > 0 ? 1 : 0): 0);
            int minTileY = minY / tileHeight - (minY < 0 ? (-minY % tileHeight > 0 ? 1 : 0): 0);
            int maxTileX = maxX / tileWidth - (maxX < 0 ? (-maxX % tileWidth > 0 ? 1 : 0): 0);
            int maxTileY = maxY / tileHeight - (maxY < 0 ? (-maxY % tileHeight > 0 ? 1 : 0): 0);

            final Index matrixIndex = matrix.getIndex();
            final int indexing[] = new int[numDimensions];

            // Update the NetCDF array indexing to set values for a specific 2D slice 
            updateIndexing(indexing, gridCoverage);

            // ----------------
            // Fill data matrix
            // ----------------

            // Loop over bands using a RandomIter 
            final RandomIter data = RandomIterFactory.create(ri, null);
            for (int tileY = minTileY; tileY <= maxTileY; tileY++) {
                for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
                    for (int trow = 0; trow < tileHeight; trow++) {
                        int j = (tileY * tileHeight) + trow;
                        if ((j >= minY) && (j <= maxY)) {
                            for (int tcol = 0; tcol < tileWidth; tcol++) {
                                int col = (tileX * tileWidth) + tcol;
                                if ((col >= minX) && (col <= maxX)) {
                                    int k = col;
                                    final int yPos = height - j + minY - 1;

                                    // Simply setting lat and lon
                                    indexing[numDimensions - 1] = k - minX;
                                    indexing[numDimensions - 2] = yPos;
                                    matrixIndex.set(indexing);

                                    // Write data
                                    switch (netCDFDataType) {
                                    case BYTE:
                                        byte sampleByte = (byte) data.getSampleFloat(k, j, 0);
                                        matrix.setByte(matrixIndex, sampleByte);
                                        break;
                                    case SHORT:
                                        short sampleShort = (short) data.getSampleFloat(k, j, 0);
                                        matrix.setShort(matrixIndex, sampleShort);
                                        break;
                                    case INT:
                                        int sampleInt = (int) data.getSampleFloat(k, j, 0);
                                        matrix.setInt(matrixIndex, sampleInt);
                                        break;
                                    case FLOAT:
                                        float sampleFloat = data.getSampleFloat(k, j, 0);
                                        matrix.setFloat(matrixIndex, sampleFloat);
                                        break;
                                    case DOUBLE:
                                        double sampleDouble = data.getSampleDouble(k, j, 0);
                                        matrix.setDouble(matrixIndex, sampleDouble);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // Finalize the iterator 
            data.done();
        }

        // ------------------------------
        // Write the data to the variable
        // ------------------------------
        writer.write(var, matrix);
        writer.flush();
    }

    /**
     * Setup the proper NetCDF array indexing, taking current dimension values from the current coverage 
     * @param indexing
     * @param currentCoverage
     */
    private void updateIndexing(final int[] indexing, final GridCoverage2D currentCoverage) {
        int i = 0;
        int dimElement = 0;
        final Map properties = currentCoverage.getProperties();
        for (NetCDFDimensionManager manager : dimensionMapping.values()) {
            // Loop over dimensions
            final DimensionBean coverageDimension = manager.getCoverageDimension();
            if (coverageDimension != null) { // Lat and lon doesn't have a Coverage dimension
                final String dimensionName = manager.getName();

                // Get the current value for that dimension for this coverage
                final Object val = properties.get(dimensionName);

                // Get all the values for that dimension, looking for the one 
                // which matches the coverage's one
                // TODO: Improve this search. Make it more smart/performant
                final Set<Object> values = (Set<Object>) manager.getDimensionValues().getValues();
                final Iterator<Object> it = values.iterator(); 
                while (it.hasNext()) {
                    Object value = it.next();
                    if (value.equals(val)) {
                        indexing[i++] = dimElement;
                        dimElement = 0; 
                        break;
                    }
                    dimElement++;
                }
            }
        }
    }

    /**
     * Setup lat lon dimension and related coordinates variable
     * 
     * @param ncFileOut
     * @param ri
     * @param transform
     * @param latLonCoordinates
     */
    private void setupCoordinates() {
        //TODO: support more CRSs and coordinates
        final RenderedImage image = sampleGranule.getRenderedImage();
        final Envelope envelope = sampleGranule.getEnvelope2D();

        GridGeometry gridGeometry = sampleGranule.getGridGeometry();
        MathTransform transform = gridGeometry.getGridToCRS();
        AxisOrder axisOrder = CRS.getAxisOrder(sampleGranule.getCoordinateReferenceSystem());

        final int numLat = image.getHeight();
        final int numLon = image.getWidth();

        final AffineTransform at = (AffineTransform) transform;

        // Setup resolutions and bbox extrema to populate regularly gridded coordinate data
        //TODO: investigate whether we need to do some Y axis flipping
        double xmin = (axisOrder == AxisOrder.NORTH_EAST) ? envelope.getMinimum(1) : envelope.getMinimum(0);
        double ymin = (axisOrder == AxisOrder.NORTH_EAST) ? envelope.getMinimum(0) : envelope.getMinimum(1);
        final double periodY = ((axisOrder == AxisOrder.NORTH_EAST) ? XAffineTransform.getScaleX0(at) : XAffineTransform.getScaleY0(at));
        final double periodX = (axisOrder == AxisOrder.NORTH_EAST) ? XAffineTransform.getScaleY0(at) : XAffineTransform.getScaleX0(at);

        // NetCDF coordinates are relative to center. Envelopes are relative to corners: apply an half pixel shift to go back to center
        xmin += (periodX / 2d);
        ymin += (periodY / 2d);

        // Adding lat lon dimensions
        final Dimension latDim = writer.addDimension(null, NetCDFUtilities.LAT, numLat);
        final Dimension lonDim = writer.addDimension(null, NetCDFUtilities.LON, numLon);

        // --------
        // latitude
        // -------- 
        final ArrayFloat latData = new ArrayFloat(new int[] { numLat });
        final Index latIndex = latData.getIndex();
        final Variable varLat = writer.addVariable(null, NetCDFUtilities.LAT, DataType.FLOAT, NetCDFUtilities.LAT);
        writer.addVariableAttribute(varLat, new Attribute(NetCDFUtilities.LONG_NAME, NetCDFUtilities.LATITUDE));
        writer.addVariableAttribute(varLat, new Attribute(NetCDFUtilities.UNITS, NetCDFUtilities.LAT_UNITS));

        for (int yPos = 0; yPos < numLat; yPos++) {
            latData.setFloat(latIndex.set(yPos),
            // new Float(
            // ymax
            // - (new Float(yPos)
            // .floatValue() * periodY))
            // .floatValue());
                    new Float(ymin + (new Float(yPos).floatValue() * periodY)).floatValue());
        }

        // ---------
        // longitude
        // ---------
        final ArrayFloat lonData = new ArrayFloat(new int[] { numLon });
        final Index lonIndex = lonData.getIndex();
        final Variable varLon = writer.addVariable(null, NetCDFUtilities.LON, DataType.FLOAT, NetCDFUtilities.LON);
        writer.addVariableAttribute(varLon, new Attribute(NetCDFUtilities.LONG_NAME, NetCDFUtilities.LONGITUDE));
        writer.addVariableAttribute(varLon, new Attribute(NetCDFUtilities.UNITS, NetCDFUtilities.LON_UNITS));

        for (int xPos = 0; xPos < numLon; xPos++) {
            lonData.setFloat(lonIndex.set(xPos), new Float(xmin
                    + (new Float(xPos).floatValue() * periodX)).floatValue());
        }

        // Latitude management
        final NetCDFDimensionManager latManager = new NetCDFDimensionManager(NetCDFUtilities.LAT);
        latManager.setNetCDFDimension(latDim);
        latManager.setDimensionValues(new DimensionValuesArray(latData));
        dimensionMapping.put(NetCDFUtilities.LAT, latManager);

        // Longitude management
        final NetCDFDimensionManager lonManager = new NetCDFDimensionManager(NetCDFUtilities.LON);
        lonManager.setNetCDFDimension(lonDim);
        lonManager.setDimensionValues(new DimensionValuesArray(lonData));
        dimensionMapping.put(NetCDFUtilities.LON, lonManager);
    }

    /**
     * Write the NetCDF file
     * @throws IOException
     * @throws InvalidRangeException
     */
    public void write() throws IOException, InvalidRangeException {
        initializeNetCDFDimensions();
        initializeNetCDFVariables();
        initializeGlobalAttributes();

        // end of define mode
        writer.create();

        // Setting values
        setCoordinateVariables();
        writeDataValues();

        // Close the writer
        writer.close();

    }

    private void initializeGlobalAttributes() {
        if (globalAttributes != null && !globalAttributes.isEmpty()) {
            Set<String> keys = globalAttributes.keySet();
            for (String key: keys) {
                String value = globalAttributes.get(key);
                if (value == null) {
                    value = "";
                }
                Attribute attr = new Attribute(key, value);
                writer.addGroupAttribute(null, attr);
            }
        }
    }

    /**
     * Release resources
     */
    public void close() {
        // release resources
        for (NetCDFDimensionManager manager: dimensionMapping.values()){
            manager.dispose();
        }
        dimensionMapping.clear();
        dimensionMapping = null;
    }

}
