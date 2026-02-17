package com.example.procesareaimaginilor;

import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import java.awt.image.BufferedImage;


public class conversieImagine {
    //folosita pt a convertii o imagine BufferedImage intr-o imagine WritableImage
    public static WritableImage toWrite(BufferedImage img) {
        int w = img.getWidth(); //latime imagine
        int h = img.getHeight(); //inaltime imagine
        //creez outputul JavaFX
        WritableImage out = new WritableImage(w, h); //imagine goala la inceput
        PixelWriter pw = out.getPixelWriter(); //obiectul cu care scriu pixeli in out

        //folosesc un buffer pt un rand de pixeli
        int[] rand = new int[w]; //vectir care tine un rand intreg de pixeli
        for (int y = 0; y < h; y++) { //parcurg fiecare rand al imaginii, de sus in jos
            //citim un rand din BufferedImage
            img.getRGB(0,y,w,1,rand,0,w);
            /**
             0, y -> de unde incep (col 0, rand y)
             w,1 -> cat citesc (latimea w, inaltimea 1 rand)
             rand -> unde pun pixelii
             0 -> offset in array
             2 -> cati pixeli pe rand in array
             **/
            pw.setPixels(0,y,w,1,PixelFormat.getIntArgbInstance(),rand,0,w); //scriu pixelii din rand in out pe randul y
        }
        return out;
    }
}
