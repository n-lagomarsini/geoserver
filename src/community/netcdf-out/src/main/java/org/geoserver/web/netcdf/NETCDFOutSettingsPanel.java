/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2014 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.web.netcdf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.apache.wicket.Component;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.FormComponent;
import org.apache.wicket.markup.html.form.FormComponentPanel;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.validation.IValidatable;
import org.apache.wicket.validation.ValidationError;
import org.apache.wicket.validation.validator.AbstractValidator;
import org.geoserver.catalog.MetadataMap;
import org.geoserver.config.SettingsInfo;
import org.geoserver.gwc.web.GWCIconFactory;
import org.geoserver.web.data.settings.SettingsPluginPanel;
import org.geoserver.web.netcdf.NetCDFSettingsContainer.GlobalAttribute;
import org.geoserver.web.netcdf.NetCDFSettingsContainer.Version;
import org.geoserver.web.util.MetadataMapModel;
import org.geoserver.web.wicket.GeoServerAjaxFormLink;
import org.geoserver.web.wicket.Icon;
import org.geoserver.web.wicket.ImageAjaxLink;
import org.geoserver.web.wicket.ParamResourceModel;
import org.geowebcache.filter.parameters.ParameterFilter;

/**
 * Simple Panel which adds a TextField for setting the Root Directory for the WorkSpace or Global Settings.
 * 
 * @author Nicola Lagomarsini Geosolutions S.A.S.
 * 
 */
public class NETCDFOutSettingsPanel extends SettingsPluginPanel {

    

    public NETCDFOutSettingsPanel(String id, IModel<SettingsInfo> model) {
        super(id, model);
        // Model associated to the metadata map
        final PropertyModel<MetadataMap> metadata = new PropertyModel<MetadataMap>(model,
                "metadata");
        

        // Getting the NetcdfSettingsContainer model from MetadataMap
        IModel<NetCDFSettingsContainer> netcdfModel = new MetadataMapModel(metadata,
                NetCDFSettingsContainer.NETCDFOUT_KEY, NetCDFSettingsContainer.class);

        // New Container
        // container for ajax updates
        NETCDFPanel panel = new NETCDFPanel("panel", netcdfModel);
        add(panel);
    }
}
