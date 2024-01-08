package org.jetbrains.java.decompiler.main.decompiler.preprocessing;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ListIterator;

public interface InstructionProcessor {
  boolean canProcess(AbstractInsnNode node, MethodNode method, ClassNode cls);

  boolean process(AbstractInsnNode node, MethodNode method, ClassNode cls, ListIterator<AbstractInsnNode> iterator);
}
