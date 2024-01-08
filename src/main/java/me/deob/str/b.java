package me.deob.str;

public class b {
   public static String indexOf(int int0, String string1) {
      int int10000 = 4;
      int int10001 = 4;
      byte byte6 = -1;
      int int8 = byte6 + 1;
      String string7 = "0"/*0,1,48*/;
      int int10002;
      switch(Integer.parseInt(string7)) {
      case 0:
         int10001 = 5;
         int8 += 13;
         string7 = "12"/*0,2,49,50*/;
         break;
      default:
         int8 += 15;
         int10002 = 0;
      }

      byte byte10;
      char[] char10003;
      switch(int8) {
      case 0:
         int8 += 5;
         byte10 = 1;
         char10003 = null;
         break;
      default:
         byte10 = 0;
         char10003 = string1.toCharArray();
         int8 = 0;
         string7 = "0"/*0,1,48*/;
      }

      int int10004;
      switch(Integer.parseInt(string7)) {
      case 0:
         int10004 = char10003.length;
         int8 += 12;
         string7 = "12"/*0,2,49,50*/;
         break;
      default:
         int8 += 5;
         int10004 = 1;
      }

      char[] char3;
      int int4;
      switch(int8) {
      case 0:
         int8 += 4;
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
         int10000 = 4 << int10001;
         int8 += 11;
         string7 = "12"/*0,2,49,50*/;
         break;
      default:
         int8 += 6;
         int2 = 1;
      }

      switch(int8) {
      case 0:
         int8 += 5;
         int10001 = 0;
         break;
      default:
         int10000 += -1;
         boolean boolean9 = false;
         string7 = "0"/*0,1,48*/;
      }

      for(int int5 = int10000 ^ 32; int2 != int4; char3[int10001] = (char)int10002) {
         int10001 = int2;
         int10002 = int0 & int5 ^ char3[int2];
         ++int0;
         ++int2;
      }

      return String.valueOf(char3, 0, int4).intern();
   }
}
