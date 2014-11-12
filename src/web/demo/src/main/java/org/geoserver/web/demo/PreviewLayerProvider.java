/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.web.demo;

import static org.geoserver.catalog.Predicates.acceptAll;
import static org.geoserver.catalog.Predicates.or;
import static org.geoserver.catalog.Predicates.sortBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.apache.wicket.extensions.markup.html.repeater.util.SortParam;
import org.apache.wicket.model.IModel;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.Predicates;
import org.geoserver.catalog.util.CloseableIterator;
import org.geoserver.catalog.util.CloseableIteratorAdapter;
import org.geoserver.web.wicket.GeoServerDataProvider;
import org.geotools.factory.CommonFactoryFinder;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.sort.SortBy;
import org.springframework.util.CompositeIterator;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;

/**
 * Provides a filtered, sorted view over the catalog layers.
 * 
 * @author Andrea Aime - OpenGeo
 */
@SuppressWarnings("serial")
public class PreviewLayerProvider extends GeoServerDataProvider<PreviewLayer> {
    
    public static final FilterFactory2 FF = CommonFactoryFinder.getFilterFactory2();
    
    public static final Property<PreviewLayer> TYPE = new BeanProperty<PreviewLayer>(
            "type", "type");

    public static final AbstractProperty<PreviewLayer> NAME = new AbstractProperty<PreviewLayer>("name") {
        @Override
        public Object getPropertyValue(PreviewLayer item) {
            if (item.layerInfo != null) {
                return item.layerInfo.prefixedName();
            }
            if (item.groupInfo != null) {
                return item.groupInfo.prefixedName();
            }
            return null;
        }
    };

    public static final Property<PreviewLayer> TITLE = new BeanProperty<PreviewLayer>(
            "title", "title");
    
    public static final Property<PreviewLayer> ABSTRACT = new BeanProperty<PreviewLayer>(
            "abstract", "abstract", false);
    
    public static final Property<PreviewLayer> KEYWORDS = new BeanProperty<PreviewLayer>(
            "keywords", "keywords", false);

    public static final Property<PreviewLayer> COMMON = new PropertyPlaceholder<PreviewLayer>(
            "commonFormats");

    public static final Property<PreviewLayer> ALL = new PropertyPlaceholder<PreviewLayer>(
            "allFormats");

    public static final List<Property<PreviewLayer>> PROPERTIES = Arrays.asList(TYPE,
            NAME, TITLE, ABSTRACT, KEYWORDS, COMMON, ALL);
    
    /**
     * A custom property that uses the derived enabled() property instead of isEnabled() to account
     * for disabled resource/store
     */
    static final Property<LayerInfo> ENABLED = new AbstractProperty<LayerInfo>("enabled") {

        public Boolean getPropertyValue(LayerInfo item) {
            return Boolean.valueOf(item.enabled());
        }

    };
    
    /**
     * A custom property that uses the derived enabled() property instead of isEnabled() to account
     * for disabled resource/store
     */
    static final Property<LayerInfo> ADVERTISED = new AbstractProperty<LayerInfo>("advertised") {

        public Boolean getPropertyValue(LayerInfo item) {
            return Boolean.valueOf(item.isAdvertised());
        }

    };
    
    @Override
    protected List<PreviewLayer> getItems() {
//        List<PreviewLayer> result = new ArrayList<PreviewLayer>();
//
//        List<LayerInfo> layers = getCatalog().getLayers();
//        for (LayerInfo layer :layers ) {
//            // ask for enabled() instead of isEnabled() to account for disabled resource/store
//            if (layer.enabled() && layer.isAdvertised()) {
//                result.add(new PreviewLayer(layer));
//            }
//        }
//
//        final List<LayerGroupInfo> layerGroups = getCatalog().getLayerGroups();
//        for (LayerGroupInfo group :layerGroups ) {
//            if (!LayerGroupInfo.Mode.CONTAINER.equals(group.getMode())) {            
//                boolean enabled = true;
//                for (LayerInfo layer : group.layers()) {
//                    // ask for enabled() instead of isEnabled() to account for disabled resource/store
//                    enabled &= layer.enabled();
//                }
//                
//                if (enabled && group.layers().size() > 0)
//                    result.add(new PreviewLayer(group));
//            }
//        }
//
//        return result;
        
        // forced to implement this method as its abstract in the super class
        throw new UnsupportedOperationException(
                "This method should not be being called! "
                        + "We use the catalog streaming API");
    }

    @Override
    protected List<Property<PreviewLayer>> getProperties() {
        return PROPERTIES;
    }

    public IModel newModel(Object object) {
        return new PreviewLayerModel((PreviewLayer) object);
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
    public Iterator<PreviewLayer> iterator(final int first, final int count) {
        Iterator<PreviewLayer> iterator = filteredItems(first, count);
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
    private Iterator<PreviewLayer> filteredItems(Integer first, Integer count) {
        final Catalog catalog = getCatalog();

        // global sorting
        final SortParam sort = getSort();
        final Property<PreviewLayer> property = getProperty(sort);

        SortBy sortOrder = null;
        if (sort != null) {
            if(property instanceof BeanProperty){
                final String sortProperty = ((BeanProperty<PreviewLayer>)property).getPropertyPath();
                sortOrder = sortBy(sortProperty, sort.isAscending());
            }
        }

        // Getting total size

        final Filter filter = getFilter();
        int countLayers = getLayerSize(filter);
        int countLayerGroup = getLayerGroupSize(filter);
        int size = countLayers + countLayerGroup;
        int last = first + count;
        
        Filter filterL = getLayerFilter(filter);
        Filter filterLG = getLayerGroupFilter(filter);
        
        //our already filtered and closeable iterator
        Iterator<LayerInfo> itemLayers = null;
        //our already filtered and closeable iterator
        Iterator<LayerGroupInfo> itemLayerGroups = null;

        if(count > size){
            // All the PreviewLayers can be put in a single iterator
            itemLayers = catalog.list(LayerInfo.class, filterL, null, null, sortOrder);
            itemLayerGroups = catalog.list(LayerGroupInfo.class, filterLG, null, null, sortOrder);
        } else {
            // It must be paged
            if (countLayers > 0) {
                if (last < countLayers) {
                    // Only Layers
                    itemLayers = catalog.list(LayerInfo.class, filterL, first, count, sortOrder);
                } else if (last >= countLayers && first < countLayers) {
                    // Layers
                    int lastLayerCount = countLayers - first;
                    itemLayers = catalog.list(LayerInfo.class, filterL, first, lastLayerCount,
                            sortOrder);

                    if (countLayerGroup > 0) {
                        // LayerGroups
                        int lastLayerGroupCount = count - lastLayerCount;
                        itemLayerGroups = catalog.list(LayerGroupInfo.class, filterLG, 0,
                                lastLayerGroupCount, sortOrder);

                    }
                } else {
                    // Only Layergroups
                    int firstLG = first - countLayers;
                    itemLayerGroups = catalog.list(LayerGroupInfo.class, filterLG, firstLG, count,
                            sortOrder);
                }
            } else if(countLayerGroup > 0){
                itemLayerGroups = catalog.list(LayerGroupInfo.class, filterLG, first, count, sortOrder);
            }
        }

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
    
    private Filter getLayerGroupFilter(Filter start) {
        // Creation of a new Filter for the LayerGroups
        //Filter lg = new LayerGroupFilter();
        Filter lg = FF.equals(FF.function("layerGroupFilter"), FF.literal(true));
        return FF.and(start, lg);
    }
    
    private Filter getLayerFilter(Filter start) {
        // Creation of a new Filter for the LayerGroups
        Filter f = FF.equals(FF.function("layerFilter"), FF.literal(true));
//        Filter f = FF.equals(FF.property("isAdvertised"), FF.literal(true));
//        Filter f2 = FF.equals(FF.property("isEnabled"), FF.literal(true));
        return FF.and(Arrays.asList(start, f));
    }
    
    private int getLayerSize(final Filter filter){
//        Filter filterL = getLayerFilter(filter);
//        return getCatalog().count(LayerInfo.class, filterL);
        
        List<LayerInfo> layers = getCatalog().getLayers();
        Predicate<LayerInfo> predicate = new Predicate<LayerInfo>() {

            @Override
            public boolean apply(LayerInfo input) {
                boolean evaluate = filter.evaluate(input);
                if(evaluate){
                    return input.enabled() && input.isAdvertised();
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
                    boolean accepted = false;
                    if (!LayerGroupInfo.Mode.CONTAINER.equals(input.getMode())) {
                        boolean enabled = true;
                        for (LayerInfo layer : input.layers()) {
                            // ask for enabled() instead of isEnabled() to account for disabled resource/store
                            enabled &= layer.enabled();
                        }

                        if (enabled && input.layers().size() > 0)
                            accepted = true;
                    }
                    return accepted;
                }
                return false;
            }
        };

        Collection<LayerGroupInfo> layersFiltered = Collections2.filter(layers, predicate);
        return layersFiltered.size();
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
        public PreviewLayer next() {
            Object next = super.next();
            if(next instanceof LayerInfo){
                return new PreviewLayer((LayerInfo)next); 
            } else if(next instanceof LayerGroupInfo){
                return new PreviewLayer((LayerGroupInfo)next); 
            }
            throw new IllegalArgumentException("Input argument is not a LayerInfo or LayerGroupInfo instance");
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
