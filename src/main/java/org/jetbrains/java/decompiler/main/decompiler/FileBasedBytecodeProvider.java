package org.jetbrains.java.decompiler.main.decompiler;

import org.jetbrains.java.decompiler.main.decompiler.preprocessing.*;
import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider;
import org.jetbrains.java.decompiler.util.InterpreterUtil;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class FileBasedBytecodeProvider implements IBytecodeProvider {
  private static final Map<String, byte[]> processedClasses = new HashMap<>();

  @Override
  public byte[] getBytecode(String externalPath, String internalPath) throws IOException {
    if (processedClasses.containsKey(externalPath)) {
      return processedClasses.get(externalPath);
    }

    File file = new File(externalPath);
    if (internalPath == null) {
      var rawClsBytes = InterpreterUtil.getBytes(file);
      var processed = preprocessClass(rawClsBytes);

      processedClasses.put(externalPath, processed);
      return processed;
    } else {
      try (ZipFile archive = new ZipFile(file)) {
        ZipEntry entry = archive.getEntry(internalPath);
        if (entry == null) throw new IOException("Entry not found: " + internalPath);
        return InterpreterUtil.getBytes(archive, entry);
      }
    }
  }

  private byte[] preprocessClass(byte[] rawClsBytes) {
    var reader = new ClassReader(rawClsBytes);
    var node = new ClassNode();
    reader.accept(node, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
    var processors = new InstructionProcessor[0];

    for (var m : node.methods) {
      boolean processingFinished;
      do {
        processingFinished = true;
        ListIterator<AbstractInsnNode> iterator = m.instructions.iterator();
        while (iterator.hasNext()) {
          AbstractInsnNode ins = iterator.next();
          if (Arrays.stream(processors)
            .filter(p -> p.canProcess(ins, m, node))
            .anyMatch(p -> p.process(ins, m, node, iterator)) && processingFinished) {
            processingFinished = false;
          }
        }
      }
      while (!processingFinished);
    }

    var writer = new ClassWriter(0);

    node.accept(writer);
    return writer.toByteArray();
  }
}
