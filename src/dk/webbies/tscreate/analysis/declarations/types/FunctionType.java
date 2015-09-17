package dk.webbies.tscreate.analysis.declarations.types;

import java.util.List;

/**
 * Created by Erik Krogh Kristensen on 02-09-2015.
 */
public class FunctionType implements DeclarationType {
    private DeclarationType returnType;
    private List<Argument> arguments;

    public FunctionType(DeclarationType returnType, List<Argument> arguments) {
        this.returnType = returnType;
        this.arguments = arguments;
    }

    public DeclarationType getReturnType() {
        return returnType;
    }

    public List<Argument> getArguments() {
        return arguments;
    }

    @Override
    public <T> T accept(DeclarationTypeVisitor<T> visitor) {
        return visitor.visit(this);
    }

    public void setReturnType(DeclarationType returnType) {
        this.returnType = returnType;
    }

    public static class Argument {
        private String name;
        private DeclarationType type;

        public Argument(String name, DeclarationType type) {
            this.name = name;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public DeclarationType getType() {
            return type;
        }

        public void setType(DeclarationType type) {
            this.type = type;
        }
    }
}
