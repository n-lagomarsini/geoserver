package org.geoserver.web.demo.function;

//import static org.geotools.filter.capability.FunctionNameImpl.parameter;

import java.util.ArrayList;
import java.util.List;

import org.geoserver.catalog.LayerGroupInfo;
import org.geoserver.catalog.LayerInfo;
import org.geotools.filter.capability.FunctionNameImpl;
import org.geotools.util.Converters;
import org.opengis.filter.capability.FunctionName;
import org.opengis.filter.expression.Expression;
import org.opengis.filter.expression.ExpressionVisitor;
import org.opengis.filter.expression.Function;
import org.opengis.filter.expression.Literal;
import org.opengis.filter.expression.VolatileFunction;

public class LayerGroupFilterFunction implements Function, VolatileFunction {

 // parameters are expression, classifier
    public static FunctionName NAME = new FunctionNameImpl("layerGroupFilter");
    
    private List<Expression> parameters;
    private Literal fallback;
    
    public LayerGroupFilterFunction() {
        this.parameters = new ArrayList<Expression>();
        this.fallback = null;
    }

    protected LayerGroupFilterFunction(List<Expression> parameters, Literal fallback) {
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
        if(object instanceof LayerGroupInfo){
            LayerGroupInfo info = (LayerGroupInfo) object;
            
            boolean accepted = false;
            
            if (!LayerGroupInfo.Mode.CONTAINER.equals(info.getMode())) {            
                boolean enabled = true;
                for (LayerInfo layer : info.layers()) {
                    // ask for enabled() instead of isEnabled() to account for disabled resource/store
                    enabled &= layer.enabled();
                }
                
                if (enabled && info.layers().size() > 0){
                    accepted = true;
                }
            }
            return Converters.convert(accepted, context);
        }
        throw new IllegalArgumentException("The selected element is not a LayerGroupInfo instance");
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
