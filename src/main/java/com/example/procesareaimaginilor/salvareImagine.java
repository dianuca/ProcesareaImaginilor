package com.example.procesareaimaginilor;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

public class salvareImagine {
    //img - img procesata, outFile - fisierul unde salvam, format - formatul jpg/png
    public static void saveImage(BufferedImage img, File outFile, String format) throws  Exception{
        String fmt = format.toLowerCase(); //ma asigur ca formatul e scris lowcapse
        BufferedImage toWrite = img; //initial imaginea scrisa e img
        if ("jpg".equals(fmt) || "jpeg".equals(fmt)) {
            toWrite = toRGB(img); //creez o imagine fara alpha cu fundalul alb
            ImageIO.write(toWrite, "jpg", outFile);
            return;
        }
        else
            ImageIO.write(toWrite, fmt, outFile); //deoarece PNG suporta transparenta
    }
    //functie pt a transforma o imagine cu transparenta ARGB intr-una fara (RGB)
    public static BufferedImage toRGB(BufferedImage img) {
        BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        //type_int_rgb -> 24 biti/pixel (8 rosii, 8 verde, 8 albastru)
        Graphics2D g = rgb.createGraphics();
        //umplem cu fundal alb deoarece JPG nu are transparenta (contrar, zonele transparente devin negre)
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        //desenam imaginea
        g.drawImage(img, 0, 0, null);
        g.dispose(); //eliberam resursele grafice
        return rgb;
    }
    //metoda pt a convertii formatul ales intr-o ext corecta
    public static String extensie(String fmt){
        if ("jpeg".equals(fmt)) return "jpg";
        return fmt;
    }
}
