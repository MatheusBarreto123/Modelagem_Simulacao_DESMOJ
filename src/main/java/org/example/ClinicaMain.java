package org.example;

import desmoj.core.simulator.Experiment;
import desmoj.core.simulator.TimeInstant;
import org.example.model.ClinicaModel;
import java.util.Arrays;

//roda apenas um cenário
public class ClinicaMain {
    public static void main(String[] args) {

        int numConsultorios  = 4;
        boolean prioridadeAtiva  = true;
        boolean modoFilaUnica    = false; // true = pool (e)

        // Perfil horário (10h). 1.0 = carga base; >1 pico; <1 vale
        double[] fatorChegadaHora = {
                0.8, 1.0, 1.2, 1.3, 1.4,
                1.2, 1.0, 0.9, 0.8, 0.7
        };

        // (c) Triagem eletrônica: não-urgentes = 15 min (use null p/ cenário base 20 min)
        Double meanNaoUrgenteOverride = null; // 15.0 ativa triagem

        Experiment exp = new Experiment("Clinica_Vida_Saudavel");

        ClinicaModel model = new ClinicaModel(
                null,
                "Modelo Clinica",
                true,    // showInReport
                true,    // showInTrace
                numConsultorios,
                prioridadeAtiva,
                modoFilaUnica,
                fatorChegadaHora,
                meanNaoUrgenteOverride
        );

        model.connectToExperiment(exp);

        exp.setShowProgressBar(true);
        TimeInstant stopTime = new TimeInstant(600.0); // clinica funciona por 10 horas
        exp.stop(stopTime);

        exp.tracePeriod(new TimeInstant(0.0), stopTime);
        exp.debugPeriod(new TimeInstant(0.0), stopTime);

        exp.start();
        exp.report();
        exp.finish();

        // ---- Resumo de console ----
        System.out.println("\n===== RESUMO DO DIA =====");
        System.out.println("Prioridade ativa? " + prioridadeAtiva);
        System.out.println("Fila única? " + modoFilaUnica);
        System.out.printf("Espera média Urgente:  %.2f min%n", model.getTempoMedioEsperaUrgente());
        System.out.printf("Espera média Não Urgente:  %.2f min%n", model.getTempoMedioEsperaNaoUrgente());

        if (!modoFilaUnica) {
            System.out.println("Pico por consultório (aguardando): " + Arrays.toString(model.getPicoFilaPorConsultorio()));
            System.out.println("\nTabela hora a hora (Total aguardando por consultório):");
            model.printTabelaHoraAHora();
            System.out.println("\nPico global por hora (maior entre consultórios): " +
                    Arrays.toString(model.getPicoHoraGlobal()));
        } else {
            System.out.println("Pico do POOL (total na fila única no dia): " + model.getPicoPoolDia());
            System.out.println("Pico do POOL por hora: " + Arrays.toString(model.getPicoHoraPool()));
            System.out.println("\nTabela hora a hora (distribuição visual do pool):");
            model.printTabelaHoraAHora();
        }

        System.exit(0);
    }
}
