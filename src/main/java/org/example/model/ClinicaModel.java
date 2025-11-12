package org.example.model;

import desmoj.core.dist.BoolDistBernoulli;
import desmoj.core.dist.ContDistExponential;
import desmoj.core.dist.ContDistNormal;
import desmoj.core.simulator.Model;
import desmoj.core.statistic.Tally;

public class ClinicaModel extends Model {

    protected final int numConsultorios;
    protected final double horizonteMin;

    // Filas e estado
    public desmoj.core.simulator.ProcessQueue<Paciente>[] filasConsultorio;
    public boolean[] consultorioOcupado;

    // Distribuições
    public ContDistExponential distChegada;
    protected ContDistNormal distAtendimentoUrgente;
    protected ContDistNormal distAtendimentoNaoUrgente;
    protected BoolDistBernoulli distTipoUrgente;

    // Métricas
    protected Tally tempoEsperaUrgente;
    protected Tally tempoEsperaNaoUrgente;

    // Qmax por consultório e global
    protected int[] qmaxPorConsultorio;
    protected int qmaxGlobal;

    // Utilização (soma de tempos ocupados)
    protected double[] somaTempoOcupado;

    // Contadores
    protected long chegadasUrg, chegadasNaoUrg, atendUrg, atendNaoUrg;

    // Gerador
    protected org.example.GeradorPacientes gerador;

    @SuppressWarnings("unchecked")
    public ClinicaModel(Model owner, String name, boolean showInReport, boolean showInTrace,
                        int numConsultorios, double horizonteMin) {
        super(owner, name, showInReport, showInTrace);
        this.numConsultorios = numConsultorios;
        this.horizonteMin = horizonteMin;
    }

    public void setSeeds(long baseSeed) {
        if (distChegada != null) distChegada.setSeed(baseSeed + 11);
        if (distAtendimentoUrgente != null) distAtendimentoUrgente.setSeed(baseSeed + 21);
        if (distAtendimentoNaoUrgente != null) distAtendimentoNaoUrgente.setSeed(baseSeed + 31);
        if (distTipoUrgente != null) distTipoUrgente.setSeed(baseSeed + 41);
    }

    @Override
    public String description() {
        return "Modelo de Gestão de Atendimentos da Clínica Vida Saudável usando DESMO-J.";
    }

    @Override
    public void init() {
        filasConsultorio = new desmoj.core.simulator.ProcessQueue[numConsultorios];
        consultorioOcupado = new boolean[numConsultorios];
        qmaxPorConsultorio = new int[numConsultorios];
        somaTempoOcupado = new double[numConsultorios];

        for (int i = 0; i < numConsultorios; i++) {
            filasConsultorio[i] = new desmoj.core.simulator.ProcessQueue<>(
                    this, "Fila_Consultorio" + (i + 1), true, true
            );
            consultorioOcupado[i] = false;
            qmaxPorConsultorio[i] = 0;
            somaTempoOcupado[i] = 0.0;
        }
        qmaxGlobal = 0;
        chegadasUrg = chegadasNaoUrg = atendUrg = atendNaoUrg = 0;

        distChegada = new ContDistExponential(this, "Dist_Tempo_Entre_Chegadas", 15.0, true, true);
        distAtendimentoUrgente = new ContDistNormal(this, "Dist_Tempo_Atendimento_Urgente", 10.0, 3.0, true, true);
        distAtendimentoNaoUrgente = new ContDistNormal(this, "Dist_Tempo_Atendimento_Nao_Urgente", 20.0, 5.0, true, true);
        distTipoUrgente = new BoolDistBernoulli(this, "Dist_Tipo_Paciente_Urgente", 0.3, true, true);

        tempoEsperaUrgente = new Tally(this, "Tempo_Espera_Urgente", true, true);
        tempoEsperaNaoUrgente = new Tally(this, "Tempo_Espera_Nao_Urgente", true, true);

        gerador = new org.example.GeradorPacientes(this, "Gerador de Pacientes", true);
    }

    @Override
    public void doInitialSchedules() {
        gerador.activate();
    }

    // === Acessores de métricas ===
    public double getTempoMedioEsperaUrgente() { return tempoEsperaUrgente.getMean(); }
    public double getTempoMedioEsperaNaoUrgente() { return tempoEsperaNaoUrgente.getMean(); }

    public int[] getQmaxPorConsultorio() { return qmaxPorConsultorio.clone(); }
    public int getQmaxGlobal() { return qmaxGlobal; }

    public double[] getUtilizacaoPorConsultorio() {
        double[] u = new double[numConsultorios];
        for (int i = 0; i < numConsultorios; i++) {
            u[i] = (horizonteMin > 0) ? (somaTempoOcupado[i] / horizonteMin) : 0.0;
        }
        return u;
    }

    public long getChegadasUrgentes() { return chegadasUrg; }
    public long getChegadasNaoUrgentes() { return chegadasNaoUrg; }
    public long getAtendimentosUrgentes() { return atendUrg; }
    public long getAtendimentosNaoUrgentes() { return atendNaoUrg; }

    // === Lógica utilitária chamada pelos processos ===
    public int escolherConsultorio() {
        int escolhido = 0;
        int menor = filasConsultorio[0].length();
        for (int i = 1; i < numConsultorios; i++) {
            int tam = filasConsultorio[i].length();
            if (tam < menor) {
                menor = tam;
                escolhido = i;
            }
        }
        return escolhido;
    }

    public double sampleTempoAtendimento(boolean urgente) {
        double t;
        if (urgente) {
            do { t = distAtendimentoUrgente.sample(); } while (t <= 0);
        } else {
            do { t = distAtendimentoNaoUrgente.sample(); } while (t <= 0);
        }
        return t;
    }

    public boolean sampleUrgente() { return distTipoUrgente.sample(); }

    public void registraEspera(double espera, boolean urgente) {
        if (urgente) tempoEsperaUrgente.update(espera);
        else tempoEsperaNaoUrgente.update(espera);
    }

    public void incChegada(boolean urgente) {
        if (urgente) chegadasUrg++; else chegadasNaoUrg++;
    }

    public void incAtendido(boolean urgente) {
        if (urgente) atendUrg++; else atendNaoUrg++;
    }

    public void adicionaOcupacao(int idx, double dur) {
        somaTempoOcupado[idx] += dur;
    }

    public void atualizaQmax(int idx) {
        int tam = filasConsultorio[idx].length();
        if (tam > qmaxPorConsultorio[idx]) qmaxPorConsultorio[idx] = tam;
        if (qmaxPorConsultorio[idx] > qmaxGlobal) qmaxGlobal = qmaxPorConsultorio[idx];
    }
}
