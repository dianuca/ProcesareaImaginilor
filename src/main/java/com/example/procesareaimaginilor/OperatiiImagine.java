package com.example.procesareaimaginilor;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.*;
import java.util.Arrays;

//primeste un BufferedImage, calculeaza indicatorii si aplica diferite operatii
public class OperatiiImagine {

    //in imagine, valorile trebuie sa fie intre 0 si 255
    //daca obtinem mai mult de 255, facem 255, la fel si invers
    public static int constrain(int v, int min, int max) {
        return v > max ? max : (v < min ? min : v);
    }
    public static int constrain(int v) {
        return constrain(v, 0, 255);
    }

    //standardizam imaginea la argb pt a avea acces simplu la alpha, r g b pe 8 biti
    public static BufferedImage ensureARGB(BufferedImage src) {
        if (src == null) return null;
        if (src.getType() == BufferedImage.TYPE_INT_ARGB) return src;

        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    //convertim la grayscale, pentru a analiza imaginea pe luminanta ci nu culoare
    public static BufferedImage toGray(BufferedImage src) {
        if (src == null) return null;

        // Daca deja e gray, copiem raster-ul
        if (src.getType() == BufferedImage.TYPE_BYTE_GRAY) {
            BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
            src.copyData(out.getRaster());
            return out;
        }

        // Convertim prin acces pe benzi (Raster)
        BufferedImage argb = ensureARGB(src);
        BufferedImage out = new BufferedImage(argb.getWidth(), argb.getHeight(), BufferedImage.TYPE_BYTE_GRAY);

        for (int y = 0; y < argb.getHeight(); y++) {
            for (int x = 0; x < argb.getWidth(); x++) {
                int pixel = argb.getRGB(x, y);
                int r = (pixel >>> 16) & 0xFF;
                int g = (pixel >>> 8) & 0xFF;
                int b = pixel & 0xFF;

                int gray = constrain((int) Math.round(0.299 * r + 0.587 * g + 0.114 * b));
                out.getRaster().setSample(x, y, 0, gray);
            }
        }
        return out;
    }

    //micsoram imaginea pentru o analiza mai rapida
    public static BufferedImage downscaleForAnalysis(BufferedImage src, int maxSide) {
        if (src == null) return null;

        int w = src.getWidth(), h = src.getHeight();
        int m = Math.max(w, h);
        if (m <= maxSide) return src;

        double s = (double) maxSide / (double) m;
        int nw = Math.max(1, (int) Math.round(w * s));
        int nh = Math.max(1, (int) Math.round(h * s));

        return resize(src, nw, nh, AffineTransformOp.TYPE_BILINEAR);
    }

    //transformam imaginea geometric cu AffineTransform pt a avea un rezultat netede, fara pixeli in trepte
    public static BufferedImage resize(BufferedImage src, int width, int height, int interpolation) {
        if (src == null) return null;
        if (width <= 0 || height <= 0) return src;

        double scaleX = (double) width / src.getWidth();
        double scaleY = (double) height / src.getHeight();

        AffineTransform af = new AffineTransform();
        af.scale(scaleX, scaleY);

        BufferedImage dst = new BufferedImage(width, height, ensureARGB(src).getType());
        AffineTransformOp op = new AffineTransformOp(af, interpolation);
        op.filter(ensureARGB(src), dst);
        return dst;
    }

    //calculam cat de luminoasa e imaginea si deviatia standard (contrastul)
    public static double[] computeLumaMeanStd(BufferedImage src) {
        BufferedImage g = toGray(src); //convertim ikmaginea la grayscale
        Raster r = g.getRaster();

        long n = (long) g.getWidth() * g.getHeight();
        if (n == 0) return new double[]{0, 0};

        double sum = 0;
        //parcurgem pixelii
        for (int y = 0; y < g.getHeight(); y++) {
            for (int x = 0; x < g.getWidth(); x++) {
                sum += r.getSample(x, y, 0);
            }
        }
        double mean = sum / n;

        double var = 0;
        for (int y = 0; y < g.getHeight(); y++) {
            for (int x = 0; x < g.getWidth(); x++) {
                double d = r.getSample(x, y, 0) - mean;
                var += d * d;
            }
        }
        var /= n;

        return new double[]{mean, Math.sqrt(var)};
    }

    //calculam energia Laplacian
    public static double computeNoiseScore(BufferedImage src) {
        BufferedImage g = toGray(src);
        Raster r = g.getRaster();
        int w = g.getWidth(), h = g.getHeight();

        double acc = 0;
        int count = 0;

        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int c  = r.getSample(x, y, 0);
                int up = r.getSample(x, y - 1, 0);
                int dn = r.getSample(x, y + 1, 0);
                int lf = r.getSample(x - 1, y, 0);
                int rt = r.getSample(x + 1, y, 0);

                int lap = (4 * c) - up - dn - lf - rt;
                acc += (double) lap * lap;
                count++;
            }
        }

        return (count == 0) ? 0 : (acc / count) / (255.0 * 255.0);
    }

    //densitatea muschiilor
    public static double computeEdgeDensity(BufferedImage src) {
        BufferedImage g = toGray(src);
        Raster r = g.getRaster();
        int w = g.getWidth(), h = g.getHeight();

        int edges = 0;
        int total = (w - 2) * (h - 2);
        if (total <= 0) return 0;

        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int gx = r.getSample(x + 1, y, 0) - r.getSample(x - 1, y, 0);
                int gy = r.getSample(x, y + 1, 0) - r.getSample(x, y - 1, 0);
                int mag = Math.abs(gx) + Math.abs(gy);
                if (mag > 80) edges++;
            }
        }
        return (double) edges / (double) total;
    }

    //pentru a masura blurul, folosing varinta Laplacian
    public static double varianceOfLaplacian(BufferedImage src) {
        BufferedImage g = toGray(src);
        Raster r = g.getRaster();
        int w = g.getWidth(), h = g.getHeight();

        double sum = 0;
        int count = 0;

        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int c  = r.getSample(x, y, 0);
                int up = r.getSample(x, y - 1, 0);
                int dn = r.getSample(x, y + 1, 0);
                int lf = r.getSample(x - 1, y, 0);
                int rt = r.getSample(x + 1, y, 0);
                int lap = (4 * c) - up - dn - lf - rt;
                sum += lap;
                count++;
            }
        }
        if (count == 0) return 0;
        double mean = sum / count;

        double var = 0;
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int c  = r.getSample(x, y, 0);
                int up = r.getSample(x, y - 1, 0);
                int dn = r.getSample(x, y + 1, 0);
                int lf = r.getSample(x - 1, y, 0);
                int rt = r.getSample(x + 1, y, 0);
                int lap = (4 * c) - up - dn - lf - rt;
                double d = lap - mean;
                var += d * d;
            }
        }

        return var / count;
    }

    //histograma pe zone extreme, nu o construiesc in intregime ci doar numar pixelii in praguri
    public static double[] brightWhiteBlackRatios(BufferedImage src) {
        BufferedImage g = toGray(src);
        Raster r = g.getRaster();
        long n = (long) g.getWidth() * g.getHeight();
        if (n == 0) return new double[]{0, 0, 0};

        long bright = 0, white = 0, black = 0;

        for (int y = 0; y < g.getHeight(); y++) {
            for (int x = 0; x < g.getWidth(); x++) {
                int v = r.getSample(x, y, 0);
                if (v > 245) bright++;
                if (v > 230) white++;
                if (v < 25)  black++;
            }
        }

        return new double[]{
                (double) bright / n,
                (double) white / n,
                (double) black / n
        };
    }

    //filtru median pt zgomot
    public static BufferedImage medianBlur3x3(BufferedImage src) {
        src = ensureARGB(src);
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        int[] rr = new int[9], gg = new int[9], bb = new int[9];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int idx = 0;
                for (int yy = Math.max(0, y - 1); yy <= Math.min(h - 1, y + 1); yy++) {
                    for (int xx = Math.max(0, x - 1); xx <= Math.min(w - 1, x + 1); xx++) {
                        int rgb = src.getRGB(xx, yy);
                        rr[idx] = (rgb >>> 16) & 0xFF;
                        gg[idx] = (rgb >>> 8) & 0xFF;
                        bb[idx] = rgb & 0xFF;
                        idx++;
                    }
                }
                Arrays.sort(rr, 0, idx);
                Arrays.sort(gg, 0, idx);
                Arrays.sort(bb, 0, idx);

                int r = rr[idx / 2];
                int g = gg[idx / 2];
                int b = bb[idx / 2];
                int a = (src.getRGB(x, y) >>> 24) & 0xFF;

                out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    //accentuarea muchilor
    public static BufferedImage sharpen(BufferedImage src) {
        // 0 -1  0
        // -1 5 -1
        // 0 -1  0
        float[] k = {
                0, -1, 0,
                -1, 5, -1,
                0, -1, 0
        };
        return convolve(src, new Kernel(3, 3, k));
    }

    //convultia cu Kenel, se reduce zgomotul dar estompeaza si detaliile
    public static BufferedImage gaussianBlur3x3(BufferedImage src) {
        // (1 2 1 / 2 4 2 / 1 2 1) / 16
        float inv = 1f / 16f;
        float[] k = {
                1 * inv, 2 * inv, 1 * inv,
                2 * inv, 4 * inv, 2 * inv,
                1 * inv, 2 * inv, 1 * inv
        };
        return convolve(src, new Kernel(3, 3, k));
    }
    public static BufferedImage convolve(BufferedImage src, Kernel kernel) {
        if (src == null) return null;
        BufferedImage in = ensureARGB(src);
        BufferedImage out = new BufferedImage(in.getWidth(), in.getHeight(), in.getType());

        // EDGE_ZERO_FILL ca in ImageUtil.convolution(...)
        ConvolveOp op = new ConvolveOp(kernel, ConvolveOp.EDGE_ZERO_FILL, null);
        op.filter(in, out);
        return out;
    }

    //transformarea liniara pe fiecare canal
    public static BufferedImage adjustBrightnessContrast(BufferedImage src, double brightness, double contrast) {
        if (src == null) return null;

        BufferedImage in = ensureARGB(src); // TYPE_INT_ARGB garantat
        int w = in.getWidth();
        int h = in.getHeight();

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = in.getRGB(x, y);

                int a = (argb >>> 24) & 0xFF;
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = (argb) & 0xFF;

                r = constrain((int) Math.round((r - 128) * contrast + 128 + brightness));
                g = constrain((int) Math.round((g - 128) * contrast + 128 + brightness));
                b = constrain((int) Math.round((b - 128) * contrast + 128 + brightness));

                out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    //transforma neliiara (mai "naturala" pt expunere")
    public static BufferedImage gammaCorrection(BufferedImage src, double gamma) {
        if (src == null) return null;
        if (gamma <= 0) gamma = 1.0;

        BufferedImage in = ensureARGB(src);
        int w = in.getWidth(), h = in.getHeight();

        int[] lut = new int[256];
        double inv = 1.0 / gamma; // gamma < 1 lumineaza, gamma > 1 intuneca
        for (int i = 0; i < 256; i++) {
            double x = i / 255.0;
            int v = (int) Math.round(255.0 * Math.pow(x, inv));
            lut[i] = constrain(v);
        }

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = in.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >>> 16) & 0xFF;
                int gC = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;

                r = lut[r];
                gC = lut[gC];
                b = lut[b];

                out.setRGB(x, y, (a << 24) | (r << 16) | (gC << 8) | b);
            }
        }
        return out;
    }

    //media pe canalele rgb
    public static double[] meanRGB(BufferedImage src) {
        BufferedImage in = ensureARGB(src);
        int w = in.getWidth(), h = in.getHeight();
        long n = (long) w * h;
        if (n == 0) return new double[]{0, 0, 0};

        long sumR = 0, sumG = 0, sumB = 0;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = in.getRGB(x, y);
                sumR += (argb >>> 16) & 0xFF;
                sumG += (argb >>> 8) & 0xFF;
                sumB += argb & 0xFF;
            }
        }

        return new double[]{
                (double) sumR / n,
                (double) sumG / n,
                (double) sumB / n
        };
    }

    //verifica daca un canal deviaza >8% fata de media totala(gri)
    public static boolean hasColorCast(double meanR, double meanG, double meanB) {
        double gray = (meanR + meanG + meanB) / 3.0;
        if (gray < 1e-6) return false;

        double dr = Math.abs(meanR - gray) / gray;
        double dg = Math.abs(meanG - gray) / gray;
        double db = Math.abs(meanB - gray) / gray;

        double maxDev = Math.max(dr, Math.max(dg, db));
        return maxDev >= 0.08;
    }

    public static BufferedImage grayWorldWhiteBalance(BufferedImage src, double strength) {
        BufferedImage in = ensureARGB(src);
        strength = Math.max(0, Math.min(1, strength));

        double[] m = meanRGB(in);
        double meanR = m[0], meanG = m[1], meanB = m[2];
        double gray = (meanR + meanG + meanB) / 3.0;
        if (gray < 1e-6) return in;

        double fr = gray / Math.max(1e-6, meanR);
        double fg = gray / Math.max(1e-6, meanG);
        double fb = gray / Math.max(1e-6, meanB);

        fr = clampFactor(fr, 0.80, 1.25);
        fg = clampFactor(fg, 0.80, 1.25);
        fb = clampFactor(fb, 0.80, 1.25);

        fr = 1.0 + (fr - 1.0) * strength;
        fg = 1.0 + (fg - 1.0) * strength;
        fb = 1.0 + (fb - 1.0) * strength;

        int w = in.getWidth(), h = in.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = in.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;

                r = constrain((int) Math.round(r * fr));
                g = constrain((int) Math.round(g * fg));
                b = constrain((int) Math.round(b * fb));

                out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }

        return out;
    }

    private static double clampFactor(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(hi, v);
    }

    public static int otsuThreshold(BufferedImage grayByteImg) {
        BufferedImage g = (grayByteImg.getType() == BufferedImage.TYPE_BYTE_GRAY)
                ? grayByteImg
                : toGray(grayByteImg);

        Raster r = g.getRaster();
        int w = g.getWidth(), h = g.getHeight();
        int total = w * h;

        int[] hist = new int[256];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                hist[r.getSample(x, y, 0)]++;
            }
        }

        double sum = 0;
        for (int i = 0; i < 256; i++) sum += i * hist[i];

        double sumB = 0;
        int wB = 0;
        double maxVar = -1;
        int thr = 127;

        for (int t = 0; t < 256; t++) {
            wB += hist[t];
            if (wB == 0) continue;
            int wF = total - wB;
            if (wF == 0) break;

            sumB += t * hist[t];
            double mB = sumB / wB;
            double mF = (sum - sumB) / wF;

            double varBetween = (double) wB * (double) wF * (mB - mF) * (mB - mF);
            if (varBetween > maxVar) {
                maxVar = varBetween;
                thr = t;
            }
        }

        return thr;
    }

    public static BufferedImage otsuThresholdToBinary(BufferedImage src) {
        BufferedImage g = toGray(src);
        int thr = otsuThreshold(g);

        BufferedImage out = new BufferedImage(g.getWidth(), g.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
        Raster inR = g.getRaster();
        WritableRaster outR = out.getRaster();

        for (int y = 0; y < g.getHeight(); y++) {
            for (int x = 0; x < g.getWidth(); x++) {
                int v = inR.getSample(x, y, 0) >= thr ? 255 : 0;
                outR.setSample(x, y, 0, v);
            }
        }
        return out;
    }
}
