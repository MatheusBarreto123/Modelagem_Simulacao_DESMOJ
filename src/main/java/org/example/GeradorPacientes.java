package org.example;

import co.paralleluniverse.fibers.SuspendExecution;
import desmoj.core.simulator.SimProcess;
import desmoj.core.simulator.TimeSpan;
import org.example.model.ClinicaModel;
import org.example.model.Paciente;

public class GeradorPacientes extends SimProcess {

    public final ClinicaModel model;

    public GeradorPacientes(ClinicaModel owner, String name, boolean showInTrace) {
        super(owner, name, showInTrace);
        this.model = owner;
    }

    @Override
    public void lifeCycle() throws SuspendExecution {
        while (true) {
            boolean urgente = model.sampleUrgente();

            Paciente p = new Paciente(model, "Paciente", true, urgente);
            p.activate();

            double inter = model.distChegada.sample();
            if (inter <= 0) inter = 0.1; // robustez

            hold(new TimeSpan(inter));
        }
    }
}
