package org.jetbrains.java.decompiler.main.decompiler;

import org.jetbrains.java.decompiler.main.extern.IResultSaver;

import java.util.jar.Manifest;

public class NoopResultSaver implements IResultSaver {
  @Override
  public void saveFolder(String path) {
    System.out.println("Saved: " + path);
  }

  @Override
  public void copyFile(String source, String path, String entryName) {
    System.out.printf("copyFile source '%s', path '%s', entryName '%s'\n", source, path, entryName);
  }

  @Override
  public void saveClassFile(String path, String qualifiedName, String entryName, String content, int[] mapping) {
    System.out.println("saveClassFile");
    System.out.println(qualifiedName);
    System.out.println(entryName);
    System.out.println(content);
  }

  @Override
  public void createArchive(String path, String archiveName, Manifest manifest) {
    System.out.println("createArchive");
  }

  @Override
  public void saveDirEntry(String path, String archiveName, String entryName) {
    System.out.println("saveDirEntry");
  }

  @Override
  public void copyEntry(String source, String path, String archiveName, String entry) {
    System.out.println("copyEntry");
  }

  @Override
  public void saveClassEntry(String path, String archiveName, String qualifiedName, String entryName, String content) {
    System.out.println("saveClassEntry");
  }

  @Override
  public void closeArchive(String path, String archiveName) {
    System.out.println("closeArchive");
  }
}
