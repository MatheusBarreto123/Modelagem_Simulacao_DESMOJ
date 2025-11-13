package org.example;

import co.paralleluniverse.fibers.SuspendExecution;
import desmoj.core.simulator.SimProcess;
import desmoj.core.simulator.TimeSpan;
import org.example.model.ClinicaModel;
import org.example.model.Paciente;


//Processo que gera pacientes ao longo do dia
public class GeradorPacientes extends SimProcess {

    public ClinicaModel model;

    public GeradorPacientes(ClinicaModel owner, String name, boolean showInTrace) {
        super(owner, name, showInTrace);
        this.model = owner;
    }

    @Override
    public void lifeCycle() throws SuspendExecution {

        // MESMO horizonte da simulação: 600 min (10h)
        double fimSim = 600.0;

        while (true) {
            double agora = presentTime().getTimeAsDouble();
            if (agora >= fimSim) {
                // Não gera mais pacientes após o tempo de simulação
                break;
            }

            boolean urgente = model.sampleUrgente();
            Paciente p = new Paciente(model, "Paciente", true, urgente);
            p.activate();

            double inter = model.sampleInterChegada();
            if (inter <= 0) inter = 0.1;

            // Garante que o gerador não passe muito do fim
            agora = presentTime().getTimeAsDouble();
            if (agora + inter > fimSim) {
                double restante = fimSim - agora;
                if (restante > 0) {
                    hold(new TimeSpan(restante));
                }
                break;
            } else {
                hold(new TimeSpan(inter));
            }
        }
    }
}
