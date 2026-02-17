package com.example.procesareaimaginilor;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.Group;
import javafx.scene.shape.Rectangle;


public class MainApp extends Application {

    //creem un vector de fisiere care retine fisierele selectate de catre utilizator
    private File[] fisiere = new File[0];
    private int indexCurenta = -1; //indexul imaginii curente

    private Image[] img_inainteUI = new Image[0]; //vector cu imaginile inainte de transformari pt interfata
    private Image[] img_dupaUI = new Image[0]; //vector cu imaginile dupa transformati pt interfata
    private String[] st_detaliiImagine = new String[0];
    private boolean[] procesata = new boolean[0]; //vector pentru a verifica daca o imagine este procesata

    //folosim bufferedImage pentru procesarea imaginilor, si Image pentru interfata
    private BufferedImage[] buff_inainte = new BufferedImage[0]; //vector cu imaginile inainte de tranformari
    private BufferedImage[] buff_dupa = new BufferedImage[0]; //vector cu imaginile dupa transformari

    private final AnalizaImagine analizatorImagine = new AnalizaImagine();
    private final ImbunatatireImagine imbunatatireImagine = new ImbunatatireImagine();

    //definim "ramele" pentru afisarea imaginilor
    private ImageView inainteView;
    private ImageView dupaView;

    private Label lbl_NumeFisier;
    private Label lbl_Numarare; //pentru a vedea la ce imagine suntem (ex: 1/10)

    //definim butoanele pentru urmatoare poza, respectiv precedenta
    private Button btnPrecedent;
    private Button btnUrmatorul;

    private TextArea ta_detaliiZona; //in aceasta zona vom pune detaliile despre imagini

    //definim fereastra pentru incarcarea fisierelor
    private Stage fereastraIncarcare;
    private ProgressBar baraProgres; //bara vizuala de progres
    private Label lblProgres; //pt afisarea mesajului de progres

    @Override
    public void start(Stage stage) {
        //MENIU
        //creez elementele din meniu
        MenuItem incarcare = new MenuItem("Incarcare fisiere");
        MenuItem salvareAll = new MenuItem("Salvare toate rezultatele");
        MenuItem iesire = new MenuItem("Iesire");

        //creez meniul si adaug toate elementele in meniu
        Menu meniu = new Menu("Meniu");
        meniu.getItems().addAll(
                incarcare,
                new SeparatorMenuItem(), //linie de separare, doar pt un aspect mai frumos
                salvareAll,
                new SeparatorMenuItem(),
                iesire
        );
        //creem bara de meniu
        MenuBar baraMeniu = new MenuBar(meniu);

        //BUTOANE PT INTERFATA
        btnPrecedent = new Button("Precedent");
        btnUrmatorul = new Button("Urmatorul");
        Button salvareImagineCurenta = new Button("Salvare");
        btnPrecedent.setStyle(ghostButtonStyle());
        btnUrmatorul.setStyle(ghostButtonStyle());
        salvareImagineCurenta.setStyle(ghostButtonStyle());

        //LABEL
        lbl_Numarare = new Label("0 / 0");
        lbl_Numarare.setStyle(pillStyle());
        lbl_NumeFisier = new Label("Nicio imagine incarcata");
        lbl_NumeFisier.setStyle("-fx-text-fill: #555; -fx-font-weight: 800;");

        //creem un box Orizontal in care vom aseza butoanele si numele fisierului
        HBox topBar = new HBox(10,
                salvareImagineCurenta,
                lbl_NumeFisier,
                btnPrecedent, btnUrmatorul, lbl_Numarare
        );
        topBar.setAlignment(Pos.CENTER);
        topBar.setPadding(new Insets(12));
        topBar.setStyle(cardStyle());

        //cu ajutorul unui box vertical vom pune meniul si butoanele unele sub altele
        VBox top = new VBox(baraMeniu, topBar);
        VBox.setMargin(topBar, new Insets(10, 14, 12, 14));

        //creez ImageView urile unde voi aseza pozele si le configurez
        inainteView = new ImageView();
        dupaView = new ImageView();
        setupImageView(inainteView);
        setupImageView(dupaView);
        //pun imaginile in "cadre"
        VBox inainteBox = imagineCard("Inainte", inainteView);
        VBox dupaBox = imagineCard("Dupa", dupaView);

        //creez pe orizontala boxurile cu imaginile, in st -> inainte, in dr -> dupa
        HBox imaginiPeLinieUi = new HBox(14, inainteBox, dupaBox); //creem pe orizontala boxul
        imaginiPeLinieUi.setPadding(new Insets(14));
        HBox.setHgrow(inainteBox, Priority.ALWAYS);
        HBox.setHgrow(dupaBox, Priority.ALWAYS);

        //creem panoul cu detalii despre fiecare imagine
        Label titluPanouDetalii = new Label("Detalii imagine curenta");
        titluPanouDetalii.setStyle("-fx-text-fill: #333; -fx-font-size: 16px; -fx-font-weight: 900;");
        ta_detaliiZona = new TextArea();
        ta_detaliiZona.setWrapText(true);
        ta_detaliiZona.setEditable(false);
        ta_detaliiZona.setPromptText("Incarca imagini. Detaliile se schimba automat la apasarea butoaelor Precedent/Urmator");
        ta_detaliiZona.setStyle(
                "-fx-background-radius: 14;" +
                        "-fx-border-radius: 14;" +
                        "-fx-border-color: rgba(0,0,0,0.08);" +
                        "-fx-control-inner-background: rgba(255,255,255,0.90);" +
                        "-fx-text-fill: #333;"
        );

        //creem panoul de detalii
        VBox PanouDetalii = new VBox(10, titluPanouDetalii, ta_detaliiZona);
        PanouDetalii.setPadding(new Insets(12));
        PanouDetalii.setPrefWidth(400);
        PanouDetalii.setStyle(cardStyle());

        //creem layoutul, prima data partea de centru unde punem imaginile before/after si jos detaliile
        BorderPane center = new BorderPane();
        center.setCenter(imaginiPeLinieUi);
        center.setBottom(PanouDetalii);
        BorderPane.setMargin(PanouDetalii, new Insets(14, 14, 14, 0));

        BorderPane root = new BorderPane();
        root.setTop(top); //in fereastra princiapala adaugam sus meniul si butoanele prec/urm
        root.setCenter(center); //adaugam si partea de centru cu imaginile before/after si detaliile despre ele
        root.setStyle("-fx-background-color: #f6f7fb;");

        Scene scene = new Scene(root, 1340, 740);
        stage.setTitle("Analizator al calitatii unei imagini si imbunatatirea acesteia");
        stage.setScene(scene);
        stage.show();

        //adaugam actiune butoanelor (evenimente)
        Runnable loadAction = () -> incarcareSiProcesare(stage);
        incarcare.setOnAction(e -> loadAction.run());

        btnPrecedent.setOnAction(e -> imaginePrecedenta());
        btnUrmatorul.setOnAction(e -> imagineUrmatoare());

        salvareImagineCurenta.setOnAction(e -> salvareFisierCurent(stage)); //salvare imagine curenta
        salvareAll.setOnAction(e -> salvareToateFisierele(stage)); //salvare toate imaginile procesate
        iesire.setOnAction(e -> stage.close());

        refresh();
        afisareUICurent();
    }

    //incarcarea si procesarea imaginilor
    private void incarcareSiProcesare(Stage stage) {
        //deschidem fereastra dialog de selectare a imaginilor
        FileChooser fc_selectareImagini = new FileChooser();
        fc_selectareImagini.setTitle("Selecteaza imaginile");
        //afisam doar fisierele cu extensiile de mai jos
        fc_selectareImagini.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif")
        );

        //creem o lista de tip File in care retinem fisierele selectate
        List<File> selectate = fc_selectareImagini.showOpenMultipleDialog(stage); //OpenMultiple.. pentru a putea selecta mai multe imagini
        if (selectate == null || selectate.isEmpty()) return; //daca nu am selectat nimic se iese din metoda

        fisiere = selectate.toArray(new File[0]); //convertim lista de fisiere selectate intr un vector
        indexCurenta = 0; //indexul imaginii curente

        //initializam toti vectorii pentru toate imaginile
        img_inainteUI = new Image[fisiere.length];
        img_dupaUI = new Image[fisiere.length];
        st_detaliiImagine = new String[fisiere.length];
        procesata = new boolean[fisiere.length];

        buff_inainte = new BufferedImage[fisiere.length];
        buff_dupa = new BufferedImage[fisiere.length];

        afisareUICurent();
        refresh();

        //creem Taskul pentru a procesa imaginile pe un thread separat
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                int totalFisiere = fisiere.length; //numarul de fisiere

                for (int i = 0; i < totalFisiere; i++) { //parcurgem toate fisierele
                    if (isCancelled()) break; //in cazul in care utilizatorul anuleaza

                    updateProgress(i, totalFisiere); //actualizam bara de progres
                    //actualizam textul din fereastra de incarcare
                    updateMessage("Procesare:  " + (i + 1) + " / " + totalFisiere + " : " + fisiere[i].getName());
                    final int idx = i;

                    try {
                        //citim imaginea cu imageIO
                        BufferedImage inputImagine = ImageIO.read(fisiere[i]);
                        if (inputImagine == null) throw new IOException("ImageIO.read() a returnat null (format neacceptat / fisier corupt).");

                        buff_inainte[i] = inputImagine; //salvam imaginea in vectorul de procesare

                        //construim stringul cu detalii despre imaginea curenta
                        StringBuilder sb_detaliiImagineCurenta = new StringBuilder();
                        sb_detaliiImagineCurenta.append("Imagine: ").append(fisiere[i].getName()).append("\n\n");

                        //creem o lista cu deciziile luate (ex: am crescut contrastul, etc)
                        List<String> deciziiDetalii = new ArrayList<>(); //o sa avem o lista care contine elemente de tip string
                        BufferedImage outImagine; //imaginea procesata

                        //analizam imaginea
                        AnalizaImagine ar = analizatorImagine.analiza(inputImagine);

                        // observatiile analizei
                        for (String n : ar.getObs()) {
                            sb_detaliiImagineCurenta.append("• ").append(n).append("\n");
                        }
                        sb_detaliiImagineCurenta.append("\n");

                        imbunatatireImagine.imbunatatire(inputImagine, ar); //imbunatatim imaginea

                        sb_detaliiImagineCurenta.append("Decizii aplicate:\n");
                        // detalii despre ce a facut imbunatatirea, adaugam in string
                        for (String d : imbunatatireImagine.getDetalii()) {
                            if (d != null && !d.isBlank()) {
                                sb_detaliiImagineCurenta.append("• ").append(d).append("\n");
                            }
                        }
                        // imaginea finala imbunatatita
                        outImagine = imbunatatireImagine.getOutputImg();

                        //salvam rezultatele
                        buff_dupa[i] = outImagine; //pe disc, imaginea procesata
                        for (String d : deciziiDetalii) sb_detaliiImagineCurenta.append("• ").append(d).append("\n");

                        st_detaliiImagine[i] = sb_detaliiImagineCurenta.toString(); //actualizam detaliile pentru poza curenta
                        procesata[i] = true; //marcam ca am procesat imaginea

                    } catch (IOException ex) {
                        procesata[i] = true;
                        buff_dupa[i] = null;
                        img_dupaUI[i] = null;
                        try {
                            img_inainteUI[i] = new Image(fisiere[i].toURI().toString(), false);
                        } catch (Exception ignored) {}
                        st_detaliiImagine[i] = "Imagine: " + fisiere[i].getName() + "\nEroare:\n" + ex.toString();

                    } catch (Exception ex) {
                        procesata[i] = true;
                        buff_dupa[i] = null;
                        img_dupaUI[i] = null;
                        try {
                            img_inainteUI[i] = new Image(fisiere[i].toURI().toString(), false);
                        } catch (Exception ignored) {}
                        st_detaliiImagine[i] = "Imagine: " + fisiere[i].getName() + "\nEroare:\n" + ex.toString();

                    }
                    //se va rula mai tarziu, dupa procesarea curenta
                    Platform.runLater(() -> {
                        img_inainteUI[idx] = conversieImagine.toWrite(buff_inainte[idx]); //o transformam in imagine
                        img_dupaUI[idx] = conversieImagine.toWrite(buff_dupa[idx]); //o transformam in imagine
                        if (indexCurenta == idx) afisareUICurent();
                    });
                }

                updateProgress(totalFisiere, totalFisiere);
                updateMessage("S-au incarcat toate imaginile");
                return null;
            }
        };

        task.setOnRunning(e -> afisareFereastraIncarcare(stage, task));
        task.setOnSucceeded(e -> ascundereFereastraIncarcare(stage, task ));
        task.setOnCancelled(e -> ascundereFereastraIncarcare(stage, task ));
        task.setOnFailed(e -> {
            ascundereFereastraIncarcare(stage, task);
            info("Eroare", "A aparut o eroare la incarcare/procesare:\n" + task.getException());
        });

        new Thread(task, "load-process-task").start();
    }

    private void salvareFisierCurent(Stage owner) {
        //daca nu avem nicio imagine incarcata
        if (fisiere.length == 0 || indexCurenta < 0) {
            info("Salavare", "Nu exista imagine de salvat.");
            return;
        }
        //verificam daca imaginea curenta este procesata
        if (!(procesata.length > indexCurenta && procesata[indexCurenta] && buff_dupa[indexCurenta] != null)) {
            info("Salvare", "Imaginea curenta nu este inca procesata.");
            return;
        }

        //creem dialogul de salvare
        FileChooser fc_salvareImagineCurenta = new FileChooser();
        fc_salvareImagineCurenta.setTitle("Salvare imagine curenta");
        //imaginea poate fi salvata doar cu extensie png si jpg
        fc_salvareImagineCurenta.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("PNG (*.png)", "*.png"),
                new FileChooser.ExtensionFilter("JPG (*.jpg)", "*.jpg")
        );

        //scoatem extensia originala, adaugam _after + setam implicit cu extensia .png
        String base = getNumeFisier(fisiere[indexCurenta].getName()) + "_after";
        fc_salvareImagineCurenta.setInitialFileName(base + ".png");

        //afisam utilizatorului dialogul de salvare
        File outImagineSalvata = fc_salvareImagineCurenta.showSaveDialog(owner);
        if (outImagineSalvata == null) return; //daca se apasa cancel, iesim din metoda

        //detectam formatul final
        String lower = outImagineSalvata.getName().toLowerCase();
        String fmt = (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) ? "jpg" : "png";

        //verificam daca se poate salva imaginea
        //se foloseste imaginea procesata
        //se salveaza pe disc
        try {
            salvareImagine.saveImage(buff_dupa[indexCurenta], outImagineSalvata, fmt);
            info("Salvare", "Salvat:\n" + outImagineSalvata.getAbsolutePath());
        } catch (Exception ex) {
            info("Eroare salvare", "Nu pot salva:\n" + ex.getMessage());
        }
    }

    private void salvareToateFisierele(Stage owner) {
        if (fisiere.length == 0) {
            info("Salvare toate imaginile", "Nu exista imagini.");
            return;
        }

        //selectam folderul unde vrem sa salvam imaginile
        DirectoryChooser dc_SalvareImagini = new DirectoryChooser();
        dc_SalvareImagini.setTitle("Selecteaza folder pt salvarea imaginilor");
        File dir = dc_SalvareImagini.showDialog(owner);
        if (dir == null) return; //daca utilizatorul apasa cancel , iesim din metoda

        //creem un choice dialog, adica un dialog cu optiuni dropdown pt selectarea extensiei imaginilor
        ChoiceDialog<String> cd_optiuniDropDown = new ChoiceDialog<>("png", "png", "jpg");
        cd_optiuniDropDown.setTitle("Salvare format");
        cd_optiuniDropDown.setHeaderText("Alege formatul pentru toate imaginile");
        cd_optiuniDropDown.setContentText("Format:");
        //in "format" vom salva extensia
        String format = cd_optiuniDropDown.showAndWait().orElse(null);
        if (format == null) return; //daca se apasa cancel, iesim

        int salvate = 0; //pentru a numara cate imagini au fost salvate
        List<String> erori = new ArrayList<>(); //vom face o lista in care salvam toate erorile

        for (int i = 0; i < fisiere.length; i++) {
            //construiesc numele fisierului rezultat
            String nume = getNumeFisier(fisiere[i].getName()) + "_after." + salvareImagine.extensie(format);
            File outImagineSalvata = new File(dir, nume); //fisierul final din directorul selectat
            //incerc sa salvez si tratam erorile
            try {
                //se salveaza imaginea procesata
                salvareImagine.saveImage(buff_dupa[i], outImagineSalvata, format);
                salvate++; //crestem nr. imaginilor salvate
            } catch (Exception ex) {
                erori.add(fisiere[i].getName() + " → " + ex.getMessage());
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Salvate: ").append(salvate).append(" / ").append(fisiere.length).append("\n");
        if (!erori.isEmpty()) {
            sb.append("\nErori/skip:\n");
            for (String e : erori) sb.append("• ").append(e).append("\n");
        }
        info("Save All", sb.toString());
    }

    private String getNumeFisier(String name) {
        // cautam ultima aparitie a caracterului '.'
        int pozitiePunct = name.lastIndexOf('.');
        // daca exista punct si nu este primul caracter
        if (pozitiePunct > 0) {
            return name.substring(0, pozitiePunct); // returnam tot ce este inainte de punct
        } else {
            return name;  // daca nu exista extensie, returnam numele original
        }
    }

    private void imaginePrecedenta() {
        if (fisiere.length == 0) return;
        if (indexCurenta > 0)
            indexCurenta--;
        afisareUICurent();
        refresh();
    }

    private void imagineUrmatoare() {
        if (fisiere.length == 0) return;
        if (indexCurenta < fisiere.length - 1)
            indexCurenta++;
        afisareUICurent();
        refresh();
    }

    private void afisareUICurent() {
        if (fisiere.length == 0 || indexCurenta < 0) {
            inainteView.setImage(null);
            dupaView.setImage(null);
            lbl_NumeFisier.setText("Nicio imagine incarcata");
            lbl_Numarare.setText("0 / 0");
            ta_detaliiZona.clear();
            return;
        }

        lbl_NumeFisier.setText(fisiere[indexCurenta].getName());
        lbl_Numarare.setText((indexCurenta + 1) + " / " + fisiere.length);

        Image b = (img_inainteUI.length > indexCurenta) ? img_inainteUI[indexCurenta] : null;
        if (b == null) b = new Image(fisiere[indexCurenta].toURI().toString(), false);
        inainteView.setImage(b);

        if (procesata.length > indexCurenta && procesata[indexCurenta] && img_dupaUI[indexCurenta] != null)
            dupaView.setImage(img_dupaUI[indexCurenta]);
        else dupaView.setImage(null);

        if (procesata.length > indexCurenta && procesata[indexCurenta] && st_detaliiImagine[indexCurenta] != null)
            ta_detaliiZona.setText(st_detaliiImagine[indexCurenta]);
        else ta_detaliiZona.setText("Imagine: " + fisiere[indexCurenta].getName() + "\nStatus: incarcare / procesare...\n");
    }

    private void refresh() {
        boolean has = fisiere.length > 0;
        btnPrecedent.setDisable(!has || indexCurenta <= 0);
        btnUrmatorul.setDisable(!has || indexCurenta >= fisiere.length - 1);
    }

    //creez o fereastra de incarcare pop up cu progress bar, legata automat la Task
    private void afisareFereastraIncarcare(Stage owner, Task<?> task) {
        if (fereastraIncarcare == null) { //daca nu a fost creata fereastra, o cream
            baraProgres = new ProgressBar(0); //bara goala (0%)
            baraProgres.setPrefWidth(360); //latime

            lblProgres = new Label("Se incarca...");
            lblProgres.setStyle("-fx-text-fill: #555; -fx-font-weight: 700;");

            Label title = new Label("Incarcare..."); //titlul ferestrei
            title.setStyle("-fx-text-fill: #333; -fx-font-size: 14px; -fx-font-weight: 900;");

            //layout-ul ferestrei
            VBox box = new VBox(10, title, baraProgres, lblProgres); //elemente puse vertical, spatiere 10
            box.setAlignment(Pos.CENTER_LEFT); //centrat vertical, aliniat la st
            box.setPadding(new Insets(16));
            box.setStyle(cardStyle()); //pentru stil

            Scene s = new Scene(box); //continutul ferestrei
            fereastraIncarcare = new Stage(); //fereastra in sine
            fereastraIncarcare.initOwner(owner); //apartine ferestrei prinicipale si ramane deasupra ei
            fereastraIncarcare.initModality(Modality.WINDOW_MODAL); //cat timp e loading deschis, nu putem interationa cu fer principala
            fereastraIncarcare.setResizable(false);
            fereastraIncarcare.setTitle("Asteptati");
            fereastraIncarcare.setScene(s);
        }
        //legarea la Task
        baraProgres.progressProperty().bind(task.progressProperty()); //actualizeaza automat bara cu progresul
        lblProgres.textProperty().bind(task.messageProperty()); //actualizeaza automat mesajul

        if (!fereastraIncarcare.isShowing()) fereastraIncarcare.show();
    }

    private void ascundereFereastraIncarcare(Stage owner, Task<?> task) {
        if (fereastraIncarcare == null) return; //daca fereastra de incarcare nu a fost creata, ne oprim
        Platform.runLater(() -> {
            try {
                //terminam proprietetile dintre progress bar si label de task
                baraProgres.progressProperty().unbind();
                lblProgres.textProperty().unbind();
            } catch (Exception ignored) {}
            fereastraIncarcare.hide(); //ascundem fereastra
        });
    }

    //setari pentru ambele imagini (before+after)
    private void setupImageView(ImageView view) {
        view.setPreserveRatio(true);
        view.setSmooth(true);
        view.setFitWidth(560);
        view.setFitHeight(430);
    }

    //metoda pt crearea panourilor pt imagini
    private VBox imagineCard(String title, ImageView view) {
        Label lbl = new Label(title);
        lbl.setStyle("-fx-text-fill: #333; -fx-font-size: 16px; -fx-font-weight: 900;");

        Group zoomGroup = new Group(view); //pun imaginea intr un grup, pt zoom
        StackPane frame = new StackPane(zoomGroup); //creez o "rama" in care pun grupul pt zoom
        frame.setAlignment(Pos.CENTER);
        frame.setPadding(new Insets(12));
        frame.setStyle(
                "-fx-background-color: rgba(255,255,255,0.88);" +
                        "-fx-background-radius: 16;" +
                        "-fx-border-radius: 16;" +
                        "-fx-border-color: rgba(0,0,0,0.06);"
        );
        // clip ca zoom-ul sa nu iasa in afara "ramei"
        Rectangle clip = new Rectangle();
        //ii setez aceleasi dimensiuni ca dimensiunile ramei pt a nu iesii in afara ei
        clip.widthProperty().bind(frame.widthProperty());
        clip.heightProperty().bind(frame.heightProperty());
        frame.setClip(clip);
        //apelez metoda
        zoomImagine(frame, zoomGroup);

        VBox box = new VBox(10, lbl, frame);
        box.setPadding(new Insets(12));
        box.setStyle(cardStyle());
        VBox.setVgrow(frame, Priority.ALWAYS);
        return box;
    }

    private void zoomImagine(StackPane frame, Group zoomGroup) {
        frame.setOnScroll(e -> {
            double scale = zoomGroup.getScaleX(); //nivelul actual de zoom
            scale *= (e.getDeltaY() > 0 ? 1.1 : 0.9); //directia zoomului : Y -> zoom marit
            scale = Math.max(0.5 , Math.min(scale,4.0));
            zoomGroup.setScaleX(scale);
            zoomGroup.setScaleY(scale);
            e.consume();
        });

        // reset rapid pe dublu-click
        frame.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2){
                zoomGroup.setScaleX(1);
                zoomGroup.setScaleY(1);
            }
        });
    }

    private void info(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    private String cardStyle() {
        return "-fx-background-color: rgba(255,255,255,0.90);" +
                "-fx-background-radius: 18;" +
                "-fx-border-radius: 18;" +
                "-fx-border-color: rgba(0,0,0,0.06);" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.10), 18, 0.18, 0, 6);";
    }

    private String ghostButtonStyle() {
        return "-fx-background-color: rgba(255,255,255,0.78);" +
                "-fx-text-fill: #333;" +
                "-fx-font-weight: 900;" +
                "-fx-background-radius: 12;" +
                "-fx-padding: 10 14;" +
                "-fx-border-color: rgba(0,0,0,0.08);" +
                "-fx-border-radius: 12;" +
                "-fx-cursor: hand;";
    }

    private String pillStyle() {
        return "-fx-background-color: rgba(0,0,0,0.06);" +
                "-fx-background-radius: 999;" +
                "-fx-padding: 6 10;" +
                "-fx-text-fill: #333;" +
                "-fx-font-weight: 900;";
    }

    public static void main(String[] args) {
        launch(args);
    }
}
