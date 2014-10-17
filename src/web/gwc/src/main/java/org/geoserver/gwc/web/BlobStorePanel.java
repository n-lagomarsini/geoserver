/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.gwc.web;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.validation.IValidatable;
import org.apache.wicket.validation.IValidator;
import org.apache.wicket.validation.ValidationError;
import org.apache.wicket.validation.validator.AbstractValidator;
import org.geoserver.gwc.ConfigurableBlobStore;
import org.geoserver.gwc.config.GWCConfig;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.web.util.MapModel;
import org.geoserver.web.wicket.ParamResourceModel;
import org.geowebcache.storage.blobstore.memory.CacheConfiguration;
import org.geowebcache.storage.blobstore.memory.CacheConfiguration.EvictionPolicy;
import org.geowebcache.storage.blobstore.memory.CacheProvider;
import org.geowebcache.storage.blobstore.memory.CacheStatistics;

/**
 * This class is a new Panel for configuring In Memory Caching for GWC. The user can enable/disable In Memory caching, enable/disable file
 * persistence. Also fromt this panel the user can have information about cache statistics and also change the cache configuration.
 * 
 * @author Nicola Lagomarsini Geosolutions
 */
public class BlobStorePanel extends Panel {

    /** Key for the miss rate */
    public static final String KEY_MISS_RATE = "missRate";

    /** Key for the hit rate */
    public static final String KEY_HIT_RATE = "hitRate";

    /** Key for the evicted elements number */
    public static final String KEY_EVICTED = "evicted";

    /** Key for the miss count */
    public static final String KEY_MISS_COUNT = "missCount";

    /** Key for the hit count */
    public static final String KEY_HIT_COUNT = "hitCount";

    /** Key for the total elements count */
    public static final String KEY_TOTAL_COUNT = "totalCount";

    /** Key for the cache current memory occupation */
    public static final String KEY_CURRENT_MEM = "currentMemory";
    
    /** Key for the cache current/total size */
    public static final String KEY_SIZE = "cacheSize";

    /** HashMap containing the values for all the statistics values */
    private HashMap<String, String> values;

    public BlobStorePanel(String id, final IModel<GWCConfig> gwcConfigModel) {

        super(id, gwcConfigModel);
        // Initialize the map
        values = new HashMap<String, String>();

        // get the CacheConfiguration Model
        IModel<CacheConfiguration> cacheConfiguration = new PropertyModel<CacheConfiguration>(
                gwcConfigModel, "cacheConfiguration");

        // Creation of the Checbox for enabling disabling inmemory caching
        IModel<Boolean> innerCachingEnabled = new PropertyModel<Boolean>(gwcConfigModel,
                "innerCachingEnabled");
        final CheckBox innerCachingEnabledChoice = new CheckBox("innerCachingEnabled",
                innerCachingEnabled);

        // Container containing all the other parameters which can be seen only if In Memory caching is enabled
        final WebMarkupContainer container = new WebMarkupContainer("container");
        container.setOutputMarkupId(true).setEnabled(true);
        
        // Container containing all the parameters related to cache configuration
        final WebMarkupContainer cacheConfigContainer = new WebMarkupContainer("cacheConfContainer");
        cacheConfigContainer.setOutputMarkupId(true).setEnabled(true);

        // Avoid Persistence checkbox
        IModel<Boolean> avoidPersistence = new PropertyModel<Boolean>(gwcConfigModel,
                "avoidPersistence");
        final CheckBox avoidPersistenceChoice = new CheckBox("avoidPersistence", avoidPersistence);
        boolean visible = innerCachingEnabledChoice.getModelObject() == null ? false
                : innerCachingEnabledChoice.getModelObject();
        container.setVisible(visible);
        avoidPersistenceChoice.setOutputMarkupId(true).setEnabled(true);

        // Cache configuration parameters
        IModel<Long> hardMemoryLimit = new PropertyModel<Long>(cacheConfiguration,
                "hardMemoryLimit");

        IModel<Long> evictionTimeValue = new PropertyModel<Long>(cacheConfiguration, "evictionTime");

        IModel<EvictionPolicy> policy = new PropertyModel<EvictionPolicy>(cacheConfiguration,
                "policy");

        IModel<Integer> concurrencyLevel = new PropertyModel<Integer>(cacheConfiguration,
                "concurrencyLevel");

        final TextField<Long> hardMemory = new TextField<Long>("hardMemoryLimit", hardMemoryLimit);
        hardMemory.setType(Long.class).setOutputMarkupId(true).setEnabled(true);
        hardMemory.add(new MinimumLongValidator("BlobStorePanel.invalidHardMemory"));

        final TextField<Long> evictionTime = new TextField<Long>("evictionTime", evictionTimeValue);
        evictionTime.setType(Long.class).setOutputMarkupId(true).setEnabled(true);

        final DropDownChoice<EvictionPolicy> policyDropDown = new DropDownChoice<EvictionPolicy>(
                "policy", policy, Arrays.asList(EvictionPolicy.values()));
        policyDropDown.setOutputMarkupId(true).setEnabled(true);
        policyDropDown.add(new IValidator<EvictionPolicy>() {
            
            @Override
            public void validate(IValidatable<EvictionPolicy> validatable) {
                EvictionPolicy value = validatable.getValue();
                if(value != EvictionPolicy.NULL){
                    // Ensure that the defined Eviction policy can be accepted by the cache used
                    ConfigurableBlobStore store = GeoServerExtensions.bean(ConfigurableBlobStore.class);
                    if(store != null){
                        CacheProvider cache = store.getCache();
                        if (cache != null) {
                            List<EvictionPolicy> policies = cache.getSupportedPolicies();
                            if(!policies.contains(value)){
                                ValidationError error = new ValidationError();
                                error.setMessage(new ParamResourceModel("BlobStorePanel.invalidPolicy", null,
                                        "").getObject());
                                validatable.error(error);
                            }
                        }
                    }
                }
            }
        });

        final TextField<Integer> textConcurrency = new TextField<Integer>("concurrencyLevel",
                concurrencyLevel);
        textConcurrency.setType(Integer.class).setOutputMarkupId(true).setEnabled(true);
        textConcurrency.add(new MinimumConcurrencyValidator());

        // Add all the parameters to the containes
        cacheConfigContainer.add(hardMemory);
        cacheConfigContainer.add(policyDropDown);
        cacheConfigContainer.add(textConcurrency);
        cacheConfigContainer.add(evictionTime);
        
        container.add(cacheConfigContainer);

        innerCachingEnabledChoice.add(new AjaxFormComponentUpdatingBehavior("onChange") {

            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                // If In Memory caching is disabled, all the other parameters cannot be seen
                boolean isVisible = innerCachingEnabledChoice.getModelObject() == null ? false
                        : innerCachingEnabledChoice.getModelObject();
                container.setVisible(isVisible);
                target.addComponent(container.getParent());
            }
        });

        add(innerCachingEnabledChoice);
        container.add(avoidPersistenceChoice);
        add(container);

        // Cache Clearing Option
        Button clearCache = new Button("cacheClear") {
            @Override
            public void onSubmit() {
                // Clear cache
                ConfigurableBlobStore store = GeoServerExtensions.bean(ConfigurableBlobStore.class);
                if (store != null) {
                    store.clearCache();
                }
            }
        };
        container.add(clearCache);

        // Cache Statistics
        final WebMarkupContainer statsContainer = new WebMarkupContainer("statsContainer");
        statsContainer.setOutputMarkupId(true);

        // Container for the statistics
        statsContainer.add(new Label("title"));
        statsContainer.add(new Label("totalCount", new MapModel(values, KEY_TOTAL_COUNT)));
        statsContainer.add(new Label("hitCount", new MapModel(values, KEY_HIT_COUNT)));
        statsContainer.add(new Label("missCount", new MapModel(values, KEY_MISS_COUNT)));
        statsContainer.add(new Label("missRate", new MapModel(values, KEY_MISS_RATE)));
        statsContainer.add(new Label("hitRate", new MapModel(values, KEY_HIT_RATE)));
        statsContainer.add(new Label("evicted", new MapModel(values, KEY_EVICTED)));
        statsContainer.add(new Label("currentMemory", new MapModel(values, KEY_CURRENT_MEM)));
        statsContainer.add(new Label("cacheSize", new MapModel(values, KEY_SIZE)));

        AjaxButton statistics = new AjaxButton("statistics") {

            @Override
            protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
                try {
                    // If checked, all the statistics are reported
                    ConfigurableBlobStore store = GeoServerExtensions
                            .bean(ConfigurableBlobStore.class);
                    if (store != null) {
                        CacheStatistics stats = store.getCacheStatistics();

                        long hitCount = stats.getHitCount();
                        long missCount = stats.getMissCount();
                        long total = stats.getRequestCount();
                        double hitRate = stats.getHitRate();
                        double missRate = stats.getMissRate();
                        long evicted = stats.getEvictionCount();
                        double currentMem = stats.getCurrentMemoryOccupation();
                        long byteToMb = 1024 * 1024;
                        double actualSize = ((long) (100 * (stats.getActualSize() * 1.0d) / byteToMb)) / 100d;
                        double totalSize = ((long) (100 * (stats.getTotalSize() * 1.0d) / byteToMb)) / 100d;

                        values.put(KEY_MISS_RATE, missRate + " %");
                        values.put(KEY_HIT_RATE, hitRate + " %");
                        values.put(KEY_EVICTED, evicted + "");
                        values.put(KEY_TOTAL_COUNT, total + "");
                        values.put(KEY_MISS_COUNT, missCount + "");
                        values.put(KEY_HIT_COUNT, hitCount + "");
                        values.put(KEY_CURRENT_MEM, currentMem + " %");
                        values.put(KEY_SIZE, actualSize + " / " + totalSize + " Mb");
                    }
                } catch (Throwable t) {
                    error(t);
                }
                target.addComponent(statsContainer);
            }
        };

        container.add(statsContainer);
        container.add(statistics);
    }

    /**
     * {@link AbstractValidator} implementation for checking if the value is null or minor than 0
     * 
     * @author Nicola Lagomarsini Geosolutions
     */
    static class MinimumLongValidator extends AbstractValidator<Long> {

        private String errorKey;

        public MinimumLongValidator(String error) {
            this.errorKey = error;
        }

        @Override
        public boolean validateOnNullValue() {
            return true;
        }

        @Override
        protected void onValidate(IValidatable<Long> validatable) {
            if (validatable == null || validatable.getValue() <= 0) {
                ValidationError error = new ValidationError();
                error.setMessage(new ParamResourceModel(errorKey, null, "").getObject());
                validatable.error(error);
            }
        }
    }

    /**
     * {@link AbstractValidator} implementation for checking if the concurrency Level is null or minor than 0
     * 
     * @author Nicola Lagomarsini Geosolutions
     */
    static class MinimumConcurrencyValidator extends AbstractValidator<Integer> {

        @Override
        public boolean validateOnNullValue() {
            return true;
        }

        @Override
        protected void onValidate(IValidatable<Integer> validatable) {
            if (validatable == null || validatable.getValue() <= 0) {
                ValidationError error = new ValidationError();
                error.setMessage(new ParamResourceModel("BlobStorePanel.invalidConcurrency", null,
                        "").getObject());
                validatable.error(error);
            }
        }
    }
}
