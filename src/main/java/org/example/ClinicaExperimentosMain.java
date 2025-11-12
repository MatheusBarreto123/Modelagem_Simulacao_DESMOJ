package org.example;

import desmoj.core.simulator.Experiment;
import desmoj.core.simulator.TimeInstant;
import org.example.model.ClinicaModel;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Locale;

public class ClinicaExperimentosMain {

    public static void main(String[] args) {
        Locale.setDefault(Locale.US); // separador decimal com ponto no CSV

        int[] cenariosConsultorios = {2, 3, 4};
        int replicacoesPorCenario = 5;
        double horizonteMin = 600.0; // 10h
        long seedBase = 12345L;

        String csvPath = "resultados_clinica.csv";
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(csvPath))) {
            // Cabeçalho dinâmico
            bw.write("consultorios,replicacao,espera_med_urg_min,espera_med_nao_urg_min,qmax_global");
            // adiciona Qmax e utilizacao por consultório (até 10 p/ cabeçalho bonito)
            int maxCols = 10;
            for (int i = 1; i <= maxCols; i++) {
                bw.write(",qmax_c" + i + ",util_c" + i);
            }
            bw.write(",chegadas_urg,chegadas_nao_urg,atend_urg,atend_nao_urg\n");

            for (int nCons : cenariosConsultorios) {
                System.out.println("\n=== CENÁRIO: " + nCons + " consultórios ===");

                for (int rep = 1; rep <= replicacoesPorCenario; rep++) {
                    String nomeExp = "Clinica_" + nCons + "cons_rep" + rep;
                    Experiment exp = new Experiment(nomeExp);

                    ClinicaModel model = new ClinicaModel(
                            null, "Modelo Clinica", true, false, nCons, horizonteMin
                    );
                    // Semeia distribuições de forma reprodutível
                    model.setSeeds(seedBase + rep);

                    model.connectToExperiment(exp);
                    exp.stop(new TimeInstant(horizonteMin));
                    exp.start();
                    exp.report();
                    exp.finish();

                    // Coleta métricas
                    double mUrg = model.getTempoMedioEsperaUrgente();
                    double mNao = model.getTempoMedioEsperaNaoUrgente();

                    int[] qmax = model.getQmaxPorConsultorio();
                    int qmaxGlobal = model.getQmaxGlobal();

                    double[] utiliz = model.getUtilizacaoPorConsultorio(); // 0..1

                    long chegUrg = model.getChegadasUrgentes();
                    long chegNao = model.getChegadasNaoUrgentes();
                    long atUrg = model.getAtendimentosUrgentes();
                    long atNao = model.getAtendimentosNaoUrgentes();

                    // Imprime no console resumido
                    System.out.printf("Rep %d -> Espera média (U: %.2f, N: %.2f) min | Qmax global: %d%n",
                            rep, mUrg, mNao, qmaxGlobal);

                    // Escreve CSV
                    bw.write(nCons + "," + rep + "," +
                            fmt(mUrg) + "," + fmt(mNao) + "," + qmaxGlobal);

                    // Preenche colunas por consultório (até maxCols)
                    for (int i = 0; i < maxCols; i++) {
                        int q = (i < qmax.length) ? qmax[i] : 0;
                        double u = (i < utiliz.length) ? utiliz[i] : 0.0;
                        bw.write("," + q + "," + fmt(u));
                    }
                    bw.write("," + chegUrg + "," + chegNao + "," + atUrg + "," + atNao + "\n");
                }
            }
            System.out.println("\nArquivo gerado: " + csvPath);
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.exit(0);
    }

    private static String fmt(double v) {
        return String.format(Locale.US, "%.4f", v);
    }
}
