/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.gwc.web.layer;

import static org.geoserver.catalog.Predicates.acceptAll;
import static org.geoserver.catalog.Predicates.or;
import static org.geoserver.catalog.Predicates.sortBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.wicket.ResourceReference;
import org.apache.wicket.extensions.markup.html.repeater.util.SortParam;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.Predicates;
import org.geoserver.catalog.util.CloseableIterator;
import org.geoserver.catalog.util.CloseableIteratorAdapter;
import org.geoserver.gwc.GWC;
import org.geoserver.gwc.config.GWCConfig;
import org.geoserver.gwc.layer.GeoServerTileLayer;
import org.geoserver.gwc.web.GWCIconFactory;
import org.geoserver.web.wicket.GeoServerDataProvider;
import org.geotools.factory.CommonFactoryFinder;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.layer.TileLayer;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.sort.SortBy;
import org.springframework.util.CompositeIterator;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;

/**
 * @author groldan
 */
class UnconfiguredCachedLayersProvider extends GeoServerDataProvider<TileLayer> {
    
    public static final FilterFactory2 FF = CommonFactoryFinder.getFilterFactory2();

    private static final long serialVersionUID = -8599398086587516574L;

    static final Property<TileLayer> TYPE = new AbstractProperty<TileLayer>("type") {

        private static final long serialVersionUID = 3215255763580377079L;

        @Override
        public ResourceReference getPropertyValue(TileLayer item) {
            return GWCIconFactory.getSpecificLayerIcon(item);
        }

        @Override
        public Comparator<TileLayer> getComparator() {
            return new Comparator<TileLayer>() {
                @Override
                public int compare(TileLayer o1, TileLayer o2) {
                    ResourceReference r1 = getPropertyValue(o1);
                    ResourceReference r2 = getPropertyValue(o2);
                    return r1.getName().compareTo(r2.getName());
                }
            };
        }
    };

    static final Property<TileLayer> NAME = new BeanProperty<TileLayer>("name", "name");

    static final Property<TileLayer> ENABLED = new BeanProperty<TileLayer>("enabled", "enabled");

    @SuppressWarnings("unchecked")
    static final List<Property<TileLayer>> PROPERTIES = Collections.unmodifiableList(Arrays.asList(
            TYPE, NAME, ENABLED));

    /**
     * Provides a list of transient TileLayers for the LayerInfo and LayerGroupInfo objects in
     * Catalog that don't already have a configured TileLayer on their metadata map.
     * 
     * @see org.geoserver.web.wicket.GeoServerDataProvider#getItems()
     */
    @Override
    protected List<TileLayer> getItems() {
//        final GWC gwc = GWC.get();
//        final GWCConfig defaults = gwc.getConfig().saneConfig().clone();
//        final GridSetBroker gridsets = gwc.getGridSetBroker();
//        final Catalog catalog = getCatalog();
//
//        defaults.setCacheLayersByDefault(true);
//
//        List<String> unconfiguredLayerIds = getUnconfiguredLayers();
//
//        List<TileLayer> layers = Lists.transform(unconfiguredLayerIds,
//                new Function<String, TileLayer>() {
//                    @Override
//                    public TileLayer apply(String input) {
//                        GeoServerTileLayer geoServerTileLayer;
//
//                        LayerInfo layer = catalog.getLayer(input);
//                        if (layer != null) {
//                            geoServerTileLayer = new GeoServerTileLayer(layer, defaults, gridsets);
//                        } else {
//                            LayerGroupInfo layerGroup = catalog.getLayerGroup(input);
//                            geoServerTileLayer = new GeoServerTileLayer(layerGroup, defaults,
//                                    gridsets);
//                        }
//                        /*
//                         * Set it to enabled regardless of the default settins, so it only shows up
//                         * as disabled if the actual layer/groupinfo is disabled
//                         */
//                        geoServerTileLayer.getInfo().setEnabled(true);
//                        return geoServerTileLayer;
//                    }
//                });
//
//        return layers;
        throw new UnsupportedOperationException(
                "This method should not be being called! "
                        + "We use the catalog streaming API");
    }

    private List<String> getUnconfiguredLayers() {
        Catalog catalog = getCatalog();
        List<String> layerIds = new LinkedList<String>();

        GWC gwc = GWC.get();

        List<LayerInfo> layers = catalog.getLayers();
        for (LayerInfo l : layers) {
            if (!gwc.hasTileLayer(l)) {
                layerIds.add(l.getId());
            }
        }

        List<LayerGroupInfo> layerGroups = catalog.getLayerGroups();
        for (LayerGroupInfo lg : layerGroups) {
            if (!gwc.hasTileLayer(lg)) {
                layerIds.add(lg.getId());
            }
        }
        return layerIds;
    }

    /**
     * @see org.geoserver.web.wicket.GeoServerDataProvider#getProperties()
     */
    @Override
    protected List<Property<TileLayer>> getProperties() {
        return PROPERTIES;
    }

    /**
     * @see org.geoserver.web.wicket.GeoServerDataProvider#newModel(java.lang.Object)
     */
    public IModel<TileLayer> newModel(final Object tileLayer) {
        return new UnconfiguredTileLayerDetachableModel(((TileLayer) tileLayer).getName());
    }

    /**
     * @see org.geoserver.web.wicket.GeoServerDataProvider#getComparator
     */
    @Override
    protected Comparator<TileLayer> getComparator(SortParam sort) {
        return super.getComparator(sort);
    }
    
    @Override
    public int size() {
        Filter filter = getFilter();
        int countLayers = getLayerSize(filter);
        int countLayerGroup = getLayerGroupSize(filter);
        return countLayers + countLayerGroup;
    }

    @Override
    public int fullSize() {
        Filter filter = Predicates.acceptAll();
        int countLayers = getLayerSize(filter);
        int countLayerGroup = getLayerGroupSize(filter);
        return countLayers + countLayerGroup;
    }
    
    @Override
    public Iterator<TileLayer> iterator(final int first, final int count) {
        Iterator<TileLayer> iterator = filteredItems(first, count);
        if (iterator instanceof CloseableIterator) {
            // don't know how to force wicket to close the iterator, lets return
            // a copy. Shouldn't be much overhead as we're paging
            try {
                return Lists.newArrayList(iterator).iterator();
            } finally {
                CloseableIteratorAdapter.close(iterator);
            }
        } else {
            return iterator;
        }
    }

    /**
     * Returns the requested page of layer objects after applying any keyword
     * filtering set on the page
     */
    private Iterator<TileLayer> filteredItems(Integer first, Integer count) {
        final Catalog catalog = getCatalog();

        // global sorting
        final SortParam sort = getSort();
        final Property<TileLayer> property = getProperty(sort);

        SortBy sortOrder = null;
        if (sort != null) {
            if(property instanceof BeanProperty){
                final String sortProperty = ((BeanProperty<TileLayer>)property).getPropertyPath();
                sortOrder = sortBy(sortProperty, sort.isAscending());
            }
        }

        // Getting total size

        final Filter filter = getFilter();
        //int countLayers = getLayerSize(filter);
        //int countLayerGroup = getLayerGroupSize(filter);
        //int size = countLayers + countLayerGroup;
        //int last = first + count;
        
        Filter filterL = getLayerFilter(filter);
        
        //our already filtered and closeable iterator
        Iterator<LayerInfo> itemLayers = null;
        //our already filtered and closeable iterator
        Iterator<LayerGroupInfo> itemLayerGroups = null;

        
        itemLayers = catalog.list(LayerInfo.class, filterL, first, count, sortOrder);
        
        int layerSize = 0;
        
        while(itemLayers.hasNext()){
            itemLayers.next();
            layerSize++;
        }
        
        try{
            if(itemLayers instanceof CloseableIterator){
                ((CloseableIterator)(itemLayers)).close();
            }
            itemLayers = null;
        }catch (Exception e){
            throw new RuntimeException(e);
        }
        
        if(layerSize == count){
            itemLayers = catalog.list(LayerInfo.class, filterL, first, count, sortOrder);
        } else {
            int countLayers = getLayerSize(filter);
            
            if(countLayers > 0){
                
                if(first < countLayers){
                    // Layers
                    int lastLayerCount = countLayers - first;
                    itemLayers = catalog.list(LayerInfo.class, filterL, first, lastLayerCount,
                            sortOrder);
                    // LayerGroups
                    int lastLayerGroupCount = count - lastLayerCount;
                    itemLayerGroups = catalog.list(LayerGroupInfo.class, filterL, 0,
                            lastLayerGroupCount, sortOrder);
                } else {
                    // Only Layergroups
                    int firstLG = first - countLayers;
                    itemLayerGroups = catalog.list(LayerGroupInfo.class, filterL, firstLG, count,
                            sortOrder);
                }
            }else {
                itemLayerGroups = catalog.list(LayerGroupInfo.class, filterL, first, count, sortOrder);
            }
        }
        
        
//        if(count > size){
//            // All the PreviewLayers can be put in a single iterator
//            itemLayers = catalog.list(LayerInfo.class, filterL, null, null, sortOrder);
//            itemLayerGroups = catalog.list(LayerGroupInfo.class, filterLG, null, null, sortOrder);
//        } else {
//            // It must be paged
//            if (countLayers > 0) {
//                if (last < countLayers) {
//                    // Only Layers
//                    itemLayers = catalog.list(LayerInfo.class, filterL, first, count, sortOrder);
//                } else if (first < countLayers) {
//                    // Layers
//                    int lastLayerCount = countLayers - first;
//                    itemLayers = catalog.list(LayerInfo.class, filterL, first, lastLayerCount,
//                            sortOrder);
//
//                    if (countLayerGroup > 0) {
//                        // LayerGroups
//                        int lastLayerGroupCount = count - lastLayerCount;
//                        itemLayerGroups = catalog.list(LayerGroupInfo.class, filterLG, 0,
//                                lastLayerGroupCount, sortOrder);
//
//                    }
//                } else {
//                    // Only Layergroups
//                    int firstLG = first - countLayers;
//                    itemLayerGroups = catalog.list(LayerGroupInfo.class, filterLG, firstLG, count,
//                            sortOrder);
//                }
//            } else if(countLayerGroup > 0){
//                itemLayerGroups = catalog.list(LayerGroupInfo.class, filterLG, first, count, sortOrder);
//            }
//        }

        // Creation of ad hoc iterators
        CloseableComposedIterator composite = new CloseableComposedIterator();
        if(itemLayers != null){
            composite.add(itemLayers);
        }

        if(itemLayerGroups != null){
            composite.add(itemLayerGroups);
        }

        return composite;
    }

    private Filter getFilter() {
        final String[] keywords = getKeywords();
        Filter filter = acceptAll();
        if (null != keywords) {
            for (String keyword : keywords) {
                Filter propContains = Predicates.fullTextSearch(keyword);
                // chain the filters together
                if (Filter.INCLUDE == filter) {
                    filter = propContains;
                } else {
                    filter = or(filter, propContains);
                }
            }
        }
        return filter;
    }
    
    private Filter getLayerFilter(Filter start) {
        // Creation of a new Filter for the Layers
        Filter f = FF.equals(FF.function("tileLayer"), FF.literal(true));
        return FF.and(Arrays.asList(start, f));
    }
    
    private int getLayerSize(final Filter filter){

      List<LayerInfo> layers = getCatalog().getLayers();
      Predicate<LayerInfo> predicate = new Predicate<LayerInfo>() {

          @Override
          public boolean apply(LayerInfo input) {
              boolean evaluate = filter.evaluate(input);
              if(evaluate){
                  return !GWC.get().hasTileLayer(input);
              }
              return false;
          }
      };
      Collection<LayerInfo> layersFiltered = Collections2.filter(layers, predicate);
      return layersFiltered.size();
  }
  
  private int getLayerGroupSize(final Filter filter) {
      // Filter filterLG = getLayerGroupFilter(filter);
      // return getCatalog().count(LayerGroupInfo.class, filterLG);
      List<LayerGroupInfo> layers = getCatalog().getLayerGroups();
      Predicate<LayerGroupInfo> predicate = new Predicate<LayerGroupInfo>() {

          @Override
          public boolean apply(LayerGroupInfo input) {
              boolean evaluate = filter.evaluate(input);
              if(evaluate){
                  return !GWC.get().hasTileLayer(input);
              }
              return false;
          }
      };
      Collection<LayerGroupInfo> layersFiltered = Collections2.filter(layers, predicate);
      return layersFiltered.size();
  }

    private class UnconfiguredTileLayerDetachableModel extends LoadableDetachableModel<TileLayer> {

        private static final long serialVersionUID = -8920290470035166218L;

        private String name;

        public UnconfiguredTileLayerDetachableModel(String layerOrGroupName) {
            this.name = layerOrGroupName;
        }

        @Override
        protected TileLayer load() {
            final GWC gwc = GWC.get();
            final GWCConfig defaults = gwc.getConfig().saneConfig().clone();
            defaults.setCacheLayersByDefault(true);
            final GridSetBroker gridsets = gwc.getGridSetBroker();
            Catalog catalog = getCatalog();

            LayerInfo layer = catalog.getLayerByName(name);
            if (layer != null) {
                return new GeoServerTileLayer(layer, defaults, gridsets);
            }
            LayerGroupInfo layerGroup = catalog.getLayerGroupByName(name);
            return new GeoServerTileLayer(layerGroup, defaults, gridsets);
        }
    }
    
    static class CloseableComposedIterator extends CompositeIterator implements CloseableIterator{

        private List<Iterator> iterators;
        
        public CloseableComposedIterator(){
            this.iterators = new ArrayList<Iterator>();
        }
        
        @Override
        public void add(Iterator iterator) {
            super.add(iterator);
            iterators.add(iterator);
        }
        
        @Override
        public TileLayer next() {
            Object next = super.next();
            GWC gwc = GWC.get();
            GWCConfig defaults = gwc .getConfig().saneConfig().clone();
            GridSetBroker gridsets = gwc.getGridSetBroker();
            GeoServerTileLayer geoServerTileLayer;
            if(next instanceof LayerInfo){
                geoServerTileLayer = new GeoServerTileLayer(((LayerInfo)next), defaults, gridsets);
            } else if(next instanceof LayerGroupInfo){
                geoServerTileLayer = new GeoServerTileLayer(((LayerGroupInfo)next), defaults,
                        gridsets);
            } else {
                throw new IllegalArgumentException("Input argument is not a LayerInfo or LayerGroupInfo instance");
            }
            /*
             * Set it to enabled regardless of the default settins, so it only shows up
             * as disabled if the actual layer/groupinfo is disabled
             */
            geoServerTileLayer.getInfo().setEnabled(true);
            return geoServerTileLayer;
            
        }
        
        @Override
        public void close() {
            for(Iterator it : iterators){
                if(it instanceof CloseableIterator){
                    ((CloseableIterator)it).close();
                }
            }
        }
    }
}
