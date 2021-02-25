package adudecalledleo.speedbridge.entityevents.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

@ApiStatus.Internal
public final class EventCallbackInjector {
    private EventCallbackInjector() { }

    public static void transform(@NotNull ClassNode classNode) {
        for (MethodNode method : classNode.methods) {
            if ((method.access & Opcodes.ACC_PUBLIC) == 0)
                continue; // ignore non-public methods
            if (MappedNames.matchesDamageMethod(method.name, method.desc))
                method.instructions.insert(getDamageInjectionInstructions());
        }
    }

    @SuppressWarnings("CommentedOutCode")
    private static InsnList getDamageInjectionInstructions() {
        final InsnList instructions = new InsnList();

        // inject the following code block into the top of the method:
        /*
        if (DamageInternals.invoke(this, source, amount))
            return false;
         */

        final LabelNode continueLabel = new LabelNode();

        // load up ze locals
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 1)); // source
        instructions.add(new VarInsnNode(Opcodes.FLOAD, 2)); // amount
        // invoke our damage events
        instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "adudecalledleo/speedbridge/entityevents/impl/event/DamageInternals",
                "invoke", MappedNames.METHOD_EVENT_DAMAGE_INVOKE_DESC));
        instructions.add(new JumpInsnNode(Opcodes.IFEQ, continueLabel));
        // if we should cancel, return false
        instructions.add(new InsnNode(Opcodes.ICONST_0));
        instructions.add(new InsnNode(Opcodes.IRETURN));
        // otherwise, continue with the rest of the method
        instructions.add(continueLabel);

        return instructions;
    }
}
