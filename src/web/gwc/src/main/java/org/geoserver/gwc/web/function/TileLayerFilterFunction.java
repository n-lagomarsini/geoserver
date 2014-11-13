package org.geoserver.gwc.web.function;

import java.util.ArrayList;
import java.util.List;

import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.LayerInfo;
import org.geoserver.gwc.GWC;
import org.geotools.filter.capability.FunctionNameImpl;
import org.geotools.util.Converters;
import org.opengis.filter.capability.FunctionName;
import org.opengis.filter.expression.Expression;
import org.opengis.filter.expression.ExpressionVisitor;
import org.opengis.filter.expression.Function;
import org.opengis.filter.expression.Literal;
import org.opengis.filter.expression.VolatileFunction;

public class TileLayerFilterFunction implements Function, VolatileFunction{
    // parameters are expression, classifier
    public static FunctionName NAME = new FunctionNameImpl("tileLayer");
    
    private List<Expression> parameters;
    private Literal fallback;
    
    public TileLayerFilterFunction() {
        this.parameters = new ArrayList<Expression>();
        this.fallback = null;
    }

    protected TileLayerFilterFunction(List<Expression> parameters, Literal fallback) {
        this.parameters = parameters;
        this.fallback = fallback;
    }
    
    @Override
    public List<Expression> getParameters() {
        return parameters;
    }
    
    @Override
    public Object evaluate(Object object) {
        return evaluate(object, Boolean.class);
    }

    @Override
    public <T> T evaluate(Object object, Class<T> context) {
        GWC gwc = GWC.get();
        if(object instanceof LayerInfo){
            LayerInfo info = (LayerInfo) object;
            boolean accepted = !gwc.hasTileLayer(info);
            return Converters.convert(accepted, context);
        } else if(object instanceof LayerGroupInfo){
            LayerGroupInfo info = (LayerGroupInfo) object;
            boolean accepted = !gwc.hasTileLayer(info);
            return Converters.convert(accepted, context);
        }
        throw new IllegalArgumentException("The selected element is not a LayerInfo instance");
    }

    @Override
    public Object accept(ExpressionVisitor visitor, Object extraData) {
        return visitor.visit(this, extraData);
    }

    @Override
    public String getName() {
        // TODO Auto-generated method stub
        return NAME.getName();
    }

    @Override
    public FunctionName getFunctionName() {
        // TODO Auto-generated method stub
        return NAME;
    }

    @Override
    public Literal getFallbackValue() {
        // TODO Auto-generated method stub
        return fallback;
    }
}
