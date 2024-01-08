// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.util;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

public class DataInputFullStream extends DataInputStream {
  private final PosAwareByteArrayInputStream bytes;

  public DataInputFullStream(byte[] bytes) {
    super(new PosAwareByteArrayInputStream(bytes));
    this.bytes = (PosAwareByteArrayInputStream) in;
  }

  public int getPos() {
    return bytes.getPos();
  }

  public byte[] readAtOffset(int offset, int count) {
    byte[] slice = new byte[count];
    System.arraycopy(bytes.getBuffer(), offset, slice, 0, count);

    return slice;
  }

  public byte[] read(int n) throws IOException {
    return InterpreterUtil.readBytes(this, n);
  }

  public void discard(int n) throws IOException {
    InterpreterUtil.discardBytes(this, n);
  }

  private static class PosAwareByteArrayInputStream extends ByteArrayInputStream {

    public PosAwareByteArrayInputStream(byte[] buf) {
      super(buf);
    }

    public byte[] getBuffer() {
      return this.buf;
    }

    public int getPos() {
      return pos;
    }
  }
}