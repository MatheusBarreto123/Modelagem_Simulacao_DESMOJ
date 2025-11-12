package org.example.model;

import co.paralleluniverse.fibers.SuspendExecution;
import desmoj.core.simulator.ProcessQueue;
import desmoj.core.simulator.SimProcess;
import desmoj.core.simulator.TimeSpan;

public class Paciente extends SimProcess {

    private final ClinicaModel model;
    private final boolean urgente;
    private int indiceConsultorio;
    private double tempoChegada;

    public Paciente(ClinicaModel owner, String name, boolean showInTrace, boolean urgente) {
        super(owner, name, showInTrace);
        this.model = owner;
        this.urgente = urgente;
    }

    @Override
    public void lifeCycle() throws SuspendExecution {
        tempoChegada = presentTime().getTimeAsDouble();

        // Contagem de chegadas (por tipo)
        model.incChegada(urgente);

        // Escolhe consultório de menor fila
        indiceConsultorio = model.escolherConsultorio();
        ProcessQueue<Paciente> fila = model.filasConsultorio[indiceConsultorio];

        // Entra na fila e atualiza Qmax
        fila.insert(this);
        model.atualizaQmax(indiceConsultorio);

        // Se não sou o primeiro ou está ocupado, espero
        if (fila.first() != this || model.consultorioOcupado[indiceConsultorio]) {
            passivate();
        }

        // Minha vez
        fila.remove(this);
        model.consultorioOcupado[indiceConsultorio] = true;

        // Espera efetiva
        double tInicio = presentTime().getTimeAsDouble();
        double espera = tInicio - tempoChegada;
        model.registraEspera(espera, urgente);

        // Tempo de atendimento (garantidamente > 0)
        double dur = model.sampleTempoAtendimento(urgente);

        // Contagem de atendidos e utilização
        model.incAtendido(urgente);
        model.adicionaOcupacao(indiceConsultorio, dur);

        // Atendimento
        hold(new TimeSpan(dur));

        // Libera consultório
        model.consultorioOcupado[indiceConsultorio] = false;

        // Acorda próximo da fila, se existir
        if (!fila.isEmpty()) {
            Paciente proximo = fila.first();
            proximo.activate();
        }
    }
}
