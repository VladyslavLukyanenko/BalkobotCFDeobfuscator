package me.deob;

import com.github.javaparser.ParseException;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import org.jetbrains.java.decompiler.main.decompiler.BalkoDecompiler;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Main {
  private static final Predicate<String> validFilePredicate = Pattern.compile("\\.java").asPredicate();
  public static void copyFolder(Path source, Path target, CopyOption... options)
      throws IOException {
    Files.walkFileTree(source, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
          throws IOException {
        Files.createDirectories(target.resolve(source.relativize(dir)));
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
          throws IOException {
        Files.copy(file, target.resolve(source.relativize(file)), options);
        return FileVisitResult.CONTINUE;
      }
    });
  }

  public static void main(String[] args) throws IOException, ParseException, InterruptedException {
    if (args.length != 2) {
      System.err.println("2 args required. 1 - installation dir, 2 - save results dir");
      System.out.println("Started with args:");
      for (var a : args) {
        System.out.println(a);
      }

      if (args.length == 0) {
        System.out.println("<No args>");
      }

      Thread.sleep(5000);
      System.exit(-1);
    }



    final Path installDir = Paths.get(args[0]);
    final Path saveResultsDir = Paths.get(args[1]);

    final String appClassesDirName = "Balkobot";
    final String libsDirName = "lib";
    final Path tempDir = Files.createTempDirectory("deobf");
    final Path appClassesTmpDir = tempDir.resolve(appClassesDirName);
    final Path libTmpDir = tempDir.resolve(libsDirName);
    final Path appDecompiledClassesDir = tempDir.resolve("decompiledSources");

    // copy app classes, lib files
    final Path appClassesDir = installDir.resolve(appClassesDirName);
    final Path appLibsDir = installDir.resolve(libsDirName);
    final Path appPostprocessedClassesDir = saveResultsDir.resolve("src/main/java");

    copyFolder(appClassesDir, appClassesTmpDir);
    copyFolder(appLibsDir, libTmpDir);

    if (!Files.exists(appDecompiledClassesDir)) {
      appDecompiledClassesDir.toFile().mkdirs();
    }

    if (!Files.exists(saveResultsDir)) {
      saveResultsDir.toFile().mkdirs();
    }

    if (!Files.exists(appPostprocessedClassesDir)) {
      appPostprocessedClassesDir.toFile().mkdirs();
    }

    var projectSkeleton = ClassLoader.getSystemClassLoader().getResourceAsStream("ProjectSkeleton.zip");
    unzip(projectSkeleton, saveResultsDir);
    BalkoDecompiler.decompile(appClassesTmpDir.toString(), appDecompiledClassesDir.toString());
    postProcess(appDecompiledClassesDir.toString(), appPostprocessedClassesDir.toString());
  }


  public static void unzip(InputStream zipFile, Path outputPath){
    try (ZipInputStream zis = new ZipInputStream(zipFile)) {

      ZipEntry entry = zis.getNextEntry();

      while (entry != null) {
        Path newFilePath = outputPath.resolve(entry.getName());
        if (entry.isDirectory()) {
          Files.createDirectories(newFilePath);
        } else {
          if(!Files.exists(newFilePath.getParent())) {
            Files.createDirectories(newFilePath.getParent());
          }
          try (OutputStream bos = Files.newOutputStream(outputPath.resolve(newFilePath))) {
            byte[] buffer = new byte[Math.toIntExact(entry.getSize())];

            int location;

            while ((location = zis.read(buffer)) != -1) {
              bos.write(buffer, 0, location);
            }
          }
        }
        entry = zis.getNextEntry();
      }
    }catch(IOException e){
      throw new RuntimeException(e);
      //handle your exception
    }
  }

  private static void postProcess(String sourcesPath, String postprocessedPath) throws IOException {
    CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
    combinedTypeSolver.add(new ReflectionTypeSolver());
    combinedTypeSolver.add(new JavaParserTypeSolver(new File(sourcesPath)));

    // Configure JavaParser to use type resolution
    JavaSymbolSolver symbolSolver = new JavaSymbolSolver(combinedTypeSolver);
    StaticJavaParser.getConfiguration()
        .setSymbolResolver(symbolSolver);

    Class<class_0> clazz0 = class_0.class;
    var cls0Fields = new HashMap<String, Integer>();

    for (var f : clazz0.getDeclaredFields()) {
      f.setAccessible(true);
      try {
        var val = (Integer) f.get(null);
        cls0Fields.put(f.getName(), val);
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }
    var sourceFiles = new ArrayList<File>();
    Files.walkFileTree(Paths.get(sourcesPath), new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        final String filePath = file.toAbsolutePath().toString();
        if (validFilePredicate.test(filePath)
            && !filePath.contains("ShopifyBackend.class")
            && !filePath.contains("ShopifySafe.class")
            && !filePath.contains("Aes.class")
            && !filePath.contains("FFX.class")
            && !filePath.contains("SDW.class")
            && !filePath.contains("CMAC.class")) {
          final File classFile = file.toFile();
          sourceFiles.add(classFile);
        }

        return super.visitFile(file, attrs);
      }
    });

    var isInnerClassPred = Pattern.compile("public (interface|enum|class) .+\\$.+", Pattern.MULTILINE).asPredicate();
    var problematicFiles = new HashMap<File, Exception>();
    var failures = Paths.get(postprocessedPath).resolve("failures");
    for (var f : sourceFiles) {
      var dst = f.getAbsolutePath().replace(sourcesPath, postprocessedPath);
      final var outputFilePath = Paths.get(dst);
      var clsName = f.getAbsolutePath()
          .replace(sourcesPath, "")
          .replace("\\", "/")
          .replace("/", ".");
      if (clsName.startsWith(".")) {
        clsName = clsName.substring(1);
      }

      var outputDir = outputFilePath.getParent();
      outputDir.toFile().mkdirs();

      var rawContent = Files.readString(f.toPath());
//      if (rawContent.contains("public enum ")) {
//        Files.writeString(outputPath, rawContent);
//        System.out.println("ENUM copied as is: " + clsName);
//        continue;
//      }

//      if (rawContent.contains("public interface ")) {
//        Files.writeString(outputPath, rawContent);
//        System.out.println("INTERFACE copied as is: " + clsName);
//        continue;
//      }
      try {
        var start = System.currentTimeMillis();
        CompilationUnit cu = StaticJavaParser.parse(f);
        Processors.processUnit(cls0Fields, cu);
        System.out.println("Processed: " + clsName + ", in " + (System.currentTimeMillis() - start) + "ms");
        Files.writeString(outputFilePath, cu.toString());

      } catch (ParseProblemException pe) {
        if (rawContent.contains("public interface ") || isInnerClassPred.test(rawContent)) {
          Files.writeString(outputFilePath, rawContent);
          problematicFiles.put(f, pe);
          System.err.println("ERR: " + f);
        } else {
//          System.err.println(f);
//          throw pe;
          Files.writeString(outputFilePath, rawContent);
          problematicFiles.put(f, pe);
          System.err.println("ERR: " + f);
        }
      } catch (Exception exc) {
//        var dst = Paths.get(f.getAbsolutePath().replace(SOURCES_PATH, failures.toString()));
//        dst.getParent().toFile().mkdirs();
//        Files.writeString(dst, Files.readString(f.toPath()));
        Files.writeString(outputFilePath, rawContent);
        problematicFiles.put(f, exc);
        System.err.println("ERR: " + f);
      }
    }

    // Parse some code
    System.out.println("DONE");
    if (problematicFiles.size() > 0) {
      System.out.println("ERRORS: " + problematicFiles.size());
      for (var e : problematicFiles.entrySet()) {
        var f = e.getKey();
        var clsName = f.getAbsolutePath()
            .replace(sourcesPath, "")
            .replace("\\", "/")
            .replace("/", ".");
        if (clsName.startsWith(".")) {
          clsName = clsName.substring(1);
        }

        final var exc = e.getValue();
        System.out.printf("%s: %s%n%n", clsName, exc.getMessage());
      }
    }
  }
}

