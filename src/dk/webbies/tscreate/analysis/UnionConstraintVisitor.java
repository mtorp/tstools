package dk.webbies.tscreate.analysis;

import com.google.common.collect.Sets;
import dk.au.cs.casa.typescript.types.Signature;
import dk.webbies.tscreate.util.Util;
import dk.webbies.tscreate.analysis.unionFind.*;
import dk.webbies.tscreate.jsnap.Snap;
import dk.webbies.tscreate.jsnap.classes.LibraryClass;
import dk.webbies.tscreate.paser.AST.*;
import dk.webbies.tscreate.paser.ExpressionVisitor;
import dk.webbies.tscreate.paser.StatementTransverse;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by Erik Krogh Kristensen on 02-09-2015.
 */
public class UnionConstraintVisitor implements ExpressionVisitor<UnionNode>, StatementTransverse<UnionNode> {
    private final Snap.Obj closure;
    private final UnionFindSolver solver;
    private final Map<TypeAnalysis.ProgramPoint, UnionNode> nodes;
    private final FunctionNode functionNode;
    private final Map<Snap.Obj, FunctionNode> functionNodes;
    private TypeAnalysis typeAnalysis;
    private final PrimitiveNode.Factory primitiveFactory;
    private HeapValueFactory heapFactory;
    private Set<Snap.Obj> analyzedFunction;

    public UnionConstraintVisitor(
            Snap.Obj function,
            UnionFindSolver solver,
            Map<TypeAnalysis.ProgramPoint, UnionNode> nodes,
            FunctionNode functionNode,
            Map<Snap.Obj, FunctionNode> functionNodes,
            HeapValueFactory heapFactory,
            TypeAnalysis typeAnalysis,
            Set<Snap.Obj> analyzedFunction) {
        this.closure = function;
        this.solver = solver;
        this.heapFactory = heapFactory;
        this.nodes = nodes;
        this.functionNode = functionNode;
        this.functionNodes = functionNodes;
        this.typeAnalysis = typeAnalysis;
        this.analyzedFunction = analyzedFunction;
        this.primitiveFactory = new PrimitiveNode.Factory(solver, typeAnalysis.globalObject);
    }

    UnionNode get(AstNode node) {
        return getUnionNode(node, this.closure, this.nodes, solver);
    }

    @Override
    public ExpressionVisitor<UnionNode> getExpressionVisitor() {
        return this;
    }

    public static UnionNode getUnionNode(AstNode node, Snap.Obj closure, Map<TypeAnalysis.ProgramPoint, UnionNode> nodes, UnionFindSolver solver) {
        if (node == null) {
            throw new NullPointerException("node cannot be null");
        }
        TypeAnalysis.ProgramPoint key = new TypeAnalysis.ProgramPoint(closure, node);
        if (nodes.containsKey(key)) {
            return nodes.get(key);
        } else {
            EmptyNode result = new EmptyNode(solver);
            nodes.put(key, result);
            return result;
        }
    }

    @Override
    public UnionNode visit(BinaryExpression op) {
        UnionNode lhs = op.getLhs().accept(this);
        UnionNode rhs = op.getRhs().accept(this);

        solver.union(lhs, primitiveFactory.nonVoid());
        solver.union(rhs, primitiveFactory.nonVoid());

        UnionNode result;
        switch (op.getOperator()) {
            case PLUS: {
                solver.union(lhs, primitiveFactory.stringOrNumber());
                solver.union(rhs, primitiveFactory.stringOrNumber());
                result = new IncludeNode(solver, lhs, rhs, primitiveFactory.stringOrNumber());
                break;
            }

            case EQUAL: // = // assignment
                solver.union(lhs, rhs);
                result = lhs;
                break;
            case PLUS_EQUAL: // +=
                solver.union(lhs, rhs, primitiveFactory.stringOrNumber());
                result = lhs;
                break;
            case NOT_EQUAL: // !=
            case EQUAL_EQUAL: // ==
            case NOT_EQUAL_EQUAL: // !==
            case EQUAL_EQUAL_EQUAL: // ===
                solver.union(lhs, rhs);
                result = primitiveFactory.bool();
                break;
            case AND: // &&
            case OR: // ||
                result = new IncludeNode(solver, lhs, rhs);
                break;
            case MINUS: // -
            case MULT: // *
            case DIV: // /
            case MOD: // %
            case MINUS_EQUAL: // -=
            case MULT_EQUAL: // *=
            case DIV_EQUAL: // /=
            case MOD_EQUAL: // %=
            case LESS_THAN: // <
            case LESS_THAN_EQUAL: // <=
            case GREATER_THAN: // >
            case GREATER_THAN_EQUAL: // >=
            case BITWISE_AND: // &
            case BITWISE_OR: // |
            case BITWISE_XOR: // ^
            case LEFT_SHIFT: // <<
            case RIGHT_SHIFT: // >>
            case UNSIGNED_RIGHT_SHIFT: // >>>
            case LEFT_SHIFT_EQUAL: // <<=
            case RIGHT_SHIFT_EQUAL: // >>=
            case UNSIGNED_RIGHT_SHIFT_EQUAL: // >>>=
                solver.union(primitiveFactory.number(), lhs);
                solver.union(primitiveFactory.number(), rhs);
                result = primitiveFactory.number();
                break;
            case INSTANCEOF: // instanceof
                result = primitiveFactory.bool();
                break;
            case IN: // in
                solver.union(lhs, primitiveFactory.string());
                solver.union(rhs, new ObjectNode(solver));
                result = primitiveFactory.bool();
                break;
            default:
                throw new UnsupportedOperationException("Don't yet handle the operator: " + op.getOperator());
        }

        return solver.union(get(op), result);
    }

    @Override
    public UnionNode visit(UnaryExpression unOp) {
        UnionNode exp = unOp.getExpression().accept(this);
        solver.union(exp, primitiveFactory.nonVoid());

        UnionNode result;
        switch (unOp.getOperator()) {
            case MINUS:
            case PLUS:
            case MINUS_MINUS:
            case PLUS_PLUS:
            case BITWISE_NOT:
                solver.union(primitiveFactory.number(), exp);
                result = primitiveFactory.number();
                break;
            case NOT:
                result = primitiveFactory.bool();
                break;
            case TYPEOF:
                result = primitiveFactory.string();
                break;
            case VOID:
                result = primitiveFactory.undefined();
                break;
            case DELETE:
                result = primitiveFactory.bool();
                break;
            default:
                throw new UnsupportedOperationException("Don't yet handle the operator: " + unOp.getOperator());
        }
        return solver.union(get(unOp), result);
    }

    @Override
    public UnionNode visit(ForInStatement forIn) {
        forIn.getInitializer().accept(new NodeTransverse<Void>() {
            @Override
            public Void visit(Identifier identifier) {
                solver.union(get(identifier), primitiveFactory.string());
                return null;
            }
        });
        solver.union(get(forIn.getCollection()), new ObjectNode(solver));
        return null;
    }

    @Override
    public UnionNode visit(ThisExpression thisExpression) {
        return solver.union(get(thisExpression), this.functionNode.thisNode);
    }

    @Override
    public UnionNode visit(ConditionalExpression condExp) {
        condExp.getCondition().accept(this);

        UnionNode left = condExp.getLeft().accept(this);
        UnionNode right = condExp.getRight().accept(this);
        solver.union(left, right);
        return solver.union(get(condExp), left);
    }

    @Override
    public UnionNode visit(CommaExpression commaExpression) {
        commaExpression.getExpressions().forEach(exp -> exp.accept(this));
        return solver.union(get(commaExpression), get(commaExpression.getLastExpression()));
    }

    @Override
    public UnionNode visit(Return aReturn) {
        UnionNode exp = aReturn.getExpression().accept(this);
        solver.union(exp, functionNode.returnNode, primitiveFactory.nonVoid());
        return null;
    }

    @Override
    public UnionNode visit(SwitchStatement switchStatement) {
        solver.union(get(switchStatement.getExpression()), primitiveFactory.stringOrNumber());
        StatementTransverse.super.visit(switchStatement);
        return null;
    }

    @Override
    public UnionNode visit(StringLiteral string) {
        return solver.union(get(string), primitiveFactory.string());
    }

    @Override
    public UnionNode visit(Identifier identifier) {
        if (identifier.getDeclaration() == null) {
            throw new RuntimeException("Cannot have null declarations");
        }
        return solver.union(get(identifier), get(identifier.getDeclaration()));
    }

    @Override
    public UnionNode visit(BooleanLiteral booleanLiteral) {
        return solver.union(get(booleanLiteral), primitiveFactory.bool());
    }

    @Override
    public UnionNode visit(UndefinedLiteral undefined) {
        return solver.union(get(undefined), primitiveFactory.undefined());
    }

    @Override
    public UnionNode visit(FunctionExpression function) {
        if (closureMatch(function, this.closure)) {
            function.getBody().accept(this);
            function.getArguments().forEach(arg -> solver.union(arg.accept(this), primitiveFactory.nonVoid()));

            if (this.closure.function.type.equals("user")) {
                for (int i = 0; i < functionNode.arguments.size(); i++) {
                    solver.union(get(function.getArguments().get(i)), functionNode.arguments.get(i));
                }
            } else {
                int boundArguments = this.closure.function.arguments.size() - 1;
                for (int i = 0; i < functionNode.arguments.size(); i++) {
                    solver.union(get(function.getArguments().get(i + boundArguments)), functionNode.arguments.get(i));
                }
                for (int i = 0; i < boundArguments; i++) {
                    solver.union(get(function.getArguments().get(i)), heapFactory.fromValue(this.closure.function.arguments.get(i + 1))); // Plus 1, because the first argment is the "this" node.
                }
            }
            return null;
        } else {
            FunctionNode result = FunctionNode.create(function, solver);
            if (function.getName() != null) {
                solver.union(get(function.getName()), result);
                function.getName().accept(this);
            }
            new UnionConstraintVisitor(this.closure, this.solver, this.nodes, result, this.functionNodes, heapFactory, typeAnalysis, analyzedFunction).visit(function.getBody());
            for (int i = 0; i < function.getArguments().size(); i++) {
                solver.union(get(function.getArguments().get(i)), result.arguments.get(i));
                solver.union(get(function.getArguments().get(i)), primitiveFactory.nonVoid());
            }
            return solver.union(get(function), result);
        }
    }

    private static boolean closureMatch(FunctionExpression function, Snap.Obj closure) {
        String type = closure.function.type;
        if (type.equals("user")) {
            return function == closure.function.astNode;
        }
        if (type.equals("bind")) {
            return function == closure.function.target.function.astNode;
        }
        throw new RuntimeException("Unknown type: " + type);
    }

    @Override
    public UnionNode visit(NumberLiteral number) {
        return solver.union(get(number), primitiveFactory.number());
    }

    @Override
    public UnionNode visit(NullLiteral nullLiteral) {
        return solver.union(get(nullLiteral), primitiveFactory.nonVoid());
    }

    @Override
    public UnionNode visit(VariableNode variableNode) {
        UnionNode initNode = variableNode.getInit().accept(this);
        UnionNode identifierNode = variableNode.getlValue().accept(this);
        solver.union(initNode, identifierNode);
        return null;
    }

    @Override
    public UnionNode visit(ObjectLiteral object) {
        ObjectNode result = new ObjectNode(solver);
        for (Map.Entry<String, Expression> entry : object.getProperties().entrySet()) {
            String key = entry.getKey();
            Expression value = entry.getValue();
            UnionNode valueNode = value.accept(this);
            solver.union(valueNode, primitiveFactory.nonVoid());
            result.addField(key, valueNode);
        }

        return solver.union(get(object), result);
    }

    @Override
    public UnionNode visit(DynamicAccessExpression dynamicAccessExpression) {
        UnionNode lookupKey = dynamicAccessExpression.getLookupKey().accept(this);
        UnionNode operand = dynamicAccessExpression.getOperand().accept(this);
        UnionNode returnType = get(dynamicAccessExpression);

        solver.union(lookupKey, primitiveFactory.stringOrNumber());
        DynamicAccessNode dynamicAccessNode = new DynamicAccessNode(returnType, lookupKey, solver);
        solver.union(operand, dynamicAccessNode);
        solver.runWhenChanged(operand, new IncludesWithFieldsResolver(operand, solver));
        return returnType;
    }

    @Override
    public UnionNode visit(CallExpression callExpression) {
        List<UnionNode> args = callExpression.getArgs().stream().map(arg -> arg.accept(this)).collect(Collectors.toList());
        UnionNode function = callExpression.getFunction().accept(this);
        EmptyNode returnNode = new EmptyNode(solver);
        solver.runWhenChanged(function, new CallGraphResolver(this.functionNode.thisNode, function, args, returnNode, callExpression));
        return solver.union(get(callExpression), returnNode);
    }

    @Override
    public UnionNode visit(MethodCallExpression methodCall) {
        List<UnionNode> args = methodCall.getArgs().stream().map(arg -> arg.accept(this)).collect(Collectors.toList());
        UnionNode function = methodCall.getMemberExpression().accept(this);
        EmptyNode returnNode = new EmptyNode(solver);
        solver.runWhenChanged(function, new CallGraphResolver(get(methodCall.getMemberExpression().getExpression()), function, args, returnNode, methodCall));
        return solver.union(get(methodCall), returnNode);
    }

    @Override
    public UnionNode visit(NewExpression newExp) {
        List<UnionNode> args = newExp.getArgs().stream().map(arg -> arg.accept(this)).collect(Collectors.toList());
        UnionNode function = newExp.getOperand().accept(this);
        UnionNode thisNode = get(newExp);
        solver.runWhenChanged(function, new NewCallResolver(function, args, thisNode, newExp));
        return thisNode;
    }

    @Override
    public UnionNode visit(MemberExpression member) {
        UnionNode objectExp = member.getExpression().accept(this);
        ObjectNode object = new ObjectNode(solver);
        UnionNode result = get(member);
        object.addField(member.getProperty(), result);
        solver.union(object, objectExp);
        solver.union(primitiveFactory.nonVoid(), result);
        solver.runWhenChanged(object, new MemberResolver(member));
        solver.runWhenChanged(object, new IncludesWithFieldsResolver(object, solver));
        return result;
    }

    private static final class IncludesWithFieldsResolver implements Runnable {
        private final UnionNode node;
        private UnionFindSolver solver;

        public IncludesWithFieldsResolver(UnionNode node, UnionFindSolver solver) {
            this.node = node;
            this.solver = solver;
        }

        @Override
        public void run() {
            UnionClass myClass = this.node.getUnionClass();
            if (myClass.getFields() == null) {
                return;
            }
            List<UnionClass> reachableClasses = this.node.getUnionClass().getReachable();
            for (UnionClass otherClass : reachableClasses) {
                if (otherClass.getFields() == null) {
                    continue;
                }
                for (String key : Sets.intersection(myClass.getFields().keySet(), otherClass.getFields().keySet())) {
                    UnionNode myField = myClass.getFields().get(key);
                    UnionNode otherField = otherClass.getFields().get(key);
                    if (myField.getUnionClass().includes == null || !myField.getUnionClass().includes.contains(otherField.getUnionClass())) {
                        solver.union(myField, new IncludeNode(solver, otherField));
                    }
                }
            }
        }
    }

    private final class MemberResolver implements Runnable {
        private MemberExpression member;
        private Set<Snap.Obj> seenPrototypes = new HashSet<>();
        private String name;

        public MemberResolver(MemberExpression member) {
            this.member = member;
            this.name = member.getProperty();
        }

        @Override
        public void run() {
            List<UnionFeature> features = UnionFeature.getReachable(get(member.getExpression()).getFeature());

            Set<Snap.Obj> prototypes = new HashSet<>();
            for (UnionFeature feature : features) {
                prototypes.addAll(feature.getPrototypes());
            }

            prototypes.removeAll(seenPrototypes);
            seenPrototypes.addAll(prototypes);
            for (Snap.Obj prototype : new HashSet<>(prototypes)) {
                UnionNode propertyNode = lookupProperty(prototype, member.getProperty());
                solver.union(get(member), propertyNode);
            }
        }

        private UnionNode lookupProperty(Snap.Value value, String name) {
            if (value == null || !(value instanceof Snap.Obj)) {
                return new EmptyNode(solver);
            }
            Snap.Obj obj = (Snap.Obj) value;
            Snap.Property property = obj.getProperty(name);
            if (property != null) {
                return heapFactory.fromProperty(property);
            }

            return lookupProperty(obj.prototype, name);
        }
    }



    private final class NewCallResolver implements Runnable {
        private final UnionNode function;
        private final List<UnionNode> args;
        private final UnionNode thisNode;
        private final CallGraphResolver callResolver;
        private final HashSet<Snap.Obj> seenHeap = new HashSet<>();
        private FunctionSignatureFactory functionNodeSignatureFactory;

        public NewCallResolver(UnionNode function, List<UnionNode> args, UnionNode thisNode, Expression callExpression) {
            this.function = function;
            this.args = args;
            this.thisNode = thisNode;
            this.callResolver = new CallGraphResolver(thisNode, function, args, new EmptyNode(solver), callExpression);
            this.callResolver.constructorCalls = true;
            this.functionNodeSignatureFactory = new FunctionSignatureFactory(UnionConstraintVisitor.this.primitiveFactory, UnionConstraintVisitor.this.solver, UnionConstraintVisitor.this.typeAnalysis.typeNames);
        }

        @Override
        public void run() {
            getFunctionNodes(function, seenHeap).stream().forEach(closure -> {
                switch (closure.function.type) {
                    case "native":
                        Snap.Property prototypeProp = closure.getProperty("prototype");
                        if (prototypeProp != null) {
                            solver.union(this.thisNode, new HasPrototypeNode(solver, (Snap.Obj) prototypeProp.value));
                        }
                        List<FunctionNode> signatures = createNativeSignatureNodes(closure, this.args, true, functionNodeSignatureFactory);
                        for (FunctionNode signature : signatures) {
                            solver.union(signature.returnNode, this.thisNode);
                        }
                        break;
                    case "user":
                        @SuppressWarnings("RedundantCast")
                        LibraryClass clazz = typeAnalysis.libraryClasses.get((Snap.Obj) closure.getProperty("prototype").value);
                        if (clazz != null) {
                            if (typeAnalysis.options.classOptions.useThisObjectUsages) {
                                solver.union(this.thisNode, clazz.getNewThisNode(solver));
                            }
                            solver.union(this.thisNode, new HasPrototypeNode(solver, clazz.prototype));

                            if (typeAnalysis.options.classOptions.useConstructorUsages) {
                                solver.union(clazz.getNewConstructorNode(solver), this.function);
                            }
                        }
                        break;
                    case "unknown":
                        break; // Nothing we can do.
                    default:
                        throw new UnsupportedOperationException("Do now know functions of type " + closure.function.type + " here.");
                }

            });

            this.callResolver.run();
        }
    }

    private final class CallGraphResolver implements Runnable {
        List<UnionNode> args;
        private final Expression callExpression; // Useful for debugging.
        boolean constructorCalls;
        private HashSet<Snap.Obj> seenHeap = new HashSet<>();
        private final FunctionNode functionNode;
        private FunctionSignatureFactory functionSignatureFactory;

        public CallGraphResolver(UnionNode thisNode, UnionNode function, List<UnionNode> args, EmptyNode returnNode, Expression callExpression) {
            solver.runWhenChanged(function, new IncludesWithFieldsResolver(function, solver));

            this.args = args;
            this.callExpression = callExpression;

            functionNode = FunctionNode.create(args.size(), solver);
            solver.union(function, functionNode);

            Util.zip(functionNode.arguments.stream(), args.stream()).forEach(pair -> solver.union(pair.left, pair.right, primitiveFactory.nonVoid()));

            solver.union(functionNode.returnNode, returnNode);
            solver.union(functionNode.thisNode, thisNode);
            
            this.functionSignatureFactory = new FunctionSignatureFactory(primitiveFactory, UnionConstraintVisitor.this.solver, UnionConstraintVisitor.this.typeAnalysis.typeNames);
        }

        @Override
        public void run() {
            Collection<Snap.Obj> functions = getFunctionNodes(functionNode, seenHeap);

            for (Snap.Obj closure : functions) {
                switch (closure.function.type) {
                    case "user":
                    case "bind": {
                        FunctionNode functionNode;
                        if (UnionConstraintVisitor.this.functionNodes.containsKey(closure)) {
                            functionNode = UnionConstraintVisitor.this.functionNodes.get(closure);
                        } else {
                            functionNode = FunctionNode.create(closure, solver);
                            UnionConstraintVisitor.this.functionNodes.put(closure, functionNode);
                        }
                        solver.union(functionNode, this.functionNode);
                        if (UnionConstraintVisitor.this.analyzedFunction.contains(closure)) {
                            break;
                        }
                        UnionConstraintVisitor.this.analyzedFunction.add(closure);

                        if (typeAnalysis.options.interProceduralAnalysisWithHeap) {
                            typeAnalysis.analyse(closure, UnionConstraintVisitor.this.functionNodes, solver, functionNode, heapFactory, analyzedFunction);
                        } else {
                            new UnionConstraintVisitor(
                                    closure,
                                    UnionConstraintVisitor.this.solver,
                                    UnionConstraintVisitor.this.nodes,
                                    functionNode,
                                    UnionConstraintVisitor.this.functionNodes,
                                    UnionConstraintVisitor.this.heapFactory,
                                    typeAnalysis,
                                    UnionConstraintVisitor.this.analyzedFunction).
                                    visit(closure.function.astNode);
                        }
                        break;
                    }
                    case "native": {
                        boolean constructorCalls = this.constructorCalls;
                        List<FunctionNode> signatures = UnionConstraintVisitor.createNativeSignatureNodes(closure, args, constructorCalls, functionSignatureFactory);
                        solver.union(functionNode, signatures);
                        break;
                    }
                    case "unknown":
                        break;
                    default:
                        throw new RuntimeException("What?");
                }
            }
        }
    }

    private static List<FunctionNode> createNativeSignatureNodes(Snap.Obj closure, List<UnionNode> args, boolean constructorCalls, FunctionSignatureFactory functionNodeFactory) {
        List<Signature> signatures;
        if (constructorCalls) {
            signatures = closure.function.constructorSignatures;
        } else {
            signatures = closure.function.callSignatures;
        }
        ArrayList<FunctionNode> result = new ArrayList<>();
        for (Signature signature : signatures) {
            result.add(functionNodeFactory.fromSignature(signature, closure, args));
        }
        return result;
    }

    private Collection<Snap.Obj> getFunctionNodes(UnionNode function, HashSet<Snap.Obj> seenHeap) {
        Set<Snap.Obj> result = new HashSet<>();
        for (UnionFeature feature : UnionFeature.getReachable(function.getFeature())) {
            if (feature.getFunctionFeature() != null) {
                for (Snap.Obj closure : feature.getFunctionFeature().getClosures()) {
                    if (closure.function == null) {
                        continue;
                    }
                    if (seenHeap.contains(closure)) {
                        continue;
                    }
                    seenHeap.add(closure);

                    result.add(closure);
                }

            }
        }

        return result;
    }
}
