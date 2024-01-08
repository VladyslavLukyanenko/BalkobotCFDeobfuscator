// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.struct.attr;

import org.jetbrains.java.decompiler.struct.consts.ConstantPool;
import org.jetbrains.java.decompiler.util.DataInputFullStream;

import java.io.IOException;

public class StructConstantValueAttribute extends StructGeneralAttribute {

  private int index;

  @Override
  public void initContent(DataInputFullStream data, ConstantPool pool) throws IOException {
    index = data.readUnsignedShort();
  }

  public int getIndex() {
    return index;
  }

  @Override
  public boolean isLengthValid(int attLength) {
    // NOTICE: regarding: https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.7.2
    // length must be equals to 2
    return attLength == 2;
  }

  @Override
  public boolean shouldInitContentOnInvalidLen() {
    return true;
  }
}
