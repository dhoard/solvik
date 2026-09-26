package org.solvik.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.Node.Children;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.object.DynamicObject.PutNode;
import com.oracle.truffle.api.strings.TruffleString;
import org.solvik.truffle.SolvikFunction;
import org.solvik.truffle.object.SolvikClass;
import org.solvik.truffle.object.SolvikAny;

@NodeInfo(shortName = "new", description = "Construct a Solvik object")
public final class SolvikNewNode extends SolvikExpressionNode {

    private final SolvikClass solvikClass;
    private final SolvikFunction constructor;
    @Children private final SolvikExpressionNode[] arguments;
    /**
     * The optional message argument of a guest exception construction (docs/LANGUAGE_SPEC.md section 22),
     * or {@code null} when the construction carries none. Lowering splits the source argument list into
     * the declared constructor arguments and this trailing message, so the constructor itself always
     * receives exactly its declared parameters.
     */
    @Child private SolvikExpressionNode messageArgument;
    @Child private PutNode initializeShapeNode = PutNode.create();
    @Child private PutNode messagePutNode = PutNode.create();

    public SolvikNewNode(SolvikClass solvikClass, SolvikExpressionNode[] arguments) {
        this(solvikClass, arguments, null);
    }

    public SolvikNewNode(SolvikClass solvikClass, SolvikExpressionNode[] arguments, SolvikExpressionNode messageArgument) {
        this.solvikClass = solvikClass;
        this.constructor = solvikClass.constructor();
        this.arguments = arguments;
        this.messageArgument = messageArgument;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        // Constructing an instance is an active use of its class, so the class and its superclass chain
        // are initialized before the constructor runs and before the value arguments are evaluated
        // (docs/LANGUAGE_SPEC.md section 7). The steady-state guard is one boolean field test.
        solvikClass.ensureInitialized();
        SolvikAny object = new SolvikAny(solvikClass);
        // Add every declared property in declaration order so all instances of a class share one
        // stable shape and no undeclared member can ever be inserted. The synthesized message slot of
        // an exception class is included in this set and seeded to null here.
        for (int i = 0; i < solvikClass.propertyCount(); i++) {
            initializeShapeNode.execute(object, solvikClass.propertyKey(i), null);
        }
        Object[] callArguments = new Object[arguments.length + 1];
        callArguments[0] = object;
        for (int i = 0; i < arguments.length; i++) {
            callArguments[i + 1] = arguments[i].executeGeneric(frame);
        }
        // The trailing message argument is evaluated after the declared constructor arguments (it is the
        // last source argument) and stored into the synthesized slot before the constructor body runs, so
        // the message is present for the whole life of the value. When no message was supplied the slot
        // keeps its null seed, and getMessage() reads that null.
        TruffleString messageKey = solvikClass.messageKey();
        if (messageKey != null && messageArgument != null) {
            messagePutNode.execute(object, messageKey, messageArgument.executeGeneric(frame));
        }
        constructor.callTarget().call(callArguments);
        return object;
    }
}
