/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.coverage.layer;

import static junit.framework.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.Keyword;
import org.geoserver.catalog.LayerInfo.Type;
import org.geoserver.catalog.NamespaceInfo;
import org.geoserver.catalog.WorkspaceInfo;
import org.geoserver.catalog.impl.CoverageInfoImpl;
import org.geoserver.catalog.impl.CoverageStoreInfoImpl;
import org.geoserver.catalog.impl.DataStoreInfoImpl;
import org.geoserver.catalog.impl.FeatureTypeInfoImpl;
import org.geoserver.catalog.impl.LayerInfoImpl;
import org.geoserver.catalog.impl.NamespaceInfoImpl;
import org.geoserver.catalog.impl.WorkspaceInfoImpl;
import org.geoserver.coverage.layer.CoverageTileLayerInfo.InterpolationType;
import org.geoserver.coverage.layer.CoverageTileLayerInfo.SeedingPolicy;
import org.geoserver.coverage.layer.CoverageTileLayerInfo.TiffCompression;
import org.geoserver.gwc.GWC;
import org.geoserver.gwc.config.GWCConfig;
import org.geoserver.gwc.layer.GeoServerTileLayerInfoImpl;
import org.geotools.coverage.grid.io.OverviewPolicy;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.geowebcache.grid.GridSet;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.grid.GridSubset;
import org.geowebcache.grid.GridSubsetFactory;
import org.geowebcache.locks.MemoryLockProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

public class CoverageTileLayerTest {

    private static final int META_TILING_X = 2;
    
    private static final int META_TILING_Y = 2;

    private static final int GUTTER = 2;

    private static final SeedingPolicy SEEDING_POLICY = SeedingPolicy.DIRECT;

    private static final OverviewPolicy OVERVIEW_POLICY = OverviewPolicy.SPEED;

    private static final InterpolationType INTERPOLATION_TYPE = InterpolationType.NEAREST;

    private static final TiffCompression TIFF_COMPRESSION = TiffCompression.NONE;

    private CoverageInfoImpl coverageInfo;

    private LayerInfoImpl layerInfo;

    private CoverageTileLayer layerInfoTileLayer;

    private Catalog catalog;

    private GridSetBroker gridSetBroker;

    private GWCConfig defaults;

    private GWC mockGWC;

    private GeoServerTileLayerInfoImpl geoserverTileLayerInfo;

    private CoverageTileLayerInfo coverageTileLayerInfo;

    private ArrayList<GridSubset> subsets = new ArrayList<GridSubset>();

    @After
    public void tearDown() throws Exception {
        GWC.set(null);
    }

    @Before
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void setUp() throws Exception {
        mockGWC = mock(GWC.class);
        MemoryLockProvider lockProvider = new MemoryLockProvider();
        when(mockGWC.getLockProvider()).thenReturn(lockProvider);
        GWC.set(mockGWC);

        gridSetBroker = new GridSetBroker(true, true);
        gridSetBroker.put(gridSetBroker.WORLD_EPSG4326);
        gridSetBroker.put(gridSetBroker.WORLD_EPSG3857);
        when (mockGWC.getGridSetBroker()).thenReturn(gridSetBroker);

        final String layerInfoId = "mock-layer-info";
        final String coverageInfoId = "mock-coverage-info";

        NamespaceInfo ns = new NamespaceInfoImpl();
        ns.setPrefix("test");
        ns.setURI("http://goserver.org/test");

        DataStoreInfoImpl storeInfo = new DataStoreInfoImpl(null);
        storeInfo.setId("mock-store-info");
        storeInfo.setEnabled(true);

        ReferencedEnvelope bbox = new ReferencedEnvelope(-180, -90, 0, 0, DefaultGeographicCRS.WGS84);
        FeatureTypeInfoImpl resource = new FeatureTypeInfoImpl((Catalog) null);
        resource.setStore(storeInfo);
        resource.setId("mock-resource-info");
        resource.setName("MockLayerInfoName");
        resource.setNamespace(ns);
        resource.setTitle("Test resource title");
        resource.setAbstract("Test resource abstract");
        resource.setEnabled(true);
        resource.setDescription("Test resource description");
        resource.setLatLonBoundingBox(bbox);
        resource.setNativeBoundingBox(bbox);
        resource.setSRS("EPSG:4326");
        resource.setKeywords((List) Arrays.asList(new Keyword("kwd1"), new Keyword("kwd2")));

        layerInfo = new LayerInfoImpl();
        layerInfo.setId(layerInfoId);
        layerInfo.setResource(resource);
        layerInfo.setEnabled(true);
        layerInfo.setName("MockLayerInfoName");
        layerInfo.setType(Type.VECTOR);

        defaults = GWCConfig.getOldDefaults();
        GridSetBroker broker = GWC.get().getGridSetBroker();
        GridSet gridSet = broker.get("EPSG:4326");
        subsets.add(GridSubsetFactory.createGridSubSet(gridSet));

        catalog = mock(Catalog.class);
        when(catalog.getLayer(eq(layerInfoId))).thenReturn(layerInfo);

        WorkspaceInfo workspace = new WorkspaceInfoImpl();
        workspace.setName("mock-workspace-info");

        CoverageStoreInfoImpl coverageStoreInfo = new CoverageStoreInfoImpl(null);
        coverageStoreInfo.setId("mock-coveragestore-info");
        coverageStoreInfo.setEnabled(true);
        coverageStoreInfo.setName("mock-coveragestore-info");
        coverageStoreInfo.setWorkspace(workspace);

        coverageInfo = new CoverageInfoImpl(null);
        coverageInfo.setId(coverageInfoId);
        coverageInfo.setEnabled(true);
        coverageInfo.setName("MockCoverageInfoName");
        coverageInfo.setStore(coverageStoreInfo);
        coverageInfo.setLatLonBoundingBox(bbox);
        coverageInfo.setNativeBoundingBox(bbox);
        coverageInfo.setNativeCRS(DefaultGeographicCRS.WGS84);
        when(catalog.getCoverage(eq(coverageInfoId))).thenReturn(coverageInfo);

        geoserverTileLayerInfo = new GeoServerTileLayerInfoImpl();
        geoserverTileLayerInfo.setEnabled(true);
        geoserverTileLayerInfo.setMetaTilingX(META_TILING_X);
        geoserverTileLayerInfo.setMetaTilingY(META_TILING_Y);
        geoserverTileLayerInfo.setGutter(GUTTER);
        geoserverTileLayerInfo.setName("test:mockCoverage");

        coverageTileLayerInfo = new CoverageTileLayerInfoImpl(geoserverTileLayerInfo);
        coverageTileLayerInfo.setOverviewPolicy(OVERVIEW_POLICY);
        coverageTileLayerInfo.setInterpolationType(INTERPOLATION_TYPE);
        coverageTileLayerInfo.setTiffCompression(TIFF_COMPRESSION);
        coverageTileLayerInfo.setSeedingPolicy(SEEDING_POLICY);


    }

    @Test
    @Ignore
    public void testEnabled() throws Exception {
        layerInfo.setEnabled(true);
        layerInfoTileLayer = new CoverageTileLayer(coverageInfo, gridSetBroker, subsets, 
                coverageTileLayerInfo, true);
        assertTrue(layerInfoTileLayer.isEnabled());
//
//        layerInfo.setEnabled(false);
//        layerInfoTileLayer = new GeoServerTileLayer(layerInfo, defaults, gridSetBroker);
//        assertFalse(layerInfoTileLayer.isEnabled());
//
//        layerInfo.setEnabled(true);
//        layerInfoTileLayer.setEnabled(true);
//        assertTrue(layerInfoTileLayer.isEnabled());
//        assertTrue(layerInfoTileLayer.getInfo().isEnabled());
//
//        layerInfoTileLayer.setEnabled(false);
//        assertFalse(layerInfoTileLayer.isEnabled());
//        assertFalse(layerInfoTileLayer.getInfo().isEnabled());
    }

//    @Test
//    public void testGetMetaTilingFactors() {
//
//        layerInfoTileLayer = new GeoServerTileLayer(layerInfo, defaults, gridSetBroker);
//
//        int[] metaTilingFactors = layerInfoTileLayer.getMetaTilingFactors();
//        assertEquals(defaults.getMetaTilingX(), metaTilingFactors[0]);
//        assertEquals(defaults.getMetaTilingY(), metaTilingFactors[1]);
//
//        GeoServerTileLayerInfo info = layerInfoTileLayer.getInfo();
//        info.setMetaTilingX(1 + defaults.getMetaTilingX());
//        info.setMetaTilingY(2 + defaults.getMetaTilingY());
//
//        LegacyTileLayerInfoLoader.save(info, layerInfo.getMetadata());
//
//        layerInfoTileLayer = new GeoServerTileLayer(layerInfo, defaults, gridSetBroker);
//        metaTilingFactors = layerInfoTileLayer.getMetaTilingFactors();
//        assertEquals(1 + defaults.getMetaTilingX(), metaTilingFactors[0]);
//        assertEquals(2 + defaults.getMetaTilingY(), metaTilingFactors[1]);
//    }
//
//    @Test
//    public void testGetName() {
//
//        layerInfoTileLayer = new GeoServerTileLayer(layerInfo, defaults, gridSetBroker);
//        assertEquals(tileLayerName(layerInfo), layerInfoTileLayer.getName());
//
//        layerGroupInfoTileLayer = new GeoServerTileLayer(layerGroup, defaults, gridSetBroker);
//        assertEquals(GWC.tileLayerName(layerGroup), layerGroupInfoTileLayer.getName());
//
//    }
//
//    @Test
//    public void testGetParameterFilters() {
//
//        layerInfoTileLayer = new GeoServerTileLayer(layerInfo, defaults, gridSetBroker);
//        List<ParameterFilter> parameterFilters = layerInfoTileLayer.getParameterFilters();
//        assertNotNull(parameterFilters);
//        assertEquals(1, parameterFilters.size());
//    }
//
//
//    @Test
//    public void testGetMetaInformation() {
//        layerInfoTileLayer = new GeoServerTileLayer(layerInfo, defaults, gridSetBroker);
//        layerGroupInfoTileLayer = new GeoServerTileLayer(layerGroup, defaults, gridSetBroker);
//
//        LayerMetaInformation metaInformation = layerInfoTileLayer.getMetaInformation();
//        assertNotNull(metaInformation);
//        String title = metaInformation.getTitle();
//        String description = metaInformation.getDescription();
//        List<String> keywords = metaInformation.getKeywords();
//        assertEquals(layerInfo.getResource().getTitle(), title);
//        assertEquals(layerInfo.getResource().getAbstract(), description);
//        assertEquals(layerInfo.getResource().getKeywords().size(), keywords.size());
//        for (String kw : keywords) {
//            assertTrue(layerInfo.getResource().getKeywords().contains(new Keyword(kw)));
//        }
//
//        metaInformation = layerGroupInfoTileLayer.getMetaInformation();
//        assertNotNull(metaInformation);
//        title = metaInformation.getTitle();
//        description = metaInformation.getDescription();
//        keywords = metaInformation.getKeywords();
//        // these properties are missing from LayerGroupInfo interface
//        assertEquals("Group title", title);
//        assertEquals("Group abstract", description);
//        
//        assertEquals(0, keywords.size());
//    }
//
//    @Test
//    public void testGetGridSubsets() throws Exception {
//        layerInfoTileLayer = new GeoServerTileLayer(layerInfo, defaults, gridSetBroker);
//        Set<String> gridSubsets = layerInfoTileLayer.getGridSubsets();
//        assertNotNull(gridSubsets);
//        assertEquals(2, gridSubsets.size());
//
//        Set<XMLGridSubset> subsets = layerInfoTileLayer.getInfo().getGridSubsets();
//        subsets.clear();
//        XMLGridSubset xmlGridSubset = new XMLGridSubset();
//        xmlGridSubset.setGridSetName("EPSG:900913");
//        subsets.add(xmlGridSubset);
//        LegacyTileLayerInfoLoader.save(layerInfoTileLayer.getInfo(), layerInfo.getMetadata());
//        layerInfoTileLayer = new GeoServerTileLayer(layerInfo, defaults, gridSetBroker);
//
//        gridSubsets = layerInfoTileLayer.getGridSubsets();
//        assertNotNull(gridSubsets);
//        assertEquals(1, gridSubsets.size());
//
//        layerGroup.setBounds(layerInfo.getResource().getLatLonBoundingBox());
//        layerGroupInfoTileLayer = new GeoServerTileLayer(layerGroup, defaults, gridSetBroker);
//        gridSubsets = layerGroupInfoTileLayer.getGridSubsets();
//        assertNotNull(gridSubsets);
//        assertEquals(2, gridSubsets.size());
//    }
//
//    @Test
//    public void testGridSubsetBoundsClippedToTargetCrsAreaOfValidity() throws Exception {
//
//        CoordinateReferenceSystem nativeCrs = CRS.decode("EPSG:4326", true);
//        ReferencedEnvelope nativeBounds = new ReferencedEnvelope(-180, 180, -90, 90, nativeCrs);
//        layerGroup.setBounds(nativeBounds);
//        defaults.getDefaultCachingGridSetIds().clear();
//        defaults.getDefaultCachingGridSetIds().add("EPSG:900913");
//        layerGroupInfoTileLayer = new GeoServerTileLayer(layerGroup, defaults, gridSetBroker);
//
//        // force building and setting the bounds to the saved representation
//        layerGroupInfoTileLayer.getGridSubsets();
//
//        XMLGridSubset savedSubset = layerGroupInfoTileLayer.getInfo().getGridSubsets().iterator()
//                .next();
//
//        BoundingBox gridSubsetExtent = savedSubset.getExtent();
//        BoundingBox expected = gridSetBroker.WORLD_EPSG3857.getOriginalExtent();
//        // don't use equals(), it uses an equality threshold we want to avoid here
//        double threshold = 1E-16;
//        assertTrue("Expected " + expected + ", got " + gridSubsetExtent,
//                expected.equals(gridSubsetExtent, threshold));
//    }
//
//    @Test
//    @SuppressWarnings({ "unchecked", "rawtypes" })
//    public void testGetFeatureInfo() throws Exception {
//
//        layerInfoTileLayer = new GeoServerTileLayer(layerInfo, defaults, gridSetBroker);
//
//        ConveyorTile convTile = new ConveyorTile(null, null, null, null);
//        convTile.setTileLayer(layerInfoTileLayer);
//        convTile.setMimeType(MimeType.createFromFormat("image/png"));
//        convTile.setGridSetId("EPSG:4326");
//        convTile.servletReq = new MockHttpServletRequest();
//        BoundingBox bbox = new BoundingBox(0, 0, 10, 10);
//
//        Resource mockResult = mock(Resource.class);
//        ArgumentCaptor<Map> argument = ArgumentCaptor.forClass(Map.class);
//        Mockito.when(mockGWC.dispatchOwsRequest(argument.capture(), (Cookie[]) anyObject()))
//                .thenReturn(mockResult);
//
//        Resource result = layerInfoTileLayer.getFeatureInfo(convTile, bbox, 100, 100, 50, 50);
//        assertSame(mockResult, result);
//
//        final Map<String, String> capturedParams = argument.getValue();
//
//        assertEquals("image/png", capturedParams.get("INFO_FORMAT"));
//        assertEquals("0.0,0.0,10.0,10.0", capturedParams.get("BBOX"));
//        assertEquals("test:MockLayerInfoName", capturedParams.get("QUERY_LAYERS"));
//        assertEquals("WMS", capturedParams.get("SERVICE"));
//        assertEquals("100", capturedParams.get("HEIGHT"));
//        assertEquals("100", capturedParams.get("WIDTH"));
//        assertEquals("GetFeatureInfo", capturedParams.get("REQUEST"));
//        assertEquals("default_style", capturedParams.get("STYLES"));
//        assertEquals("SE_XML", capturedParams.get("EXCEPTIONS"));
//        assertEquals("1.1.1", capturedParams.get("VERSION"));
//        assertEquals("image/png", capturedParams.get("FORMAT"));
//        assertEquals("test:MockLayerInfoName", capturedParams.get("LAYERS"));
//        assertEquals("EPSG:4326", capturedParams.get("SRS"));
//        assertEquals("50", capturedParams.get("X"));
//        assertEquals("50", capturedParams.get("Y"));
//
//        verify(mockGWC, times(1)).dispatchOwsRequest((Map) anyObject(), (Cookie[]) anyObject());
//
//        when(mockGWC.dispatchOwsRequest((Map) anyObject(), (Cookie[]) anyObject())).thenThrow(
//                new RuntimeException("mock exception"));
//        try {
//            layerInfoTileLayer.getFeatureInfo(convTile, bbox, 100, 100, 50, 50);
//            fail("Expected GeoWebCacheException");
//        } catch (GeoWebCacheException e) {
//            assertTrue(true);
//        }
//    }
//
//
//    @Test
//    @SuppressWarnings({ "unchecked", "rawtypes" })
//    public void testGetTile() throws Exception {
//
//        Resource mockResult = mock(Resource.class);
//        ArgumentCaptor<Map> argument = ArgumentCaptor.forClass(Map.class);
//        Mockito.when(mockGWC.dispatchOwsRequest(argument.capture(), (Cookie[]) anyObject()))
//                .thenReturn(mockResult);
//
//        BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
//        RenderedImageMap fakeDispatchedMap = new RenderedImageMap(new WMSMapContent(), image,
//                "image/png");
//
//        RenderedImageMapResponse fakeResponseEncoder = mock(RenderedImageMapResponse.class);
//        MimeType mimeType = MimeType.createFromFormat("image/png");
//        when(mockGWC.getResponseEncoder(eq(mimeType), (RenderedImageMap) anyObject())).thenReturn(
//                fakeResponseEncoder);
//
//        StorageBroker storageBroker = mock(StorageBroker.class);
//        when(storageBroker.get((TileObject) anyObject())).thenReturn(false);
//
//        layerInfoTileLayer = new GeoServerTileLayer(layerInfo, defaults, gridSetBroker);
//
//        MockHttpServletRequest servletReq = new MockHttpServletRequest();
//        HttpServletResponse servletResp = new MockHttpServletResponse();
//        long[] tileIndex = { 0, 0, 0 };
//
//        ConveyorTile tile = new ConveyorTile(storageBroker, layerInfoTileLayer.getName(),
//                "EPSG:4326", tileIndex, mimeType, null, servletReq, servletResp);
//
//        GeoServerTileLayer.WEB_MAP.set(fakeDispatchedMap);
//        ConveyorTile returned = layerInfoTileLayer.getTile(tile);
//        assertNotNull(returned);
//        assertNotNull(returned.getBlob());
//        assertEquals(CacheResult.MISS, returned.getCacheResult());
//        assertEquals(200, returned.getStatus());
//
//        verify(storageBroker, atLeastOnce()).get((TileObject) anyObject());
//        verify(mockGWC, times(1)).getResponseEncoder(eq(mimeType), isA(RenderedImageMap.class));
//    }
//
//    @Test
//    public void testGetMimeTypes() throws Exception {
//
//        layerInfoTileLayer = new GeoServerTileLayer(layerInfo, defaults, gridSetBroker);
//        List<MimeType> mimeTypes = layerInfoTileLayer.getMimeTypes();
//        assertEquals(defaults.getDefaultOtherCacheFormats().size(), mimeTypes.size());
//
//        layerInfoTileLayer.getInfo().getMimeFormats().clear();
//        layerInfoTileLayer.getInfo().getMimeFormats().add("image/gif");
//
//        mimeTypes = layerInfoTileLayer.getMimeTypes();
//        assertEquals(1, mimeTypes.size());
//        assertEquals(MimeType.createFromFormat("image/gif"), mimeTypes.get(0));
//    }
    

}
