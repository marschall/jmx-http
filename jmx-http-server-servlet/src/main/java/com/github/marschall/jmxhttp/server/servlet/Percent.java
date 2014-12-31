package com.github.marschall.jmxhttp.server.servlet;

public class Percent {

  public static void main(String[] args) {
    float uncompressedSize = 8.0f;
    float compressedSize = 7.0f;
    System.out.printf("%f %%%n", compressedSize / uncompressedSize * 100.0f);

    uncompressedSize = 8.0f;
    compressedSize = 1.0f;
    System.out.printf("%f %%%n", compressedSize / uncompressedSize * 100.0f);

    uncompressedSize = 47f;
    compressedSize = 68f;
    System.out.printf("%f %%%n", compressedSize / uncompressedSize * 100.0f);

    uncompressedSize = 20f;
    compressedSize = 40f;
    System.out.printf("%f %%%n", compressedSize / uncompressedSize * 100.0f);
  }

}
