package com.example.procesareaimaginilor;

import java.awt.image.BufferedImage;

//clasa folosita pentru analiza unei imagini si retinerea rezultatelor analizei
public class AnalizaImagine {
    //variabilele clasei - indicatorii de analiza
    private double meanLuma; //media valorilor de luminanta
    private double stdLuma; //cat de mult variaza luminozitatea medie (contrastul)
    private double noiseScore; //cat zgomot are imaginea (puncte colorate, granulatii, pixeli )
    private double edgeDensity; //cat de multe muchii/cntururi are imaginea (daca e text avem mai multe muchii)
    private double blurVariance; //pentru a calcula claritatea imaginii, valoare mica = blurata
    private double brightRatio; //procent pixeli luminosi
    private double whiteRatio; //procent pixeli albi (documentele au de obicei procent mare de pixeli albi si mic de negrii)
    private double blackRatio; //procent pixeli negrii
    private boolean likelyDoc; //pentru a verifica daca e document (true) sau poza (false)
    //pentru a retined detaliile despre poza (ex: contrast mare, etc)
    private final String[] obs = new String[10];
    private int obsCount = 0;

    //metoda care primeste o poza, o analizeaza si initializeaza variabilele clasei
    public AnalizaImagine analiza(BufferedImage img){
        obsCount = 0;
        //micsoram imaginea pt o analiza mai precisa si mai rapida
        BufferedImage img_mica = OperatiiImagine.downscaleForAnalysis(img, 420);
        //calculam media si deviatia luminozitatii
        double[] stats = OperatiiImagine.computeLumaMeanStd(img_mica);
        meanLuma = stats[0];
        stdLuma = stats[1];
        noiseScore = OperatiiImagine.computeNoiseScore(img_mica);
        edgeDensity = OperatiiImagine.computeEdgeDensity(img_mica);
        blurVariance = OperatiiImagine.varianceOfLaplacian(img_mica); //ne bazam pe Lapclain, valoare mica = blur, valoare mare = clara
        //calculam pixelii luminosi, albi si negrii
        double[] pixeli = OperatiiImagine.brightWhiteBlackRatios(img_mica);
        brightRatio = pixeli[0];
        whiteRatio = pixeli[1];
        blackRatio = pixeli[2];
        //iau decizia daca e document sau poza
        likelyDoc =
                (edgeDensity > 0.10 && whiteRatio > 0.30)
                        || (whiteRatio > 0.65 && edgeDensity > 0.04)
                        || (whiteRatio > 0.40 && blackRatio > 0.010 && edgeDensity > 0.06);
        addObsAnaliza(String.format("Analiza: mean luma (media valorilor de luminanta si contrastul)=%.1f, std=%.1f", meanLuma, stdLuma));
        addObsAnaliza(String.format("NoiseScore/Zgomotul imaginii=%.3f, EdgeDensity/Densitatea muchiilor=%.3f", noiseScore, edgeDensity));
        addObsAnaliza(String.format("BlurVar(Laplacian)=%.1f", blurVariance));
        addObsAnaliza(String.format("brightRatio=%.3f, whiteRatio=%.3f, blackRatio=%.3f", brightRatio, whiteRatio, blackRatio));
        addObsAnaliza(likelyDoc ? "Tip: probabil DOCUMENT" : "Tip: probabil FOTO");
        return this;
    }
    //metoda folosita pentru a adauga o observatie despre poza
    private void addObsAnaliza(String s){
        if (obsCount < obs.length){
            obs[obsCount++] = s;
        }
    }

    //metode get
    public double getMeanLuma() { return meanLuma; }
    public double getStdLuma() { return stdLuma; }
    public double getNoiseScore() { return noiseScore; }
    public double getBlurVariance() { return blurVariance; }
    public double getBrightRatio() { return brightRatio; }
    public boolean isLikelyDocument() { return likelyDoc; }
    public String[] getObs() {
        String [] rez = new String[obsCount];
        for (int i = 0; i < obsCount; i++) {
            rez[i] = obs[i];
        }
        return rez;
    }
}
