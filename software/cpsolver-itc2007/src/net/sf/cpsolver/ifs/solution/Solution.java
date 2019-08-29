//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package net.sf.cpsolver.ifs.solution;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.sf.cpsolver.ifs.model.Model;
import net.sf.cpsolver.ifs.model.Value;
import net.sf.cpsolver.ifs.model.Variable;
import net.sf.cpsolver.ifs.perturbations.PerturbationsCounter;
import net.sf.cpsolver.ifs.solver.Solver;

public class Solution<V extends Variable<V, T>, T extends Value<V, T>> {
    private static DecimalFormat sTimeFormat;
    private Model<V, T> iModel;
    private long iIteration;
    private double iTime;
    private boolean iBestComplete;
    private Map<String, String> iBestInfo;
    private long iBestIteration;
    private double iBestTime;
    private double iBestPerturbationsPenaly;
    private double iBestValue;
    private List<SolutionListener<V, T>> iSolutionListeners;
    private PerturbationsCounter<V, T> iPerturbationsCounter;

    public Solution(Model<V, T> model) {
        this(model, 0L, 0.0D);
    }

    public Solution(Model<V, T> model, long iteration, double time) {
        this.iIteration = 0L;
        this.iTime = 0.0D;
        this.iBestComplete = false;
        this.iBestInfo = null;
        this.iBestIteration = -1L;
        this.iBestTime = -1.0D;
        this.iBestPerturbationsPenaly = -1.0D;
        this.iBestValue = 0.0D;
        this.iSolutionListeners = new ArrayList();
        this.iPerturbationsCounter = null;
        this.iModel = model;
        this.iIteration = iteration;
        this.iTime = time;
    }

    public long getIteration() {
        return this.iIteration;
    }

    public Model<V, T> getModel() {
        return this.iModel;
    }

    public double getTime() {
        return this.iTime;
    }

    public void update(double time) {
        this.iTime = time;
        ++this.iIteration;
        Iterator i$ = this.iSolutionListeners.iterator();

        while(i$.hasNext()) {
            SolutionListener<V, T> listener = (SolutionListener)i$.next();
            listener.solutionUpdated(this);
        }

    }

    public void init(Solver<V, T> solver) {
        this.iIteration = 0L;
        this.iTime = 0.0D;
        if (this.iModel != null) {
            this.iModel.init(solver);
        }

        this.iPerturbationsCounter = solver.getPerturbationsCounter();
    }

    public String toString() {
        return "Solution{\n  model=" + this.iModel + ",\n  iteration=" + this.iIteration + ",\n  time=" + this.iTime + "\n}";
    }

    public Map<String, String> getInfo() {
        Map<String, String> ret = this.getModel().getInfo();
        if (this.getPerturbationsCounter() != null) {
            this.getPerturbationsCounter().getInfo(ret, this.getModel());
        }

        ret.put("Time", sTimeFormat.format(this.getTime() / 60.0D) + " min");
        ret.put("Iteration", String.valueOf(this.getIteration()));
        if (this.getTime() > 0.0D) {
            ret.put("Speed", sTimeFormat.format((double)this.getIteration() / this.getTime()) + " it/s");
        }

        Iterator i$ = this.iSolutionListeners.iterator();

        while(i$.hasNext()) {
            SolutionListener<V, T> listener = (SolutionListener)i$.next();
            listener.getInfo(this, ret);
        }

        return ret;
    }

    public Map<String, String> getExtendedInfo() {
        Map<String, String> ret = this.getModel().getExtendedInfo();
        if (this.getPerturbationsCounter() != null) {
            this.getPerturbationsCounter().getInfo(ret, this.getModel());
        }

        ret.put("Time", sTimeFormat.format(this.getTime() / 60.0D) + " min");
        ret.put("Iteration", String.valueOf(this.getIteration()));
        if (this.getTime() > 0.0D) {
            ret.put("Speed", sTimeFormat.format((double)this.getIteration() / this.getTime()) + " it/s");
        }

        Iterator i$ = this.iSolutionListeners.iterator();

        while(i$.hasNext()) {
            SolutionListener<V, T> listener = (SolutionListener)i$.next();
            listener.getInfo(this, ret);
        }

        return ret;
    }

    public Map<String, String> getInfo(Collection<V> variables) {
        Map<String, String> ret = this.getModel().getInfo(variables);
        if (this.getPerturbationsCounter() != null) {
            this.getPerturbationsCounter().getInfo(ret, this.getModel(), variables);
        }

        ret.put("Time", sTimeFormat.format(this.getTime()) + " sec");
        ret.put("Iteration", String.valueOf(this.getIteration()));
        if (this.getTime() > 0.0D) {
            ret.put("Speed", sTimeFormat.format((double)this.getIteration() / this.getTime()) + " it/s");
        }

        Iterator i$ = this.iSolutionListeners.iterator();

        while(i$.hasNext()) {
            SolutionListener<V, T> listener = (SolutionListener)i$.next();
            listener.getInfo(this, ret, variables);
        }

        return ret;
    }

    public Map<String, String> getBestInfo() {
        return this.iBestInfo;
    }

    public long getBestIteration() {
        return this.iBestIteration < 0L ? this.getIteration() : this.iBestIteration;
    }

    public double getBestTime() {
        return this.iBestTime < 0.0D ? this.getTime() : this.iBestTime;
    }

    public boolean isBestComplete() {
        return this.iBestComplete;
    }

    public double getBestValue() {
        return this.iBestValue;
    }

    public void setBestValue(double bestValue) {
        this.iBestValue = bestValue;
    }

    public double getBestPerturbationsPenalty() {
        return this.iBestPerturbationsPenaly;
    }

    public PerturbationsCounter<V, T> getPerturbationsCounter() {
        return this.iPerturbationsCounter;
    }

    public void clearBest() {
        this.getModel().clearBest();
        this.iBestInfo = null;
        this.iBestTime = -1.0D;
        this.iBestIteration = -1L;
        this.iBestComplete = false;
        this.iBestValue = 0.0D;
        this.iBestPerturbationsPenaly = -1.0D;
        Iterator i$ = this.iSolutionListeners.iterator();

        while(i$.hasNext()) {
            SolutionListener<V, T> listener = (SolutionListener)i$.next();
            listener.bestCleared(this);
        }

    }

    public void saveBest() {
        this.getModel().saveBest();
        this.iBestInfo = this.getInfo();
        this.iBestTime = this.getTime();
        this.iBestIteration = this.getIteration();
        this.iBestComplete = this.getModel().nrUnassignedVariables() == 0;
        this.iBestValue = this.getModel().getTotalValue();
        this.iBestPerturbationsPenaly = this.iPerturbationsCounter == null ? 0.0D : this.iPerturbationsCounter.getPerturbationPenalty(this.getModel());
        Iterator i$ = this.iSolutionListeners.iterator();

        while(i$.hasNext()) {
            SolutionListener<V, T> listener = (SolutionListener)i$.next();
            listener.bestSaved(this);
        }

    }

    public void restoreBest() {
        if (this.iBestInfo != null) {
            this.getModel().restoreBest();
            this.iTime = this.iBestTime;
            this.iIteration = this.iBestIteration;
            Iterator i$ = this.iSolutionListeners.iterator();

            while(i$.hasNext()) {
                SolutionListener<V, T> listener = (SolutionListener)i$.next();
                listener.bestRestored(this);
            }

        }
    }

    public void addSolutionListener(SolutionListener<V, T> listener) {
        this.iSolutionListeners.add(listener);
    }

    public void removeSolutionListener(SolutionListener<V, T> listener) {
        this.iSolutionListeners.remove(listener);
    }

    static {
        sTimeFormat = new DecimalFormat("0.00", new DecimalFormatSymbols(Locale.US));
    }
}
