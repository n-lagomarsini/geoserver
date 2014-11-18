package org.geoserver.web.wicket;

import static org.geoserver.catalog.Predicates.sortBy;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.extensions.ajax.markup.html.autocomplete.AbstractAutoCompleteTextRenderer;
import org.apache.wicket.extensions.ajax.markup.html.autocomplete.AutoCompleteTextField;
import org.apache.wicket.extensions.ajax.markup.html.modal.ModalWindow;
import org.apache.wicket.extensions.markup.html.repeater.util.SortParam;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.FormComponentPanel;
import org.apache.wicket.model.IModel;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogInfo;
import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.catalog.Predicates;
import org.geoserver.catalog.util.CloseableIterator;
import org.geoserver.catalog.util.CloseableIteratorAdapter;
import org.geoserver.web.GeoServerApplication;
import org.geoserver.web.wicket.GeoServerDataProvider.Property;
import org.geotools.factory.CommonFactoryFinder;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.expression.PropertyName;
import org.opengis.filter.sort.SortBy;

import com.google.common.collect.Lists;

public class AutoCompletePanel<T extends CatalogInfo> extends FormComponentPanel<T> {

    public static final int TOTAL_NUMBER = 21;

    private static final FilterFactory2 FF = CommonFactoryFinder.getFilterFactory2();

    public static final String DOTS = "...";

    /** serialVersionUID */
    private static final long serialVersionUID = 1L;

    // private GeoServerDataProvider<T> provider;
    private Property<T> propertySelector;

    private Class<T> class1;

    public AutoCompletePanel(String id, IModel<T> model, Property<T> propertySelector, List<Property<T>> visibleProperties,Class<T> class1) {
        super(id, model);
        // this.provider = provider;
        this.propertySelector = propertySelector;
        this.class1 = class1;
        initComponents(model, visibleProperties);
    }

    private void initComponents(final IModel<T> model, final List<Property<T>> visibleProperties) {
        
        AbstractAutoCompleteTextRenderer<T> renderer = new AbstractAutoCompleteTextRenderer<T>() {

            /** serialVersionUID */
            private static final long serialVersionUID = 1L;

            @Override
            protected String getTextValue(T object) {
                PropertyName property = FF.property(propertySelector.getName());
                Object evaluate = property.evaluate(object);
                return evaluate.toString();
            }
        };

        final AutoCompleteTextField<T> field = new AutoCompleteTextField<T>("field", model, renderer) {

            /** serialVersionUID */
            private static final long serialVersionUID = 1L;

            @Override
            protected Iterator<T> getChoices(String input) {

                if (input == null || input.isEmpty()) {
                    List<T> emptyList = Collections.emptyList();
                    return emptyList.iterator();
                }

                final FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
                final PropertyName property = ff.property(propertySelector.getName());
                Filter filter = ff.like(property, "%" + input + "%");
                filter = ff.and(filter, Filter.INCLUDE);

                SortBy sortOrder = Predicates.sortBy(propertySelector.getName(), true);

                final Catalog catalog = GeoServerApplication.get().getCatalog();
                Iterator<T> iterator = catalog.list(class1, filter, 0, TOTAL_NUMBER, sortOrder);

                // Getting the Layers from the
                // TODO Auto-generated method stub
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
        };

        add(field);
        
        final ModalWindow popupWindow = new ModalWindow("popup");
        add(popupWindow);
        add(new AjaxLink("add") {
            @Override
            public void onClick(AjaxRequestTarget target) {
                popupWindow.setInitialHeight(375);
                popupWindow.setInitialWidth(525);
                popupWindow.setTitle(new ParamResourceModel("chooseElement", this));
                popupWindow.setContent(new ListPanel(popupWindow.getContentId(), visibleProperties, class1, propertySelector) {

                    @Override
                    protected void handleInput(Object input, AjaxRequestTarget target) {
                        popupWindow.close(target);
                        field.getModel().setObject((T) input);
                        target.addComponent( field );
                    }
                });

                popupWindow.show(target);
            }
        });
    }
    
    static class ListPanel<T extends CatalogInfo> extends GeoServerTablePanel{

        /** serialVersionUID */
        private static final long serialVersionUID = 1L;
        
        private Property initialProp;

        public ListPanel(String id, List<Property> props, Class clazz, Property initialProp) {
            super(id, new ElementProvider(props, clazz, initialProp));
            this.initialProp = initialProp;
        }

        @Override
        protected Component getComponentForProperty(String id, final IModel itemModel,
                Property property) {
            IModel model = property.getModel( itemModel );
            if ( initialProp == property ) {
                return new SimpleAjaxLink( id, model ) {
                    @Override
                    protected void onClick(AjaxRequestTarget target) {
                        handleInput( itemModel.getObject(), target );
                        
                    }
                };
            }
            else {
                return new Label( id, model );
            }
        }
        
        protected void handleInput(Object input, AjaxRequestTarget target){
            
        }
    }
    
    static class ElementProvider extends GeoServerDataProvider{
        
        public static final Property<CatalogInfo> NAME = new BeanProperty<CatalogInfo>("name",
        "name");
        
        private List<Property> properties;
        private Class clazz;

        

        public ElementProvider(List<Property> properties, Class clazz, Property initialProp){
            this.properties = extractProperties(properties, initialProp);
            this.clazz = clazz;
        }
        
        @Override
        public int size() {
            Filter filter = getFilter();
            int count = getCatalog().count(LayerInfo.class, filter);
            return count;
        }

        @Override
        public int fullSize() {
            Filter filter = Predicates.acceptAll();
            int count = getCatalog().count(LayerInfo.class, filter);
            return count;
        }
        
        @Override
        public Iterator iterator(final int first, final int count) {
            Iterator iterator = filteredItems(first, count);
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
        private Iterator filteredItems(Integer first, Integer count) {
            final Catalog catalog = getCatalog();

            // global sorting

            SortParam sort = getSort();
            final Property property = getProperty(sort );

            SortBy sortOrder = null;
            if (sort != null) {
                if(property instanceof BeanProperty){
                    final String sortProperty = ((BeanProperty)property).getPropertyPath();
                    sortOrder = sortBy(sortProperty, sort.isAscending());
                }
            }

            final Filter filter = getFilter();
            //our already filtered and closeable iterator
            Iterator items = catalog.list(clazz, filter, first, count, sortOrder);

            return items;
        }

        @Override
        protected List getItems() {
            return null;
        }

        @Override
        protected List<Property> getProperties() {
            return properties;
        }
        
        private static List<Property> extractProperties(List<Property> properties, Property initialProp){
            List<Property> props = new ArrayList<Property>(properties);
            if(!props.contains(initialProp)){
                props.add(initialProp);
            }
            return props;
        }
    }
}
