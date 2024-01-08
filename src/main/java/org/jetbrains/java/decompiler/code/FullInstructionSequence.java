// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.code;

import org.jetbrains.java.decompiler.util.VBStyleCollection;

import java.util.List;


public class FullInstructionSequence extends InstructionSequence {

  // *****************************************************************************
  // constructors
  // *****************************************************************************

  public FullInstructionSequence(VBStyleCollection<Instruction, Integer> collinstr, ExceptionTable extable) {
    super(collinstr);
    this.exceptionTable = extable;

    // translate raw exception handlers to instr
    List<ExceptionHandler> handlers = extable.getHandlers();
    for (int i = 0; i < handlers.size(); i++) {
      ExceptionHandler handler = handlers.get(i);
      handler.from_instr = this.getPointerByAbsOffset(handler.from);
      handler.to_instr = this.getPointerByAbsOffset(handler.to);
      handler.handler_instr = this.getPointerByAbsOffset(handler.handler);

      if (handler.to_instr < 0) {
        handlers.remove(handler);
      }
    }
  }
}
