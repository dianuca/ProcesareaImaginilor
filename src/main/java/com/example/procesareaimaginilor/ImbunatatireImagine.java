package com.example.procesareaimaginilor;

import java.awt.image.BufferedImage;

public class ImbunatatireImagine {

    private BufferedImage outputImg; //imagine modificata
    private final String[] detalii = new String[40]; //detalii despre ce modificari i au fost aduse imaginii
    private int detaliiCount = 0; //numarul de detalii adaugate

    //variabile care nu isi schimba valoarea
    private static final double DARK_MEAN = 100; //sub acest prag imaginea e considerata intunecata
    private static final double LOW_CONTRAST_STD = 28; //contrast mic (plata)
    private static final double VERY_LOW_CONTRAST_STD = 22; // constrast si mai mic, foarte plata
    private static final double NOISY_SCORE = 0.018;
    private static final double VERY_NOISY_SCORE = 0.035; //mai mare de 35%
    private static final double OVEREXPOSED_BRIGHT_RATIO = 0.028; // daca e un procent mai mare => imagine supraexpusa
    private static final double BLURRY_VAR = 150; //sub 400 => imaginea are blur mare
    private static final double SOFT_VAR = 300 ; //peste 150, imaginea e clara

    //parametrii : imaginea de output si imaginea care a fost deja analizata
    public void imbunatatire(BufferedImage input, AnalizaImagine img) {
        outputImg = input;
        detaliiCount = 0;
        boolean document = img.isLikelyDocument(); //pentru a verifica daca este document
        BufferedImage imgAux = input; //voi lucra pe o imagine auxiliara deoarece o tot modific

        imgAux = zgomot(imgAux, img, document);
        imgAux = gammaCorectie(imgAux, img, document);
        imgAux = exposureSiContrast(imgAux, img, document);
        imgAux = whiteBalance(imgAux, img, document);
        imgAux = sharpen(imgAux, img, document);
        imgAux = documentBinar(imgAux, img, document);
        outputImg = imgAux;
    }

    //imgAux - imaginea dupa modificari, img - analiza imaginii initiale
    private BufferedImage zgomot(BufferedImage imgAux, AnalizaImagine img, boolean document) {
        if (img.getNoiseScore() > VERY_NOISY_SCORE){ // peste 35% => are zgomot foarte mare
            //verificam daca e document sau foto, adaugam mesajul si modificam imaginea
            if (document){
                addDetalii(String.format("Zgomot MARE (%.3f) + DOCUMENT => Gaussian 3x3 aplicat de doua ori.",img.getNoiseScore()));
                imgAux = OperatiiImagine.gaussianBlur3x3(imgAux);
                imgAux = OperatiiImagine.gaussianBlur3x3(imgAux);
            }
            else{
                addDetalii(String.format("Zgomot MARE (%.3f) => Median 3x3 aplicat de doua ori.", img.getNoiseScore()));
                imgAux = OperatiiImagine.medianBlur3x3(imgAux);
                imgAux = OperatiiImagine.medianBlur3x3(imgAux);
            }
        }
        else if (img.getNoiseScore() > NOISY_SCORE){ //intre 18 si 35% => zgomot mediu
            if (document){
                addDetalii(String.format("Zgomot MEDIU (%.3f) + DOCUMENT => Gussian 3x3 aplicat o data", img.getNoiseScore()));
                imgAux = OperatiiImagine.gaussianBlur3x3(imgAux);
            }
            else{
                addDetalii(String.format("Zgomot MEDIU (%.3f) => Median 3x3 aplicat o data", img.getNoiseScore()));
                imgAux = OperatiiImagine.medianBlur3x3(imgAux);
            }
        }
        else if (img.getNoiseScore() > NOISY_SCORE * 0.75){ //daca avem un zgomot mic, aplicam un blur mai soft
            addDetalii(String.format("Zgomot MIC (%.3f) => Gussian 3x3 (soft)", img.getNoiseScore()));
            imgAux = OperatiiImagine.gaussianBlur3x3(imgAux);
        }
        else
            addDetalii(String.format("Zgomot OK (%.3f) => Fara blur", img.getNoiseScore()));
        return imgAux;
    }
    //pentru corectarea expunerii
    private BufferedImage gammaCorectie(BufferedImage imgAux, AnalizaImagine img, boolean document) {
        if (document) return imgAux;
        double gamma = 1.0; //initializare gama, 1 = fara schimbari
        // Caz 1: prea luminoasa (highlight-uri multe) => intunecam pic (gamma > 1)
        if (img.getBrightRatio() > OVEREXPOSED_BRIGHT_RATIO) { //imagine supraexpusa
            double diferenta = img.getBrightRatio() - OVEREXPOSED_BRIGHT_RATIO; //diferenta dintre praguri
            double severitate = diferenta /(1.0-OVEREXPOSED_BRIGHT_RATIO); //coeficient intre 0 si 1, care exprima cat de grava e supraexpunerea
            //0.3 ->putin luminoasa, 1->imagine complet supraexpusa
            gamma = 1.10 + severitate*0.25; //valoare intre 1.10 si 1.35
            addDetalii(String.format(
                    "Gamma: imagine supraexpusa (brightRatio=%.3f) => gamma=%.2f",
                    img.getBrightRatio(), gamma));
            return OperatiiImagine.gammaCorrection(imgAux, gamma);
        }

        // Caz 2: prea intunecata => luminam (micsorez gama)
        if (img.getMeanLuma() < DARK_MEAN) { //meanLuma - luminozitatea medie
            double diferenta = DARK_MEAN - img.getMeanLuma(); // diferenta fata de pragul acceptabil
            double severitate = diferenta / DARK_MEAN; //coeficient normalizat (0-1) care exprima cat de grava este subexpunere
            //ex: 0.0 -> imagine ok, 0.3 -> putin intunecata, 1 -> imagine complet subexpusa
            gamma = 0.95 - severitate*0.35; //gama variaza intre 0.95 si 0.60
            // daca e si contrast foarte mic, luminam putin mai agresiv
            if (img.getStdLuma() < VERY_LOW_CONTRAST_STD) {
                gamma = Math.max(0.55, gamma - 0.08);
            }
            addDetalii(String.format(
                    "Gamma: imagine subexpusa (mean=%.1f, std=%.1f) => gamma=%.2f",
                    img.getMeanLuma(), img.getStdLuma(), gamma));
            return OperatiiImagine.gammaCorrection(imgAux, gamma);
        }

        // Daca e ok, nu aplicam
        addDetalii(String.format(
                "Gamma: OK (mean=%.1f, brightRatio=%.3f) => fara corectie",
                img.getMeanLuma(), img.getBrightRatio()));

        return imgAux;
    }
    private BufferedImage exposureSiContrast(BufferedImage imgAux, AnalizaImagine img, boolean document) {
        if (document) return imgAux;
        double brightness = 0; //neutru
        double contrast = 1; //neutru
        //imagine prea luminoasa
        if (img.getBrightRatio() > OVEREXPOSED_BRIGHT_RATIO){ //prea multi pixeli albi
            double diferenta = img.getBrightRatio() - OVEREXPOSED_BRIGHT_RATIO;
            double severitate = diferenta / (1.0-OVEREXPOSED_BRIGHT_RATIO);
            brightness = -12-severitate*28; //scad luminozitatea, vreau sa fie in intervalul [-12,-40]
            contrast = 1-severitate*0.08; //scad contrastul, vreau sa fie intre [1.00, 0.92]
            addDetalii(String.format("Prea luminoasa (brightRatio=%.3f) => luminozitate %.0f, contrast x%.2f",img.getBrightRatio(),brightness,contrast));
            return OperatiiImagine.adjustBrightnessContrast(imgAux, brightness, contrast); //aplicam asupra imaginii brightness ul si contrastul
        }
        //imagine intunecata (luminozitatea medie e mica) , cresc luminozitatea
        if (img.getMeanLuma() < DARK_MEAN){
            double diferenta = DARK_MEAN - img.getMeanLuma(); //diferenta fata de pragul optim
            double severitate = diferenta/DARK_MEAN; //normalizam intre valori [0,1] cat de "grava" e diferenta
            brightness = severitate*65.0; //in intervalul [0,65]
            if (img.getStdLuma() < VERY_LOW_CONTRAST_STD) contrast = 1.22;
            else if (img.getStdLuma() < LOW_CONTRAST_STD) contrast = 1.15;
            else contrast = 1.10;
            addDetalii(String.format(
                    "Intunecata (mean=%.1f, std=%.1f) => brightness %+.0f, contrast x%.2f",
                    img.getMeanLuma(), img.getStdLuma(), brightness, contrast));
            return OperatiiImagine.adjustBrightnessContrast(imgAux, brightness, contrast);
        }
        //contrast foarte mic, imagine plata
        if (img.getStdLuma() < VERY_LOW_CONTRAST_STD){
            brightness = 6;
            contrast = 1.18;
            addDetalii(String.format(
                    "Contrast FOARTE MIC (std=%.1f) => brightness %+.0f, contrast x%.2f",
                    img.getStdLuma(), brightness, contrast));
            return OperatiiImagine.adjustBrightnessContrast(imgAux, brightness, contrast);
        }
        //contrast mic
        if (img.getStdLuma() < LOW_CONTRAST_STD){
            brightness = 4;
            contrast = 1.12;
            addDetalii(String.format(
                    "Contrast MIC (std=%.1f) => brightness %+.0f, contrast x%.2f",
                    img.getStdLuma(), brightness, contrast));
            return OperatiiImagine.adjustBrightnessContrast(imgAux, brightness, contrast);
        }
        //daca nu se incadreaza in niciun caz de mai sus, inseamna ca este ok contrastul
        addDetalii(String.format("Contrastul este OK (mean=%.1f, std=%.1f) => fara ajustare",img.getMeanLuma(), img.getStdLuma()));
        return imgAux;
    }
    private BufferedImage whiteBalance(BufferedImage imgAux, AnalizaImagine img, boolean document) {
        if (document) return imgAux;
        BufferedImage small = OperatiiImagine.downscaleForAnalysis(imgAux,420);
        double[] m = OperatiiImagine.meanRGB(small);
        if (OperatiiImagine.hasColorCast(m[0], m[1], m[2])){ //verifica daca un canal deviaza >8% fata de media totala(gri)
            addDetalii(String.format("Color balance: meanRGB=%.1f/%.1f/%.1f => aplic gray-world",m[0],m[1],m[2]));
            return OperatiiImagine.grayWorldWhiteBalance(imgAux, 0.55);
        }
        //in cazul in care imaginea e echilibrata
        addDetalii(String.format("Color Balance OK (meanRGB=%.1f/%.1f/%.1f)", m[0],m[1],m[2]));
        return imgAux;
    }
    //sharpen
    private BufferedImage sharpen(BufferedImage imgAux, AnalizaImagine img, boolean document) {
        if (document){
            addDetalii("DOCUMENT => nu aplic sharpen");
            return imgAux;
        }
        //imagine clara
        if (img.getBlurVariance() >= BLURRY_VAR){
            addDetalii(String.format("Fara blur (%.1f) => Nu aplic sharpen",img.getBlurVariance()));
            return imgAux;
        }
        //daca e blurata => sharpen, dar depinde de contrast
        if (img.getStdLuma() < LOW_CONTRAST_STD && img.getBlurVariance() < SOFT_VAR){
            addDetalii(String.format("Imagine FOARTE plata + blurata, aplic sharpen de doua ori"));
            imgAux = OperatiiImagine.sharpen(imgAux);
            imgAux = OperatiiImagine.sharpen(imgAux);
            return imgAux;
        }
        if (img.getStdLuma() < LOW_CONTRAST_STD){
            addDetalii(String.format("Blurata + putin plata (%.1f , %.1f) => sharpen aplicat o data", img.getBlurVariance(), img.getStdLuma()));
            imgAux = OperatiiImagine.sharpen(imgAux);
            return imgAux;
        }
        addDetalii(String.format("Blurata, dar contras ok => sharpen aplicat o data"));
        return OperatiiImagine.sharpen(imgAux);
    }
    //document
    private BufferedImage documentBinar(BufferedImage imgAux, AnalizaImagine img, boolean document) {
        if (!document){
            addDetalii("Foto => pastrez color");
            return imgAux;
        }
        if (img.getStdLuma() < 40 && img.getMeanLuma() > 120){
            addDetalii("Document => aplic mic boost de contrast");
            imgAux = OperatiiImagine.adjustBrightnessContrast(imgAux, 0, 1);
        }
        addDetalii("Document => Threshold Otsu");
        return OperatiiImagine.otsuThresholdToBinary(imgAux);
    }

    public BufferedImage getOutputImg(){
        return outputImg;
    }
    public String[] getDetalii(){
        String[] rez = new String[detaliiCount];
        for (int i = 0; i < detaliiCount; i++){
            rez[i] = detalii[i];
        }
        return rez;
    }
    public void addDetalii(String s){
        if (detaliiCount < detalii.length) {
            detalii[detaliiCount++] = s;
        }
    }
}
