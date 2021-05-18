package io.github.speedbridgemc.entityevents.impl;

import io.github.speedbridgemc.entityevents.impl.event.DamageInternals;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

@ApiStatus.Internal
public final class EventCallbackInjector {
    private EventCallbackInjector() { }

    public static final Logger LOGGER = LogManager.getLogger("EntityEvents|EventCallbackInjector");

    public static void transform(@NotNull ClassNode classNode) {
        for (MethodNode method : classNode.methods) {
            if ((method.access & Opcodes.ACC_PUBLIC) == 0)
                continue; // ignore non-public methods, since they can't be overriding Entity.damage
            if (MappedNames.matchesDamageMethod(method.name, method.desc)) {
                LOGGER.debug("Injecting damage event callback into {}.{}{}",
                        classNode.name.replace('/', '.'), method.name, method.desc);
                method.instructions.insert(getDamageInjectionInstructions());
            }
        }
    }

    private static @NotNull String getBinaryName(@NotNull Class<?> clazz) {
        return clazz.getName().replace('/', '.');
    }

    private static final String DAMAGE_INTERNALS_NAME = getBinaryName(DamageInternals.class);

    @SuppressWarnings("CommentedOutCode")
    private static InsnList getDamageInjectionInstructions() {
        final InsnList insns = new InsnList();

        // inject the following code block into the top of the method:
        /*
        if (DamageInternals.invoke(this, source, amount))
            return false;
         */

        final LabelNode continueLabel = new LabelNode();

        // load up ze locals
        insns.add(new VarInsnNode(Opcodes.ALOAD, 0)); // this
        insns.add(new VarInsnNode(Opcodes.ALOAD, 1)); // source
        insns.add(new VarInsnNode(Opcodes.FLOAD, 2)); // amount
        // invoke our damage events
        insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                DAMAGE_INTERNALS_NAME,
                "invoke", MappedNames.METHOD_EVENT_DAMAGE_INVOKE_DESC));
        // check if we should cancel - if yes, return true
        insns.add(new JumpInsnNode(Opcodes.IFEQ, continueLabel));
        insns.add(new InsnNode(Opcodes.ICONST_0));
        insns.add(new InsnNode(Opcodes.IRETURN));
        // otherwise, continue with the rest of the method
        insns.add(continueLabel);

        return insns;
    }
}
