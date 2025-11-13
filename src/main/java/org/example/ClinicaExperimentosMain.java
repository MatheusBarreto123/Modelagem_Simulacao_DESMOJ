package org.example;

import desmoj.core.simulator.Experiment;
import desmoj.core.simulator.TimeInstant;
import org.example.model.ClinicaModel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

//roda varios cenarios e replicações
public class ClinicaExperimentosMain {

    // Se quiser calcular payback de verdade, coloque aqui o benefício mensal estimado
    private static final double BENEFICIO_MENSAL_R$ = 0.0;

    public static void main(String[] args) {

        // CUIDADO: se ainda estiver pesado, diminua cenários e reps
        int[] cenariosConsultorios = {2, 3, 4, 5, 6};
        int replicacoesPorCenario = 5; // pode colocar 3 se quiser ainda mais leve

        // Perfil de carga ao longo do dia (10 blocos de 1h)
        double[] fatorChegadaHora = {
                0.8, 1.0, 1.2, 1.3, 1.4,
                1.2, 1.0, 0.9, 0.8, 0.7
        };

        // (a) & (b) – filas separadas, sem e com prioridade
        ResultadoCenario resSemPrio = testaModo(
                false,     // prioridadeAtiva
                false,     // modoFilaUnica
                cenariosConsultorios,
                replicacoesPorCenario,
                fatorChegadaHora,
                null,      // meanNaoUrgenteOverride (20 min)
                "Cenário BASE – Filas separadas, SEM prioridade"
        );

        ResultadoCenario resComPrio = testaModo(
                true,      // prioridadeAtiva
                false,     // modoFilaUnica
                cenariosConsultorios,
                replicacoesPorCenario,
                fatorChegadaHora,
                null,
                "Cenário PRIORIDADE – Filas separadas, COM prioridade"
        );

        System.out.println();
        System.out.println(">>> (a) & (b) RESUMO NUMÉRICO PARA O RELATÓRIO");
        System.out.printf("Sem prioridade: menor nº de consultórios que cumpre a meta = %s%n",
                resSemPrio.menorConsultorios == null ? "NENHUM" : resSemPrio.menorConsultorios);
        System.out.printf("Com prioridade: menor nº de consultórios que cumpre a meta = %s%n",
                resComPrio.menorConsultorios == null ? "NENHUM" : resComPrio.menorConsultorios);

        // (c) – triagem eletrônica (não urgentes = 15 min)
        System.out.println();
        System.out.println("==== CENÁRIO TRIAGEM ELETRÔNICA (Não-urgentes = 15 min) ====");

        ResultadoCenario baseTriagem = testaModo(
                true,
                false,
                cenariosConsultorios,
                replicacoesPorCenario,
                fatorChegadaHora,
                null,
                "Triagem BASE – NaoUrg = 20 min"
        );

        ResultadoCenario triagem15 = testaModo(
                true,
                false,
                cenariosConsultorios,
                replicacoesPorCenario,
                fatorChegadaHora,
                15.0,
                "Triagem ELETRÔNICA – NaoUrg = 15 min"
        );

        if (baseTriagem.menorConsultorios != null && triagem15.menorConsultorios != null) {
            int c_base = baseTriagem.menorConsultorios;
            int c_triag = triagem15.menorConsultorios;
            double custo = 30000 + 5000 * c_triag;
            double paybackMeses = (BENEFICIO_MENSAL_R$ > 0)
                    ? (custo / BENEFICIO_MENSAL_R$)
                    : Double.POSITIVE_INFINITY;

            System.out.println();
            System.out.println(">>> (c) RESUMO NUMÉRICO PARA O RELATÓRIO");
            System.out.printf("Menor nº de consultórios (base)   = %d%n", c_base);
            System.out.printf("Menor nº de consultórios (triagem)= %d%n", c_triag);
            System.out.printf("Custo do sistema de triagem       = R$ %.0f%n", custo);
            if (BENEFICIO_MENSAL_R$ > 0) {
                System.out.printf("Benefício mensal estimado         = R$ %.0f%n", BENEFICIO_MENSAL_R$);
                System.out.printf("Payback aproximado                = %.1f meses%n", paybackMeses);
            } else {
                System.out.println("Defina BENEFICIO_MENSAL_R$ para calcular o payback.");
            }
        }

        // (e) – fila única (pool) com prioridade
        System.out.println();
        System.out.println("==== COMPARAÇÃO FILA ÚNICA (POOL) ====");
        ResultadoCenario resFilaUnica = testaModo(
                true,
                true,
                cenariosConsultorios,
                replicacoesPorCenario,
                fatorChegadaHora,
                null,
                "Fila ÚNICA – Pool de consultórios, COM prioridade"
        );

        System.out.println();
        System.out.println(">>> (e) RESUMO NUMÉRICO PARA O RELATÓRIO");
        System.out.printf("Fila única (pool): menor nº de consultórios que cumpre a meta = %s%n",
                resFilaUnica.menorConsultorios == null ? "NENHUM" : resFilaUnica.menorConsultorios);

        System.exit(0);
    }

    /**
     * Resultado de um cenário (um “modo”: combinações de prioridade / fila única / triagem).
     */
    static class ResultadoCenario {
        Integer menorConsultorios; // menor nº de consultórios que cumpre a meta
        double[] mediaPorHora;     // média do pico por hora para esse nº de consultórios
        double[] p95PorHora;       // p95 do pico por hora para esse nº de consultórios
    }

    /**
     * Roda apenas UM “dia típico” para cada nº de consultórios e procura o menor
     * que atende a meta de fila ≤5. Imprime tabela pronta pra colar no Word.
     */
    private static ResultadoCenario testaModo(boolean prioridadeAtiva,
                                              boolean modoFilaUnica,
                                              int[] cenariosConsultorios,
                                              int reps,
                                              double[] fatorChegadaHora,
                                              Double meanNaoUrgenteOverride,
                                              String tituloModo) {

        System.out.println();
        System.out.println("================================================");
        System.out.println("MODO: " + tituloModo);
        System.out.println("      prioridade " + (prioridadeAtiva ? "ATIVA" : "DESATIVADA")
                + " | fila " + (modoFilaUnica ? "ÚNICA (POOL)" : "POR CONSULTÓRIO")
                + (meanNaoUrgenteOverride != null ? " | NãoUrg=" + meanNaoUrgenteOverride + " min" : ""));
        System.out.println("================================================");

        int H = 10; // 10 blocos de 1h

        Integer melhorC = null;
        double[] melhorMedia = null;
        double[] melhorP95 = null;

        // varremos os possíveis números de consultórios
        for (int nCons : cenariosConsultorios) {

            boolean todosCumpriram = true;
            List<int[]> picosHoraReps = new ArrayList<>();

            for (int rep = 1; rep <= reps; rep++) {

                String nomeExp = "Clinica_" + nCons + "cons_"
                        + (prioridadeAtiva ? "prio" : "semprio")
                        + (modoFilaUnica ? "_pool" : "_sep")
                        + (meanNaoUrgenteOverride != null ? "_triagem" : "")
                        + "_rep" + rep;

                Experiment exp = new Experiment(nomeExp);

                // showInReport=false, showInTrace=false para ficar bem leve
                ClinicaModel model = new ClinicaModel(
                        null,
                        "Modelo Clinica",
                        false,
                        false,
                        nCons,
                        prioridadeAtiva,
                        modoFilaUnica,
                        fatorChegadaHora,
                        meanNaoUrgenteOverride
                );

                model.connectToExperiment(exp);
                TimeInstant stopTime = new TimeInstant(600.0); // 10h = 600 min
                exp.stop(stopTime);
                exp.start();

                exp.finish();

                boolean cumpriu;
                int[] picoHoraVet;

                if (modoFilaUnica) {
                    int picoPool = model.getPicoPoolDia();
                    cumpriu = (picoPool <= 5 * nCons);
                    picoHoraVet = model.getPicoHoraPool();
                } else {
                    int[] picos = model.getPicoFilaPorConsultorio();
                    int pico = 0;
                    for (int p : picos) pico = Math.max(pico, p);
                    cumpriu = (pico <= 5);
                    picoHoraVet = model.getPicoHoraGlobal();
                }

                if (!cumpriu) {
                    todosCumpriram = false;
                }

                picosHoraReps.add(Arrays.copyOf(picoHoraVet, picoHoraVet.length));
            }

            System.out.printf("c = %d -> Cumpriu em todas as reps? %s%n", nCons, (todosCumpriram ? "SIM" : "NÃO"));

            if (todosCumpriram) {
                // calculamos médias e p95 por hora para esse nº de consultórios
                double[] medias = new double[H];
                double[] p95s = new double[H];

                for (int h = 0; h < H; h++) {
                    List<Integer> valores = new ArrayList<>();
                    for (int[] vet : picosHoraReps) {
                        valores.add(vet[h]);
                    }
                    medias[h] = mediaLista(valores);
                    p95s[h] = percentil(valores, 0.95);
                }

                melhorC = nCons;
                melhorMedia = medias;
                melhorP95 = p95s;
                break; // já achou o menor c que cumpre, não precisa testar c maiores
            }
        }

        // ====== TABELA RESUMO (PRONTA PARA COLAR NO WORD) ======
        System.out.println();
        System.out.println("===== Tabela (d) — RESUMO para: " + tituloModo + " =====");
        System.out.println("Hora\tMedia_c\tP95_c");

        if (melhorC != null) {
            for (int h = 0; h < 10; h++) {
                String horaLabel = String.format("H%02d", (h + 1));
                System.out.printf("%s\t%.1f\t%.0f%n", horaLabel, melhorMedia[h], melhorP95[h]);
            }
        } else {
            System.out.println("Nenhum valor de consultórios testado conseguiu cumprir a meta de fila ≤ 5.");
        }

        System.out.println();
        System.out.println(">>> Menor nº de consultórios que cumpre a meta neste modo: "
                + (melhorC == null ? "NENHUM" : melhorC));
        System.out.println("(Copie esta tabela e use 'Converter texto em tabela' no Word.)");
        System.out.println();

        ResultadoCenario res = new ResultadoCenario();
        res.menorConsultorios = melhorC;
        res.mediaPorHora = melhorMedia;
        res.p95PorHora = melhorP95;
        return res;
    }

    // ===== Funções auxiliares para estatística simples =====

    private static double mediaLista(List<Integer> xs) {
        double s = 0;
        int n = 0;
        for (Integer v : xs) {
            if (v != null && v < Integer.MAX_VALUE) {
                s += v;
                n++;
            }
        }
        return n == 0 ? Double.NaN : s / n;
    }

    private static double percentil(List<Integer> xs, double p) {
        List<Integer> ys = new ArrayList<>();
        for (Integer v : xs) {
            if (v != null && v < Integer.MAX_VALUE) ys.add(v);
        }
        if (ys.isEmpty()) return Double.NaN;
        ys.sort(Integer::compareTo);
        int idx = (int) Math.ceil(p * ys.size()) - 1;
        idx = Math.max(0, Math.min(idx, ys.size() - 1));
        return ys.get(idx);
    }
}
