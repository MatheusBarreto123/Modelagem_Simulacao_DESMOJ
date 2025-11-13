package org.example.model;

import desmoj.core.simulator.*;
import desmoj.core.dist.*;
import desmoj.core.statistic.Tally;
import org.example.GeradorPacientes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


//ClinicaModel é o modelo DESMOJ que representa a Clinica
public class ClinicaModel extends Model {

    // Parâmetros
    protected final int numConsultorios;
    public final boolean prioridadeAtiva; //se true, urgentes tem prioridade na fila
    public final boolean modoFilaUnica; //se true, todos os consultórios atendem de uma fila unica(pool); se false, cada consultório tem sua própria fila
    protected final double[] fatorChegadaHora;
    protected final Double meanNaoUrgenteOverride;   // (c)

    // Filas por consultório (modo filas separadas)
    @SuppressWarnings("unchecked")
    public ProcessQueue<Paciente>[] filaUrg = new ProcessQueue[0]; //fila de urgentes no consultório[i]
    @SuppressWarnings("unchecked")
    public ProcessQueue<Paciente>[] filaNao = new ProcessQueue[0]; //filas de não urgentes no consultório[i]

    // Filas (modo fila única)
    public ProcessQueue<Paciente> filaUrgGlobal;
    public ProcessQueue<Paciente> filaNaoGlobal;

    // Estado dos consultórios
    public boolean[] consultorioOcupado;

    // Distribuições
    public ContDistExponential distChegadaBase;            // mean 15
    protected ContDistNormal distAtendimentoUrgente;       // N(10,3)
    protected ContDistNormal distAtendimentoNaoUrgente;    // N(20,5) ou override
    protected BoolDistBernoulli distTipoUrgente;           // 30%

    // Métricas
    protected Tally tempoEsperaUrgente;
    protected Tally tempoEsperaNaoUrgente;

    // Filas separadas – picos
    protected int[]  picoFilaPorConsultorio;     // pico dia por consultório
    protected int[][] picoHoraPorConsultorio;    // [hora][consultorio]

    // Fila única – picos
    private   int    picoPoolDia = 0;            // pico do total da fila única no dia
    private   int[]  picoHoraPool = new int[10]; // pico do pool por hora

    // Tabela hora-a-hora (snapshot)
    protected List<int[]> tabelaHoraAHora;

    // Processos
    protected GeradorPacientes gerador;
    protected SamplerHora samplerHora;

    //metódo construtor --> CLINICA MODEL
    public ClinicaModel(Model owner, String name, boolean showInReport, boolean showInTrace,
                        int numConsultorios, boolean prioridadeAtiva, boolean modoFilaUnica,
                        double[] fatorChegadaHora, Double meanNaoUrgenteOverride) {
        super(owner, name, showInReport, showInTrace);
        this.numConsultorios = numConsultorios;
        this.prioridadeAtiva = prioridadeAtiva;
        this.modoFilaUnica = modoFilaUnica;
        this.fatorChegadaHora = fatorChegadaHora != null ? Arrays.copyOf(fatorChegadaHora, fatorChegadaHora.length) : null;
        this.meanNaoUrgenteOverride = meanNaoUrgenteOverride;
    }

    @Override
    public String description() {
        return "Modelo de Gestao de Atendimentos da Clínica Vida Saudável (DESMO-J)";

    }

    @Override
    public void init() {
        // Estado
        consultorioOcupado = new boolean[numConsultorios];
        Arrays.fill(consultorioOcupado, false);

        // Filas
        if (modoFilaUnica) {
            filaUrgGlobal = new ProcessQueue<>(this, "Fila Unica Urgente", true, true);
            filaNaoGlobal = new ProcessQueue<>(this, "Fila Unica Não Urgente", true, true);
        } else {
            filaUrg = new ProcessQueue[numConsultorios];
            filaNao = new ProcessQueue[numConsultorios];
            for (int i = 0; i < numConsultorios; i++) {
                filaUrg[i] = new ProcessQueue<>(this, "Fila Urgente - Consultorio: " + (i + 1), true, true);
                filaNao[i] = new ProcessQueue<>(this, "Fila Não Urgente - Consultorio: " + (i + 1), true, true);
            }
        }

        // Métricas de pico
        picoFilaPorConsultorio = new int[numConsultorios];
        picoHoraPorConsultorio = new int[10][numConsultorios];
        picoPoolDia = 0; // <-- picoPoolDia é int, inicialize assim
        for (int h = 0; h < 10; h++) {
            Arrays.fill(picoHoraPorConsultorio[h], 0);
            picoHoraPool[h] = 0;
        }

        // Tabela hora-a-hora
        tabelaHoraAHora = new ArrayList<>();

        // Distribuições
        distChegadaBase = new ContDistExponential(this, "Distancia Entre Chegada dos Pacientes", 15.0, true, true);  //o sistema de chegada segue uma distribuição exponencial

        distAtendimentoUrgente = new ContDistNormal(this, "Dist_Serv_Urg", 10.0, 3.0, true, true); //Tempo Atendimento --> Dist. Normal --> casos urgentes temos 10 min de atendimento e 3 min de desvio padrão
        distAtendimentoUrgente.setNonNegative(true);

        double meanNao = (meanNaoUrgenteOverride != null) ? meanNaoUrgenteOverride : 20.0;
        distAtendimentoNaoUrgente = new ContDistNormal(this, "Dist_Serv_Nao", meanNao, 5.0, true, true); //Tempo de Atendimento --> Dist. Normal --> para casos não urgentes temos 20 min de atendimento e 5 de desvio padrão
        distAtendimentoNaoUrgente.setNonNegative(true);

        distTipoUrgente = new BoolDistBernoulli(this, "Dist_Tipo_Urgente", 0.3, true, true);

        // Tallys
        tempoEsperaUrgente    = new Tally(this, "Tempo_Espera_Urgente", true, true);
        tempoEsperaNaoUrgente = new Tally(this, "Tempo_Espera_NaoUrgente", true, true);

        // Processos
        gerador = new GeradorPacientes(this, "Gerador_Pacientes", true);
        samplerHora = new SamplerHora(this, "Sampler_Hora", true);
    }

    @Override
    public void doInitialSchedules() {
        gerador.activate();
        samplerHora.activate();
    }

    // ======= Apoio de decisão =======

    // ETA (filas separadas)
    public double estimaETA(int i) {
        double base = consultorioOcupado[i] ? mediaServicoMista() : 0.0;
        if (modoFilaUnica) return base; // sem filas por consultório no pool
        int qUrg = filaUrg[i].length();
        int qNao = filaNao[i].length();
        double soma = qUrg * mediaServicoUrgente() + qNao * mediaServicoNaoUrgente();
        return base + soma;
    }

    private double mediaServicoUrgente()    { return distAtendimentoUrgente.getMean(); }
    private double mediaServicoNaoUrgente() { return distAtendimentoNaoUrgente.getMean(); }
    private double mediaServicoMista()      { return 0.3 * mediaServicoUrgente() + 0.7 * mediaServicoNaoUrgente(); }

    public int escolherConsultorioETA() {
        int escolhido = 0;
        double melhorETA = estimaETA(0);
        for (int i = 1; i < numConsultorios; i++) {
            double eta = estimaETA(i);
            if (eta < melhorETA) { melhorETA = eta; escolhido = i; }
        }
        return escolhido;
    }

    // Próximo (filas separadas)
    public Paciente pickProximo(int i) {
        if (prioridadeAtiva) {
            if (!filaUrg[i].isEmpty()) return filaUrg[i].first();
            if (!filaNao[i].isEmpty()) return filaNao[i].first();
            return null;
        } else {
            if (filaUrg[i].isEmpty() && filaNao[i].isEmpty()) return null;
            if (filaUrg[i].isEmpty()) return filaNao[i].first();
            if (filaNao[i].isEmpty()) return filaUrg[i].first();
            Paciente u = filaUrg[i].first();
            Paciente n = filaNao[i].first();
            return (u.getChegada() <= n.getChegada()) ? u : n;
        }
    }

    // Próximo (pool)
    public Paciente pickGlobal() {
        if (prioridadeAtiva) {
            if (!filaUrgGlobal.isEmpty()) return filaUrgGlobal.first();
            if (!filaNaoGlobal.isEmpty()) return filaNaoGlobal.first();
            return null;
        } else {
            if (filaUrgGlobal.isEmpty() && filaNaoGlobal.isEmpty()) return null;
            if (filaUrgGlobal.isEmpty()) return filaNaoGlobal.first();
            if (filaNaoGlobal.isEmpty()) return filaUrgGlobal.first();
            Paciente u = filaUrgGlobal.first();
            Paciente n = filaNaoGlobal.first();
            return (u.getChegada() <= n.getChegada()) ? u : n;
        }
    }

    // Despacho (pool)
    public void tentarDespacho() {
        if (!modoFilaUnica) return;
        for (int i = 0; i < numConsultorios; i++) {
            if (!consultorioOcupado[i]) {
                Paciente proximo = pickGlobal();
                if (proximo != null) {
                    // Remove do pool e ativa
                    if (proximo.isUrgente()) filaUrgGlobal.remove(proximo);
                    else filaNaoGlobal.remove(proximo);
                    atualizaPicosPool(); // após remoção também registramos (não diminui pico, mas registra estado)
                    proximo.setIndiceConsultorio(i);
                    consultorioOcupado[i] = true;
                    proximo.activate();
                }
            }
        }
    }

    public boolean isMinhaVez(int i, Paciente p) {
        if (modoFilaUnica) return true; // no pool, ativado pelo dispatcher
        Paciente proximo = pickProximo(i);
        return proximo == p;
    }

    public boolean sampleUrgente() {
        return distTipoUrgente.sample();
    }

    // Interchegada com perfil horário
    public double sampleInterChegada() {
        double meanBase = distChegadaBase.getMean(); // 15
        double t = presentTime().getTimeAsDouble();
        int h = (int)Math.max(0, Math.min(9, Math.floor(t / 60.0)));
        double fator = (fatorChegadaHora != null && fatorChegadaHora.length > h) ? fatorChegadaHora[h] : 1.0;
        double meanHora = meanBase / Math.max(0.0001, fator);
        double amostraBase = distChegadaBase.sample();
        return amostraBase * (meanHora / meanBase);
    }

    public double sampleTempoAtendimento(boolean urgente) {
        return urgente ? distAtendimentoUrgente.sample() : distAtendimentoNaoUrgente.sample();
    }

    public void registraEspera(double espera, boolean urgente) {
        if (urgente) tempoEsperaUrgente.update(espera);
        else tempoEsperaNaoUrgente.update(espera);
    }

    public double getTempoMedioEsperaUrgente()    { return tempoEsperaUrgente.getMean(); }
    public double getTempoMedioEsperaNaoUrgente() { return tempoEsperaNaoUrgente.getMean(); }

    // ======= Picos (filas separadas) =======
    public void atualizaPicosFilasSeparadas() {
        int h = (int)Math.max(0, Math.min(9, Math.floor(presentTime().getTimeAsDouble() / 60.0)));
        for (int i = 0; i < numConsultorios; i++) {
            int aguardando = filaUrg[i].length() + filaNao[i].length();
            if (aguardando > picoFilaPorConsultorio[i]) picoFilaPorConsultorio[i] = aguardando;
            if (aguardando > picoHoraPorConsultorio[h][i]) picoHoraPorConsultorio[h][i] = aguardando;
        }
    }

    // ======= Picos (pool) =======
    public void atualizaPicosPool() {
        int totalPool = (filaUrgGlobal == null ? 0 : filaUrgGlobal.length())
                + (filaNaoGlobal == null ? 0 : filaNaoGlobal.length());
        if (totalPool > picoPoolDia) picoPoolDia = totalPool;
        int h = (int)Math.max(0, Math.min(9, Math.floor(presentTime().getTimeAsDouble() / 60.0)));
        if (totalPool > picoHoraPool[h]) picoHoraPool[h] = totalPool;
    }

    public int[] getPicoFilaPorConsultorio() { return picoFilaPorConsultorio; }
    public int   getPicoPoolDia()            { return picoPoolDia; }
    public int[] getPicoHoraPool()           { return Arrays.copyOf(picoHoraPool, picoHoraPool.length); }

    public int[] getPicoHoraGlobal() {
        int[] out = new int[10];
        if (modoFilaUnica) {
            // no pool, retornamos o pico do pool por hora
            System.arraycopy(picoHoraPool, 0, out, 0, out.length);
        } else {
            for (int h = 0; h < 10; h++) {
                int max = 0;
                for (int i = 0; i < numConsultorios; i++) {
                    max = Math.max(max, picoHoraPorConsultorio[h][i]);
                }
                out[h] = max;
            }
        }
        return out;
    }

    public void printTabelaHoraAHora() {
        System.out.print("Hora");
        for (int i = 0; i < numConsultorios; i++) System.out.printf("\tC%02d", (i + 1));
        System.out.println();
        for (int h = 0; h < tabelaHoraAHora.size(); h++) {
            int[] linha = tabelaHoraAHora.get(h);
            System.out.printf("%02d:00", (h + 1));
            for (int v : linha) System.out.printf("\t%d", v);
            System.out.println();
        }
    }

    // Sampler: snapshot por hora
    protected class SamplerHora extends SimProcess {
        public SamplerHora(Model owner, String name, boolean showInTrace) { super(owner, name, showInTrace); }
        @Override
        public void lifeCycle() throws co.paralleluniverse.fibers.SuspendExecution {
            int horas = 10; // 600 min
            for (int h = 0; h < horas; h++) {
                hold(new TimeSpan(60.0)); // a cada hora
                int[] snap = new int[numConsultorios];
                if (modoFilaUnica) {
                    int totalPool = (filaUrgGlobal == null ? 0 : filaUrgGlobal.length())
                            + (filaNaoGlobal == null ? 0 : filaNaoGlobal.length());
                    int porCons = (int)Math.ceil(totalPool / (double)numConsultorios);
                    Arrays.fill(snap, porCons);
                    atualizaPicosPool(); // registrar pico por hora
                } else {
                    for (int i = 0; i < numConsultorios; i++) {
                        snap[i] = filaUrg[i].length() + filaNao[i].length();
                    }
                    atualizaPicosFilasSeparadas();
                }
                tabelaHoraAHora.add(snap);
            }
        }
    }
}
