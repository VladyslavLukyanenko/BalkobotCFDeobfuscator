package me.deob.str;

public class e {
   public static String concat(String string0, int int1) {
      int int10000 = 2;
      int int10001 = 2;
      byte byte6 = -1;
      int int8 = byte6 + 1;
      String string7 = "0"/*0,1,48*/;
      int int10002;
      switch(Integer.parseInt(string7)) {
      case 0:
         int10000 = 4;
         int10001 = 4;
         int8 += 8;
         string7 = "26"/*0,2,50,54*/;
         break;
      default:
         int8 += 5;
         int10002 = 231;
      }

      switch(int8) {
      case 0:
         int8 += 14;
         int10002 = 1;
         break;
      default:
         int10002 = int10001;
         int10001 = 1;
         int8 = 0;
         string7 = "0"/*0,1,48*/;
      }

      byte byte10;
      switch(Integer.parseInt(string7)) {
      case 0:
         int10001 += int10002;
         byte10 = 0;
         int8 += 2;
         string7 = "26"/*0,2,50,54*/;
         break;
      default:
         int8 += 14;
         byte10 = 0;
      }

      char[] char10003;
      switch(int8) {
      case 0:
         int8 += 8;
         byte10 = 1;
         char10003 = null;
         break;
      default:
         char10003 = string0.toCharArray();
         int8 = 0;
         string7 = "0"/*0,1,48*/;
      }

      int int10004;
      switch(Integer.parseInt(string7)) {
      case 0:
         int10004 = char10003.length;
         int8 += 11;
         string7 = "26"/*0,2,50,54*/;
         break;
      default:
         int8 += 5;
         int10004 = 1;
      }

      char[] char3;
      int int4;
      switch(int8) {
      case 0:
         int8 += 5;
         int4 = 1;
         char3 = null;
         break;
      default:
         int4 = int10004;
         char3 = char10003;
         int8 = 0;
         string7 = "0"/*0,1,48*/;
      }

      int int2;
      switch(Integer.parseInt(string7)) {
      case 0:
         int2 = byte10;
         int10000 <<= int10001;
         int8 += 2;
         string7 = "26"/*0,2,50,54*/;
         break;
      default:
         int8 += 5;
         int2 = 1;
      }

      switch(int8) {
      case 0:
         int8 += 7;
         int10001 = 4;
         break;
      default:
         int10000 += -1;
         boolean boolean9 = false;
         string7 = "0"/*0,1,48*/;
      }

      for(int int5 = int10000 ^ 32; int2 != int4; char3[int10001] = (char)int10002) {
         int10001 = int2;
         int10002 = char3[int2] ^ int1 & int5;
         ++int1;
         ++int2;
      }

      return String.valueOf(char3, 0, int4).intern();
   }
}
