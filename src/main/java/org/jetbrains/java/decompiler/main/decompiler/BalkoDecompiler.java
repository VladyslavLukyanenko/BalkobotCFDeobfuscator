package org.jetbrains.java.decompiler.main.decompiler;

import org.jetbrains.java.decompiler.main.Fernflower;
import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;


public class BalkoDecompiler {
  public static void decompile(final String source, final String output) throws IOException {
    final Predicate<String> validFilePredicate = Pattern.compile("\\.class").asPredicate();

    PrintStreamLogger logger = new PrintStreamLogger(System.out);
    var options = new HashMap<>(IFernflowerPreferences.DEFAULTS);
    options.put(IFernflowerPreferences.IGNORE_INVALID_BYTECODE, "1");
    options.put(IFernflowerPreferences.RENAME_ENTITIES, "1");
    options.put(IFernflowerPreferences.USER_RENAMER_CLASS, "org.jetbrains.java.decompiler.main.decompiler.InvalidNamesIdentifierRenamer");
    IBytecodeProvider provider = new FileBasedBytecodeProvider();
    IResultSaver saver = new NoopResultSaver();
    var accessor = new FernFlowerAccessor(provider, saver, options, logger);

    var ffw = new Fernflower(provider, new RegularResultSaver(Paths.get(output)), options, logger);
    var sourceFiles = new ArrayList<File>();
    Files.walkFileTree(Paths.get(source), new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        final String filePath = file.toAbsolutePath().toString();
        if (validFilePredicate.test(filePath) && (!filePath.contains("Aes.class") && !filePath.contains("FFX.class"))) {
          final File classFile = file.toFile();
          sourceFiles.add(classFile);
        }

        return super.visitFile(file, attrs);
      }
    });

    sourceFiles.sort((l, r) -> {
      int weight1 = l.getName().contains("$") ? 1 : 0;
      int weight2 = r.getName().contains("$") ? 1 : 0;
      return weight1 - weight2;
    });

    for (File classFile : sourceFiles) {
      accessor.addSource(classFile);
      ffw.addSource(classFile);
    }

    ffw.decompileContext();
  }


  /**
   * Tag used to demarcate an ordinary argument.
   */
  private static final char TAG_ARG = '\u0001';

  /**
   * Tag used to demarcate a constant.
   */
  private static final char TAG_CONST = '\u0002';
  private static List<String> parseRecipe(String recipe,
                                          Object[] constants)
  {

    Objects.requireNonNull(recipe, "Recipe is null");
    // Element list containing String constants, or null for arguments
    List<String> elements = new ArrayList<>();

    int cCount = 0;
    int oCount = 0;

    StringBuilder acc = new StringBuilder();

    for (int i = 0; i < recipe.length(); i++) {
      char c = recipe.charAt(i);

      if (c == TAG_CONST) {
        if (cCount == constants.length) {
          // Not enough constants
          throw new RuntimeException();
        }
        // Accumulate constant args along with any constants encoded
        // into the recipe
        acc.append(constants[cCount++]);
      } else if (c == TAG_ARG) {
        // Flush any accumulated characters into a constant
        if (acc.length() > 0) {
          elements.add(acc.toString());
          acc.setLength(0);
        }
        elements.add(null);
        oCount++;
      } else {
        // Not a special character, this is a constant embedded into
        // the recipe itself.
        acc.append(c);
      }
    }

    // Flush the remaining characters as constant:
    if (acc.length() > 0) {
      elements.add(acc.toString());
    }

    return elements;
  }
}
