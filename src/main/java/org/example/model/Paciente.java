package org.example.model;

import co.paralleluniverse.fibers.SuspendExecution;
import desmoj.core.simulator.ProcessQueue;
import desmoj.core.simulator.SimProcess;
import desmoj.core.simulator.TimeSpan;

//Processo que representa um paciente passando pela fila e pelo atendimento
public class Paciente extends SimProcess {

    private final ClinicaModel model;
    private final boolean urgente;
    private int indiceConsultorio = -1; // no pool, atribuído no despacho
    private double tempoChegada;
    private double chegada;

    public Paciente(ClinicaModel owner, String name, boolean showInTrace, boolean urgente) {
        super(owner, name, showInTrace);
        this.model = owner;
        this.urgente = urgente;
    }

    public boolean isUrgente() { return urgente; }
    public double getChegada() { return chegada; }
    public void setIndiceConsultorio(int i) { this.indiceConsultorio = i; }

    @Override
    public void lifeCycle() throws SuspendExecution {
        tempoChegada = presentTime().getTimeAsDouble();
        chegada = tempoChegada;

        if (model.modoFilaUnica) {
            // entra no pool global
            if (urgente) model.filaUrgGlobal.insert(this);
            else model.filaNaoGlobal.insert(this);
            model.atualizaPicosPool();

            // tenta despachar se houver servidor livre; senão aguarda
            model.tentarDespacho();
            passivate(); // será reativado pelo dispatcher quando começar atendimento

        } else {
            // filas separadas: escolhe consultório por menor ETA
            indiceConsultorio = model.escolherConsultorioETA();
            ProcessQueue<Paciente> qUrg = model.filaUrg[indiceConsultorio];
            ProcessQueue<Paciente> qNao = model.filaNao[indiceConsultorio];

            if (urgente) qUrg.insert(this);
            else qNao.insert(this);

            model.atualizaPicosFilasSeparadas();

            if (!model.consultorioOcupado[indiceConsultorio] && model.isMinhaVez(indiceConsultorio, this)) {
                // segue direto
            } else {
                passivate();
            }

            // retira da fila
            if (urgente) qUrg.remove(this);
            else qNao.remove(this);
            model.consultorioOcupado[indiceConsultorio] = true;
        }

        // registra espera
        double inicioAtendimento = presentTime().getTimeAsDouble();
        double espera = inicioAtendimento - tempoChegada;
        model.registraEspera(espera, urgente);

        // atendimento
        double serv = model.sampleTempoAtendimento(urgente);
        if (serv <= 0) serv = 0.1;
        hold(new TimeSpan(serv));

        // libera consultório
        model.consultorioOcupado[indiceConsultorio] = false;

        if (model.modoFilaUnica) {
            // após terminar, tenta despachar outro do pool
            model.tentarDespacho();
        } else {
            // ativa próximo da fila do consultório
            Paciente proximo = model.pickProximo(indiceConsultorio);
            if (proximo != null) {
                model.consultorioOcupado[indiceConsultorio] = true;
                proximo.activate();
            }
        }
    }
}
