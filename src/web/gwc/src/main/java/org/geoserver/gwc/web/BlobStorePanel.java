/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.gwc.web;

import java.util.Arrays;
import java.util.HashMap;

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
import org.apache.wicket.validation.ValidationError;
import org.apache.wicket.validation.validator.AbstractValidator;
import org.geoserver.gwc.ConfigurableBlobStore;
import org.geoserver.gwc.config.GWCConfig;
import org.geoserver.platform.GeoServerExtensions;
import org.geoserver.web.util.MapModel;
import org.geoserver.web.wicket.ParamResourceModel;
import org.geowebcache.storage.blobstore.cache.CacheConfiguration;
import org.geowebcache.storage.blobstore.cache.CacheConfiguration.EvictionPolicy;
import org.geowebcache.storage.blobstore.cache.CacheStatistics;

public class BlobStorePanel extends Panel {

    public static final String KEY_MISS_RATE = "missRate";

    public static final String KEY_HIT_RATE = "hitRate";

    public static final String KEY_EVICTED = "evicted";
    
    public static final String KEY_MISS_COUNT = "missCount";

    public static final String KEY_HIT_COUNT = "hitCount";

    public static final String KEY_TOTAL_COUNT = "totalCount";

    private HashMap<String, String> values;

    public BlobStorePanel(String id, final IModel<GWCConfig> gwcConfigModel) {

        super(id, gwcConfigModel);

        values = new HashMap<String, String>();

        IModel<CacheConfiguration> cacheConfiguration = new PropertyModel<CacheConfiguration>(
                gwcConfigModel, "cacheConfiguration");

        IModel<Boolean> innerCachingEnabled = new PropertyModel<Boolean>(gwcConfigModel,
                "innerCachingEnabled");
        final CheckBox innerCachingEnabledChoice = new CheckBox("innerCachingEnabled",
                innerCachingEnabled);

        final WebMarkupContainer container = new WebMarkupContainer("container");
        container.setOutputMarkupId(true).setEnabled(true);

        IModel<Boolean> avoidPersistence = new PropertyModel<Boolean>(gwcConfigModel,
                "avoidPersistence");
        final CheckBox avoidPersistenceChoice = new CheckBox("avoidPersistence", avoidPersistence);
        boolean visible = innerCachingEnabledChoice.getModelObject() == null ? false
                : innerCachingEnabledChoice.getModelObject();
        container.setVisible(visible);
        avoidPersistenceChoice.setOutputMarkupId(true).setEnabled(true);

        IModel<Long> hardMemoryLimit = new PropertyModel<Long>(cacheConfiguration,
                "hardMemoryLimit");
        
        IModel<Long> evictionTimeValue = new PropertyModel<Long>(cacheConfiguration,
                "evictionTime");

        IModel<EvictionPolicy> policy = new PropertyModel<EvictionPolicy>(cacheConfiguration, "policy");

        IModel<Integer> concurrencyLevel = new PropertyModel<Integer>(cacheConfiguration,
                "concurrencyLevel");

        // Add text entry widget
        final TextField<Long> hardMemory = new TextField<Long>("hardMemoryLimit", hardMemoryLimit);
        hardMemory.setType(Long.class).setOutputMarkupId(true).setEnabled(true);
        hardMemory.add(new MinimumLongValidator("BlobStorePanel.invalidHardMemory"));
        
        final TextField<Long> evictionTime = new TextField<Long>("evictionTime", evictionTimeValue);
        evictionTime.setType(Long.class).setOutputMarkupId(true).setEnabled(true);

        final DropDownChoice<EvictionPolicy> policyDropDown = new DropDownChoice<>("policy", policy, Arrays.asList(EvictionPolicy.values()));
        policyDropDown.setOutputMarkupId(true).setEnabled(true);

        final TextField<Integer> textConcurrency = new TextField<Integer>("concurrencyLevel",
                concurrencyLevel);
        textConcurrency.setType(Integer.class).setOutputMarkupId(true).setEnabled(true);
        textConcurrency.add(new MinimumConcurrencyValidator());

        container.add(hardMemory);
        container.add(policyDropDown);
        container.add(textConcurrency);
        container.add(evictionTime);

        innerCachingEnabledChoice.add(new AjaxFormComponentUpdatingBehavior("onChange") {

            @Override
            protected void onUpdate(AjaxRequestTarget target) {
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
                ConfigurableBlobStore store = GeoServerExtensions.bean(ConfigurableBlobStore.class);
                if(store != null){
                    store.clearCache();
                }
            }
        };
        container.add(clearCache);

        // Cache Statistics
        final WebMarkupContainer statsContainer = new WebMarkupContainer("statsContainer");
        statsContainer.setOutputMarkupId(true);

        statsContainer.add(new Label("title"));
        statsContainer.add(new Label("totalCount", new MapModel(values, KEY_TOTAL_COUNT)));
        statsContainer.add(new Label("hitCount", new MapModel(values, KEY_HIT_COUNT)));
        statsContainer.add(new Label("missCount", new MapModel(values, KEY_MISS_COUNT)));
        statsContainer.add(new Label("missRate", new MapModel(values, KEY_MISS_RATE)));
        statsContainer.add(new Label("hitRate", new MapModel(values, KEY_HIT_RATE)));
        statsContainer.add(new Label("evicted", new MapModel(values, KEY_EVICTED)));

        AjaxButton statistics = new AjaxButton("statistics") {

            @Override
            protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
                try {
                    ConfigurableBlobStore store = GeoServerExtensions.bean(ConfigurableBlobStore.class);
                    if(store != null){
                        CacheStatistics stats = store.getCacheStatistics();

                        long hitCount = stats.getHitCount();
                        long missCount = stats.getMissCount();
                        long total = stats.getRequestCount();
                        double hitRate = stats.getHitRate();
                        double missRate = stats.getMissRate();
                        long evicted = stats.getEvictionCount();

                        values.put(KEY_MISS_RATE, missRate + " %");
                        values.put(KEY_HIT_RATE, hitRate + " %");
                        values.put(KEY_EVICTED, evicted + "");
                        values.put(KEY_TOTAL_COUNT, total + "");
                        values.put(KEY_MISS_COUNT, missCount + "");
                        values.put(KEY_HIT_COUNT, hitCount + "");
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
                error.setMessage(new ParamResourceModel(errorKey, null,
                        "").getObject());
                validatable.error(error);
            }
        }
    }

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
