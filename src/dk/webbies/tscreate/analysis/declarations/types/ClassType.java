package dk.webbies.tscreate.analysis.declarations.types;

import java.util.Map;

/**
 * Created by Erik Krogh Kristensen on 17-09-2015.
 */
public class ClassType extends DeclarationType{
    private final DeclarationType constructorType;
    private final Map<String, DeclarationType> prototypeFields;
    private final Map<String, DeclarationType> staticFields;
    private final String name;
    public DeclarationType superClass;

    public ClassType(String name, DeclarationType constructorType, Map<String, DeclarationType> properties, Map<String, DeclarationType> staticFields) {
        this.constructorType = constructorType;
        this.prototypeFields = properties;
        this.name = name;
        this.staticFields = staticFields;
    }

    public void setSuperClass(DeclarationType superClass) {
        this.superClass = superClass;
    }

    // Typecasting this, because it is unresolvedType for a while.
    public FunctionType getConstructorType() {
        return (FunctionType) constructorType.resolve();
    }

    public Map<String, DeclarationType> getPrototypeFields() {
        return prototypeFields;
    }

    public Map<String, DeclarationType> getStaticFields() {
        return staticFields;
    }

    public ClassType getSuperClass() {
        return (ClassType) DeclarationType.resolve(superClass);
    }

    public String getName() {
        return name;
    }

    @Override
    public <T> T accept(DeclarationTypeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    @Override
    public <T, A> T accept(DeclarationTypeVisitorWithArgument<T, A> visitor, A argument) {
        return visitor.visit(this, argument);
    }
}
