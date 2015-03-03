/* (c) 2015 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.web.netcdf;

import java.util.ArrayList;
import java.util.Arrays;
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
import org.geoserver.gwc.web.GWCIconFactory;
import org.geoserver.web.netcdf.NetCDFSettingsContainer.GlobalAttribute;
import org.geoserver.web.netcdf.NetCDFSettingsContainer.Version;
import org.geoserver.web.wicket.GeoServerAjaxFormLink;
import org.geoserver.web.wicket.Icon;
import org.geoserver.web.wicket.ImageAjaxLink;
import org.geoserver.web.wicket.ParamResourceModel;

public class NETCDFPanel extends FormComponentPanel<NetCDFSettingsContainer>{
    
    private final ListView<GlobalAttribute> globalAttributes;
    
    private final CheckBox shuffle;
    
    private final TextField<Double> compressionLevel;
    
    private final DropDownChoice<NetCDFSettingsContainer.Version> version;
    
    public NETCDFPanel(String id, IModel<NetCDFSettingsContainer> netcdfModel) {
        super(id, netcdfModel);
        // Model associated to the metadata map
        //final PropertyModel<MetadataMap> metadata = new PropertyModel<MetadataMap>(model,
                //"metadata");

        // New Container
        // container for ajax updates
        final WebMarkupContainer container = new WebMarkupContainer("container");
        container.setOutputMarkupId(true);
        add(container);


        // CheckBox associated to the shuffle parameter
        shuffle = new CheckBox("shuffle", new PropertyModel(netcdfModel, "shuffle"));
        container.add(shuffle);

        // TextBox associated to the compression parameter
        compressionLevel = new TextField<Double>("compressionLevel",
                new PropertyModel(netcdfModel, "compressionLevel"));

        // DropDown associated to the netcdf version parameter
        List<NetCDFSettingsContainer.Version> versions = Arrays
                .asList(NetCDFSettingsContainer.Version.values());
        version = new DropDownChoice<NetCDFSettingsContainer.Version>(
                "netcdfVersion", new PropertyModel(netcdfModel, "netcdfVersion"), versions);
        version.setOutputMarkupId(true);
        version.add(new AjaxFormComponentUpdatingBehavior("onChange") {

            @Override
            protected void onUpdate(AjaxRequestTarget target) {
                version.processInput();
                Version v = version.getConvertedInput();
                compressionLevel.setEnabled(v == Version.NETCDF_1_4);
                target.addComponent(container);
            }
        });
        container.add(version);

        // Update the compressionLevel value
        // Enabling it only for netcdf 1.4
        compressionLevel.setEnabled(version.getConvertedInput() == Version.NETCDF_1_4);
        // Adding validation on the compression level
        compressionLevel.add(new AbstractValidator<Double>() {

            @Override
            public boolean validateOnNullValue() {
                return true;
            }

            @Override
            protected void onValidate(IValidatable<Double> validatable) {
                if (validatable != null && validatable.getValue() != null) {
                    Double value = validatable.getValue();
                    if (value < 0) {
                        ValidationError error = new ValidationError();
                        error.setMessage(new ParamResourceModel(
                                "NETCDFOutSettingsPanel.lowCompression", null, "").getObject());
                        validatable.error(error);
                    } else if (value > 9) {
                        ValidationError error = new ValidationError();
                        error.setMessage(new ParamResourceModel(
                                "NETCDFOutSettingsPanel.highCompression", null, "").getObject());
                        validatable.error(error);
                    }
                }
            }
        });
        container.add(compressionLevel);

        IModel<List<GlobalAttribute>> attributeModel = new PropertyModel(netcdfModel,
                "globalAttributes");
        // Global Attributes definition
        globalAttributes = new ListView<GlobalAttribute>("globalAttributes", attributeModel) {

            private static final long serialVersionUID = 1L;

            @Override
            protected void populateItem(final ListItem<GlobalAttribute> item) {
                // Create form
                final Label keyField;
                keyField = new Label("key", new PropertyModel<String>(item.getModel(), "key"));
                item.add(keyField);

                // Create form
                final Label valueField;
                valueField = new Label("value", new PropertyModel<String>(item.getModel(), "value"));
                item.add(valueField);

                final Component removeLink;

                removeLink = new ImageAjaxLink("remove", GWCIconFactory.DELETE_ICON) {
                    private static final long serialVersionUID = 1L;

                    @Override
                    protected void onClick(AjaxRequestTarget target) {
                        List<GlobalAttribute> list;
                        list = new ArrayList<GlobalAttribute>(globalAttributes.getModelObject());
                        final GlobalAttribute attribute = (GlobalAttribute) getDefaultModelObject();

                        list.remove(attribute);
                        globalAttributes.setModelObject(list);
                        item.remove();

                        target.addComponent(container);
                    }
                };
                removeLink.setDefaultModel(item.getModel());
                // removeLink.add(new AttributeModifier("title", true, new ResourceModel(
                // "ParameterFilterEditor.removeLink")));
                item.add(removeLink);
            }
        };
        globalAttributes.setOutputMarkupId(true);
        container.add(globalAttributes);

        // TextField for a new Value
        final TextField<String> newValue = new TextField<String>("newValue", Model.of(""));
        newValue.setOutputMarkupId(true);
        container.add(newValue);

        // TextField for a new Key
        final TextField<String> newKey = new TextField<String>("newKey", Model.of(""));
        newKey.setOutputMarkupId(true);
        container.add(newKey);

        GeoServerAjaxFormLink addLink = new GeoServerAjaxFormLink("add") {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onClick(AjaxRequestTarget target, Form form) {
                newValue.processInput();
                newKey.processInput();
                String key = newKey.getModelObject();
                if (key == null || key.isEmpty()) {
                    ParamResourceModel rm = new ParamResourceModel(
                            "NETCDFOutSettingsPanel.nonEmptyKey", null, "");
                    error(rm.getString());
                } else {
                    String value = newValue.getModelObject();
                    GlobalAttribute attribute = new GlobalAttribute(key, value);
                    if (!globalAttributes.getModelObject().contains(attribute)) {
                        globalAttributes.getModelObject().add(attribute);
                    }
                    newKey.setModel(Model.of("")); // Reset the key field
                    newValue.setModel(Model.of("")); // Reset the Value field
                    
                    target.addComponent(container);
                }
            }
        };
        addLink.add(new Icon("addIcon", GWCIconFactory.ADD_ICON));
        container.add(addLink);
        
        NetCDFSettingsContainer object = netcdfModel.getObject();
        object.toString();
    }
    
    
    @Override
    protected void convertInput() {
        globalAttributes.visitChildren(new Component.IVisitor<Component>() {

            @Override
            public Object component(Component component) {
                if (component instanceof FormComponent) {
                    FormComponent<?> formComponent = (FormComponent<?>) component;
                    formComponent.processInput();
                }
                return Component.IVisitor.CONTINUE_TRAVERSAL;
            }
        });
        List<GlobalAttribute> info = globalAttributes.getModelObject();
        NetCDFSettingsContainer convertedInput = new NetCDFSettingsContainer();
        convertedInput.setCompressionLevel(compressionLevel.getModelObject());
        convertedInput.setGlobalAttributes(info);
        convertedInput.setNetcdfVersion(version.getModelObject());
        convertedInput.setShuffle(shuffle.getModelObject());
        setConvertedInput(convertedInput);
    }
}
