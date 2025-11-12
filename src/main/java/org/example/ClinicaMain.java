package org.example;

import desmoj.core.simulator.Experiment;
import desmoj.core.simulator.TimeInstant;
import org.example.model.ClinicaModel;

public class ClinicaMain {
    public static void main(String[] args) {
        int numConsultorios = 4;
        double horizonteMin = 600.0;

        Experiment exp = new Experiment("Clinica Vida Saudavel");
        ClinicaModel model = new ClinicaModel(null, "Modelo Clinica", true, true, numConsultorios, horizonteMin);
        model.setSeeds(123L); // opcional

        model.connectToExperiment(exp);
        exp.setShowProgressBar(true);
        exp.stop(new TimeInstant(horizonteMin));

        exp.tracePeriod(new TimeInstant(0.0), new TimeInstant(horizonteMin));
        exp.debugPeriod(new TimeInstant(0.0), new TimeInstant(horizonteMin));

        exp.start();
        exp.report();
        exp.finish();

        System.exit(0);
    }
}
